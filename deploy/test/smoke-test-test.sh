#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
smoke_script=$repo_dir/deploy/smoke-test.sh
server_script=$repo_dir/deploy/test/smoke_server.py
test_dir=$(mktemp -d)
server_pid=

cleanup() {
  if [[ -n $server_pid ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -rf -- "$test_dir"
}
trap cleanup EXIT

bash -n "$smoke_script"
bash -n "$repo_dir/deploy/update.sh"
bash -n "$repo_dir/deploy/test/smoke-test-test.sh"

port_file=$test_dir/port
python3 "$server_script" "$port_file" &
server_pid=$!
for _ in {1..100}; do
  [[ -s $port_file ]] && break
  kill -0 "$server_pid" 2>/dev/null || {
    printf '%s\n' "Smoke fixture server stopped unexpectedly." >&2
    exit 1
  }
  sleep 0.05
done
[[ -s $port_file ]] || {
  printf '%s\n' "Smoke fixture server did not start." >&2
  exit 1
}
base_url=http://127.0.0.1:$(<"$port_file")

run_success_case() {
  local scenario=$1
  local output

  output=$(bash "$smoke_script" "$base_url/$scenario" 2>&1) || {
    printf 'Expected success for %s.\n%s\n' "$scenario" "$output" >&2
    exit 1
  }
  [[ $output == *SMOKE_VALIDATION_SUCCEEDED* ]] || {
    printf 'Success marker missing for %s.\n%s\n' "$scenario" "$output" >&2
    exit 1
  }
}

run_failure_case() {
  local scenario=$1
  local expected=$2
  local output
  local exit_code

  set +e
  output=$(bash "$smoke_script" "$base_url/$scenario" 2>&1)
  exit_code=$?
  set -e
  [[ $exit_code -ne 0 ]] || {
    printf 'Expected non-zero exit for %s.\n%s\n' "$scenario" "$output" >&2
    exit 1
  }
  [[ $output == *"$expected"* ]] || {
    printf 'Expected %s for %s.\n%s\n' "$expected" "$scenario" "$output" >&2
    exit 1
  }
}

run_success_case success
run_failure_case bad-http "Expected 2xx but received 503"
run_failure_case bad-health-json "HEALTH JSON validation"
run_failure_case down "Expected status UP but received DOWN"
run_failure_case bad-openapi-json "OPENAPI JSON validation"
run_failure_case missing-path "Missing required path(s)"

printf '%s\n' "All smoke validation tests passed."
