# 등록증 갱신과 체류 만료 안내

## 갱신 순서

온보딩을 완료한 활성 유학생 본인의 access JWT, 프로필, 가입 시 등록증 인증 기록이 필요하다. 모든 응답은 `Cache-Control: no-store`를 사용한다. 요청으로 OCR 추출값을 직접 수정할 수 없다.

| 순서 | 요청 | 결과 |
| --- | --- | --- |
| 1 | `POST /api/students/me/registration-renewals/front` (multipart `file`) | 앞면 OCR 결과, `renewalId`, `renewalExpiresAt` |
| 2 | `POST /api/students/me/registration-renewals/{renewalId}/back` (multipart `file`) | 뒷면 만료일·일련번호 추출 및 진위 API 확인 |
| 3 | 카드 만료 시 `POST /api/students/me/registration-renewals/{renewalId}/stay-document` (multipart `file`) | 이번 카드와 PDF 등록번호 일치 및 유효한 만료일 확인. 진위 API를 다시 호출하지 않는다 |
| 4 | `POST /api/students/me/registration-renewals/{renewalId}/confirm` (본문 없음) | 확인된 이름·국적·체류자격·만료일을 함께 반영하고 지원 대상 및 현재 배지 상태 반환 |

- 앞·뒷면은 JPG/PNG, 체류 증빙은 PDF만 받는다. 각 5MB 이하이며 파일 시그니처를 검사한다.
- 등록증의 원문 체류자격과 저장된 학업과정은 별개다. D-2와 학사라면 프로필·조회 응답에 D-2-2를 제공한다. 학업과정 자체는 갱신하지 않는다.
- D-10 등 지원 대상 밖의 체류자격도 정상 인증되면 새 이름·국적·체류자격·만료일을 저장한다. 기존 D-2를 유지하지 않는다. `serviceSupported=false`와 안내 문구를 반환하며 기존 학업과정으로 D-2 코드를 만들지 않는다.
- 신규 가입은 기존처럼 D-2만 허용한다. 갱신의 지원 대상 판정은 진위 확인 성공 여부 및 취업 허가 여부와 다르다.
- 앞면의 발급일자는 진위 확인에 사용한다. 뒷면·PDF의 허가일자·신고일은 추출하거나 응답하지 않는다.
- 앞면 처리 성공부터 30분 동안 본인의 최신 갱신만 유효하다. 앞면 재촬영은 이전 미확정 결과를 무효화한다. 뒷면 확인 후 다시 촬영하려면 앞면부터 진행한다.
- OCR·진위·PDF 확인만으로 확정 정보를 바꾸지 않는다. 최종 확인은 계정 이름, 인증 기록, 프로필 및 조건 충족 시 배지 발급을 하나의 트랜잭션으로 처리한다. 실패 시 기존 확정 정보와 재시도 가능한 갱신 결과를 유지한다.
- 한국 날짜 기준 만료일 당일까지 유효하다. 최종 확인 시 날짜가 바뀌어 증빙이 만료되면 저장하지 않는다.
- 등록증 갱신 원본은 저장·제공하지 않는다. 등록번호·발급일자는 진위 확인 및 이번 카드와 PDF 비교에 필요한 동안만 AES-GCM으로 암호화하여 임시 보관한다. 인증된 이름·국적·체류자격은 최종 확인 전까지 암호화된 갱신 초안에 보관한다. 최종 확인 또는 30분 만료 정리 시 초안을 제거한다.
- 가입 당시 등록번호와의 동일인 대조 기능은 없다. 이번 카드의 진위 확인이 가입 당시와 동일인임을 보장하지 않는다.
- 가입 인증 기록이 없는 회원의 전환 및 온보딩 후 학업과정 수정 API는 제공하지 않는다.
- 이전 버전에서 생성하여 이름·국적·체류자격 초안이 없는 진행 중 갱신은 `REGISTRATION_FRONT_REQUIRED`로 앞면 재촬영을 요구한다.

주요 오류: `REGISTRATION_REQUIRED`, `REGISTRATION_RENEWAL_NOT_FOUND`, `REGISTRATION_RENEWAL_EXPIRED`, `REGISTRATION_FRONT_REQUIRED`, `REGISTRATION_BACK_REQUIRED`, `VALID_STAY_DOCUMENT_REQUIRED`, `STAY_DOCUMENT_NOT_REQUIRED`(409), `STAY_DOCUMENT_MISMATCH`, `STAY_DOCUMENT_EXPIRED`, `UNREADABLE_REGISTRATION`, `REGISTRATION_NOT_VERIFIED`(422). 기존 OCR·진위 클라이언트의 오류 응답 형식을 유지한다.

## 최종 확인 응답과 현재 정보 조회

`GET /api/students/me/registration`은 최종 확인 응답과 같은 구조다. 진행 중인 갱신 값은 노출하지 않는다.

```json
{
  "name": "LIN MAI",
  "nationalityCode": "VN",
  "registrationVisaCode": "D-10",
  "visaCode": "D-10",
  "studyProgram": "BACHELOR",
  "stayExpirationDate": "2027-04-23",
  "serviceSupported": false,
  "supportMessage": "현재 체류자격 또는 학업과정은 서비스 지원 대상이 아닙니다.",
  "active": false,
  "issuedAt": "2026-11-23T00:00:00Z"
}
```

- `registrationVisaCode`는 인증 기록의 원문 코드다. `visaCode`는 D-2와 저장된 지원 학업과정을 조합한 코드이며, 그 외에는 원문이다.
- `serviceSupported`는 원문 D-2와 지원 학업과정 조합 여부다. 기존 학업과정이 없으면 false이며 세부 코드를 임의로 만들지 않는다.
- 갱신의 최종 확인 응답은 기존 `active`·`issuedAt`·`stayExpirationDate`를 유지하며 새 정보 필드를 추가한다.
- 별도 배지 조회 `GET /api/students/me/badge`의 응답 구조는 유지한다. `issuedAt`만으로 현재 배지 유효 여부를 판단하지 않는다.
- 기존 배지 판정은 인증된 D-2·유효한 만료일·세 증명서 최종 등록 여부를 사용한다. 이번 변경은 DB 배지 회수 및 추천·지원 제한을 실행하지 않는다. 지원 대상 밖으로 변경된 회원의 제한 연결은 별도 작업이다.
- 갱신 최종 성공 시 발급 조건을 충족하면 기존 세 증명서를 다시 제출하지 않아도 배지를 발급한다. 유효한 배지의 조기 갱신은 기존 발급 시각을 유지한다.
- 인증된 체류자격·만료일은 프로필 수정 API로 직접 변경할 수 없다.

## 만료일별 최초 안내

`POST /api/students/me/stay-expiration-notice/claim` (본문 없음)을 홈에서 안내를 표시할 준비가 되었을 때 호출한다.

```json
{"show":true,"stayExpirationDate":"2026-12-22","remainingDays":29,"message":"체류기간 만료까지 29일 남았어요."}
```

- 한국 날짜 기준 0~30일 남았고 해당 만료일의 안내 이력이 없으면 `show=true`를 반환하면서 이력을 저장한다.
- 만료일 당일에는 `remainingDays=0`, `message="오늘 체류기간이 만료돼요"`를 반환한다. 1일 전에는 실제 1일을 표시한다.
- 안내한 만료일은 재접속·날짜 변경·같은 만료일 갱신으로 반복하지 않는다. 다른 새 만료일이 확정되면 그 날짜에 대해 한 번 더 안내한다. 과거에 안내한 날짜로 돌아오면 반복하지 않는다.
- 31일 이상 남았거나 이미 만료되었거나 안내 이력이 있으면 `show=false`이며 문구는 null이다. 이 경우 확정 만료일과 실제 남은 일수는 반환한다. 가입 인증 기록이 없으면 false와 나머지 null을 반환한다.
- 회원 잠금으로 갱신 최종 저장과 안내 요청을 직렬화한다. 동시 요청 중 한 요청만 true다. DB 저장 실패 시 이력도 롤백한다.
- 요청 성공 시 안내 이력을 기록하므로 응답이 유실되어도 재요청은 false다. 사전 조회 용도로 호출하지 말고 실제 표시 시점에 호출한다.
- 프론트는 true일 때 문구와 갱신 이동 버튼을 표시한다. 로그인 API 자체에서 안내 이력을 소모하지 않는다.
- 배지 회수·추천 및 지원 제한과 독립적이다.

## 설정·Flyway·검증

- 기존 `REGISTRATION_VERIFICATION_ENABLED`, `REGISTRATION_ENCRYPTION_KEY`, `APICK_API_KEY` 및 OCR 설정을 사용한다. 신규 비밀키는 없다. 현재 정보·안내 조회에는 외부 OCR/진위 호출이 없다.
- Flyway V33은 기존 갱신 임시 컬럼과 만료 인덱스를 유지한다.
- V36은 인증 기록에 `expiry_notice_dates` JSONB 배열을 추가한다. 기존 회원은 빈 이력으로 시작하며 기존 확정값은 변경하지 않는다.
- V36은 프로필의 `fk_student_profile_visa`와 인증 기록의 `student_registration_verification_visa_code_fkey`를 제거한다. 지원 대상 코드만 들어 있는 기준표에 없는 정상 인증 체류자격도 저장하기 위해서다. 기준표 및 기존 값·다른 FK는 유지하며 신규 가입·직접 입력 검증은 그대로 적용한다.
- 검증 범위: 카드/PDF 갱신, 최종 확인 전 보존, 전체 정보 갱신과 롤백, 지원 대상 전환과 학업과정 유지, 안내 날짜 경계·동시 요청·롤백·권한, 기존 데이터 보존 마이그레이션, 신규 가입 제한 회귀.
- 실제 서류 OCR 인식 품질과 운영 진위 API 연결은 운영 환경에서 별도 확인해야 한다.
