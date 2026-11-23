# Google/Kakao 소셜 인증

## 현재 제공하는 기능

- Google/Kakao 인증, 기존 회원 로그인, 신규 사용자의 30분 가입 진행 정보 보관
- 실제 서비스는 Google/Kakao 로그인을 사용하며, LOCAL 이메일 회원가입·로그인은 개발 환경에서 `LOCAL_LOGIN_ENABLED=true`로 명시적으로 활성화한 경우에만 제공한다. 기본값은 `false`이며 비활성화 시 유효한 LOCAL 가입·로그인 요청은 `403 FORBIDDEN`을 반환한다.
- 약관 저장, 가입 중 휴대전화 인증, 신규 회원 생성, 온보딩 단계 판정은 후속 API에서 제공

## 프론트 연결 순서

1. 랜딩 페이지에서 선택한 회원 유형으로 브라우저 페이지를 이동한다.
   - 유학생: `GET /api/auth/social/google?role=STUDENT`
   - 사업주: `GET /api/auth/social/kakao?role=EMPLOYER`
   - 제공자는 `google` 또는 `kakao`, 유형은 `STUDENT` 또는 `EMPLOYER`이다. `fetch` 대신 페이지 이동을 사용한다.
2. 제공자 로그인 후 백엔드 콜백이 인가 코드를 교환하고 ID Token을 검증한다.
3. 백엔드가 `socialSignup` 쿠키를 설정하고, 설정된 프론트 결과 페이지로 이동시킨다. URL에 토큰을 넣지 않는다.
4. 결과 페이지에서 `GET /api/auth/csrf`를 호출한다.
5. 응답의 `token`을 `X-XSRF-TOKEN` 헤더에 넣어 `POST /api/auth/social/result`를 호출한다. 요청 본문은 없고 같은 브라우저의 쿠키를 함께 전송한다.

### 신규 사용자

```json
{
  "status": "SIGNUP_REQUIRED",
  "role": "STUDENT",
  "email": null,
  "signupExpiresAt": "2026-11-16T10:30:00Z",
  "login": null
}
```

아직 회원 계정과 HIRELINK 토큰을 만들지 않는다. 프론트는 약관·휴대전화 인증 단계로 연결한다. 현재 이슈에서는 그 이후 가입 완료 요청을 제공하지 않는다.

### 기존 회원

```json
{
  "status": "LOGIN",
  "role": "EMPLOYER",
  "email": "member@example.com",
  "signupExpiresAt": null,
  "login": {
    "userId": 1,
    "role": "EMPLOYER",
    "accessToken": "access-token-example",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

`login.accessToken`을 메모리에 보관하고 보호된 API의 `Authorization: Bearer ...` 헤더에 사용한다. Refresh Token은 응답 본문 대신 기존 방식의 HttpOnly 쿠키로 설정한다. 이후 재발급·로그아웃은 기존 API와 CSRF 규칙을 사용한다. 로그인별 식별값을 공유하는 접근·리프레시 토큰은 일반 로그아웃 시 해당 로그인만 함께 무효화된다([로그아웃 계약](./logout-session.md)). 기존 회원의 결과는 한 번만 교환할 수 있다.

회원은 이메일이 아닌 `(provider, providerUserId)`로 식별한다. 이메일이 같아도 서로 다른 소셜 계정을 자동으로 연결하지 않는다. 소셜 이메일이 없거나 확인되지 않았다면 `null`이다. 선택한 유형이 기존 회원 유형과 다르면 `SOCIAL_ROLE_MISMATCH`로 실패하며 기존 유형을 바꾸지 않는다.

## 쿠키와 만료

| 쿠키 | 용도 | Path | HttpOnly | Secure | SameSite | 기간 |
| --- | --- | --- | --- | --- | --- | --- |
| `JSESSIONID` | 소셜 제공자로 이동하기 전 인가 요청의 state·nonce·PKCE 정보 연결 | `/api/auth/social` | O | O | Lax | 인가 요청 최대 30분, 콜백 처리 후 세션 폐기 |
| `socialSignup` | 소셜 인증 완료 후 가입 진행 또는 기존 회원 결과 교환 | `/api/auth` | O | O | Lax | 발급 후 30분 |
| `refreshToken` | HIRELINK Access Token 재발급 | `/api/auth` | O | O | Lax | 기존 정책인 14일 |

Domain은 지정하지 않는다. 로컬 HTTP 개발에서만 `AUTH_COOKIE_SECURE=false`를 사용한다.

- `socialSignup` 원문은 브라우저 쿠키에만 두고, DB에는 SHA-256 해시와 제공자·고유 ID·유형·선택 이메일·생성/만료 시각을 저장한다. Google/Kakao 토큰은 이 테이블에 저장하지 않는다.
- **30분이 되면 즉시 사용 불가**다. 결과를 다시 조회해도 유효시간은 늘어나지 않는다.
- 만료 행은 앱 실행 중 1분 간격으로 정리한다. 앱이 꺼져 있으면 물리 삭제는 재시작 후 정리 작업에서 이루어지며, 만료된 정보로 가입을 진행할 수는 없다.
- 임시 정보가 만료되면 소셜 인증부터 다시 시작한다. 이미 생성된 회원 계정의 만료 정책이 아니다.
- 인가 요청은 서버 메모리의 짧은 HTTP 세션을 사용한다. 소셜 인증 도중 서버가 재시작되면 다시 인증해야 한다.

## 실패 처리

| 위치 | 응답 | 프론트 처리 |
| --- | --- | --- |
| 시작 API | 400 `INVALID_INPUT` | 제공자·회원 유형 확인 |
| 시작/결과 API | 503 `SOCIAL_LOGIN_UNAVAILABLE` | 소셜 로그인 설정 전 안내 |
| 소셜 콜백 | 결과 페이지의 `?error=SOCIAL_AUTH_FAILED` | 인증 실패 안내 후 재시도 |
| 소셜 콜백 | `?error=SOCIAL_ROLE_MISMATCH` 또는 `WITHDRAWN_ACCOUNT` | 가입한 회원 유형 안내 또는 탈퇴 계정 안내 |
| 결과 API | 401 `INVALID_SOCIAL_SESSION` | 소셜 인증 다시 시작 |
| 결과 API | 403 `INVALID_CSRF_TOKEN` | CSRF 조회와 쿠키 전송 확인 |
| 결과 API | 403 `SOCIAL_ROLE_MISMATCH` 또는 `WITHDRAWN_ACCOUNT` | 유형/회원 상태 안내 |
| 시작/결과 API | 500 `INTERNAL_SERVER_ERROR` | 서버 오류 안내 |

콜백 오류는 리다이렉트의 `error` 값으로 전달한다. Controller의 오류 응답은 기존 `{ "code", "message" }` 형식을 사용한다.

## 환경 설정

기본값은 비활성화다. 실제 개발자 앱 설정을 끝낸 뒤 서버 환경변수로 활성화한다.

| 변수 | 값/설명 |
| --- | --- |
| `SOCIAL_LOGIN_ENABLED` | 설정 완료 후 `true` |
| `SOCIAL_CALLBACK_BASE_URL` | 브라우저가 사용하는 프론트 origin. 로컬 예: `http://localhost:3000` |
| `SOCIAL_FRONTEND_REDIRECT_URI` | 프론트 인증 결과 페이지. 로컬 예: `http://localhost:3000/auth/social/callback` |
| `GOOGLE_CLIENT_ID` | Google 웹 애플리케이션 OAuth Client ID |
| `GOOGLE_CLIENT_SECRET` | Google Client Secret |
| `KAKAO_CLIENT_ID` | Kakao REST API 키 |
| `KAKAO_CLIENT_SECRET` | Kakao Client Secret |

Google과 Kakao 개발자 콘솔에 각각 아래 Redirect URI를 등록한다. Kakao는 카카오 로그인과 OpenID Connect, Client Secret을 활성화한다.

- `${SOCIAL_CALLBACK_BASE_URL}/api/auth/social/callback/google`
- `${SOCIAL_CALLBACK_BASE_URL}/api/auth/social/callback/kakao`

배포에서는 HTTPS를 사용한다. Callback base와 프론트 결과 페이지는 같은 origin이어야 하며 query/fragment를 포함하지 않는다. Vercel을 거치는 환경에서는 **시작·콜백·CSRF·결과 API 모두 같은 프론트 도메인의 `/api/*` rewrite로 호출**한다. 콜백에 API 전용 도메인을 혼용하면 프론트 도메인에 필요한 쿠키를 전달할 수 없다. 프론트 결과 페이지 경로는 `/api/*` 밖에 만든다.

`compose.deploy.yaml`은 서버의 `backend.env`를 읽으므로 위 변수를 해당 비공개 파일에 설정할 수 있다. 실제 키는 저장소나 이미지에 넣지 않는다. 외부 제공자 로그인은 실제 키와 등록된 Redirect URI가 준비된 뒤 별도 확인해야 한다.

## DB와 명세 변경

- Flyway V17: `social_auth_session` 추가, `user_account.email` NULL 허용 및 전역 UNIQUE 해제
- LOCAL 가입 이메일 중복은 기존 `(provider, provider_user_id)` UNIQUE로 계속 차단
- 회원·프로필 응답의 `email`은 소셜 가입을 위해 Nullable로 변경
- Swagger: 소셜 시작/결과 API, 쿠키·CSRF·실패 예시, DTO Nullable 반영
