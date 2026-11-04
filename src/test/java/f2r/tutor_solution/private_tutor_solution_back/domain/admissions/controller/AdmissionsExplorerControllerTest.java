package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsExplorerService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionsExplorerControllerTest {
    @Test void givenPublicSwitchOff_whenReading_then503BeforeAnyDatabaseOrRateLimit() {
        var service=mock(AdmissionsExplorerService.class);var limiter=mock(AdmissionsRateLimiter.class);
        var controller=new AdmissionsExplorerController(service,limiter,false);
        assertThatThrownBy(()->controller.universities(2027,mock(HttpServletRequest.class)))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));
        verifyNoInteractions(service,limiter);
    }
    @Test void givenRateLimitExceeded_whenReading_then429AndNoDatabase() {
        var service=mock(AdmissionsExplorerService.class);var limiter=mock(AdmissionsRateLimiter.class);
        when(limiter.resolveClientIp(any())).thenReturn("192.0.2.1");
        var controller=new AdmissionsExplorerController(service,limiter,true);
        assertThatThrownBy(()->controller.universities(2027,mock(HttpServletRequest.class)))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(429));
        verifyNoInteractions(service);
    }
    @Test void givenEnabledApprovedCatalogue_whenReading_thenNoStoreAndNoAiDailyCharge() {
        var service=mock(AdmissionsExplorerService.class);var limiter=mock(AdmissionsRateLimiter.class);
        when(limiter.resolveClientIp(any())).thenReturn("192.0.2.1");when(limiter.tryAcquire("explore-192.0.2.1")).thenReturn(true);
        when(service.universities(2027)).thenReturn(List.of());
        var response=new AdmissionsExplorerController(service,limiter,true).universities(2027,mock(HttpServletRequest.class));
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(limiter,never()).tryAcquireDaily();
    }

    @Test void givenMemberScoreRequest_whenCalculating_thenReusesEngineAfterCurrentAccountCheck() {
        var access=mock(f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsMemberAccess.class);
        var service=mock(AdmissionsExplorerService.class);
        var scores=mock(f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService.class);
        var limiter=mock(AdmissionsRateLimiter.class);
        var p=new f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal(1L,"teacher","TUTOR");
        when(access.token(p)).thenReturn("member_fixture");when(limiter.tryAcquire("score-member-1")).thenReturn(true);
        var input=java.util.Map.of("KOR_STD",131.0);
        var req=new AdmissionsJeongsiController.ScoreRequest("테스트대학교",2027,null,"컴퓨터공학과",input);
        var response=new AdmissionsMemberExplorerController(access,service,scores,limiter).score(p,req);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var order=inOrder(access,scores);order.verify(access).token(p);order.verify(scores).calculate("테스트대학교",2027,null,"컴퓨터공학과",input,null);
        verify(limiter,never()).tryAcquireDaily();
    }
    @Test void givenInvalidMember_whenCalculating_thenEngineIsNeverReached() {
        var access=mock(f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsMemberAccess.class);
        var service=mock(AdmissionsExplorerService.class);
        var scores=mock(f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService.class);
        var limiter=mock(AdmissionsRateLimiter.class);
        when(access.token(null)).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        assertThatThrownBy(()->new AdmissionsMemberExplorerController(access,service,scores,limiter).score(null,null))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(scores,service,limiter);
    }
}
