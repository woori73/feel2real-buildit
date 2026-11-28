import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import buildWeb from '../scripts/build-web.cjs';

const root = fileURLToPath(new URL('..', import.meta.url));
function makeBuild(t, origin) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'codi-launch-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  cpSync(path.join(root, 'www'), path.join(dir, 'www'), { recursive: true });
  cpSync(path.join(root, 'capacitor.config.js'), path.join(dir, 'capacitor.config.js'));
  buildWeb.build(dir, origin);
  const config = createRequire(import.meta.url)(path.join(dir, 'capacitor.config.js'));
  const script = ['entry.js', 'launch.js'].map(name => readFileSync(path.join(dir, config.webDir, name), 'utf8')).join('\n');
  return { config, script };
}
function openPage(script, retryButton) {
  const navigations = [];
  runInNewContext(script, {
    document: { getElementById: () => retryButton },
    window: { location: { replace: url => navigations.push(url) } }
  });
  return navigations;
}

test('production and pilot builds use the same destination for entry, retry and the native allowlist', t => {
  for (const origin of [undefined, 'https://pilot.example.test:8443']) {
    const { config, script } = makeBuild(t, origin);
    const entry = `${origin ?? 'https://feel2real.co.kr'}/codi`;
    assert.deepEqual(openPage(script, null), [entry]);
    assert.deepEqual(config.server.allowNavigation, [new URL(entry).hostname]);
    const handlers = {};
    const visits = openPage(script, { addEventListener: (event, handler) => { handlers[event] = handler; } });
    assert.equal(visits.length, 0);
    handlers.click();
    assert.deepEqual(visits, [entry]);
  }
});

test('build rejects cleartext URLs, paths, credentials, query parameters and fragments', () => {
  for (const value of ['http://pilot.test','https://pilot.test/chat','https://a:b@pilot.test','https://pilot.test/?a=1','https://pilot.test/#demo']) {
    assert.throws(() => buildWeb.resolveOrigin(value));
  }
});
