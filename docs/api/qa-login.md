# QA 계정 이메일 로그인

일반 회원은 Google/Kakao로 로그인한다. 배포 환경에서 기존 LOCAL 테스트 계정이 필요한 경우에만 서버 설정으로 해당 이메일의 로그인을 허용한다.

## 서버 설정

```dotenv
LOCAL_LOGIN_ENABLED=false
QA_LOGIN_ALLOWED_EMAILS=qa.student@example.com,qa.employer@example.com
```

위 이메일은 예시다. 실제 허용 목록은 서버의 비공개 환경 파일에만 설정한다. `compose.deploy.yaml`은 `/etc/hirelink/backend.env` 또는 `BACKEND_ENV_FILE`로 지정한 파일을 읽는다. 이 기능을 포함한 백엔드 이미지를 배포하고, 환경 파일 변경 후 컨테이너를 재생성해야 적용된다.

- 기본값은 빈 목록이며, 설정하지 않으면 기존처럼 LOCAL 로그인을 차단한다.
- 쉼표로 구분한 이메일은 앞뒤 공백을 제거하고 소문자로 비교한다. 완전한 이메일이 일치해야 하며 와일드카드나 도메인 단위 허용은 지원하지 않는다.
- `user_account`뿐 아니라 해당 회원에 연결된 `auth_account`의 `provider=LOCAL`, `provider_user_id=정규화된 이메일`, 기존 비밀번호 해시가 필요하다. 허용 목록만으로 계정이나 비밀번호를 만들지 않는다.
- LOCAL 회원가입은 계속 차단한다. 유학생·사업주 권한이나 온보딩·인증 조건을 우회하지 않는다.
- 허용 목록은 새 이메일 로그인 요청에 적용한다. 기존 로그인 종료에는 일반 로그아웃을 사용한다.

## 요청과 응답

`POST /api/auth/login`에 기존 계정의 `email`, `password`를 보낸다. 요청 형식과 성공 응답은 기존 LOCAL 로그인과 같다. 실제 비밀번호는 문서·저장소에 넣지 않는다.

| 상황 | 응답 |
| --- | --- |
| 허용된 기존 LOCAL 계정, 비밀번호 일치, 탈퇴하지 않음 | `200`, 기존 `userId`·`role`·접근 토큰 및 `refreshToken` 쿠키 |
| LOCAL 비활성화, 허용 목록에 없는 이메일 | `403 FORBIDDEN` |
| 허용된 이메일이지만 LOCAL 인증 정보가 없거나 비밀번호 불일치 | `401 INVALID_CREDENTIALS` |
| 비밀번호가 일치하지만 탈퇴한 계정 | `403 WITHDRAWN_ACCOUNT` |
| 잘못된 요청 형식 | `400 INVALID_INPUT` |
| LOCAL 비활성화 상태에서 회원가입 요청 | QA 허용 목록에 있어도 `403 FORBIDDEN` |

접근 토큰은 보호 API의 `Authorization: Bearer ...`에 사용한다. 재발급·로그아웃은 기존 쿠키·CSRF 및 [로그인 세션 계약](./logout-session.md)을 따른다. 두 기기에서 로그인한 뒤 한 기기에서 로그아웃하면 해당 로그인만 무효화된다.

## 적용 확인

1. CI에서 `QaLoginIntegrationTest`, `LocalAuthDisabledIntegrationTest`, `AuthIntegrationTest`, `AuthServiceDefaultConfigurationTest`, `OpenApiIntegrationTest`를 검증한다.
2. 대상 서버에 변경된 이미지와 허용 목록이 적용됐는지 확인한다.
3. 유학생·사업주 QA 계정 각각 로그인 후 기존 회원 ID·유형이 유지되는지 확인한다.
4. 발급된 토큰으로 보호 API를 호출하고, 재발급·로그아웃이 정상 동작하는지 확인한다.

응답이 계속 `403 FORBIDDEN`이면 대상 서버의 배포 버전과 허용 목록 적용 여부를 확인한다. `401 INVALID_CREDENTIALS`이면 기존 LOCAL 인증 정보와 비밀번호를 확인한다. 이 변경은 DB 구조나 기존 계정 데이터를 변경하지 않으므로 별도 Flyway 마이그레이션은 추가하지 않는다.
