package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdmissionsChatController 단위 테스트 — rate limit 연동·XFF 처리·입력 검증.
 * given/when/then 구조.
 */
class AdmissionsChatControllerRateLimitTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private AdmissionsRateLimiter limiter(String trustedProxies) {
        return new AdmissionsRateLimiter(10, 60, 10_000, trustedProxies, now::get);
    }

    private AdmissionsChatController controller(AdmissionsRateLimiter limiter) {
        AdmissionsChatService mockService = mock(AdmissionsChatService.class);
        return new AdmissionsChatController(mockService, limiter);
    }

    private HttpServletRequest mockRequest(String remoteAddr, String xff) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        return req;
    }

    @Test
    @DisplayName("chat — rate limit 초과 시 429")
    void chat_returns429WhenRateLimited() {
        // given — 분당 10회 소진
        var limiter = limiter("");
        var controller = controller(limiter);
        String ip = "192.168.1.200";
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(ip));
        }
        var request = new AdmissionsChatController.ChatRequest("질문", null, null);

        // when
        var response = controller.chat(request, mockRequest(ip, null));

        // then
        assertEquals(429, response.getStatusCode().value());
        assertEquals("error", response.getBody().status());
    }

    @Test
    @DisplayName("chat — 위조 XFF 무시, remoteAddr 기준으로 제한")
    void chat_ignoresSpoofedXff() {
        // given — 신뢰 프록시 미설정: XFF 위조해도 remoteAddr 기준
        var limiter = limiter("");
        var controller = controller(limiter);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("203.0.113.7"));
        }
        var request = new AdmissionsChatController.ChatRequest("질문", null, null);

        // when — 공격자가 다른 IP로 위장한 XFF 전송
        var response = controller.chat(request, mockRequest("203.0.113.7", "1.2.3.4"));

        // then — 위조 XFF가 아닌 실제 IP 기준으로 429
        assertEquals(429, response.getStatusCode().value());
    }

    @Test
    @DisplayName("chat — 신뢰 프록시의 XFF는 실제 클라이언트 IP로 제한")
    void chat_usesXffFromTrustedProxy() {
        // given — 10.0.0.5는 신뢰 프록시
        var limiter = limiter("10.0.0.5");
        var controller = controller(limiter);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("1.2.3.4"));
        }
        var request = new AdmissionsChatController.ChatRequest("질문", null, null);

        // when — 프록시를 거쳐 온 요청
        var response = controller.chat(request, mockRequest("10.0.0.5", "1.2.3.4"));

        // then — XFF IP 기준으로 429
        assertEquals(429, response.getStatusCode().value());
    }

    @Test
    @DisplayName("chat — 학년도 검증 (2027/2028만 허용)")
    void chat_rejectsInvalidAcademicYear() {
        // given
        var controller = controller(limiter(""));
        var request = new AdmissionsChatController.ChatRequest("질문", 2026, null);

        // when
        var response = controller.chat(request, mockRequest("127.0.0.1", null));

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("error", response.getBody().status());
    }

    // ---------- 공개 API 스위치(기본 OFF) · 전역 일일 상한 ----------

    @Test
    @DisplayName("chat — 공개 API가 꺼져 있으면 503이고 rate limit·서비스를 거치지 않는다")
    void chat_returns503WhenPublicApiDisabled() {
        // given
        var limiter = limiter("");
        AdmissionsChatService service = mock(AdmissionsChatService.class);
        var controller = new AdmissionsChatController(service, limiter, false);
        var request = new AdmissionsChatController.ChatRequest("질문", 2027, null);

        // when
        var response = controller.chat(request, mockRequest("203.0.113.1", null));

        // then
        assertEquals(503, response.getStatusCode().value());
        assertEquals("error", response.getBody().status());
        org.mockito.Mockito.verifyNoInteractions(service);
        assertEquals(0, limiter.trackedIpCount());
        assertEquals(0, limiter.dailyUsed());
    }

    @Test
    @DisplayName("chat — 전역 일일 상한을 넘으면 IP가 달라도 429")
    void chat_returns429WhenDailyLimitReached() {
        // given — 하루 2회, 서로 다른 IP 3개
        var limiter = new AdmissionsRateLimiter(10, 60, 10_000, "", 2, now::get);
        AdmissionsChatService service = mock(AdmissionsChatService.class);
        when(service.ask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AdmissionsChatService.AdmissionsAnswer(false, "", "테스트", null, java.util.List.of(), null, false));
        var controller = new AdmissionsChatController(service, limiter);
        var request = new AdmissionsChatController.ChatRequest("질문", null, null);

        // when
        controller.chat(request, mockRequest("198.51.100.1", null));
        controller.chat(request, mockRequest("198.51.100.2", null));
        var third = controller.chat(request, mockRequest("198.51.100.3", null));

        // then
        assertEquals(429, third.getStatusCode().value());
        assertEquals("error", third.getBody().status());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(2)).ask(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("chat — IP 제한에 걸린 요청은 전역 일일 카운트를 쓰지 않는다")
    void chat_ipLimitedRequestDoesNotConsumeDailyQuota() {
        // given — IP 분당 10회 소진
        var limiter = limiter("");
        var controller = controller(limiter);
        String ip = "192.0.2.77";
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(ip));
        }
        var request = new AdmissionsChatController.ChatRequest("질문", null, null);

        // when
        var response = controller.chat(request, mockRequest(ip, null));

        // then
        assertEquals(429, response.getStatusCode().value());
        assertEquals(0, limiter.dailyUsed());
    }
}
