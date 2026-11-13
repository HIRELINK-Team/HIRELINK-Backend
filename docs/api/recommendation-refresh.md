# 우선순위 변경과 추천 갱신

우선순위 저장과 현재 추천 반영은 다음 순서로 분리한다.

1. `PATCH /api/students/me/matching-preferences`로 새 우선순위를 저장한다.
2. 프론트는 저장 완료 후 현재 추천을 갱신할지 사용자에게 확인한다.
3. 사용자가 거절하면 갱신 API를 호출하지 않는다. `GET /api/recommendations`와
   `GET /api/recommendations/{id}`는 기존 배치의 `generatedAt`, 점수, 이유를 그대로 반환한다.
   우선순위만 변경된 경우 `refreshRequired`는 `false`이며 유효한 미지원 추천으로 지원할 수 있다.
4. 사용자가 갱신을 선택하면 `POST /api/recommendations/refresh`를 호출한다. 저장된 최신
   우선순위로 즉시 재계산하되, `WAITING_RESPONSE`, `OFFERED` 지원이 연결된 추천은
   유지하고 나머지 자리만 채우며 `generatedAt`을 갱신한다.

즉시 갱신하지 않아도 기존 `generatedAt`에서 24시간이 경과하면 다음 목록 조회에서
저장된 최신 우선순위로 자동 갱신한다. 근무시간, 위치, 희망급여, 공고 상태 등
기존 갱신 사유의 판정은 유지한다.
