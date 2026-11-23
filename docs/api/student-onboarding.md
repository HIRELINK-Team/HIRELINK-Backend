# 유학생 온보딩 일괄 저장 · 완료

`POST /api/students/me/onboarding`은 전화번호 인증으로 계정을 만든 뒤 마지막 **마치기**에서 호출한다. 로그인한 본인의 액세스 토큰을 사용하며, 회원 연결 등록증 인증 기록이 필요하다. LOCAL 테스트 계정은 프로필 생성 후 내 정보 일괄 저장 · 구직조건 API를 사용한다.

## 화면별 API 연결

등록증 기능이 활성화된 신규 유학생의 흐름이다. 실제 서비스 로그인은 카카오·구글을 사용하며, LOCAL 테스트 계정·사업주·기능 비활성 가입은 기존 흐름을 유지한다.

| 화면 · 동작 | API · 저장 시점 | 다음 단계 · 실패 처리 |
| --- | --- | --- |
| 소셜 인증 | `GET /api/auth/social/{provider}?role=STUDENT`로 전체 페이지 이동 → 콜백 후 `POST /api/auth/social/result` | `SIGNUP_REQUIRED`는 약관으로, `LOGIN`은 액세스 토큰으로 온보딩 상태 조회 |
| 약관 동의 | `GET` / `PUT /api/auth/social/terms` | 응답의 약관 ID 사용. 별도 고유식별정보 동의를 포함한 필수 약관 동의 후 등록증 제출 |
| 등록증 앞면 촬영 · 앨범 선택 | `POST /api/auth/social/registration/front` | `registrationNumber` 전체를 확인. 잘못 읽으면 이미지 재제출, OCR 값 직접 수정 API 없음 |
| 등록증 뒷면 촬영 · 앨범 선택 | `POST /api/auth/social/registration/back` | `serialNumber`와 `stayExpiresOn` 확인. `requiresStayDocument=false`는 전화번호 인증으로, `true`는 PDF 제출로 |
| 체류기간 PDF 선택 | `POST /api/auth/social/registration/stay-document` | 카드 앞면·뒷면 확인이 선행되어야 함. 성공 시 같은 응답 모델의 카드 일련번호와 PDF 만료일을 사용. PDF 인식·번호·기간 오류는 PDF 재선택, `REGISTRATION_FRONT_REQUIRED`는 앞면부터 재시작 |
| 전화번호 인증 · 계정 생성 | `POST /api/auth/social/phone-verifications` → `POST /api/auth/social/phone-verifications/verify` | 성공 `201`에서 계정·토큰 생성. 약관·등록증·체류기간을 재검사하며 아직 온보딩 완료는 아님 |
| 계정 생성 후 · 재로그인 | `GET /api/users/me/onboarding` | 아래 상태표의 `status`와 `nextStep`으로 이동. 미완료 회원은 항상 생년월일부터 다시 입력 |
| 거주지역 · 희망 근무지에서 현재 위치 찾기 | 최초 GPS 사용 전 `GET /api/users/me/term-agreements/location`, 미동의면 `PUT /api/users/me/term-agreements` | 동의 저장 성공 후 프론트에서 기기 위치 권한·GPS 처리. 주소 직접 검색·선택은 위치 동의 없이 가능 |
| 생년월일 → 학업과정 → 거주지역 → 한국어 수준 → 경력 → 희망 업종·시간·위치·반경 → 우선순위 | 화면 입력을 프론트에서 모음. 화면별 서버 저장 없음 | 반경은 100~1,000m의 100m 간격. 경력 없음은 `[]`. TOPIK은 서류 최종 등록으로만 반영. 중도 이탈 후 세부 화면 복구 없음 |
| 마치기 | `POST /api/students/me/onboarding` | 전체 입력을 한 번에 저장. 성공 `completed=true` 후 상태 조회는 `COMPLETED/NONE`. 실패하면 이번 저장 전체 취소, 완료 후 재제출은 409 |

- 계정 생성 전 변경 요청은 같은 브라우저의 `socialSignup` 쿠키와 `GET /api/auth/csrf`에서 얻은 CSRF 쿠키·헤더를 사용한다. 최초 가입 세션의 30분 만료는 연장하지 않으며 만료 시 소셜 인증부터 다시 시작한다. 계정 생성 후 API는 로그인 액세스 토큰을 사용한다.
- 앞면·뒷면은 촬영·앨범 선택 모두 같은 JPG/PNG 업로드 API를 사용한다. 앞면 응답의 전체 등록번호와 뒷면/PDF 공통 응답의 일련번호를 프론트에서 구분한다. 일련번호 없는 구형 카드의 `serialNumber=null`은 정상 응답이다. 허가일자·신고일은 확인 화면 응답에 없다.
- 등록증 확인 후 이미지를 바꾸려면 앞면부터 진행한다. PDF만 실패한 경우에는 같은 세션에서 PDF를 다시 제출할 수 있다. 앞면 재처리를 시작하면 이전 카드·PDF 결과를 무효화한다.
- 촬영·앨범 선택·PDF 선택 UI, 약관 본문·번역, 기기 권한·지도 SDK·GPS, 마지막 저장 전 화면 상태 관리는 프론트 담당이다. 서버는 파일 출처를 판별하거나 기기 권한을 요청하지 않는다. 확인용 등록번호·일련번호는 브라우저 영구 저장소·분석 이벤트·로그에 남기지 않고 화면 이탈·재촬영 시작·가입 완료·세션 만료 시 정리한다.

상세 요청·응답·오류는 [등록증·PDF 확인](student-registration.md), [생년월일 검사](signup-birth-date.md), [위치 동의](location-consent.md)와 Swagger를 따른다. 이 연결표는 프론트 구현이나 실물 OCR·APICK·SMS·GPS 연동 QA 완료를 의미하지 않는다.

## 상태 조회와 재진입

`GET /api/users/me/onboarding`은 계정 생성 또는 재로그인 후 액세스 토큰으로 호출한다. 기존 로그인 · 가입 응답의 필드는 변경하지 않는다. 계정 생성 전 가입 쿠키만 있는 요청은 401이며, 회원이 생성된 뒤부터 이 조회를 사용한다.

| 대상 | status | nextStep | completedAt |
| --- | --- | --- | --- |
| 등록증 인증을 거친 유학생, 완료 시각 없음 | `REQUIRED` | `BASIC_INFORMATION` | `null` |
| 등록증 인증을 거친 유학생, 완료 시각 있음 | `COMPLETED` | `NONE` | 최초 완료 시각 |
| 등록증 인증 기록이 없는 LOCAL · 기존 가입 회원, 사업주 | `NOT_APPLICABLE` | `EXISTING_FLOW` | `null` |

```json
{"userId":1,"role":"STUDENT","status":"REQUIRED","nextStep":"BASIC_INFORMATION","completedAt":null}
```

- `REQUIRED/BASIC_INFORMATION`이면 과거에 보존된 날짜 유무와 무관하게 항상 생년월일부터 다시 입력한다. 등록증·전화번호 인증을 반복하거나 마지막 작성 화면부터 이어가지 않는다.
- 프로필이나 일부 입력이 있더라도 `onboarding_completed_at`이 없으면 완료로 추정하지 않는다.
- `COMPLETED`와 `NONE`은 추가 온보딩 입력이 없다는 뜻이다. 체류기간 유효 · 추천 이용 허가는 별도로 판단한다. 만료일이 지나도 저장된 완료 상태와 시각은 유지한다.
- `NOT_APPLICABLE`은 **새 유학생 온보딩의 적용 대상이 아니다**라는 뜻이며, 기존 가입 유형의 온보딩까지 완료했다는 뜻이 아니다. 프론트는 기존 유형별 가입 · 프로필 흐름을 사용한다. 기존 소셜 가입 응답의 `onboardingRequired=true`와도 구분한다.
- 현재 기능 플래그의 켜짐 여부나 로그인 제공자 이름으로 판정하지 않고, 서버에 저장된 회원 유형 · 회원 연결 인증 기록 · 완료 시각을 조회한다.
- 성공은 `200`, `Cache-Control: no-store`다. 미인증 · 없는 회원은 `401 UNAUTHORIZED`, 탈퇴 회원은 `403 WITHDRAWN_ACCOUNT`다.
- 조회는 데이터를 생성 · 수정하거나 외부 OCR · APICK · SMS 인증을 호출하지 않는다. 조회 중 일괄 저장이 진행되면 커밋된 상태를 일관되게 읽으며, 완료 요청 성공 후 다시 조회하면 완료 상태를 반환한다.

프론트는 신규 계정의 휴대전화 가입 성공 후 또는 기존 계정 로그인 후 이 API를 호출하고 위 분기로 이동한다. 미완료 온보딩 입력은 화면에서 모아 마지막 POST 요청으로 저장한다.

## 가입 중단과 서비스 API 접근

- 계정 생성 전 가입을 다시 시작하면 같은 소셜 계정의 이전 가입 세션·약관 동의·등록증/PDF 추출값·전화번호 인증정보를 삭제한다. 새 세션에서 모든 인증을 다시 진행한다.
- 가입 세션은 생성부터 30분간 유효하며 요청으로 연장되지 않는다. 만료 즉시 사용을 차단하고 1분 간격 정리 작업이 연결된 임시정보를 삭제한다.
- 현재 가입 등록증/PDF 처리는 파일을 읽어 OCR을 수행하고 임시 추출값만 DB에 저장한다. 가입 원본 파일을 비공개 저장소에 영구 업로드하지 않는다.
- 등록증 인증을 거친 온보딩 미완료 회원은 아래 API에서 `403 ONBOARDING_REQUIRED`를 받는다. 프로필이 일부 존재해도 완료 시각이 없으면 동일하다.
  - `/api/students/me/profile` 및 하위 경로
  - `/api/students/me/careers` 및 하위 경로
  - `/api/students/me/matching-preferences`
  - `/api/students/me/documents/**`
  - `/api/students/me/badge`, `/api/students/me/working-conditions`
  - `/api/students/me/registration-renewals/**`
  - `/api/students/me/registration`, `/api/students/me/stay-expiration-notice/**`
- 로그인·토큰 처리, 온보딩 상태 조회·최종 저장, 위치 약관 동의, 탈퇴 API는 이 차단 목록에 포함하지 않는다. 각 API의 기존 인증·검증은 유지한다.
- 최종 저장이 성공하면 같은 액세스 토큰으로 위 API를 사용할 수 있다. 실패하면 계정·가입 인증은 유지되며 서비스 접근은 계속 차단한다.
- 프론트는 홈·매칭·지원내역·마이페이지 진입을 막고 생년월일 화면으로 안내한다. 추천·지원 등 위 목록 밖 API에는 이번 차단 규칙을 적용하지 않는다.

## 입력 및 저장

화면마다 서버에 저장하지 않고 프론트에서 입력을 모아 한 번에 보낸다. 중도 이탈하면 임시값을 버리고 재로그인 후 생년월일부터 다시 시작한다. 이미 생성된 계정과 가입·인증정보는 유지한다.

```json
{
  "birthDate": "2000-05-14",
  "currentRegion": "서울특별시 마포구",
  "koreanLevel": "INTERMEDIATE",
  "studyProgram": "BACHELOR",
  "careers": [],
  "matchingPreferences": {
    "businessTypeFlexible": false,
    "preferredJobs": [{"businessTypeId": 1}],
    "preferredWorkTimes": [
      {"dayOfWeek": "MON", "startTime": "22:00", "endTime": "02:00"}
    ],
    "preferredLocation": {
      "locationName": "서울특별시 마포구",
      "latitude": 37.5665,
      "longitude": 126.978,
      "radiusM": 500
    },
    "priorities": [
      {"criterionId": 1, "priorityOrder": 1},
      {"criterionId": 2, "priorityOrder": 2},
      {"criterionId": 3, "priorityOrder": 3},
      {"criterionId": 4, "priorityOrder": 4}
    ]
  }
}
```

- 생년월일은 계정 생성 후 직접 입력하며 최종 요청의 `birthDate`는 필수다. `YYYY-MM-DD` 형식으로 보내며 한국 날짜 기준 만 18세가 되는 생일부터 허용한다. 누락·null·미래 날짜·만 18세 미만이면 400으로 전체 저장을 취소한다. 과거 가입 인증 정보의 날짜를 재사용하지 않고 이번 제출값으로 검사한다. 가입 전 생년월일 저장 API는 종료되어 400으로 최종 저장 경로를 안내한다.
- 이름은 가입 시 저장된 회원 이름을 유지한다. 국적 · 체류 만료일은 `student_registration_verification`에서 가져오며 요청으로 덮어쓸 수 없다. 체류자격은 등록증 원문 코드와 필수 `studyProgram`을 조합한다. 외국인등록번호는 받거나 저장하지 않는다.
- `careers`는 필수이며 경력 없음은 `[]`로 보낸다. 경력이 있으면 기존 경력 DTO와 동일한 `careerName`, `startMonth`, `endMonth`, `description`이 모두 필요하다. 기간은 `YYYY-MM`, 종료월은 시작월 이상이며 미래 근무기간은 허용하지 않는다.
- `topikLevel`은 온보딩에서 입력받지 않는다. `null`을 포함해 전달하면 400이다. TOPIK 성적증명서를 OCR 추출한 뒤 최종 확인해야 급수가 저장된다. 기존 확인된 급수는 온보딩으로 변경하지 않는다.
- `matchingPreferences`의 업종 여부 · 목록, 근무시간, 근무위치, 우선순위는 모두 필수다. 부분 수정 API와 달리 모든 항목을 전송해야 한다. 근무시간은 최소 1개이며 아래 연속 시간 규칙을 충족해야 한다.
- 업종 상관없음은 `businessTypeFlexible=true`, `preferredJobs=[]`다. 그 외에는 최소 1개 업종이 필요하고 기타 업종에는 `otherJobName`이 필요하다.
- 시간은 30분 단위이며 연속 1시간 이상인 구간이 최소 하나 있어야 한다. 떨어진 30분 구간을 합산하지 않으며 조건을 충족한 뒤 별도 30분 구간을 추가할 수 있다. 자정을 넘는 구간은 `MON 22:00~02:00`처럼 시작 요일 하나로 저장한다. 다음 날 및 일요일→월요일 경계의 실제 시간 겹침도 차단한다.
- 반경 `radiusM`은 정수 `100, 200, 300, 400, 500, 600, 700, 800, 900, 1000`만 허용한다(미터 단위, 100~1,000m, 100m 간격). `PATCH /api/students/me/matching-preferences`와 온보딩 일괄 저장에 동일하게 적용한다. 범위 밖 값, 100의 배수가 아닌 값, 소수·문자열·null·누락은 `400 INVALID_INPUT`이며 요청의 어떤 항목도 저장하지 않는다.
- 구직조건 부분 수정에서 `preferredLocation`을 생략하면 기존 위치·반경을 유지한다. 기존에 저장된 범위·간격 밖 반경도 조회와 다른 항목 수정 시 그대로 반환하며 자동 보정하거나 삭제하지 않는다. `preferredLocation`을 전송하면 위치명·위도·경도·반경을 모두 보내야 하고 반경은 새 입력 조건을 만족해야 한다. DB 구조와 기존 데이터는 변경하지 않는다.
- 우선순위는 근무시간 · 급여 · 거리 · 업종 4개 기준과 순위 1~4를 중복 없이 모두 보낸다.
- `minimumHourlyWage`는 입력받지 않으며 `null`을 포함해 전달하면 전체 요청을 거절한다. DB 컬럼과 기존 값은 보존하고 조회 응답에도 노출하지 않는다. 우선순위의 급여 항목은 유지한다.

이미 개별 API로 만든 미완료 프로필이 있다면 제출한 기본정보·학업과정을 갱신하고 경력 · 업종 · 시간 · 우선순위 목록을 교체한다. 학교·학년·과거 학적 상태 등 요청 대상이 아닌 기존 정보는 유지한다. 선택한 학업과정과 인증된 세부 체류자격이 충돌하면 오류로 전체 롤백한다. 경력 개별 쓰기 API는 종료되었으며 온보딩에서는 이 최종 제출로만 경력을 저장한다.

## 응답과 원자성

성공은 `200`, `Cache-Control: no-store`다.

```json
{"studentId":1,"completed":true,"completedAt":"2026-11-20T03:00:00Z"}
```

프로필 · 경력 · 구직조건 · `student_profile.onboarding_completed_at`을 한 트랜잭션으로 저장한다. 검증이나 DB 저장이 실패하면 기존 정보까지 모두 이전 상태로 돌아간다. 회원 행을 먼저 잠가 같은 회원의 완료 · 프로필 수정 · 구직조건 수정 · 경력 변경을 순서대로 처리한다. 동시 완료 요청은 한 건만 성공하며, 완료 후 재제출은 `409 ONBOARDING_ALREADY_COMPLETED`다. 다시 제출해도 완료 시각을 바꾸거나 경력을 중복 생성하지 않는다. 완료 후에는 [내 정보 최종 일괄 저장](student-information.md) 또는 매칭 조건 관리 API를 사용한다.

| 상태 | 오류 |
| --- | --- |
| 400 | `INVALID_INPUT`: 필수 항목 누락, 잘못된 형식, 경력 기간 또는 구직조건 오류 |
| 401 | `UNAUTHORIZED`: 유효한 로그인 없음 |
| 403 | `FORBIDDEN`, `WITHDRAWN_ACCOUNT` |
| 404 | `MATCHING_REFERENCE_NOT_FOUND`: 등록되지 않은 업종 · 우선순위 기준 |
| 409 | `PHONE_NOT_VERIFIED`, `REGISTRATION_VERIFICATION_REQUIRED`, `ONBOARDING_ALREADY_COMPLETED` |

오류 응답은 기존 `{"code":"...","message":"..."}` 구조를 사용한다.

## 체류기간과 후속 작업

가입 이후 체류기간이 만료되어도 이 API의 입력 · 완료는 허용한다. 확인된 만료일을 그대로 프로필에 옮기며 카드 · PDF 인증 시각을 바꾸지 않는다. `completed=true`는 입력 완료이고 체류기간 유효 · 추천 이용 허가가 아니다.

체류기간 만료에 따른 추천 제한과 추천·지원·채용 제안·매칭 API의 온보딩 접근 제한은 별도 연결 사항이다. 등록증 갱신은 온보딩 완료 후 기존 갱신 API를 사용한다. 상태 조회 응답을 전역 서비스 접근 허가로 사용하지 않는다.

V25는 완료 시각 컬럼만 추가한다. 기존 회원의 완료 여부를 임의로 추정해 일괄 변경하지 않는다.

V26의 가입 세션·회원 연결 인증 정보 `birth_date` 컬럼과 기존 값은 유지한다. 새 가입에서 날짜를 저장·이전하지 않고 온보딩에서도 재사용하지 않는다. 기존 nullable 컬럼과 V25 완료 시각을 사용하므로 이번 변경에 새 Flyway 마이그레이션은 필요하지 않다.

## 학업과정과 서비스 지원 대상 (요구사항 2번)

온보딩 요청의 `studyProgram`은 필수 문자열이며 아래 다섯 값만 허용한다. 숫자 ID를 클라이언트에서 고정하지 않는다.

| studyProgram | 학업과정 | 최종 visaCode |
| --- | --- | --- |
| ASSOCIATE | 전문학사 | D-2-1 |
| BACHELOR | 학사 | D-2-2 |
| MASTER | 석사 | D-2-3 |
| DOCTORATE | 박사 | D-2-4 |
| EXCHANGE | 교환학생 | D-2-6 |

- 등록증에서는 `D-2`를 확인한다. `D-2-1` 등의 세부 코드는 OCR 추출 대상이 아니며 사용자가 선택한 학업과정으로 생성해 프로필에 저장한다. 예: 등록증 `D-2` + `MASTER` 선택 → 프로필 `D-2-3`. 등록증 인증 기록은 `D-2` 그대로 보존한다. 등록증 체류자격이 `D-2`가 아니면 `400 INVALID_INPUT`이며 모든 온보딩 변경을 취소한다.
- 지원하지 않는 체류자격은 등록증 인식·후속 확인·전화번호 가입 확정에서 `422 UNSUPPORTED_VISA`로 차단한다. 이전 버전에서 생성한 미지원 인증 기록도 온보딩을 완료할 수 없다. 등록증 원문 코드와 인증 시각은 그대로 보존한다.
- `GET /api/students/me/profile`과 프로필 수정 응답의 `studyProgram`은 선택 코드이며, `studyProgramId`는 DB의 기준 데이터 ID다. 기존 미입력·알 수 없는 기존 과정은 `studyProgram=null`로 반환하며 조회 시 추정하여 저장하지 않는다.
- 학교·학년·학적 상태는 온보딩에서 입력하지 않는다. 프로필 PATCH에서도 `universityName`, `gradeLevel`, `academicState`, `studyProgram`, `studyProgramId`를 보내면 400이다. 기존 값은 보존하며 학교·학년은 [재학증명서 OCR 최종 등록](student-enrollment.md)으로 반영한다.
- 인증 회원의 프로필 PATCH에서 `visaCode`를 직접 보내면 400이다. 등록증 인증 기록이 없는 개발용 기존 프로필은 지원 세부 코드만 입력 가능하며 저장된 학업과정과 충돌할 수 없다.
- Flyway V29는 다섯 학업과정을 이름 기준으로 추가한다. 기존 이름의 ID와 기존 회원 프로필은 변경하지 않는다. 기존 미지원 회원의 추천·지원 제한 소급 적용은 포함하지 않는다.
