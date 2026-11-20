# 가입 생년월일 검사 및 보존

새 유학생 소셜 가입 순서는 **약관 → 등록증 앞면·뒷면 및 필요한 PDF → 생년월일 → 전화번호 인증 → 계정 생성 → 거주지역부터 온보딩 → 마치기**다.

## 생년월일 저장

`PUT /api/auth/social/birth-date`

가입 진행 `socialSignup` 쿠키와 기존 CSRF 쿠키·헤더를 사용한다. 계정 생성 전 API이며 로그인 토큰만으로 호출할 수 없다. 소셜 로그인과 등록증 기능이 활성화된 유학생에게 제공한다. 필수 약관 동의와 유효한 등록증/PDF 확인이 먼저 필요하다.

```json
{"birthDate":"2000-05-14"}
```

날짜는 직접 입력한다. `YYYY-MM-DD` 문자열만 받으며 다른 필드는 거절한다. 한국 날짜 기준 만 18세 생일 당일부터 허용하고, 미성년자·미래 날짜는 거절한다. 등록번호에서 날짜를 추출하거나 등록번호와 대조하지 않는다.

성공은 `200`, `Cache-Control: no-store`다.

```json
{"ageVerified":true,"signupExpiresAt":"2026-11-20T10:30:00Z"}
```

- 생년월일은 기존 가입 세션에 저장하며 같은 세션에서 유효한 날짜로 수정할 수 있다. 유효하지 않은 요청은 기존 저장값을 변경하지 않는다.
- 기존 30분 만료 시각은 연장하지 않는다. 만료되면 즉시 사용 불가하며 기존 정리 절차에서 세션과 함께 삭제된다.
- 아직 회원·JWT·프로필은 만들지 않는다. 전화번호 인증 성공 시 생년월일을 회원 연결 인증 정보에 함께 보존하고 임시 세션을 소모한다. 계정 생성 후 보존된 날짜에는 가입 세션의 30분 만료가 적용되지 않는다.
- `POST /api/auth/social/phone-verifications`와 `/verify`는 날짜가 없으면 `409 BIRTH_DATE_REQUIRED`, 만 18세 미만이면 `422 AGE_REQUIREMENT_NOT_MET`으로 SMS 발송·계정 생성을 차단한다. 등록증·약관·세션의 기존 검사도 유지한다.
- 사업주와 등록증 기능 비활성 상태는 기존 가입 흐름을 유지한다. LOCAL 테스트 계정은 기존 개별 API를 사용한다.

| 상태 | 오류 |
| --- | --- |
| 400 | `INVALID_INPUT`, `REQUIRED_TERMS_NOT_AGREED` |
| 401 | `INVALID_SOCIAL_SESSION` |
| 403 | `INVALID_CSRF_TOKEN`, `FORBIDDEN`, `SOCIAL_ROLE_MISMATCH`, `WITHDRAWN_ACCOUNT` |
| 409 | `SOCIAL_ACCOUNT_ALREADY_REGISTERED`, `REGISTRATION_VERIFICATION_REQUIRED`, `STAY_DOCUMENT_REQUIRED` |
| 422 | `AGE_REQUIREMENT_NOT_MET`, `STAY_DOCUMENT_EXPIRED` |
| 503 | `SOCIAL_LOGIN_UNAVAILABLE`, `REGISTRATION_VERIFICATION_UNAVAILABLE` |

## 재로그인과 마지막 저장

생년월일을 보존한 미완료 회원의 `GET /api/users/me/onboarding`은 `REQUIRED/CURRENT_REGION`을 반환한다. 등록증·생년월일·전화 인증을 반복하지 않고 거주지역부터 다시 입력한다. 이후 입력은 여전히 프론트에서 모아서 마지막에 한 번 저장한다.

`POST /api/students/me/onboarding`은 보존된 생년월일을 사용한다. 요청의 `birthDate`는 생략하고, 보내더라도 저장된 날짜와 같아야 한다. 다른 날짜로 덮어쓸 수 없다. 이전 버전에서 생년월일을 보존하지 않은 기존 인증 회원은 `BASIC_INFORMATION`으로 안내하고 기존처럼 마지막 요청에 날짜를 포함한다. V26은 기존 행에 날짜를 임의로 채우지 않는다.

전체 요청 및 오류 처리는 [온보딩 일괄 저장](student-onboarding.md)을 따른다.
