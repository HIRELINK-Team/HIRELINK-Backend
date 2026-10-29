# HIRELINK Backend

외국인 유학생과 사업주를 연결하는 구인·구직 매칭 플랫폼 **HIRELINK**의 백엔드 서버입니다.

## 주요 기능 소개

HIRELINK는 외국인 유학생의 구직 조건과 사업주의 채용 정보를 연결하는 서비스를 목표로 합니다. 현재 저장소에서 확인되는 구현 범위는 다음과 같습니다.

- LOCAL 이메일 회원가입 및 로그인
- BCrypt 비밀번호 해싱
- JWT Access Token 발급 및 Bearer 인증
- HttpOnly 쿠키 기반 Refresh Token 저장과 Access Token 재발급
- Refresh Token 원문 대신 SHA-256 해시 저장
- 재발급 API의 CSRF 보호
- 회원·인증 정보와 Refresh Token의 PostgreSQL 영속화
- Flyway 마이그레이션과 JPA 스키마 검증
- Testcontainers PostgreSQL을 사용한 인증 통합 테스트

구인 공고, 지원, 사업주 검토, 면접, 채용 및 매칭 기능은 이 README 작성 시점의 소스에서 확인되지 않아 후속 구현 범위로 남겨 둡니다.

### 현재 인증 API

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | LOCAL 이메일 회원가입 | 불필요 |
| `POST` | `/api/auth/login` | LOCAL 이메일 로그인 | 불필요 |
| `GET` | `/api/auth/csrf` | 재발급용 CSRF 보안 확인값 조회 | 불필요 |
| `POST` | `/api/auth/refresh` | Access Token 재발급 | Refresh Token 쿠키 및 CSRF 확인값 |

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

폼 로그인, HTTP Basic, 서버 세션, Spring Security 기본 로그아웃은 사용하지 않습니다. 로그아웃 API와 휴대전화 인증 API는 현재 소스에서 확인되지 않습니다.

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
- 별도 파일·오브젝트 스토리지는 현재 설정 및 소스에서 확인되지 않음

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
│   │   │   │   └── member/
│   │   │   │       ├── entity/
│   │   │   │       └── repository/
│   │   │   ├── global/
│   │   │   │   ├── exception/
│   │   │   │   └── security/
│   │   │   └── HirelinkBackendApplication.java
│   │   └── resources/
│   │       ├── db/migration/
│   │       └── application.yml
│   └── test/java/com/hirelink/domain/auth/
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

`AuthIntegrationTest`는 Testcontainers가 생성한 일회용 PostgreSQL을 대상으로 실제 HTTP 요청을 보내 회원가입, 로그인, 입력 검증, 중복 가입, 트랜잭션 롤백, 쿠키 속성, 다중 로그인, 토큰과 CSRF 검증을 확인합니다. 개발 데이터베이스 접속값은 사용하지 않으며 테스트용 JWT 키도 실행 시 별도로 생성합니다.

전체 빌드 검증이 필요하면 `./gradlew build` 또는 `.\gradlew.bat build`를 사용합니다.

## 기여

- [Git Workflow](docs/convention/git-workflow.md)
- [Commit Message Convention](docs/convention/commit-message.md)

Issue를 먼저 생성하고 작업 브랜치와 PR에 연결하는 흐름을 기본안으로 제안합니다. 세부 브랜치 전략, 리뷰 승인 수, merge 방식은 팀 합의 후 확정합니다.
