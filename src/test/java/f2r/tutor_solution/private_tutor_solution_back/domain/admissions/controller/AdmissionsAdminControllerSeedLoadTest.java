package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsDataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 시드 적재 중복 실행 차단: 적재가 도는 동안 들어온 두 번째 요청은 409로 거절하고, 끝나면 다시 받는다. */
class AdmissionsAdminControllerSeedLoadTest {

    private static final AdmissionsDataLoader.LoadResult RESULT =
            new AdmissionsDataLoader.LoadResult(1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0);

    private static AdmissionsAdminController controller(AdmissionsDataLoader loader) {
        return new AdmissionsAdminController(null, null, loader, null, null, null, null, null);
    }

    @Test
    @DisplayName("given 적재가 진행 중 when 두 번째 적재 요청이 오면 then 409 ALREADY_RUNNING이고 적재기는 한 번만 호출된다")
    void secondRequestWhileLoading_isRejected() throws Exception {
        // given: 첫 적재가 latch에서 멈춰 있다
        AdmissionsDataLoader loader = mock(AdmissionsDataLoader.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(loader.loadSeed(anyString())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return RESULT;
        });
        AdmissionsAdminController controller = controller(loader);
        CompletableFuture<ResponseEntity<Map<String, Object>>> first =
                CompletableFuture.supplyAsync(() -> controller.loadSeed(Map.of("seedJson", "{}")));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        ResponseEntity<Map<String, Object>> second = controller.loadSeed(Map.of("seedJson", "{}"));
        release.countDown();

        // then
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(second.getBody()).containsEntry("status", "ALREADY_RUNNING");
        assertThat(first.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        verify(loader, times(1)).loadSeed(anyString());
    }

    @Test
    @DisplayName("given 앞선 적재가 실패로 끝남 when 다시 적재하면 then 잠금이 풀려 있어 정상 처리된다")
    void afterFailure_lockIsReleased() throws Exception {
        // given
        AdmissionsDataLoader loader = mock(AdmissionsDataLoader.class);
        when(loader.loadSeed(anyString())).thenThrow(new RuntimeException("db error")).thenReturn(RESULT);
        AdmissionsAdminController controller = controller(loader);
        assertThat(controller.loadSeed(Map.of("seedJson", "{}")).getStatusCode().value()).isEqualTo(500);

        // when
        ResponseEntity<Map<String, Object>> again = controller.loadSeed(Map.of("seedJson", "{}"));

        // then
        assertThat(again.getStatusCode().value()).isEqualTo(200);
    }
}
