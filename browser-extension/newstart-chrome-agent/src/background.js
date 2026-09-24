/*
 * NewStart Chrome Agent — background service worker (MV3, Phase 2 PoC).
 *
 * 책임: FSS content script가 올린 "단계 판별 결과(번호·근거 카테고리와 시각만)"를
 * 보관하고, NewStart 페이지의 content script가 조회할 수 있게 응답한다.
 * 절대 보관하지 않음: 화면의 어떤 텍스트·입력값·URL 쿼리·쿠키·세션 정보도 저장하지 않는다.
 * (전송 payload 자체가 {step, substate, evidence categories, version}로 구성 — §6)
 */
'use strict';

var VERSION = '0.4.0';
var STORE_KEY = 'lastFssStep';
var STALE_MS = 10 * 60 * 1000; /* 10분 이상 지난 판별은 만료 취급 */

function loadLast(cb) {
  chrome.storage.session.get(STORE_KEY, function (ses) {
    if (chrome.runtime.lastError || !ses || !ses[STORE_KEY]) {
      chrome.storage.local.get(STORE_KEY, function (loc) {
        cb((loc && loc[STORE_KEY]) || null);
      });
      return;
    }
    cb(ses[STORE_KEY]);
  });
}

function saveLast(rec) {
  var obj = {};
  obj[STORE_KEY] = rec;
  chrome.storage.session.set(obj, function () {
    if (chrome.runtime.lastError) chrome.storage.local.set(obj, function () {});
  });
  obj = null;
}

chrome.runtime.onMessage.addListener(function (msg, sender, sendResponse) {
  if (!msg || typeof msg !== 'object') return false;

  if (msg.type === 'FSS_STEP') {
    /* sender가 FSS content script(fss.or.kr matches)에서만 수신 인정. */
    var url = sender.tab && sender.tab.url ? sender.tab.url : '';
    if (url.indexOf('https://www.fss.or.kr/') === 0) {
      saveLast({
        step: msg.step,
        substate: msg.substate || null,
        evidence: Array.isArray(msg.evidence) ? msg.evidence.slice(0, 6) : [],
        version: msg.version || VERSION,
        ts: Date.now()
      });
    }
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'GET_STATUS') {
    loadLast(function (last) {
      if (last && Date.now() - last.ts > STALE_MS) last = null;
      sendResponse({ installed: true, version: VERSION, last: last });
    });
    return true; /* async sendResponse */
  }

  if (msg.type === 'OPEN_NEWSTART') {
    /* 사용자가 패널에서 직접 클릭하는 이동 보조. 클릭 자동화 아님(§12). */
    chrome.tabs.create({ url: 'https://feel2real.co.kr/newstart/fss-helper' });
    sendResponse({ ok: true });
    return false;
  }

  return false;
});
