package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("시드 URL 목록 적재 잡 — 주소 검사·순서 적재·실패 시 정지·중복 실행 차단")
class AdmissionsSeedLoadJobTest {

    private static final String SHA = "3514038".repeat(5) + "abcde"; // 40자
    private static final String BASE = "https://raw.githubusercontent.com/woori73/buildit-admissions/" + SHA + "/data/seed/prior_2026/";

    private static AdmissionsDataLoader.LoadResult result(int prior) {
        return new AdmissionsDataLoader.LoadResult(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, prior, 0);
    }

    @Test
    @DisplayName("given 고정 커밋 raw 주소 when 검사하면 then 통과하고, 다른 호스트·브랜치명·상위 경로는 거절한다")
    void validateUrls_allowsOnlyPinnedRawSeedUrls() {
        // given
        String ok = BASE + "ipsi_seed_prior_2026_01_hanyang_khu.json";

        // when / then
        assertThat(AdmissionsSeedLoadJob.validateUrls(List.of(ok))).isEmpty();
        assertThat(AdmissionsSeedLoadJob.validateUrls(List.of("https://example.com/a.json"))).isNotEmpty();
        assertThat(AdmissionsSeedLoadJob.validateUrls(List.of(
                "https://raw.githubusercontent.com/woori73/buildit-admissions/main/data/seed/a.json"))).as("브랜치명").isNotEmpty();
        assertThat(AdmissionsSeedLoadJob.validateUrls(List.of(BASE + "../../secret.json"))).isNotEmpty();
        assertThat(AdmissionsSeedLoadJob.validateUrls(List.of(ok, ok))).as("중복").isNotEmpty();
        assertThat(AdmissionsSeedLoadJob.validateUrls(List.of())).isNotEmpty();
    }

    @Test
    @DisplayName("given 파일 2개 when 실행하면 then 순서대로 내려받아 적재하고 입시결과 건수를 합산한다")
    void loadsFilesInOrder() throws Exception {
        // given
        AdmissionsDataLoader loader = mock(AdmissionsDataLoader.class);
        when(loader.loadSeed("A")).thenReturn(result(10));
        when(loader.loadSeed("B")).thenReturn(result(5));
        List<String> fetched = new ArrayList<>();
        AdmissionsSeedLoadJob job = new AdmissionsSeedLoadJob(loader,
                url -> { fetched.add(url); return url.endsWith("01.json") ? "A" : "B"; }, Runnable::run);

        // when
        var outcome = job.start(List.of(BASE + "01.json", BASE + "02.json"));

        // then
        assertThat(outcome).isEqualTo(AdmissionsSeedLoadJob.StartOutcome.STARTED);
        assertThat(fetched).containsExactly(BASE + "01.json", BASE + "02.json");
        @SuppressWarnings("unchecked") Map<String, Object> run = (Map<String, Object>) job.status().get("lastRun");
        assertThat(run).containsEntry("result", "COMPLETED").containsEntry("processed", 2).containsEntry("priorResults", 15);
        assertThat(job.isRunning()).isFalse();
    }

    @Test
    @DisplayName("given 두 번째 파일이 거절됨 when 실행하면 then 거기서 멈추고 실패 파일·사유를 남기며 잠금을 푼다")
    void stopsAtFirstFailure() throws Exception {
        // given
        AdmissionsDataLoader loader = mock(AdmissionsDataLoader.class);
        when(loader.loadSeed("A")).thenReturn(result(10));
        when(loader.loadSeed("B")).thenThrow(new AdmissionsDataLoader.SeedLoadException("알 수 없는 university_code=X"));
        AdmissionsSeedLoadJob job = new AdmissionsSeedLoadJob(loader,
                url -> url.endsWith("01.json") ? "A" : url.endsWith("02.json") ? "B" : "C", Runnable::run);

        // when
        job.start(List.of(BASE + "01.json", BASE + "02.json", BASE + "03.json"));

        // then
        @SuppressWarnings("unchecked") Map<String, Object> run = (Map<String, Object>) job.status().get("lastRun");
        assertThat(run).containsEntry("result", "FAILED").containsEntry("processed", 1);
        assertThat((String) run.get("error")).contains("02.json").contains("university_code=X");
        verify(loader, never()).loadSeed(eq("C"));
        assertThat(job.tryLock()).as("잠금 해제").isTrue();
    }

    @Test
    @DisplayName("given 잡이 실행 중 when 다시 시작하거나 단일 파일 잠금을 잡으면 then ALREADY_RUNNING·false")
    void secondStartWhileRunning_isRejected() throws Exception {
        // given: 실행을 보류하는 실행기
        List<Runnable> pending = new ArrayList<>();
        Executor hold = pending::add;
        AdmissionsDataLoader loader = mock(AdmissionsDataLoader.class);
        when(loader.loadSeed(anyString())).thenReturn(result(1));
        AdmissionsSeedLoadJob job = new AdmissionsSeedLoadJob(loader, url -> "A", hold);
        job.start(List.of(BASE + "01.json"));

        // when
        var second = job.start(List.of(BASE + "02.json"));
        boolean uploadLock = job.tryLock();

        // then
        assertThat(second).isEqualTo(AdmissionsSeedLoadJob.StartOutcome.ALREADY_RUNNING);
        assertThat(uploadLock).isFalse();
        pending.forEach(Runnable::run);
        assertThat(job.isRunning()).isFalse();
    }
}
