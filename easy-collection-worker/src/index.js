/**
 * EASY 자동수집 Playwright Worker — 메인 서버.
 *
 * BuildIt과 분리된 Node.js 프로세스로 실행된다.
 * 인증정보는 Worker 메모리에만 보관하고 AI 모델에 전달하지 않는다.
 *
 * API:
 *   POST /jobs — 다운로드 작업 요청 { jobId, siteType, targetUrl, downloadSelector }
 *   GET  /jobs/:jobId — 작업 상태 조회
 *   GET  /health — 헬스 체크
 *
 * 인증: Authorization: Bearer <WORKER_AUTH_TOKEN>
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { chromium } = require('playwright');
const config = require('./config');
const { isUrlAllowed } = require('./url-guard');

const jobs = new Map(); // jobId -> { status, ... }

function sendJson(res, statusCode, obj) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

function checkAuth(req) {
  if (!config.authToken) {
    return false;
  }
  const header = req.headers['authorization'] || '';
  return header === 'Bearer ' + config.authToken;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', chunk => {
      data += chunk;
      if (data.length > 1024 * 1024) {
        reject(new Error('요청 본문이 너무 큽니다.'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

async function runDownloadJob(job) {
  const jobTempDir = path.join(config.tempDir, job.jobId);
  let browser = null;
  try {
    job.status = 'RUNNING';
    job.startedAt = new Date().toISOString();

    // URL 검증
    const urlCheck = isUrlAllowed(job.targetUrl, config.allowedUrlPatterns);
    if (!urlCheck.allowed) {
      throw new Error(urlCheck.reason);
    }

    fs.mkdirSync(jobTempDir, { recursive: true });

    // 브라우저 실행 (작업별 독립 프로세스)
    browser = await chromium.launch({
      headless: true,
      timeout: config.browserLaunchTimeout,
    });

    // 세션 격리: 작업별 독립 컨텍스트
    const context = await browser.newContext({ acceptDownloads: true });
    const page = await context.newPage();

    try {
      // 페이지 이동 (timeout 적용)
      await page.goto(job.targetUrl, { timeout: config.pageNavigationTimeout });

      // 다운로드 트리거
      const [download] = await Promise.all([
        page.waitForEvent('download', { timeout: config.downloadTimeout }),
        page.click(job.downloadSelector, { timeout: 10000 }),
      ]);

      // 파일 검증
      const filename = download.suggestedFilename();
      const ext = path.extname(filename).toLowerCase();
      if (!config.allowedExtensions.includes(ext)) {
        throw new Error('허용되지 않은 파일 형식입니다: ' + ext);
      }

      const tempPath = path.join(jobTempDir, filename);
      await download.saveAs(tempPath);

      const stat = fs.statSync(tempPath);
      if (stat.size > config.maxFileSizeBytes) {
        fs.unlinkSync(tempPath);
        throw new Error('파일 크기 제한 초과: ' + stat.size + ' bytes');
      }
      if (stat.size === 0) {
        throw new Error('빈 파일이 다운로드되었습니다.');
      }

      // 파일 해시 계산 (중복 검사용)
      const hash = crypto.createHash('sha256')
        .update(fs.readFileSync(tempPath))
        .digest('hex');

      job.status = 'COMPLETED';
      job.completedAt = new Date().toISOString();
      job.result = {
        filename,
        fileSize: stat.size,
        sha256: hash,
        tempPath, // BuildIt이 수집 후 삭제
      };
    } finally {
      await context.close();
    }
  } catch (error) {
    job.status = 'FAILED';
    job.completedAt = new Date().toISOString();
    job.error = error.message;
    // 실패 시 임시파일 정리
    cleanupJobDir(job.jobId);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
}

function cleanupJobDir(jobId) {
  const dir = path.join(config.tempDir, jobId);
  try {
    fs.rmSync(dir, { recursive: true, force: true });
  } catch {
    // 정리 실패는 무시 (로그만)
  }
}

const server = http.createServer(async (req, res) => {
  try {
    // 헬스 체크는 인증 제외
    if (req.method === 'GET' && req.url === '/health') {
      return sendJson(res, 200, { status: 'ok', version: '1.0.0' });
    }

    // 인증 검사
    if (!checkAuth(req)) {
      return sendJson(res, 401, { error: '인증이 필요합니다.' });
    }

    if (req.method === 'POST' && req.url === '/jobs') {
      const body = JSON.parse(await readBody(req));
      const { siteType, targetUrl, downloadSelector } = body;

      if (!siteType || !targetUrl || !downloadSelector) {
        return sendJson(res, 400, { error: 'siteType, targetUrl, downloadSelector가 필요합니다.' });
      }

      // URL 사전 검증
      const urlCheck = isUrlAllowed(targetUrl, config.allowedUrlPatterns);
      if (!urlCheck.allowed) {
        return sendJson(res, 403, { error: urlCheck.reason });
      }

      const jobId = 'job_' + crypto.randomBytes(8).toString('hex');
      const job = {
        jobId,
        siteType,
        targetUrl,
        downloadSelector,
        status: 'PENDING',
        createdAt: new Date().toISOString(),
      };
      jobs.set(jobId, job);

      // 비동기로 실행 (jobTimeout 적용)
      const timeoutId = setTimeout(() => {
        const j = jobs.get(jobId);
        if (j && (j.status === 'PENDING' || j.status === 'RUNNING')) {
          j.status = 'FAILED';
          j.error = '작업 시간 초과';
          cleanupJobDir(jobId);
        }
      }, config.jobTimeout);

      runDownloadJob(job).finally(() => clearTimeout(timeoutId));

      return sendJson(res, 202, { jobId, status: 'PENDING' });
    }

    const jobMatch = req.url.match(/^\/jobs\/([a-zA-Z0-9_]+)$/);
    if (req.method === 'GET' && jobMatch) {
      const job = jobs.get(jobMatch[1]);
      if (!job) {
        return sendJson(res, 404, { error: '작업을 찾을 수 없습니다.' });
      }
      // tempPath는 내부용이므로 응답에서 제외 (파일 내용은 별도 전달)
      const { tempPath, ...safeResult } = job.result || {};
      return sendJson(res, 200, { ...job, result: job.result ? { ...job.result, tempPath: undefined } : undefined });
    }

    return sendJson(res, 404, { error: '찾을 수 없습니다.' });
  } catch (error) {
    return sendJson(res, 500, { error: '서버 오류가 발생했습니다.' });
  }
});

if (require.main === module) {
  if (!config.authToken) {
    console.error('WORKER_AUTH_TOKEN 환경변수가 필요합니다.');
    process.exit(1);
  }
  fs.mkdirSync(config.tempDir, { recursive: true });
  server.listen(config.port, '127.0.0.1', () => {
    console.log(`Worker listening on 127.0.0.1:${config.port}`);
  });
}

module.exports = { server, jobs, runDownloadJob, cleanupJobDir };
