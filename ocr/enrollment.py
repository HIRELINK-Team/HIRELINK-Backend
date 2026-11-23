import re
import unicodedata

from recognition import UnreadableDocument
from student_document_errors import UnreadableStudentFields


def extract_enrollment(pages):
    lines = [" ".join(unicodedata.normalize("NFKC", line).split()) for page in pages for line in page]
    text = "\n".join(lines)
    if not re.search(r"재\s*학\s*증\s*명\s*서|certificate\s+of\s+(?:enrollment|registration)", text, re.I):
        raise UnreadableDocument()
    years, semesters, schools, campuses = set(), set(), set(), set()
    # OCR이 학사 일정을 여러 줄로 나누어도 개인 학기 수로 취급하지 않는다.
    personal = re.sub(r"\d{4}\s*(?:학년도|년도|년|academic\s+year)\s*(?:제\s*)?\d+\s*학기", "", text, flags=re.I)
    personal = re.sub(r"(?:학년도|academic\s+year)\s*[:：]?\s*\d{4}\s*(?:제\s*)?\d+\s*학기", "", personal, flags=re.I)
    personal = re.sub(r"academic\s+year\s*[:：]?\s*\d{4}(?:\s*[-/]\s*\d{2,4})?", "", personal, flags=re.I)
    for pattern in (r"(?<!\d)(\d+)\s*학\s*년(?!\s*도)", r"학\s*년(?!\s*도)\s*[:：]?\s*(\d+)(?!\d)",
                    r"\byear\s*[:：]\s*(\d+)\b", r"\b(\d+)(?:st|nd|rd|th)?\s+year\b"):
        years.update(int(value) for value in re.findall(pattern, personal, re.I))
    for pattern in (r"(?<!\d)(\d+)\s*학\s*기", r"(?:이수|등록|재학|수료|현재|총)\s*학\s*기\s*(?:수)?\s*[:：]?\s*(\d+)(?!\d)",
                    r"(?:completed|current|registered|total)\s+semesters?\s*[:：]?\s*(\d+)\b"):
        semesters.update(int(value) for value in re.findall(pattern, personal, re.I))
    for index, line in enumerate(lines):
        school = re.match(r"(?:대학교|대학명|학교명|학교|university)\s*[:：]\s*(.*)$", line, re.I)
        if school:
            value = school.group(1) or (lines[index + 1] if index + 1 < len(lines) else "")
            if value:
                schools.add(value)
        elif re.fullmatch(r"[가-힣A-Za-z0-9 ·.\-]+(?:대학교|University)(?:\s+[가-힣A-Za-z]+캠퍼스)?(?:\s*총장)?", line, re.I):
            schools.add(re.sub(r"\s*총장$", "", line))
        campus = re.match(r"(?:캠퍼스|campus)\s*[:：]\s*(.+)$", line, re.I)
        if campus:
            campuses.add(re.sub(r"\s*캠퍼스$", "", campus.group(1)))
        for value in re.findall(r"([가-힣A-Za-z]+)\s*캠퍼스", line):
            campuses.add(value)
    missing = []
    if len(schools) != 1 or any(not school.strip() or len(school) > 150 for school in schools):
        missing.append("universityName")
    if len(years) > 1 or (years and min(years) < 1) or (not years and (len(semesters) != 1 or min(semesters) < 1)):
        missing.append("gradeLevelOrSemesterCount")
    if missing:
        raise UnreadableStudentFields(*missing)
    if len(campuses) > 1:
        raise UnreadableDocument()
    year = next(iter(years)) if years else None
    semester = next(iter(semesters)) if not years else None
    school = next(iter(schools))
    campus = next(iter(campuses)) if campuses else None
    if campus and len(campus) > 150:
        raise UnreadableDocument()
    return {"universityName": school, "campusName": campus, "gradeLevel": year, "semesterCount": semester}
