package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdmissionsRateLimiter 단위 테스트 — 헤더 위조 방지·요청 제한·동시 요청.
 * given/when/then 구조. 시간은 AtomicLong 기반 가짜 클럭으로 제어한다.
 */
class AdmissionsRateLimiterTest {

    private AtomicLong now;

    private AdmissionsRateLimiter limiter(int perMinute, int perHour, int maxIps, String proxies) {
        return new AdmissionsRateLimiter(perMinute, perHour, maxIps, proxies, now::get);
    }

    private HttpServletRequest request(String remoteAddr, String xff) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        return req;
    }

    // ---------- X-Forwarded-For 신뢰 경계 ----------

    @Test
    @DisplayName("XFF — 신뢰 프록시 미설정 시 위조 헤더 무시, remoteAddr 사용")
    void resolveClientIp_ignoresSpoofedXffByDefault() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "");

        // when — 공격자가 XFF를 위조해도
        String ip = limiter.resolveClientIp(request("203.0.113.9", "1.2.3.4"));

        // then — 실제 접속 IP(remoteAddr)를 사용한다
        assertEquals("203.0.113.9", ip);
    }

    @Test
    @DisplayName("XFF — 신뢰 프록시에서 온 요청은 첫 번째 항목 신뢰")
    void resolveClientIp_trustsXffFromTrustedProxy() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "10.0.0.5");

        // when
        String ip = limiter.resolveClientIp(request("10.0.0.5", "1.2.3.4, 10.0.0.6"));

        // then
        assertEquals("1.2.3.4", ip);
    }

    @Test
    @DisplayName("XFF — CIDR 대역의 프록시도 신뢰")
    void resolveClientIp_trustsProxyByCidr() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "10.0.0.0/8");

        // when
        String ip = limiter.resolveClientIp(request("10.1.2.3", "1.2.3.4"));

        // then
        assertEquals("1.2.3.4", ip);
    }

    @Test
    @DisplayName("XFF — CIDR 대역 밖 프록시는 불신, remoteAddr 사용")
    void resolveClientIp_rejectsProxyOutsideCidr() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "10.0.0.0/8");

        // when
        String ip = limiter.resolveClientIp(request("192.168.1.1", "1.2.3.4"));

        // then
        assertEquals("192.168.1.1", ip);
    }

    @Test
    @DisplayName("XFF — 첫 항목이 IP가 아니면 remoteAddr로 폴백")
    void resolveClientIp_fallsBackOnInvalidXff() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "10.0.0.5");

        // when
        String ip = limiter.resolveClientIp(request("10.0.0.5", "not-an-ip, 1.2.3.4"));

        // then
        assertEquals("10.0.0.5", ip);
    }

    @Test
    @DisplayName("XFF — 헤더가 비어 있으면 remoteAddr 사용")
    void resolveClientIp_fallsBackOnEmptyXff() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "10.0.0.5");

        // when
        String ip = limiter.resolveClientIp(request("10.0.0.5", "   "));

        // then
        assertEquals("10.0.0.5", ip);
    }

    @Test
    @DisplayName("XFF — IPv6 프록시 정확 매칭")
    void resolveClientIp_trustsIpv6Proxy() {
        // given
        now = new AtomicLong(0);
        var limiter = limiter(10, 60, 100, "2001:db8::1");

        // when
        String ip = limiter.resolveClientIp(request("2001:db8::1", "2001:db8::99"));

        // then
        assertEquals("2001:db8::99", ip);
    }

    // ---------- 요청 제한 ----------

    @Test
    @DisplayName("rate limit — 분당 10회 초과 시 차단")
    void tryAcquire_blocksAfterPerMinute() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(10, 1000, 100, "");

        // when — 10회는 통과
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("192.168.1.100"), "10회까지 통과 (i=" + i + ")");
        }

        // then — 11회째 차단
        assertFalse(limiter.tryAcquire("192.168.1.100"), "11회째는 차단");
    }

    @Test
    @DisplayName("rate limit — 1분 경과 후 윈도우 이동, 다시 허용")
    void tryAcquire_windowSlidesAfterMinute() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(2, 1000, 100, "");
        assertTrue(limiter.tryAcquire("10.0.0.9"));
        assertTrue(limiter.tryAcquire("10.0.0.9"));
        assertFalse(limiter.tryAcquire("10.0.0.9"));

        // when — 61초 경과
        now.addAndGet(61_000L);

        // then — 다시 허용
        assertTrue(limiter.tryAcquire("10.0.0.9"), "윈도우 이동 후 허용되어야 함");
    }

    @Test
    @DisplayName("rate limit — 시간당 60회 초과 시 차단, 1시간 경과 후 해제")
    void tryAcquire_blocksAfterPerHour() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(10_000, 5, 100, "");

        // when — 5회는 통과
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("10.0.0.8"), "5회까지 통과 (i=" + i + ")");
        }

        // then — 6회째 차단
        assertFalse(limiter.tryAcquire("10.0.0.8"), "6회째는 차단");

        // when — 61분 경과
        now.addAndGet(61 * 60_000L);

        // then — 다시 허용
        assertTrue(limiter.tryAcquire("10.0.0.8"), "1시간 경과 후 허용되어야 함");
    }

    @Test
    @DisplayName("rate limit — IP별로 독립 계산")
    void tryAcquire_independentPerIp() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(10, 1000, 100, "");
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("10.0.0.1");
        }

        // when / then — 다른 IP는 영향 없음
        assertTrue(limiter.tryAcquire("10.0.0.2"), "다른 IP는 통과해야 함");
    }

    @Test
    @DisplayName("rate limit — 차단된 요청은 기록되지 않음")
    void tryAcquire_blockedNotRecorded() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(1, 1000, 100, "");
        assertTrue(limiter.tryAcquire("10.0.0.7"));
        assertFalse(limiter.tryAcquire("10.0.0.7")); // 차단 — 기록 안 됨

        // when — 61초 경과
        now.addAndGet(61_000L);

        // then — 기록된 1건만 윈도우 밖으로 이동 → 허용
        assertTrue(limiter.tryAcquire("10.0.0.7"));
    }

    // ---------- 무한 증가 방지 ----------

    @Test
    @DisplayName("capacity — 추적 IP 수 상한 유지 (LRU 퇴출)")
    void trackedIps_boundedByMax() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(10, 1000, 5, "");

        // when — 상한(5)보다 많은 IP에서 요청
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryAcquire("192.168.9." + i));
        }

        // then — 추적 IP 수가 상한을 넘지 않음
        assertTrue(limiter.trackedIpCount() <= 5,
                "추적 IP 상한 초과: " + limiter.trackedIpCount());
    }

    @Test
    @DisplayName("sweep — 1시간 미활동 IP 항목 정리")
    void idleEntries_sweptAfterOneHour() {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(10, 1000, 100, "");
        limiter.tryAcquire("10.1.0.1");
        limiter.tryAcquire("10.1.0.2");
        limiter.tryAcquire("10.1.0.3");
        assertEquals(3, limiter.trackedIpCount());

        // when — 2시간 경과 후 새 요청 (sweep 유발)
        now.addAndGet(2 * 3_600_000L);
        limiter.tryAcquire("10.9.9.9");

        // then — 미활동 항목 정리됨
        assertEquals(1, limiter.trackedIpCount(), "미활동 IP가 정리되어야 함");
    }

    // ---------- 동시 요청 ----------

    @Test
    @DisplayName("concurrency — 동시 요청에서도 제한 초과 허용 없음")
    void tryAcquire_threadSafe() throws Exception {
        // given
        now = new AtomicLong(1_000_000L);
        var limiter = limiter(10, 1000, 100, "");
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    if (limiter.tryAcquire("9.9.9.9")) {
                        allowed.incrementAndGet();
                    }
                    return null;
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "스레드 준비 시간 초과");

            // when — 50개 스레드 동시 요청
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "스레드 종료 시간 초과");

            // then — 정확히 10건만 허용 (초과 허용 없음)
            assertEquals(10, allowed.get(), "동시 요청에서도 제한을 초과하면 안 됨");
        } finally {
            pool.shutdownNow();
        }
    }
}
