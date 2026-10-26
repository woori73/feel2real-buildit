const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/agent_studio.js'), 'utf8');
const template = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/studio/index.html'), 'utf8');
const catalog = require('../../main/resources/static/js/studio_catalog.js');

class Element {
  constructor(tagName = 'div') {
    this.tagName = tagName.toUpperCase();
    this.value = '';
    this.textContent = '';
    this.children = [];
    this.dataset = {};
    this.files = [];
    this.disabled = false;
    this.open = false;
    this.classList = { toggle() {} };
  }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  add(option) { this.append(option); }
  setAttribute(name, value) { this[name] = value; }
  removeAttribute(name) { delete this[name]; }
  addEventListener() {}
  scrollIntoView() {}
  click() {}
}

function harness(respond = () => Response.json([])) {
  const nodes = new Map([...template.matchAll(/\bid="([^"]+)"/g)].map(match => [match[1], new Element()]));
  for (const [id, node] of nodes) node.id = id;
  nodes.get('files').dataset.owner = 'materials';
  nodes.get('revision-form').elements = { fileId: new Element('select') };
  const calls = [];
  const document = {
    getElementById: id => nodes.get(id) || null,
    querySelectorAll: () => [],
    querySelector: () => null,
    addEventListener() {},
    createElement: tag => new Element(tag),
  };
  const location = { origin: 'https://studio.test', pathname: '/ai-agent-studio', search: '?projectId=ep01-test' };
  const fetch = async (url, options) => {
    calls.push({ url, options });
    // Leave startup pending so tests exercise only explicit user actions.
    if (new URL(url, location.origin).pathname === '/api/studio/project') return new Promise(() => {});
    return respond(url, options);
  };
  vm.runInNewContext(source, {
    document, location, fetch, URL, URLSearchParams, FormData,
    Option: function(text, value) { this.textContent = text; this.value = value; },
    window: { StudioCatalog: catalog }, setTimeout, console,
  });
  return { nodes, calls, location };
}

const event = () => ({ preventDefault() {} });
const file = (name = 'research.zip') => new File(['PK\x03\x04archive'], name);
const stored = { id: 'saved-file-id', filename: 'research.zip', sha256: 'a'.repeat(64), size_bytes: 11 };
const text = element => [element.textContent, ...element.children.map(child => typeof child === 'string' ? child : text(child))].join(' ');
const posts = h => h.calls.filter(call => call.options.method === 'POST');

test('given upload button when clicked then chooser opens synchronously before a slow file-list request', async () => {
  const order = [];
  const h = harness(() => { order.push('list'); return new Promise(() => {}); });
  h.nodes.get('files').open = true;
  h.nodes.get('upload-file').click = () => order.push('chooser');
  h.nodes.get('open-upload').onclick(event());
  assert.deepEqual(order, ['chooser', 'list']);
  assert.equal(posts(h).length, 0);
});

test('given no selection or empty or oversized file when submitted then no request is sent', async () => {
  for (const selection of [[], [{ name: 'empty.md', size: 0 }], [{ name: 'large.zip', size: 100 * 1024 * 1024 + 1 }]]) {
    const h = harness();
    h.nodes.get('upload-file').files = selection;
    await h.nodes.get('upload-form').onsubmit(event());
    assert.equal(posts(h).length, 0);
    assert.match(text(h.nodes.get('upload-status')), /파일|선택/);
    assert.equal(h.nodes.get('upload-submit').disabled, false);
  }
});

test('given a selected file when upload succeeds then multipart bytes and project are preserved and a download receipt remains', async () => {
  const h = harness((url, options) => options.method === 'POST' ? Response.json({ file: stored, duplicate: false }) : Response.json([]));
  const selected = file();
  h.nodes.get('upload-file').files = [selected];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.equal(posts(h).length, 1);
  assert.equal(new URL(posts(h)[0].url, h.location.origin).searchParams.get('projectId'), 'ep01-test');
  assert.equal(posts(h)[0].options.credentials, 'same-origin');
  assert.equal(posts(h)[0].options.body.get('file'), selected);
  assert.match(text(h.nodes.get('upload-receipt')), /saved-file-id/);
  assert.match(text(h.nodes.get('upload-receipt')), /a{64}/);
  const download = h.nodes.get('upload-receipt').children.find(child => child.tagName === 'A');
  assert.equal(download.href, '/api/studio/files/saved-file-id/content?projectId=ep01-test');
  assert.match(text(h.nodes.get('upload-status')), /보관/);
  assert.equal(h.nodes.get('upload-submit').disabled, false);
  assert.equal(h.nodes.get('upload-file').files[0], selected);
});

test('given identical bytes under a different name when server reuses the original then search uses the stored filename', async () => {
  const h = harness((url, options) => options.method === 'POST' ? Response.json({ file: stored, duplicate: true }) : Response.json([]));
  h.nodes.get('upload-file').files = [file('renamed-research.zip')];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.equal(h.nodes.get('query').value, stored.filename);
  assert.match(text(h.nodes.get('upload-status')), /이미 보관/);
  assert.match(text(h.nodes.get('upload-receipt')), /saved-file-id/);
});

test('given 629 stored files when upload completes then every page is loaded without losing the receipt', async () => {
  const files = Array.from({ length: 629 }, (_, i) => ({ ...stored, id: 'file-' + i, filename: 'source-' + i + '.md', extension: 'md' }));
  const h = harness((url, options) => {
    if (options.method === 'POST') return Response.json({ file: stored, duplicate: false });
    const parsed = new URL(url, 'https://studio.test');
    if (parsed.pathname === '/api/studio/files') {
      const offset = Number(parsed.searchParams.get('offset'));
      return Response.json(files.slice(offset, offset + 200));
    }
    return Response.json([]);
  });
  h.nodes.get('upload-file').files = [file()];
  await h.nodes.get('upload-form').onsubmit(event());
  const pages = h.calls.filter(call => new URL(call.url, h.location.origin).pathname === '/api/studio/files' && call.options.method !== 'POST');
  assert.deepEqual(pages.map(call => new URL(call.url, h.location.origin).searchParams.get('offset')), ['0', '200', '400', '600']);
  assert.equal(h.nodes.get('revision-form').elements.fileId.children.length, 630);
  assert.match(text(h.nodes.get('upload-receipt')), /saved-file-id/);
  assert.equal(posts(h).length, 1);
});

test('given a committed upload when list refresh returns 401 then saved ID and download survive without another POST', async () => {
  const h = harness((url, options) => options.method === 'POST' ? Response.json({ file: stored, duplicate: false }) : Response.json({ message: 'Unauthorized' }, { status: 401 }));
  h.nodes.get('upload-file').files = [file()];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.match(text(h.nodes.get('upload-receipt')), /saved-file-id/);
  assert.match(text(h.nodes.get('upload-status')), /보관.*완료/);
  assert.match(text(h.nodes.get('upload-status')), /401/);
  assert.match(text(h.nodes.get('upload-status')), /재업로드/);
  assert.equal(posts(h).length, 1);
  assert.equal(h.nodes.get('upload-submit').disabled, false);
});

test('given an expired login when POST returns 401 then selected file remains and login return URL stays on this project', async () => {
  const h = harness(() => Response.json({ message: 'Unauthorized' }, { status: 401 }));
  const selected = file();
  h.nodes.get('upload-file').files = [selected];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.match(text(h.nodes.get('upload-status')), /로그인.*401/);
  assert.equal(h.nodes.get('upload-file').files[0], selected);
  assert.doesNotMatch(text(h.nodes.get('upload-receipt')), /saved-file-id/);
  const login = h.nodes.get('upload-status').children.find(child => child.tagName === 'A');
  const url = new URL(login.href, h.location.origin);
  assert.equal(url.origin, h.location.origin);
  assert.equal(url.searchParams.get('returnUrl'), h.location.pathname + h.location.search);
  assert.equal(login.target, '_blank');
  assert.equal(posts(h).length, 1);
});

test('given a non-JSON proxy rejection when upload returns 413 then HTTP code is visible and controls recover', async () => {
  const h = harness(() => new Response('<html>Too large</html>', { status: 413, headers: { 'Content-Type': 'text/html' } }));
  h.nodes.get('upload-file').files = [file()];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.match(text(h.nodes.get('upload-status')), /413/);
  assert.equal(h.nodes.get('upload-file').disabled, false);
  assert.equal(h.nodes.get('upload-submit').disabled, false);
});

test('given a lost response when upload throws then outcome is unknown and duplicate search is required before retry', async () => {
  const h = harness(() => { throw new TypeError('Failed to fetch'); });
  h.nodes.get('upload-file').files = [file()];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.match(text(h.nodes.get('upload-status')), /결과.*확인하지 못/);
  assert.match(text(h.nodes.get('upload-status')), /보관함/);
  assert.equal(posts(h).length, 1);
  assert.equal(h.nodes.get('upload-submit').disabled, false);
});

test('given a server error when response is 503 then outcome stays unverified and no automatic retry occurs', async () => {
  const h = harness(() => Response.json({ message: 'Unavailable' }, { status: 503 }));
  h.nodes.get('upload-file').files = [file()];
  await h.nodes.get('upload-form').onsubmit(event());
  assert.match(text(h.nodes.get('upload-status')), /결과.*확인하지 못/);
  assert.match(text(h.nodes.get('upload-status')), /503/);
  assert.equal(posts(h).length, 1);
});

test('given an in-flight upload when submitted twice then only one POST is sent', async () => {
  let complete;
  const h = harness((url, options) => options.method === 'POST' ? new Promise(resolve => { complete = resolve; }) : Response.json([]));
  h.nodes.get('upload-file').files = [file()];
  const first = h.nodes.get('upload-form').onsubmit(event());
  assert.equal(h.nodes.get('upload-submit').disabled, true);
  assert.equal(h.nodes.get('upload-file').disabled, true);
  await h.nodes.get('upload-form').onsubmit(event());
  assert.equal(posts(h).length, 1);
  complete(Response.json({ file: stored, duplicate: false }));
  await first;
  assert.equal(h.nodes.get('upload-submit').disabled, false);
});
