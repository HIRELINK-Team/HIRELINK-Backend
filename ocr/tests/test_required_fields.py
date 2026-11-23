import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from main import app
from enrollment import extract_enrollment
from academic import extract_transcript, extract_topik
from student_document_errors import UnreadableStudentFields


class RequiredFieldsTest(unittest.TestCase):
    def test_enrollment_reports_all_unreadable_required_fields_without_requiring_campus(self):
        cases = [
            (["재학증명서", "2학년"], ["universityName"]),
            (["재학증명서", "학교명: 숭실대학교", "2026학년도 2학기"], ["gradeLevelOrSemesterCount"]),
            (["재학증명서"], ["universityName", "gradeLevelOrSemesterCount"]),
            (["재학증명서", "학교명: 숭실대학교", "1학년", "2학년"], ["gradeLevelOrSemesterCount"]),
        ]
        for lines, fields in cases:
            with self.subTest(lines=lines), self.assertRaises(UnreadableStudentFields) as raised:
                extract_enrollment([lines])
            self.assertEqual(raised.exception.fields, fields)
        result = extract_enrollment([["재학증명서", "학교명: 숭실대학교", "이수 학기 수: 3"]])
        self.assertIsNone(result["campusName"])
        self.assertIsNone(result["gradeLevel"])
        self.assertEqual(result["semesterCount"], 3)

    def test_transcript_does_not_substitute_older_or_cumulative_gpa(self):
        for lines in (["성적증명서", "2025년 1학기 평균학점 4.24", "2025년 2학기"],
                      ["성적증명서", "2025년 2학기", "누적 평점평균 4.24"],
                      ["성적증명서", "평균학점 4.24"]):
            with self.subTest(lines=lines), self.assertRaises(UnreadableStudentFields) as raised:
                extract_transcript([lines])
            self.assertEqual(raised.exception.fields, ["latestSemesterAverageGpa"])
        with self.assertRaises(UnreadableStudentFields) as raised:
            extract_topik([["TOPIK 성적표", "총점 250"]])
        self.assertEqual(raised.exception.fields, ["topikLevel"])

    def test_routes_preserve_field_identifiers_but_never_return_document_text(self):
        cases = [
            ("enrollment", ["재학증명서", "비공개 이름"], ["universityName", "gradeLevelOrSemesterCount"]),
            ("transcript", ["성적증명서", "2026년 1학기", "비공개 이름"], ["latestSemesterAverageGpa"]),
            ("topik", ["TOPIK 성적표", "총점 250", "비공개 이름"], ["topikLevel"]),
        ]
        with patch("main.create_engine", return_value=object()), TestClient(app) as client:
            for kind, lines, fields in cases:
                with self.subTest(kind=kind), patch("main.recognize_document",
                        side_effect=lambda engine, data, mime, extractor: extractor([lines])):
                    response = client.post(f"/student/{kind}", content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                    self.assertEqual(response.status_code, 422)
                    self.assertEqual(response.json(), {"code": "OCR_REQUIRED_FIELDS_UNREADABLE", "fields": fields})
                    self.assertEqual(response.headers["cache-control"], "no-store")
                    self.assertNotIn("비공개", response.text)
            with patch("main.recognize_document", side_effect=lambda engine, data, mime, extractor: extractor([["다른 서류"]])):
                response = client.post("/student/enrollment", content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                self.assertEqual(response.status_code, 422)
                self.assertEqual(response.json(), {"code": "OCR_FAILED"})
