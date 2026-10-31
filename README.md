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
- 휴대전화 인증을 완료한 사업주의 프로필 등록
- 사업주 본인의 프로필 조회·수정
- 사업주 본인의 사업장 목록·상세 조회 및 정보 수정
- 사업주 본인의 채용공고 목록 조회와 상태·임시저장 필터
- 사업주 본인 공고 및 유학생의 모집 중 공고 상세 조회
- 사업주 본인 사업장의 채용공고 등록·임시저장과 근무조건 검증
- 사업주 본인 공고의 부분 수정과 임시저장 공고의 모집 시작
- 사업주 본인 공고의 모집 마감과 실제 마감 시각 기록
- 국세청 상태조회 API를 통한 사업자등록번호 확인
- PaddleOCR을 통한 사업자등록번호·주소 인식과 사용자가 확인한 정보로 사업장 등록
- 유학생 본인의 프로필·경력 조회 및 비공개 프로필 사진의 임시 URL 발급
- 유학생 프로필 부분 수정 및 비자별 학업 상태 검증
- 유학생 본인의 근무 경력 추가·수정·삭제
- 유학생 본인의 매칭 조건 조회·저장·수정
- 업종·사업장·채용공고 테이블과 Entity, 공고 소유 사업주 관계

지원, 사업주 검토, 면접, 채용 및 매칭 기능은 후속 구현 범위입니다. 공고 마감에 따른 지원·채용 제안 종료 처리는 해당 기능과의 후속 연동이 필요합니다.

### 사업장·채용공고 데이터 구조

- `V8__create_workplace_and_job_posting.sql`은 `business_type`, `workplace`, `job_posting`을 생성합니다. 업종 초기 데이터 8종은 V10에서 등록합니다.
- `V13__create_job_posting_work_time.sql`은 기존 ERD의 `job_posting_work_time`을 생성합니다. 공고 ID, 요일, 시작·종료 시간은 필수이며 공고 테이블을 외래키로 참조합니다.
- `JobPosting.workplace → Workplace.employerUser → UserAccount` 관계로 공고 소유 사업주의 회원 ID를 확인할 수 있습니다. 연관 Entity는 지연 로딩하므로 필요한 데이터는 트랜잭션 안에서 조회합니다.
- 공고 상태는 `DRAFT`, `RECRUITING`, `CLOSED`이며 저장할 때 명시합니다. 임시저장을 위해 ERD의 선택 컬럼은 NULL을 허용합니다.
- 생성·수정 시각은 기존 회원 Entity와 같은 방식으로 기록합니다. 사업장과 공고의 업종은 각각 별도로 연결됩니다.
- 채용공고 등록 시 회원 역할과 사업장 소유권을 확인하고, 공고와 근무시간을 하나의 트랜잭션으로 저장합니다. 모집 상태의 필수 근무조건과 날짜를 검증하고 제목을 생성합니다.

### 현재 인증 API

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | LOCAL 이메일 회원가입 | 불필요 |
| `POST` | `/api/auth/login` | LOCAL 이메일 로그인 | 불필요 |
| `GET` | `/api/auth/csrf` | 쿠키 인증 요청용 CSRF 보안 확인값 조회 | 불필요 |
| `POST` | `/api/auth/refresh` | Access Token 재발급 | Refresh Token 쿠키 및 CSRF 확인값 |
| `POST` | `/api/auth/logout` | 현재 로그인 종료 | CSRF 확인값 필수, Refresh Token 쿠키 선택 |
| `POST` | `/api/auth/phone-verifications` | 휴대전화 인증번호 요청 | Access Token |
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

`SmsSender`가 솔라피 SMS API로 인증번호 발송을 요청합니다. 실제 발송에는 API Key·Secret과 인증된 발신번호 설정이 필요합니다.
설정 누락, 솔라피 접수 거절, 통신 오류·시간 초과는 `500 SMS_DELIVERY_FAILED`로 처리합니다. 외부 오류 응답이나 인증번호를 응답·로그로 제공하지 않습니다.

- 솔라피 접수 성공 응답: `200`, `{ "expiresIn": 300, "message": "인증번호가 전송되었습니다." }`. 접수 성공은 단말 수신 완료를 보장하지 않습니다.
- 인증번호는 6자리 숫자 문자열이며 DB에 해시만 저장합니다. 유효시간은 접수 성공 시점부터 300초입니다.
- 문자 내용은 `[HIRELINK] 인증번호 [6자리 코드]`이며 SMS로 발송합니다.
- 계정별·전화번호별로 각각 60초 간격, 최근 1시간 최대 5회까지 요청할 수 있습니다. 발송 실패도 요청 횟수에 포함합니다.
- 한 계정에는 현재 인증번호 하나만 유지합니다. 재전송 성공 시 교체하며, 실패하면 기존 인증번호를 유지합니다.
- 유효한 인증의 실패 횟수는 재전송·번호 변경으로 초기화하지 않습니다. 실패 횟수가 5회이면 만료 전까지 재전송을 차단합니다.
- 입력 오류 `400`, 로그인 필요 `401`, 탈퇴 계정 `403`, 요청 제한 `429`, 발송 실패 `500`을 반환합니다.
- PostgreSQL에 인증 정보와 발송 시도 기록을 저장합니다. 계정과 번호를 잠가 동시 요청도 제한을 적용하고, 다음 요청 시 해당 계정·번호의 1시간이 지난 발송 기록을 정리합니다.
- 문자 발송만으로 회원의 전화번호나 인증 완료 시각을 변경하지 않습니다.

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
- 실제 사용자 인증 흐름은 솔라피 발송 설정과 수신 확인을 마친 환경에서 사용할 수 있습니다.

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

### 사업주 프로필 등록 API

`POST /api/employers/me/profile`은 Access Token으로 식별한 사업주 본인의 프로필을 생성합니다.

```json
{
  "name": "테스트 이름",
  "acquisitionChannel": "REFERRAL"
}
```

- 이름은 필수이며 공백만으로 구성할 수 없고 최대 100자입니다. 유입 경로도 필수이며 `SEARCH`(검색), `REFERRAL`(지인 추천), `OTHER`(기타) 중 하나를 사용합니다. 기타의 별도 입력값은 받지 않습니다.
- 요청에는 `name`, `acquisitionChannel`만 허용합니다. `userId`, `phoneNumber`, `position` 등 명세에 없는 항목이나 잘못된 입력은 `400 INVALID_INPUT`입니다.
- 현재 회원의 역할이 `EMPLOYER`이며 `phone_number`, `phone_verified_at`이 모두 저장되어 있어야 합니다. 전화번호는 기존 인증 정보를 사용합니다.
- 이름과 유입 경로는 `user_account`에 저장하고, `employer_profile`은 회원과 같은 ID로 생성합니다. ERD의 `position`은 처음에 `NULL`입니다.
- 성공 응답은 `201 Created`이며 `employerId`, `name`, `email`, `phoneNumber`, `acquisitionChannel`, `position`을 반환합니다. 전화번호는 `010-0000-0000` 형식이며 응답에 `Cache-Control: no-store`를 설정합니다.
- 로그인 필요는 `401 UNAUTHORIZED`, 사업주가 아닌 계정은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다.
- 휴대전화 미인증은 `409 PHONE_NOT_VERIFIED`, 이미 등록된 프로필은 `409 DUPLICATE_EMPLOYER_PROFILE`, 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.
- 회원 정보 저장과 프로필 생성은 하나의 트랜잭션으로 처리합니다. 같은 회원의 동시 등록도 한 번만 성공하며, 실패한 요청은 기존 정보를 변경하지 않습니다.
- V12 마이그레이션은 `employer_profile` 테이블만 추가합니다. 사업장·채용공고 정보는 이 API에서 저장하지 않습니다.
- 실제 Supabase에는 해당 환경에서 Flyway 마이그레이션을 적용해야 합니다. 신규 회원의 휴대전화 인증에는 솔라피 발송 설정과 수신 확인이 필요합니다.

### 내 사업주 프로필 조회 API

`GET /api/employers/me/profile`은 Access Token으로 식별한 사업주 본인의 회원 정보와 프로필을 반환합니다. 요청 본문과 Query / Path Parameter는 사용하지 않습니다.

- 성공 응답은 `200 OK`이며 `employerId`, `name`, `email`, `phoneNumber`, `position`을 포함합니다. `employerId`는 회원 ID와 같습니다.
- 이름·이메일·전화번호는 `user_account`, 직책은 `employer_profile`에서 조회합니다. 전화번호는 `010-0000-0000` 형식이며, 직책 미등록 시 `position`은 `null`입니다.
- 유입 경로(`acquisitionChannel`)와 사업장 정보는 응답에 포함하지 않습니다. 사업장 조회는 별도 API의 범위입니다.
- 인증 정보가 없거나 유효하지 않으면 `401 UNAUTHORIZED`, 현재 회원이 사업주가 아니면 `403 FORBIDDEN`, 탈퇴 계정이면 `403 WITHDRAWN_ACCOUNT`입니다.
- 본인의 프로필이 없으면 `404 EMPLOYER_PROFILE_NOT_FOUND`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 조회는 기존 데이터를 생성하거나 수정하지 않으며, 응답에는 `Cache-Control: no-store`를 설정합니다. 기존 테이블을 사용하므로 새 마이그레이션은 없습니다.

### 내 사업주 프로필 수정 API

`PATCH /api/employers/me/profile`은 Access Token으로 식별한 사업주 본인의 이름과 직책을 부분 수정합니다.

```json
{
  "name": "수정 이름",
  "position": null
}
```

- 보내지 않은 항목은 기존 값을 유지합니다. 위 예시는 이름을 수정하고 기존 직책을 삭제합니다.
- 이름을 보내면 문자열·최대 100자이며 `null`, 빈 문자열, 공백만 입력할 수 없습니다.
- 직책을 보내면 문자열·최대 50자이며 빈 문자열과 공백만 입력할 수 없습니다. `position: null`은 직책 삭제를 뜻합니다.
- 이름은 `user_account.name`, 직책은 `employer_profile.position`에 하나의 트랜잭션으로 저장합니다. 같은 회원의 수정 요청은 순서대로 처리하며, 저장에 실패하면 요청의 변경을 모두 취소합니다.
- 빈 요청(`{}`), 잘못된 타입·길이·값, 수정 대상이 아닌 항목은 `400 INVALID_INPUT`입니다. `userId`, `email`, `phoneNumber`, `acquisitionChannel`, `preferredLanguage`는 수정 요청에 포함하지 않습니다.
- 이메일과 유입 경로는 이 API에서 변경하지 않습니다. 전화번호 변경은 기존 휴대전화 인증 API에서 처리합니다.
- 성공하면 `200 OK`와 조회 API와 같은 `employerId`, `name`, `email`, `phoneNumber`, `position`을 반환합니다. 전화번호는 `010-0000-0000` 형식이며 응답에는 `Cache-Control: no-store`를 설정합니다.
- 인증 정보가 없거나 유효하지 않으면 `401 UNAUTHORIZED`, 현재 회원이 사업주가 아니면 `403 FORBIDDEN`, 탈퇴 계정이면 `403 WITHDRAWN_ACCOUNT`입니다.
- 본인의 프로필이 없으면 `404 EMPLOYER_PROFILE_NOT_FOUND`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 기존 테이블과 조회 응답 DTO를 재사용하며 새 마이그레이션은 없습니다. 사업장 정보 수정은 별도 API의 범위입니다.

### 내 사업장 목록 조회 API

`GET /api/employers/me/workplaces`는 Access Token으로 식별한 사업주 본인의 사업장 목록을 반환합니다. 요청 본문과 Query / Path Parameter는 사용하지 않습니다.

- 성공하면 `200`과 `{ "workplaces": [...] }`를 반환하며, 등록된 사업장이 없으면 `200`과 `{ "workplaces": [] }`를 반환합니다.
- 각 항목은 `workplaceId`, `workplaceName`, `businessTypeId`, `businessTypeName`, `workplaceAddress`, `employeeCountRange`로 구성합니다. `businessTypeName`은 `FOOD_CAFE`, `OTHER` 등의 업종 코드입니다.
- 사업장 ID 오름차순으로 반환하며 페이지네이션은 적용하지 않습니다.
- 목록에는 사업자등록번호·대표자명·등록증 파일 정보·좌표를 포함하지 않습니다. 응답에는 `Cache-Control: no-store`를 적용합니다.
- 현재 DB의 회원 역할과 탈퇴 여부를 검사합니다. 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아니면 `403 FORBIDDEN`, 탈퇴 계정이면 `403 WITHDRAWN_ACCOUNT`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 읽기 전용 트랜잭션에서 현재 사용자가 소유한 사업장과 업종을 함께 조회합니다. 데이터 생성·수정과 외부 서비스 호출은 수행하지 않습니다.
- 기존 테이블과 Entity를 사용하며 새 마이그레이션이나 환경변수는 없습니다.

### 사업장 상세 조회 API

`GET /api/workplaces/{workplaceId}`는 로그인한 사업주가 본인 소유 사업장의 상세 정보를 조회합니다. 요청 본문과 Query Parameter는 사용하지 않으며 Path Parameter로 사업장 ID를 받습니다.

- 성공하면 `200`과 `workplaceId`, `workplaceName`, `businessRegistrationNumber`, `businessTypeId`, `businessTypeName`, `employeeCountRange`, `workplaceAddress`, `latitude`, `longitude`를 반환합니다.
- 사업자등록번호는 `123-45-67890` 형식으로 표시하며 저장된 값은 변경하지 않습니다. 위도·경도는 숫자로 반환하고 `businessTypeName`은 업종 코드입니다.
- 등록증 원본·파일 경로·파일 URL은 반환하지 않으며, 응답에는 `Cache-Control: no-store`를 적용합니다.
- 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아닌 계정이나 타인 소유 사업장 접근은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다.
- 사업장이 없으면 `404 WORKPLACE_NOT_FOUND`입니다. 사업장 ID를 Long으로 변환할 수 없으면 `400 INVALID_INPUT`, 예상하지 못한 내부 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 현재 회원 상태와 사업장 존재 여부·소유권을 확인한 뒤 DTO를 반환합니다. 읽기 전용 트랜잭션으로 조회하며 데이터 변경이나 OCR·국세청·파일 저장소 호출은 수행하지 않습니다.
- 기존 테이블과 Entity를 사용하며 새 마이그레이션이나 환경변수는 없습니다.

### 사업장 정보 수정 API

`PATCH /api/workplaces/{workplaceId}`는 로그인한 사업주가 본인 소유 사업장의 이름·업종·주소·좌표를 부분 수정합니다. 보내지 않은 항목은 기존 값을 유지합니다.

- JSON 요청에는 `workplaceName`, `businessTypeId`, `workplaceAddress`, `latitude`, `longitude`만 허용합니다. 사업장명은 공백 불가·최대 150자, 주소는 공백 불가·최대 500자입니다.
- 주소·위도·경도 중 하나라도 수정하려면 세 항목을 함께 보냅니다. 위도는 -90~90, 경도는 -180~180이며 좌표는 소수점 이하 7자리까지 허용합니다. 주소 검색과 지도 선택은 프론트엔드에서 처리합니다.
- 모든 항목의 명시적 `null`, 빈 요청, 잘못된 타입·길이·좌표, 알 수 없는 항목은 `400 INVALID_INPUT`입니다. 업종 ID는 JSON 정수, 좌표는 JSON 숫자로 보내야 합니다.
- 사업자등록번호·대표자명·직원 수·등록증 파일은 변경하지 않습니다. `OTHER` 업종에도 별도의 기타 업종명을 입력받지 않습니다.
- 성공하면 `200`과 상세 조회 API와 같은 9개 항목을 반환합니다. 사업자등록번호 표시 형식과 `Cache-Control: no-store`도 동일합니다.
- 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아닌 계정이나 타인 소유 사업장 접근은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다.
- 없는 사업장은 `404 WORKPLACE_NOT_FOUND`, 없는 업종은 `404 BUSINESS_TYPE_NOT_FOUND`입니다. 사업장 ID를 Long으로 변환할 수 없으면 `400 INVALID_INPUT`, 예상하지 못한 내부 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 하나의 트랜잭션으로 저장하며 중간에 실패하면 이번 수정 전체를 취소합니다. 수정할 사업장 행을 잠가 동시에 다른 항목을 수정할 때 기존 값이 덮어써지지 않도록 합니다.
- 기존 테이블과 Entity를 사용하며 새 마이그레이션·환경변수나 OCR·국세청·파일 저장소 호출은 없습니다.

### 내 채용공고 목록 조회 API

`GET /api/employers/me/job-postings`는 로그인한 사업주 본인의 사업장에 연결된 공고를 조회합니다. 요청 본문과 Path Parameter는 없으며 Query Parameter로 필터를 받습니다.

| status | includeDraft | 반환하는 상태 |
| --- | --- | --- |
| 미전달 | 미전달 또는 `false` | `RECRUITING`, `CLOSED` |
| 미전달 | `true` | `DRAFT`, `RECRUITING`, `CLOSED` |
| `RECRUITING` | 미전달 / `true` / `false` | `RECRUITING` |
| `CLOSED` | 미전달 / `true` / `false` | `CLOSED` |

- `status`는 `RECRUITING` 또는 `CLOSED`, `includeDraft`는 소문자 `true` 또는 `false`만 허용합니다. 빈 값이나 잘못된 값은 `400 INVALID_INPUT`입니다. 상태가 지정되어도 `includeDraft`의 형식은 검증합니다.
- 성공하면 `200`과 `{ "jobPostings": [...] }`를 반환합니다. 각 항목은 `jobPostingId`, `workplaceId`, `workplaceName`, `jobRole`, `wageType`, `wageAmount`, `status`, `applicationDeadlineDate`, `workTimes`, `createdAt`입니다.
- 임시저장에서 미입력된 `jobRole`, `wageType`, `wageAmount`, `applicationDeadlineDate`는 `null`, 근무시간이 없으면 `workTimes: []`입니다. 응답 상태에는 `DRAFT`도 포함될 수 있습니다.
- 공고는 생성 시각 내림차순, 같은 시각이면 ID 내림차순입니다. 근무시간은 월~일·시작 시간 순으로 반환하며 시간이 같으면 ID 순으로 정렬합니다. 시작·종료 시간은 `HH:mm`이고 종료 시간 `00:00`은 해당 요일의 자정입니다.
- 공고가 없거나 조건에 맞는 공고가 없으면 `200`과 `{ "jobPostings": [] }`를 반환합니다. 페이지네이션·지원자 수·화면 표시용 D-Day 문구는 포함하지 않습니다.
- 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아닌 계정은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`, 예상하지 못한 내부 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 읽기 전용 트랜잭션으로 공고와 사업장을 조회하고, 해당 공고들의 근무시간은 한 번에 조회합니다. 데이터 생성·수정·자동 마감이나 외부 서비스 호출은 하지 않으며 응답에는 `Cache-Control: no-store`를 적용합니다.
- 추가 환경변수·Redis 설정은 없습니다. 실제 DB에서는 배포 후 애플리케이션의 Flyway 실행으로 V13 적용 여부를 확인해야 합니다.

### 채용공고 상세 조회 API

`GET /api/job-postings/{jobPostingId}`는 로그인한 사용자가 공고의 사업장 정보와 근무조건을 조회합니다. 요청 본문과 Query Parameter는 없습니다.

- 현재 회원 정보가 사업주이면 본인 사업장의 `DRAFT`, `RECRUITING`, `CLOSED` 공고를 조회할 수 있습니다. 유학생이면 `RECRUITING` 공고만 조회할 수 있습니다.
- 성공하면 `200`과 `jobPostingId`, `title`, `status`, `workplaceId`, `workplaceName`, `workplaceAddress`, `latitude`, `longitude`, `businessTypeId`, `businessTypeName`, `jobRole`, `wageType`, `wageAmount`, `recruitmentCount`, `workStartDate`, `requiredKoreanLevel`, `preferredExperience`, `workTimeNegotiable`, `applicationDeadlineDate`, `workTimes`를 반환합니다. 응답에 `Cache-Control: no-store`를 적용합니다.
- 임시저장 공고에서 미입력된 제목·업종·근무조건은 `null`입니다. 해당 항목은 `title`, `businessTypeId`, `businessTypeName`, `jobRole`, `wageType`, `wageAmount`, `recruitmentCount`, `workStartDate`, `requiredKoreanLevel`, `preferredExperience`, `workTimeNegotiable`, `applicationDeadlineDate`입니다.
- `businessTypeId`와 `businessTypeName`은 공고에 선택된 업종의 ID·코드입니다. 공고의 업종이 없으면 사업장 업종으로 대체하지 않고 두 항목 모두 `null`로 반환합니다. 사업장명·주소·좌표는 연결된 사업장의 현재 정보를 반환합니다.
- `workTimes`는 없으면 `[]`이며, 월~일·시작 시간·ID 순으로 정렬합니다. 각 항목은 `dayOfWeek`, `startTime`, `endTime`이며 시간 형식은 `HH:mm`입니다. 종료 시간 `00:00`은 해당 요일의 자정입니다.
- 인증 오류는 `401 UNAUTHORIZED`, 다른 사업주의 공고 또는 유학생의 임시저장·마감 공고 접근은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다. 없는 공고는 `404 JOB_POSTING_NOT_FOUND`, 공고 ID를 Long으로 변환할 수 없으면 `400 INVALID_INPUT`, 예상하지 못한 내부 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식을 사용합니다.
- 읽기 전용 트랜잭션으로 조회합니다. 제목은 저장된 값을 반환하며, 마감일이 지나도 조회 시 상태를 변경하지 않습니다. 추천 결과·지원자 정보·사업자등록증 정보는 응답에 포함하지 않습니다.
- 기존 V8·V10·V13의 테이블과 업종 데이터를 사용하며 새 마이그레이션·환경변수·Redis 설정은 없습니다. OCR·국세청·파일 저장소도 호출하지 않습니다.

### 채용공고 등록·임시저장 API

`POST /api/job-postings`는 로그인한 사업주 본인의 사업장에 새 공고를 생성합니다. `status`에는 `DRAFT` 또는 `RECRUITING`을 명시해야 합니다. 기존 공고를 수정하거나 임시저장을 모집 상태로 전환하는 기능은 수정 API에서 처리합니다.

- `DRAFT`는 `workplaceId`와 `status`만 필수입니다. 나머지 항목은 생략하거나 `null`로 보낼 수 있습니다. `workTimes`의 생략·`null`·빈 배열은 모두 근무시간 없음으로 처리합니다. 입력한 값은 임시저장에서도 타입·길이·허용값·근무시간 검사를 통과해야 합니다.
- `RECRUITING`은 `businessTypeId`, `jobRole`, `recruitmentCount`, `wageType`, `wageAmount`, `workStartDate`, `requiredKoreanLevel`, `preferredExperience`, `applicationDeadlineDate`, `workTimeNegotiable`, `workTimes`가 모두 필수입니다. 근무시간 협의가 가능해도 `workTimes`는 한 개 이상이어야 합니다.
- ID는 양의 JSON 정수, 직무명은 공백 불가·최대 100자입니다. 채용 인원은 기존 SMALLINT 범위에 맞춰 1~32,767명, 급여는 1~2,147,483,647원의 JSON 정수로 받습니다. 숫자 문자열이나 소수는 허용하지 않습니다.
- 급여 유형은 `HOURLY` / `MONTHLY`, 한국어 수준은 `BEGINNER` / `INTERMEDIATE` / `ADVANCED`, 우대 경력은 `NONE` / `SIX_MONTHS_OR_MORE` / `ONE_YEAR_OR_MORE`입니다. 협의 여부는 JSON의 `true` / `false`로 받습니다.
- 날짜는 `YYYY-MM-DD` 형식입니다. 모집 시작 시 근무 시작일과 모집 마감일 모두 한국 시간(`Asia/Seoul`) 기준 오늘 또는 미래여야 합니다. 임시저장은 과거 날짜도 보관합니다. 두 날짜의 앞뒤 순서는 제한하지 않습니다.
- 근무시간은 `MON`~`SUN`과 `HH:mm` 형식의 30분 단위로 받습니다. 시작·종료가 같은 구간, 종료가 시작보다 이른 구간, 같은 요일의 중복·겹침은 400입니다. 바로 이어지는 구간은 허용합니다.
- 자정을 넘는 근무는 `MON 22:00~00:00`, `TUE 00:00~04:00`처럼 두 구간으로 나눠 전송합니다. 종료 `00:00`은 해당 요일의 자정이며 `00:00~00:00`은 허용하지 않습니다.
- 공고 제목은 `사업장명 + 공백 + 직무명 + 공백 + 모집` 형식입니다. 임시저장에서 직무가 없으면 제목도 `null`입니다. 200자를 넘으면 앞 199자와 `…`로 제목만 줄이고 사업장명·직무명은 보존합니다. 제목은 서버가 생성하며 Request로 받지 않습니다.
- 업종은 공고에 선택된 값을 저장하며 사업장 업종을 자동으로 복사하지 않습니다. `otherJobName`을 포함해 명세에 없는 필드는 받지 않습니다.
- 성공하면 `201`과 `jobPostingId`, `workplaceId`, `workplaceName`, `title`, `businessTypeId`, `businessTypeName`, `jobRole`, `recruitmentCount`, `wageType`, `wageAmount`, `workStartDate`, `requiredKoreanLevel`, `preferredExperience`, `applicationDeadlineDate`, `workTimeNegotiable`, `status`, `workTimes`, `createdAt`을 반환합니다. 미입력 값은 `null`, 근무시간이 없으면 `[]`이며 근무시간은 기존 조회 API와 동일한 순서·형식입니다. 응답에 `Cache-Control: no-store`를 적용합니다.
- 잘못된 입력은 `400 INVALID_INPUT`, 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아닌 계정·타인 사업장 접근은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다. 없는 사업장은 `404 WORKPLACE_NOT_FOUND`, 없는 업종은 `404 BUSINESS_TYPE_NOT_FOUND`, 예상하지 못한 저장 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류 응답은 기존 `{ code, message }` 형식을 사용합니다.
- 공고와 모든 근무시간은 함께 성공하거나 함께 취소됩니다. 동일한 POST 요청을 다시 보내면 별도의 새 공고를 생성합니다. 기존 공고 불러오기는 조회한 값을 프론트 입력 폼에 채운 뒤 새 POST 요청으로 등록하는 흐름입니다.
- 기존 V8·V10·V13을 사용하며 새 마이그레이션·환경변수·Redis 설정은 없습니다. 실제 사용에는 미리 등록된 본인 사업장이 필요합니다.

### 채용공고 수정 API

`PATCH /api/job-postings/{jobPostingId}`는 로그인한 사업주 본인의 공고를 부분 수정합니다. 보내지 않은 항목은 기존 값을 유지하며, 공고의 사업장은 변경할 수 없습니다.

- 수정 항목은 `businessTypeId`, `jobRole`, `recruitmentCount`, `wageType`, `wageAmount`, `workStartDate`, `requiredKoreanLevel`, `preferredExperience`, `applicationDeadlineDate`, `workTimeNegotiable`, `workTimes`, `status`입니다. 빈 요청, 각 항목의 `null`, 잘못된 타입과 알 수 없는 항목은 `400 INVALID_INPUT`입니다. `workplaceId`, `title`, `otherJobName`은 받지 않습니다. 기타 업종은 `OTHER`를 선택하고 구체적인 업무는 직무명으로 입력합니다.
- 입력한 값의 길이·숫자 범위·허용값·날짜 및 시간 형식은 등록 API와 같습니다. `DRAFT`는 일부만 저장할 수 있고, `RECRUITING`은 기존 값과 이번 요청을 합친 최종 상태가 등록 API의 필수 조건을 모두 충족해야 합니다.
- `DRAFT → RECRUITING` 전환 시 근무 시작일과 모집 마감일 모두 한국 시간(`Asia/Seoul`) 기준 오늘 또는 미래여야 합니다. `RECRUITING` 수정 시에는 이번에 보낸 날짜만 검사하며, 보내지 않은 기존 날짜는 과거여도 유지합니다. `DRAFT` 상태에서는 과거 날짜도 저장할 수 있습니다. 두 날짜의 앞뒤 순서는 제한하지 않습니다.
- 모집 중 공고는 임시저장으로 되돌릴 수 없으며 마감한 공고는 수정하거나 재모집할 수 없습니다. 이 경우 `409 JOB_POSTING_STATE_CONFLICT`를 반환합니다. 요청의 `status`는 `DRAFT` 또는 `RECRUITING`만 허용하며 `CLOSED`는 `400 INVALID_INPUT`입니다. 모집 마감은 별도 API에서 처리합니다.
- `workTimes`를 보내면 기존 목록 전체를 교체합니다. `[]`로 전부 삭제하는 것은 최종 상태가 `DRAFT`일 때만 가능합니다. 근무시간 협의 여부와 관계없이 `RECRUITING`은 근무시간이 한 개 이상이어야 합니다. 요일·30분 단위·중복·겹침·자정 분할 규칙은 등록 API와 같습니다.
- 제목은 등록과 동일하게 `사업장명 + 공백 + 직무명 + 공백 + 모집`으로 다시 생성합니다. 직무명이 없으면 제목은 `null`이고, 200자를 넘으면 제목만 앞 199자와 `…`로 줄입니다. 기존 사업장·생성 시각·마감 시각·기타 업종명 컬럼은 변경하지 않습니다.
- 성공하면 `200`과 등록 API의 응답 중 `createdAt`을 `updatedAt`으로 바꾼 18개 항목을 반환합니다. 임시저장에서 미입력된 값은 `null`, 근무시간이 없으면 `[]`입니다. 근무시간은 월~일·시작 시간·ID 순으로 반환하며 `Cache-Control: no-store`를 적용합니다. 근무시간만 수정해도 `updatedAt`을 갱신합니다.
- 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아닌 계정·타인 공고 접근은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다. 없는 공고는 `404 JOB_POSTING_NOT_FOUND`, 없는 업종은 `404 BUSINESS_TYPE_NOT_FOUND`, 공고 ID를 Long으로 변환할 수 없으면 `400 INVALID_INPUT`, 예상하지 못한 저장 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식입니다.
- 공고 행을 잠가 같은 공고의 수정 요청을 순서대로 처리합니다. 공고 정보와 근무시간 교체는 하나의 트랜잭션으로 처리하며, 중간에 실패하면 기존 공고와 근무시간을 그대로 유지합니다.
- 기존 V8·V10·V13을 사용하며 새 마이그레이션·환경변수·Redis 설정은 없습니다. OCR·국세청·파일 저장소 호출도 없습니다.

### 채용공고 모집 마감 API

`PATCH /api/job-postings/{jobPostingId}/close`는 로그인한 사업주가 본인 사업장의 모집 중 공고를 마감합니다. 요청 본문과 Query Parameter는 없습니다.

- 현재 회원 정보의 역할과 탈퇴 여부를 확인하고 공고 소유권을 검사합니다. `RECRUITING` 상태만 `CLOSED`로 변경할 수 있습니다.
- `closedAt`에 실제 처리 시점의 서버 시각을 기록하고 `updatedAt`도 갱신합니다. 제목·모집조건·근무시간·생성 시각은 그대로 유지하며 공고를 삭제하지 않습니다. 모집 마감일이 지나도 현재 상태가 `RECRUITING`이면 수동 마감할 수 있습니다.
- 성공하면 `200`과 `jobPostingId`, `status`, `closedAt`을 반환합니다. `status`는 `CLOSED`, `closedAt`은 ISO-8601 형식이며 모두 null이 아닙니다. 응답에는 `Cache-Control: no-store`를 적용합니다.
- `DRAFT` 또는 이미 `CLOSED`인 공고는 `409 JOB_POSTING_STATE_CONFLICT`입니다. 같은 요청을 다시 보내도 최초 마감 시각은 변경하지 않습니다. 같은 공고의 동시 마감 요청은 한 건만 성공하고 나머지는 409를 반환합니다.
- 수정 API와 동일한 공고 행 잠금을 사용하고 하나의 트랜잭션으로 처리합니다. 저장 실패 시 상태와 시각의 변경을 모두 취소합니다.
- 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아닌 계정·타인 공고 접근은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다. 없는 공고는 `404 JOB_POSTING_NOT_FOUND`, 공고 ID를 Long으로 변환할 수 없으면 `400 INVALID_INPUT`, 예상하지 못한 저장 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류는 기존 `{ code, message }` 형식입니다.
- 마감한 공고는 기존 수정 API에서 수정·재모집할 수 없습니다. 사업주는 본인 마감 공고를 조회할 수 있지만 유학생의 상세 조회는 기존 정책대로 403입니다. 다시 모집하려면 기존 조회 결과를 입력 폼에 채운 뒤 등록 API로 새 공고를 생성합니다.
- 검토 중인 지원과 대기 중인 채용 제안을 종료하고 이미 완료된 채용을 유지하는 것은 최종 정책입니다. 현재 API는 공고 상태와 마감 시각까지만 처리하며, 지원·채용 제안 종료는 해당 기능이 준비된 후 별도로 연결해야 합니다.
- 기존 V8의 컬럼을 사용하므로 새 마이그레이션·환경변수·Redis 설정은 없습니다. 외부 서비스 호출이나 날짜에 따른 자동 마감도 수행하지 않습니다.

### 사업자등록번호 확인 API

`POST /api/workplaces/business-registration/verify`는 로그인한 사업주가 입력한 사업자등록번호의 현재 영업 상태를 확인합니다.

```json
{
  "businessRegistrationNumber": "123-45-67890"
}
```

- 입력은 문자열 숫자 10자리 또는 `123-45-67890` 형식이며, 앞뒤 공백은 제거합니다. 누락·NULL·잘못된 타입이나 형식·알 수 없는 요청 항목은 `400 INVALID_INPUT`입니다.
- Access Token으로 사용자를 식별하고 DB의 현재 회원 상태와 역할을 확인합니다. 인증 오류는 `401 UNAUTHORIZED`, 사업주가 아니면 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`입니다.
- 국세청 조회가 정상 완료되면 `200`과 `businessRegistrationNumber`, `valid`, `message`를 반환합니다. 응답 번호는 하이픈을 포함하며 `Cache-Control: no-store`를 사용합니다.
- 국세청 상태 코드 `01`(계속사업자)만 `valid=true`입니다. `02`(휴업), `03`(폐업), 미등록 번호는 `valid=false`입니다. 과세 유형으로 가입을 제한하지 않습니다.
- 메시지는 `true`일 때 `확인 가능한 사업자등록번호입니다.`, `false`일 때 `확인할 수 없는 사업자등록번호입니다.`입니다.
- 인증키 미설정, 외부 연결 실패·시간 초과·오류 응답·해석할 수 없는 응답은 `502 BUSINESS_REGISTRATION_LOOKUP_FAILED`입니다. 예상하지 못한 내부 오류는 `500 INTERNAL_SERVER_ERROR`입니다. 오류 응답은 기존 `{code, message}` 형식을 사용합니다.
- 조회 결과를 저장하거나 사업장을 생성하지 않습니다. 이 확인은 사업자의 영업 상태에 대한 조회이며, 요청자가 해당 사업자의 대표자인지 증명하지 않습니다. 대표자명은 반환하지 않습니다.
- 외부 호출은 연결 제한 3초·응답 제한 5초를 사용하며 리다이렉트를 따라가지 않습니다. 외부 오류의 원문이나 인증키를 API 응답에 포함하지 않습니다.

실제 연동에는 [국세청 사업자등록정보 진위확인 및 상태조회 서비스](https://www.data.go.kr/data/15081808/openapi.do)의 활용 신청과 인증키가 필요합니다. 공공데이터포털의 **일반 인증키(Decoding) 원문**을 서버 환경변수 `NTS_SERVICE_KEY`로 설정합니다. URL 인코딩은 서버가 수행하므로 사전에 인코딩하지 않습니다. 키를 프론트엔드나 저장소에 포함하지 않습니다.

로컬 테스트는 테스트용 HTTP 서버를 사용합니다. 실제 국세청 조회는 인증키 설정 후 별도로 확인해야 합니다. 이 기능에는 Supabase 스키마 변경이나 Redis 설정이 필요하지 않습니다.

### 사업자등록증 OCR API

`POST /api/workplaces/business-registration/ocr`는 로그인한 사업주가 올린 등록증에서 사업자등록번호와 사업장 주소를 추출합니다. `multipart/form-data`를 사용합니다.

| key | 설명 | value 타입 | 옵션 | Nullable |
| --- | --- | --- | --- | --- |
| businessRegistrationFile | 사업자등록증 파일 | File | PDF / JPG / PNG, 최대 5MB, 파일 하나 | X |

성공하면 `200 OK`와 다음 응답을 반환합니다.

| key | 설명 | value 타입 | 옵션 | Nullable |
| --- | --- | --- | --- | --- |
| businessRegistrationNumber | 인식한 사업자등록번호 | String | 123-45-67890 형식 | X |
| workplaceAddress | 인식한 사업장 주소 | String | 사용자 확인·수정, 미인식·후보 충돌 시 null | O |

```json
{
  "businessRegistrationNumber": "123-45-67890",
  "workplaceAddress": "서울 마포구 월드컵북로 00"
}
```

| status | response content |
| --- | --- |
| 200 | 번호 인식 성공, 주소는 미인식 시 null |
| 400 | 파일 누락·빈 파일·추가 요청 항목·잘못된 전송 형식 |
| 401 | 로그인 필요 |
| 403 | 사업주가 아니거나 탈퇴 계정 |
| 413 | 파일 또는 전체 업로드 요청 크기 초과 |
| 415 | 지원하지 않는 파일 형식 또는 Content-Type과 파일 헤더 불일치 |
| 422 | 번호 미인식 또는 서로 다른 번호 후보를 하나로 결정할 수 없음 |
| 502 | OCR 프로그램 연결 실패·처리 실패·시간 초과·잘못된 응답 |
| 500 | 예상하지 못한 서버 오류 |

- OCR은 입력 보조 기능입니다. 프론트엔드는 번호와 주소를 미리 채우고 사용자가 확인·수정하도록 합니다. 주소를 못 읽으면 빈 칸으로 표시하여 직접 입력하게 합니다. 최종 주소는 주소 검색·지도에서 확인하고 좌표와 함께 등록합니다.
- 같은 호스트의 Python OCR 프로그램에서 PaddleOCR을 CPU로 실행합니다. 사업자번호와 주소만 반환하며 대표자명·전체 인식 결과는 응답하지 않습니다. 원본은 외부 OCR 서비스로 전송하지 않습니다.
- 파일명·확장자만 신뢰하지 않고 Content-Type과 PDF/JPG/PNG 파일 헤더를 확인합니다. 손상되거나 인식되지 않는 문서는 실패할 수 있습니다.
- 파일은 요청 처리 중 메모리에서 다룹니다. 파일당 5MB, 전체 multipart 요청 6MB, 디스크 저장 전환 기준 6MB로 구성하여 허용된 업로드를 디스크에 쓰지 않습니다. 요청 종료 후 원본을 보관하지 않으며 DB·Storage에 업로드 원본, 파일 경로, 전체 OCR 결과를 저장하지 않습니다.
- 내부 OCR 호출은 연결 제한 3초·응답 제한 40초이며 리다이렉트나 자동 재시도를 사용하지 않습니다. Python 프로그램은 한 번에 한 문서를 처리하고, 처리 중인 경우 새 요청을 빠르게 실패시킵니다. 원본·인식 결과·오류 원문은 로그에 기록하지 않습니다.
- 성공 응답에는 `Cache-Control: no-store`를 적용합니다. 모든 오류는 기존 `{code, message}` 형식을 사용합니다.
- OCR 실행 방법은 아래 로컬 실행 안내를 참고합니다. 모델은 이미지 빌드 시 내려받으며 인식은 내려받은 모델로 처리합니다. 원본 보관용 볼륨이나 Storage 버킷은 만들지 않습니다.

### 사업장 등록 API

`POST /api/workplaces`는 로그인한 사업주가 확인한 정보를 받아 사업장을 생성합니다. 요청은 `application/json`이며 파일은 포함하지 않습니다.

| key | 설명 | value 타입 | 옵션 | Nullable |
| --- | --- | --- | --- | --- |
| workplaceName | 사업장명 | String | 공백 불가, 최대 150자 | X |
| businessRegistrationNumber | 확정한 사업자등록번호 | String | 숫자 10자리 또는 123-45-67890, 앞뒤 공백 제거 | X |
| businessTypeId | 업종 ID | Long | 기존 업종 ID | X |
| employeeCountRange | 직원 수 구간 | String | UNDER_5 / FIVE_OR_MORE | X |
| workplaceAddress | 사업장 전체 주소 | String | 공백 불가, 최대 500자 | X |
| latitude | 위도 | Decimal | -90~90, 소수점 이하 7자리 | X |
| longitude | 경도 | Decimal | -180~180, 소수점 이하 7자리 | X |

```json
{
  "workplaceName": "모모카페 연남점",
  "businessRegistrationNumber": "123-45-67890",
  "businessTypeId": 1,
  "employeeCountRange": "UNDER_5",
  "workplaceAddress": "서울 마포구 월드컵북로 00",
  "latitude": 37.5665000,
  "longitude": 126.9780000
}
```

성공하면 `201 Created`와 사업장 상세 조회와 동일한 9개 항목(`workplaceId`, `workplaceName`, `businessRegistrationNumber`, `businessTypeId`, `businessTypeName`, `employeeCountRange`, `workplaceAddress`, `latitude`, `longitude`)을 반환합니다. 모두 NULL이 아니며, 번호는 하이픈 포함 형식입니다.

| status | response content |
| --- | --- |
| 201 | 사업장 등록 성공 |
| 400 | 필수값 누락·NULL·잘못된 타입/값·알 수 없는 항목, 휴업·폐업·미등록 번호 |
| 401 | 로그인 필요 |
| 403 | 사업주가 아니거나 탈퇴 계정 |
| 404 | 존재하지 않는 업종 |
| 409 | 이미 등록된 사업자등록번호 |
| 415 | application/json이 아닌 요청 형식 |
| 502 | 국세청 설정 누락·조회 실패 |
| 500 | 예상하지 못한 서버 오류 |

- Access Token과 DB의 현재 회원 상태로 요청자를 확인하며, `employerUserId`는 받지 않습니다. 한 사업주는 여러 사업장을 등록할 수 있습니다.
- 최종 요청의 번호로 국세청 상태를 다시 조회하며, 계속사업자만 등록합니다. OCR 결과나 사전 확인 버튼의 결과를 영업 상태 확인으로 대신하지 않습니다.
- OCR 호출 이력은 저장하지 않으며, 등록 요청은 사용자가 확정한 값을 받습니다. 이 흐름은 등록증 진위나 요청자의 사업자 소유권을 증명하지 않습니다.
- 번호는 하이픈 없이 저장하며, DB UNIQUE 제약으로 동시 중복 등록도 차단합니다. 중복은 `409 DUPLICATE_BUSINESS_REGISTRATION_NUMBER`입니다.
- 주소와 좌표는 프론트엔드에서 확인한 값을 저장합니다. OCR 주소는 수정할 수 있는 후보이며 좌표를 생성하지 않습니다. 대표자명과 OTHER 업종의 별도 업종명은 받지 않습니다.
- 등록은 하나의 트랜잭션으로 처리합니다. 국세청 조회나 DB 저장에 실패하면 사업장을 생성하지 않습니다.
- `V15__allow_workplace_without_registration_file.sql`은 기존 파일 경로 컬럼의 NOT NULL만 해제하며 기존 값은 보존합니다. 새 등록에서는 파일 경로를 NULL로 저장합니다. 등록증용 Storage 버킷이나 Redis는 사용하지 않습니다.
- `V16__allow_workplace_without_representative_name.sql`은 대표자명 컬럼의 NOT NULL만 해제합니다. 기존 값은 보존하고 새 등록에서는 NULL로 저장하며, 사업장 등록·상세 조회·수정 응답에 대표자명을 포함하지 않습니다.
- 성공 응답에는 `Cache-Control: no-store`를 적용합니다. 실제 국세청 조회에는 서버의 `NTS_SERVICE_KEY` 설정이 필요합니다.

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
- 신규 회원의 휴대전화 인증에는 솔라피 발송 설정과 수신 확인이 필요합니다. 통합 테스트는 일회용 DB에 인증된 회원을 준비해 등록을 검증합니다.

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

### 매칭 조건 조회 API

`GET /api/students/me/matching-preferences`는 Access Token으로 식별한 유학생 본인의 희망 업종·근무시간·지역·최소 희망 시급·우선순위를 조회합니다. 요청 본문과 Query / Path parameter는 없습니다.

- 성공 시 `200 OK`이며 Entity 대신 `StudentMatchingPreferenceResponse`를 반환합니다.
- 프로필은 있지만 아직 조건을 입력하지 않았다면 아래 응답을 반환합니다. 조회 중 기본값을 DB에 저장하지 않습니다. 일부 조건만 저장된 경우에는 해당 조건을 반환하고 나머지는 입력 전 상태로 둡니다.
- `businessTypeFlexible=true`이면 `preferredJobs`는 항상 빈 배열입니다. `false`인 상태에서도 아직 업종을 선택하지 않았다면 빈 배열일 수 있습니다.
- 희망 업종은 업종 ID 순, 근무시간은 `MON`부터 `SUN`까지 요일·시작 시간·종료 시간 순, 우선순위는 `priorityOrder` 오름차순으로 반환합니다.
- 시간은 `HH:mm`, 좌표는 ERD의 `NUMERIC(10,7)` 값을 사용합니다. `otherJobName`은 `OTHER` 업종에만 반환하며 나머지는 `null`입니다.
- 사용자별 우선순위를 자동 지정하지 않습니다. `criterionId`는 기준을 식별하는 번호이며 사용자가 정한 순서인 `priorityOrder`와 다릅니다. ERD의 `weight`는 유지하지만 응답에 포함하거나 계산하지 않습니다.
- 인증 오류는 `401 UNAUTHORIZED`, 유학생이 아닌 계정은 `403 FORBIDDEN`, 탈퇴 계정은 `403 WITHDRAWN_ACCOUNT`, 프로필 미등록은 `404 STUDENT_PROFILE_NOT_FOUND`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.
- 저장·수정은 같은 경로의 `PATCH` API를 사용합니다.

```json
{
  "businessTypeFlexible": false,
  "preferredJobs": [],
  "preferredWorkTimes": [],
  "preferredLocation": null,
  "minimumHourlyWage": null,
  "priorities": []
}
```

V9는 ERD에 맞춰 `priority_criterion`, `student_preferred_job`, `student_preferred_work_time`, `student_preferred_location`, `student_job_preference`, `student_priority`를 생성합니다. 기존 `business_type`을 재사용하며, V10에서 아래 기준 데이터를 등록합니다. 한글 표시명은 프론트엔드에서 사용하며 DB에는 기존 `name` / `criterion_name` 컬럼에 영문 코드를 저장합니다.

| businessTypeId | businessTypeName | 화면 표시명 |
| --- | --- | --- |
| 1 | `FOOD_CAFE` | 음식점·카페 |
| 2 | `RETAIL` | 매장·판매 |
| 3 | `OFFICE_EVENT_TOURISM` | 사무·행사·관광 |
| 4 | `INTERPRETATION_TRANSLATION` | 통·번역 |
| 5 | `DUTY_FREE` | 면세점 |
| 6 | `MAJOR_RELATED` | 전공·학업 |
| 7 | `INTERNSHIP` | 인턴십 |
| 8 | `OTHER` | 기타 |

| criterionId | criterionName | 화면 표시명 |
| --- | --- | --- |
| 1 | `WORK_TIME` | 근무시간 |
| 2 | `WAGE` | 시급 |
| 3 | `DISTANCE` | 거리 |
| 4 | `BUSINESS_TYPE` | 업종 |

실제 Supabase DB에서는 배포 시 Flyway V9·V10 적용이 필요합니다. 기존에 업종 데이터를 수동 등록했다면 ID·코드 충돌 여부를 먼저 확인해야 하며, 자동으로 기존 데이터를 덮어쓰지 않습니다. 기준 데이터가 추가되거나 변경되면 기존 마이그레이션을 수정하지 않고 새 파일로 관리합니다. 이 조회 API에는 Redis·Storage 설정이 필요하지 않습니다.

### 매칭 조건 저장·수정 API

`PATCH /api/students/me/matching-preferences`는 프로필이 있는 유학생 본인의 조건을 저장·수정합니다. 보내지 않은 항목은 유지하며, 성공 시 `200 OK`와 조회 API와 같은 전체 조건을 반환합니다.

```json
{
  "preferredWorkTimes": [
    { "dayOfWeek": "MON", "startTime": "22:00", "endTime": "00:00" },
    { "dayOfWeek": "TUE", "startTime": "00:00", "endTime": "02:00" }
  ],
  "minimumHourlyWage": null
}
```

- 업종을 수정하려면 `businessTypeFlexible`과 `preferredJobs`를 함께 보냅니다. `true`이면 빈 목록, `false`이면 한 개 이상의 업종을 보내며 같은 업종을 중복 선택할 수 없습니다. 전달된 목록으로 기존 희망 업종을 전체 교체합니다.
- `OTHER`의 `otherJobName`은 공백이 아닌 문자열로 최대 100자입니다. 다른 업종에서는 생략하거나 `null`로 보냅니다.
- `preferredWorkTimes`를 보내면 기존 시간 목록을 전체 교체하며, `[]`는 전체 삭제입니다. 요일은 `MON`~`SUN`, 시간은 `HH:mm` 형식이며 분은 `00` 또는 `30`만 허용합니다.
- 종료의 `00:00`은 해당 요일이 끝나는 자정, 시작의 `00:00`은 해당 요일이 시작되는 시각입니다. 자정을 넘는 근무시간은 프론트엔드가 위 예시처럼 요일별로 나눠 보냅니다. `24:00`, 시작·종료가 같은 구간, 같은 요일의 중복·겹치는 구간은 허용하지 않습니다. 바로 이어지는 구간은 허용합니다.
- `preferredLocation`은 위치명·위도·경도·반경을 모두 보내 기존 위치를 교체합니다. 위치명은 공백 불가·최대 150자, 위도는 -90~90, 경도는 -180~180, 좌표는 소수점 이하 7자리까지, 반경은 1 이상의 정수(미터)입니다. `null`로 위치를 삭제하는 기능은 제공하지 않습니다.
- `minimumHourlyWage`는 1 이상의 정수(원) 또는 `null`입니다. 명시적인 `null`은 기존 시급을 초기화합니다.
- `priorities`를 보내면 근무시간·시급·거리·업종 네 기준을 모두 보내야 합니다. `criterionId`는 중복될 수 없으며 `priorityOrder`는 1~4를 각각 한 번씩 사용합니다. `[]`나 `null`로 우선순위를 삭제할 수 없습니다.
- 가중치는 이 API에서 계산하지 않습니다. 신규 기준 또는 순위가 바뀐 기준의 `weight`는 `null`로 저장하고, 같은 기준의 순위가 유지되면 기존 가중치를 보존합니다. 우선순위를 생략하면 순위·가중치 모두 유지합니다. `weight`는 요청·응답에 포함하지 않습니다.
- 한 요청의 변경은 하나의 트랜잭션으로 처리하며 오류가 나면 전체 취소합니다. 같은 회원의 동시 수정은 순서대로 처리하여 생략한 필드가 다른 요청으로 덮어써지지 않게 합니다.
- 빈 요청, 알 수 없는 항목, 잘못된 자료형·범위·조합, 허용하지 않은 `null`은 `400 INVALID_INPUT`, 없는 업종·우선순위 기준은 `404 MATCHING_REFERENCE_NOT_FOUND`입니다. 회원·프로필 오류와 예상하지 못한 서버 오류는 조회 API와 같습니다.

`V11__allow_unassigned_student_priority_weight.sql`은 `student_priority.weight`의 NOT NULL 제약만 제거합니다. 기존 값과 다른 컬럼·제약은 유지합니다. ERD도 `weight NUMERIC(6,3)`을 NULL 허용으로 맞춰야 합니다. 실제 Supabase에는 배포 시 V11 적용이 필요하며, 가중치 계산과 Redis 도입은 이 API에 포함하지 않습니다.

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
| `NTS_SERVICE_KEY` | 사업자등록번호 확인 시 필수 | 국세청 API 일반 인증키(Decoding) 원문. 미설정 시 확인 요청은 502 |
| `PADDLE_OCR_URL` | 선택 | 기본값 `http://localhost:8091/recognize`. 로컬 OCR 주소이며 인증키 불필요 |
| `SOLAPI_API_KEY` | 인증 문자 발송 시 필수 | 솔라피 API Key. 미설정 시 발송 요청은 500 |
| `SOLAPI_API_SECRET` | 인증 문자 발송 시 필수 | 솔라피 API Secret. 미설정 시 발송 요청은 500 |
| `SOLAPI_SENDER_NUMBER` | 인증 문자 발송 시 필수 | 솔라피에 등록·인증한 발신번호. 하이픈 없이 입력 |

실제 발송 설정값은 실행 환경의 환경변수로 전달합니다. `.env.example`의 해당 항목은 빈 값으로 유지합니다.

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

### 로컬 OCR 실행

Docker가 실행 중인 상태에서 다음 명령으로 OCR 프로그램을 별도로 시작합니다.

```bash
docker compose --profile ocr up -d --build ocr
```

- 최초 빌드는 Python 패키지와 한국어 모델 다운로드가 필요합니다. 이후에는 빌드된 이미지를 재사용합니다.
- Java 서버를 호스트에서 실행하면 기본 `PADDLE_OCR_URL`을 그대로 사용합니다. Docker 내부에서 실행하는 경우 같은 내부 네트워크에 연결하고 `http://ocr:8000/recognize`를 사용합니다.
- OCR 포트는 호스트의 `127.0.0.1:8091`에만 연결하며 외부에 공개하지 않습니다. 문서 인식에 외부 API나 인터넷 연결은 필요하지 않습니다.
- CPU 2개·메모리 2GB 한도를 설정하며 한 프로세스에서 한 문서씩 처리합니다. 긴 문서나 시간 초과는 502로 처리될 수 있습니다.
- 컨테이너는 일반 사용자와 읽기 전용 파일 시스템으로 실행합니다. 임시 시스템 파일용 `/tmp`만 메모리에 두며 업로드 문서는 파일로 저장하지 않습니다.
- 중지하려면 `docker compose --profile ocr stop ocr`를 실행합니다.

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

`EmployerProfileCreateIntegrationTest`는 사업주 프로필 등록 응답과 저장, 입력 검증, 현재 회원의 역할·휴대전화 인증 조건, 중복·동시 등록 및 저장 실패 시 회원 정보와 프로필의 전체 롤백을 검증합니다. 일회용 PostgreSQL을 사용하며 실제 Supabase 데이터는 변경하지 않습니다.

`EmployerProfileReadIntegrationTest`는 본인 정보만 반환하는지, 직책의 NULL 응답, 조회 전후 데이터 보존, 인증·권한·미등록 오류와 DB 조회 실패 시 안전한 오류 응답을 검증합니다.

`EmployerProfileUpdateIntegrationTest`는 부분 수정과 조회 연동, 직책 삭제, 입력·권한·미등록 오류, 다른 회원과 수정 대상이 아닌 정보의 보존, 저장 실패 시 전체 롤백과 동시 부분 수정을 검증합니다.

`BusinessRegistrationIntegrationTest`는 번호 정규화, 성공·실패 응답, 입력·권한 검사와 DB 데이터 보존을 확인합니다. `NtsBusinessRegistrationClientTest`는 테스트용 HTTP 서버로 요청 형식과 인증키 인코딩, 사업자 상태 판정, 설정 누락·응답 오류·연결 실패·시간 초과·리다이렉트 차단을 검증합니다. 실제 인증키나 국세청 조회는 사용하지 않습니다.

`WorkplaceRegistrationIntegrationTest`는 OCR 결과 확인 후 등록, 파일 제한·원본 미보관, 권한·입력·동시 중복 등록·롤백을 확인합니다. `PaddleOcrClientTest`는 테스트용 HTTP 서버로 바이너리 요청·응답과 실패 처리를 검증합니다. `ocr/tests`는 모델 다운로드 없이 필드 추출과 요청·오류 처리를 검증합니다. 테스트는 가상 입력만 사용합니다.

`WorkplaceListIntegrationTest`는 본인 사업장만 반환하는지, 응답 항목과 ID 정렬, 빈 목록, 조회 전후 데이터 보존, 인증·권한 검사와 DB 조회 실패 시 안전한 오류 응답을 검증합니다. 일회용 PostgreSQL에 테스트용 사업장을 만들며 실제 Supabase 데이터는 변경하지 않습니다.

`WorkplaceDetailIntegrationTest`는 상세 응답 항목과 번호 표시 형식, 좌표, 원본 파일 정보 제외, 소유권·회원 상태 검사, 없는 사업장과 잘못된 ID, 서버 오류 및 조회 전후 데이터 보존을 검증합니다. 일회용 PostgreSQL을 사용하며 실제 Supabase 데이터는 변경하지 않습니다.

`WorkplaceUpdateIntegrationTest`는 부분 수정과 변경 불가 항목 보존, 입력 검증·주소/좌표 동시 전달, 소유권·회원 상태, 미존재 오류, 동시 수정과 DB 저장 실패 시 롤백을 검증합니다. 일회용 PostgreSQL을 사용하며 실제 Supabase 데이터는 변경하지 않습니다.

`JobPostingListIntegrationTest`는 본인 공고만 조회하는지, 공고·근무시간 정렬, 필터 우선순위, 임시저장 미입력 값, 빈 목록, 인증·권한·서버 오류와 데이터 보존을 검증합니다. `JobPostingEntityIntegrationTest`는 근무시간 Entity 저장과 새 테이블의 외래키·필수 컬럼도 확인합니다. 모두 일회용 PostgreSQL을 사용합니다.

`JobPostingDetailIntegrationTest`는 상세 응답과 근무시간 정렬, 공고 업종과 사업장 업종 구분, 임시저장 미입력 값, 현재 회원 역할·소유권·공고 상태에 따른 조회 권한, 입력·인증·미등록·서버 오류 및 데이터 보존을 검증합니다. 일회용 PostgreSQL을 사용하며 실제 Supabase 데이터는 변경하지 않습니다.

`JobPostingCreateIntegrationTest`는 등록·임시저장 저장과 기존 목록·상세 조회 연동, 필수값·날짜·근무시간 검증, 소유권·회원 상태, 제목 길이와 원본 보존, 근무시간 저장 실패 시 전체 롤백을 확인합니다. 실제 Supabase에 접근하지 않고 일회용 PostgreSQL을 사용합니다.

`JobPostingUpdateIntegrationTest`는 부분 수정과 기존 값 보존, 임시저장의 모집 시작, 공고 상태별 날짜 검사, 근무시간 교체·삭제와 수정 시각, 권한·입력·상태 오류 및 저장 실패 시 전체 롤백을 확인합니다. 일회용 PostgreSQL을 사용합니다.

`JobPostingCloseIntegrationTest`는 마감 상태·시각 저장과 기존 정보 보존, 목록·상세·수정 API의 마감 후 동작, 반복·동시 마감, 권한·입력 오류 및 저장 실패 시 롤백을 확인합니다. 일회용 PostgreSQL을 사용하며 실제 Supabase 데이터는 변경하지 않습니다.

`StudentProfileReadIntegrationTest`는 본인 정보만 조회하는지, 미입력 값과 빈 경력 목록, 경력 정렬, 권한·미등록 오류와 이미지 주소 발급 실패를 확인합니다. `SupabaseStorageClientTest`는 테스트용 HTTP 서버로 900초 발급 요청, URL 인코딩, 외부 오류 처리와 리다이렉트 차단을 검증합니다.

`StudentProfileUpdateIntegrationTest`는 비자 초기 데이터, 미전송 필드 보존, TOPIK 삭제, 비자·학업 상태 조합, 입력·권한 오류, 동시 부분 수정 및 저장 실패 시 롤백을 검증합니다. 사진 서비스 장애가 프로필 수정을 막지 않는지도 확인합니다.

`StudentCareerCreateIntegrationTest`는 경력 저장과 프로필 조회 연동, 선택 입력, 월 형식·현재 월 경계·기간 순서, 입력·권한 오류 및 DB 저장 실패 처리를 검증합니다.

`StudentMatchingPreferenceReadIntegrationTest`는 기준 데이터와 ID 생성, 입력 전·부분 입력 상태, 본인 조건 조회와 정렬, 권한 검사, DB 제약 및 안전한 오류 응답을 검증합니다. 테스트는 일회용 PostgreSQL에서 실행하며 실제 Supabase 데이터를 변경하지 않습니다.

`StudentMatchingPreferenceUpdateIntegrationTest`는 최초 저장·부분 수정·목록 교체, 자정 및 시간 중복, 입력·권한 오류, 가중치 유지·초기화, 전체 롤백과 동시 최초 수정을 검증합니다.

전체 빌드 검증이 필요하면 `./gradlew build` 또는 `.\gradlew.bat build`를 사용합니다.

## 기여

- [Git Workflow](docs/convention/git-workflow.md)
- [Commit Message Convention](docs/convention/commit-message.md)

Issue를 먼저 생성하고 작업 브랜치와 PR에 연결하는 흐름을 기본안으로 제안합니다. 세부 브랜치 전략, 리뷰 승인 수, merge 방식은 팀 합의 후 확정합니다.
