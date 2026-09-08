const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const { loadSectionsModule } = require('./helpers/browser_script_harness');

const VIDEOS_JS = path.resolve(__dirname, '../../main/resources/static/js/tutor_academy_videos.js');

/**
 * 영상 관리 화면(tutor_academy_videos.js)의 실제 코드를 실행해 검증한다(재구현 아님).
 *
 * Phase 3: 단일 "등록된 영상" 목록 + 필터 드롭다운 방식을 버리고 미지정/원장/수업 세
 * 영역으로 항상 분리해서 그린다. 각 카드의 용도 select를 바꾸면 즉시 PATCH로 서버에
 * 반영된다. 신규 업로드는 용도를 고르지 않으면 막는다(기본 선택값 없음).
 */
function runVideosPage(options) {
  const opts = options || {};
  const videos = opts.videos || [];

  const elements = new Map();
  const element = (id) => {
    if (!elements.has(id)) {
      elements.set(id, {
        id,
        dataset: {},
        style: {},
        innerHTML: '',
        textContent: '',
        disabled: false,
        files: [],
        classList: { add() {}, remove() {}, toggle() {} },
        addEventListener(type, cb) {
          this[`_on_${type}`] = this[`_on_${type}`] || [];
          this[`_on_${type}`].push(cb);
        }
      });
    }
    return elements.get(id);
  };
  ['videoInput', 'uploadButton', 'uploadStatus', 'uploadProgress', 'videoGroups', 'emptyState', 'videoCount',
    'videoLinkUrl', 'videoLinkTitle', 'videoLinkButton', 'videoLinkStatus']
    .forEach(element);

  const purposeState = { checked: null };
  const linkPurposeState = { checked: null };
  let domReadyCb = null;

  const document = {
    addEventListener(type, cb) { if (type === 'DOMContentLoaded') domReadyCb = cb; },
    getElementById(id) { return elements.has(id) ? element(id) : null; },
    querySelector(selector) {
      if (selector === 'input[name="videoPurpose"]:checked') return purposeState.checked;
      if (selector === 'input[name="videoLinkPurpose"]:checked') return linkPurposeState.checked;
      return null;
    },
    querySelectorAll(selector) {
      if (selector === 'input[name="videoPurpose"]') return purposeState.all || [];
      if (selector === 'input[name="videoLinkPurpose"]') return linkPurposeState.all || [];
      return [];
    },
    createElement() {
      return {
        style: {}, dataset: {}, classList: { add() {}, remove() {} },
        setAttribute() {}, replaceWith() {}
      };
    }
  };

  const fetchCalls = [];
  const fetchAuth = opts.fetchAuth || (async (url, init) => {
    fetchCalls.push({ url, init });
    if (!init || !init.method) return { ok: true, json: async () => videos };
    return { ok: true, json: async () => ({}) };
  });

  const context = vm.createContext({
    console,
    document,
    URL: { createObjectURL: () => 'blob:mock', revokeObjectURL() {} },
    FormData,
    confirm: () => true,
    window: {
      F2RAcademySections: loadSectionsModule(),
      ensureCurrentUser: async () => {},
      fetchAuth
    }
  });
  vm.runInContext(fs.readFileSync(VIDEOS_JS, 'utf8'), context, { filename: VIDEOS_JS });

  return {
    elements,
    element,
    fetchCalls,
    purposeState,
    linkPurposeState,
    async ready() {
      if (!domReadyCb) throw new Error('DOMContentLoaded 핸들러가 등록되지 않았다');
      await domReadyCb();
    },
    click(id) {
      const el = element(id);
      (el._on_click || []).forEach((cb) => cb({}));
    },
    change(container, target) {
      const el = element(container);
      (el._on_change || []).forEach((cb) => cb({ target: { closest: (sel) => (sel === '[data-purpose-select]' ? target : null) } }));
    }
  };
}

const VIDEOS = [
  { videoId: 1, videoId_: 1, originalFilename: '원장님 인터뷰.mp4', purpose: 'DIRECTOR', durationSec: 30, width: 1920, height: 1080 },
  { videoId: 2, originalFilename: '문법 강의.mp4', purpose: 'LESSON', durationSec: 45, width: 720, height: 1280 },
  { videoId: 3, originalFilename: '옛날 업로드.mp4', purpose: null, durationSec: 12, width: 640, height: 360 }
];

test('given DIRECTOR/LESSON/미지정 영상이 섞여 있을 때 when 관리 화면을 렌더 then 세 영역으로 완전히 분리되고 미지정이 맨 위에 강조된다', async () => {
  const page = runVideosPage({ videos: VIDEOS });
  await page.ready();

  const html = page.element('videoGroups').innerHTML;

  // then — 세 영역 헤딩과 정확한 카운트
  assert.match(html, /용도 미지정 \(1\)/, '미지정 영역 카운트가 틀림');
  assert.match(html, /대표 원장 소개 영상 \(1\)/, '원장 영역 카운트가 틀림');
  assert.match(html, /수업 영상 \(1\)/, '수업 영역 카운트가 틀림');

  // then — 미지정 영역이 원장/수업보다 먼저(위) 나온다
  const unclassifiedIdx = html.indexOf('용도 미지정');
  const directorIdx = html.indexOf('대표 원장 소개 영상 (');
  assert.ok(unclassifiedIdx >= 0 && unclassifiedIdx < directorIdx, '미지정 영역이 맨 위에 있어야 한다');

  // then — 각 카드의 용도 select가 실제 값으로 미리 선택되어 있다
  assert.match(html, /data-purpose-select="1"[\s\S]{0,300}?value="DIRECTOR" selected/);
  assert.match(html, /data-purpose-select="2"[\s\S]{0,300}?value="LESSON" selected/);

  // then — 옛 단일 목록 + 필터 드롭다운은 사라졌다
  assert.doesNotMatch(html, /purposeFilter/);
});

test('given 미지정 영상 카드의 용도 select를 변경 when change 이벤트가 발생 then PATCH로 즉시 서버에 반영한다', async () => {
  const page = runVideosPage({ videos: VIDEOS });
  await page.ready();
  page.fetchCalls.length = 0; // 초기 GET 기록은 지우고 이번 액션만 본다

  page.change('videoGroups', { dataset: { purposeSelect: '3' }, value: 'LESSON', disabled: false });
  // onPurposeChange는 async — 마이크로태스크가 끝날 시간을 준다
  await new Promise((resolve) => setImmediate(resolve));

  const patchCall = page.fetchCalls.find((c) => c.init && c.init.method === 'PATCH');
  assert.ok(patchCall, 'PATCH 요청이 발생하지 않았다');
  assert.equal(patchCall.url, '/api/tutor/academy/videos/3/purpose');
  assert.deepEqual(JSON.parse(patchCall.init.body), { purpose: 'LESSON' });
});

test('given 용도를 아무것도 선택하지 않음 when 업로드 버튼을 누르면 then 업로드 요청 자체를 보내지 않는다(필수화)', async () => {
  const page = runVideosPage({ videos: [] });
  await page.ready();
  page.purposeState.checked = null; // 아무 라디오도 선택 안 함
  page.element('videoInput').files = [{ name: 'clip.mp4', size: 1024 }];
  page.fetchCalls.length = 0;

  page.click('uploadButton');
  await new Promise((resolve) => setImmediate(resolve));

  const uploadCall = page.fetchCalls.find((c) => c.url === '/api/tutor/academy/videos' && c.init && c.init.method === 'POST');
  assert.equal(uploadCall, undefined, '용도 미선택인데 업로드 요청이 나갔다');
  assert.equal(page.element('uploadStatus').textContent, '영상 용도(수업 영상 · 대표 원장 소개 영상 · 용도 지정 안 함)를 선택해 주세요.');
});

test('given 영상이 없을 때 when 관리 화면을 렌더 then 세 영역 모두 빈 상태 문구를 보여준다', async () => {
  const page = runVideosPage({ videos: [] });
  await page.ready();

  const html = page.element('videoGroups').innerHTML;
  assert.doesNotMatch(html, /용도 미지정/, '영상이 없으면 미지정 영역 자체가 없어야 한다');
  assert.match(html, /아직 지정된 대표 원장 소개 영상이 없습니다/);
  assert.match(html, /아직 지정된 수업 영상이 없습니다/);
});

const MIXED_VIDEOS = [
  { videoId: 1, originalFilename: '문법 강의.mp4', purpose: 'LESSON', durationSec: 45, width: 720, height: 1280,
    sourceKind: 'DIRECT_UPLOAD', videoUrl: '/api/academies/x/videos/1/stream' },
  { videoId: 2, originalFilename: '네이버 공개수업', purpose: 'LESSON', sourceKind: 'EXTERNAL_LINK',
    provider: 'NAVER', externalUrl: 'https://naver.me/F4WnDovq', videoUrl: null, thumbnailUrl: null }
];

test('given 직접 업로드 + 외부 링크가 섞였을 때 when 렌더 then 카드마다 출처 배지와 카운트가 30 기준이다', async () => {
  const page = runVideosPage({ videos: MIXED_VIDEOS });
  await page.ready();

  const html = page.element('videoGroups').innerHTML;
  assert.match(html, /직접 업로드/);
  assert.match(html, /외부 링크 · 네이버/);
  assert.match(html, /href="https:\/\/naver\.me\/F4WnDovq"[^>]*target="_blank"/);
  assert.doesNotMatch(html, /<video class="video-card-thumb" src="null"/, '외부 링크에 <video src=null> 를 만들면 안 된다');
  assert.equal(page.element('videoCount').textContent, '(2/30)');
});

test('given 외부 링크 등록 폼 when https URL·제목·용도를 채우고 등록 then link 엔드포인트로 JSON POST 한다', async () => {
  const page = runVideosPage({ videos: [] });
  await page.ready();
  page.element('videoLinkUrl').value = 'https://naver.me/F4WnDovq';
  page.element('videoLinkTitle').value = '6월 공개수업';
  page.linkPurposeState.checked = { value: 'LESSON' };
  page.linkPurposeState.all = [{ checked: true }];
  page.fetchCalls.length = 0;

  page.click('videoLinkButton');
  await new Promise((resolve) => setImmediate(resolve));

  const call = page.fetchCalls.find((c) => c.url === '/api/tutor/academy/videos/link');
  assert.ok(call, 'link 엔드포인트로 요청이 나가지 않았다');
  assert.equal(call.init.method, 'POST');
  assert.deepEqual(JSON.parse(call.init.body),
    { url: 'https://naver.me/F4WnDovq', title: '6월 공개수업', purpose: 'LESSON' });
});

test('given http:// URL when 외부 링크 등록 then 요청을 보내지 않고 오류를 표시한다', async () => {
  const page = runVideosPage({ videos: [] });
  await page.ready();
  page.element('videoLinkUrl').value = 'http://naver.me/x';
  page.element('videoLinkTitle').value = '제목';
  page.linkPurposeState.checked = { value: 'LESSON' };
  page.fetchCalls.length = 0;

  page.click('videoLinkButton');
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(page.fetchCalls.find((c) => c.url === '/api/tutor/academy/videos/link'), undefined);
  assert.match(page.element('videoLinkStatus').textContent, /https/);
});
