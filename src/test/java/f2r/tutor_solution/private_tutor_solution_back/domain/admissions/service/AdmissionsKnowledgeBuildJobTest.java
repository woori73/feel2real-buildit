package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("지식 항목 생성 백그라운드 잡 — 진행 상태·중복 실행 차단·오류 기록")
class AdmissionsKnowledgeBuildJobTest {

    @Test
    @DisplayName("given 생성이 단계별로 진행됨 when 실행하면 then 완료 상태와 건수·단계 진행이 남는다")
    void completesWithCounts() {
        // given
        AdmissionsKnowledgeBuildService service = mock(AdmissionsKnowledgeBuildService.class);
        when(service.buildAll(any(AdmissionsKnowledgeBuildService.Progress.class))).thenAnswer(inv -> {
            AdmissionsKnowledgeBuildService.Progress p = inv.getArgument(0);
            p.phase("지난해 입시결과(대학별)", 2);
            p.step(3, 0, 0);
            p.step(5, 1, 0);
            return new AdmissionsKnowledgeBuildService.BuildResult(5, 1, 0, List.of(1L, 2L));
        });
        AdmissionsKnowledgeBuildJob job = new AdmissionsKnowledgeBuildJob(service, Runnable::run);

        // when
        var outcome = job.start();

        // then
        assertThat(outcome).isEqualTo(AdmissionsKnowledgeBuildJob.StartOutcome.STARTED);
        @SuppressWarnings("unchecked") Map<String, Object> run = (Map<String, Object>) job.status().get("lastRun");
        assertThat(run).containsEntry("result", "COMPLETED").containsEntry("created", 5).containsEntry("updated", 1)
                .containsEntry("phase", "지난해 입시결과(대학별)").containsEntry("phaseTotal", 2).containsEntry("phaseDone", 2);
        assertThat(job.isRunning()).isFalse();
    }

    @Test
    @DisplayName("given 실행 중 when 다시 시작하면 then ALREADY_RUNNING")
    void secondStartIsRejected() {
        // given
        List<Runnable> pending = new ArrayList<>();
        AdmissionsKnowledgeBuildJob job = new AdmissionsKnowledgeBuildJob(mock(AdmissionsKnowledgeBuildService.class), pending::add);
        job.start();

        // when / then
        assertThat(job.start()).isEqualTo(AdmissionsKnowledgeBuildJob.StartOutcome.ALREADY_RUNNING);
    }

    @Test
    @DisplayName("given 생성 중 예외 when 실행하면 then ERROR와 사유가 남고 다시 시작할 수 있다")
    void errorIsRecordedAndLockReleased() {
        // given
        AdmissionsKnowledgeBuildService service = mock(AdmissionsKnowledgeBuildService.class);
        when(service.buildAll(any(AdmissionsKnowledgeBuildService.Progress.class))).thenThrow(new IllegalStateException("db down"));
        AdmissionsKnowledgeBuildJob job = new AdmissionsKnowledgeBuildJob(service, Runnable::run);

        // when
        job.start();

        // then
        @SuppressWarnings("unchecked") Map<String, Object> run = (Map<String, Object>) job.status().get("lastRun");
        assertThat(run).containsEntry("result", "ERROR");
        assertThat((String) run.get("error")).contains("db down");
        assertThat(job.isRunning()).isFalse();
    }
}
