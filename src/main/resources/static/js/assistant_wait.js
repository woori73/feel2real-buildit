/*
 * 대화형 화면의 'AI 응답 대기' 규칙(순수 함수). 브라우저에서는 window.AssistantWait, 테스트에서는 require로 쓴다.
 * - 8초부터 "오래 걸리는 중"을, 30초부터 "자동으로 멈출 수 있음"을 안내한다.
 * - 한도(기본 100초)를 넘기면 기다리기를 멈추고 서버 상태를 다시 읽는다(작업 내용은 서버 작업본이 정본이다).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.AssistantWait = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';
  var SLOW_MS = 8000;
  var VERY_SLOW_MS = 30000;
  var DEFAULT_TIMEOUT_MS = 100000;

  function noticeFor(elapsedMs) {
    var s = Math.floor(elapsedMs / 1000);
    if (elapsedMs < SLOW_MS) return '';
    if (elapsedMs < VERY_SLOW_MS) return 'AI 응답이 평소보다 오래 걸리고 있어요 (' + s + '초). 작업 내용은 그대로 안전해요.';
    return 'AI가 아직 답하는 중이에요 (' + s + '초). 너무 오래 걸리면 자동으로 멈추고 작업 내용은 그대로 둘게요.';
  }

  /** 테스트·운영 점검용 재정의 값(양수일 때만)을 반영한 대기 한도(ms). */
  function timeoutMs(override) {
    var n = Number(override);
    return n > 0 ? n : DEFAULT_TIMEOUT_MS;
  }

  function timeoutMessage() {
    return '응답이 너무 늦어 기다리기를 멈췄어요. 작업 내용은 그대로예요. 잠시 후 다시 시도해 주세요.';
  }

  function lateAppliedMessage() {
    return '응답이 늦었지만 수정이 이미 끝나 있었어요. 미리보기를 확인해 보세요. 마음에 들지 않으면 되돌리기를 눌러 주세요.';
  }

  return {
    SLOW_MS: SLOW_MS, VERY_SLOW_MS: VERY_SLOW_MS, DEFAULT_TIMEOUT_MS: DEFAULT_TIMEOUT_MS,
    noticeFor: noticeFor, timeoutMs: timeoutMs, timeoutMessage: timeoutMessage, lateAppliedMessage: lateAppliedMessage
  };
});
