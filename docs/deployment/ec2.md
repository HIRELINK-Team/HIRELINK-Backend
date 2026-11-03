# EC2 배포

EC2 한 대에서 Docker Compose로 Caddy, Java 백엔드, OCR을 실행한다.
PostgreSQL은 Supabase의 별도 프로젝트를 사용한다.

## 실행 환경

- Ubuntu 24.04 LTS, x86_64, 메모리 4GB 이상
- Docker Engine과 Docker Compose 2.30 이상
- EC2 보안 그룹: SSH 22는 관리자 IP, HTTP 80과 HTTPS 443은 인터넷 접근 허용
- 도메인의 A 레코드를 EC2에 연결된 탄력적 IP로 설정
- 배포 설정이 병합된 최신 `main` 사용

`compose.yaml`은 로컬 개발용이고, 배포에서는 `compose.deploy.yaml`을 단독으로 사용한다.
배포 파일에는 PostgreSQL 컨테이너가 없다.
Caddy만 호스트의 80·443 포트를 공개한다. Java와 OCR 포트는 외부에 공개하지 않는다.
OCR은 외부 인터넷 접근이 없는 내부 네트워크에 연결하며, 모델은 이미지 빌드 때 준비한다.

## Supabase 준비

1. HIRELINK 테이블이 없는 새 프로젝트인지 확인한다. 기존 테이블이 있다면 Flyway 이력을 먼저 확인한다.
2. Data API integration 설정에서 `Enable Data API`를 끈다. 이 구성은 Java 서버의 JDBC 연결만으로 테이블에 접근한다. 팀에서 REST·GraphQL Data API를 별도로 쓰는 프로젝트에는 이 설정을 그대로 적용하지 않는다.
3. 프로젝트의 `Connect`에서 `Session pooler` 접속 정보를 확인한다. IPv4 연결을 사용하며 포트는 5432다. 사용자명은 화면에 표시된 값을 그대로 사용한다.
4. Database Settings의 SSL Configuration에서 CA 인증서를 내려받고, EC2의 `/etc/hirelink/supabase-ca.crt`에 둔다. 인증서 파일은 백엔드 컨테이너 사용자가 읽을 수 있어야 한다.

앱 시작 시 Flyway가 기존 마이그레이션을 순서대로 적용하고, JPA가 `ddl-auto=validate`로 확인한다.
배포를 위해 기존 SQL을 수정하거나 `baseline-on-migrate`, `ddl-auto=update`를 사용하지 않는다.

## 서버 설정 파일

다음 파일은 EC2에서만 관리하며 저장소에 넣지 않는다.

| 경로 | 내용 | 권한 |
| --- | --- | --- |
| `/etc/hirelink/deploy.env` | 공개 도메인, 배포 이미지 태그 | 소유자 root, 600 |
| `/etc/hirelink/backend.env` | DB 접속 정보와 서버용 인증키 | 소유자 root, 600 |
| `/etc/hirelink/supabase-ca.crt` | Supabase에서 내려받은 공개 CA 인증서 | 소유자 root, 644 |

디렉터리는 `sudo install -d -m 700 /etc/hirelink`로 생성한다.
설정 파일은 새로 만들 때만 생성하고, 이미 있는 파일을 빈 파일로 덮어쓰지 않는다.
파일 내용은 `sudo nano` 등 서버 편집기로 입력한다.

`deploy.env` 형식:

```dotenv
API_DOMAIN=api.example.com
IMAGE_TAG=배포할-커밋의-짧은-SHA
```

`backend.env` 형식:

```dotenv
DB_URL=jdbc:postgresql://SESSION_POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=/app/certs/supabase-ca.crt
DB_USERNAME=CONNECT_화면의_사용자명
DB_PASSWORD=DB_비밀번호
JWT_SECRET=BASE64_인증키
SOLAPI_API_KEY=문자_API_KEY
SOLAPI_API_SECRET=문자_API_SECRET
SOLAPI_SENDER_NUMBER=등록된_발신번호
NTS_SERVICE_KEY=공공데이터포털_디코딩_인증키
```

위 값은 형식 설명용이다. 실제 값에는 따옴표를 덧붙이지 않고 `이름=값`으로 입력한다.
백엔드 설정은 Compose의 `raw` 형식으로 읽으므로 비밀번호의 `$` 같은 문자를 그대로 전달한다.
이 파일을 `source`로 실행하거나 Compose의 `--env-file` 인자로 사용하지 않는다.

JWT 인증키는 최소 32바이트의 난수를 Base64로 인코딩한다.
최초 생성 시 서버에서 난수를 만들고 파일에 직접 기록하며, 기존 키를 배포할 때마다 바꾸지 않는다.
문자·국세청 설정이 없으면 관련 요청은 실패하므로 실제 기능 확인 전에 설정한다.
SOLAPI 허용 IP에는 EC2의 탄력적 IP를 등록한다.

프로필 사진이 없는 회원은 Storage 설정 없이 조회할 수 있다.
사진을 사용하는 환경에는 `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `SUPABASE_STORAGE_BUCKET`도 설정한다.
등록증 원본을 저장하는 버킷은 사용하지 않는다.

## 이미지 빌드와 실행

코드는 승인된 변경이 병합된 `main`에서 가져온다.

```bash
git clone --branch main https://github.com/HIRELINK-Team/HIRELINK-Backend.git
cd HIRELINK-Backend
git rev-parse --short HEAD
```

확인한 SHA를 `deploy.env`의 `IMAGE_TAG`와 맞춘다.
빌드 메모리 사용이 겹치지 않도록 두 이미지를 순서대로 빌드한다.

```bash
sudo docker compose --env-file /etc/hirelink/deploy.env -f compose.deploy.yaml config --quiet
sudo docker compose --env-file /etc/hirelink/deploy.env -f compose.deploy.yaml build backend
sudo docker compose --env-file /etc/hirelink/deploy.env -f compose.deploy.yaml build ocr
sudo docker compose --env-file /etc/hirelink/deploy.env -f compose.deploy.yaml up -d --no-build
sudo docker compose --env-file /etc/hirelink/deploy.env -f compose.deploy.yaml ps
```

이미지 빌드에는 실제 DB나 인증키를 전달하지 않는다. `backend.env`는 실행 시에만 주입된다.
Caddy는 백엔드의 healthcheck 통과 후 시작하며, 도메인과 80·443 연결이 준비되면 HTTPS 인증서를 자동으로 발급·갱신한다.
인증서 데이터는 `caddy-data`, `caddy-config` 볼륨에 유지한다.
백엔드 기동은 OCR 기동 성공 여부에 의존하지 않는다. OCR 오류는 해당 API 요청에서 처리한다.

## 실행 확인

실제 도메인으로 다음 요청을 확인한다.

```bash
curl --fail --silent --show-error https://api.example.com/health
```

정상 응답은 `{"status":"UP"}`이다. 이 경로는 Java 서버 응답 여부만 확인한다.
Supabase의 `flyway_schema_history`에서 마이그레이션 성공 여부도 확인한다.
OCR, 문자 발송, 국세청 조회는 각각 실제 요청으로 별도 확인한다.
컨테이너 사용량은 `sudo docker stats --no-stream`으로 확인한다.
현재 메모리 제한은 백엔드 768MB, OCR 2GB, Caddy 128MB이며 실제 부하를 보고 조정한다.
백엔드와 OCR 컨테이너는 메모리의 디스크 스왑을 허용하지 않는다.

설정 점검 시 `docker compose config`는 비밀값을 출력할 수 있으므로 `config --quiet`를 사용한다.
`docker inspect`, `env`, 설정 파일 내용, 실제 요청 데이터가 포함된 로그를 공개 이슈나 PR에 첨부하지 않는다.

## 프론트 연결

Vercel 프론트의 `/api/*`를 백엔드 HTTPS 주소로 전달한다.
프론트의 기존 `vercel.json`에 다음 설정을 병합하며, API rewrite를 SPA rewrite보다 먼저 둔다.

```json
{
  "rewrites": [
    { "source": "/api/:path*", "destination": "https://BACKEND_DOMAIN/api/:path*" },
    { "source": "/(.*)", "destination": "/index.html" }
  ],
  "headers": [
    {
      "source": "/api/:path*",
      "headers": [
        { "key": "Cache-Control", "value": "no-store" },
        { "key": "x-vercel-enable-rewrite-caching", "value": "0" }
      ]
    }
  ]
}
```

프론트는 같은 출처의 `/api/...`를 호출한다.
기존 `Secure`, `HttpOnly`, `SameSite=Lax` 쿠키와 CSRF 검사를 유지한다.
`GET /api/auth/csrf`의 `headerName`, `token`을 재발급·로그아웃 요청 헤더에 사용한다.
일반 인증 API에는 `Authorization: Bearer ...`를 전달한다.
브라우저에서 로그인 → 재발급 → 로그아웃 → 재발급 실패까지 확인한다.

## 재배포와 중지

재배포할 때는 `git pull --ff-only` 후 배포할 SHA로 `IMAGE_TAG`를 바꾸고 빌드·실행 명령을 반복한다.
DB 마이그레이션이 적용된 배포는 이전 이미지로 바꾸기 전에 스키마 호환성을 확인한다.
장애 복구를 위해 직전 이미지와 Caddy 인증서 볼륨을 유지한다.

EC2를 중지하면 프론트의 API 요청도 실패한다. 팀이 정한 서비스 운영 시간에 맞춰 중지한다.
인스턴스를 다시 시작하면 Docker 서비스와 `unless-stopped` 컨테이너가 자동 실행된다.
수동으로 Compose를 중지했다면 다시 `up -d --no-build`로 시작한다.
중지 중에도 EBS와 보유한 탄력적 IP에는 비용이 계산된다.

## 공식 문서

- [Docker Ubuntu 설치](https://docs.docker.com/engine/install/ubuntu/)
- [Caddy Docker Compose와 HTTPS](https://caddyserver.com/docs/running#docker-compose)
- [Supabase PostgreSQL 연결](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase SSL](https://supabase.com/docs/guides/platform/ssl-enforcement)
- [Supabase Data API 보안](https://supabase.com/docs/guides/api/securing-your-api)
