const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const { loadSectionsModule } = require('./helpers/browser_script_harness');

const businessInfo = require('../../main/resources/static/js/academy_business_info.js');

const PUBLIC_JS = path.resolve(__dirname, '../../main/resources/static/js/academy_public.js');

/**
 * 발행 학원 페이지 `상담 신청` 클릭 경로를 실제 academy_public.js를 실행해 검증한다.
 *
 * browser_script_harness는 businessInfo를 stub으로 넣지만 여기서는 실제 helper를 주입해야
 * 연락처 판정까지 같은 코드로 통과한다.
 */
function runPublicPage(academy) {
  const elements = new Map();
  const element = (id) => {
    if (!elements.has(id)) {
      elements.set(id, {
        id,
        dataset: {},
        style: {},
        innerHTML: '',
        textContent: '',
        classList: { add() {}, remove() {} },
        addEventListener() {},
        contains() { return false; },
        querySelectorAll() { return []; }
      });
    }
    return elements.get(id);
  };
  const alerts = [];
  const document = {
    getElementById(id) {
      // 렌더 대상만 존재하는 DOM을 흉내낸다. 그 외(영상 player 등)는 없는 것으로 둔다.
      return ['publicView', 'errorMsg', 'consultOptions', 'consultModal'].includes(id)
        ? element(id)
        : null;
    },
    addEventListener() {},
    querySelector() { return null; },
    querySelectorAll() { return []; }
  };
  element('publicView').dataset.slug = academy.slug;
  const context = vm.createContext({
    alert(message) { alerts.push(message); },
    console,
    document,
    async fetch(url) {
      if (url === `/api/academies/${academy.slug}`) {
        return { ok: true, json: async () => academy };
      }
      return { ok: false, json: async () => [] };
    },
    async fetchAuth() { return { ok: false }; },
    location: { href: '', origin: 'http://localhost', pathname: `/academies/${academy.slug}` },
    sessionStorage: { getItem() { return null; } },
    setTimeout,
    window: {
      F2RAcademyBusinessInfo: businessInfo,
      F2RLessonProductShowcase: { render() { return ''; } },
      // 섹션 마크업 정본. academy_public.js가 이것 없이는 부팅하지 않는다.
      F2RAcademySections: loadSectionsModule(),
      addEventListener() {}
    }
  });
  vm.runInContext(fs.readFileSync(PUBLIC_JS, 'utf8'), context, { filename: PUBLIC_JS });
  return { context, elements: { options: element('consultOptions'), modal: element('consultModal') }, alerts };
}

const academyWith = (businessInfoRaw) => ({
  slug: 'sample-academy',
  name: '샘플 학원',
  intro: '소개',
  subjects: [],
  regions: [],
  lessons: [],
  pageSettings: { businessInfo: businessInfoRaw }
});

test('given 학원이 전화·이메일을 입력한 발행 페이지 when 상담 신청을 누르면 then 두 연락 수단이 모달에 열린다', async () => {
  // given
  const page = runPublicPage(academyWith({ phone: '010-1234-5678', email: 'hello@academy.co.kr' }));
  await new Promise((resolve) => setTimeout(resolve, 0));

  // when
  page.context.window.requestConsult();

  // then
  assert.equal(page.elements.modal.style.display, 'flex');
  assert.match(page.elements.options.innerHTML, /href="tel:01012345678"/);
  assert.match(page.elements.options.innerHTML, /href="mailto:hello@academy\.co\.kr"/);
  assert.match(page.elements.options.innerHTML, /010-1234-5678/);
  assert.deepEqual(page.alerts, []);
});

test('given 이메일만 입력한 발행 페이지 when 상담 신청을 누르면 then 전화 옵션 없이 이메일만 노출된다', async () => {
  // given
  const page = runPublicPage(academyWith({ email: 'ask@academy.co.kr' }));
  await new Promise((resolve) => setTimeout(resolve, 0));

  // when
  page.context.window.requestConsult();

  // then
  assert.equal(page.elements.modal.style.display, 'flex');
  assert.match(page.elements.options.innerHTML, /mailto:ask@academy\.co\.kr/);
  assert.doesNotMatch(page.elements.options.innerHTML, /tel:/);
});

test('given 연락처가 없는 발행 페이지 when 상담 신청을 누르면 then 모달 대신 데모 안내를 띄운다', async () => {
  // given — pageSettings 자체가 null인 legacy Academy
  const page = runPublicPage({ slug: 'legacy-academy', name: '레거시 학원', intro: '', subjects: [], regions: [], lessons: [] });
  await new Promise((resolve) => setTimeout(resolve, 0));

  // when
  page.context.window.requestConsult();

  // then
  assert.notEqual(page.elements.modal.style.display, 'flex');
  assert.deepEqual(page.alerts, ['상담 신청 기능은 준비 중입니다. (데모)']);
});

test('given 마스킹 dummy만 표시되는 발행 페이지 when 상담 신청을 누르면 then dummy 번호로 전화 링크를 만들지 않는다', async () => {
  // given — footer는 000-0000-0000으로 보이지만 저장값은 blank다
  const page = runPublicPage(academyWith({ phone: '', email: '' }));
  await new Promise((resolve) => setTimeout(resolve, 0));

  // when
  page.context.window.requestConsult();

  // then
  assert.deepEqual(page.alerts, ['상담 신청 기능은 준비 중입니다. (데모)']);
  assert.equal(page.elements.options.innerHTML, '');
});

test('given 열린 상담 모달 when 배경을 클릭하면 then 닫히고 카드 내부 클릭은 무시된다', async () => {
  // given
  const page = runPublicPage(academyWith({ phone: '010-1234-5678' }));
  await new Promise((resolve) => setTimeout(resolve, 0));
  page.context.window.requestConsult();
  const modal = page.elements.modal;

  // when — 카드 내부에서 버블링된 클릭(currentTarget !== target)
  page.context.window.closeConsultModal({ currentTarget: modal, target: { id: 'card' } });

  // then
  assert.equal(modal.style.display, 'flex');

  // when — 배경 자체 클릭
  page.context.window.closeConsultModal({ currentTarget: modal, target: modal });

  // then
  assert.equal(modal.style.display, 'none');
});
