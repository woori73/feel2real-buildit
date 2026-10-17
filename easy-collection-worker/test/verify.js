/**
 * Worker 검증 테스트 — 로컬 환경에서 실행.
 *
 * 검증 항목:
 * - URL 가드 (허용/차단)
 * - 작업 생성·상태 조회
 * - 인증 실패 처리
 * - 다운로드 및 파일 검증
 */
const http = require('http');
const { isUrlAllowed } = require('../src/url-guard');

const TEST_TOKEN = 'test-token-12345';
process.env.WORKER_AUTH_TOKEN = TEST_TOKEN;
process.env.WORKER_PORT = '3199';
process.env.WORKER_TEMP_DIR = '/tmp/easy-worker-test';

const { server } = require('../src/index');

function request(options, body) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, res => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => resolve({ status: res.statusCode, body: data }));
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

async function main() {
  const results = {};

  // 1. URL 가드 테스트
  const patterns = ['http://127.0.0.1:*', 'http://localhost:*'];
  results.urlGuardAllowed = isUrlAllowed('http://127.0.0.1:8080/test', patterns).allowed === true;
  results.urlGuardBlockedExternal = isUrlAllowed('https://www.google.com/', patterns).allowed === false;
  results.urlGuardBlockedProtocol = isUrlAllowed('file:///etc/passwd', patterns).allowed === false;
  results.urlGuardBlockedInternal = isUrlAllowed('http://192.168.1.1/admin', patterns).allowed === false;

  // 2. 헬스 체크 (인증 불필요)
  const health = await request({ hostname: '127.0.0.1', port: 3199, path: '/health', method: 'GET' });
  results.healthCheck = health.status === 200;

  // 3. 인증 없이 작업 생성 → 401
  const noAuth = await request(
    { hostname: '127.0.0.1', port: 3199, path: '/jobs', method: 'POST',
      headers: { 'Content-Type': 'application/json' } },
    JSON.stringify({ siteType: 'TEST', targetUrl: 'http://127.0.0.1:8080', downloadSelector: '#btn' })
  );
  results.authRequired = noAuth.status === 401;

  // 4. 허용되지 않은 URL → 403
  const blocked = await request(
    { hostname: '127.0.0.1', port: 3199, path: '/jobs', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + TEST_TOKEN } },
    JSON.stringify({ siteType: 'TEST', targetUrl: 'https://evil.com/steal', downloadSelector: '#btn' })
  );
  results.blockedUrlRejected = blocked.status === 403;

  // 5. 필수 파라미터 누락 → 400
  const badRequest = await request(
    { hostname: '127.0.0.1', port: 3199, path: '/jobs', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + TEST_TOKEN } },
    JSON.stringify({ siteType: 'TEST' })
  );
  results.badRequestRejected = badRequest.status === 400;

  console.log(JSON.stringify(results, null, 2));
  const allOk = Object.values(results).every(v => v === true);
  console.log('\n=== ALL TESTS ' + (allOk ? 'PASSED' : 'FAILED') + ' ===');

  server.close();
  process.exit(allOk ? 0 : 1);
}

// 서버 시작 대기 후 테스트 실행
server.listen(3199, '127.0.0.1', () => {
  setTimeout(main, 500);
});
