/*
 * 대화형 화면의 '내려받기'·'버전' 표시 규칙(순수 함수). 브라우저에서는 window.AssistantExport, 테스트에서는 require로 쓴다.
 * - 내려받기: 미리보기에 그려진 홈페이지를 한 개의 HTML 파일로 만든다(스크립트 제거, 기준 주소 삽입).
 * - 버전: 버전 기록 한 줄을 사람이 읽는 문구로 바꾼다.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.AssistantExport = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /** 파일 이름으로 쓸 수 없는 글자를 '-'로 바꾸고 날짜(yyyymmdd)를 붙인다. */
  function fileName(academyName, now) {
    var d = now instanceof Date ? now : new Date();
    var ymd = d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0');
    var base = String(academyName || '').replace(/[\\/:*?"<>|\s]+/g, '-').replace(/^-+|-+$/g, '');
    return (base || 'homepage') + '-' + ymd + '.html';
  }

  /**
   * 미리보기 문서의 HTML 문자열을 내려받을 파일 내용으로 바꾼다.
   * 스크립트는 제거하고(그려진 결과만 남긴다), 상대 경로 자원이 열리도록 기준 주소(base)를 넣는다.
   */
  function finalizeHtml(html, baseHref) {
    var out = String(html || '').replace(/<script\b[\s\S]*?<\/script>/gi, '').replace(/<noscript\b[\s\S]*?<\/noscript>/gi, '');
    if (baseHref && !/<base\s/i.test(out)) {
      var tag = '<base href="' + String(baseHref).replace(/"/g, '&quot;') + '">';
      out = /<head[^>]*>/i.test(out) ? out.replace(/<head([^>]*)>/i, '<head$1>' + tag) : tag + out;
    }
    return /^\s*<!doctype/i.test(out) ? out : '<!DOCTYPE html>\n' + out;
  }

  /** 버전 기록 한 줄: "v3 · 10.13 07:40 · 메모". 시각은 서버가 준 문자열을 그대로 잘라 쓴다(시간대 변환 없음). */
  function versionLabel(v) {
    var no = v && v.versionNo != null ? 'v' + v.versionNo : '버전';
    var raw = v && v.createdAt ? String(v.createdAt) : '';
    var m = raw.match(/^\d{4}-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
    var when = m ? m[1] + '.' + m[2] + ' ' + m[3] + ':' + m[4] : '';
    var note = v && v.note ? String(v.note) : '기록';
    return [no, when, note].filter(Boolean).join(' · ');
  }

  /** 내려받는 파일에 합친 CSS의 상대 url(...)을 원래 CSS 위치 기준 절대 주소로 바꾼다(글꼴·배경 이미지가 깨지지 않게). */
  function absolutizeCssUrls(css, cssHref) {
    return String(css || '').replace(/url\(\s*(['"]?)([^)'"]+)\1\s*\)/g, function (all, q, ref) {
      if (/^(data:|https?:|\/\/|#)/i.test(ref)) return all;
      try { return 'url(' + q + new URL(ref, cssHref).href + q + ')'; } catch (e) { return all; }
    });
  }

  var TARGETS = [
    { key: 'ELEMENTARY', label: '초등' }, { key: 'MIDDLE', label: '중등' }, { key: 'HIGH', label: '고등' },
    { key: 'UNIVERSITY', label: '대학' }, { key: 'ADULT', label: '성인' }
  ];

  /** 첫 화면(랜딩)에서 보관해 둔 제작 요청의 sessionStorage 키. landing_buildit.js와 같은 값이어야 한다. */
  var PENDING_KEY = 'builditPendingPrompt';
  var PENDING_TTL_MS = 60 * 60 * 1000;

  /** 보관된 제작 요청을 꺼내고 지운다. 없거나 깨졌거나 1시간이 지났으면 ''를 돌려준다. */
  function takePendingPrompt(storage, nowMs) {
    var raw = null;
    try { raw = storage.getItem(PENDING_KEY); storage.removeItem(PENDING_KEY); } catch (e) { return ''; }
    if (!raw) return '';
    try {
      var v = JSON.parse(raw);
      var fresh = v && typeof v.text === 'string' && typeof v.at === 'number' && nowMs - v.at <= PENDING_TTL_MS && nowMs >= v.at;
      return fresh ? v.text.trim() : '';
    } catch (e) { return ''; }
  }

  /** "홈페이지를 공개하고 싶어"처럼 공개(정식 배포 등록)를 바라는 짧은 말인지. 디자인 수정 요청은 아니다. */
  function isPublishIntent(text) {
    var t = String(text || '').trim();
    if (!t || t.length > 40) return false;
    if (/(비공개|숨|삭제|추가|버튼|섹션|문구|색|바꿔|변경|수정)/.test(t)) return false;
    return /(공개|배포|게시|올려)/.test(t) && /(하고\s*싶|할래|할게|해\s*(줘|주세요|줄래)|하자|하겠|해요|싶어)/.test(t);
  }

  return {
    takePendingPrompt: takePendingPrompt, isPublishIntent: isPublishIntent, PENDING_KEY: PENDING_KEY,
    fileName: fileName, finalizeHtml: finalizeHtml, versionLabel: versionLabel,
    absolutizeCssUrls: absolutizeCssUrls, TARGETS: TARGETS
  };
});
