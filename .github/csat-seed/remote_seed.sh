set -euo pipefail
OBJECT="__OBJECT__"
/opt/buildit-wiki/venv/bin/python -c "import httpx,sys; print('httpx', httpx.__version__)"
aws s3 cp "$OBJECT" /tmp/csat-day1-seed.tgz
rm -rf /tmp/csat-seed && mkdir -p /tmp/csat-seed
tar -xzf /tmp/csat-day1-seed.tgz -C /tmp/csat-seed
set -a; . /etc/buildit-wiki-identity.env; set +a
/opt/buildit-wiki/venv/bin/python /tmp/csat-seed/prod_seed_runner.py \
  --root /tmp/csat-seed/wiki-backend-src \
  --base http://127.0.0.1:8000 \
  --sources /tmp/csat-seed/sources
rm -rf /tmp/csat-seed /tmp/csat-day1-seed.tgz
echo SEED_RUNNER_DONE
