#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf '[FAIL] %s: %s\n' "$1" "$2" >&2
  exit 1
}

start() {
  printf '[START] %s\n' "$1"
}

success() {
  printf '[SUCCESS] %s\n' "$1"
}

[[ $# -eq 1 ]] || fail "INPUT" "A base URL is required."
base_url=${1%/}
[[ $base_url == https://* || $base_url == http://* ]] \
  || fail "INPUT" "The base URL must use HTTP or HTTPS."

work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

request_json() {
  local step=$1
  local url=$2
  local output_file=$3
  local http_status

  start "$step HTTP request"
  if ! http_status=$(curl --silent --show-error --location --max-time 10 \
    --retry 3 --retry-delay 3 --output "$output_file" --write-out '%{http_code}' "$url"); then
    fail "$step HTTP request" "The request could not be completed."
  fi
  [[ $http_status =~ ^2[0-9]{2}$ ]] \
    || fail "$step HTTP status" "Expected 2xx but received $http_status."
  success "$step HTTP status ($http_status)"
}

validate_json() {
  local step=$1
  local input_file=$2

  start "$step JSON validation"
  python3 - "$input_file" <<'PY' >/dev/null 2>&1 \
    || fail "$step JSON validation" "The response is not valid JSON."
import json
import sys
from pathlib import Path

json.loads(Path(sys.argv[1]).read_text())
PY
  success "$step JSON validation"
}

health_body=$work_dir/health.json
request_json "HEALTH" "$base_url/health" "$health_body"
validate_json "HEALTH" "$health_body"

start "HEALTH status validation"
health_status=$(python3 - "$health_body" <<'PY'
import json
import sys
from pathlib import Path

document = json.loads(Path(sys.argv[1]).read_text())
status = document.get("status") if isinstance(document, dict) else None
print(status if isinstance(status, str) else "")
PY
)
[[ $health_status == UP ]] \
  || fail "HEALTH status validation" "Expected status UP but received ${health_status:-missing}."
success "HEALTH status validation (UP)"

openapi_body=$work_dir/openapi.json
request_json "OPENAPI" "$base_url/v3/api-docs" "$openapi_body"
validate_json "OPENAPI" "$openapi_body"

start "OPENAPI core path validation"
if ! missing_paths=$(python3 - "$openapi_body" <<'PY'
import json
import sys
from pathlib import Path

required_paths = (
    "/api/job-postings/{jobPostingId}",
    "/api/recommendations",
    "/api/applications",
)
document = json.loads(Path(sys.argv[1]).read_text())
paths = document.get("paths") if isinstance(document, dict) else None
if not isinstance(paths, dict):
    print("paths")
    raise SystemExit(1)
missing = [path for path in required_paths if path not in paths]
if missing:
    print(", ".join(missing))
    raise SystemExit(1)
PY
); then
  fail "OPENAPI core path validation" "Missing required path(s): ${missing_paths:-paths}."
fi
success "OPENAPI core paths: /api/job-postings/{jobPostingId}, /api/recommendations, /api/applications"
printf '%s\n' "SMOKE_VALIDATION_SUCCEEDED"
