import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from main import app
from academic import extract_transcript, extract_topik
from recognition import UnreadableDocument


class AcademicExtractionTest(unittest.TestCase):
    def transcript(self, *lines):
        return extract_transcript([["성적증명서", *lines]])

    def test_latest_term_not_last_printed_row_or_cumulative_average(self):
        for blocks in (("2025년 1학기 평점평균 3.12", "2025년 2학기 평균평점: 4.24 / 4.5"),
                       ("2025년 2학기 평균평점: 4.24 / 4.5", "2025년 1학기 평점평균 3.12")):
            result = self.transcript(*blocks, "누적 평점평균 3.50")
            self.assertEqual(result, {"academicYear": 2025, "semester": "SECOND", "averageGpa": "4.24", "topikLevel": None})

    def test_term_order_across_year_and_season_and_zero_gpa(self):
        self.assertEqual(self.transcript("2025년 겨울학기 평균학점 4.0", "2026학년도 1학기", "평균 학점:", "0.00")["averageGpa"], "0.00")
        self.assertEqual(self.transcript("2026 하계학기 GPA: 4.2", "2026 1학기 GPA: 3.2")["semester"], "SUMMER")

    def test_never_falls_back_to_older_term_or_total(self):
        for lines in (("2025년 1학기 평균평점 3.0", "2025년 2학기 과목 성적 4.5"),
                      ("2025년 2학기", "누적 평점평균 4.24"),
                      ("2025년 2학기 평균평점 4.1", "평균학점 4.2"),
                      ("평점평균 4.24",), ("2025년 2학기 평균학점 -1.0",)):
            with self.subTest(lines=lines), self.assertRaises(UnreadableDocument):
                self.transcript(*lines)
        with self.assertRaises(UnreadableDocument):
            extract_transcript([["재학증명서", "2025년 2학기 평균평점 4.24"]])

    def test_topik_grade_is_not_total_score_or_exam_number(self):
        for level in range(1, 7):
            result = extract_topik([["TOPIK 성적증명서", "제99회", "총점: 250", f"등급: {level}급"]])
            self.assertEqual(result["topikLevel"], level)
            self.assertIsNone(result["averageGpa"])
        for lines in (("등급 7",), ("등급 0",), ("등급 3", "등급 4"), ("총점 250",)):
            with self.subTest(lines=lines), self.assertRaises(UnreadableDocument):
                extract_topik([["한국어능력시험 성적표", *lines]])

    def test_internal_routes_and_unreadable_errors(self):
        with patch("main.create_engine", return_value=object()), TestClient(app) as client:
            for kind, parser in (("transcript", extract_transcript), ("topik", extract_topik)):
                with patch("main.recognize_document", return_value={"topikLevel": 3}) as recognize:
                    result = client.post(f"/student/{kind}", content=b"%PDF-test", headers={"Content-Type": "application/pdf"})
                    self.assertEqual(result.status_code, 200)
                    self.assertEqual(recognize.call_args.args[3], parser)
                    self.assertEqual(result.headers["cache-control"], "no-store")
                with patch("main.recognize_document", side_effect=UnreadableDocument()):
                    self.assertEqual(client.post(f"/student/{kind}", content=b"test", headers={"Content-Type": "image/png"}).status_code, 422)
