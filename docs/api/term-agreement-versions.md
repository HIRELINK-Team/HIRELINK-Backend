# 약관 동의 버전 저장·조회

약관 본문·번역·버전별 본문 보관은 프론트에서 관리한다. 백엔드는 약관 종류, 필수 여부, 현재 본문 버전과 회원이 실제 확인한 버전의 동의 여부·시각을 관리한다. 본문 API와 개정 약관 재동의 기능은 제공하지 않는다.

## 조회

| API | 인증 | 용도 |
| --- | --- | --- |
| GET /api/auth/social/terms | 가입 진행 쿠키 | 가입 대상 약관과 임시 동의 기록 |
| GET /api/users/me/term-agreements | Bearer | 전체 약관 메타데이터와 로그인 회원 본인의 기록 |
| GET /api/users/me/term-agreements/location | Bearer | 위치 약관 메타데이터와 본인의 기록 |

응답은 `Cache-Control: no-store`이며 조회만으로 동의 행을 만들거나 변경하지 않는다.

- `currentVersion`: 해당 항목의 현재 적용 본문 버전. 아직 설정하지 않았다면 null.
- `agreedVersion`: 저장된 동의 여부에 대응하는 본문 버전. 과거 버전 미기록 또는 가입 시 선택 항목 생략은 null.
- `isAgreed`, `agreedAt`: 기존 동의 여부·시각. `agreedVersion=null`만으로 미동의라고 판단하지 않는다.
- 현재 본문이 v2여도 기존 v1 동의 기록은 v1과 기존 시각을 유지한다.
- 동의하지 않은 항목을 명시적으로 전송한 경우에도 표시한 본문의 버전은 저장하며, 동의 시각은 null이다.

회원 조회는 본인만 가능하며 인증 없음은 401, 탈퇴 계정은 403이다. 온보딩 중 위치 동의에도 사용하므로 이 API에 온보딩 완료를 요구하지 않는다.

## 저장

두 PUT API에 `version`이 필수다. 서버가 현재 버전을 자동으로 채워서는 안 된다.

```json
{"agreements":[{"termTypeId":4,"isAgreed":true,"version":"example-v1"}]}
```

예시 ID와 버전은 실제 조회 결과·표시한 본문으로 대체한다. 버전은 영문 또는 숫자로 시작하는 1~64자의 영문·숫자·점·밑줄·하이픈 식별값이며 항목마다 다를 수 있다. 프론트는 본문의 버전이 `currentVersion`과 일치할 때 그 본문으로 동의받는다. 조회한 버전명만 새로 붙여 예전 본문을 전송하지 않는다.

- `PUT /api/auth/social/terms`: 필수 약관 전체 true, 생략한 선택 약관은 false/null 버전. 같은 버전의 true 반복은 시각 유지. 가입 임시 상태에서 새 본문을 확인하고 새 버전을 보내면 새 동의 시각을 기록한다.
- `PUT /api/users/me/term-agreements`: 요청 항목만 갱신한다. 같은 버전의 기존 동의 시각은 유지한다. 이미 동의한 기록을 다른 버전으로 덮어쓰는 기능은 제공하지 않는다. 과거 버전 미기록 true에 임의로 이름을 붙이는 요청도 차단한다.
- 하나라도 검증 또는 저장에 실패하면 요청 전체를 롤백한다.
- 소셜 전화번호 인증으로 계정을 생성할 때 가입 세션의 실제 버전·시각을 그대로 정식 기록에 옮긴다. 그 사이 현재 버전이 변경돼도 새 버전으로 바꾸지 않는다.
- 배포 전에 만들어져 필수 동의 버전이 없는 가입 임시 세션은 약관 동의를 다시 저장해야 등록증 인증·전화번호 인증을 진행할 수 있다. 기존 정식 회원의 동의 기록에는 이 검사를 적용하지 않는다.

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 400 | INVALID_INPUT | version 누락·공백·형식 오류, 기존 입력 검증 오류 |
| 409 | TERM_VERSION_MISMATCH | 실제 표시한 버전과 서버의 현재 버전이 다름 |
| 409 | TERM_RECONSENT_NOT_SUPPORTED | 기존 정식 동의 기록의 버전을 변경하려 함 |
| 503 | TERM_VERSION_UNAVAILABLE | 해당 약관의 현재 버전이 아직 설정되지 않음 |

기존 인증·필수 약관·약관 ID 오류 계약도 유지한다. 상세 응답은 Swagger를 참고한다.

## Flyway와 배포 전 설정

V37은 `term_type.current_version`, `user_term_agreement.term_version`, `social_auth_session_term_agreement.term_version`을 nullable로 추가한다. 기존 동의 여부·시각은 보존하며 확인되지 않은 과거 버전을 일괄 v1로 채우지 않는다. 현재 버전도 임의 생성하지 않으므로 처음에는 null이다.

**새 가입·위치 동의 저장을 사용하기 전에** 프론트 본문과 대응하는 항목별 실제 버전을 정하고 DB에 등록해야 한다. 미설정 상태에서는 동의 저장이 503으로 거절된다. 프론트도 두 PUT 요청에 실제 본문 version을 포함하도록 함께 반영해야 한다.

현재 항목 확인:

```sql
SELECT id, term_type_name, is_required, is_social_signup, is_student_registration, current_version
FROM term_type ORDER BY id;
```

실제 값 확정 후 적용할 SQL 형식(아래 자리표시자를 먼저 대체):

```sql
UPDATE term_type
SET current_version = '<실제 본문 버전>'
WHERE id = <확인한 약관 ID>;
```

운영 변경은 대상 ID·본문·버전이 일치하는지 확인한 뒤 별도로 적용한다. 신규 버전으로 개정할 때도 현재 버전만 변경하며 회원/가입 세션의 저장된 `term_version`이나 `agreed_at`을 일괄 변경하지 않는다. 이미 공개한 버전명은 다른 본문에 재사용하지 않는다. 관리자 API나 외부 자동 수집 기능은 추가하지 않는다.
