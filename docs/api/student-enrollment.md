# 재학증명서 OCR 및 최종 등록

## 범위와 순서

요구사항 3번의 학년 판정·등록 차단·기존 자료 보존을 구현한다. 이 동작에 필요한 재학증명서 업로드/확인/등록 기반을 함께 제공한다. 성적증명서·TOPIK·탈퇴 정리는 [서류 확장 API](./student-academic-documents.md)에 정리한다. 사업주 원본 열람은 공동 협의 후 연결한다.

1. 로그인한 유학생이 `POST /api/students/me/documents/enrollment/preview`에 `multipart/form-data`의 `file`을 보낸다. PDF/JPG/PNG, 5MB 이하이며 헤더와 MIME 유형이 일치해야 한다.
2. OCR로 재학증명서의 대학교·캠퍼스(표시된 경우)·학년 또는 개인 학기 수를 인식한다. 추출할 수 없거나 값이 상충하면 재업로드를 요청한다. OCR 정보 추출은 서류 진위 인증을 뜻하지 않는다.
3. 성공 시 `draftId`, `universityName`, `campusName`, 환산한 `gradeLevel`, 원본 추출값 `extracted`를 반환한다. 이 시점에는 기존 최종 서류·프로필을 변경하지 않는다.
4. 사용자가 결과를 확인한 뒤 `POST /api/students/me/documents/enrollment/{draftId}/confirm`을 호출한다. 수정한 학교·학년 등을 받지 않고 서버 보관값을 재검증한다. 본인의 최신 미확정 결과만 등록할 수 있다.
5. 성공 시 원본 연결·학교·학년을 원자적으로 갱신하고 등록 시각을 반환한다. 캠퍼스는 재학증명서 정보에 저장한다. `GET /api/students/me/documents/enrollment`는 최종 등록 정보만 조회한다.

모든 성공 응답은 `Cache-Control: no-store`이며 원본 경로나 공개 URL은 노출하지 않는다. 원본 열람 API는 이번 범위에 포함하지 않는다.

## 학년 판정

- 명시된 학년이 있으면 그 값을 우선 사용한다.
- 학년이 없으면 개인의 현재 재학 학기 또는 완료한 이수 학기 수를 `ceil(학기 수 / 2)`로 환산한다. 다음 학기를 더하지 않는다.
- 1~2학기=1학년, 3~4학기=2학년, 5~6학기=3학년, 7~8학기=4학년이다.
- `2026학년도 2학기` 같은 학사 일정은 개인 학기 수로 사용하지 않는다. 그것만으로 학년을 추측하지 않는다.
- 명시 학년 5 이상 또는 환산에 사용하는 학기 수 9 이상이면 `422 UNSUPPORTED_GRADE_LEVEL`, 메시지 `지원하는 학년 범위를 벗어났습니다`를 반환한다. 명시 학년이 있을 때는 학기 수로 대체하거나 재계산하지 않는다.
- 0·음수·누락·서로 다른 복수 학년은 인식 불가로 처리한다. 학년이 없는데 서로 다른 개인 학기 수가 인식되어도 추측하지 않는다.

## 교체·실패와 파일 정리

- 미확정 파일과 최종 파일은 서로 다른 경로를 사용한다. 미확정 재업로드 성공 시 이전 미확정 파일만 교체한다.
- 인식 불가·범위 초과·저장 오류·DB 롤백은 기존 최종 원본과 프로필을 유지한다. 범위 검사는 업로드와 최종 확인 두 단계에 적용한다.
- 같은 회원의 등록은 계정 행 잠금으로 직렬화한다. 오래된 draftId, 다른 회원의 draftId, 이미 확정한 draftId는 409이며 다시 등록하지 않는다.
- 최종 DB 커밋 후 이전 원본 삭제를 시도한다. 저장소 장애 시 삭제 작업을 DB에 유지하고 재시도한다. 삭제 실패를 새 등록 실패로 되돌리지 않는다.
- 업로드 직전에 별도 트랜잭션으로 정리 작업을 기록한다. 새 파일을 정상 연결하면 작업을 같은 등록 트랜잭션에서 제거한다. DB 롤백·프로세스 중단으로 연결하지 못한 파일은 1시간 뒤 정리 대상이 된다. 연결된 미확정·최종 파일에는 이 기한을 적용하지 않는다.
- 정리 스케줄러는 기본 60초 주기로 최대 20건을 처리한다. 삭제 실패 시 5분 뒤 재시도한다. 운영 지연 중 이전 파일의 물리 삭제가 늦어질 수 있지만 API는 최신 최종 등록 정보만 제공한다.

## 오류 계약

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | INVALID_INPUT | multipart 또는 draftId 형식 오류 |
| 401 | UNAUTHORIZED | 계정 인증 없음 |
| 403 | FORBIDDEN / WITHDRAWN_ACCOUNT | 유학생 아님 / 탈퇴 계정 |
| 404 | STUDENT_PROFILE_NOT_FOUND / ENROLLMENT_NOT_REGISTERED | 프로필 / 최종 서류 없음 |
| 409 | ENROLLMENT_DRAFT_NOT_FOUND | 본인의 최신 미확정 결과가 아님 |
| 413 | FILE_TOO_LARGE | 5MB 초과 |
| 415 | UNSUPPORTED_FILE_TYPE | 유형 또는 파일 헤더 오류 |
| 422 | UNREADABLE_ENROLLMENT / UNSUPPORTED_GRADE_LEVEL | 인식 불가 / 지원 학년 초과 |
| 502 | ENROLLMENT_OCR_FAILED / STUDENT_DOCUMENT_STORAGE_FAILED | OCR 또는 저장소 실패 |

## 설정 및 마이그레이션

- V30은 `student_enrollment_document`와 `student_document_cleanup`을 추가한다. 기존 프로필·학년은 일괄 수정하지 않는다. 새 최종 서류의 학년에는 1~4 DB 제약을 둔다.
- `STUDENT_DOCUMENT_PADDLE_URL`: 로컬 기본 `http://localhost:8091`, 배포 Compose에서는 `http://ocr:8000`이다. 내부 OCR 경로는 `/student/enrollment`다.
- `SUPABASE_URL`, `SUPABASE_SECRET_KEY`는 기존 서버 저장소 설정을 사용한다. `STUDENT_DOCUMENT_BUCKET` 기본값은 `student-documents`이며 **사전에 비공개 버킷으로 생성**해야 한다. 업로드 시 버킷의 `public=false`를 확인하며 공개 버킷이면 업로드하지 않는다. 클라이언트에 버킷 읽기 정책이나 서버 비밀키를 제공하지 않는다.
- 공개 버킷은 파일 다운로드 접근 제어를 우회하므로 증명서에 사용하지 않는다. [Supabase 공식 버킷 문서](https://supabase.com/docs/guides/storage/buckets/fundamentals)
- 배포 시 백엔드와 OCR 이미지를 함께 갱신한다. 실제 운영 버킷 생성·DB 적용·실제 서류 인식 검증은 코드 테스트와 별도다.

## 검증

로컬 테스트·빌드는 실행하지 않는다. CI에서 학년 우선/학기 1~9/학사 일정 제외/OCR 오류, 업로드와 최종 등록 분리, 범위 초과와 DB 실패 시 원본 보존, 권한, 동시 확인, 파일 삭제 재시도, 기존 데이터 보존 마이그레이션, Swagger를 검증한다. OCR 엔진 출력과 외부 저장소는 합성 자료·테스트 서버 또는 mock을 사용하며 실제 학교 서류의 인식 정확도를 보증하는 테스트는 아니다.
