import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from main import app
from enrollment import extract_enrollment
from recognition import UnreadableDocument


class EnrollmentExtractionTest(unittest.TestCase):
    def extract(self, *lines):
        return extract_enrollment([["재학증명서", "학교명: 숭실대학교", *lines]])

    def test_explicit_year_wins_over_semesters(self):
        for year in range(1, 6):
            result = self.extract(f"{year}학년", "이수 학기 수: 9")
            self.assertEqual(result["gradeLevel"], year)
            self.assertIsNone(result["semesterCount"])

    def test_current_and_completed_semesters_are_not_incremented(self):
        for count in range(1, 10):
            for label in ("현재", "이수", "등록"):
                with self.subTest(count=count, label=label):
                    result = self.extract(f"{label} 학기 수: {count}")
                    self.assertEqual(result["semesterCount"], count)
                    self.assertIsNone(result["gradeLevel"])
            self.assertEqual(self.extract(f"제 {count} 학기 재학 중")["semesterCount"], count)

    def test_calendar_semester_is_not_personal_semester_count(self):
        for calendar in ("2026학년도 2학기", "2026 학년도 제 2 학기", "학년도: 2026 2학기"):
            with self.subTest(calendar=calendar):
                with self.assertRaises(UnreadableDocument):
                    self.extract(calendar)
                result = self.extract(calendar, "이수 학기 수: 7")
                self.assertEqual(result["semesterCount"], 7)

    def test_campus_and_separate_field_value(self):
        result = extract_enrollment([["재 학 증 명 서", "학교명:", "숭실대학교", "캠퍼스: 서울", "학년: 3"]])
        self.assertEqual(result, {"universityName": "숭실대학교", "campusName": "서울", "gradeLevel": 3, "semesterCount": None})
        with self.assertRaises(UnreadableDocument):
            self.extract("2026학년도", "2학기")
        self.assertEqual(self.extract("2026학년도", "2학기", "이수 학기 수:", "5")["semesterCount"], 5)
        self.assertEqual(self.extract("3학년", "캠퍼스: 서울캠퍼스")["campusName"], "서울")

    def test_rejects_missing_conflicting_invalid_or_wrong_document(self):
        for lines in (("학년 0",), ("1학년", "2학년"), ("이수 학기 수: 3", "등록 학기 수: 4"),
                      ("학년도: 2026",), ("2026학년도 2학기",), ("학기 수: 0",)):
            with self.subTest(lines=lines), self.assertRaises(UnreadableDocument):
                self.extract(*lines)
        with self.assertRaises(UnreadableDocument):
            extract_enrollment([["성적증명서", "학교명: 숭실대학교", "3학년"]])

    def test_internal_endpoint_uses_enrollment_parser_and_does_not_return_raw_text(self):
        with patch("main.create_engine", return_value=object()), TestClient(app) as client:
            expected = self.extract("2학년")
            with patch("main.recognize_document", return_value=expected) as recognize:
                response = client.post("/student/enrollment", content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json(), expected)
                self.assertEqual(recognize.call_args.args[3], extract_enrollment)
                self.assertEqual(response.headers["cache-control"], "no-store")
            with patch("main.recognize_document", side_effect=UnreadableDocument()):
                self.assertEqual(client.post("/student/enrollment", content=b"test", headers={"Content-Type": "image/png"}).status_code, 422)
