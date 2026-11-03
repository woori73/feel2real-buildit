package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 지식 항목 생성(knowledge/build) 백그라운드 잡.
 *
 * <p>187개교 입시결과까지 넣으면 생성에 10분 넘게 걸려 HTTP 요청 하나로는 게이트웨이 시간 초과(504)가 난다.
 * 재색인 잡과 같은 방식으로 단일 스레드에서 돌리고, 중복 시작은 막으며, 진행 상황은 상태 조회로 본다.
 * 생성 자체는 대학 단위로 나눠 커밋하므로({@link AdmissionsKnowledgeBuildService#buildAll(AdmissionsKnowledgeBuildService.Progress)})
 * 중간에 앱이 재시작돼도 끝난 부분은 남고, 다시 실행하면 바뀐 것만 갱신된다(멱등).
 */
@Slf4j
@Service
public class AdmissionsKnowledgeBuildJob {

    public enum StartOutcome { STARTED, ALREADY_RUNNING }

    /** 한 번의 실행 기록(메모리, 재시작 시 사라짐). */
    static final class Run {
        final Instant startedAt = Instant.now();
        volatile Instant finishedAt;
        volatile String phase = "준비";
        volatile int phaseTotal;
        volatile int phaseDone;
        volatile int created;
        volatile int updated;
        volatile int unchanged;
        volatile String result = "RUNNING"; // RUNNING | COMPLETED | ERROR
        volatile String error;
    }

    private final AdmissionsKnowledgeBuildService buildService;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Run lastRun;

    @Autowired
    public AdmissionsKnowledgeBuildJob(AdmissionsKnowledgeBuildService buildService) {
        this(buildService, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admissions-knowledge-build");
            t.setDaemon(true);
            return t;
        }));
    }

    /** 테스트용: 실행기를 주입한다. */
    AdmissionsKnowledgeBuildJob(AdmissionsKnowledgeBuildService buildService, Executor executor) {
        this.buildService = buildService;
        this.executor = executor;
    }

    public StartOutcome start() {
        if (!running.compareAndSet(false, true)) {
            return StartOutcome.ALREADY_RUNNING;
        }
        Run run = new Run();
        lastRun = run;
        try {
            executor.execute(() -> execute(run));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return StartOutcome.STARTED;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void execute(Run run) {
        try {
            var r = buildService.buildAll(new AdmissionsKnowledgeBuildService.Progress() {
                @Override
                public void phase(String name, int total) {
                    run.phase = name;
                    run.phaseTotal = total;
                    run.phaseDone = 0;
                }

                @Override
                public void step(int created, int updated, int unchanged) {
                    run.phaseDone++;
                    run.created = created;
                    run.updated = updated;
                    run.unchanged = unchanged;
                }
            });
            run.created = r.created();
            run.updated = r.updated();
            run.unchanged = r.unchanged();
            run.result = "COMPLETED";
        } catch (RuntimeException e) {
            run.result = "ERROR";
            run.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("admissions knowledge build job error", e);
        } finally {
            run.finishedAt = Instant.now();
            log.info("admissions knowledge build job finished: result={} created={} updated={} unchanged={}",
                    run.result, run.created, run.updated, run.unchanged);
            running.set(false);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", running.get());
        Run run = lastRun;
        if (run == null) {
            out.put("lastRun", null);
            return out;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("result", run.result);
        r.put("phase", run.phase);
        r.put("phaseTotal", run.phaseTotal);
        r.put("phaseDone", run.phaseDone);
        r.put("created", run.created);
        r.put("updated", run.updated);
        r.put("unchanged", run.unchanged);
        r.put("startedAt", run.startedAt.toString());
        r.put("finishedAt", run.finishedAt == null ? null : run.finishedAt.toString());
        r.put("error", run.error);
        out.put("lastRun", r);
        return out;
    }
}
