import { createServer } from 'node:http';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));

const outputDir = join(projectRoot, 'data', 'newstart-cafe', 'thanks-members', 'roster-2026-10-01', 'raw');
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
  'Access-Control-Allow-Private-Network': 'true',
};

const server = createServer(async (req, res) => {
  for (const [key, value] of Object.entries(corsHeaders)) res.setHeader(key, value);
  if (req.method === 'OPTIONS') {
    res.writeHead(204).end();
    return;
  }
  if (req.method !== 'POST' || req.url !== '/capture') {
    res.writeHead(404).end();
    return;
  }

  try {
    let body = '';
    for await (const part of req) {
      body += part;
      if (body.length > 1_000_000) throw new Error('payload too large');
    }
    const batch = JSON.parse(body);
    if (!Number.isInteger(batch.page) || batch.page < 1 || !Number.isInteger(batch.chunk) || batch.chunk < 1 || !Array.isArray(batch.records) || batch.records.length === 0) {
      throw new Error('invalid capture batch');
    }
    await mkdir(outputDir, { recursive: true });
    const file = `page-${String(batch.page).padStart(2, '0')}-chunk-${String(batch.chunk).padStart(2, '0')}.json`;
    await writeFile(join(outputDir, file), `${JSON.stringify(batch, null, 2)}\n`, { flag: 'wx' });
    res.writeHead(201, { 'Content-Type': 'application/json' }).end(JSON.stringify({ saved: file, rows: batch.records.length }));
    console.log(`saved ${file}: ${batch.records.length} rows`);
  } catch (error) {
    res.writeHead(400, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: error.message }));
    console.error(error.message);
  }
});

const port = Number.parseInt(process.env.PORT, 10) || 18877;
server.listen(port, '127.0.0.1', () => {
  console.log(`CAPTURE_RECEIVER_READY http://127.0.0.1:${port}/capture`);
});
