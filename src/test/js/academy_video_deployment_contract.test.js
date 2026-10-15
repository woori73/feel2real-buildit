const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');
const app = read('../../main/resources/application.yaml');
const production = read('../../main/resources/config/application-prd.yaml');
const index = read('../../../docs/INDEX.md');
const workflow = read('../../../docs/WORKFLOW.md');
const runbook = read('../../../docs/AWS_DEPLOYMENT.md');

test('server multipart video product invariants remain explicit in application configuration', () => {
  for (const expected of [
    'max-duration-sec: 120', 'max-upload-bytes: 209715200',
    'max-output-bytes: 209715200', 'max-count: 30', 'upload-max-count: 30',
    // preload 로 받은 signed URL / 302 가 방문자의 첫 클릭까지 살아 있어야 콜드 재연결이 없다(§9~11).
    'delivery-signed-url-ttl-ms: 900000'
  ]) assert.ok(app.includes(expected), `missing config: ${expected}`);
  assert.match(production, /max-file-size: 200MB/);
  assert.match(production, /max-request-size: 205MB/);
});

test('production temp work is explicitly routed away from an implicit tmpfs', () => {
  assert.match(production, /validation-temp-dir: \$\{ACADEMY_VIDEO_VALIDATION_TEMP_DIR:\/var\/tmp\/f2r/);
  assert.match(production, /optimization-temp-dir: \$\{ACADEMY_VIDEO_OPTIMIZATION_TEMP_DIR:\/var\/tmp\/f2r/);
  assert.match(production, /APP_VIDEO_FFMPEG_PATH:\/usr\/local\/bin\/ffmpeg/);
  assert.match(production, /APP_VIDEO_FFPROBE_PATH:\/usr\/local\/bin\/ffprobe/);
});

test('canonical runbook is linked and covers every deployment stop gate', () => {
  assert.match(index, /\[AWS_DEPLOYMENT\.md\]\(AWS_DEPLOYMENT\.md\)/);
  assert.match(workflow, /단일 실행 문서.*\[AWS_DEPLOYMENT\.md\]/);
  for (const gate of [
    'IAM', 'CORS', 'ETag', 'AbortIncompleteMultipartUpload', 'idle_timeout.timeout_seconds',
    '/var/tmp/f2r', 'libx264', 'Actuator', 'Chrome', 'Content-Range', 'dev', 'stg'
  ]) assert.ok(runbook.includes(gate), `runbook missing gate: ${gate}`);
});

test('runbook preserves server-upload security and operational boundaries', () => {
  assert.match(runbook, /Block Public Access 전체 ON/);
  assert.match(runbook, /signed URL.*저장.*않는다/);
  assert.match(runbook, /ALB timeout은 연결 \*\*무활동\*\* 제한/);
  assert.match(runbook, /Spring `spring\.servlet\.multipart\.\*`.*Academy 영상.*200MB\/request 205MB/s);
  assert.match(runbook, /POST \/api\/tutor\/academy\/videos.*multipart 요청 하나만/s);
  assert.match(runbook, /academy-video\/attempts\/.*active raw\/normalized.*시간만으로 삭제.*금지/s);
});
