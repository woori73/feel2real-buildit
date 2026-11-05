#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/f2r_luna_env.sh"
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
printf 'UNCHANGED=keep\nOPENAI_API_KEY=old\nNEWSTART_LLM_PRIMARY=meta\nAPP_ADMISSIONS_LLM_PRIMARY=meta\n' > "$test_dir/env"
printf 'sk-test_only_no_real_secret' > "$test_dir/key"
f2r_counseling_model_apply luna "$test_dir/env" "$test_dir/key"
grep -q '^UNCHANGED=keep$' "$test_dir/env"
test "$(grep -c '^OPENAI_API_KEY=' "$test_dir/env")" = 1
grep -q '^APP_ADMISSIONS_LLM_PRIMARY=openai$' "$test_dir/env"
grep -q '^NEWSTART_LLM_PRIMARY=openai$' "$test_dir/env"
grep -q '^SITEWIDE_LUNA_ENABLED=true$' "$test_dir/env"
test "$(stat -c %a "$test_dir/env")" = 600
cp "$test_dir/env" "$test_dir/before"
printf 'bad key\nINJECTED=true' > "$test_dir/key"
if f2r_counseling_model_apply luna "$test_dir/env" "$test_dir/key"; then exit 1; fi
cmp "$test_dir/env" "$test_dir/before"
f2r_counseling_model_apply unchanged "$test_dir/env"
cmp "$test_dir/env" "$test_dir/before"
f2r_counseling_model_apply meta "$test_dir/env"
grep -q '^NEWSTART_LLM_PRIMARY=meta$' "$test_dir/env"
grep -q '^APP_ADMISSIONS_LLM_PRIMARY=meta$' "$test_dir/env"
grep -q '^SITEWIDE_LUNA_ENABLED=false$' "$test_dir/env"
printf 'sk-wiki_test_only' > "$test_dir/key"
: > "$test_dir/wiki.env"
f2r_counseling_model_apply luna "$test_dir/wiki.env" "$test_dir/key"
grep -q '^SITEWIDE_LUNA_ENABLED=true$' "$test_dir/wiki.env"
grep -q '^OPENAI_API_KEY=sk-wiki_test_only$' "$test_dir/wiki.env"
echo 'Luna env tests PASS (activation, rollback, permissions, invalid-key atomicity)'
