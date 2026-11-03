package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 지식 항목 생성 백그라운드 작업: 시작은 바로 돌려주고, 한 번에 하나만 돌며, 결과·오류를 상태로 남긴다. */
class AdmissionsKnowledgeBuildJobTest {

    /** 넘겨받은 작업을 바로 실행하지 않고 모아 두는 실행기 — "실행 중" 상태를 재현한다. */
    private static final class QueuedExecutor implements java.util.concurrent.Executor {
        final List<Runnable> queued = new ArrayList<>();
        @Override public void execute(Runnable r) { queued.add(r); }
        void runAll() { queued.forEach(Runnable::run); queued.clear(); }
    }

    @Test
    @DisplayName("given 생성이 진행 중 when 다시 시작하면 then ALREADY_RUNNING이고 생성은 한 번만 돈다")
    void secondStartWhileRunning_isRejected() {
        // given
        AdmissionsKnowledgeBuildService service = mock(AdmissionsKnowledgeBuildService.class);
        when(service.buildAll()).thenReturn(new AdmissionsKnowledgeBuildService.BuildResult(3, 1, 10, List.of()));
        QueuedExecutor executor = new QueuedExecutor();
        AdmissionsKnowledgeBuildJob job = new AdmissionsKnowledgeBuildJob(service, executor);
        assertThat(job.start()).isEqualTo(AdmissionsKnowledgeBuildJob.StartOutcome.STARTED);

        // when
        var second = job.start();

        // then
        assertThat(second).isEqualTo(AdmissionsKnowledgeBuildJob.StartOutcome.ALREADY_RUNNING);
        assertThat(job.status()).containsEntry("running", true);
        verify(service, never()).buildAll();
        executor.runAll();
        verify(service).buildAll();
    }

    @Test
    @DisplayName("given 생성이 끝남 when 상태를 보면 then COMPLETED와 건수가 남고 다시 시작할 수 있다")
    void completedRun_reportsCountsAndReleases() {
        // given
        AdmissionsKnowledgeBuildService service = mock(AdmissionsKnowledgeBuildService.class);
        when(service.buildAll()).thenReturn(new AdmissionsKnowledgeBuildService.BuildResult(3, 1, 10, List.of()));
        QueuedExecutor executor = new QueuedExecutor();
        AdmissionsKnowledgeBuildJob job = new AdmissionsKnowledgeBuildJob(service, executor);
        job.start();

        // when
        executor.runAll();

        // then
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) job.status().get("lastRun");
        assertThat(job.status()).containsEntry("running", false);
        assertThat(last).containsEntry("result", "COMPLETED").containsEntry("created", 3)
                .containsEntry("updated", 1).containsEntry("unchanged", 10).containsKey("finishedAt");
        assertThat(job.start()).isEqualTo(AdmissionsKnowledgeBuildJob.StartOutcome.STARTED);
    }

    @Test
    @DisplayName("given 생성 중 오류 when 상태를 보면 then ERROR와 오류 종류가 남고 잠금이 풀린다")
    void failedRun_reportsErrorAndReleases() {
        // given
        AdmissionsKnowledgeBuildService service = mock(AdmissionsKnowledgeBuildService.class);
        when(service.buildAll()).thenThrow(new IllegalStateException("db"));
        QueuedExecutor executor = new QueuedExecutor();
        AdmissionsKnowledgeBuildJob job = new AdmissionsKnowledgeBuildJob(service, executor);
        job.start();

        // when
        executor.runAll();

        // then
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) job.status().get("lastRun");
        assertThat(last).containsEntry("result", "ERROR").containsEntry("error", "IllegalStateException");
        assertThat(job.isRunning()).isFalse();
    }
}
