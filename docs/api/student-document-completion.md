# 마이페이지 서류 등록 완성률

## 조회 API

`GET /api/students/me/documents/completion`

유학생 access JWT가 필요하다. 본인의 완성률만 조회하며 사용자 ID·추출값을 입력받지 않는다. 응답은 `Cache-Control: no-store`다.

```json
{
  "completionPercentage": 75,
  "registrationIncluded": false,
  "enrollmentIncluded": true,
  "transcriptIncluded": true,
  "topikIncluded": true
}
```

- 각 `Included` 항목이 true일 때 25%를 더한다. 합계는 0·25·50·75·100%만 반환한다.
- 등록증: 최종 인증 기록의 `cardVerifiedAt`이 있고 `stayExpiresOn`이 한국 날짜 기준 오늘 이상이어야 반영한다. 프로필에 만료일만 입력되어 있거나 미확정 갱신 만료일만 있는 경우는 반영하지 않는다.
- 재학·성적·TOPIK: 해당 종류에 최종 원본 연결(`objectKey`)과 등록 시각(`registeredAt`)이 모두 있어야 반영한다. OCR 추출·파일 업로드만 끝난 draft는 제외한다.
- 재업로드 중에도 이전 최종 등록 서류가 있으면 그 서류의 25%를 유지한다. 실패/중단만으로 기존 등록 상태가 바뀌지 않는다.
- 체류 만료일 당일까지 등록증 25%를 유지하고 다음 날부터 제외한다. 세 증명서를 최종 등록한 회원은 100%에서 75%가 된다.
- 등록증 갱신이 최종 성공하여 유효한 만료일이 저장된 후 다시 조회하면 25%가 복구된다. 기존 만료일이 지난 상태에서 갱신 시작·OCR·진위/PDF 확인만으로는 복구하지 않는다.
- 조회 시 계산하므로 만료 반영을 위한 배치나 완성률 저장 컬럼이 필요 없다. 네 항목은 읽기 전용 트랜잭션의 동일한 DB 스냅샷으로 조회한다.
- 완성률 조회는 배지 발급 시각·계정·서류 데이터를 변경하지 않는다.

오류는 기존 `ErrorResponse(code, message)` 형식이다. 미인증은 401, 사업주/탈퇴 계정은 403, 유학생 프로필이 없으면 404다. 최종 등록 서류가 없는 정상 프로필은 오류 대신 0%를 반환한다.

## 변경 범위와 검증

- Flyway 영향 없음: 기존 `student_registration_verification`, `student_enrollment_document`의 최종 상태를 읽으며 스키마·제약·저장 데이터 변경이 없다.
- 모든 조합의 퍼센트, 최종 확인 전/후, 재업로드 실패, 만료/갱신 반영, 본인 접근 제한, Swagger를 CI에서 검증한다. 로컬 빌드/테스트는 실행하지 않는다.
