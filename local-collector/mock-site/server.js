// Safe, non-sensitive mock "financial site" used ONLY to prove the Visible Local Browser
// Collector lifecycle end-to-end (Section 10 of the spec). No real card/bank/CREFIA/Hometax site
// is touched by this file. Zero third-party dependencies (Node's built-in http/fs only).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORT = process.env.MOCK_SITE_PORT ? Number(process.env.MOCK_SITE_PORT) : 4173;

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.csv': 'text/csv; charset=utf-8',
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  let requested = url.pathname === '/' ? '/index.html' : url.pathname;

  // Force-download so Playwright's `page.waitForEvent('download')` fires, exactly like a real
  // "이용내역 다운로드" button would on a real card/bank site.
  if (requested === '/sample-download.csv') {
    const filePath = path.join(PUBLIC_DIR, 'sample-download.csv');
    const data = fs.readFileSync(filePath);
    res.writeHead(200, {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': 'attachment; filename="card-usage-mock.csv"',
    });
    res.end(data);
    return;
  }

  // KB PoC mock Excel download: stable fake bytes (NOT a real statement, never parsed as one).
  // The Java-side KbCard* pipeline is proven separately against the real 14-column schema.
  if (requested === '/kb-download.xls') {
    const data = Buffer.from(
        'KB PoC mock download — fake bytes for collector lifecycle verification only.\n'
        + 'Not a real card statement. Never ingested as financial data.\n', 'utf-8');
    res.writeHead(200, {
      'Content-Type': 'application/vnd.ms-excel',
      'Content-Disposition': 'attachment; filename="kb-usage-mock.xls"',
    });
    res.end(data);
    return;
  }

  const filePath = path.join(PUBLIC_DIR, requested);
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(400);
    res.end('bad path');
    return;
  }
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end('not found');
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { 'Content-Type': CONTENT_TYPES[ext] || 'application/octet-stream' });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log(`[mock-site] listening on http://localhost:${PORT}`);
});
