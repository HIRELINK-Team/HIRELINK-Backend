# HIRELINK-Backend

Java 17, Spring Boot, Gradle 기반의 HIRELINK 백엔드입니다.

## 실행

PostgreSQL에 개발용 데이터베이스를 준비한 후 접속 정보를 환경변수로 설정합니다.
아래 명령은 PowerShell 기준입니다. `.env` 파일을 자동으로 읽지는 않습니다.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/hirelink"
$env:DB_USERNAME = "<DB 사용자>"
$env:DB_PASSWORD = "<DB 비밀번호>"
$env:JWT_SECRET = "<32바이트 이상 난수를 Base64로 인코딩한 서명 키>"
$env:AUTH_COOKIE_SECURE = "false" # 로컬 HTTP 테스트에서만 설정
.\gradlew.bat bootRun
```

Flyway가 `db/migration`의 SQL을 적용하고, JPA의 `ddl-auto: validate`가 Entity와 DB의 호환성을 검증합니다.
`JWT_SECRET`은 서버의 비밀 환경변수로 관리하며 저장소에 넣지 않습니다. 서버를 다시 시작해도 같은 키를 사용해야 기존 토큰을 검증할 수 있습니다.
HTTPS 환경에서는 `AUTH_COOKIE_SECURE`의 기본값인 `true`를 사용합니다.

## LOCAL 이메일 회원가입

`POST /api/auth/signup`

```json
{
  "email": "member@example.com",
  "password": "password123",
  "role": "STUDENT"
}
```

- 이메일은 앞뒤 공백을 제거하고 소문자로 변환합니다.
- 역할은 `STUDENT`, `EMPLOYER`만 허용합니다.
- 비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다. 원문을 변형하지 않고 BCrypt로 해싱합니다.
- 별도 비밀번호 최대 길이 검증은 없습니다. 다만 현재 BCrypt 라이브러리는 UTF-8 72바이트 초과 입력을 거부하며, 인코더가 거부하는 입력에는 `400 INVALID_PASSWORD`를 반환합니다.
- 이름, 전화번호 등 가입 시 입력하지 않는 정보는 ERD의 nullable 컬럼으로 유지합니다. `preferred_language`의 초기값은 `KO`입니다.

성공 응답: `201 Created`

```json
{
  "userId": 1,
  "email": "member@example.com",
  "role": "STUDENT",
  "accessToken": "<Access Token>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

오류 응답은 `code`, `message`만 반환합니다.

| HTTP 상태 | code | 의미 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 잘못된 이메일·비밀번호·역할 또는 JSON 요청 |
| 400 | `INVALID_PASSWORD` | BCrypt 인코더가 처리할 수 없는 비밀번호 |
| 409 | `DUPLICATE_EMAIL` | 이미 사용 중인 이메일 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 |

```json
{
  "code": "DUPLICATE_EMAIL",
  "message": "이미 사용 중인 이메일입니다."
}
```

요청 처리 순서:

1. `SignupRequest`에서 이메일을 정규화하고 Controller의 `@Valid`로 입력을 검증합니다.
2. `AuthService`에서 이메일 중복을 조회하고 비밀번호를 BCrypt로 해싱합니다.
3. 하나의 트랜잭션에서 `UserAccount`와 연결된 `AuthAccount`를 저장합니다.
4. 동시 요청으로 발생하는 UNIQUE 제약 위반도 중복 이메일 오류로 변환합니다. 저장 중 실패하면 두 테이블의 변경을 모두 롤백합니다.
5. 토큰을 발급하고 Refresh Token의 검증용 해시를 저장한 뒤 `SignupResponse`를 반환합니다. 토큰 저장 실패 시 회원·인증 계정 생성도 롤백합니다.

## LOCAL 이메일 로그인

`POST /api/auth/login`

```json
{
  "email": "member@example.com",
  "password": "password123"
}
```

성공 응답: `200 OK`

```json
{
  "userId": 1,
  "role": "STUDENT",
  "accessToken": "<Access Token>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

이메일은 회원가입과 같은 방식으로 정규화합니다. 비밀번호는 변형하지 않고 저장된 해시와 비교합니다.
이메일이 존재하지 않는 경우와 비밀번호가 틀린 경우 모두 `401 INVALID_CREDENTIALS`를 반환합니다.
자격 증명이 일치하더라도 `withdrawn_at`에 값이 있으면 `403 WITHDRAWN_ACCOUNT`를 반환합니다.
요청 형식 오류는 `400 INVALID_INPUT`, 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`입니다.

## 토큰과 인증

- Access Token은 응답 본문으로 전달하며 유효기간은 1시간입니다.
- Refresh Token은 응답 본문에 넣지 않고 `refreshToken` HttpOnly 쿠키로 전달합니다.
- 쿠키의 기본 속성은 `Secure`, `SameSite=Lax`, `Path=/api/auth`, `Max-Age=1209600`입니다.
- Refresh Token은 발급 시점부터 14일 동안 유효하며 기존 기록의 만료일을 자동 연장하지 않습니다.
- 여러 로그인은 각각 별도 기록으로 유지합니다.
- `refresh_token` 테이블에는 토큰 원문 대신 SHA-256 해시, 회원 ID, 발급·만료 시각을 저장합니다.
- JWT는 HS256으로 서명합니다. 회원 ID는 `sub`, 역할은 `role`에 저장하고 발급자·발급/만료 시각·고유 ID·토큰 용도를 함께 검증에 사용합니다. 이메일·이름·전화번호는 넣지 않습니다.
- Refresh Token은 API 접근용 Access Token으로 사용할 수 없습니다.

회원가입·로그인 POST 외의 요청은 다음 헤더로 인증합니다.

```text
Authorization: Bearer <Access Token>
```

토큰 누락·서명 오류·만료는 `401 UNAUTHORIZED`, 접근 거부는 `403 FORBIDDEN`이며 응답은 동일한 `{ code, message }` 형식입니다.
Controller 메서드에 `@AuthenticationPrincipal Jwt jwt` 파라미터를 선언하면 Spring Security가 검증한 회원 정보를 사용할 수 있습니다.

```java
Long userId = Long.valueOf(jwt.getSubject());
String role = jwt.getClaimAsString("role");
```

기본 폼 로그인·HTTP Basic·로그아웃은 비활성화하고 서버 세션은 사용하지 않습니다.
현재 API 인증은 Bearer 헤더만 사용하므로 CSRF 검사를 비활성화했습니다. Refresh Token 쿠키를 읽는 재발급·로그아웃 API를 추가할 때 CSRF 방어를 함께 구성해야 합니다.
CORS 허용 주소와 브라우저 배포 환경에 맞는 쿠키 정책은 프론트 연동 시 확정합니다.

현재 구현에는 토큰 재발급·로그아웃 API와 휴대전화 인증 API가 포함되지 않습니다.

## 검증

Java 17과 실행 중인 Docker가 필요합니다.

```powershell
.\gradlew.bat build
```

`AuthIntegrationTest`는 Testcontainers의 일회용 PostgreSQL에서 실제 HTTP 요청을 보내 검증합니다.
테스트용 DB 접속값을 별도로 주입하므로 개발 DB를 사용하지 않습니다.
테스트용 JWT 서명 키도 실행 시 별도로 생성합니다.
회원가입과 로그인, 입력 검증, 동시 중복 가입, 트랜잭션 롤백, 쿠키 속성, 다중 로그인, 토큰 서명·만료·용도 검증을 확인합니다.
