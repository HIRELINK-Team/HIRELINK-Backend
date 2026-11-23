# 채용 완료 삭제 API

## 정책

- 사업주는 본인 소유 공고의 채용 완료만 삭제할 수 있다.
- 삭제된 채용 완료는 사업주·유학생 목록, 상세·연락처 조회, 사업주 비자 만료 문자 대상에서 즉시 제외된다.
- 삭제는 복구 API를 제공하지 않는 소프트 삭제다. 채용 성립 이력인 `Application(COMPLETED)`, `EmploymentOffer(ACCEPTED)`, 지원 조회의 `matchId`는 유지한다. 삭제된 `matchId`로 채용 완료 상세를 조회하면 `404 EMPLOYMENT_MATCH_NOT_FOUND`를 반환한다.
- 이미 삭제된 항목을 다시 삭제하거나 빈 공고를 전체 삭제하면 `204 No Content`를 반환한다.

## 선택 삭제

`DELETE /api/job-postings/{jobPostingId}/matches`

```json
{
  "matchIds": [501, 502]
}
```

요청한 ID 전체가 존재하고 경로의 공고에 속하는지 먼저 검증한 뒤 하나의 트랜잭션으로 삭제한다. 없는 ID가 있으면 `404 EMPLOYMENT_MATCH_NOT_FOUND`, 다른 공고의 ID가 포함되면 `403 FORBIDDEN`이며 일부만 삭제하지 않는다.

## 공고별 전체 삭제

`DELETE /api/job-postings/{jobPostingId}/matches/all`

현재 남아 있는 해당 공고의 채용 완료를 하나의 트랜잭션으로 전부 삭제한다. 다른 공고나 다른 사업주의 채용 완료는 변경하지 않는다.
