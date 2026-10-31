import re
import time
import warnings
from io import BytesIO


MAX_IMAGE_EDGE = 1600
NUMBER_LABEL = re.compile(r"(?:사\s*업\s*자\s*)?등\s*록\s*번\s*호\s*[:：]?")
NUMBER = re.compile(r"(?<!\d)(\d{3})\s*-?\s*(\d{2})\s*-?\s*(\d{5})(?!\d)")
ADDRESS_LABEL = re.compile(r"사\s*업\s*장\s*소\s*재\s*지\s*[:：]?")
NEXT_FIELD = re.compile(
    r"사\s*업\s*의?\s*종\s*류|업\s*태|종\s*목|개\s*업\s*연?\s*월\s*일|"
    r"성\s*명|대\s*표\s*자|생\s*년\s*월\s*일|법\s*인\s*등\s*록|"
    r"본\s*점|공\s*동\s*사\s*업\s*자|발\s*급\s*사\s*유|"
    r"상\s*호|전\s*자\s*세\s*금|주\s*류\s*판\s*매|사\s*업\s*자\s*단\s*위"
)


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
    addresses = set()
    for lines in pages:
        for index, line in enumerate(lines):
            label = NUMBER_LABEL.search(line)
            if label:
                candidate = line[label.end():]
                if not candidate.strip() and index + 1 < len(lines):
                    candidate = lines[index + 1]
                for match in NUMBER.finditer(candidate):
                    numbers.add("-".join(match.groups()))
            label = ADDRESS_LABEL.search(line)
            if label:
                parts = [line[label.end():]]
                for following in lines[index + 1:]:
                    if NUMBER_LABEL.search(following) or ADDRESS_LABEL.search(following):
                        break
                    next_field = NEXT_FIELD.search(following)
                    if next_field:
                        parts.append(following[:next_field.start()])
                        break
                    parts.append(following)
                address = NEXT_FIELD.split(" ".join(parts), maxsplit=1)[0]
                address = " ".join(address.strip(" :：").split())
                if address and len(address) <= 500:
                    addresses.add(address)
    if len(numbers) != 1:
        raise UnreadableDocument()
    return {
        "businessRegistrationNumber": next(iter(numbers)),
        "workplaceAddress": next(iter(addresses)) if len(addresses) == 1 else None,
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


def recognize_document(engine, data, content_type):
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
    return extract_registration(pages)
