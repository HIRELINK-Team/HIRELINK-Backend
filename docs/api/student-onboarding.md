# 유학생 온보딩 일괄 저장 · 완료

`POST /api/students/me/onboarding`은 전화번호 인증으로 계정을 만든 뒤 마지막 **마치기**에서 호출한다. 로그인한 본인의 액세스 토큰을 사용하며, 회원 연결 등록증 인증 기록이 필요하다. LOCAL 테스트 계정은 기존 프로필 · 경력 · 구직조건 API를 사용한다.

## 상태 조회와 재진입

`GET /api/users/me/onboarding`은 계정 생성 또는 재로그인 후 액세스 토큰으로 호출한다. 기존 로그인 · 가입 응답의 필드는 변경하지 않는다. 계정 생성 전 가입 쿠키만 있는 요청은 401이며, 회원이 생성된 뒤부터 이 조회를 사용한다.

| 대상 | status | nextStep | completedAt |
| --- | --- | --- | --- |
| 등록증 인증 및 생년월일 보존, 완료 시각 없음 | `REQUIRED` | `CURRENT_REGION` | `null` |
| 이전 가입에서 생년월일 미보존, 완료 시각 없음 | `REQUIRED` | `BASIC_INFORMATION` | `null` |
| 등록증 인증을 거친 유학생, 완료 시각 있음 | `COMPLETED` | `NONE` | 최초 완료 시각 |
| 등록증 인증 기록이 없는 LOCAL · 기존 가입 회원, 사업주 | `NOT_APPLICABLE` | `EXISTING_FLOW` | `null` |

```json
{"userId":1,"role":"STUDENT","status":"REQUIRED","nextStep":"CURRENT_REGION","completedAt":null}
```

- `REQUIRED/CURRENT_REGION`이면 거주지역부터 다시 입력한다. 이전 가입에서 생년월일을 보존하지 않은 회원의 `BASIC_INFORMATION`은 생년월일부터 입력한다. 등록증 · 전화번호 인증을 반복하거나 마지막 세부 입력 화면부터 이어가지 않는다.
- 프로필이나 일부 입력이 있더라도 `onboarding_completed_at`이 없으면 완료로 추정하지 않는다.
- `COMPLETED`와 `NONE`은 추가 온보딩 입력이 없다는 뜻이다. 체류기간 유효 · 추천 이용 허가는 별도로 판단한다. 만료일이 지나도 저장된 완료 상태와 시각은 유지한다.
- `NOT_APPLICABLE`은 **새 유학생 온보딩의 적용 대상이 아니다**라는 뜻이며, 기존 가입 유형의 온보딩까지 완료했다는 뜻이 아니다. 프론트는 기존 유형별 가입 · 프로필 흐름을 사용한다. 기존 소셜 가입 응답의 `onboardingRequired=true`와도 구분한다.
- 현재 기능 플래그의 켜짐 여부나 로그인 제공자 이름으로 판정하지 않고, 서버에 저장된 회원 유형 · 회원 연결 인증 기록 · 완료 시각을 조회한다.
- 성공은 `200`, `Cache-Control: no-store`다. 미인증 · 없는 회원은 `401 UNAUTHORIZED`, 탈퇴 회원은 `403 WITHDRAWN_ACCOUNT`다.
- 조회는 데이터를 생성 · 수정하거나 외부 OCR · APICK · SMS 인증을 호출하지 않는다. 조회 중 일괄 저장이 진행되면 커밋된 상태를 일관되게 읽으며, 완료 요청 성공 후 다시 조회하면 완료 상태를 반환한다.

프론트는 신규 계정의 휴대전화 가입 성공 후 또는 기존 계정 로그인 후 이 API를 호출하고 위 분기로 이동한다. 미완료 온보딩 입력은 화면에서 모아 마지막 POST 요청으로 저장한다.

## 입력 및 저장

화면마다 서버에 저장하지 않고 프론트에서 입력을 모아 한 번에 보낸다. 중도 이탈하면 계정은 유지하고 재로그인 후 거주지역 입력 화면부터 다시 시작한다. 가입 시 저장한 생년월일은 회원 연결 정보에 보존된다. 세부 입력 화면의 복구는 제공하지 않는다.

```json
{
  "currentRegion": "서울특별시 마포구",
  "koreanLevel": "INTERMEDIATE",
  "careers": [],
  "topikLevel": null,
  "matchingPreferences": {
    "businessTypeFlexible": false,
    "preferredJobs": [{"businessTypeId": 1}],
    "preferredWorkTimes": [
      {"dayOfWeek": "MON", "startTime": "22:00", "endTime": "00:00"},
      {"dayOfWeek": "TUE", "startTime": "00:00", "endTime": "02:00"}
    ],
    "preferredLocation": {
      "locationName": "서울특별시 마포구",
      "latitude": 37.5665,
      "longitude": 126.978,
      "radiusM": 500
    },
    "priorities": [
      {"criterionId": 1, "priorityOrder": 1},
      {"criterionId": 2, "priorityOrder": 2},
      {"criterionId": 3, "priorityOrder": 3},
      {"criterionId": 4, "priorityOrder": 4}
    ]
  }
}
```

- 생년월일은 전화번호 인증 전에 `PUT /api/auth/social/birth-date`로 직접 입력·검사하고 계정 생성 시 보존한다. 마지막 요청에서는 `birthDate`를 생략한다. 보내면 보존된 값과 같아야 하며 다른 값은 400이다. 이전 버전에서 가입하여 날짜가 보존되지 않은 인증 회원만 마지막 요청에 `birthDate`가 필수다. 최종 저장도 한국 날짜 기준 만 18세를 재검사한다. 명시적인 `null`은 허용하지 않는다.
- 이름은 가입 시 저장된 회원 이름을 유지한다. 국적 · 체류자격 · 체류 만료일은 `student_registration_verification`에서 가져오며 요청으로 덮어쓸 수 없다. 외국인등록번호는 받거나 저장하지 않는다.
- `careers`는 필수이며 경력 없음은 `[]`로 보낸다. 경력이 있으면 기존 생성 API와 동일한 `careerName`, `startMonth`, `endMonth`, `description`이 모두 필요하다. 기간은 `YYYY-MM`, 종료월은 시작월 이상이며 미래 근무기간은 허용하지 않는다.
- `topikLevel`은 필수이며 없음은 명시적인 `null`, 보유 급수는 정수 1~6이다. 생략은 400이다. `null`은 완료 시각과 함께 저장되므로 미입력과 구분된다.
- `matchingPreferences`의 업종 여부 · 목록, 근무시간, 근무위치, 우선순위는 모두 필수다. 기존 부분 수정 API와 달리 근무시간은 최소 1개다.
- 업종 상관없음은 `businessTypeFlexible=true`, `preferredJobs=[]`다. 그 외에는 최소 1개 업종이 필요하고 기타 업종에는 `otherJobName`이 필요하다.
- 시간은 기존 30분 단위 계약을 사용한다. 자정을 넘는 구간은 요일별로 나누고 종료 `00:00`은 그 요일의 끝이다.
- 반경 `radiusM`은 정수 `100, 200, 300, 400, 500, 600, 700, 800, 900, 1000`만 허용한다(미터 단위, 100~1,000m, 100m 간격). `PATCH /api/students/me/matching-preferences`와 온보딩 일괄 저장에 동일하게 적용한다. 범위 밖 값, 100의 배수가 아닌 값, 소수·문자열·null·누락은 `400 INVALID_INPUT`이며 요청의 어떤 항목도 저장하지 않는다.
- 구직조건 부분 수정에서 `preferredLocation`을 생략하면 기존 위치·반경을 유지한다. 기존에 저장된 범위·간격 밖 반경도 조회와 다른 항목 수정 시 그대로 반환하며 자동 보정하거나 삭제하지 않는다. `preferredLocation`을 전송하면 위치명·위도·경도·반경을 모두 보내야 하고 반경은 새 입력 조건을 만족해야 한다. DB 구조와 기존 데이터는 변경하지 않는다.
- 우선순위는 근무시간 · 급여 · 거리 · 업종 4개 기준과 순위 1~4를 중복 없이 모두 보낸다.
- `minimumHourlyWage`는 기존 구직조건과 동일한 선택 항목이다. 생략 시 기존 값을 유지하고 명시적인 `null`은 해제한다. 새 온보딩의 필수 항목이 아니다.

이미 개별 API로 만든 미완료 프로필이 있다면 제출한 기본정보와 TOPIK을 갱신하고 경력 · 업종 · 시간 · 우선순위 목록을 교체한다. 학교 등 요청 대상이 아닌 기존 정보는 유지하며 기존 학업 상태와 인증된 체류자격 등이 충돌하면 오류로 전체 롤백한다. 기존 개별 API는 여전히 호출 즉시 저장하며 이 API를 화면별로 대체 호출하지 않는다.

## 응답과 원자성

성공은 `200`, `Cache-Control: no-store`다.

```json
{"studentId":1,"completed":true,"completedAt":"2026-11-20T03:00:00Z"}
```

프로필 · 경력 · 구직조건 · `student_profile.onboarding_completed_at`을 한 트랜잭션으로 저장한다. 검증이나 DB 저장이 실패하면 기존 정보까지 모두 이전 상태로 돌아간다. 회원 행을 먼저 잠가 같은 회원의 완료 · 프로필 수정 · 구직조건 수정 · 경력 변경을 순서대로 처리한다. 동시 완료 요청은 한 건만 성공하며, 완료 후 재제출은 `409 ONBOARDING_ALREADY_COMPLETED`다. 다시 제출해도 완료 시각을 바꾸거나 경력을 중복 생성하지 않는다. 완료 후에는 개별 수정 API를 사용한다.

| 상태 | 오류 |
| --- | --- |
| 400 | `INVALID_INPUT`: 필수 항목 누락, 잘못된 형식, 경력 기간 또는 구직조건 오류 |
| 401 | `UNAUTHORIZED`: 유효한 로그인 없음 |
| 403 | `FORBIDDEN`, `WITHDRAWN_ACCOUNT` |
| 404 | `MATCHING_REFERENCE_NOT_FOUND`: 등록되지 않은 업종 · 우선순위 기준 |
| 409 | `PHONE_NOT_VERIFIED`, `REGISTRATION_VERIFICATION_REQUIRED`, `ONBOARDING_ALREADY_COMPLETED` |

오류 응답은 기존 `{"code":"...","message":"..."}` 구조를 사용한다.

## 체류기간과 후속 작업

가입 이후 체류기간이 만료되어도 이 API의 입력 · 완료는 허용한다. 확인된 만료일을 그대로 프로필에 옮기며 카드 · PDF 인증 시각을 바꾸지 않는다. `completed=true`는 입력 완료이고 체류기간 유효 · 추천 이용 허가가 아니다.

체류기간 만료 시 추천 제한은 별도 담당자 작업이다. 가입 후 체류 증빙 갱신은 후속이며, 공통 온보딩 접근 제한과 사업주 흐름은 이 API에서 변경하지 않는다. 상태 조회 응답을 전역 서비스 접근 허가로 사용하지 않는다.

V25는 완료 시각 컬럼만 추가한다. 기존 회원의 완료 여부를 임의로 추정해 일괄 변경하지 않는다.

V26은 가입 세션과 회원 연결 인증 정보에 `birth_date`를 추가한다. 기존 행을 임의 날짜로 채우지 않으며 기존 미완료 회원은 `BASIC_INFORMATION` 흐름으로 계속 입력할 수 있다.
