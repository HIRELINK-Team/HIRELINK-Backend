# 유학생 온보딩 일괄 저장 · 완료

`POST /api/students/me/onboarding`은 전화번호 인증으로 계정을 만든 뒤 마지막 **마치기**에서 호출한다. 로그인한 본인의 액세스 토큰을 사용하며, 회원 연결 등록증 인증 기록이 필요하다. LOCAL 테스트 계정은 기존 프로필 · 경력 · 구직조건 API를 사용한다.

## 입력 및 저장

화면마다 서버에 저장하지 않고 프론트에서 입력을 모아 한 번에 보낸다. 중도 이탈하면 계정은 유지하고 재로그인 후 전화번호 인증 다음 기본정보 입력 화면부터 다시 시작한다. 세부 입력 화면의 복구는 제공하지 않는다.

```json
{
  "birthDate": "2000-05-14",
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

- 생년월일은 직접 입력한다. 기존 프로필과 동일하게 한국 날짜 기준 만 18세 이상이어야 한다.
- 이름은 가입 시 저장된 회원 이름을 유지한다. 국적 · 체류자격 · 체류 만료일은 `student_registration_verification`에서 가져오며 요청으로 덮어쓸 수 없다. 외국인등록번호는 받거나 저장하지 않는다.
- `careers`는 필수이며 경력 없음은 `[]`로 보낸다. 경력이 있으면 기존 생성 API와 동일한 `careerName`, `startMonth`, `endMonth`, `description`이 모두 필요하다. 기간은 `YYYY-MM`, 종료월은 시작월 이상이며 미래 근무기간은 허용하지 않는다.
- `topikLevel`은 필수이며 없음은 명시적인 `null`, 보유 급수는 정수 1~6이다. 생략은 400이다. `null`은 완료 시각과 함께 저장되므로 미입력과 구분된다.
- `matchingPreferences`의 업종 여부 · 목록, 근무시간, 근무위치, 우선순위는 모두 필수다. 기존 부분 수정 API와 달리 근무시간은 최소 1개다.
- 업종 상관없음은 `businessTypeFlexible=true`, `preferredJobs=[]`다. 그 외에는 최소 1개 업종이 필요하고 기타 업종에는 `otherJobName`이 필요하다.
- 시간은 기존 30분 단위 계약을 사용한다. 자정을 넘는 구간은 요일별로 나누고 종료 `00:00`은 그 요일의 끝이다.
- 반경은 양수 정수 미터 값이다. 화면의 100m · 500m · 1km 표시는 슬라이더 눈금이며 세 값으로만 제한하지 않는다.
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

현재 추천 API에 체류기간 만료 차단이 구현되어 있다고 가정하면 안 된다. 체류기간 만료 시 추천 제한, 가입 후 체류 증빙 갱신, 온보딩 상태 조회는 별도 후속 작업이다. 공통 온보딩 접근 제한과 사업주 흐름은 이번 API에서 변경하지 않는다.

V25는 완료 시각 컬럼만 추가한다. 기존 회원의 완료 여부를 임의로 추정해 일괄 변경하지 않는다.
