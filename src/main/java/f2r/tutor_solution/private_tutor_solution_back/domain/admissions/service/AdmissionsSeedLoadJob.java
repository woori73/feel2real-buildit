package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 시드 여러 파일을 서버가 차례로 받아 적재하는 백그라운드 잡.
 *
 * <p>187개교 입시결과는 시드 파일이 수십~수백 개라 관리자 화면에서 하나씩 올릴 수 없다. 관리자는 buildit-admissions
 * 저장소의 <b>고정 커밋</b> raw URL 목록만 넘기고, 서버가 순서대로 받아 {@link AdmissionsDataLoader#loadSeed(String)}로
 * 적재한다(파일마다 별도 트랜잭션 — 실패하면 그 파일만 롤백되고 거기서 멈춘다. 행 단위 자연키라 다시 실행해도 중복되지 않는다).
 *
 * <p>안전장치:
 * <ul>
 *   <li>주소는 {@code https://raw.githubusercontent.com/woori73/buildit-admissions/<40자 커밋>/data/seed/...json}만 받는다
 *       (임의 주소 요청 방지, 브랜치 이름 대신 커밋으로 고정해 적재 중 내용이 바뀌지 않게).</li>
 *   <li>파일 하나는 {@value #MAX_FILE_BYTES}바이트, 목록은 {@value #MAX_FILES}개까지.</li>
 *   <li>화면의 단일 파일 적재와 같은 잠금을 쓴다 — 둘 중 하나만 동시에 돈다(겹치면 409).</li>
 * </ul>
 */
@Slf4j
@Service
public class AdmissionsSeedLoadJob {

    static final int MAX_FILES = 500;
    static final int MAX_FILE_BYTES = 30 * 1024 * 1024;
    static final Pattern ALLOWED_URL = Pattern.compile(
            "^https://raw\\.githubusercontent\\.com/woori73/buildit-admissions/[0-9a-f]{40}/data/seed/[A-Za-z0-9_\\-/]+\\.json$");

    public enum StartOutcome { STARTED, ALREADY_RUNNING }

    static final class Run {
        final Instant startedAt = Instant.now();
        volatile Instant finishedAt;
        final int total;
        volatile int processed;
        volatile String current;
        volatile String result = "RUNNING"; // RUNNING | COMPLETED | FAILED
        volatile String error;
        final List<Map<String, Object>> files = Collections.synchronizedList(new ArrayList<>());

        Run(int total) { this.total = total; }
    }

    private final AdmissionsDataLoader loader;
    private final Function<String, String> fetcher;
    private final Executor executor;
    /** 시드 적재 잠금(단일 파일 업로드와 URL 목록 적재가 같이 쓴다). 같은 적재가 겹치면 두 번째가 중복 키로 실패하기 때문. */
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile Run lastRun;

    @Autowired
    public AdmissionsSeedLoadJob(AdmissionsDataLoader loader) {
        this(loader, AdmissionsSeedLoadJob::httpGet, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admissions-seed-load");
            t.setDaemon(true);
            return t;
        }));
    }

    /** 테스트용: 내려받기와 실행기를 주입한다. */
    AdmissionsSeedLoadJob(AdmissionsDataLoader loader, Function<String, String> fetcher, Executor executor) {
        this.loader = loader;
        this.fetcher = fetcher;
        this.executor = executor;
    }

    /** 단일 파일 적재(화면 업로드)용 잠금. 잡이 돌고 있으면 false. */
    public boolean tryLock() {
        return loading.compareAndSet(false, true);
    }

    public void unlock() {
        loading.set(false);
    }

    public boolean isRunning() {
        return loading.get();
    }

    /**
     * 주소 목록을 검사한다. 문제가 있으면 사유 목록(비어 있으면 통과).
     */
    public static List<String> validateUrls(List<String> urls) {
        List<String> problems = new ArrayList<>();
        if (urls == null || urls.isEmpty()) {
            problems.add("주소 목록이 비어 있습니다.");
            return problems;
        }
        if (urls.size() > MAX_FILES) problems.add("파일은 " + MAX_FILES + "개까지 받습니다(" + urls.size() + "개).");
        for (String u : urls) {
            if (u == null || !ALLOWED_URL.matcher(u.strip()).matches() || u.contains("..")) {
                problems.add("허용되지 않는 주소: " + u);
            }
        }
        if (urls.stream().map(String::strip).distinct().count() != urls.size()) problems.add("같은 주소가 두 번 있습니다.");
        return problems;
    }

    /** 시작. 주소 검사는 호출 전에 {@link #validateUrls(List)}로 한다. */
    public StartOutcome start(List<String> urls) {
        if (!tryLock()) {
            return StartOutcome.ALREADY_RUNNING;
        }
        List<String> list = urls.stream().map(String::strip).toList();
        Run run = new Run(list.size());
        lastRun = run;
        try {
            executor.execute(() -> execute(run, list));
        } catch (RuntimeException e) {
            unlock();
            throw e;
        }
        return StartOutcome.STARTED;
    }

    private void execute(Run run, List<String> urls) {
        try {
            for (String url : urls) {
                run.current = fileName(url);
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("file", run.current);
                try {
                    String text = fetcher.apply(url);
                    var r = loader.loadSeed(text);
                    rec.put("status", r.skipped() > 0 ? "LOADED_WITH_SKIPS" : "LOADED");
                    rec.put("universities", r.universities());
                    rec.put("materials", r.materials());
                    rec.put("departments", r.departments());
                    rec.put("types", r.types());
                    rec.put("schedules", r.schedules());
                    rec.put("priorResults", r.priorResults());
                    rec.put("priorResultsUpdated", r.priorResultsUpdated());
                    rec.put("skippedUnknownYear", r.skipped());
                    run.files.add(rec);
                    run.processed++;
                } catch (Exception e) {
                    // 이 파일은 롤백됐다. 다음 파일로 넘어가지 않는다(순서가 있는 적재일 수 있음). 다시 실행하면 끝난 파일은 중복되지 않는다.
                    String reason = e instanceof AdmissionsDataLoader.SeedLoadException ? e.getMessage()
                            : e.getClass().getSimpleName() + ": " + e.getMessage();
                    rec.put("status", "FAILED");
                    rec.put("error", reason);
                    run.files.add(rec);
                    run.result = "FAILED";
                    run.error = run.current + ": " + reason;
                    log.warn("admissions seed url load failed at {}: {}", run.current, reason);
                    return;
                }
            }
            run.result = "COMPLETED";
        } finally {
            run.current = null;
            run.finishedAt = Instant.now();
            log.info("admissions seed url load finished: result={} processed={}/{}", run.result, run.processed, run.total);
            unlock();
        }
    }

    static String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", loading.get());
        Run run = lastRun;
        if (run == null) {
            out.put("lastRun", null);
            return out;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("result", run.result);
        r.put("total", run.total);
        r.put("processed", run.processed);
        r.put("current", run.current);
        r.put("startedAt", run.startedAt.toString());
        r.put("finishedAt", run.finishedAt == null ? null : run.finishedAt.toString());
        r.put("error", run.error);
        int prior = 0, priorUpd = 0;
        synchronized (run.files) {
            for (Map<String, Object> f : run.files) {
                prior += (Integer) f.getOrDefault("priorResults", 0);
                priorUpd += (Integer) f.getOrDefault("priorResultsUpdated", 0);
            }
            r.put("priorResults", prior);
            r.put("priorResultsUpdated", priorUpd);
            r.put("files", new ArrayList<>(run.files));
        }
        out.put("lastRun", r);
        return out;
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    static String httpGet(String url) {
        try {
            HttpResponse<byte[]> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("내려받기 실패 HTTP " + resp.statusCode());
            }
            if (resp.body().length > MAX_FILE_BYTES) {
                throw new IllegalStateException("파일이 너무 큽니다(" + resp.body().length + " bytes)");
            }
            return new String(resp.body(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("내려받기 실패: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("내려받기 중단", e);
        }
    }
}
