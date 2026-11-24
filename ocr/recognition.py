import re
import time
import warnings
from io import BytesIO


MAX_IMAGE_EDGE = 1600
NUMBER_LABEL = re.compile(r"(?:사\s*업\s*자\s*)?등\s*록\s*번\s*호\s*[:：]?")
NUMBER = re.compile(r"(?<!\d)(\d{3})\s*-?\s*(\d{2})\s*-?\s*(\d{5})(?!\d)")



class UnreadableDocument(Exception):
    pass


def create_engine():
    from paddleocr import PaddleOCR

    return PaddleOCR(
        text_detection_model_name="PP-OCRv5_mobile_det",
        text_recognition_model_name="korean_PP-OCRv5_mobile_rec",
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        device="cpu",
        cpu_threads=2,
        enable_mkldnn=False,
    )


def extract_registration(pages):
    numbers = set()
    for lines in pages:
        for index, line in enumerate(lines):
            label = NUMBER_LABEL.search(line)
            if label:
                candidate = line[label.end():]
                if not candidate.strip() and index + 1 < len(lines):
                    candidate = lines[index + 1]
                for match in NUMBER.finditer(candidate):
                    numbers.add("-".join(match.groups()))
    if len(numbers) != 1:
        raise UnreadableDocument()
    return {
        "businessRegistrationNumber": next(iter(numbers)),
        "workplaceAddress": None,
    }


def reading_lines(result):
    boxes = []
    for text, polygon in zip(result["rec_texts"], result["rec_polys"]):
        xs = [float(point[0]) for point in polygon]
        ys = [float(point[1]) for point in polygon]
        boxes.append((sum(ys) / len(ys), min(xs), max(ys) - min(ys), text))
    rows = []
    for y, x, height, text in sorted(boxes):
        if rows and abs(y - rows[-1][0]) <= max(height, rows[-1][1]) * 0.6:
            rows[-1][2].append((x, text))
        else:
            rows.append((y, height, [(x, text)]))
    return [" ".join(text for _, text in sorted(row[2])) for row in rows]


def recognize_document(engine, data, content_type, extractor=extract_registration, max_pages=None):
    import numpy as np
    import pypdfium2 as pdfium
    from PIL import Image, ImageOps, UnidentifiedImageError

    deadline = time.monotonic() + 30

    def read_image(image):
        if time.monotonic() > deadline:
            raise TimeoutError()
        with image.convert("RGB") as rgb:
            rgb.thumbnail((MAX_IMAGE_EDGE, MAX_IMAGE_EDGE))
            pixels = np.array(rgb)[:, :, ::-1].copy()
        try:
            results = list(engine.predict(pixels))
            return [line for result in results for line in reading_lines(result)]
        finally:
            pixels.fill(0)

    pages = []
    try:
        if content_type == "application/pdf":
            with pdfium.PdfDocument(data) as document:
                if max_pages is not None and not 1 <= len(document) <= max_pages:
                    raise UnreadableDocument()
                for index in range(len(document)):
                    page = document[index]
                    try:
                        scale = min(2, MAX_IMAGE_EDGE / max(page.get_size()))
                        bitmap = page.render(scale=scale)
                        try:
                            with bitmap.to_pil() as image:
                                pages.append(read_image(image))
                        finally:
                            bitmap.close()
                    finally:
                        page.close()
        else:
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                with Image.open(BytesIO(data)) as image:
                    if image.format not in {"PNG", "JPEG"}:
                        raise UnreadableDocument()
                    with ImageOps.exif_transpose(image) as oriented:
                        pages.append(read_image(oriented))
    except (UnidentifiedImageError, OSError, ValueError, pdfium.PdfiumError,
            Image.DecompressionBombWarning, Image.DecompressionBombError):
        raise UnreadableDocument() from None
    if time.monotonic() > deadline:
        raise TimeoutError()
    return extractor(pages)
