import unittest

from recognition import UnreadableDocument, extract_registration, reading_lines


class RegistrationExtractionTest(unittest.TestCase):
    def test_number_and_multiline_address_exclude_other_fields(self):
        result = extract_registration([[
            "사업자등록증", "등 록 번 호 : 123 - 45 - 67890",
            "성 명 : 테스트 생년월일 : 2000.01.01",
            "사 업 장 소 재 지 : 테스트시 테스트로 10", "2층 (테스트동)",
            "사업의 종류 : 업태 서비스 종목 테스트", "발급 사유 : 신규",
        ]])
        self.assertEqual(result, {
            "businessRegistrationNumber": "123-45-67890",
            "workplaceAddress": "테스트시 테스트로 10 2층 (테스트동)",
        })

    def test_next_line_labels_and_same_line_next_field(self):
        result = extract_registration([[
            "사업자등록번호", "1234567890", "사업장 소재지", "테스트 주소 업태 : 서비스",
        ]])
        self.assertEqual(result["workplaceAddress"], "테스트 주소")

    def test_missing_or_conflicting_address_is_optional(self):
        for pages in [
            [["등록번호: 1234567890", "성명: 테스트"]],
            [["등록번호: 1234567890", "사업장소재지", "사업의 종류: 테스트"]],
            [["등록번호: 1234567890", "사업장소재지: 첫 주소"], ["사업장소재지: 다른 주소"]],
        ]:
            with self.subTest(pages=pages):
                self.assertIsNone(extract_registration(pages)["workplaceAddress"])

    def test_missing_or_ambiguous_number_is_rejected(self):
        for pages in [[], [["주류판매신고번호: 1234567890"]],
                      [["등록번호: 1234567890"], ["등록번호: 9876543210"]]]:
            with self.subTest(pages=pages), self.assertRaises(UnreadableDocument):
                extract_registration(pages)
        self.assertEqual(extract_registration([["등록번호: 1234567890"]] * 2)["businessRegistrationNumber"], "123-45-67890")

    def test_boxes_are_grouped_into_reading_order(self):
        result = {"rec_texts": ["주소", "1234567890", "등록번호:", "사업장소재지:"],
                  "rec_polys": [
                      [[100, 40], [150, 40], [150, 60], [100, 60]],
                      [[100, 1], [150, 1], [150, 21], [100, 21]],
                      [[0, 0], [90, 0], [90, 20], [0, 20]],
                      [[0, 41], [90, 41], [90, 61], [0, 61]],
                  ]}
        self.assertEqual(reading_lines(result), ["등록번호: 1234567890", "사업장소재지: 주소"])
