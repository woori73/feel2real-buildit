package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsSusiMinimumService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 수능최저 확인 공개 API: 스위치·rate limit·요청 오류 처리. */
class AdmissionsSusiMinimumControllerTest {

    private static final AdmissionsSusiMinimumController.CheckRequest REQ =
            new AdmissionsSusiMinimumController.CheckRequest("한양대학교(서울)", 2027, null, "의예과", Map.of("KOR_GRD", 1.0));

    private static AdmissionsRateLimiter limiter(int perMinute) {
        return new AdmissionsRateLimiter(perMinute, 60, 100, "", 500);
    }

    @Test
    @DisplayName("given 공개 API 스위치 OFF when 요청하면 then 503이고 확인하지 않는다")
    void disabled_returns503() {
        // given
        var service = mock(AdmissionsSusiMinimumService.class);
        var controller = new AdmissionsSusiMinimumController(service, limiter(10), false);
        // when
        var r = controller.check(REQ, new MockHttpServletRequest());
        // then
        assertThat(r.getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("given 분당 한도 1회 when 두 번 요청하면 then 두 번째는 429다")
    void rateLimited_returns429() {
        // given
        var service = mock(AdmissionsSusiMinimumService.class);
        when(service.check(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(new AdmissionsSusiMinimumService.CheckResult("한양대학교(서울)", 2027, List.of(), "안내"));
        var controller = new AdmissionsSusiMinimumController(service, limiter(1), true);
        // when
        var first = controller.check(REQ, new MockHttpServletRequest());
        var second = controller.check(REQ, new MockHttpServletRequest());
        // then
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(429);
    }

    @Test
    @DisplayName("given 잘못된 입력·2027이 아닌 학년도 when 요청하면 then 400이다")
    void invalid_returns400() {
        // given
        var service = mock(AdmissionsSusiMinimumService.class);
        when(service.check(anyString(), anyInt(), any(), any(), any()))
                .thenThrow(new AdmissionsJeongsiScoreService.InvalidScoreRequestException("수능최저 확인에 쓰지 않는 입력 항목: KOR_STD"));
        var controller = new AdmissionsSusiMinimumController(service, limiter(10), true);
        // when
        var bad = controller.check(REQ, new MockHttpServletRequest());
        var wrongYear = controller.check(new AdmissionsSusiMinimumController.CheckRequest("한양대학교(서울)", 2028, null, null, Map.of()),
                new MockHttpServletRequest());
        // then
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(wrongYear.getStatusCode().value()).isEqualTo(400);
    }
}
