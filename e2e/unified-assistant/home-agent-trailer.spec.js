const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

// ONLY the two named, disposable local test databases are permitted. No AWS/remote DB credentials.
const container = process.env.TRAILER_TEST_DB_CONTAINER || 'unified-ai-mysql';
const configurations = {
  'unified-ai-mysql': ['f2r_unified_test', 'unified-local-only'],
  'f2r-policy-check': ['f2r_policy_test', 'policy-local-only']
};
const config = configurations[container];
const id = '8c2ef989-b739-4bf4-bcd7-b9582851e8e1';
const item = 'homepage-trailer-isolated-test';
const revision = '00000000-0000-4000-8000-000000009925';
const url = '/api/landing/videos/home-agent-trailer/stream';
// Final trailer supplied by the user on 2026-10-29 (preview_v16i_web.mp4); the homepage plays this bundled file.
const bundled = '/video/landing/ai-agent-trailer-v16i-20261029.mp4';
const bundledHash = '2c9fb23ce1a131f7946eec8ee10a8d38541c89186d9d49337b246bebf0b02a99';
const output = process.env.TRAILER_EVIDENCE_DIR || path.join(__dirname, '../../build/verification/home-trailer');
const fixture = fs.readFileSync(path.join(__dirname, 'fixtures/test-only.mp4'));
const hash = crypto.createHash('sha256').update(fixture).digest('hex');
let inserted = false;
let fixturePath;

function sql(statement) {
  if (!config) throw new Error('Only explicitly named local test containers are allowed');
  const env = { ...process.env };
  for (const key of ['DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH']) delete env[key];
  return execFileSync('docker', ['--host=unix:///var/run/docker.sock', 'exec', '-i', container,
    'mysql', '--default-character-set=utf8mb4', '-uroot', `-p${config[1]}`, '-N', '-B', config[0]],
  { input: statement, encoding: 'utf8', env, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
}

test.describe('isolated approved Studio trailer — synthetic MP4, never the production original', () => {
  test.beforeAll(async ({ baseURL }) => {
    if (baseURL !== 'http://localhost:8080') throw new Error('Trailer fixtures require the loopback application');
    // Refuse to overwrite ANY pre-existing file or test item, including from an interrupted run.
    expect(sql(`SELECT COUNT(*) FROM studio_files WHERE id='${id}';`)).toBe('0');
    expect(sql(`SELECT COUNT(*) FROM studio_items WHERE item_key='${item}';`)).toBe('0');
    fs.mkdirSync(output, { recursive: true });
    fixturePath = path.resolve(output, 'synthetic-trailer.mp4');
    fs.writeFileSync(fixturePath, fixture);
    const key = fixturePath.replaceAll("'", "''");
    sql(`START TRANSACTION;
      INSERT IGNORE INTO studio_projects(id,title) VALUES ('ai-agent-part1','Isolated test project');
      INSERT INTO studio_files(id,project_id,sha256,storage_key,filename,extension,size_bytes,uploaded_by)
        VALUES ('${id}','ai-agent-part1','${hash}','${key}','synthetic-trailer.mp4','mp4',${fixture.length},1);
      INSERT INTO studio_items(project_id,item_key,head_id,approved_id)
        VALUES ('ai-agent-part1','${item}','${revision}','${revision}');
      INSERT INTO studio_revisions(id,project_id,item_key,label,kind,origin,title,content,dialogue,direction,work_status,file_id,created_by)
        VALUES ('${revision}','ai-agent-part1','${item}','isolated-test','VIDEO','USER_EDIT','Synthetic trailer fixture','','','','READY','${id}',1);
      COMMIT;`);
    inserted = true;
  });

  test.afterAll(() => {
    if (inserted) sql(`START TRANSACTION;
      DELETE FROM studio_revisions WHERE id='${revision}' AND item_key='${item}';
      DELETE FROM studio_items WHERE project_id='ai-agent-part1' AND item_key='${item}';
      DELETE FROM studio_files WHERE id='${id}' AND sha256='${hash}'; COMMIT;`);
    if (fixturePath) fs.rmSync(fixturePath, { force: true });
  });

  test('public alias supports exact and ranged bytes while original Studio API stays private', async ({ request }) => {
    const counts = sql('SELECT COUNT(*) FROM studio_files; SELECT COUNT(*) FROM studio_events;');
    const response = await request.get(url);
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('video/mp4');
    expect(response.headers()['cache-control']).toBe('no-store');
    expect(crypto.createHash('sha256').update(await response.body()).digest('hex')).toBe(hash);
    const range = await request.get(url, { headers: { Range: 'bytes=0-31' } });
    expect(range.status()).toBe(206);
    expect(await range.body()).toEqual(fixture.subarray(0, 32));
    expect((await request.get(`${url}?fileId=other`)).status()).toBe(400);
    const privateResponse = await request.get(`/api/studio/files/${id}/stream?projectId=ai-agent-part1`, { maxRedirects: 0 });
    expect([302, 401, 403]).toContain(privateResponse.status());
    expect(sql('SELECT COUNT(*) FROM studio_files; SELECT COUNT(*) FROM studio_events;')).toBe(counts);
  });

  test('bundled final trailer is byte-identical and supports range requests', async ({ request }) => {
    const full = await request.get(bundled);
    expect(full.status()).toBe(200);
    expect(full.headers()['content-type']).toContain('video/mp4');
    expect(crypto.createHash('sha256').update(await full.body()).digest('hex')).toBe(bundledHash);
    const partial = await request.get(bundled, { headers: { Range: 'bytes=0-31' } });
    expect(partial.status()).toBe(206);
    expect((await partial.body()).length).toBe(32);
  });

  for (const width of [360, 390, 430, 1440]) {
    test(`manual trailer playback ${width}px with source-preserving fallback`, async ({ browser }) => {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, isMobile: width < 500, hasTouch: width < 500 });
      try {
        await context.route('**/*', route => {
          const u = new URL(route.request().url());
          if (u.origin !== 'http://localhost:8080') return route.abort();
          if (u.pathname.startsWith('/api/analytics/')) return route.fulfill({ status: 204 });
          if (u.pathname === '/api/agent-media/public') return route.fulfill({ json: { configured: false } });
          return route.continue();
        });
        const page = await context.newPage();
        await page.goto('/');
        const video = page.locator('#homeAgentTrailer');
        await expect(video).toHaveAttribute('src', bundled);
        await expect(page.locator('#homeAgentTrailerPending')).toHaveCount(0);
        expect(await video.evaluate(v => v.paused && !v.autoplay && v.controls && v.playsInline)).toBe(true);
        await page.locator('#homeAgentTrailerPlay').click();
        await expect.poll(() => video.evaluate(v => v.currentTime > 0 && v.readyState >= 2), { timeout: 5000 }).toBe(true);
        await page.locator('#homeAgentTrailerPlay').click();
        await expect.poll(() => video.evaluate(v => v.paused), { timeout: 5000 }).toBe(true);
        expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
        await page.locator('#ai-content').screenshot({ path: path.join(output, `${width}-final-trailer.png`) });
        await page.route(`**${bundled}`, route => route.fulfill({ status: 503 }));
        // Inject transport failure directly: load() may hide the play button before another click.
        await video.evaluate(v => { v.load(); v.play().catch(() => {}); });
        await expect(page.locator('#homeAgentTrailerStatus')).toContainText('불러오지 못했습니다');
        await expect(video).toBeHidden();
        await expect(page.locator('.en-hero-art')).toHaveCount(0);
      } finally { await context.close(); }
    });
  }

  test('approval revocation denies the Studio alias while the homepage keeps the bundled final trailer', async ({ request }) => {
    try {
      sql(`UPDATE studio_items SET approved_id=NULL WHERE project_id='ai-agent-part1' AND item_key='${item}';`);
      expect((await request.get(url)).status()).toBe(404);
      const html = await (await request.get('/')).text();
      expect(html).toContain(`src="${bundled}"`);
      expect(html).not.toContain(url);
      expect(html).not.toContain('id="homeAgentTrailerPending"');
    } finally {
      sql(`UPDATE studio_items SET approved_id='${revision}' WHERE project_id='ai-agent-part1' AND item_key='${item}';`);
    }
  });
});
