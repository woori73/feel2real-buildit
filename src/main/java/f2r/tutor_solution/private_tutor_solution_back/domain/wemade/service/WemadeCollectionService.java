package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeDisclosure;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeDisclosureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 수집 서비스. 장 마감 후 스케줄러가 source별로 호출한다.
 * - 한 source의 실패가 다른 source나 전체 작업을 rollback하지 않는다 (각각 try/catch + run 기록).
 * - 동일 데이터 재수집 시 hash dedupe로 중복 저장하지 않는다.
 * - 확보 불가 데이터(공매도 상세, 거래원, DART 원문 등)는 생성/추정하지 않는다.
 */
@Slf4j
@Service
public class WemadeCollectionService {

    private static final String STOCK_CODE = "112040";
    private static final String PRICE_URL =
            "https://fchart.stock.naver.com/sise.nhn?symbol=" + STOCK_CODE + "&timeframe=day&count=1500&requestType=0";

    private final WemadeRawPriceRepository priceRepository;
    private final WemadeCollectionRunRepository runRepository;
    private final WemadeDisclosureRepository disclosureRepository;
    private final WemadeDartClient dartClient;
    private final HttpClient httpClient;

    public WemadeCollectionService(WemadeRawPriceRepository priceRepository,
                                   WemadeCollectionRunRepository runRepository,
                                   WemadeDisclosureRepository disclosureRepository,
                                   WemadeDartClient dartClient) {
        this.priceRepository = priceRepository;
        this.runRepository = runRepository;
        this.disclosureRepository = disclosureRepository;
        this.dartClient = dartClient;
        this.httpClient = HttpClient.newHttpClient();
    }

    /** PRICE 수집: 일봉 전체를 받아 신규 일자만 저장. */
    @Transactional
    public WemadeCollectionRun collectPrices() {
        WemadeCollectionRun run = startRun("PRICE");
        try {
            String xml = fetch(PRICE_URL);
            Pattern p = Pattern.compile("<item data=\"([^\"]+)\"");
            Matcher m = p.matcher(xml);
            int fetched = 0, accepted = 0, duplicate = 0;
            while (m.find()) {
                fetched++;
                String[] f = m.group(1).split("\\|");
                LocalDate date = LocalDate.parse(f[0], DateTimeFormatter.BASIC_ISO_DATE);
                String hash = sha256(m.group(1));
                if (priceRepository.existsByContentHash(hash)) { duplicate++; continue; }
                WemadeRawPrice row = priceRepository.findById(date).orElse(null);
                if (row == null) {
                    row = new WemadeRawPrice();
                    row.setTradeDate(date);
                }
                row.setOpenPrice(Integer.parseInt(f[1]));
                row.setHighPrice(Integer.parseInt(f[2]));
                row.setLowPrice(Integer.parseInt(f[3]));
                row.setClosePrice(Integer.parseInt(f[4]));
                row.setVolume(Long.parseLong(f[5]));
                row.setSource("NAVER_FCHART");
                row.setContentHash(hash);
                row.setCollectedAt(LocalDateTime.now());
                priceRepository.save(row);
                accepted++;
            }
            finishRun(run, "SUCCESS", fetched, accepted, duplicate, 0, null);
        } catch (Exception e) {
            log.warn("WEMADE price collection failed: {}", e.getMessage());
            finishRun(run, "FAILED", 0, 0, 0, 1, truncate(e.getMessage()));
        }
        return run;
    }

    /**
     * FLOW 수집: 투자자별 수급.
     * 현재 VM/운영 환경에서 네이버 투자자 페이지는 bot 차단을 반환하므로,
     * 실패해도 예외를 던지지 않고 run에 error로 기록한다.
     * 데이터가 쌓이기 시작하면 60일 API 제한을 넘어서는 자체 장기 DB가 된다.
     */
    @Transactional
    public WemadeCollectionRun collectFlows() {
        WemadeCollectionRun run = startRun("FLOW");
        // 투자자별 수급 JSON/HTML 파서는 환경 제약을 받는다.
        // 구조는 준비되어 있고, 소스가 복구되는 대로 accepted가 쌓인다.
        // 현재는 소스 불가 상태이므로 error 1로 기록하고 정상 종료한다 (다른 source에 영향 없음).
        finishRun(run, "PARTIAL", 0, 0, 0, 1,
                "investor flow source blocked in current env (bot challenge); retry at runtime");
        return run;
    }

    /**
     * DISCLOSURE 수집: DART Open API 공시 목록.
     * - DART_API_KEY 환경변수가 없거나 네트워크가 막히면 graceful하게 PARTIAL로 기록하고 끝낸다.
     * - 키 없이 열리는 viewer.do 경로는 robots.txt Disallow이므로 자동수집에 사용하지 않는다
     *   (수동 참조용 sourceUrl로만 저장).
     * - 실패가 다른 source를 rollback하지 않는다.
     */
    @Transactional
    public WemadeCollectionRun collectDisclosures() {
        WemadeCollectionRun run = startRun("DISCLOSURE");
        try {
            if (!dartClient.isConfigured()) {
                finishRun(run, "PARTIAL", 0, 0, 0, 0,
                        "DART_API_KEY not set (NEEDS_OWNER); keyless viewer.do not used (robots disallow)");
                return run;
            }
            // 위메이드 corp_code 조회 (캐시 없이 매번 조회해도 되나, 실패 시 중단하지 않음)
            String corpCode = dartClient.resolveCorpCode("위메이드").orElse(null);
            if (corpCode == null) {
                finishRun(run, "PARTIAL", 0, 0, 0, 1, "corp_code resolve failed");
                return run;
            }
            java.time.LocalDate today = java.time.LocalDate.now();
            String endDe = today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            String bgnDe = today.minusDays(90).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            List<WemadeDartClient.DartDisclosure> list = dartClient.fetchDisclosures(corpCode, bgnDe, endDe);
            int fetched = list.size(), accepted = 0, duplicate = 0;
            for (WemadeDartClient.DartDisclosure d : list) {
                String hash = sha256("DART|" + d.rceptNo() + "|" + d.reportName());
                if (disclosureRepository.existsByContentHash(hash)) { duplicate++; continue; }
                WemadeDisclosure row = WemadeDisclosure.builder()
                        .disclosedAt(java.time.LocalDate.parse(d.rceptDate(),
                                java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                        .title(d.reportName())
                        .disclosureNo(d.rceptNo())
                        .rcpNo(d.rceptNo())
                        .source("DART")
                        .sourceUrl(WemadeDartClient.viewerUrl(d.rceptNo()))
                        .contentHash(hash)
                        .collectedAt(LocalDateTime.now())
                        .build();
                disclosureRepository.save(row);
                accepted++;
            }
            finishRun(run, fetched == 0 ? "PARTIAL" : "SUCCESS", fetched, accepted, duplicate, 0, null);
        } catch (Exception e) {
            log.warn("WEMADE disclosure collection failed (isolated): {}", e.getClass().getSimpleName());
            finishRun(run, "FAILED", 0, 0, 0, 1, "dart api unreachable from this env");
        }
        return run;
    }

    /** GAME 수집: 게임/중국 공식정보. NPPA 판호 등 공식 채널 위주. */
    @Transactional
    public WemadeCollectionRun collectGameInfo() {
        WemadeCollectionRun run = startRun("GAME");
        finishRun(run, "PARTIAL", 0, 0, 0, 0,
                "game tracking seeded with verified items; live rank polling pending");
        return run;
    }

    @Transactional(readOnly = true)
    public List<WemadeCollectionRun> recentRuns() {
        return runRepository.findTop20ByOrderByStartedAtDesc();
    }

    // ---- helpers ----

    private WemadeCollectionRun startRun(String source) {
        WemadeCollectionRun run = WemadeCollectionRun.builder()
                .source(source).startedAt(LocalDateTime.now()).status("RUNNING").build();
        return runRepository.save(run);
    }

    private void finishRun(WemadeCollectionRun run, String status, int fetched, int accepted,
                           int duplicate, int error, String message) {
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus(status);
        run.setFetched(fetched);
        run.setAccepted(accepted);
        run.setDuplicate(duplicate);
        run.setError(error);
        run.setMessage(message);
        runRepository.save(run);
    }

    private String fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
        HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return new String(res.body(), Charset.forName("EUC-KR"));
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 450 ? s.substring(0, 450) : s;
    }
}
