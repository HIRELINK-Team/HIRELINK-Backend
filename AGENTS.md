# HIRELINK Backend Agent Guide

목표는 필요한 코드와 테스트만 읽고, 작은 변경으로 작업을 끝내는 것이다. 이 문서와 저장소 원문이 충돌하면 현재 코드와 저장소 문서를 우선한다.

## 탐색

- 이슈, 요청, 변경 파일에서 시작해 관련 도메인 패키지와 가장 유사한 기존 구현·통합 테스트만 먼저 찾는다.
- 전체 저장소 구조나 모든 문서를 선행 탐색하지 않는다. 공통 설정, 보안, 마이그레이션처럼 영향 범위가 넓을 때만 탐색을 넓힌다.
- 협업 규칙은 `docs/convention/git-workflow.md`, 커밋 형식은 `docs/convention/commit-message.md`, PR 형식은 `.github/PULL_REQUEST_TEMPLATE.md`를 필요한 시점에만 확인한다.

## 구현

- 요청 범위 밖 리팩터링, 이름 변경, 포맷 정리, 의존성 변경을 하지 않는다.
- 기존 Controller/Service/Repository/Entity/DTO 패키지 구조와 생성자 주입 방식을 따른다.
- 추천·지원·채용 제안·채용 확정 로직은 `recommendation`, `application`, `employmentoffer`, `employmentmatch`의 기존 엔티티 상태 전이 메서드, 상태·종료 사유, 잠금 순서와 관련 테스트를 기준으로 한다. 근거 없는 상태나 전이를 새로 만들지 않는다.
- 예외와 API 응답은 기존 예외 클래스, `GlobalExceptionHandler`, `ErrorResponse(code, message)`, 기존 DTO 형식을 재사용한다.
- API 계약이나 DB 구조가 바뀌면 관련 문서·Flyway 마이그레이션까지 같은 범위에서 확인한다.

## 검증

- 변경 도메인의 관련 테스트를 먼저 실행한다. 예: `./gradlew test --tests 'com.hirelink.domain.application.ApplicationRejectIntegrationTest' --no-daemon`.
- 관련 테스트가 통과한 뒤 공통 코드·설정·마이그레이션 변경처럼 영향이 넓을 때만 `./gradlew build --no-daemon`을 실행한다.
- 통합 테스트는 Docker와 Testcontainers PostgreSQL을 사용한다. 실행하지 못한 검증은 통과했다고 쓰지 않는다.
- 완료 전 `git diff`와 변경 파일만 다시 읽어 범위 이탈, 상태 전이, 예외·응답, 불필요한 로그·주석을 자체 리뷰한다.

## Git과 PR

- 커밋을 요청받은 경우 구현과 테스트를 각각 검토 가능한 커밋으로 분리하고 기존 커밋 메시지 규칙을 따른다.
- PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`의 문구와 구조를 유지하며, 미실행 테스트와 후속 작업을 명시한다.

## 소통

- 같은 분석을 반복하거나 장문 진행 보고를 하지 않는다. 새 판단, 위험, 막힘이 있을 때만 짧게 알린다.
- 최종 답변은 `구현 요약`, `테스트`, `커밋`, `남은 문제`, `PR 본문`만 간결하게 작성한다. 해당 사항이 없으면 `없음`으로 표시한다.
