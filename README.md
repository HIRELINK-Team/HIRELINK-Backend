# HIRELINK-Backend

Java 17, Spring Boot, Gradle 기반의 HIRELINK 백엔드입니다.

## 실행

PostgreSQL에 개발용 데이터베이스를 준비한 후 접속 정보를 환경변수로 설정합니다.
아래 명령은 PowerShell 기준입니다. `.env` 파일을 자동으로 읽지는 않습니다.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/hirelink"
$env:DB_USERNAME = "<DB 사용자>"
$env:DB_PASSWORD = "<DB 비밀번호>"
.\gradlew.bat bootRun
```

Flyway가 `db/migration`의 SQL을 적용하고, JPA의 `ddl-auto: validate`가 Entity와 DB의 호환성을 검증합니다.

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
  "role": "STUDENT"
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
5. 저장이 완료되면 `SignupResponse`를 반환합니다.

현재는 토큰 발급이나 로그인·재발급·로그아웃 기능이 없습니다. 회원가입 후 자동 로그인에 필요한 토큰 발급은 JWT 구현 단계에서 연결합니다.
Security는 기본 폼 로그인·HTTP Basic·로그아웃을 비활성화하고 모든 요청에 인증을 요구하지 않습니다. CSRF 검사는 회원가입 POST에 한해서 제외합니다.

## 검증

Java 17과 실행 중인 Docker가 필요합니다.

```powershell
.\gradlew.bat build
```

`AuthSignupIntegrationTest`는 Testcontainers의 일회용 PostgreSQL에서 실제 HTTP 요청을 보내 검증합니다.
테스트용 DB 접속값을 별도로 주입하므로 개발 DB를 사용하지 않습니다.
정상 가입, 입력 검증, 이메일 정규화, BCrypt 저장, 동시 중복 가입, 트랜잭션 롤백, 오류 응답을 확인합니다.
