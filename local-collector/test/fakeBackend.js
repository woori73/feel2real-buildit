// TEST-ONLY fake EASY backend implementing exactly the collector-agent HTTP contract
// (/api/admin/easy/collector-agent/**) that src/backendClient.js and src/collector.js call.
// This proves the REAL, unmodified collector code drives a real visible browser correctly,
// independent of whether the Java backend happens to be reachable in this environment. The
// actual contract shape is separately verified by the Java-side tests
// (EasyCollectorTaskServiceTest, EasyCollectorAgentTokenFilterTest, EasySecurityWebMvcTest).
import http from 'node:http';

export const VALID_TOKEN = 'e2e-test-collector-token';

export function createFakeBackend({ port }) {
  const tasks = new Map();
  let nextTaskId = 1;

  function queueTask({ sourceCode, requestedFrom, requestedTo }) {
    const taskId = nextTaskId++;
    tasks.set(taskId, {
      taskId, sourceCode, requestedFrom, requestedTo,
      status: 'STARTING', downloadedFilename: null, downloadedSize: null, downloadedSha256: null,
    });
    return taskId;
  }

  function getTask(taskId) {
    return tasks.get(taskId);
  }

  function readBody(req) {
    return new Promise((resolve, reject) => {
      let data = '';
      req.on('data', (chunk) => { data += chunk; });
      req.on('end', () => resolve(data ? JSON.parse(data) : null));
      req.on('error', reject);
    });
  }

  function json(res, status, body) {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(body === undefined ? '' : JSON.stringify(body));
  }

  const server = http.createServer(async (req, res) => {
    const auth = req.headers['authorization'] || '';
    if (auth !== `Bearer ${VALID_TOKEN}`) {
      json(res, 401, { error: 'unauthorized' });
      return;
    }

    const url = new URL(req.url, `http://localhost:${port}`);
    const parts = url.pathname.split('/').filter(Boolean); // api admin easy collector-agent ...

    if (req.method === 'POST' && url.pathname === '/api/admin/easy/collector-agent/heartbeat') {
      res.writeHead(204); res.end(); return;
    }
    if (req.method === 'GET' && url.pathname === '/api/admin/easy/collector-agent/next-task') {
      const pending = [...tasks.values()].find((t) => t.status === 'STARTING');
      if (!pending) { res.writeHead(204); res.end(); return; }
      pending.status = 'BROWSER_OPEN';
      json(res, 200, pending);
      return;
    }
    // /api/admin/easy/collector-agent/tasks/{id}/{transition}
    if (parts[4] === 'tasks' && parts.length === 7) {
      const taskId = Number(parts[5]);
      const transition = parts[6];
      const task = tasks.get(taskId);
      if (!task) { json(res, 404, { error: 'not found' }); return; }
      const body = req.method === 'POST' ? await readBody(req) : null;
      switch (transition) {
        case 'waiting-for-auth': task.status = 'WAITING_FOR_USER_AUTH'; break;
        case 'resume': task.status = 'AUTHENTICATED'; break;
        case 'collecting': task.status = 'COLLECTING'; break;
        case 'downloading': task.status = 'DOWNLOADING'; break;
        case 'completed':
          task.status = 'COMPLETED';
          task.downloadedFilename = body.filename;
          task.downloadedSize = body.size;
          task.downloadedSha256 = body.sha256;
          break;
        case 'failed':
          task.status = 'FAILED';
          task.errorSummary = body ? body.reason : null;
          break;
        default: json(res, 404, { error: 'unknown transition' }); return;
      }
      json(res, 200, task);
      return;
    }
    json(res, 404, { error: 'not found' });
  });

  return new Promise((resolve) => {
    server.listen(port, '127.0.0.1', () => resolve({ server, queueTask, getTask }));
  });
}
