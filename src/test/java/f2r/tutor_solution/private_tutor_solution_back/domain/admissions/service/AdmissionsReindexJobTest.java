package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("임베딩 재색인 백그라운드 잡")
class AdmissionsReindexJobTest {

    private AdmissionsEmbeddingService service;
    private AdmissionsVectorIndex vectorIndex;
    private AdmissionsEmbeddingProperties props;
    private final Set<Long> embedded = new HashSet<>();

    @BeforeEach
    void setUp() {
        service = mock(AdmissionsEmbeddingService.class);
        vectorIndex = mock(AdmissionsVectorIndex.class);
        props = new AdmissionsEmbeddingProperties();
        props.setEnabled(true);
        props.setIndexPauseMs(0);
        when(service.activeIds()).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(service.allIds()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        when(service.embeddedIds()).thenAnswer(inv -> new HashSet<>(embedded));
    }

    private AdmissionsReindexJob syncJob() {
        return new AdmissionsReindexJob(service, props, vectorIndex, Runnable::run);
    }

    @Test
    @DisplayName("given 임베딩이 없는 ACTIVE 4건 when MISSING 시작하면 then 모두 처리하고 완료 후 인덱스를 한 번 갱신한다")
    void missing_processesAllAndReloadsOnce() {
        // given
        when(service.embedItem(anyLong())).thenAnswer(inv -> { embedded.add(inv.getArgument(0)); return null; });
        var job = syncJob();

        // when
        var outcome = job.start(AdmissionsReindexJob.Mode.MISSING);

        // then
        assertThat(outcome).isEqualTo(AdmissionsReindexJob.StartOutcome.STARTED);
        var st = job.status();
        assertThat(st).containsEntry("total", 4).containsEntry("done", 4).containsEntry("missing", 0)
                .containsEntry("running", false);
        @SuppressWarnings("unchecked") var last = (java.util.Map<String, Object>) st.get("lastRun");
        assertThat(last).containsEntry("result", "COMPLETED").containsEntry("embedded", 4).containsEntry("failed", 0);
        verify(vectorIndex, org.mockito.Mockito.times(1)).reload();
    }

    @Test
    @DisplayName("given 이미 임베딩된 항목 when MISSING 시작하면 then 그 항목은 다시 임베딩하지 않는다(중복 방지)")
    void missing_skipsAlreadyEmbedded() {
        // given: 1,2 완료
        embedded.addAll(List.of(1L, 2L));
        when(service.embedItem(anyLong())).thenAnswer(inv -> { embedded.add(inv.getArgument(0)); return null; });

        // when
        syncJob().start(AdmissionsReindexJob.Mode.MISSING);

        // then
        verify(service, never()).embedItem(1L);
        verify(service, never()).embedItem(2L);
        verify(service).embedItem(3L);
        verify(service).embedItem(4L);
    }

    @Test
    @DisplayName("given 전부 완료 when MISSING 시작하면 then NOTHING_TO_DO이고 실행 상태가 남지 않는다")
    void missing_nothingToDo() {
        // given
        embedded.addAll(List.of(1L, 2L, 3L, 4L));
        var job = syncJob();

        // when
        var outcome = job.start(AdmissionsReindexJob.Mode.MISSING);

        // then
        assertThat(outcome).isEqualTo(AdmissionsReindexJob.StartOutcome.NOTHING_TO_DO);
        assertThat(job.isRunning()).isFalse();
        verify(service, never()).embedItem(anyLong());
        verify(vectorIndex, never()).reload();
    }

    @Test
    @DisplayName("given 일부 항목 실패 when 실행하면 then 실패 건수·사유를 기록하고, 성공분만 done에 반영되며 재시작(MISSING)으로 실패분만 재시도한다")
    void partialFailure_recordedAndRetriedOnlyForFailed() {
        // given: 3번만 실패
        when(service.embedItem(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            if (id == 3L) return "타임아웃 60000ms";
            embedded.add(id);
            return null;
        });
        var job = syncJob();

        // when
        job.start(AdmissionsReindexJob.Mode.MISSING);

        // then
        var st = job.status();
        assertThat(st).containsEntry("done", 3).containsEntry("missing", 1);
        @SuppressWarnings("unchecked") var last = (java.util.Map<String, Object>) st.get("lastRun");
        assertThat(last).containsEntry("failed", 1).containsEntry("embedded", 3);
        @SuppressWarnings("unchecked") var failures = (java.util.Map<Long, String>) last.get("failures");
        assertThat(failures).containsEntry(3L, "타임아웃 60000ms");

        // when: 서버 복구 후 재시도
        when(service.embedItem(3L)).thenAnswer(inv -> { embedded.add(3L); return null; });
        var retry = job.start(AdmissionsReindexJob.Mode.MISSING);

        // then: 3번만 다시 시도
        assertThat(retry).isEqualTo(AdmissionsReindexJob.StartOutcome.STARTED);
        verify(service, org.mockito.Mockito.times(2)).embedItem(3L);
        verify(service, org.mockito.Mockito.times(1)).embedItem(1L);
        assertThat(job.status()).containsEntry("missing", 0);
    }

    @Test
    @DisplayName("given 실행 중인 잡 when 다시 시작하면 then ALREADY_RUNNING이고 두 번째 실행이 만들어지지 않는다")
    void duplicateStart_isRejectedWhileRunning() {
        // given: 실행기가 작업을 보류 → running 유지
        List<Runnable> pending = new ArrayList<>();
        Executor held = pending::add;
        var job = new AdmissionsReindexJob(service, props, vectorIndex, held);
        when(service.embedItem(anyLong())).thenAnswer(inv -> { embedded.add(inv.getArgument(0)); return null; });

        // when
        var first = job.start(AdmissionsReindexJob.Mode.MISSING);
        var second = job.start(AdmissionsReindexJob.Mode.MISSING);
        var all = job.start(AdmissionsReindexJob.Mode.ALL);

        // then
        assertThat(first).isEqualTo(AdmissionsReindexJob.StartOutcome.STARTED);
        assertThat(second).isEqualTo(AdmissionsReindexJob.StartOutcome.ALREADY_RUNNING);
        assertThat(all).isEqualTo(AdmissionsReindexJob.StartOutcome.ALREADY_RUNNING);
        assertThat(pending).hasSize(1);
        assertThat(job.status()).containsEntry("running", true);

        // when: 보류된 작업이 끝나면
        pending.get(0).run();

        // then: 다시 시작 가능
        assertThat(job.isRunning()).isFalse();
        assertThat(job.start(AdmissionsReindexJob.Mode.MISSING)).isEqualTo(AdmissionsReindexJob.StartOutcome.NOTHING_TO_DO);
    }

    @Test
    @DisplayName("given 실행 중 when 중단 요청하면 then 현재 항목까지 마치고 CANCELLED로 끝난다")
    void cancel_stopsAfterCurrentItem() {
        // given: 2번째 항목 처리 중 중단 요청
        var holder = new AdmissionsReindexJob[1];
        when(service.embedItem(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            embedded.add(id);
            if (id == 2L) holder[0].cancel();
            return null;
        });
        holder[0] = syncJob();

        // when
        holder[0].start(AdmissionsReindexJob.Mode.MISSING);

        // then
        verify(service, never()).embedItem(3L);
        @SuppressWarnings("unchecked") var last = (java.util.Map<String, Object>) holder[0].status().get("lastRun");
        assertThat(last).containsEntry("result", "CANCELLED").containsEntry("embedded", 2);
        assertThat(holder[0].isRunning()).isFalse();
        verify(vectorIndex).reload();
    }

    @Test
    @DisplayName("given 새 잡 인스턴스(앱 재시작) when 상태를 조회하면 then 이전 실행 기록은 없지만 DB 기준 done/missing은 유지된다")
    void statusAfterRestart_isDerivedFromDb() {
        // given: 1,2 완료된 채 앱이 재시작된 상황
        embedded.addAll(List.of(1L, 2L));

        // when
        var st = syncJob().status();

        // then
        assertThat(st).containsEntry("total", 4).containsEntry("done", 2).containsEntry("missing", 2)
                .containsEntry("running", false);
        assertThat(st.get("lastRun")).isNull();
    }

    @Test
    @DisplayName("given 임베딩 비활성 when 시작하면 then 호출 없이 전부 실패로 기록한다")
    void disabled_failsWithoutCalling() {
        // given
        props.setEnabled(false);
        var job = syncJob();

        // when
        job.start(AdmissionsReindexJob.Mode.MISSING);

        // then
        verify(service, never()).embedItem(anyLong());
        @SuppressWarnings("unchecked") var last = (java.util.Map<String, Object>) job.status().get("lastRun");
        assertThat(last).containsEntry("result", "ERROR").containsEntry("failed", 4);
    }
}
