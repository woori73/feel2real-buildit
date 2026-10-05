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

    private final WemadeRawPriceRepository priceRepository;
    private final WemadeCollectionRunRepository runRepository;
    private final WemadeDisclosureRepository disclosureRepository;
    private final WemadeDartClient dartClient;
    private final WemadePykrxCollector pykrxCollector;
    private final HttpClient httpClient;

    public WemadeCollectionService(WemadeRawPriceRepository priceRepository,
                                   WemadeCollectionRunRepository runRepository,
                                   WemadeDisclosureRepository disclosureRepository,
                                   WemadeDartClient dartClient,
                                   WemadePykrxCollector pykrxCollector) {
        this.priceRepository = priceRepository;
        this.runRepository = runRepository;
        this.disclosureRepository = disclosureRepository;
        this.dartClient = dartClient;
        this.pykrxCollector = pykrxCollector;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * PRICE 수집: 종목별 일봉을 받아 신규 일자만 저장.
     * 112040/101730 = pykrx 우선 (KRX 공식, 키 불필요). pykrx 불가 시 네이버 폴백.
     * 002517 = eastmoney(CNY). 네이버 bot 우회는 시도하지 않는다.
     */
    @Transactional
    public WemadeCollectionRun collectPrices() {
        WemadeCollectionRun run = startRun("PRICE");
        int fetched = 0, accepted = 0, duplicate = 0, error = 0;
        StringBuilder msg = new StringBuilder();
        LocalDate today = LocalDate.now();
        // 1순위: pykrx (최근 10일 윈도우, dedupe로 중복 방지)
        try {
            WemadePykrxCollector.CollectResult r = pykrxCollector.collectPrices(
                    List.of("112040", "101730"), today.minusDays(10), today);
            fetched += r.fetched(); accepted += r.accepted(); duplicate += r.duplicate();
            msg.append(r.note()).append(" ");
            if (r.accepted() == 0 && r.note() != null && r.note().contains("unavailable")) {
                // 폴백: 네이버 (기존 수집기 유지)
                for (String symbol : List.of("112040", "101730")) {
                    try {
                        int[] nr = collectNaver(symbol);
                        fetched += nr[0]; accepted += nr[1]; duplicate += nr[2];
                        msg.append(symbol).append(":NAVER_FCHART_fallback ");
                    } catch (Exception e) {
                        error++;
                        msg.append(symbol).append(":").append(e.getClass().getSimpleName()).append(" ");
                        log.warn("WEMADE price fallback failed for {}: {}", symbol, e.getClass().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            error++;
            msg.append("pykrx:").append(e.getClass().getSimpleName()).append(" ");
            log.warn("WEMADE pykrx price collection failed (isolated): {}", e.getClass().getSimpleName());
        }
        // 킹넷은 eastmoney 유지
        try {
            int[] k = collectKingnet("002517");
            fetched += k[0]; accepted += k[1]; duplicate += k[2];
        } catch (Exception e) {
            error++;
            msg.append("002517:").append(e.getClass().getSimpleName()).append(" ");
            log.warn("WEMADE price collection failed for 002517 (isolated): {}", e.getClass().getSimpleName());
        }
        String status = error == 0 ? "SUCCESS" : (accepted > 0 ? "PARTIAL" : "FAILED");
        finishRun(run, status, fetched, accepted, duplicate, error,
                msg.length() == 0 ? null : truncate(msg.toString()));
        return run;
    }

    /** 네이버 일봉 (112040, 101730). */
    private int[] collectNaver(String symbol) throws Exception {
        String url = "https://fchart.stock.naver.com/sise.nhn?symbol=" + symbol + "&timeframe=day&count=1500&requestType=0";
        String xml = fetch(url);
        Pattern p = Pattern.compile("<item data=\"([^\"]+)\"");
        Matcher m = p.matcher(xml);
        int fetched = 0, accepted = 0, duplicate = 0;
        while (m.find()) {
            fetched++;
            String[] f = m.group(1).split("\\|");
            LocalDate date = LocalDate.parse(f[0], DateTimeFormatter.BASIC_ISO_DATE);
            String hash = sha256(symbol + "|" + m.group(1));
            if (priceRepository.existsByContentHash(hash)) { duplicate++; continue; }
            WemadeRawPrice.PriceId id = new WemadeRawPrice.PriceId(symbol, date);
            WemadeRawPrice row = priceRepository.findById(id).orElse(null);
            if (row == null) {
                row = new WemadeRawPrice();
                row.setSymbol(symbol);
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
        return new int[]{fetched, accepted, duplicate};
    }

    /** 킹넷(002517.SZ) 일봉 — eastmoney kline API. CNY는 100배 스케일로 저장. */
    private int[] collectKingnet(String symbol) throws Exception {
        String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                + "?fields1=f1,f2,f3,f4,f5&fields2=f51,f52,f53,f54,f55,f56,f57"
                + "&klt=101&fqt=1&secid=0.002517&beg=20200101&end=29991231&lmt=2000";
        String body = fetchUtf8(url);
        // "klines":["2026-09-01,17.44,16.97,17.44,16.87,630322,1077170779.50", ...]
        Matcher km = Pattern.compile("\"klines\":\\[(.*?)]", Pattern.DOTALL).matcher(body);
        if (!km.find()) throw new IllegalStateException("no klines in response");
        Matcher im = Pattern.compile("\"(\\d{4}-\\d{2}-\\d{2},[\\d.,-]+)\"").matcher(km.group(1));
        int fetched = 0, accepted = 0, duplicate = 0;
        while (im.find()) {
            fetched++;
            String[] f = im.group(1).split(",");
            LocalDate date = LocalDate.parse(f[0]);
            long open = Math.round(Double.parseDouble(f[1]) * 100);
            long close = Math.round(Double.parseDouble(f[2]) * 100);
            long high = Math.round(Double.parseDouble(f[3]) * 100);
            long low = Math.round(Double.parseDouble(f[4]) * 100);
            long volume = (long) Double.parseDouble(f[5]);
            String hash = sha256(symbol + "|" + f[0] + "|" + close);
            if (priceRepository.existsByContentHash(hash)) { duplicate++; continue; }
            WemadeRawPrice.PriceId id = new WemadeRawPrice.PriceId(symbol, date);
            WemadeRawPrice row = priceRepository.findById(id).orElse(null);
            if (row == null) {
                row = new WemadeRawPrice();
                row.setSymbol(symbol);
                row.setTradeDate(date);
            }
            row.setOpenPrice((int) open);
            row.setHighPrice((int) high);
            row.setLowPrice((int) low);
            row.setClosePrice((int) close);
            row.setVolume(volume);
            row.setSource("EASTMONEY");
            row.setContentHash(hash);
            row.setCollectedAt(LocalDateTime.now());
            priceRepository.save(row);
            accepted++;
        }
        return new int[]{fetched, accepted, duplicate};
    }

    /**
     * FLOW 수집: 투자자별 수급 (pykrx 우선).
     * pykrx 투자자 엔드포인트는 KRX 로그인(KRX_ID/KRX_PW) 없이 빈 결과를 반환한다.
     * 이 경우 PENDING으로 기록하고 조용히 종료한다 — 네이버 bot 우회는 시도하지 않는다.
     * 대체 경로: KIS Open API (NEEDS_OWNER), KRX CSV 다운로드.
     * 데이터가 쌓이기 시작하면 60일 API 제한을 넘어서는 자체 장기 DB가 된다.
     */
    @Transactional
    public WemadeCollectionRun collectFlows() {
        WemadeCollectionRun run = startRun("FLOW");
        LocalDate today = LocalDate.now();
        try {
            WemadePykrxCollector.CollectResult r = pykrxCollector.collectFlows(
                    List.of("112040"), today.minusDays(65), today);
            String status = r.accepted() > 0 ? "SUCCESS" : "PARTIAL";
            finishRun(run, status, r.fetched(), r.accepted(), r.duplicate(), 0, r.note());
        } catch (Exception e) {
            log.warn("WEMADE pykrx flow collection failed (isolated): {}", e.getClass().getSimpleName());
            finishRun(run, "PARTIAL", 0, 0, 0, 0,
                    "PENDING: pykrx flows unavailable; fallback=KIS Open API (NEEDS_OWNER)");
        }
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

    private String fetchUtf8(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
        HttpResponse<String> res = httpClient.send(req,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res.body();
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
