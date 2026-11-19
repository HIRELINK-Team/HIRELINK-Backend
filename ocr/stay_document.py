"""Read the subject's current fields; never infer validity from history dates."""
import re

from recognition import UnreadableDocument
from registration import dates, single


TITLE = re.compile(r"외\s*국\s*인\s*등\s*록\s*사\s*실\s*증\s*명|CERTIFICATE\s+OF\s+(?:ALIEN|FOREIGN\s+RESIDENT)\s+REGISTRATION", re.I)
IDENTITY = r"외\s*국\s*인\s*등\s*록\s*번\s*호|(?:Alien|Foreign\s+Resident)\s+Registration\s*(?:No\.?|Number)"
PERIOD = r"체\s*류\s*기\s*간(?:\s*만\s*료\s*일)?|Period\s+of\s+(?:Sojourn|Stay)"
OTHER_FIELDS = r"성\s*명|Full\s+Name|등록\s*일자|Date\s+of\s+Registration|국가\s*/\s*지역|국적|Country|체류\s*자격|Status\s+of\s+Sojourn|여권\s*번호|Passport|생년월일|Date\s+of\s+Birth|발급일|Date\s+of\s+Issuance"
FIELDS = re.compile("|".join([IDENTITY, PERIOD, OTHER_FIELDS]), re.I)
SECTION_END = re.compile(r"등록\s*체류지|Address|이전의|Previous\s+Registration|Expired|용도|Purpose|신청인|Applicant|변경\s*사항|Record\s+of|동거\s*가족|Family\s+Member|출입국관리법|Immigration\s+Act", re.I)
DATE_TEXT = r"[0-9]{4}\s*[.\-/]\s*[0-9]{1,2}\s*[.\-/]\s*[0-9]{1,2}"
PERIOD_VALUE = re.compile(rf"{DATE_TEXT}\s*\.?(?:\s*(?:~|～|–|to)\s*{DATE_TEXT}\s*\.?)?", re.I)


def field(lines, pattern):
    label = re.compile(pattern, re.I)
    values = []
    for index, line in enumerate(lines):
        match = label.search(line)
        if not match:
            continue
        value = line[match.end():].strip(" :：/()")
        duplicate = label.match(value)
        if duplicate:
            value = value[duplicate.end():].strip(" :：/()")
        # Multiple column headings without a value cannot be safely paired
        # with the next flattened OCR row.
        if not value and index + 1 < len(lines):
            value = lines[index + 1].strip()
            translated = label.fullmatch(value.strip(" :：/()"))
            if translated and index + 2 < len(lines):
                value = lines[index + 2].strip()
        value = FIELDS.split(value, maxsplit=1)[0].strip(" :：/()")
        if not value:
            raise UnreadableDocument()
        values.append(value)
    return single(values)


def extract_stay_document(pages):
    if not 1 <= len(pages) <= 3:
        raise UnreadableDocument()
    numbers, expiries = [], []
    for lines in pages:
        if not TITLE.search(" ".join(lines[:8])):
            raise UnreadableDocument()
        subject = []
        started = False
        for line in lines:
            if not started and re.search(IDENTITY, line, re.I):
                started = True
            if started:
                if SECTION_END.search(line):
                    break
                subject.append(line)
        number = field(subject, IDENTITY)
        if not re.fullmatch(r"[0-9]{6}\s*-?\s*[5-8][0-9]{6}", number):
            raise UnreadableDocument()
        period = field(subject, PERIOD)
        if not PERIOD_VALUE.fullmatch(period):
            raise UnreadableDocument()
        values = dates(period)
        if len(values) not in (1, 2) or (len(values) == 2 and values[1] < values[0]):
            raise UnreadableDocument()
        numbers.append(re.sub(r"[\s-]", "", number))
        expiries.append(values[-1])
    return {"registrationNumber": single(numbers), "stayExpiresOn": single(expiries).isoformat()}
