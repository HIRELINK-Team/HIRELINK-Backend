# 성적·TOPIK 증명서와 유학생 탈퇴

기존 [재학증명서 API](./student-enrollment.md)와 같은 비공개 `student-documents` 버킷, 5MB 제한, PDF/JPEG/PNG 업로드를 사용한다. OCR은 정보 추출이며 서류 진위 확인이 아니다. 실제 학교별 서식의 인식 품질은 운영 전 별도 확인한다.

## 성적·TOPIK API

JWT 인증이 필요하며 본인의 활성 유학생 프로필만 사용할 수 있다. 아래 `{kind}`는 `transcript` 또는 `topik`이다.

| 메서드·경로 | 입력·결과 |
| --- | --- |
| POST `/api/students/me/documents/{kind}/preview` | multipart `file`. `draftId`와 `extracted` 반환. 프로필과 최종 원본은 그대로 유지 |
| POST `/api/students/me/documents/{kind}/{draftId}/confirm` | 수정값 요청 본문 없음. 본인의 해당 종류 최신 초안만 확정. 프로필·원본 교체 후 이전 원본 삭제 |
| GET `/api/students/me/documents/{kind}` | 최종 확정된 결과만 반환. 미확정이면 404 |

응답 예시:

```json
{
  "documentType": "TRANSCRIPT",
  "draftId": "ad0856ec-3b1e-4d1d-88cd-1f5063aa2720",
  "extracted": {"academicYear": 2025, "semester": "SECOND", "averageGpa": 4.24, "topikLevel": null},
  "registeredAt": null
}
```

- 성적은 문서에 명시된 학기 중 가장 최근 학기의 **평균 학점**이다. 인쇄된 마지막 행이나 누적 평점을 대신 사용하지 않는다. 만점 기준 저장·환산은 하지 않는다. `semester`는 `FIRST`, `SUMMER`, `SECOND`, `WINTER`이다.
- 현재 파서는 학년도·학기 표기와 평점평균/평균평점/평균학점/GPA 라벨이 있는 한국어 학기 서식을 지원한다. 최신 학기의 평균을 특정하지 못하거나 후보가 충돌하면 422로 재업로드를 안내한다. 임의의 과거 학기 값을 사용하지 않는다.
- TOPIK은 1~6급만 저장한다. 응답은 `topikLevel` 외 추출 필드가 null이다.
- 프로필 PATCH와 온보딩의 `topikLevel` 입력은 **숫자·null 모두 400**이다. 확인된 TOPIK 값은 해당 서류의 최종 등록으로만 갱신한다. 한국어 자기평가 수준은 기존처럼 수정할 수 있다.
- 최종 확정 전에 이탈하거나 저장·검증이 실패하면 기존 확정 정보와 원본을 유지한다. 동일 초안 중복 확정은 409다. 다른 서류 종류는 변경하지 않는다.
- 종류별 최신 확정 원본 한 개와 확인 대기 초안을 구분한다. 이전 파일은 DB 커밋 이후 삭제하며 실패하면 기존 정리 큐에서 재시도한다. 원본 경로나 공개 URL은 응답에 포함하지 않는다. 개별 삭제 API는 없다.
- 401 인증, 403 비유학생/탈퇴, 404 미등록, 409 초안 없음·교체됨, 413 5MB 초과, 415 파일 형식, 422 추출 불가, 502 OCR·저장소 오류. 오류 응답은 기존 `{code,message}` 형식이다.

## 탈퇴·복구

`POST /api/students/me/withdrawal`에 JWT와 `{"confirmed":true}`를 전송한다. 응답은 `requestedAt`, `recoveryDeadline`이다.

1. 요청 즉시 휴면으로 표시하고 refresh token을 삭제한다. JWT의 계정 버전을 변경하므로 이전 access/refresh token은 복구 후에도 재사용할 수 없다. 원본과 소셜 연결은 유지한다.
2. 요청 이후 새로 수행한 소셜 인증의 결과 교환이 30일 경계 **이전**에 완료되면 복구한다. 탈퇴 전에 발급된 가입/로그인 세션으로 복구할 수 없다.
3. 30일 경계부터 복구를 거절한다. 정리 작업은 세 종류의 확정 원본·초안·대기 삭제 파일을 실제 삭제하고 Google/Kakao 연결을 해제한 뒤 로컬 인증 연결을 제거한다. 외부 실패 시 완료로 표시하지 않고 재시도한다.
4. 팀원 도메인의 관계를 유지하기 위해 사용자 식별 행을 물리 삭제하지 않는다. 이메일·전화번호·이름은 비우고 최종 탈퇴 시각을 기록한다. 이 작업이 전체 서비스의 모든 개인정보 보관 정책을 구현했다는 뜻은 아니다.

사업주 열람 API와 매칭 삭제·휴면 중 열람 정책은 공동 협의 전까지 제공하지 않는다. 매칭·추천·지원·채용 제안 코드나 테스트는 변경하지 않는다.

## 운영 설정과 확인

- `STUDENT_WITHDRAWAL_ENABLED=true`: 아래 자격 증명 준비 후 탈퇴 신청과 정리 스케줄러 활성화. 기본값은 false이며 이때 신청은 503이다. 기존 로그인은 유지된다.
- `SOCIAL_TOKEN_ENCRYPTION_KEY`: 암호학적 난수 **32바이트의 Base64**, JWT 키와 별도 관리. Google refresh token은 AES-256-GCM으로 암호화해 저장하며 사용자 소셜 식별자에 결합한다. 이 키를 분실/임의 교체하면 기존 연결 해제를 처리할 수 없다.
- `KAKAO_ADMIN_KEY`: 해당 서비스 앱의 사용자 연결 해제 권한이 있는 관리자 키. 저장소나 로그에 기록하지 않는다.
- 활성화 시 유학생 Google 로그인에 offline/consent를 요청해 연결 해제용 refresh token을 확보한다. 기존 Google 회원에게 자격 증명이 없으면 탈퇴 신청에 `409 SOCIAL_REAUTH_REQUIRED`를 반환하고 새 소셜 로그인을 요구한다. 회원 가입 대기 세션의 암호화 값은 가입 완료 시 인증 계정으로 승계한다.
- `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, 비공개 `student-documents` 버킷 연결 및 OCR 컨테이너 배포를 확인한다. 실제 업로드→결과→확정, 실제 공급자 연결 해제는 CI의 모의 호출만으로 검증되지 않는다.
- Flyway V31은 기존 재학증명서 테이블에 종류별 복합 키와 성적/TOPIK 필드를 추가한다. V32는 기존 사용자·인증·소셜 세션 테이블에 탈퇴 시각·계정 버전·암호화 자격 증명 필드를 추가한다. 기존 Flyway 파일은 변경하지 않는다.

공급자 연결 해제 계약: [Google OAuth](https://developers.google.com/identity/protocols/oauth2/web-server), [Kakao 로그인 REST API](https://developers.kakao.com/docs/en/kakaologin/rest-api).
