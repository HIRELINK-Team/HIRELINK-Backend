from io import BytesIO
import unittest
from unittest.mock import Mock

from PIL import Image

from recognition import UnreadableDocument, recognize_document


class DocumentDecodingTest(unittest.TestCase):
    def test_png_jpeg_and_pdf_are_decoded_in_memory(self):
        engine = Mock()
        engine.predict.return_value = [{
            "rec_texts": ["등록번호: 123-45-67890"],
            "rec_polys": [[[0, 0], [90, 0], [90, 20], [0, 20]]],
        }]
        with Image.new("RGB", (100, 100), "white") as image:
            for fmt, content_type in [("PNG", "image/png"), ("JPEG", "image/jpeg"), ("PDF", "application/pdf")]:
                with self.subTest(format=fmt), BytesIO() as buffer:
                    image.save(buffer, format=fmt)
                    result = recognize_document(engine, buffer.getvalue(), content_type)
                    self.assertEqual(result, {"businessRegistrationNumber": "123-45-67890", "workplaceAddress": None})

    def test_corrupt_document_does_not_reach_model(self):
        engine = Mock()
        for content_type in ["image/png", "application/pdf"]:
            with self.subTest(type=content_type), self.assertRaises(UnreadableDocument):
                recognize_document(engine, b"invalid-document", content_type)
        engine.predict.assert_not_called()
