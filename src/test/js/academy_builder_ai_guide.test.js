const assert = require('node:assert/strict');
const test = require('node:test');
const guide = require('../../main/resources/static/js/academy_builder_ai_guide.js');

/**
 * AI Guided Academy Builder(Phase 5B) 순수 헬퍼. DOM/fetch 없이 스텝 정의·세션 상태·진행률·
 * 후보 파싱만 검증한다. wiring(academy_builder.js)은 별도 하네스 테스트에서 다룬다.
 */

test('STEPS의 text 단계 target은 전부 실제 builderDraft key(또는 intro)다 — 상상한 field가 없다', () => {
  const known = new Set([
    'intro', 'principalDesc', 'principalQuote',
    'philosophyItem1', 'philosophyItem2', 'philosophyItem3', 'philosophyItem4',
    'philosophyKeyword', 'philosophyTitle',
    'introHeading', 'introMotto', 'introBodySecondary',
    'featuredEyebrow', 'featuredTitle', 'featuredDescription', 'featuredCtaLabel',
    'videosSectionTitle', 'socialSectionTitle', 'miniLecturesSectionTitle'
  ]);
  guide.STEPS.filter((s) => s.kind === 'text').forEach((step) => {
    assert.ok(step.target, `${step.id} text 단계에 target이 없다`);
    assert.ok(known.has(step.target), `${step.id} target(${step.target})이 서버 allowlist 밖이다`);
  });
});

test('첫 단계는 이름/주소 온보딩, 두 번째가 스타일, 마지막이 마무리다 (Phase 5B.2 재정렬)', () => {
  assert.equal(guide.STEPS[0].kind, 'naming');
  assert.equal(guide.STEPS[0].id, 'naming');
  assert.equal(guide.firstStepId(), 'naming');
  assert.equal(guide.STEPS[1].kind, 'style');
  assert.equal(guide.STEPS[guide.STEPS.length - 1].kind, 'finish');
  assert.equal(guide.STEP_IDS.length, 8, '권장 8단계');
});

test('createSession은 저장값이 없거나 손상돼도 안전한 기본 세션을 만든다', () => {
  const fresh = guide.createSession(null);
  assert.equal(fresh.currentStepId, 'naming');
  assert.deepEqual(fresh.completedStepIds, []);
  assert.deepEqual(fresh.skippedStepIds, []);
  assert.equal(fresh.onboarded, false);

  const dirty = guide.createSession({
    currentStepId: 'no-such-step',
    completedStepIds: ['style', 'bogus', 'style'],
    skippedStepIds: 'not-an-array',
    desiredStyle: 'x'.repeat(500)
  });
  assert.equal(dirty.currentStepId, 'naming', '알 수 없는 스텝은 첫 스텝으로 clamp');
  assert.deepEqual(dirty.completedStepIds, ['style'], '알 수 없는/중복 스텝 제거');
  assert.deepEqual(dirty.skippedStepIds, []);
  assert.ok(dirty.desiredStyle.length <= 200, 'desiredStyle 길이 제한');
});

test('기존 세션 호환: videos 단계 진행 상태가 programs 로 마이그레이션된다', () => {
  const legacy = guide.createSession({
    currentStepId: 'videos',
    completedStepIds: ['style', 'hero', 'videos'],
    skippedStepIds: ['videos'],
    onboarded: true
  });
  assert.equal(legacy.currentStepId, 'programs', 'videos → programs');
  assert.ok(legacy.completedStepIds.indexOf('programs') >= 0);
  assert.ok(legacy.completedStepIds.indexOf('videos') < 0, 'videos id 는 더 이상 없다');
  assert.equal(guide.migrateStepId('videos'), 'programs');
  assert.equal(guide.stepById('videos').id, 'programs', 'stepById 도 마이그레이션');
});

test('isNamingComplete: 발행 + 실제 handle + 이름이면 naming 을 건너뛴다', () => {
  assert.equal(guide.isNamingComplete(null), false);
  assert.equal(guide.isNamingComplete({ name: '학원', handle: '', publicPathUrl: null }), false, '미발행/미설정');
  assert.equal(guide.isNamingComplete({ name: '학원', handle: 'academy-a1b2c3', publicPathUrl: '/academies/x' }), false, '임시 handle');
  assert.equal(guide.isNamingComplete({ name: '압구정에듀홀릭', handle: 'apgujeong-eduholic', publicPathUrl: '/academies/x' }), true);
});

test('isNamingConfigured: 발행 전이라도 이름 + 실제 handle 이면 설정된 것으로 본다(§6)', () => {
  assert.equal(guide.isNamingConfigured(null), false);
  assert.equal(guide.isNamingConfigured({ name: '새 학원', handle: 'apgujeong-eduholic' }), false, 'placeholder 이름');
  assert.equal(guide.isNamingConfigured({ name: '압구정에듀홀릭', handle: 'academy-a1b2c3' }), false, '임시 handle');
  assert.equal(guide.isNamingConfigured({ name: '압구정에듀홀릭', handle: '' }), false, 'handle 없음');
  // 발행 안 됐어도(publicPathUrl 없음) 이름 + 실제 handle 이면 true — 진행 중이던 사용자를 존중한다.
  assert.equal(guide.isNamingConfigured({ name: '압구정에듀홀릭', handle: 'apgujeong-eduholic' }), true);
});

test('shouldStartAtNaming: §7·§8 — 이름/주소 미설정 + 세션이 naming 을 지나쳐 있으면 STEP 1 로 되돌린다', () => {
  const newAcademy = { name: '새 학원', handle: '' };
  const configured = { name: '압구정에듀홀릭', handle: 'apgujeong-eduholic', publicPathUrl: '/academies/x' };

  // #195 이전 세션: currentStepId 가 style, completedStepIds 에 naming 없음 → 되돌린다.
  const legacySession = { currentStepId: 'style', onboarded: true, completedStepIds: ['style'] };
  assert.equal(guide.shouldStartAtNaming(newAcademy, legacySession), true);

  // 다른 academy 로 진행하던 세션을 신규 academy 가 물려받은 경우(키가 academy 별이 아님) → 되돌린다.
  assert.equal(guide.shouldStartAtNaming(newAcademy, { currentStepId: 'philosophy', completedStepIds: ['style', 'hero'] }), true);

  // 이미 이름/주소가 설정돼 있으면 저장된 진행 상태를 존중한다.
  assert.equal(guide.shouldStartAtNaming(configured, legacySession), false);

  // 사용자가 이미 naming 을 완료/건너뛴 세션은 존중한다.
  assert.equal(guide.shouldStartAtNaming(newAcademy, { currentStepId: 'style', completedStepIds: ['naming'] }), false);
  assert.equal(guide.shouldStartAtNaming(newAcademy, { currentStepId: 'style', skippedStepIds: ['naming'] }), false);

  // 이미 naming 에 있으면 no-op.
  assert.equal(guide.shouldStartAtNaming(newAcademy, guide.createSession(null)), false);
});

test('markCompleted / markSkipped는 상호 배타적이며 진행률에 반영된다', () => {
  let s = guide.createSession(null);
  s = guide.markCompleted(s, 'naming');
  s = guide.markCompleted(s, 'style');
  s = guide.markCompleted(s, 'hero');
  s = guide.markSkipped(s, 'hero'); // 같은 스텝을 skip하면 completed에서 빠진다
  assert.deepEqual(s.completedStepIds, ['naming', 'style']);
  assert.deepEqual(s.skippedStepIds, ['hero']);

  const info = guide.progress(guide.goToStep(s, 'principal'));
  assert.equal(info.total, guide.STEP_IDS.length);
  assert.equal(info.position, guide.stepIndex('principal') + 1);
  assert.equal(info.doneCount, 2);
  assert.ok(info.remaining.indexOf('principal') < 0, '현재 스텝은 remaining에 없다');
});

test('nextStepId / prevStepId는 경계에서 null을 준다', () => {
  assert.equal(guide.prevStepId('naming'), null);
  assert.equal(guide.nextStepId(guide.STEP_IDS[guide.STEP_IDS.length - 1]), null);
  assert.equal(guide.nextStepId('naming'), 'style');
  assert.equal(guide.nextStepId('style'), guide.STEP_IDS[2]);
});

test('stepsWithExistingContent는 이미 채워진 target 단계만 집어낸다(기존 사용자 보호)', () => {
  const preview = {
    intro: '이미 있는 히어로 소개',
    pageSettings: { builderDraft: { principalDesc: '이미 있는 원장 소개', philosophyTitle: '' } }
  };
  const ids = guide.stepsWithExistingContent(preview);
  assert.ok(ids.indexOf('hero') >= 0);
  assert.ok(ids.indexOf('principal') >= 0);
  assert.ok(ids.indexOf('philosophy') < 0, '빈 값은 기존 콘텐츠로 보지 않는다');
  assert.equal(guide.hasAnyExistingContent(preview), true);
  assert.equal(guide.hasAnyExistingContent({ pageSettings: {} }), false);
});

test('parseCandidates: JSON 배열 → 정리된 후보, 길이 초과/중복 제거', () => {
  const raw = '["학교별 시험, 분석부터 다릅니다","학교별 시험, 분석부터 다릅니다","' + '가'.repeat(500) + '"]';
  const list = guide.parseCandidates(raw, { maxLength: 60, limit: 3 });
  assert.deepEqual(list, ['학교별 시험, 분석부터 다릅니다']);
});

test('parseCandidates: 코드펜스/설명이 섞여도 배열만 뽑고, 배열이 없으면 줄 목록으로 폴백', () => {
  const fenced = '```json\n["후보1","후보2"]\n```';
  assert.deepEqual(guide.parseCandidates(fenced, { maxLength: 80 }), ['후보1', '후보2']);

  const lines = '1. 첫 번째 문구\n2) 두 번째 문구\n- 세 번째 문구';
  assert.deepEqual(guide.parseCandidates(lines, { maxLength: 80 }), ['첫 번째 문구', '두 번째 문구', '세 번째 문구']);
});

test('parseCandidates: 완전히 깨진 응답은 빈 배열', () => {
  assert.deepEqual(guide.parseCandidates('죄송합니다. 만들 수 없습니다.', {}), []);
  assert.deepEqual(guide.parseCandidates('', {}), []);
  assert.deepEqual(guide.parseCandidates(null, {}), []);
});

test('isGuideTextTarget은 text 스텝 target만 true', () => {
  assert.equal(guide.isGuideTextTarget('principalDesc'), true);
  assert.equal(guide.isGuideTextTarget('intro'), true);
  assert.equal(guide.isGuideTextTarget('name'), false);
  assert.equal(guide.isGuideTextTarget('monthlyTuition'), false);
});
