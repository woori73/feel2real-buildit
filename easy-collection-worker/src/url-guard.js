/**
 * URL 검증 — 임의 URL 접근 및 내부망 접근 차단.
 *
 * P1에서는 허용된 패턴(로컬 테스트 사이트)만 접근 가능하다.
 */
const { URL } = require('url');

function isUrlAllowed(urlString, allowedPatterns) {
  let url;
  try {
    url = new URL(urlString);
  } catch {
    return { allowed: false, reason: '잘못된 URL 형식입니다.' };
  }

  // http/https만 허용
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    return { allowed: false, reason: 'http/https만 허용됩니다.' };
  }

  // 허용 패턴 매칭
  for (const pattern of allowedPatterns) {
    if (matchPattern(urlString, pattern.trim())) {
      return { allowed: true };
    }
  }
  return { allowed: false, reason: '허용되지 않은 URL입니다.' };
}

function matchPattern(urlString, pattern) {
  // 간단한 와일드카드 매칭: * -> .*
  const regexStr = pattern
    .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
    .replace(/\*/g, '.*');
  return new RegExp('^' + regexStr).test(urlString);
}

module.exports = { isUrlAllowed };
