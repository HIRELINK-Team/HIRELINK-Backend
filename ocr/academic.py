import re
import unicodedata
from decimal import Decimal, InvalidOperation
from recognition import UnreadableDocument


def normalized(pages):
    return "\n".join(" ".join(unicodedata.normalize("NFKC", line).split()) for page in pages for line in page)


def extract_transcript(pages):
    text = normalized(pages)
    if not re.search(r"성\s*적\s*증\s*명\s*서|academic\s+transcript|transcript\s+of", text, re.I):
        raise UnreadableDocument()
    headers = list(re.finditer(r"(?<!\d)(20\d{2}|19\d{2})\s*(?:학년도|년도|년)?\s*[-./:]?\s*(?:제\s*)?(1|2|여름|겨울|하계|동계)\s*(?:계절\s*)?학기", text))
    if not headers:
        raise UnreadableDocument()
    terms = {}
    rank = {"1": 1, "여름": 2, "하계": 2, "2": 3, "겨울": 4, "동계": 4}
    for i, header in enumerate(headers):
        key = (int(header[1]), rank[header[2]])
        block = text[header.end():headers[i+1].start() if i+1 < len(headers) else len(text)]
        # 누적/전체 평점은 최신 학기 평점으로 사용하지 않는다.
        block = re.split(r"누적|총\s*평점|전체|cumulative|overall", block, flags=re.I)[0]
        values = re.findall(r"(?:평점\s*평균|평균\s*평점|평균\s*학점|(?:semester\s+)?GPA)\s*[:：]?\s*(\d+(?:\.\d{1,4})?)(?![\d.])", block, re.I)
        terms.setdefault(key, []).extend(values)
    key = max(terms)
    values = set(terms[key])
    if len(values) != 1:
        raise UnreadableDocument()
    try:
        value = Decimal(next(iter(values)))
        if value < 0 or value >= 1000000:
            raise UnreadableDocument()
    except InvalidOperation:
        raise UnreadableDocument()
    return {"academicYear": key[0], "semester": {1:"FIRST",2:"SUMMER",3:"SECOND",4:"WINTER"}[key[1]], "averageGpa": str(value), "topikLevel": None}


def extract_topik(pages):
    text = normalized(pages)
    if not re.search(r"TOPIK|한국어\s*능력\s*시험", text, re.I) or not re.search(r"성적|score|result|certificate", text, re.I):
        raise UnreadableDocument()
    values = set(int(v) for v in re.findall(r"(?:등급|급수|level|grade)\s*[:：]?\s*([0-9]+)(?:\s*급)?", text, re.I))
    if not values:
        values = set(int(v) for v in re.findall(r"(?<!\d)([0-9]+)\s*급", text))
    if len(values) != 1 or not 1 <= next(iter(values)) <= 6:
        raise UnreadableDocument()
    return {"academicYear": None, "semester": None, "averageGpa": None, "topikLevel": next(iter(values))}
