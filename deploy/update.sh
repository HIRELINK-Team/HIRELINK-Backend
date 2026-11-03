#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

[[ $EUID -eq 0 ]] || fail "Run the deployment as root."
[[ $# -eq 1 && $1 =~ ^[0-9a-f]{40}$ ]] || fail "A full commit SHA is required."
release_sha=$1
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
deploy_env=/etc/hirelink/deploy.env
[[ -f "$deploy_env" ]] || fail "Deployment environment file is missing."
[[ $(runuser -u ubuntu -- git -C "$repo_dir" rev-parse HEAD) == "$release_sha" ]] \
  || fail "The checked-out revision does not match the release."

candidate_env=$(mktemp /etc/hirelink/deploy.XXXXXX)
trap 'rm -f -- "$candidate_env"' EXIT
python3 - "$deploy_env" "$candidate_env" "$release_sha" <<'PY'
import re
import sys
from pathlib import Path

source, target, sha = sys.argv[1:]
lines = Path(source).read_text().splitlines()
domains = [line.split("=", 1)[1] for line in lines if line.startswith("API_DOMAIN=")]
if len(domains) != 1 or not re.fullmatch(r"[a-zA-Z0-9.-]+", domains[0]):
    raise SystemExit("A single valid API_DOMAIN is required.")
updates = {
    "IMAGE_TAG": sha,
    "BACKEND_IMAGE": "ghcr.io/hirelink-team/hirelink-backend",
    "OCR_IMAGE": "ghcr.io/hirelink-team/hirelink-ocr",
}
lines = [line for line in lines if line.split("=", 1)[0] not in updates]
lines.extend(f"{key}={value}" for key, value in updates.items())
Path(target).write_text("\n".join(lines) + "\n")
PY

compose_env=$candidate_env
compose() {
  docker compose --env-file "$compose_env" -f "$repo_dir/compose.deploy.yaml" "$@"
}

compose config --quiet >/dev/null 2>&1 || fail "DEPLOY_CONFIG_INVALID"
printf '%s\n' "PULLING_RELEASE_IMAGES"
compose pull >/dev/null 2>&1 || fail "IMAGE_PULL_FAILED"
mv -f -- "$candidate_env" "$deploy_env"
compose_env=$deploy_env
printf '%s\n' "STARTING_RELEASE"
compose up -d --no-build --wait --wait-timeout 180 >/dev/null 2>&1 \
  || fail "SERVICE_HEALTH_CHECK_FAILED"
compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null 2>&1 \
  || fail "HTTPS_CONFIG_RELOAD_FAILED"

api_domain=$(python3 - "$deploy_env" <<'PY'
import sys
from pathlib import Path

for line in Path(sys.argv[1]).read_text().splitlines():
    if line.startswith("API_DOMAIN="):
        print(line.split("=", 1)[1])
        break
PY
)
curl --fail --silent --max-time 10 --retry 3 --retry-delay 3 \
  "https://$api_domain/health" >/dev/null 2>&1 || fail "HTTPS_HEALTH_CHECK_FAILED"
printf '%s\n' "DEPLOYMENT_SUCCEEDED"
