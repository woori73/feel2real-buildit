/*
 * F2R 웹앱 service worker (2026-11-19).
 * 학생 기록·장부 같은 개인 자료는 절대 저장하지 않는다 — 모든 요청은 그대로 네트워크로 보낸다.
 * 화면 이동(navigate)이 인터넷 끊김으로 실패했을 때만 미리 넣어 둔 /offline.html을 보여 준다.
 */
const CACHE = 'f2r-offline-v1';
const OFFLINE_URL = '/offline.html';

function isNavigation(request) {
  return request.mode === 'navigate' && request.method === 'GET';
}

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.add(new Request(OFFLINE_URL, { cache: 'reload' }))));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
  if (!isNavigation(event.request)) return; // 화면 이동 말고는 손대지 않는다(브라우저 기본 동작).
  event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE_URL)));
});

if (typeof module === 'object' && module.exports) module.exports = { isNavigation, OFFLINE_URL, CACHE };
