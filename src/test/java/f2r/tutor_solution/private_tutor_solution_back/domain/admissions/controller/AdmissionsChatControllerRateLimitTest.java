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
}
