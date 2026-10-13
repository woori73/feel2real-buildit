package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdmissionsChatController rate limit 단위 테스트.
 * given/when/then 구조.
 */
class AdmissionsChatControllerRateLimitTest {

    private AdmissionsChatController controller() {
        AdmissionsChatService mockService = mock(AdmissionsChatService.class);
        return new AdmissionsChatController(mockService);
    }

    private HttpServletRequest mockRequest(String ip) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn(ip);
        return req;
    }

    @Test
    @DisplayName("rate limit — 분당 10회 초과 시 차단")
    void checkRateLimit_blocksAfter10PerMinute() {
        // given
        var controller = controller();
        String ip = "192.168.1.100";

        // when — 10회는 통과
        for (int i = 0; i < 10; i++) {
            assertTrue(controller.checkRateLimit(ip), "10회까지 통과해야 함 (i=" + i + ")");
        }

        // then — 11회째는 차단
        assertFalse(controller.checkRateLimit(ip), "11회째는 차단되어야 함");
    }

    @Test
    @DisplayName("rate limit — 다른 IP는 독립적으로 계산")
    void checkRateLimit_independentPerIp() {
        // given
        var controller = controller();

        // when — IP1에서 10회 소진
        for (int i = 0; i < 10; i++) {
            controller.checkRateLimit("10.0.0.1");
        }

        // then — IP2는 여전히 통과
        assertTrue(controller.checkRateLimit("10.0.0.2"), "다른 IP는 영향받지 않아야 함");
    }

    @Test
    @DisplayName("chat — 학년도 검증 (2027/2028만 허용)")
    void chat_rejectsInvalidAcademicYear() {
        // given
        var controller = controller();
        var request = new AdmissionsChatController.ChatRequest("질문", 2026, null);

        // when
        var response = controller.chat(request, mockRequest("127.0.0.1"));

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("error", response.getBody().status());
    }

    @Test
    @DisplayName("chat — rate limit 초과 시 429")
    void chat_returns429WhenRateLimited() {
        // given
        var controller = controller();
        String ip = "192.168.1.200";
        for (int i = 0; i < 10; i++) {
            controller.checkRateLimit(ip);
        }
        var request = new AdmissionsChatController.ChatRequest("질문", null, null);

        // when
        var response = controller.chat(request, mockRequest(ip));

        // then
        assertEquals(429, response.getStatusCode().value());
    }
}
