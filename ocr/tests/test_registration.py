import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from main import app
from recognition import UnreadableDocument
from registration import extract_front, extract_back


FRONT = ["외국인등록증 Residence Card", "등록번호: 000101-5000000", "성명: TEST STUDENT",
         "국적: VIETNAM", "체류자격: D-2", "발급일자: 2020.01.01"]


class RegistrationExtractionTest(unittest.TestCase):
    def test_front_extracts_labels_and_never_invents_missing_fields(self):
        result = extract_front([FRONT])
        self.assertEqual(result, {"name": "TEST STUDENT", "nationality": "VIETNAM", "visaCode": "D-2",
                                  "registrationNumber": "0001015000000", "issueDate": "2020-01-01"})
        for lines in [FRONT[:-1], FRONT + ["발급일자: 2021.01.01"], FRONT + ["등록번호: 000101-6000000"],
                      [line.replace("2020.01.01", "2020.02.30") for line in FRONT],
                      [line.replace("D-2", "UNKNOWN") for line in FRONT], FRONT[1:]]:
            with self.subTest(lines=lines), self.assertRaises(UnreadableDocument):
                extract_front([lines])

    def test_english_labels_next_lines_and_detailed_visa(self):
        result = extract_front([["Residence Card", "0001015000000", "Full Name", "TEST STUDENT",
                                 "Nationality: VN", "Status of Stay: D - 2 - 2", "Date of Issue: 2020-01-01"]])
        self.assertEqual(result["visaCode"], "D-2-2")

    def test_expiry_uses_label_or_latest_permission_row_not_other_dates(self):
        for value in ["2027.07.27", "2026.07.28 ~ 2027.07.27"]:
            result = extract_back([["체류기간 만료일: " + value, "일련번호: 00000000001", "발급일: 2099.01.01"]])
            self.assertEqual(result, {"stayExpiresOn": "2027-07-27", "serialNumber": "00000000001"})
        result = extract_back([["허가일자 만료일자", "2020.01.01 2028.01.01", "2026.01.01 2027.01.01",
                                "체류지 변경", "2099.01.01", "일련번호: 00000000001"]])
        self.assertEqual(result["stayExpiresOn"], "2027-01-01")

    def test_ambiguous_missing_or_invalid_expiry_and_serial_fail_closed(self):
        for lines in [["발급일: 2027.01.01"], ["체류기간 만료일: 2027.02.30"],
                      ["체류기간 만료일: 2027.01.01", "체류기간 만료일: 2028.01.01"],
                      ["허가일자 만료일자", "2026.01.01 2027.01.01", "2026.01.01 2028.01.01"],
                      ["허가일자 만료일자", "2026.01.01 날짜인식불가"],
                      ["체류기간 만료일: 2027.01.01", "일련번호: 123"],
                      ["체류기간 만료일: 2028.01.01 ~ 2027.01.01"]]:
            with self.subTest(lines=lines), self.assertRaises(UnreadableDocument):
                extract_back([lines])
        self.assertIsNone(extract_back([["체류기간 만료일: 2027.01.01"]])["serialNumber"])

    def test_registration_routes_only_accept_images_and_keep_errors_private(self):
        with patch("main.create_engine", return_value=object()), TestClient(app) as client:
            for side, extractor in [("front", extract_front), ("back", extract_back)]:
                with patch("main.recognize_document", return_value={"test": "result"}) as recognize:
                    response = client.post("/registration/" + side, content=b"image", headers={"Content-Type": "image/jpeg"})
                    self.assertEqual(response.status_code, 200)
                    self.assertEqual(response.headers["Cache-Control"], "no-store")
                    self.assertIs(recognize.call_args.args[3], extractor)
                    recognize.reset_mock()
                    response = client.post("/registration/" + side, content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                    self.assertEqual(response.status_code, 415)
                    recognize.assert_not_called()
                with patch("main.recognize_document", side_effect=UnreadableDocument("sensitive")):
                    response = client.post("/registration/" + side, content=b"image", headers={"Content-Type": "image/png"})
                    self.assertEqual(response.status_code, 422)
                    self.assertNotIn("sensitive", response.text)
