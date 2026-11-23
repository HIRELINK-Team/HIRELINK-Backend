# 나의 근무 가능 조건

## 조회 API

`GET /api/students/me/working-conditions` — JWT의 유학생 본인만 조회한다. 응답은 `Cache-Control: no-store`다.

기존 배지 서비스로 현재 유효 여부를 검사한다. 발급 시각이 있어도 체류기간이 만료되거나 필수 서류가 최종 등록되지 않았으면 403이다. 만료일 당일까지 유효하고 다음 날부터 차단하며, 갱신 최종 성공·배지 재발급 후 다시 허용한다. 회수되어 발급 시각이 지워진 경우도 차단한다.

온보딩에서 선택한 학업과정과 조합한 세부 비자 코드를 사용한다. 등록증 OCR이 D-2-2 등의 세부 코드를 추출한다고 가정하지 않는다. 대학교·캠퍼스·학년, 가장 최근 학기 평균 학점, TOPIK 등급은 최종 등록된 세 서류에서 읽는다. 초안은 사용하지 않는다.

### 응답

- `basicInformation`: visaCode, studyProgram, gradeLevel, universityName, campusName, topikLevel, previousSemesterGpa.
- `calculatedOn`: 저장 결과를 계산한 한국 날짜. 본인 조회 시에도 최신 기준을 확인하고 갱신한다.
- `accreditationEffectiveFrom`: 적용한 인증대학 목록의 시작일.
- `estimate`: requiredTopikLevel, topikRequirementMet, accreditedUniversity, excellentGrades, excellentKorean, additionalWeekdayHours, combinedWeeklyLimitHours, termWeekdays, weekendsAndHolidays, vacations.
- `notice`: 예상 기준이며 시간제취업 허가 완료가 아니라는 안내.

각 기간은 `{ "type": "...", "hours": ... }` 형태다.

| type | hours | 의미 |
| --- | --- | --- |
| WEEKDAY_LIMIT | 25/30/35 | 학기 중 주중 한도 |
| SHARED_WEEKLY_LIMIT | null | 모든 기간이 combinedWeeklyLimitHours 하나를 공유. 별도 가산하지 않음 |
| NO_TIME_CAP | null | 서비스 기준 시간 상한 없음 |

기본요건 미충족자는 세 기간 모두 SHARED_WEEKLY_LIMIT이고 combinedWeeklyLimitHours는 10 또는 15다. 충족자는 combinedWeeklyLimitHours가 null이고, 주중 한도와 주말·공휴일·방학의 NO_TIME_CAP을 함께 제공한다. null을 0시간으로 표시하지 않는다.

### 계산표

| 과정·학년 | TOPIK 기본요건 | 미충족 공통 주당 한도 | 충족 주중 | 충족+우대 주중 |
| --- | --- | --- | --- | --- |
| 전문학사 | 3급 | 10 | 25 | 30 |
| 학사·교환학생 1~2학년 | 3급 | 10 | 25 | 30 |
| 학사·교환학생 3~4학년 | 4급 | 10 | 25 | 30 |
| 석사·박사 | 4급 | 15 | 30 | 35 |

지원 학년은 1~4학년이다. 우대는 인증대학, 평균 학점 4.0 이상, TOPIK 5급 이상 중 하나 이상이면 충족한다. 만점 환산은 하지 않는다. 기본요건 충족자에게만 최대 5시간을 추가하며 조건 수만큼 합산하지 않는다.

시간제취업 허가 신청·허가 여부·허가 상태, 학적 상태, KIIP, 학교별 학사일정, 현재 학기/방학 판별은 제공하거나 계산에 사용하지 않는다.

### 오류

기존 `ErrorResponse(code, message)` 형식을 사용한다.

| HTTP | code / 원인 |
| --- | --- |
| 401 | UNAUTHORIZED: 인증 필요 |
| 403 | FORBIDDEN: 유학생 아님 / WITHDRAWN_ACCOUNT: 탈퇴 계정 |
| 403 | ACTIVE_BADGE_REQUIRED: 현재 배지 없음 |
| 404 | 기존 프로필 없음 오류 |
| 409 | WORKING_CONDITIONS_DATA_INCOMPLETE: 학업과정·세부 비자 불일치, 최종 서류값 누락/지원 범위 초과 |
| 503 | ACCREDITED_UNIVERSITY_LIST_UNAVAILABLE: 적용 목록 없음 또는 항목 수 불일치 |

## 인증대학 목록과 갱신

최초 목록은 일반대학 135, 전문대학 33, 대학원대학 16개 항목이며, **2026-11-23부터 적용**한다. 원문은 `docs/reference/accredited-universities-2026-11-23.json`에 보존한다. 캠퍼스와 중복 학교를 통합한 비교 이름은 173개다. 이는 184개 원문 항목 누락이 아니다.

학교명은 NFKC 정규화, 괄호 표기 제거, 학교명 뒤 캠퍼스 표기 제거, 공백 제거 후 비교한다. `(본교)`와 캠퍼스를 구분하지 않는다. 캠퍼스 추출값은 응답에 보존하되 판정에는 사용하지 않는다. 유사도 매칭이나 학교명 별칭 추정은 하지 않는다.

목록 변경은 관리자 API나 자동 수집 없이 **새 Flyway 마이그레이션으로 수동 관리**한다.

1. 새 적용 시작일과 출처, 전체 항목 수를 정한다.
2. `accredited_university_catalog`에 새 적용일 행을 추가한다.
3. 해당 날짜의 전체 목록을 `accredited_university_entry`에 저장한다. category/listed_name 원문과 `AccreditedUniversityService.normalize` 규칙의 normalized_name을 함께 넣는다.
4. 전체 항목 수와 정규화 결과를 CI에서 검증한다. 변경분만 넣지 않는다.
5. 배포된 V34나 이전 목록을 덮어쓰지 않는다. 기존 적용일 버전을 유지하고, 새 목록은 적용일 전까지 사용하지 않는다.

한국 날짜 이하의 가장 최근 적용일을 선택한다. 최종 서류와 적용 목록으로 계산한 결과를 유학생 프로필에 저장한다. 실패·중단된 업로드는 기존 최종 값과 계산 결과를 유지한다. 목록 변경은 배지·서류 완성률을 변경하지 않는다. 인증대학에서 제외되어도 성적/한국어 우대가 있으면 +5시간을 유지한다.

V34는 독립된 목록 테이블 2개와 최초 데이터를 추가한다. 기존 회원·서류 테이블과 독립적으로 관리한다.

## 계산 결과 저장과 내부 연동

V40은 `student_profile.working_conditions` JSONB 컬럼을 추가한다. 기존 프로필 값은 유지하며 새 컬럼의 초기값은 null이다. 저장 JSON은 위 `StudentWorkingConditionsResponse`와 동일한 구조다. JPA에서는 `StudentProfile.getWorkingConditions()`로 읽을 수 있다.

- 배지 발급·재발급 시 최종 서류로 계산하여 같은 트랜잭션에서 저장한다. 이미 배지가 있는 사용자의 서류 최종 교체도 이 경로에서 저장값을 갱신한다.
- 계산 또는 저장이 실패하면 서류 확정·프로필·배지 변경 전체가 롤백된다. 추출 미리보기는 저장 결과를 바꾸지 않는다.
- 본인 조회는 기존 현재 배지 검사를 통과한 뒤 최신 기준으로 계산·저장하여 응답한다. 저장값이 있어도 현재 배지가 없으면 403이다.
- 자동 갱신은 기존 배지 발급 사용자에게 초기 저장값을 채우고 인증대학 기준 변경을 반영한다. 100명씩 조회하며 사용자별 별도 트랜잭션으로 처리한다. 한 사용자의 실패는 다른 사용자의 갱신을 막지 않고 다음 실행에서 재시도한다.
- 기본 갱신 간격과 최초 실행 대기는 각각 60초다. `student-working-conditions.refresh-enabled`(기본 true), `student-working-conditions.refresh-delay`, `student-working-conditions.refresh-initial-delay`로 조정한다. 목록 적용일에 갱신 작업이 순차 반영하며, 본인 조회는 갱신 작업을 기다리지 않고 최신 기준을 적용한다.
- 자동 갱신은 현재 배지 조건을 충족하는 사용자만 처리한다. 휴면 중에도 복구 기한 전에는 값을 갱신할 수 있지만, 정확히 탈퇴 신청 후 30일이 지났거나 최종 탈퇴한 사용자는 제외한다. 이 처리는 외부 열람 권한을 부여하지 않는다.

저장값의 존재는 현재 배지 보유·열람 권한·서비스 지원 대상·시간제취업 허가 완료를 보장하지 않는다. 외부 응답에 연결하는 기능은 자체 권한 검사 후 사용해야 한다. 다른 조회 기능에서 최신 기준을 즉시 확인해야 할 때는 회원 행을 잠근 동일 트랜잭션 안에서 `StudentWorkingConditionsSnapshotService.refresh(profile)`를 호출할 수 있다. 이 내부 메서드에는 인증·매칭 권한 검사가 포함되지 않는다.

## 범위와 검증

이 API는 유학생 본인의 예상 근무시간을 제공한다. 사업주 조회와 추천·지원 기능에는 연결되어 있지 않다.

단위 테스트는 과정/학년 경계, 우대 조합/최대 5시간, 공통 한도, 학교명 정규화와 자동 갱신의 실패 격리·재시도를 검증한다. 통합 테스트는 현재 배지/만료/갱신, 본인 권한, 서류 최종 확정 시 저장과 실패 롤백, 기존 사용자 자동 갱신, 적용일별 목록, 목록 변경의 독립성 및 V33→V34·V39→V40 데이터 보존을 검증한다. 실행은 GitHub CI에서만 수행한다. 실제 외부 OCR 호출·운영 DB 배포는 이 테스트에 포함되지 않는다.
