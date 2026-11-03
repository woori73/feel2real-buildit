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
 * 지식 항목 생성(전형·일정 → 검색용)을 백그라운드로 돌린다. 행이 많으면 요청이 게이트웨이 제한(약 5분)을 넘겨
 * 화면에 504가 나고, 같은 버튼을 다시 누르면 작업이 겹친다. 그래서 시작만 받고(202) 진행 상태는 따로 조회한다.
 * 한 번에 하나만 돈다. 결과는 메모리에만 남는다(재시작하면 사라지지만, 생성은 멱등이라 다시 눌러도 된다).
 */
@Slf4j
@Service
public class AdmissionsKnowledgeBuildJob {

    public enum StartOutcome { STARTED, ALREADY_RUNNING }

    private final AdmissionsKnowledgeBuildService buildService;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Map<String, Object> lastRun;

    @Autowired
    public AdmissionsKnowledgeBuildJob(AdmissionsKnowledgeBuildService buildService) {
        this(buildService, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admissions-knowledge-build");
            t.setDaemon(true);
            return t;
        }));
    }

    AdmissionsKnowledgeBuildJob(AdmissionsKnowledgeBuildService buildService, Executor executor) {
        this.buildService = buildService;
        this.executor = executor;
    }

    public StartOutcome start() {
        if (!running.compareAndSet(false, true)) {
            return StartOutcome.ALREADY_RUNNING;
        }
        // 작업 스레드가 쓰는 동안 상태 조회가 읽으므로 동시 접근 가능한 맵을 쓴다.
        Map<String, Object> run = new java.util.concurrent.ConcurrentHashMap<>();
        run.put("result", "RUNNING");
        run.put("startedAt", Instant.now().toString());
        lastRun = run;
        try {
            executor.execute(() -> execute(run));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return StartOutcome.STARTED;
    }

    private void execute(Map<String, Object> run) {
        try {
            var r = buildService.buildAll();
            run.put("created", r.created());
            run.put("updated", r.updated());
            run.put("unchanged", r.unchanged());
            run.put("result", "COMPLETED");
            log.info("admissions knowledge build by admin: created={} updated={} unchanged={}",
                    r.created(), r.updated(), r.unchanged());
        } catch (RuntimeException e) {
            run.put("result", "ERROR");
            run.put("error", e.getClass().getSimpleName());
            log.error("admissions knowledge build failed", e);
        } finally {
            run.put("finishedAt", Instant.now().toString());
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /** {@code {running, lastRun:{result:RUNNING|COMPLETED|ERROR, startedAt, finishedAt, created, updated, unchanged, error}}}. */
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", running.get());
        out.put("lastRun", lastRun == null ? null : new LinkedHashMap<>(lastRun));
        return out;
    }
}
