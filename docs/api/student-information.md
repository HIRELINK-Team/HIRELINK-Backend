# 내 정보 최종 일괄 저장

`PATCH /api/students/me/profile`은 로그인한 유학생의 **수정 반영하기**에서 호출한다. 상세 화면의 확인, 경력 추가·수정·삭제는 클라이언트 임시 상태로 관리하며 이 API를 호출하지 않는다. 저장하지 않고 나가면 임시 상태를 폐기한다.

## 요청

```json
{
  "birthDate": "2000-03-14",
  "phoneNumber": "010-1234-5678",
  "currentRegion": "서울 마포구",
  "koreanLevel": "ADVANCED",
  "preferredLocation": {
    "locationName": "강남역",
    "latitude": 37.4979,
    "longitude": 127.0276,
    "radiusM": 500
  },
  "careers": [
    {"careerName": "카페 아르바이트", "startMonth": "2023-03", "endMonth": "2023-09", "description": "음료 제조, 고객 응대"}
  ]
}
```

- 여섯 필드만 수정할 수 있다. 생략한 항목은 유지하며 빈 요청·명시적 null·알 수 없는 필드·잘못된 JSON 자료형은 `400 INVALID_INPUT`이다.
- 생년월일은 한국 날짜 기준 만 18세 이상이어야 한다. 거주지역은 온보딩과 동일한 문자열이며 최대 150자다. 한국어 수준은 `BEGINNER`, `INTERMEDIATE`, `ADVANCED`다.
- `preferredLocation`은 매칭 조건 관리와 같은 저장 데이터다. 변경 시 이름·위도·경도·반경을 모두 보낸다. 좌표는 숫자, 반경은 100~1,000m의 100m 간격이다. 내 정보 편집 도중 매칭 조건 수정 API를 별도로 호출하지 않는다.
- `careers`는 최종 전체 목록이다. 빈 배열이면 기존 경력을 모두 삭제하며, 생략하면 유지한다. 각 항목의 경력명·시작월·종료월·근무 내용은 필수다. 경력명은 최대 150자, 기간은 `YYYY-MM`, 종료월은 시작월 이상이며 이번 달까지만 허용한다. 경력 ID와 사용자 ID는 받지 않는다. 저장 성공 후 경력 ID는 새로 부여한다.
- 이름·국적·체류자격·체류 만료일·TOPIK·학교·학년·학업과정·학적 상태는 이 요청에서 수정할 수 없다. 서류 정보는 해당 인증·최종 등록 경로를 사용한다.

## 전화번호 변경

1. 기존 `/api/auth/phone-verifications`에서 새 번호로 문자를 요청한다.
2. `/api/auth/phone-verifications/verify`에서 번호와 인증번호를 확인한다.
3. 유학생 프로필이 있는 계정은 인증 성공 결과만 임시 보관한다. 이 시점에는 `user_account.phone_number`와 `phone_verified_at`을 변경하지 않는다.
4. 응답의 `verifiedAt`부터 **300초 미만**에 새 번호를 포함하여 최종 일괄 저장한다. 인증번호 입력의 유효시간 300초와 별도로 계산한다.
5. 해당 계정의 인증된 번호와 일치하고 아직 유효해야 한다. 번호 중복도 저장 시 다시 검사한다. 성공한 저장에서 인증 결과를 소비한다.

- 번호는 010으로 시작하는 11자리이며 하이픈 입력도 허용한다. DB에는 하이픈을 제거한다.
- 새 인증번호 발송 시 이전 확인 결과를 폐기한다. 같은 코드를 재확인하여 저장 가능 시간을 연장할 수 없다.
- 기존 번호와 같은 값을 보내거나 번호를 생략하면 새 인증은 필요하지 않다.
- 인증 누락·번호 불일치·확인 후 5분 경과는 `409 PHONE_NOT_VERIFIED`, 다른 계정이 사용하는 번호는 `409 DUPLICATE_PHONE_NUMBER`다.
- 프로필 생성 전 가입 단계와 사업주의 기존 번호 인증은 확인 성공 시 번호를 저장하는 동작을 유지한다.

## 저장·응답

회원 행을 먼저 잠근다. 번호 변경이 있으면 기존 번호별 잠금 순서에 따라 직렬화한다. 프로필·전화번호·경력·희망 근무지역을 한 트랜잭션으로 저장하며 검증 오류나 DB 오류가 발생하면 **전체 롤백**한다. 소비 중이던 인증 결과도 복구되므로 만료 전에는 재시도할 수 있다. DB 변경을 반영한 뒤에도 인증 결과가 유효한지 재확인한다.

성공 시 기존 `StudentProfileUpdateResponse`와 `Cache-Control: no-store`를 반환한다. 사진 URL을 발급하지 않는다. 저장된 경력·전화번호는 `GET /api/students/me/profile`, 희망 근무지역은 `GET /api/students/me/matching-preferences`로 조회한다. 실패 시 클라이언트는 편집 중인 값을 유지하고 오류를 수정하여 다시 요청한다.

인증 오류는 `401 UNAUTHORIZED`, 역할 오류는 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`, 본인 프로필 미등록은 `404 STUDENT_PROFILE_NOT_FOUND`, 예상하지 못한 저장 오류는 `500 INTERNAL_SERVER_ERROR`다.

## 기존 경력 API 전환

`POST /api/students/me/careers`, `PATCH /api/students/me/careers/{careerId}`, `DELETE /api/students/me/careers/{careerId}`는 더 이상 개별 저장하지 않는다. 인증된 유학생 요청에 `400 INVALID_INPUT`과 일괄 저장 안내를 반환한다. 온보딩의 마지막 제출에서 경력을 저장하는 동작은 유지한다.

## DB 변경

Flyway `V35__add_pending_phone_change_verification.sql`은 기존 `phone_verification`에 확인 시각 `verified_at`과 저장 유효시각 `change_expires_at`을 추가한다. 기존 인증 요청에는 두 값이 null이며 회원 번호를 변환하지 않는다. 두 확인 값은 로그인 회원에 대해서만 함께 기록한다. 새 테이블이나 환경변수는 필요하지 않다.
