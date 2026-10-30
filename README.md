# HIRELINK Backend

외국인 유학생과 사업주를 연결하는 구인·구직 매칭 플랫폼 **HIRELINK**의 백엔드 서버입니다.

## 주요 기능 소개

HIRELINK는 외국인 유학생의 구직 조건과 사업주의 채용 정보를 연결하는 서비스를 목표로 합니다. 현재 저장소에서 확인되는 구현 범위는 다음과 같습니다.

- LOCAL 이메일 회원가입 및 로그인
- BCrypt 비밀번호 해싱
- JWT Access Token 발급 및 Bearer 인증
- HttpOnly 쿠키 기반 Refresh Token 저장과 Access Token 재발급
- 현재 로그인 기록과 Refresh Token 쿠키를 제거하는 로그아웃
- Refresh Token 원문 대신 SHA-256 해시 저장
- 재발급·로그아웃 API의 CSRF 보호
- 회원·인증 정보와 Refresh Token의 PostgreSQL 영속화
- Flyway 마이그레이션과 JPA 스키마 검증
- Testcontainers PostgreSQL을 사용한 인증 통합 테스트
- 로그인한 회원의 약관별 동의 상태 저장
- 휴대전화 인증을 완료한 유학생의 기본 프로필 등록
- 유학생 본인의 프로필·경력 조회 및 비공개 프로필 사진의 임시 URL 발급
- 유학생 프로필 부분 수정 및 비자별 학업 상태 검증
- 유학생 본인의 근무 경력 추가·수정·삭제
- 업종·사업장·채용공고 테이블과 Entity, 공고 소유 사업주 관계

채용공고 등록·조회 API, 지원, 사업주 검토, 면접, 채용 및 매칭 기능은 후속 구현 범위입니다.

### 사업장·채용공고 데이터 구조

- `V8__create_workplace_and_job_posting.sql`은 `business_type`, `workplace`, `job_posting`을 생성합니다. 업종 초기 데이터는 포함하지 않습니다.
- `JobPosting.workplace → Workplace.employerUser → UserAccount` 관계로 공고 소유 사업주의 회원 ID를 확인할 수 있습니다. 연관 Entity는 지연 로딩하므로 필요한 데이터는 트랜잭션 안에서 조회합니다.
- 공고 상태는 `DRAFT`, `RECRUITING`, `CLOSED`이며 저장할 때 명시합니다. 임시저장을 위해 ERD의 선택 컬럼은 NULL을 허용합니다.
- 생성·수정 시각은 기존 회원 Entity와 같은 방식으로 기록합니다. 사업장과 공고의 업종은 각각 별도로 연결됩니다.
- 이번 구성은 테이블과 Entity까지입니다. 사업주 역할·소유권 검사, 모집 상태의 필수값 검사, 제목 생성, 사진·근무시간 저장은 해당 기능을 구현할 때 추가합니다.

### 현재 인증 API

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | LOCAL 이메일 회원가입 | 불필요 |
| `POST` | `/api/auth/login` | LOCAL 이메일 로그인 | 불필요 |
| `GET` | `/api/auth/csrf` | 쿠키 인증 요청용 CSRF 보안 확인값 조회 | 불필요 |
| `POST` | `/api/auth/refresh` | Access Token 재발급 | Refresh Token 쿠키 및 CSRF 확인값 |
| `POST` | `/api/auth/logout` | 현재 로그인 종료 | CSRF 확인값 필수, Refresh Token 쿠키 선택 |
| `POST` | `/api/auth/phone-verifications` | 휴대전화 인증번호 요청 (SMS 연동 예정) | Access Token |
| `POST` | `/api/auth/phone-verifications/verify` | 휴대전화 인증번호 확인 | Access Token |

- 이메일은 앞뒤 공백 제거 후 소문자로 정규화합니다.
- 가입 역할은 `STUDENT`, `EMPLOYER`만 허용합니다.
- 비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다. BCrypt가 처리할 수 없는 입력에는 `400 INVALID_PASSWORD`를 반환합니다.
- Access Token은 1시간, Refresh Token은 최초 발급 시점부터 14일 동안 유효합니다.
- Refresh Token은 `refreshToken` HttpOnly 쿠키로 전달하고 DB에는 SHA-256 해시만 저장합니다.
- 쿠키의 기본 속성은 `Secure`, `SameSite=Lax`, `Path=/api/auth`입니다. 로컬 HTTP에서만 `AUTH_COOKIE_SECURE=false`를 사용합니다.
- 회원가입과 로그인, Refresh Token 저장은 하나의 트랜잭션으로 처리됩니다.
- 오류 응답은 `{ "code": "...", "message": "..." }` 형식입니다.

보호된 API에는 다음 헤더를 사용합니다.

```text
Authorization: Bearer <Access Token>
```

폼 로그인, HTTP Basic, 서버 세션, Spring Security 기본 로그아웃은 사용하지 않습니다.

### 휴대전화 인증번호 요청 API

`POST /api/auth/phone-verifications`에 Access Token과 `{ "phoneNumber": "010-0000-0000" }` 형식의 본문을 전달합니다.
국내 `010` 번호만 지원하며 하이픈이 없는 11자리 입력도 허용합니다. DB에는 하이픈을 제거한 번호를 저장합니다.

**실제 SMS 업체는 아직 연결하지 않았습니다.** 현재 서버의 발송 요청은 `500 SMS_DELIVERY_FAILED`를 반환합니다.
`SmsSender`에 실제 발송을 연결한 뒤 아래 성공 흐름을 사용할 수 있습니다. 자동 테스트에서는 이 외부 발송 부분만 대체합니다.
인증번호를 응답이나 로그로 제공하지 않습니다.

- 발송 성공 응답: `200`, `{ "expiresIn": 300, "message": "인증번호가 전송되었습니다." }`
- 인증번호는 6자리 숫자 문자열이며 DB에 해시만 저장합니다. 유효시간은 발송 시점부터 300초입니다.
- 계정별·전화번호별로 각각 60초 간격, 최근 1시간 최대 5회까지 요청할 수 있습니다. 발송 실패도 요청 횟수에 포함합니다.
- 한 계정에는 현재 인증번호 하나만 유지합니다. 재전송 성공 시 교체하며, 실패하면 기존 인증번호를 유지합니다.
- 유효한 인증의 실패 횟수는 재전송·번호 변경으로 초기화하지 않습니다. 실패 횟수가 5회이면 만료 전까지 재전송을 차단합니다.
- 입력 오류 `400`, 로그인 필요 `401`, 탈퇴 계정 `403`, 요청 제한 `429`, 발송 실패 `500`을 반환합니다.
- PostgreSQL에 인증 정보와 발송 시도 기록을 저장합니다. 계정과 번호를 잠가 동시 요청도 제한을 적용하고, 다음 요청 시 해당 계정·번호의 1시간이 지난 발송 기록을 정리합니다.
- 실제 SMS 연동은 후속 작업입니다. 문자 발송만으로 회원의 전화번호나 인증 완료 시각을 변경하지 않습니다.

### 휴대전화 인증번호 확인 API

`POST /api/auth/phone-verifications/verify`에 Access Token과 다음 본문을 전달합니다. 인증번호는 앞자리 0을 유지하도록 문자열로 보냅니다.

```json
{
  "phoneNumber": "010-0000-0000",
  "code": "000123"
}
```

성공 응답은 `200`이며 `phoneNumber`, ISO-8601 형식의 `verifiedAt`, `message`를 반환합니다.
응답의 전화번호는 `010-0000-0000` 형식이고 DB에는 하이픈 없이 저장합니다.

- 인증번호는 요청한 계정과 전화번호에서만 사용할 수 있습니다. 다른 계정·번호, 만료 또는 소비된 인증번호는 `400 INVALID_VERIFICATION_CODE`입니다.
- 일치하지 않는 인증번호는 실패 횟수를 저장합니다. 1~4회는 `400`, 5회부터는 만료 전까지 `429 PHONE_VERIFICATION_RATE_LIMIT`이며 올바른 번호를 입력해도 인증하거나 재전송할 수 없습니다.
- 인증 성공 시 회원의 `phone_number`, `phone_verified_at`을 저장하고 인증번호와 실패 횟수를 삭제합니다. 최근 1시간 발송 시도 기록은 유지하여 요청 제한이 초기화되지 않게 합니다.
- 다른 회원이 이미 인증한 번호는 `409 DUPLICATE_PHONE_NUMBER`입니다. 동시에 같은 번호를 인증해도 한 회원에게만 저장됩니다.
- 입력 형식 오류 `400`, 로그인 필요 `401`, 탈퇴 계정 `403`, 예상하지 못한 서버 오류 `500`을 반환합니다.
- 회원 정보 저장과 인증번호 삭제는 하나의 트랜잭션으로 처리합니다. 저장이 실패하면 기존 회원 정보와 인증번호를 유지합니다.
- 실제 SMS 연동 전까지 사용자에게 인증번호를 전달할 수 없으므로, 전체 사용자 인증 흐름은 아직 사용할 수 없습니다.

### 로그아웃 API

`POST /api/auth/logout`은 요청 본문 없이 현재 브라우저의 `refreshToken` 쿠키를 사용합니다.
재발급과 동일하게 `GET /api/auth/csrf`에서 받은 확인값을 `X-XSRF-TOKEN` 헤더에 넣고, 해당 응답의 `XSRF-TOKEN` 쿠키도 함께 전송합니다.
브라우저의 `fetch` 요청에는 `credentials: "include"`를 사용합니다.

성공 응답은 `200 OK`입니다.

```json
{
  "message": "로그아웃되었습니다."
}
```

- 전달된 Refresh Token의 해시와 일치하는 DB 기록만 삭제합니다. 다른 기기의 로그인은 유지합니다.
- 응답에서 Refresh Token 쿠키를 같은 경로·보안 속성으로 만료 처리합니다.
- 쿠키가 없거나, 토큰이 이미 삭제되었거나 만료된 상태에서도 `200`을 반환합니다. 동시에 같은 토큰으로 요청해도 정상 처리합니다.
- Access Token은 검증하지 않으므로 만료된 Bearer 헤더가 있어도 로그아웃할 수 있습니다.
- CSRF 확인값 누락·불일치는 `403 INVALID_CSRF_TOKEN`, 서버 처리 실패는 `500 INTERNAL_SERVER_ERROR`입니다.
- 프론트는 성공 응답을 받은 뒤 보관 중인 Access Token을 삭제하고 로그인 화면으로 이동합니다.
- 서버의 Access Token 블랙리스트는 사용하지 않습니다. 이미 발급한 Access Token은 만료 시각까지 유효하지만, 삭제된 Refresh Token으로는 재발급할 수 없습니다.

### 약관 동의 저장 API

`PUT /api/users/me/term-agreements`는 Access Token으로 식별한 회원의 동의 상태를 저장합니다.
`userId`는 요청 본문으로 받지 않으며, `STUDENT`와 `EMPLOYER`가 같은 API를 사용합니다.

현재 등록된 약관 종류는 다음과 같습니다.

| termTypeId | 약관 이름 |
| --- | --- |
| 1 | 이용약관 |
| 2 | 개인정보처리방침 |
| 3 | 광고성 정보 수신 |

```json
{
  "agreements": [
    { "termTypeId": 1, "isAgreed": true },
    { "termTypeId": 2, "isAgreed": true },
    { "termTypeId": 3, "isAgreed": false }
  ]
}
```

성공 시 `200 OK`와 `agreements` 배열을 반환합니다. 각 항목에는 `termTypeId`, `isAgreed`, `agreedAt`이 포함됩니다.

- 새로 동의하거나 미동의에서 동의로 바꾸면 현재 시각을 저장합니다. 같은 동의를 반복하면 기존 시각을 유지합니다.
- 미동의(`false`)는 `agreedAt: null`로 저장합니다.
- 요청에 포함한 약관만 갱신하고 같은 순서로 반환합니다. 생략한 약관은 유지하며 빈 배열은 변경 없이 빈 배열을 반환합니다.
- 한 요청의 모든 항목을 하나의 트랜잭션으로 저장합니다. 오류가 발생하면 전체 변경을 취소합니다.
- 필수값 누락·잘못된 형식·중복된 약관 ID는 `400 INVALID_INPUT`, 없는 약관 ID는 `404 TERM_TYPE_NOT_FOUND`입니다.
- 로그인 필요 `401`, 탈퇴 계정 `403`, 예상하지 못한 서버 오류 `500`은 기존 공통 오류 형식을 사용합니다.
- 이번 범위는 동의 상태 저장입니다. 필수 동의 검사, 역할별 약관 제한, 약관 내용·버전 관리는 포함하지 않습니다. 화면의 전체 동의 선택 여부는 별도로 저장하지 않습니다.
- 약관 종류가 추가되면 기존 ID를 유지하고 새 Flyway 파일로 데이터를 추가합니다.

### 유학생 프로필 등록 API

`POST /api/students/me/profile`은 Access Token으로 식별한 유학생의 기본 프로필을 생성합니다.

```json
{
  "name": "테스트 이름",
  "birthDate": "2000-03-14",
  "nationality": "VN",
  "currentRegion": "서울 마포구",
  "koreanLevel": "INTERMEDIATE"
}
```

- 이름은 공백만으로 구성할 수 없으며 최대 100자, 현재 거주지역은 공백만으로 구성할 수 없으며 최대 150자입니다.
- 국적은 ISO 3166-1 alpha-2의 영문 대문자 2자리 코드, 한국어 수준은 `BEGINNER`, `INTERMEDIATE`, `ADVANCED` 중 하나입니다.
- 생년월일은 `YYYY-MM-DD` 형식이며 한국 날짜(`Asia/Seoul`) 기준으로 만 18세가 되는 생일부터 등록할 수 있습니다.
- 휴대전화 번호와 인증 완료 시각이 모두 저장된 `STUDENT` 계정만 등록할 수 있습니다.
- 이름은 `user_account`, 나머지 입력값은 `student_profile`에 하나의 트랜잭션으로 저장합니다. 프로필 ID는 회원 ID와 같습니다.
- 학교·비자·체류 정보 등 나머지 프로필 컬럼은 ERD대로 유지하며 등록 시 `NULL`입니다. `visa_type`에는 V7 마이그레이션으로 9종을 등록하고, `study_program`의 초기 데이터는 추가하지 않습니다.
- 성공 응답은 `201 Created`이며 `studentId`, `name`, `phoneNumber`, `birthDate`, `nationality`, `currentRegion`, `koreanLevel`을 반환합니다. 전화번호는 `010-0000-0000` 형식입니다.
- 입력 오류·만 18세 미만은 `400 INVALID_INPUT`, 로그인 필요는 `401 UNAUTHORIZED`, 유학생이 아닌 계정은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다.
- 휴대전화 미인증은 `409 PHONE_NOT_VERIFIED`, 이미 등록된 프로필은 `409 DUPLICATE_STUDENT_PROFILE`, 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.
- 같은 회원의 동시 등록 요청도 한 번만 저장하며, 등록 실패 시 기존 이름과 프로필 상태를 유지합니다.
- 실제 SMS 연동 전에는 신규 회원이 휴대전화 인증을 완료하는 사용자 흐름이 아직 연결되지 않습니다. 통합 테스트는 일회용 DB에 인증된 회원을 준비해 등록을 검증합니다.

### 내 유학생 프로필 조회 API

`GET /api/students/me/profile`은 Access Token으로 식별한 유학생 본인의 회원 정보, 프로필과 경력 목록을 반환합니다. 요청 본문과 Query / Path Parameter는 사용하지 않습니다.

- 성공 응답은 `200 OK`이며 `studentId`, `name`, `email`, `phoneNumber`, `birthDate`, `nationality`, `currentRegion`, `koreanLevel`을 포함합니다.
- 온보딩 정보는 `visaCode`, `universityName`, `enrollmentStartDate`, `studyProgramId`, `gradeLevel`, `academicState`, `topikLevel`, `stayStartDate`, `stayExpirationDate`, `hirelinkBadgeIssuedAt`으로 반환합니다. 미입력 값은 `null`입니다.
- 경력은 `careers` 배열로 반환합니다. 각 항목은 `careerId`, `careerName`, `startMonth`, `endMonth`, `description`이며, 경력이 없으면 `[]`입니다.
- 경력은 시작월 내림차순, 시작월이 같으면 ID 내림차순입니다. `startMonth`, `endMonth`는 `YYYY-MM` 형식이며 ERD에 따라 둘 다 필수입니다. `description`은 `null`일 수 있습니다.
- 사진 경로가 없으면 `profileImageUrl`은 `null`입니다. 사진 경로가 있으면 Supabase Storage에서 15분(900초) 유효한 임시 URL을 발급해 반환합니다. DB의 `profile_image_key`는 응답에 포함하지 않습니다.
- 임시 URL이 만료된 뒤 사진을 다시 불러오려면 프로필을 다시 조회합니다. 사진 파일은 주소 만료로 삭제되지 않습니다. 응답에는 `Cache-Control: no-store`를 설정합니다.
- `401 UNAUTHORIZED`는 인증 정보가 없거나 유효하지 않은 경우, `403 FORBIDDEN`은 유학생이 아닌 경우, `403 WITHDRAWN_ACCOUNT`는 탈퇴한 회원입니다.
- 본인의 프로필 자체가 없으면 `404 STUDENT_PROFILE_NOT_FOUND`입니다. 온보딩 미완료나 빈 경력 목록은 `404`가 아닙니다.
- 사진 경로가 있는데 설정 누락·파일 없음·통신 오류 등으로 임시 URL 발급에 실패하면 `500 INTERNAL_SERVER_ERROR`를 반환합니다. 예상하지 못한 서버 오류도 같은 코드를 사용합니다.
- 사진 업로드·변경 API는 별도 작업입니다. 이번 API는 저장된 사진의 조회 주소만 발급합니다.

### 경력 추가 API

`POST /api/students/me/careers`는 프로필이 등록된 유학생 본인의 근무 경력을 추가합니다. 사용자 ID는 Access Token에서 가져오며 Request에 포함하지 않습니다.

```json
{
  "careerName": "카페 아르바이트",
  "startMonth": "2023-03",
  "endMonth": "2023-09",
  "description": "음료 제조, 고객 응대"
}
```

- `careerName`은 필수이며 공백만 입력할 수 없고 최대 150자입니다.
- `startMonth`, `endMonth`는 모두 필수이며 JSON 문자열 `YYYY-MM` 형식으로 전달합니다. 종료월은 시작월과 같거나 뒤여야 합니다.
- 두 날짜 모두 한국 날짜(`Asia/Seoul`) 기준 현재 월까지만 허용합니다. 다음 달 이후의 시작월·종료월은 `400 INVALID_INPUT`입니다.
- `description`은 선택 입력이며 생략하거나 `null`을 보내면 `NULL`로 저장합니다.
- 필수값 누락, 잘못된 자료형·월 형식·근무기간, 허용되지 않은 요청 항목은 `400 INVALID_INPUT`입니다.
- 성공 응답은 `201 Created`이며 `careerId`, `careerName`, `startMonth`, `endMonth`, `description`을 반환합니다. 응답에는 `Cache-Control: no-store`를 설정합니다.
- 한 회원은 여러 경력을 등록할 수 있습니다. 저장된 경력은 기존 `GET /api/students/me/profile` 응답의 `careers`에 포함됩니다.
- 인증 오류는 `401 UNAUTHORIZED`, 유학생이 아닌 계정은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`, 본인 프로필 미등록은 `404 STUDENT_PROFILE_NOT_FOUND`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.
- 기존 V6의 `student_career` 테이블을 사용하며 이번 기능을 위한 새 마이그레이션은 없습니다. 실제 Supabase DB에 V6가 적용되어 있어야 하며, Redis·Storage 설정은 추가로 필요하지 않습니다.

### 경력 수정 API

`PATCH /api/students/me/careers/{careerId}`는 로그인한 유학생 본인의 경력에서 요청에 포함된 항목만 수정합니다.

```json
{
  "endMonth": "2023-10",
  "description": null
}
```

- 수정 가능한 항목은 `careerName`, `startMonth`, `endMonth`, `description`입니다. 생략한 항목은 유지하고 `description: null`은 근무 내용을 삭제합니다.
- 경력명은 공백만 입력할 수 없으며 최대 150자입니다. 경력명·시작월·종료월에 명시적인 `null`을 보내거나, 빈 객체·잘못된 자료형·허용하지 않은 항목을 보내면 `400 INVALID_INPUT`입니다.
- 근무기간은 JSON 문자열 `YYYY-MM` 형식입니다. 기존 값과 합친 최종 근무기간에서 종료월은 시작월과 같거나 뒤여야 하며, 두 날짜 모두 한국 날짜(`Asia/Seoul`) 기준 이번 달까지만 허용합니다.
- 성공 응답은 `200 OK`와 기존 `StudentCareerResponse`의 다섯 항목이며 `Cache-Control: no-store`를 설정합니다. 수정 결과는 프로필 조회의 `careers`에도 반영됩니다.
- 인증 오류는 `401 UNAUTHORIZED`, 유학생이 아니거나 다른 사람의 경력은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`, 없는 경력은 `404 STUDENT_CAREER_NOT_FOUND`입니다. 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.
- 같은 경력의 동시 수정은 순서대로 처리합니다. 저장 실패 시 요청의 변경사항은 모두 취소됩니다.
- 기존 V6의 `student_career` 테이블을 사용합니다. 추가 마이그레이션이나 Redis·Storage 설정은 필요하지 않습니다.

### 경력 삭제 API

`DELETE /api/students/me/careers/{careerId}`는 로그인한 유학생 본인의 경력을 실제 삭제합니다. Request Body는 사용하지 않습니다.

- 성공 응답은 `204 No Content`이며 응답 본문은 없습니다. 응답에는 `Cache-Control: no-store`를 설정합니다.
- 현재 회원의 유학생 역할·탈퇴 여부와 해당 경력의 소유자를 확인합니다. 사용자 ID는 Access Token에서 가져옵니다.
- 삭제된 경력은 이후 프로필 조회의 `careers`에 포함되지 않습니다. 회원·프로필·다른 경력은 유지됩니다.
- 인증 오류는 `401 UNAUTHORIZED`, 유학생이 아니거나 다른 사람의 경력은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다.
- 없는 경력이나 이미 삭제한 경력을 다시 요청하면 `404 STUDENT_CAREER_NOT_FOUND`입니다. 숫자가 아니거나 Long 범위를 벗어난 ID는 `400 INVALID_INPUT`입니다.
- 삭제는 하나의 트랜잭션으로 처리합니다. 저장소 오류 시 삭제를 취소하고 `500 INTERNAL_SERVER_ERROR`를 반환합니다. 같은 경력의 수정·삭제 요청은 순서대로 처리합니다.
- 삭제 복구 기능은 제공하지 않습니다. 기존 테이블과 예외 처리를 사용하므로 추가 마이그레이션이나 Redis·Storage 설정은 필요하지 않습니다.

### 내 유학생 프로필 수정 API

`PATCH /api/students/me/profile`은 Access Token으로 식별한 유학생 본인의 프로필에서 요청에 포함된 항목만 수정합니다.

```json
{
  "visaCode": "D-4-1",
  "academicState": "IN_PROGRESS",
  "topikLevel": null
}
```

- 수정 가능한 항목은 `name`, `birthDate`, `nationality`, `currentRegion`, `koreanLevel`, `visaCode`, `stayExpirationDate`, `universityName`, `academicState`, `topikLevel`입니다.
- 보내지 않은 항목은 유지합니다. `topikLevel: null`은 기존 TOPIK 급수를 삭제하며, 급수를 입력할 때는 정수 1~6만 허용합니다. 나머지 항목은 명시적인 `null`을 허용하지 않습니다.
- 빈 객체, 잘못된 자료형, 허용 목록에 없는 항목은 `400 INVALID_INPUT`입니다. `studyProgramId`, `gradeLevel`, 이메일·전화번호·사진·경력도 수정 요청에 포함할 수 없습니다.
- 이름은 최대 100자, 현재 거주지역과 학교명은 최대 150자이며 공백만 입력할 수 없습니다. 국적·한국어 수준은 등록 API와 같은 기준으로 검증합니다. 생년월일 변경 시 한국 날짜 기준 만 18세 이상인지 확인합니다.
- 체류 만료일은 과거 날짜도 저장할 수 있습니다. 기존 체류 시작일이 있으면 그 날짜보다 앞선 만료일은 허용하지 않습니다.
- 비자 또는 학업 상태를 보냈을 때 기존 값과 합친 최종 조합을 검사합니다. 비자 변경 후 기존 학업 상태가 맞지 않으면 `400 INVALID_INPUT`이며, 맞는 `academicState`를 함께 보내야 합니다. 서버가 기존 상태를 자동으로 삭제하지 않습니다.
- 아직 학업 상태를 입력하지 않은 회원은 비자만 먼저 저장할 수 있습니다. 학업 상태를 입력하려면 요청 또는 기존 프로필에 비자가 있어야 합니다.
- `study_program_id`, `grade_level`을 비롯한 수정 대상 외 컬럼은 그대로 보존합니다. 비자에 따라 학업과정을 자동 지정하지 않습니다.
- 이름과 프로필 수정은 하나의 트랜잭션으로 처리합니다. 같은 회원의 동시 수정은 순서대로 처리하고, 실패한 요청의 변경사항은 모두 취소합니다.
- 성공 응답은 `200 OK`이며 `studentId`, `name`, `email`, `phoneNumber`, `birthDate`, `nationality`, `currentRegion`, `koreanLevel`, `visaCode`, `universityName`, `studyProgramId`, `gradeLevel`, `academicState`, `topikLevel`, `stayExpirationDate`, `hirelinkBadgeIssuedAt`을 반환합니다.
- 수정 응답에는 `profileImageUrl`을 포함하지 않으며 Storage API도 호출하지 않습니다. 사진 주소는 기존 프로필 조회 API에서 받습니다. 기존 조회 응답은 유지합니다.
- 인증 오류는 `401 UNAUTHORIZED`, 유학생이 아닌 계정은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`, 본인의 프로필 미등록은 `404 STUDENT_PROFILE_NOT_FOUND`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.

학업 상태는 아래 영문 코드를 요청·응답·DB에 사용합니다. `COMPLETED` 등 같은 코드를 사용하는 상태는 비자에 맞춰 화면에 표시합니다.

| 비자 | 이름 | 학업 상태 코드와 화면 표시 |
| --- | --- | --- |
| D-2-1 / D-2-2 / D-2-3 / D-2-4 | 전문학사 / 학사 / 석사 / 박사 | `ENROLLED` 재학 중, `ON_LEAVE` 휴학 중, `COMPLETED` 수료, `GRADUATED` 졸업 |
| D-2-6 | 교환학생 | `IN_PROGRESS` 교환학생 진행 중, `COMPLETED` 교환학기 종료, `DISCONTINUED` 중단 |
| D-2-7 | 일·학습연계 | `IN_PROGRESS` 과정 참여 중, `ON_LEAVE_OR_DISCONTINUED` 휴학/중단, `COMPLETED` 과정 수료 |
| D-2-8 | 방문학생 | `IN_PROGRESS` 방문학기 진행 중, `COMPLETED` 방문학기 종료, `DISCONTINUED` 중단 |
| D-4-1 / D-4-7 | 한국어연수 / 외국어연수 | `IN_PROGRESS` 연수 중, `DISCONTINUED` 연수 중단, `COMPLETED` 연수 수료 |

실제 Supabase DB에는 배포 환경에서 Flyway V7 적용이 필요합니다. 동일한 비자 데이터를 수동으로 중복 등록하지 않습니다. 자동 테스트는 별도 PostgreSQL에서 검증하며 실제 Supabase 연결이나 데이터 변경을 수행하지 않습니다. Redis 설정은 필요하지 않습니다.

### 프로필 사진 저장소 설정

사진 파일은 Supabase Storage의 비공개 버킷에, 버킷 내부 경로는 `student_profile.profile_image_key`에 저장합니다. 기존 JWT로 본인 여부를 확인한 백엔드가 Storage API를 호출합니다.

1. 실제 DB의 Flyway 적용 상태를 확인하고 새 마이그레이션을 적용합니다. 테이블을 수동으로 중복 생성하지 않습니다.
2. Supabase Storage에서 `profile-images` 비공개 버킷을 생성합니다. 다른 이름을 사용하면 `SUPABASE_STORAGE_BUCKET`에 지정합니다.
3. 서버 환경에 `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `SUPABASE_STORAGE_BUCKET`을 설정합니다. Secret key는 서버에서만 사용하며 프론트엔드와 저장소에 포함하지 않습니다.
4. 실제 환경에서 사진 경로와 파일이 일치하는지, 발급한 URL로 사진을 조회할 수 있는지 확인합니다.

URL은 Supabase 프로젝트 기본 URL을 사용합니다. 비밀키는 [공식 API 키 안내](https://supabase.com/docs/guides/getting-started/api-keys)의 서버용 Secret key를 사용하고 `apikey` 헤더로 전달합니다. 임시 URL 발급은 [Storage API](https://supabase.com/docs/reference/javascript/storage-from-createsignedurl)의 `expiresIn`을 900초로 지정합니다.

사진이 없는 회원의 조회는 Storage 설정 없이도 동작합니다. 자동 테스트에서는 일회용 PostgreSQL과 테스트용 HTTP 서버를 사용하며, 실제 Supabase DB·Storage의 설정과 연결 확인은 별도로 수행해야 합니다.

## 팀원

| 이름 | 역할 | GitHub |
| --- | --- | --- |
| 박서은 | 조건·우선순위 기반 일자리 추천, 지원 및 채용 제안·수락, 최종 매칭 프로세스 구현 | [@1PSE](https://github.com/1PSE) |
| 박준서 | 인증·인가, 회원 및 유학생 프로필 관리, 사업주·사업장 및 채용공고 관리 | [@doondowob](https://github.com/doondowob) |

## 기술 스택

### Language / Framework

- Java 17
- Spring Boot 4.1.2
- Gradle 9.8.2 (Gradle Wrapper)
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Flyway

### Database / Storage

- PostgreSQL
- Supabase Storage: 비공개 프로필 사진의 임시 URL 발급, 실제 환경 설정·연결 확인 필요

### Authentication

- Spring Security
- Spring Security OAuth2 Resource Server의 JWT 지원
- HS256 서명 JWT
- BCrypt 비밀번호 해싱
- 쿠키 및 CSRF 보호 기반 토큰 재발급

### Deployment / Infrastructure

- TODO: 배포 환경 및 인프라 구성은 현재 저장소에서 확인되지 않음
- Testcontainers PostgreSQL (통합 테스트)

### API Documentation

- TODO: Swagger/OpenAPI 등 자동화된 API 문서 도구는 현재 의존성과 설정에서 확인되지 않음

## 프로젝트 구조

현재 소스의 실제 패키지 및 주요 디렉터리 구조입니다.

```text
HIRELINK-Backend/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── feature.md
│   │   ├── fix.md
│   │   └── other.md
│   └── PULL_REQUEST_TEMPLATE.md
├── docs/
│   └── convention/
│       ├── commit-message.md
│       └── git-workflow.md
├── gradle/wrapper/
├── src/
│   ├── main/
│   │   ├── java/com/hirelink/
│   │   │   ├── domain/
│   │   │   │   ├── auth/
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── service/
│   │   │   │   ├── member/
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── service/
│   │   │   │   └── student/
│   │   │   │       ├── controller/
│   │   │   │       ├── dto/
│   │   │   │       ├── entity/
│   │   │   │       ├── repository/
│   │   │   │       └── service/
│   │   │   ├── global/
│   │   │   │   ├── exception/
│   │   │   │   ├── security/
│   │   │   │   └── storage/
│   │   │   └── HirelinkBackendApplication.java
│   │   └── resources/
│   │       ├── db/migration/
│   │       └── application.yml
│   └── test/java/com/hirelink/
│       ├── domain/
│       │   ├── auth/
│       │   ├── member/
│       │   └── student/
│       └── global/storage/
├── build.gradle
├── gradlew
├── gradlew.bat
└── settings.gradle
```

## 로컬 개발 환경 설정

### 요구사항

- JDK 17
- PostgreSQL: 애플리케이션 로컬 실행 시 필요
- Docker: Testcontainers 기반 통합 테스트 실행 시 필요

Gradle은 저장소의 Wrapper를 사용하므로 별도 설치가 필요하지 않습니다. PostgreSQL 버전은 현재 저장소에 명시되어 있지 않습니다.

### 환경변수 설정

`src/main/resources/application.yml`이 요구하는 환경변수는 다음과 같습니다. 이 프로젝트는 `.env` 파일을 자동으로 읽도록 구성되어 있지 않습니다.

| 환경변수 | 필수 여부 | 설명 |
| --- | --- | --- |
| `DB_URL` | 필수 | PostgreSQL JDBC URL |
| `DB_USERNAME` | 필수 | 데이터베이스 사용자 |
| `DB_PASSWORD` | 필수 | 데이터베이스 비밀번호 |
| `JWT_SECRET` | 필수 | JWT HS256 서명 키. 32바이트 이상 난수를 Base64로 인코딩해 사용 |
| `AUTH_COOKIE_SECURE` | 선택 | 기본값 `true`. 로컬 HTTP 테스트에서만 `false` 사용 |
| `SUPABASE_URL` | 사진 조회 시 필수 | Supabase 프로젝트의 HTTPS 기본 URL |
| `SUPABASE_SECRET_KEY` | 사진 조회 시 필수 | 서버 전용 Secret key. 프론트엔드·소스 코드에 포함 금지 |
| `SUPABASE_STORAGE_BUCKET` | 선택 | 기본값 `profile-images`. 실제 비공개 버킷 이름 |

macOS/Linux 예시:

```shell
export DB_URL="jdbc:postgresql://localhost:5432/hirelink"
export DB_USERNAME="<DB 사용자>"
export DB_PASSWORD="<DB 비밀번호>"
export JWT_SECRET="<32바이트 이상 난수를 Base64로 인코딩한 서명 키>"
export AUTH_COOKIE_SECURE="false"
```

PowerShell 예시:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/hirelink"
$env:DB_USERNAME = "<DB 사용자>"
$env:DB_PASSWORD = "<DB 비밀번호>"
$env:JWT_SECRET = "<32바이트 이상 난수를 Base64로 인코딩한 서명 키>"
$env:AUTH_COOKIE_SECURE = "false"
```

비밀값은 저장소에 커밋하지 않습니다. 서버 재시작 후에도 기존 토큰을 검증하려면 같은 `JWT_SECRET`을 사용해야 합니다. HTTPS 환경에서는 `AUTH_COOKIE_SECURE`의 기본값 `true`를 유지합니다.

### 데이터베이스 실행

Docker Compose를 이용해 로컬 PostgreSQL 개발 환경을 실행할 수 있습니다.

프로젝트 루트의 `.env.example`을 참고하여 `.env` 파일을 생성하고, DB 비밀번호 등 필요한 환경변수를 설정합니다.

```bash
docker compose up -d db
```

PostgreSQL은 로컬 `5433` 포트로 연결되며, Docker Volume을 통해 데이터가 유지됩니다.

애플리케이션 실행 시 Flyway가 `src/main/resources/db/migration`의 SQL을 적용하고, JPA의 `ddl-auto: validate`가 Entity와 데이터베이스 스키마의 호환성을 검증합니다.

### 애플리케이션 실행

macOS/Linux:

```shell
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### API 문서

Swagger UI나 OpenAPI 문서 엔드포인트는 현재 구성되어 있지 않습니다. 구현된 인증 API는 이 README의 [현재 인증 API](#현재-인증-api)를 참고합니다.

### 테스트 실행

Docker가 실행 중인 환경에서 다음 명령을 사용합니다.

macOS/Linux:

```shell
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

`AuthIntegrationTest`는 Testcontainers가 생성한 일회용 PostgreSQL을 대상으로 실제 HTTP 요청을 보내 회원가입, 로그인, 입력 검증, 중복 가입, 트랜잭션 롤백, 쿠키 속성, 다중 로그인, 토큰과 CSRF 검증을 확인합니다. 로그아웃의 반복·동시 요청, 현재 로그인만 무효화되는지, 오류 발생 시 기존 기록이 유지되는지도 검증합니다. 개발 데이터베이스 접속값은 사용하지 않으며 테스트용 JWT 키도 실행 시 별도로 생성합니다.

`TermAgreementIntegrationTest`는 약관 초기 데이터, 동의 상태 변경과 시각 유지, 사용자 구분, 입력 오류, 없는 약관, 저장 실패 시 전체 롤백과 동시 요청을 확인합니다.

`StudentProfileIntegrationTest`는 프로필 등록 응답과 저장, 만 18세 경계, 입력 검증, 유학생·휴대전화 인증 조건, 중복·동시 등록 및 저장 실패 시 롤백을 확인합니다.

`StudentProfileReadIntegrationTest`는 본인 정보만 조회하는지, 미입력 값과 빈 경력 목록, 경력 정렬, 권한·미등록 오류와 이미지 주소 발급 실패를 확인합니다. `SupabaseStorageClientTest`는 테스트용 HTTP 서버로 900초 발급 요청, URL 인코딩, 외부 오류 처리와 리다이렉트 차단을 검증합니다.

`StudentProfileUpdateIntegrationTest`는 비자 초기 데이터, 미전송 필드 보존, TOPIK 삭제, 비자·학업 상태 조합, 입력·권한 오류, 동시 부분 수정 및 저장 실패 시 롤백을 검증합니다. 사진 서비스 장애가 프로필 수정을 막지 않는지도 확인합니다.

`StudentCareerCreateIntegrationTest`는 경력 저장과 프로필 조회 연동, 선택 입력, 월 형식·현재 월 경계·기간 순서, 입력·권한 오류 및 DB 저장 실패 처리를 검증합니다.

전체 빌드 검증이 필요하면 `./gradlew build` 또는 `.\gradlew.bat build`를 사용합니다.

## 기여

- [Git Workflow](docs/convention/git-workflow.md)
- [Commit Message Convention](docs/convention/commit-message.md)

Issue를 먼저 생성하고 작업 브랜치와 PR에 연결하는 흐름을 기본안으로 제안합니다. 세부 브랜치 전략, 리뷰 승인 수, merge 방식은 팀 합의 후 확정합니다.
