import { createServer } from 'node:http';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));

const outputDir = join(projectRoot, 'data', 'newstart-cafe', 'thanks-members', 'naver-id-prefix-2026-10-01');
const outputFile = join(outputDir, 'naver-id-prefix.json');

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
  if (req.method !== 'POST' || req.url !== '/capture-prefix') {
    res.writeHead(404).end();
    return;
  }

  try {
    let body = '';
    for await (const part of req) {
      body += part;
      if (body.length > 2_000_000) throw new Error('payload too large');
    }
    const payload = JSON.parse(body);
    const records = payload.records;
    if (!Array.isArray(records) || records.length === 0) {
      throw new Error('invalid prefix capture payload');
    }
    for (const r of records) {
      if (typeof r.cafeMemberTag !== 'string' || r.cafeMemberTag.length === 0
        || typeof r.naverIdPrefix !== 'string') {
        throw new Error('invalid prefix record');
      }
    }
    await mkdir(outputDir, { recursive: true });
    const doc = {
      snapshotDate: '2026-10-01',
      capturedAt: payload.capturedAt || new Date().toISOString(),
      sourceUrl: payload.sourceUrl || null,
      source: 'NAVER_CAFE_STUDIO',
      field: 'maskedMemberId',
      note: '네이버 로그인 ID는 Cafe Studio에서 마스킹되어 노출된다. '
        + 'naverIdPrefix 는 maskedMemberId 에서 후행 * 를 제거한, 화면에 실제 노출되는 접두부이다. '
        + '전체 네이버 ID가 아니며, 이 값만으로 THANKS_MEMBER 를 자동 부여하지 않는다.',
      total: records.length,
      records,
    };
    await writeFile(outputFile, `${JSON.stringify(doc, null, 2)}\n`, { flag: 'w' });
    res.writeHead(201, { 'Content-Type': 'application/json' })
      .end(JSON.stringify({ saved: outputFile, rows: records.length }));
    console.log(`saved ${outputFile}: ${records.length} rows`);
  } catch (error) {
    res.writeHead(400, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: error.message }));
    console.error(error.message);
  }
});

const port = Number.parseInt(process.env.PORT, 10) || 18888;
server.listen(port, '127.0.0.1', () => {
  console.log(`PREFIX_RECEIVER_READY http://127.0.0.1:${port}/capture-prefix`);
});
