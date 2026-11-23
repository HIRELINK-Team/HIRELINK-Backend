# 등록증 갱신과 HIRELINK 인증 배지

## 담당과 범위

- 준서: 등록증 갱신, 최종 만료일 저장·조회, 배지 최초 발급·갱신 후 재발급·현재 상태 조회.
- 서은: 만료에 따른 배지 회수, 추천·지원 제한, 지원자·매칭 화면의 현재 배지 상태 연결.
- 이번 변경은 서은 담당 서비스 및 테스트를 수정하지 않는다. 갱신 후 추천·지원 제한 해제 연결도 별도 협의 범위다.
- 가입 당시 등록번호와 갱신 등록번호의 동일인 대조, HMAC 비교값 보관, 과거 회원 비교값 전환은 이번 개발 범위에서 제외했다. 진위 확인 성공이 가입 당시와 동일인임을 보증하지는 않는다.

## 갱신 순서

공통: 유효한 유학생 access JWT가 필요하고, 가입 시 등록증 인증 기록과 프로필이 있어야 한다. 탈퇴 상태는 허용하지 않는다. 응답은 `Cache-Control: no-store`다. 사용자 입력으로 추출값을 덮어쓰지 않는다.

| 순서 | 요청 | 결과 |
| --- | --- | --- |
| 1 | `POST /api/students/me/registration-renewals/front` (multipart `file`) | 앞면 OCR 결과와 `renewalId`, `renewalExpiresAt` |
| 2 | `POST /api/students/me/registration-renewals/{renewalId}/back` (multipart `file`) | 뒷면 만료일·일련번호 추출 후 진위 API 확인 |
| 3 | 카드 만료 시 `POST /api/students/me/registration-renewals/{renewalId}/stay-document` (multipart `file`) | 이번 갱신 카드와 PDF 등록번호 일치 및 PDF 만료일 확인. 진위 API 재호출 없음 |
| 4 | `POST /api/students/me/registration-renewals/{renewalId}/confirm` (본문 없음) | 서버의 확인된 만료일을 인증 기록과 프로필에 함께 반영하고 배지 발급 여부 판단 |

- 앞/뒷면: JPG/PNG, PDF 증빙: PDF만, 각 5MB 이하이며 파일 시그니처도 검증한다.
- 등록증 체류자격은 `D-2` 원문이다. `D-2-2` 같은 세부 코드는 학업과정과 조합한 기존 프로필 값을 유지한다.
- 앞면: 이름·국적·D-2·등록번호·발급일자. 뒷면/PDF: 허가일자·신고일은 추출하거나 반환하지 않는다.
- 뒷면은 `stayExpiresOn`, `serialNumber`를 반환한다. 만료일이 지났으면 `requiresStayDocument=true`다. 2011년 이전 카드의 일련번호는 없을 수 있다.
- 매 회원의 최신 갱신만 유효하며 앞면 성공 후 30분 이내 최종 확인한다. 앞면 재촬영은 이전 미확정 결과를 무효화한다. 뒷면 확인 후 재촬영도 앞면부터 진행한다.
- OCR·진위·PDF 성공만으로는 최종 만료일/배지를 변경하지 않는다. 최종 확인 중 DB 오류가 발생해도 기존 최종 정보와 확인 가능한 갱신 결과를 보존한다.
- 한국 날짜로 만료일 당일까지 유효하다. 확인 전 날짜가 바뀌어 증빙이 만료되면 최종 반영하지 않고 다시 확인하도록 안내한다.
- 갱신 원본은 저장/제공하지 않는다. 등록번호·발급일은 진위 확인 및 이번 갱신의 PDF 대조에 필요한 동안만 AES-GCM 암호화하여 임시 보관한다. PDF가 필요 없거나 PDF 확인이 끝나면 제거한다. 중단된 갱신도 30분 만료 후 정리 작업이 삭제한다.
- 현재 API는 가입 시 등록증 인증 기록이 없는 개발용 회원의 전환 경로를 제공하지 않는다.

주요 오류: `REGISTRATION_REQUIRED`, `REGISTRATION_RENEWAL_NOT_FOUND`, `REGISTRATION_RENEWAL_EXPIRED`, `REGISTRATION_FRONT_REQUIRED`, `REGISTRATION_BACK_REQUIRED`, `VALID_STAY_DOCUMENT_REQUIRED`(409), `STAY_DOCUMENT_NOT_REQUIRED`(409), `STAY_DOCUMENT_MISMATCH`, `STAY_DOCUMENT_EXPIRED`, `UNSUPPORTED_VISA`, `REGISTRATION_NOT_VERIFIED`(422). 기존 OCR/진위 확인 클라이언트 오류 형식을 재사용한다.

## 배지 발급과 조회

`GET /api/students/me/badge`와 갱신 최종 확인의 응답:

```json
{"active":true,"issuedAt":"2026-11-23T00:00:00Z","stayExpirationDate":"2027-04-23"}
```

- 가입 때 인증된 D-2 등록증이 있고, 최종 만료일이 지나지 않았고, 재학·성적·TOPIK 세 종류에 최종 등록 원본과 등록 시각이 모두 있어야 발급한다. 업로드 중인 임시 원본은 세지 않는다.
- 세 서류 중 마지막 서류의 최종 확인 또는 등록증 갱신의 최종 확인에서 발급한다. 일반 서류 재등록이나 유효한 배지를 가진 상태에서의 조기 갱신은 발급 시각을 유지한다.
- 배지가 만료된 뒤 갱신이 최종 성공하면 새 발급 시각을 기록한다. 기존 세 서류를 재제출할 필요 없다.
- 현재 상태 조회는 한국 날짜의 만료일과 최종 등록 상태를 확인한다. 만료일 다음 날부터 `active=false`지만, 조회 자체는 DB 발급 시각을 지우거나 추천·지원 데이터를 변경하지 않는다.
- `hirelinkBadgeIssuedAt`/`issuedAt`만으로 현재 유효 여부를 판단하지 않는다. 서은의 회수·제한 로직은 최종 만료일과 발급 상태를 함께 연결해야 한다.
- 최종 만료일은 `student_registration_verification.stay_expires_on`과 `student_profile.stay_expiration_date`에 반영한다. 프로필 조회의 `stayExpirationDate`를 재사용한다. 인증 기록이 있는 회원은 기존 프로필 PATCH로 만료일을 직접 변경할 수 없다.
- 기존 확정 데이터를 일괄 재발급하는 배치/데이터 전환은 포함하지 않는다. 발급 트리거는 위 두 최종 확인 경로다.

## 설정·Flyway·검증

- 기존 `REGISTRATION_VERIFICATION_ENABLED`, `REGISTRATION_ENCRYPTION_KEY`, `APICK_API_KEY` 등 실제 환경 변수 매핑은 `application.yml`의 `registration-verification` 설정을 따른다. OCR/진위 확인 연결과 암호화 키가 설정되어 있어야 한다. 신규 비밀키는 추가하지 않는다.
- 갱신 임시 정보 정리는 기본 60초마다 실행된다. 테스트에서만 `registration-verification.renewal-cleanup-initial-delay`를 늘린다.
- Flyway V33은 기존 인증 테이블에 갱신 임시 컬럼 7개와 만료 인덱스를 추가한다. 기존 최종 인증 값은 변경하지 않고, 새 테이블/FK는 추가하지 않는다.
- CI에서 갱신/PDF/재촬영/만료/중복확인/실패 롤백/소유권/배지 발급/마이그레이션을 검증한다. 로컬 테스트·빌드는 실행하지 않는다.
- 실제 등록증/PDF 인식과 운영 진위 API 호출 품질은 별도 운영 연결 확인이 필요하다.
