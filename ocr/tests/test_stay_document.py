import unittest
from io import BytesIO
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
from PIL import Image

from main import app
from recognition import UnreadableDocument, recognize_document
from stay_document import extract_stay_document


SUBJECT = ["외국인등록 사실증명", "발급일: 2099.01.01", "외국인등록번호 (Foreign Resident Registration No.): 000101-5000000",
           "성명 (Full Name): TEST STUDENT", "체류기간 (Period of Sojourn): 2026.01.01 ~ 2027.07.27",
           "등록일자: 2010.01.01", "등록체류지: 테스트 주소", "체류자격 변경사항", "2098.01.01 D-2",
           "동거 가족 사항", "외국인등록번호: 000101-6000000", "체류기간: 2099.01.01"]


class StayDocumentTest(unittest.TestCase):
    def test_reads_only_current_subject_and_ignores_history_family_and_issue_dates(self):
        self.assertEqual(extract_stay_document([SUBJECT]), {
            "registrationNumber": "0001015000000", "stayExpiresOn": "2027-07-27"})
        for value in ["2027.07.27", "2027-07-27", "2026/1/1 ~ 2027/7/27", "2026.1.1. ~ 2027.7.27."]:
            with self.subTest(value=value):
                lines = [line.replace("2026.01.01 ~ 2027.07.27", value) for line in SUBJECT]
                self.assertEqual(extract_stay_document([lines])["stayExpiresOn"], "2027-07-27")

    def test_english_and_next_line_labels(self):
        self.assertEqual(extract_stay_document([["CERTIFICATE OF ALIEN REGISTRATION", "Alien Registration No.",
            "0001015000000", "Period of Sojourn", "2027.07.27", "Date of Registration: 2000.01.01"]])["stayExpiresOn"], "2027-07-27")
        self.assertEqual(extract_stay_document([["외국인등록 사실증명", "외국인등록번호", "(Foreign Resident Registration No.)",
            "0001015000000", "체류기간", "(Period of Sojourn)", "2027.07.27"]])["stayExpiresOn"], "2027-07-27")

    def test_missing_masked_conflicting_and_ambiguous_fields_fail_closed(self):
        bad = [[], SUBJECT[1:], [line for line in SUBJECT if not line.startswith("체류기간 (Period")],
               [line.replace("000101-5000000", "000101-5******") for line in SUBJECT],
               SUBJECT[:5] + ["체류기간: 2028.07.27"],
               SUBJECT[:3] + ["외국인등록번호: 000101-6000000"] + SUBJECT[3:],
               SUBJECT[:4] + ["체류기간 (Period of Sojourn) 등록일자 (Date of Registration)", "2027.07.27 2020.01.01"]]
        for lines in bad:
            with self.subTest(lines=lines), self.assertRaises(UnreadableDocument):
                extract_stay_document([lines])

    def test_invalid_dates_and_conflicting_pages_are_rejected(self):
        for value in ["2027.02.30", "2028.01.01 ~ 2027.01.01", "2026.01.01 2027.07.27",
                      "2026.01.01 ~ 2027.07.27 2099.01.01", "판독불가", "2027.07.27 ~ 판독불가"]:
            with self.subTest(value=value), self.assertRaises(UnreadableDocument):
                extract_stay_document([[line.replace("2026.01.01 ~ 2027.07.27", value) for line in SUBJECT]])
        for pages in [[], [SUBJECT] * 4, [SUBJECT, ["continuation without identifiable subject"]],
                      [SUBJECT, [line.replace("2027.07.27", "2028.07.27") for line in SUBJECT]],
                      [SUBJECT, [line.replace("000101-5000000", "000101-6000000") for line in SUBJECT]]]:
            with self.subTest(pages=pages), self.assertRaises(UnreadableDocument):
                extract_stay_document(pages)
        self.assertEqual(extract_stay_document([SUBJECT] * 2)["stayExpiresOn"], "2027-07-27")

    def test_route_accepts_only_pdf_and_sanitizes_failures(self):
        with patch("main.create_engine", return_value=object()), TestClient(app) as client:
            with patch("main.recognize_document", return_value={"stayExpiresOn": "2027-07-27"}) as recognize:
                response = client.post("/registration/stay-document", content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.headers["Cache-Control"], "no-store")
                self.assertIs(recognize.call_args.args[3], extract_stay_document)
                self.assertEqual(recognize.call_args.kwargs, {"max_pages": 3})
                recognize.reset_mock()
                response = client.post("/registration/stay-document", content=b"image", headers={"Content-Type": "image/png"})
                self.assertEqual(response.status_code, 415)
                recognize.assert_not_called()
            for error, status in [(UnreadableDocument("private"), 422), (RuntimeError("private"), 503)]:
                with patch("main.recognize_document", side_effect=error):
                    response = client.post("/registration/stay-document", content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                    self.assertEqual(response.status_code, status)
                    self.assertNotIn("private", response.text)

    def test_pdf_page_limit_and_broken_document_fail_before_ocr(self):
        engine = Mock()
        with Image.new("RGB", (20, 20), "white") as image:
            output = BytesIO()
            image.save(output, format="PDF", save_all=True, append_images=[image] * 3)
            with self.assertRaises(UnreadableDocument):
                recognize_document(engine, output.getvalue(), "application/pdf", extract_stay_document, max_pages=3)
        with self.assertRaises(UnreadableDocument):
            recognize_document(engine, b"%PDF-broken", "application/pdf", extract_stay_document, max_pages=3)
        engine.predict.assert_not_called()

    def test_decoded_pdf_uses_subject_extractor(self):
        engine = Mock()
        engine.predict.return_value = [{"rec_texts": SUBJECT,
            "rec_polys": [[[0, i * 30], [400, i * 30], [400, i * 30 + 15], [0, i * 30 + 15]]
                          for i in range(len(SUBJECT))]}]
        with Image.new("RGB", (100, 100), "white") as image, BytesIO() as output:
            image.save(output, format="PDF")
            result = recognize_document(engine, output.getvalue(), "application/pdf", extract_stay_document, max_pages=3)
        self.assertEqual(result, {"registrationNumber": "0001015000000", "stayExpiresOn": "2027-07-27"})
