set -euo pipefail
OBJECT="__OBJECT__"
/opt/buildit-wiki/venv/bin/python --version
/opt/buildit-wiki/venv/bin/python -m pip --version
aws s3 cp "$OBJECT" /tmp/csat-day1-seed.tgz
rm -rf /tmp/csat-seed && mkdir -p /tmp/csat-seed
tar -xzf /tmp/csat-day1-seed.tgz -C /tmp/csat-seed
# venv에는 httpx가 없다 — 사이트 패키지를 건드리지 않고 임시 디렉터리에만 설치한다.
/opt/buildit-wiki/venv/bin/python -m pip install --quiet --no-warn-script-location --target /tmp/csat-seed/pylibs httpx
export PYTHONPATH=/tmp/csat-seed/pylibs
set -a; . /etc/buildit-wiki-identity.env; set +a
/opt/buildit-wiki/venv/bin/python -c "import httpx; print('httpx', httpx.__version__)"
/opt/buildit-wiki/venv/bin/python /tmp/csat-seed/prod_seed_runner.py \
  --root /tmp/csat-seed/wiki-backend-src \
  --base http://127.0.0.1:8000 \
  --sources /tmp/csat-seed/sources
rm -rf /tmp/csat-seed /tmp/csat-day1-seed.tgz
echo SEED_RUNNER_DONE
