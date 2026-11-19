# 유학생 가입 등록증 OCR · 진위 확인

## 이번 범위

- 계정 생성 전 등록증 앞면 · 뒷면 OCR, APICK 기재 정보 일치 확인, PDF 제출 필요 여부 반환
- 기존 PaddleOCR 서버를 확장하며 별도 OCR 모델 · 의존성을 추가하지 않는다.
- 체류기간 PDF 검증, 휴대전화 가입 시 등록증 결과 필수 검사 · 회원 정보 이전, 온보딩 일괄 저장 · 상태 조회는 후속 작업이다.
- 이 기능만으로 등록증 검증을 우회한 계정 생성을 차단하지 않는다. 전체 가입 흐름 공개는 후속 연결 작업까지 완료한 뒤 진행한다.

## 프론트 연결

1. 기존 소셜 인증으로 `socialSignup` 쿠키를 받는다. 계정은 아직 생성되지 않는다.
2. `GET /api/auth/social/terms`에서 약관을 조회한다. 기능이 활성화된 유학생에게는 필수 `고유식별정보 수집·이용` 항목이 추가된다. 사업주 또는 기능 비활성 상태에는 기존 약관 목록을 유지한다. ID는 응답값을 사용한다.
3. 확정된 약관 본문을 표시하고 별도 체크박스로 동의를 받는다. `PUT /api/auth/social/terms`에 다른 필수 약관과 함께 저장한다. 기존 동의 시각 보존 정책을 따른다. 동의 문서 · 번역은 프론트에서 제공하며 DB의 항목명만으로 법적 요건을 갖췄다고 간주하지 않는다.
4. 카메라로 앞면을 촬영해 `POST /api/auth/social/registration/front`로 보낸다. 응답을 확인하고 틀리면 재촬영한다. 값을 직접 수정하는 API는 없다.
5. 카메라로 뒷면을 촬영해 `POST /api/auth/social/registration/back`로 보낸다. 서버가 보관한 앞면 값과 뒷면 OCR 값으로 APICK을 호출한다.
6. `requiresStayDocument=true`이면 후속 PDF 검증이 필요하다. false이면 카드에서 읽은 체류 만료일이 한국 날짜 기준 지나지 않은 상태다. 이 응답은 계정 생성 · 온보딩 완료가 아니다.

두 POST는 `multipart/form-data`이며 **`registrationFile` 파일 파트 한 개만** 허용한다. JPG/PNG, 파일 최대 5MB, 전체 요청 최대 6MB다. PDF · 추가 파일 · 폼 필드는 거절한다. 파일 이름을 경로로 사용하거나 원본 이미지를 저장하지 않는다. 프론트는 사진 선택 UI 없이 촬영만 제공하지만, 서버가 이미지의 카메라 출처를 증명하는 기능은 아니다.

쿠키와 `GET /api/auth/csrf`에서 받은 `X-XSRF-TOKEN` 헤더를 함께 보낸다. Bearer 토큰은 이 API의 인증 수단이 아니며 전달해도 CSRF를 우회하지 않는다. 응답은 `Cache-Control: no-store`로 취급하고 원문을 브라우저 영구 저장소 · 분석 도구에 기록하지 않는다.

앞면 응답 예시 (가상 데이터):

```json
{
  "name": "TEST STUDENT",
  "nationalityCode": "VN",
  "visaCode": "D-2",
  "maskedRegistrationNumber": "000101-*******",
  "issueDate": "2020-01-01",
  "signupExpiresAt": "2026-11-20T10:30:00Z"
}
```

뒷면 응답 예시:

```json
{
  "cardVerified": true,
  "stayExpiresOn": "2026-11-20",
  "requiresStayDocument": false,
  "signupExpiresAt": "2026-11-20T10:30:00Z"
}
```

## 판정과 재촬영

- 앞면: 이름 · 국적 · 체류자격 · 등록번호 · 발급일이 명확해야 한다. 국가 코드는 ISO 코드와 Java의 영문 · 한글 국가명으로 변환한다. 변환 불가 · 상충하는 값은 재촬영 대상이다. 체류자격은 기존 프로필 지원 범위를 유지한다. 세부 코드를 임의로 상위 코드로 바꾸지 않는다.
- 뒷면: 명시된 체류 만료일 또는 허가일 · 만료일 표를 인식한다. 표는 **가장 최근 허가일의 행**을 사용하며 같은 허가일에 상충하는 만료일이 있으면 실패한다. 발급일 · 주소 변경일 등에서 아무 날짜나 가져오지 않는다. 표의 여러 열을 구별하지 못하면 실패한다.
- 2011-01-01 이전 발급 카드만 일련번호 없이 진위 확인할 수 있다. 그 외에는 11자리 일련번호가 필요하다.
- APICK HTTP 성공 · `api.success` · 과금 여부만으로 통과시키지 않는다. 오류가 없고 `data.success=1`, `data.result=1`일 때만 성공이다. 불일치 0은 422, 미확정 · 시간 초과 · 잘못된 응답은 502다. 자동 재시도는 하지 않는다.
- 카드 기재 정보의 일치 확인이며 실물 위변조 · 얼굴 대조 · PDF 진위 · 최신 체류 상태까지 확인하는 것은 아니다.
- 날짜는 `Asia/Seoul` 기준이다. 만료일 당일은 유효하고 다음 날부터 PDF가 필요하다.
- 정상 파일 형식으로 앞면 처리를 시작하면 이전 앞면 · 뒷면 · 진위 결과를 무효화한다. OCR 실패도 이전 결과를 되살리지 않는다. 뒷면 재시도도 이전 뒷면 · 진위 결과를 무효화한다. 파일 형식 · 크기 오류처럼 인식 시작 전 거절된 요청은 기존 결과를 유지한다.
- PDF가 필요하지 않은 성공에서는 저장된 등록번호를 제거한다. 그 뒤 다시 확인하려면 앞면부터 재촬영한다. PDF가 필요하면 후속 동일인 확인을 위해 번호를 암호화 보관하며 기존 가입 세션 만료를 넘겨 사용하지 않는다. 카드 일련번호는 DB에 저장하지 않는다.

## 임시 저장과 보안

Flyway V22는 `social_signup_registration`과 유학생 등록증용 약관 구분을 추가한다. 임시 행은 기존 `social_auth_session.token_hash`에 연결하고 `ON DELETE CASCADE`를 적용한다. 회원 테이블이나 프로필은 생성하지 않는다.

- 앞면 추출값 전체는 AES-256-GCM으로 암호화한다. 요청마다 12바이트 무작위 IV를 사용하고 가입 세션 해시를 AAD에 넣어 다른 세션의 암호문을 사용할 수 없게 한다.
- 키는 DB · 저장소 · 로그에 넣지 않는다. `REGISTRATION_ENCRYPTION_KEY`로 주입하고 접근권한을 제한한다. 키 미설정 · 오류 시 평문 저장으로 대체하지 않는다.
- 가입 유효시간은 기존 소셜 세션 생성부터 30분이며 OCR · 조회로 연장되지 않는다. 외부 처리가 끝날 때도 만료를 다시 검사한다. 같은 가입 요청은 기존 소셜 사용자 → 세션 잠금 순서를 따른다.
- 새 소셜 인증, 가입 시작 API의 이전 세션 폐기, 가입 완료 · 중복 번호 처리에 따른 세션 삭제 시 함께 삭제된다. 만료 세션은 기존 1분 주기 정리 작업이 삭제한다. 앱 중지 중에는 만료로 사용은 거절되지만 물리 삭제는 앱 재시작 후 이루어진다.
- 원본 이미지는 요청 메모리에서만 처리한다. 읽은 바이트 배열은 처리 후 덮어쓰지만 JVM · 프레임워크가 만든 모든 메모리 복사본의 즉시 소거를 보장하지 않는다.
- APICK 오류 응답에는 등록번호가 포함될 수 있어 원문 응답 · 예외 원인을 화면이나 로그에 전파하지 않는다. 앞면 응답에서도 등록번호 뒷자리는 가린다.
- 운영 DB 삭제와 백업 삭제는 별개다. DB 백업/PITR의 보존기간과 복구 시 만료 데이터 정리를 운영 설정에서 확인해야 하며 이 코드가 백업 정책을 변경하지 않는다.

## 오류

| HTTP | code | 처리 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 파일 파트 · 크기 외 형식 확인 |
| 400 | REQUIRED_TERMS_NOT_AGREED | 별도 고유식별정보 동의 포함 필수 약관 저장 |
| 401 | INVALID_SOCIAL_SESSION | 소셜 인증부터 다시 시작 |
| 403 | INVALID_CSRF_TOKEN / FORBIDDEN | CSRF 또는 회원 유형 확인 |
| 403 | SOCIAL_ROLE_MISMATCH / WITHDRAWN_ACCOUNT | 기존 소셜 인증 오류 처리 유지 |
| 409 | REGISTRATION_FRONT_REQUIRED | 앞면부터 재촬영 |
| 409 | SOCIAL_ACCOUNT_ALREADY_REGISTERED | 기존 계정 로그인 |
| 413 | FILE_TOO_LARGE | 파일 5MB · 전체 요청 6MB 제한 확인 |
| 415 | UNSUPPORTED_FILE_TYPE | JPG/PNG 촬영 이미지 사용 |
| 422 | UNREADABLE_REGISTRATION / REGISTRATION_NOT_VERIFIED | 선명하게 재촬영 |
| 502 | REGISTRATION_OCR_FAILED / REGISTRATION_VERIFICATION_FAILED | 잠시 후 재시도 |
| 503 | REGISTRATION_VERIFICATION_UNAVAILABLE | 운영 설정 확인 |
| 500 | INTERNAL_SERVER_ERROR | 서버 오류 |

## 운영 활성화와 검증 한계

| 환경변수 | 설명 |
| --- | --- |
| REGISTRATION_VERIFICATION_ENABLED | 기본 false. 준비 후 true로 설정 |
| REGISTRATION_ENCRYPTION_KEY | 보안 난수 32바이트를 Base64로 인코딩한 전용 키. JWT 키와 분리 |
| REGISTRATION_PADDLE_URL | 내부 OCR 서버 base URL. Compose는 `http://ocr:8000` |
| APICK_API_KEY | APICK 키. 서버에서만 사용 |

HTTPS 서비스 환경과 기존 `SOCIAL_LOGIN_ENABLED` 설정이 필요하다. 키를 교체하면 기존 임시 암호문은 읽을 수 없으므로 진행 중 세션의 만료 · 폐기 정책과 함께 교체한다. 운영 키 · 백업 설정은 이 작업에서 변경하지 않는다.

활성화 전 확정된 동의 본문 · 화면, APICK 처리위탁 계약 적용, 전용 키와 DB 접근권한 · 백업 보관 정책을 확인한다. PM 전달 문구는 초안이며 이 문서나 항목명이 법적 검토를 대체하지 않는다.

CI는 합성 OCR 결과 · 가상 데이터 · 모의 HTTP 응답으로 추출, 암호화, 상태 전이, 만료, CSRF, API 계약을 검증한다. 실제 등록증 촬영본의 구형 · 신형 양식, 다국어 이름 · 국적, 촬영 품질별 인식률 및 실제 APICK 유료 연동은 별도 확인이 필요하다. 모든 등록증 양식 지원 완료로 간주하지 않는다. 실제 PDF 인식은 후속 이슈다.

공식 연동 명세: https://apick.app/dev_guide/identi_card5

APICK 처리위탁 약정: https://apick.app/privacy_entrustment
