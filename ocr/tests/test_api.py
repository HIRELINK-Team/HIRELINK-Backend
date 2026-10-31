import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from main import app
from recognition import UnreadableDocument


class OcrApiTest(unittest.TestCase):
    def setUp(self):
        self.engine_patch = patch("main.create_engine", return_value=object())
        self.engine_patch.start()
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.engine_patch.stop()

    def test_binary_request_returns_only_selected_fields(self):
        result = {"businessRegistrationNumber": "123-45-67890", "workplaceAddress": None}
        with patch("main.recognize_document", return_value=result) as recognize:
            response = self.client.post("/recognize", content=b"test", headers={"Content-Type": "image/png"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), result)
        self.assertEqual(response.headers["Cache-Control"], "no-store")
        self.assertEqual(recognize.call_args.args[1:], (b"test", "image/png"))

    def test_invalid_upload_does_not_reach_engine(self):
        with patch("main.recognize_document") as recognize:
            for data, content_type, status in [(b"test", "text/plain", 415),
                                                (b"", "image/png", 422),
                                                (b"0" * (5 * 1024 * 1024 + 1), "image/png", 413)]:
                response = self.client.post("/recognize", content=data, headers={"Content-Type": content_type})
                self.assertEqual(response.status_code, status)
            recognize.assert_not_called()

    def test_errors_are_sanitized_and_next_request_can_run(self):
        for error, status in [(UnreadableDocument(), 422), (RuntimeError("document-content"), 503)]:
            with patch("main.recognize_document", side_effect=error):
                response = self.client.post("/recognize", content=b"test", headers={"Content-Type": "application/pdf"})
                self.assertEqual(response.status_code, status)
                self.assertEqual(response.json(), {"code": "OCR_FAILED"})
        self.assertEqual(self.client.get("/health").status_code, 200)
        self.assertEqual(self.client.get("/docs").status_code, 404)
