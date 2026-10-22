package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 임베딩 재색인 백그라운드 잡.
 *
 * <p>왜 필요한가: 188건을 HTTP 요청 하나에서 동기로 돌리면 ALB idle timeout·관리자 중복 클릭에 취약하고,
 * 하나의 트랜잭션이 길게 열려 중간에 끊기면 완료분도 사라진다. 이 잡은
 * <ul>
 *   <li>단일 스레드로 항목을 순차 처리하고 항목마다 즉시 커밋한다(끊겨도 완료분 유지),</li>
 *   <li>동시에 한 번만 실행한다(중복 시작은 ALREADY_RUNNING),</li>
 *   <li>진행 상황(전체/완료/실패/진행 중)은 DB에서 계산하므로 앱 재시작 후에도 확인된다,</li>
 *   <li>재시도는 MISSING 모드 재실행으로 충분하다 — 이미 성공한 항목은 대상에서 빠져 임베딩이 중복되지 않는다.</li>
 * </ul>
 * 실패 사유는 메모리({@code lastRun.failures})와 로그에 남는다(재시작 시 메모리분은 사라지지만 DB 기준 "미완료"는 유지).
 */
@Slf4j
@Service
public class AdmissionsReindexJob {

    public enum Mode { MISSING, ALL }

    public enum StartOutcome { STARTED, ALREADY_RUNNING, NOTHING_TO_DO }

    /** 한 번의 실행 기록(메모리). */
    public static final class Run {
        final Mode mode;
        final Instant startedAt = Instant.now();
        volatile Instant finishedAt;
        volatile int total;
        volatile int processed;
        volatile int embedded;
        volatile int failed;
        volatile Long currentId;
        volatile String result = "RUNNING"; // RUNNING | COMPLETED | CANCELLED | ERROR
        final Map<Long, String> failures = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

        Run(Mode mode) { this.mode = mode; }
    }

    private final AdmissionsEmbeddingService embeddingService;
    private final AdmissionsEmbeddingProperties props;
    private final AdmissionsVectorIndex vectorIndex;
    private final Executor executor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean cancelRequested = false;
    private volatile Run lastRun;

    @Autowired
    public AdmissionsReindexJob(AdmissionsEmbeddingService embeddingService,
                                AdmissionsEmbeddingProperties props,
                                AdmissionsVectorIndex vectorIndex) {
        this(embeddingService, props, vectorIndex, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admissions-reindex");
            t.setDaemon(true);
            return t;
        }));
    }

    /** 테스트용: 실행기를 주입한다(동기 실행기 또는 보류 실행기). */
    AdmissionsReindexJob(AdmissionsEmbeddingService embeddingService,
                         AdmissionsEmbeddingProperties props,
                         AdmissionsVectorIndex vectorIndex,
                         Executor executor) {
        this.embeddingService = embeddingService;
        this.props = props;
        this.vectorIndex = vectorIndex;
        this.executor = executor;
    }

    /** 잡을 시작한다. 이미 실행 중이면 새로 시작하지 않는다. */
    public StartOutcome start(Mode mode) {
        if (!running.compareAndSet(false, true)) {
            return StartOutcome.ALREADY_RUNNING;
        }
        try {
            List<Long> targets = computeTargets(mode);
            if (targets.isEmpty()) {
                running.set(false);
                return StartOutcome.NOTHING_TO_DO;
            }
            cancelRequested = false;
            Run run = new Run(mode);
            run.total = targets.size();
            lastRun = run;
            executor.execute(() -> execute(run, targets));
            return StartOutcome.STARTED;
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    /** 실행 중인 잡에 중단을 요청한다(현재 항목까지 마치고 멈춘다). */
    public boolean cancel() {
        if (!running.get()) {
            return false;
        }
        cancelRequested = true;
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    private List<Long> computeTargets(Mode mode) {
        if (mode == Mode.ALL) {
            return embeddingService.allIds();
        }
        Set<Long> have = embeddingService.embeddedIds();
        List<Long> out = new ArrayList<>();
        for (Long id : embeddingService.activeIds()) {
            if (!have.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private void execute(Run run, List<Long> targets) {
        try {
            if (!props.isEnabled()) {
                for (Long id : targets) {
                    run.failures.put(id, "embedding disabled (app.admissions.embedding.enabled=false)");
                }
                run.failed = targets.size();
                run.processed = targets.size();
                run.result = "ERROR";
                return;
            }
            for (Long id : targets) {
                if (cancelRequested) {
                    run.result = "CANCELLED";
                    return;
                }
                run.currentId = id;
                String reason;
                try {
                    reason = embeddingService.embedItem(id);
                } catch (RuntimeException e) {
                    reason = "예외: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                if (reason == null) {
                    run.embedded++;
                } else {
                    run.failed++;
                    run.failures.put(id, reason);
                    log.warn("admissions reindex item failed: knowledgeId={} reason={}", id, reason);
                }
                run.processed++;
                pause();
            }
            run.result = "COMPLETED";
        } catch (RuntimeException e) {
            run.result = "ERROR";
            log.error("admissions reindex job error", e);
        } finally {
            run.currentId = null;
            run.finishedAt = Instant.now();
            try {
                if (run.embedded > 0) {
                    vectorIndex.reload();
                }
            } catch (RuntimeException e) {
                log.error("admissions reindex: index reload failed", e);
            }
            log.info("admissions reindex job finished: result={} total={} embedded={} failed={}",
                    run.result, run.total, run.embedded, run.failed);
            running.set(false);
        }
    }

    private void pause() {
        long ms = props.getIndexPauseMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelRequested = true;
        }
    }

    /**
     * 진행 상태. total/done/missing은 DB 기준(재시작 후에도 유지), running·lastRun은 메모리 기준.
     */
    public Map<String, Object> status() {
        List<Long> active = embeddingService.activeIds();
        Set<Long> have = embeddingService.embeddedIds();
        int done = 0;
        for (Long id : active) {
            if (have.contains(id)) {
                done++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", active.size());
        out.put("done", done);
        out.put("missing", active.size() - done);
        out.put("indexSize", vectorIndex.size());
        out.put("indexReady", vectorIndex.isReady());
        out.put("running", running.get());
        out.put("cancelRequested", cancelRequested && running.get());
        Run run = lastRun;
        if (run == null) {
            out.put("lastRun", null);
        } else {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("mode", run.mode.name());
            r.put("result", run.result);
            r.put("startedAt", run.startedAt.toString());
            r.put("finishedAt", run.finishedAt == null ? null : run.finishedAt.toString());
            r.put("total", run.total);
            r.put("processed", run.processed);
            r.put("embedded", run.embedded);
            r.put("failed", run.failed);
            r.put("currentId", run.currentId);
            synchronized (run.failures) {
                r.put("failures", new LinkedHashMap<>(run.failures));
            }
            out.put("lastRun", r);
        }
        return out;
    }
}
