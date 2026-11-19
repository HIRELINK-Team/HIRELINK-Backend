"""Conservative extraction: ambiguous or unlabelled fields require another capture."""
import re
from datetime import date

from recognition import UnreadableDocument


DATE = re.compile(r"(?<!\d)(\d{4})\s*[.\-/]\s*(\d{1,2})\s*[.\-/]\s*(\d{1,2})(?!\d)")
NUMBER = re.compile(r"(?<!\d)(\d{6})\s*-?\s*([5-8]\d{6})(?!\d)")
LABELS = {
    "name": r"성\s*명|(?:Full\s*)?Name",
    "nationality": r"국\s*적(?:\s*/\s*지\s*역)?|Nationality|Country(?:\s*/\s*Region)?",
    "visaCode": r"체\s*류\s*자\s*격|Status\s*of\s*Stay|Visa(?:\s*Type)?",
    "issueDate": r"발\s*급\s*(?:일\s*자|연\s*월\s*일|일)|Date\s*of\s*Issue|Issue\s*Date",
}
ALL_LABELS = re.compile("|".join(LABELS.values()) + r"|외\s*국\s*인\s*등\s*록\s*번\s*호|Registration\s*(?:No\.?|Number)", re.I)
EXPIRY_LABEL = re.compile(r"체\s*류\s*(?:기\s*간\s*)?만\s*료\s*일|Expiry\s*Date|Expiration\s*Date|Period\s*of\s*Stay", re.I)
TABLE_HEADER = re.compile(r"허\s*가\s*(?:일\s*자|일)|Date\s*of\s*Permission", re.I)
END_HEADER = re.compile(r"만\s*료\s*(?:일\s*자|일)|Date\s*of\s*Expiry|Expiry\s*Date", re.I)
SECTION_END = re.compile(r"체\s*류\s*지|Address|일\s*련\s*번\s*호|Serial|발\s*급", re.I)
SERIAL_LABEL = re.compile(r"일\s*련\s*번\s*호|Serial\s*(?:No\.?|Number)|Card\s*(?:No\.?|Number)", re.I)


def single(values):
    values = set(values)
    if len(values) != 1:
        raise UnreadableDocument()
    return next(iter(values))


def dates(text):
    try:
        return [date(*(int(part) for part in match.groups())) for match in DATE.finditer(text)]
    except ValueError:
        raise UnreadableDocument() from None


def labelled(lines, pattern):
    label = re.compile(pattern, re.I)
    values = []
    for index, line in enumerate(lines):
        match = label.search(line)
        if match:
            value = line[match.end():].strip(" :：/()")
            # Korean and English labels may be printed together.
            duplicate = label.match(value)
            if duplicate:
                value = value[duplicate.end():].strip(" :：/()")
            if not value and index + 1 < len(lines):
                value = lines[index + 1]
            value = ALL_LABELS.split(value, maxsplit=1)[0].strip(" :：/()")
            if value:
                values.append(value)
    return single(values)


def extract_front(pages):
    if len(pages) != 1:
        raise UnreadableDocument()
    lines = pages[0]
    title = " ".join(lines)
    if not re.search(r"외\s*국\s*인\s*등\s*록\s*증|(?:Alien\s*Registration|Residence)\s*Card", title, re.I):
        raise UnreadableDocument()
    number = single("".join(match.groups()) for match in NUMBER.finditer(title))
    name = labelled(lines, LABELS["name"])
    nationality = labelled(lines, LABELS["nationality"])
    visa = labelled(lines, LABELS["visaCode"])
    if not re.fullmatch(r"D\s*-\s*[24](?:\s*-\s*[1-8])?", visa, re.I):
        raise UnreadableDocument()
    issued = single(dates(labelled(lines, LABELS["issueDate"])))
    return {"name": name, "nationality": nationality,
            "visaCode": re.sub(r"\s", "", visa).upper(),
            "registrationNumber": number, "issueDate": issued.isoformat()}


def extract_back(pages):
    if len(pages) != 1:
        raise UnreadableDocument()
    lines = pages[0]
    expiries, serials, rows = [], [], []
    in_table = False
    for index, line in enumerate(lines):
        if TABLE_HEADER.search(line):
            header = line + (" " + lines[index + 1] if index + 1 < len(lines) else "")
            if not END_HEADER.search(header):
                raise UnreadableDocument()
            in_table = True
            continue
        if SECTION_END.search(line):
            in_table = False
        serial = SERIAL_LABEL.search(line)
        if serial:
            value = line[serial.end():].strip(" :：/()")
            if not value and index + 1 < len(lines):
                value = lines[index + 1].strip()
            if not re.fullmatch(r"\d{11}", value):
                raise UnreadableDocument()
            serials.append(value)
        if in_table:
            values = dates(line)
            if values:
                if len(values) != 2 or values[1] < values[0]:
                    raise UnreadableDocument()
                rows.append(tuple(values))
            elif re.search(r"\d", line):
                raise UnreadableDocument()
        else:
            expiry = EXPIRY_LABEL.search(line)
            if expiry:
                value = line[expiry.end():]
                if not value.strip(" :：/()") and index + 1 < len(lines):
                    value = lines[index + 1]
                values = dates(value)
                if len(values) not in (1, 2) or (len(values) == 2 and values[1] < values[0]):
                    raise UnreadableDocument()
                expiries.append(values[-1])
    if rows:
        latest_permission = max(start for start, _ in rows)
        expiries.append(single(end for start, end in rows if start == latest_permission))
    expiry = single(expiries)
    serial = single(serials) if serials else None
    return {"stayExpiresOn": expiry.isoformat(), "serialNumber": serial}
