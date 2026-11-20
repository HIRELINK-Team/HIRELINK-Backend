# 유학생 가입 등록증 OCR · 진위 확인

## 이번 범위

- 계정 생성 전 등록증 앞면 · 뒷면 OCR, APICK 기재 정보 일치 확인, 필요한 체류기간 PDF의 등록번호 일치 · 만료일 확인
- 기존 PaddleOCR 서버를 확장하며 별도 OCR 모델 · 의존성을 추가하지 않는다.
- 활성화된 유학생 소셜 휴대전화 가입의 선행 인증 검사와 회원 정보 이전을 포함한다. 이후 입력은 [온보딩 일괄 저장 · 상태 조회 API](student-onboarding.md)를 사용한다.
- `REGISTRATION_VERIFICATION_ENABLED=true`인 유학생 소셜 가입은 휴대전화 인증 요청 · 확인 API에서 선행 검증을 필수 검사한다. false인 기존 소셜 가입 · 사업주 · LOCAL 가입에는 적용하지 않는다. 전체 새 가입 흐름 공개는 서버 환경변수 설정, 프론트 연동 및 실제 OCR · APICK · PDF · SMS 흐름 확인 후 진행한다.

## 프론트 연결

1. 기존 소셜 인증으로 `socialSignup` 쿠키를 받는다. 계정은 아직 생성되지 않는다.
2. `GET /api/auth/social/terms`에서 약관을 조회한다. 기능이 활성화된 유학생에게는 필수 `고유식별정보 수집·이용` 항목이 추가된다. 사업주 또는 기능 비활성 상태에는 기존 약관 목록을 유지한다. ID는 응답값을 사용한다.
3. 확정된 약관 본문을 표시하고 별도 체크박스로 동의를 받는다. `PUT /api/auth/social/terms`에 다른 필수 약관과 함께 저장한다. 기존 동의 시각 보존 정책을 따른다. 동의 문서 · 번역은 프론트에서 제공하며 DB의 항목명만으로 법적 요건을 갖췄다고 간주하지 않는다.
4. 카메라로 앞면을 촬영해 `POST /api/auth/social/registration/front`로 보낸다. 응답을 확인하고 틀리면 재촬영한다. 값을 직접 수정하는 API는 없다.
5. 카메라로 뒷면을 촬영해 `POST /api/auth/social/registration/back`로 보낸다. 서버가 보관한 앞면 값과 뒷면 OCR 값으로 APICK을 호출한다.
6. `requiresStayDocument=true`이면 `POST /api/auth/social/registration/stay-document`로 PDF를 확인한다. false이면 카드에서 읽은 체류 만료일이 한국 날짜 기준 지나지 않은 상태다. 이 응답은 계정 생성 · 온보딩 완료가 아니다.

두 POST는 `multipart/form-data`이며 **`registrationFile` 파일 파트 한 개만** 허용한다. JPG/PNG, 파일 최대 5MB, 전체 요청 최대 6MB다. PDF · 추가 파일 · 폼 필드는 거절한다. 파일 이름을 경로로 사용하거나 원본 이미지를 저장하지 않는다. 프론트는 사진 선택 UI 없이 촬영만 제공하지만, 서버가 이미지의 카메라 출처를 증명하는 기능은 아니다.

쿠키와 `GET /api/auth/csrf`에서 받은 `X-XSRF-TOKEN` 헤더를 함께 보낸다. Bearer 토큰은 이 API의 인증 수단이 아니며 전달해도 CSRF를 우회하지 않는다. 응답은 `Cache-Control: no-store`로 취급하고 원문을 브라우저 영구 저장소 · 분석 도구에 기록하지 않는다.

앞면 응답 예시 (가상 데이터):

```json
{
  "name": "TEST STUDENT",
  "nationalityCode": "VN",
  "visaCode": "D-2",
  "registrationNumber": "000101-5000000",
  "issueDate": "2020-01-01",
  "signupExpiresAt": "2026-11-20T10:30:00Z"
}
```

앞면 응답의 `maskedRegistrationNumber`는 제거하고 `registrationNumber`로 교체한다. 새 값은 숫자 6자리 + 하이픈 + 숫자 7자리인 14자 문자열이며 선행 0을 유지한다. 기존 클라이언트는 응답 키 매핑을 변경해야 한다. 하이픈 없는 13자리 숫자는 서버 내부 OCR·진위 확인·암호화 저장 형식이며, 화면에는 위 응답값을 그대로 표시한다. 나머지 응답 필드와 요청 형식은 유지한다.

프론트는 확인용 원문을 화면 상태에서만 사용하고 화면 이탈·가입 인증 완료·세션 만료 시 정리해야 한다. 재촬영을 시작하거나 실패하면 이전 결과도 화면에서 제거한다. 브라우저 영구 저장소·분석 이벤트·콘솔 로그에 번호를 기록하지 않는다. 이는 프론트 연동 요구사항이며 이 백엔드 변경으로 프론트 구현·QA가 완료되는 것은 아니다.

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
- 정상 파일 형식으로 앞면 처리를 시작하면 이전 앞면 · 뒷면 · 진위 · PDF 결과를 무효화한다. OCR 실패도 이전 결과를 되살리지 않는다. 뒷면 재시도도 이전 뒷면 · 진위 결과를 무효화한다. 파일 형식 · 크기 오류처럼 인식 시작 전 거절된 요청은 기존 결과를 유지한다.
- APICK 확인 성공 시 발급일자를 제거한다. PDF가 필요하지 않으면 등록번호도 제거한다. 성공 후 뒷면 재촬영은 앞면부터 진행한다. PDF가 필요하면 동일인 확인을 위해 등록번호만 암호화 보관하며 PDF 확인 성공 시 제거하고 기존 가입 세션 만료를 넘겨 사용하지 않는다. 카드 일련번호는 DB에 저장하지 않는다.

## 체류기간 PDF 확인

`POST /api/auth/social/registration/stay-document`는 같은 가입 쿠키 · CSRF · 별도 필수 동의를 사용한다. `multipart/form-data`의 `stayDocument` 파일 한 개만 받는다. PDF 최대 5MB · 3쪽, 전체 요청 6MB이며 폼으로 등록번호나 날짜를 직접 전달할 수 없다. MIME과 PDF 시그니처를 검사하고 손상 · 읽을 수 없는 암호화 파일 · 초과 페이지는 422로 거절한다. 원본은 메모리에서만 처리한다.

선행 APICK 확인이 완료되고 카드 만료일이 한국 날짜 기준 지난 경우에만 제출한다. 카드 만료일 당일은 PDF가 필요하지 않다. PDF의 대상자 등록번호 전체가 앞면과 일치해야 하며 가림 번호는 성공 처리하지 않는다. PDF 만료일 당일까지 유효하고, 날짜 범위는 종료일을 사용한다. 사용자가 결과를 직접 수정하는 기능은 없다.

```json
{
  "stayDocumentVerified": true,
  "stayExpiresOn": "2027-07-27",
  "signupExpiresAt": "2026-11-20T10:30:00Z"
}
```

실패 시 화면의 ‘다시 촬영’ 버튼은 PDF를 다시 선택 · 업로드하는 동작이다. 실패한 PDF로 확인 완료 상태가 생기지 않는다. 실패 후에는 같은 세션에서 PDF만 다시 제출할 수 있다. 성공 후에는 등록번호가 삭제되어 다시 확인하려면 앞면부터 시작해야 한다. 등록증 재촬영은 이전 PDF 결과도 무효화한다.

V23은 기존 임시 테이블에 `document_expires_on`, `document_verified_at`을 추가한다. 카드에서 읽은 `stay_expires_on`과 APICK 시각은 유지하고 PDF 만료일 · 확인 시각을 별도로 저장한다. 기존 세션 삭제 시 함께 삭제되며 유효시간은 연장하지 않는다. 확인 성공 후 등록번호 · 발급일자는 남기지 않는다. 계정 생성과 회원 정보 이전은 휴대전화 인증 확인 API에서 수행한다.

추출 기준은 [공식 별지 제139호서식](https://www.law.go.kr/flDownload.do?flSeq=152659139)의 대상자 영역이다. 현재 체류기간을 사용하고 등록일자 · 발급일 · 주소 변경일 · 체류자격 변경일 · 동거 가족 번호는 사용하지 않는다. 여러 날짜 중 최댓값을 택하지 않는다. 현재 값이 충돌하면 거절한다. 여러 쪽인 경우 각 쪽의 대상자 영역을 명확히 읽을 수 있고 번호 · 현재 만료일이 일치해야 한다. 제목 · 대상자 영역이 없는 연속 페이지나 표의 열이 혼합된 경우도 보수적으로 거절한다. 실제 발급 PDF로 레이아웃 · 인식률 검증 전 모든 양식 지원을 보장하지 않는다.

`stayDocumentVerified`는 **등록번호 일치 · 체류기간 확인**이며 문서 위변조 · 전자서명 · 발급기관 조회 검증이 아니다. PDF 처리에서 APICK을 호출하지 않는다.

| HTTP | code | 처리 |
| --- | --- | --- |
| 409 | REGISTRATION_BACK_REQUIRED | 앞면 · 뒷면 진위 확인 먼저 완료 |
| 409 | STAY_DOCUMENT_NOT_REQUIRED | 카드 만료일이 아직 지나지 않아 PDF 불필요 |
| 409 | REGISTRATION_FRONT_REQUIRED | 확인 완료 · 정보 삭제 이후에는 앞면부터 다시 시작 |
| 422 | UNREADABLE_STAY_DOCUMENT | 잘못된 문서 · 가림 번호 · 모호한 날짜 · 손상 · 페이지 초과. PDF 재선택 |
| 422 | STAY_DOCUMENT_MISMATCH | 본인의 PDF 재선택 |
| 422 | STAY_DOCUMENT_EXPIRED | 유효한 체류기간이 명시된 PDF 재선택 |

400 · 401 · 403 · 413 · 415 · 502 · 503은 기존 인증 오류 계약을 따른다. PDF의 415는 PDF 이외 형식 또는 시그니처 불일치를 의미한다.

## 소셜 휴대전화 가입 연결

기존 `POST /api/auth/social/phone-verifications`와 `POST /api/auth/social/phone-verifications/verify`의 요청 · 성공 응답 형식을 유지한다. 새 API나 클라이언트가 보낸 인증 완료 플래그는 사용하지 않는다.

등록증 기능이 활성화된 유학생은 두 API에서 서버의 임시 결과를 검사한다. APICK 확인이 필요하고 카드 만료일이 지난 경우 PDF 확인이 필요하다. PDF 만료일도 한국 날짜 기준 오늘 이상이어야 한다. 인증번호 발송 후 재촬영 · 체류기간 만료 · 필수 동의 누락이 발생하면 계정 생성을 거절한다. 전화번호 잠금 대기 후 세션 · 인증번호 만료를 다시 확인하고, 계정 저장 후에도 세션 · 체류기간을 확인하여 만료된 상태로 완료하지 않는다.

- `409 REGISTRATION_VERIFICATION_REQUIRED`: 앞면부터 진위 확인을 완료한다. 카드가 유효했던 때 등록번호를 삭제한 후 날짜가 바뀌어 만료된 경우도 앞면부터 다시 진행한다.
- `409 STAY_DOCUMENT_REQUIRED`: 카드 확인 후 필요한 PDF를 제출한다.
- `422 STAY_DOCUMENT_EXPIRED`: PDF로 확인한 체류기간도 지나 앞면부터 다시 진행한다.
- `503 REGISTRATION_VERIFICATION_UNAVAILABLE`: 임시정보 복호화 설정을 확인한다.
- 기존 400 · 401 · 403 · 409 중복 번호 · 429 · 500 오류 계약도 유지한다. 선행 검증 오류는 가입 쿠키를 삭제하지 않고, 전화번호 중복일 때만 기존 방식으로 해당 가입 정보를 정리한다.

휴대전화 인증 성공 시 인증된 이름을 `user_account.name`에 저장한다. V24의 `student_registration_verification`은 회원별 국적 · 체류자격 · 유효한 만료일 · APICK 확인 시각 · 적용한 PDF 확인 시각 · 가입 처리 시각을 보관한다. 원래 인증 시각과 약관 동의 시각을 유지한다. 등록번호 · 발급일자 · 카드 일련번호 · 암호문 · 원본 파일은 이전하지 않는다.

생년월일은 가입 전에 검사·보존하지만 거주지역 · 한국어 수준을 아직 받지 않았으므로 빈값을 채워 `student_profile`을 만들지 않는다. [온보딩 일괄 저장 API](student-onboarding.md)에서 회원 연결 인증 정보를 사용한다. 계정 · 소셜 연결 · 전화번호 · 인증 결과 · 동의 · 토큰 생성과 임시 세션 삭제는 같은 트랜잭션이다. 저장 실패 시 모두 롤백한다. `201`은 계정 생성이고 `onboardingRequired=true`이며, 온보딩 완료는 이후 `POST /api/students/me/onboarding`에서 처리한다.

중복 전화번호면 기존 회원을 변경하지 않고 이번 임시 세션 · 등록증/PDF 결과만 함께 정리한다. 회원 연결 인증 정보는 회원 삭제 시 함께 삭제한다. 실제 회원 탈퇴 보관 정책 변경이나 기존 API에 대한 온보딩 접근 제한은 이번 작업에 포함하지 않는다.

## 임시 저장과 보안

Flyway V22는 `social_signup_registration`과 유학생 등록증용 약관 구분을 추가한다. 임시 행은 기존 `social_auth_session.token_hash`에 연결하고 `ON DELETE CASCADE`를 적용한다. OCR · PDF API 자체는 회원 테이블이나 프로필을 생성하지 않는다.

- 앞면 추출값 전체는 AES-256-GCM으로 암호화한다. 요청마다 12바이트 무작위 IV를 사용하고 가입 세션 해시를 AAD에 넣어 다른 세션의 암호문을 사용할 수 없게 한다.
- 키는 DB · 저장소 · 로그에 넣지 않는다. `REGISTRATION_ENCRYPTION_KEY`로 주입하고 접근권한을 제한한다. 키 미설정 · 오류 시 평문 저장으로 대체하지 않는다.
- 가입 유효시간은 기존 소셜 세션 생성부터 30분이며 OCR · 조회로 연장되지 않는다. 외부 처리가 끝날 때도 만료를 다시 검사한다. 같은 가입 요청은 기존 소셜 사용자 → 세션 잠금 순서를 따른다.
- 새 소셜 인증, 가입 시작 API의 이전 세션 폐기, 가입 완료 · 중복 번호 처리에 따른 세션 삭제 시 함께 삭제된다. 만료 세션은 기존 1분 주기 정리 작업이 삭제한다. 앱 중지 중에는 만료로 사용은 거절되지만 물리 삭제는 앱 재시작 후 이루어진다.
- 원본 이미지는 요청 메모리에서만 처리한다. 읽은 바이트 배열은 처리 후 덮어쓰지만 JVM · 프레임워크가 만든 모든 메모리 복사본의 즉시 소거를 보장하지 않는다.
- 앞면 성공 응답에서만 사용자 확인용 등록번호 전체를 HTTPS로 전달하며 `Cache-Control: no-store`를 유지한다. 프론트 복호화용 별도 암호화 프로토콜은 추가하지 않는다. 임시 DB의 AES-256-GCM 보관·인증 후 번호 삭제·세션 만료 정책과 영구 회원 정보로 번호를 이전하지 않는 정책은 그대로다.
- APICK 오류 응답에는 등록번호가 포함될 수 있어 원문 응답 · 예외 원인을 화면이나 로그에 전파하지 않는다. 오류 응답에는 번호를 넣지 않으며 앞면 응답 DTO의 `toString()`도 원문을 숨긴다.
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

CI는 합성 OCR 결과 · 가상 데이터 · 모의 HTTP 응답으로 추출, 암호화, 상태 전이, 만료, CSRF, API 계약을 검증한다. 실제 등록증 촬영본의 구형 · 신형 양식, 다국어 이름 · 국적, 촬영 품질별 인식률 및 실제 APICK 유료 연동은 별도 확인이 필요하다. 모든 등록증 양식 지원 완료로 간주하지 않는다. PDF도 합성 입력 검증이며 실제 발급 PDF 인식률 검증은 후속이다.

공식 연동 명세: https://apick.app/dev_guide/identi_card5

APICK 처리위탁 약정: https://apick.app/privacy_entrustment

전화번호 인증 전에 [가입 생년월일 API](signup-birth-date.md)로 만 18세 이상을 검사한다. 저장된 날짜는 계정 생성 시 회원 연결 정보에 함께 보존하며, 이후 재접속은 거주지역부터 시작한다.
