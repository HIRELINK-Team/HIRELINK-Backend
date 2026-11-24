# 사업주 가입 및 온보딩

## 계정 생성과 중단

소셜 인증 → 약관 동의 → 가입용 전화번호 인증 성공 시 계정 생성은 기존 공통 API를 유지한다.
유학생 인증·가입·온보딩 API의 동작은 변경하지 않는다.

| 중단 시점 | 계정·임시 정보 | 외부 소셜 연결 | 재시작 |
| --- | --- | --- | --- |
| 약관 또는 전화번호 인증 전 | 계정 없음. 임시 가입 정보는 소셜 인증부터 30분 후 만료·정리 | 자동 해제하지 않음 | 소셜 로그인 시작 API로 다시 시작하면 이전 가입 정보·임시 동의·인증번호 폐기 후 약관부터 진행 |
| 전화번호 인증 성공 후 온보딩 미완료 | 계정·전화번호·정식 약관 유지 | 자동 해제하지 않음 | 이름·유입 경로부터 다시 입력 |
| 온보딩 완료 후 | 계정·프로필·사업장 유지 | 자동 해제하지 않음 | 대시보드 |

30분 안에 기존 가입 쿠키로 단순 복귀하는 경우에는 기존 공통 임시 가입 정보가 유효할 수 있다.
가입 시작 버튼은 기존 소셜 시작 API를 호출해야 한다. 소셜 연결 해제와 가입 세션 삭제는 별개다.

## 계정 생성 이후 프론트 연결

모든 아래 API는 Bearer accessToken을 사용한다. 가입용 socialSignup 쿠키로는 호출할 수 없다.

1. 로그인/계정 생성 후 `GET /api/employers/me/onboarding`을 조회한다.
   - `REQUIRED / BASIC_INFORMATION`: 이름·유입 경로부터 입력한다.
   - `COMPLETED / DASHBOARD`: 대시보드로 이동한다.
   - 공통 `GET /api/users/me/onboarding`은 기존 유학생 계약을 유지한다. 사업주는 전용 상태 조회를 사용한다.
2. 미완료 온보딩에 진입·재진입할 때 `POST /api/employers/me/onboarding/restart`를 호출하고 클라이언트의 이전 화면 초안을 비운다.
   - 기존 시도·OCR·사업자번호 인증을 폐기하고 새 `attemptId`, `expiresAt`을 반환한다.
   - 시도는 현재 로그인에 연결되므로 새 로그인으로는 이전 시도를 사용할 수 없다.
   - 시도 유효기간은 30분이다. 만료는 회원 계정·약관·전화번호를 삭제하지 않는다.
   - 단순 HTTP 조회만으로는 초기화하지 않는다. 브라우저 종료를 서버가 감지한다고 가정하지 않는다.
3. 이후 OCR·번호 인증·최종 저장에 `X-Onboarding-Attempt: <attemptId>` 헤더를 보낸다.
4. 이름·유입 경로 입력 때 기존 프로필 생성 API를 호출하지 않고 화면에서 입력을 유지한다.
5. 마지막 완료에서 전체 입력을 한 번에 제출한다. 성공하기 전까지 새 프로필·사업장은 저장하지 않는다.

기존 개별 프로필·사업장 API는 기존 클라이언트 호환을 위해 유지한다. 새 온보딩은 아래 최종 저장 API를 사용해야 완료 상태가 기록된다.
추천·지원·채용 제안·매칭 API의 접근 정책은 이번 변경에 포함하지 않는다.

## 등록증 제출 및 번호 인증

- `POST /api/employers/me/onboarding/business-registration/ocr`
  - `multipart/form-data`, `businessRegistrationFile` 한 개. PDF·JPG·PNG 최대 5MB.
  - 사업자등록번호만 추출하고 번호 OCR 성공 이력을 현재 시도에 기록한다.
  - 원본 파일은 보관하지 않는다. 주소는 검색 SDK 등에서 별도로 입력한다.
  - 응답 예: `{ "businessRegistrationNumber": "123-45-67890" }`
  - 번호 미인식은 422. 재촬영·재업로드가 필요하다. 실패한 재업로드도 이전 OCR·번호 인증을 무효화한다.
- `POST /api/employers/me/onboarding/business-registration/verify`
  - JSON `{ "businessRegistrationNumber": "123-45-67890" }`
  - 사용자가 OCR 결과를 수정할 수 있다. 현재 시도의 OCR 성공 없이 인증은 불가하다.
  - 국세청 **상태 조회**만 사용한다. 대표자명·개업일자 진위 확인은 하지 않는다.
  - 계속사업이면 `valid=true`. 휴업·폐업·미등록은 `valid=false`. 외부 장애는 502.
  - 번호를 수정하면 화면의 인증 완료 상태를 해제하고 다시 인증한다. 서버도 최종 제출 번호와 인증 번호가 다르면 거부한다.
- 검증 범위는 등록증 제출·번호 추출·사업자 영업 상태 확인이다. 수정 번호와 원본 일치 또는 대표자 신원을 보증하지 않는다.

기존 `/api/workplaces/business-registration/ocr`는 응답 호환을 위해 `workplaceAddress`를 남기지만 새 OCR은 주소를 추출하지 않으므로 null이다. 새 온보딩 OCR 응답에는 주소 필드가 없다.

## 최종 저장

`POST /api/employers/me/onboarding`

```json
{
  "profile": { "name": "김민수", "acquisitionChannel": "SEARCH" },
  "workplace": {
    "workplaceName": "예시카페",
    "businessRegistrationNumber": "123-45-67890",
    "businessTypeId": 1,
    "employeeCountRange": "UNDER_5",
    "workplaceAddress": "서울특별시 성동구 예시로 10",
    "workplaceAddressDetail": "2층",
    "latitude": 37.5665,
    "longitude": 126.978
  }
}
```

현재 로그인·사업주 계정·전화번호 인증·시도·OCR·인증된 최종 번호를 검사한다.
국세청 상태를 다시 조회하고 계속사업인 경우에만 프로필·사업장·완료 시각을 한 트랜잭션으로 저장한다.
기존 일부 프로필이 있으면 이름·유입 경로는 최종 제출값으로 갱신하며 직책은 유지한다.
실패하면 프로필·사업장 변경을 모두 롤백하고 기존 계정·정식 약관·전화번호 인증은 유지한다.
회원 행 잠금으로 최종 제출과 재시작을 직렬화한다. 성공한 시도는 소모되며 중복 최종 제출은 409다.
완료 응답은 `userId`, `workplaceId`, `completedAt`이며 새 계정이나 로그인 토큰을 발급하지 않는다.

주요 오류: 409 `EMPLOYER_ONBOARDING_RESTART_REQUIRED`, `EMPLOYER_ONBOARDING_COMPLETED`,
`BUSINESS_DOCUMENT_REQUIRED`, `BUSINESS_VERIFICATION_REQUIRED`, `PHONE_NOT_VERIFIED`,
`DUPLICATE_BUSINESS_REGISTRATION`. 국세청 조회 오류는 502이며 가입 완료로 처리하지 않는다.

## 주소 및 기존 데이터

- 등록·상세 조회·수정에 `workplaceAddressDetail`을 추가한다. 기존 호출 호환을 위해 선택 필드로 제공한다.
- `workplaceAddress`는 기본 주소, `latitude`/`longitude`는 검색한 기본 주소의 좌표다.
- 상세 주소만 수정할 때 좌표는 유지한다. 빈 문자열로 상세 주소를 지울 수 있다.
- 기본 주소 변경은 좌표와 함께 전송한다. 변경된 기본 주소에 상세 주소가 생략되면 이전 상세 주소를 비운다.
- 기존 전체 주소는 임의로 분리하지 않는다. 기존 데이터의 기본 주소 응답은 원래 주소를 그대로 제공한다.
- DB의 기존 `workplace_address`와 엔티티 `getWorkplaceAddress()`는 표시용 전체 주소를 유지하므로 다른 도메인의 주소 읽기는 유지된다.
- V44는 기존 프로필과 사업장을 모두 가진 회원을 완료 상태로 보정한다. 기존 행과 값은 삭제하지 않는다.

## 검증 및 배포

V44 적용이 필요하다. CI의 임시 PostgreSQL 마이그레이션 검증은 운영 DB 적용과 다르다.
백엔드·OCR 변경은 배포 시 함께 반영한다. 실제 운영 DB 적용·외부 소셜/SMS/국세청/OCR QA는 별도로 수행한다.
