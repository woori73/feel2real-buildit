#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# DB-free harnesses, MVC authorization, parser/crypto, existing accounting regression tests only.
bash gradlew test \
  --tests '*EasyFinancial*Test' \
  --tests '*EasyCollectionIngestIdempotencyTest' \
  --tests '*EasyCollectionApprovalTest' \
  --tests '*EasySecurityWebMvcTest' \
  --tests '*EasyLedgerServiceTest' \
  --tests '*EasyJournalServiceTest' \
  --tests '*ShinhanCard*Test'
npm --prefix local-collector test
# Run headed Playwright separately under a desktop or xvfb-run:
# xvfb-run -a npm --prefix local-collector run test:e2e-mock
