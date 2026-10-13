const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const { loadSectionsModule } = require('./helpers/browser_script_harness');

const businessInfo = require('../../main/resources/static/js/academy_business_info.js');

const PUBLIC_JS = path.resolve(__dirname, '../../main/resources/static/js/academy_public.js');

/**
 * 공개 홈페이지 Hero 태블릿(사진↔영상 순환)이 원장 사진 유무와 관계없이 부팅하는지 검증한다.
 * 실제 academy_public.js를 실행하고, 태블릿 DOM은 사진/영상 요소 유무만 흉내 낸다.
 */
function runPublicPage({ withPhoto, withVideo = true }) {
  const mkEl = () => ({ dataset: {}, style: {}, classList: { add() {}, remove() {} }, addEventListener() {}, setAttribute() {}, removeAttribute() {}, pause() {}, play() { return Promise.resolve(); } });
  const photo = withPhoto ? mkEl() : null;
  const video = withVideo ? mkEl() : null;
  const tablet = {
    dataset: {},
    querySelector(sel) {
      if (sel === '.tablet-media-stack') return mkEl();
      if (sel === '.tablet-photo') return photo;
      if (sel === '.tablet-video') return video;
      return null;
    }
  };
  const elements = new Map();
  const element = (id) => {
    if (!elements.has(id)) {
      elements.set(id, {
        id, dataset: {}, style: {}, innerHTML: '', textContent: '',
        classList: { add() {}, remove() {} }, addEventListener() {}, contains() { return false; },
        querySelector(sel) { return id === 'publicView' && sel === '.tablet' ? tablet : null; },
        querySelectorAll() { return []; }
      });
    }
    return elements.get(id);
  };
  const academy = { slug: 'hero-cycle', name: '순환 학원', intro: '소개', subjects: [], regions: [], lessons: [], pageSettings: null };
  const document = {
    getElementById(id) { return ['publicView', 'errorMsg'].includes(id) ? element(id) : null; },
    addEventListener() {},
    body: { contains() { return true; } },
    querySelector() { return null; },
    querySelectorAll() { return []; }
  };
  element('publicView').dataset.slug = academy.slug;
  const context = vm.createContext({
    alert() {},
    console,
    URLSearchParams,
    document,
    async fetch(url) {
      return url === `/api/academies/${academy.slug}` ? { ok: true, json: async () => academy } : { ok: false, json: async () => [] };
    },
    async fetchAuth() { return { ok: false }; },
    location: { href: '', origin: 'http://localhost', pathname: `/academies/${academy.slug}` },
    sessionStorage: { getItem() { return null; } },
    setTimeout() { return 0; },
    clearTimeout() {},
    window: {
      F2RAcademyBusinessInfo: businessInfo,
      F2RLessonProductShowcase: { render() { return ''; } },
      F2RAcademySections: loadSectionsModule(),
      addEventListener() {}
    }
  });
  vm.runInContext(fs.readFileSync(PUBLIC_JS, 'utf8'), context, { filename: PUBLIC_JS });
  return { photo, video, errorMsg: element('errorMsg') };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

test('given 원장 사진이 없고 영상만 있는 홈페이지 when 페이지를 부팅하면 then 오류 문구 없이 렌더가 끝난다', async () => {
  // given — 대화로 새로 만든 학원은 원장 사진이 아직 없다
  const page = runPublicPage({ withPhoto: false });

  // when
  await settle();

  // then
  assert.equal(page.errorMsg.textContent, '');
});

test('given 원장 사진과 영상이 모두 있는 홈페이지 when 페이지를 부팅하면 then 사진을 먼저 보여 주고 영상은 숨긴다', async () => {
  // given
  const page = runPublicPage({ withPhoto: true });

  // when
  await settle();

  // then — 기존 순환 동작은 그대로다
  assert.equal(page.errorMsg.textContent, '');
  assert.equal(page.photo.style.opacity, '1');
  assert.equal(page.video.style.opacity, '0');
});

test('given 영상 요소가 없는 홈페이지 when 페이지를 부팅하면 then 오류 문구 없이 렌더가 끝난다', async () => {
  // given
  const page = runPublicPage({ withPhoto: true, withVideo: false });

  // when
  await settle();

  // then
  assert.equal(page.errorMsg.textContent, '');
});
