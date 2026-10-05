package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeChinaDisclosure;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeChinaNews;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaDisclosureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaNewsRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeCollectionRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 중국 현지 정보 수집: 킹넷(002517) CNINFO 공식 공시 + 중국 뉴스.
 *
 * 원칙:
 * - 중국 공시 원문 → 한국어 번역 → 핵심 추출 → 투자자 관점 설명 순서.
 * - 공시와 기사가 충돌하면 공시를 우선하고 차이를 표시한다.
 * - AI가 중요 사실을 임의 확정하지 않는다. 원문 근거(announcementId, URL)를 반드시 보존한다.
 * - 재게시·복제기사는 hash dedupe로 제거한다.
 * - 실패한 source가 전체를 rollback하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeChinaService {

    private static final String CNINFO_QUERY = "http://www.cninfo.com.cn/new/hisAnnouncement/query";
    /** 킹넷(恺英网络) 선전 종목 */
    private static final String KINGNET_SEC_CODE = "002517";

    private final WemadeChinaDisclosureRepository disclosureRepository;
    private final WemadeChinaNewsRepository newsRepository;
    private final WemadeCollectionRunRepository runRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    /** CNINFO 공시 수집 (킹넷, 최근 90일). */
    @Transactional
    public WemadeCollectionRun collectDisclosures() {
        WemadeCollectionRun run = startRun("CHINA_DISCLOSURE");
        int fetched = 0, accepted = 0, duplicate = 0;
        try {
            LocalDate today = LocalDate.now();
            String seDate = today.minusDays(90).format(DateTimeFormatter.ISO_DATE)
                    + "~" + today.format(DateTimeFormatter.ISO_DATE);
            String payload = "{\"pageNum\":1,\"pageSize\":30,\"column\":\"szse\",\"tabName\":\"fulltext\","
                    + "\"plate\":\"\",\"stock\":\"" + KINGNET_SEC_CODE + "\",\"searchkey\":\"\","
                    + "\"secid\":\"\",\"category\":\"\",\"trade\":\"\",\"seDate\":\"" + seDate + "\"}";
            String body = postJson(CNINFO_QUERY, payload);
            List<CnAnn> anns = parseAnnouncements(body);
            fetched = anns.size();
            for (CnAnn a : anns) {
                // 위메이드 관련 키워드 필터
                if (!isWemadeRelated(a.title)) continue;
                String hash = WemadeCollectionService.sha256("CN-DISCLOSURE|" + a.announcementId);
                if (disclosureRepository.existsByContentHash(hash)) { duplicate++; continue; }
                WemadeChinaDisclosure row = WemadeChinaDisclosure.builder()
                        .announcedAt(a.date)
                        .secCode(KINGNET_SEC_CODE)
                        .secName("恺英网络")
                        .titleCn(a.title)
                        .titleKr(null) // 번역은 별도 파이프라인/운영자 검수 후 입력
                        .summaryKr(null)
                        .whyMatters(null)
                        .announcementId(a.announcementId)
                        .sourceUrl("http://www.cninfo.com.cn/new/disclosure/detail?announcementId=" + a.announcementId)
                        .grade("A")
                        .contentHash(hash)
                        .collectedAt(LocalDateTime.now())
                        .build();
                disclosureRepository.save(row);
                accepted++;
            }
            finishRun(run, "SUCCESS", fetched, accepted, duplicate, 0, null);
        } catch (Exception e) {
            log.warn("WEMADE china disclosure collection failed (isolated): {}", e.getClass().getSimpleName());
            finishRun(run, "FAILED", fetched, accepted, duplicate, 1, truncate(e.getMessage()));
        }
        return run;
    }

    /**
     * 중국 뉴스 수집 프레임워크.
     * 안정적인 공개 뉴스 API가 없어 현재는 스켈레톤으로 둔다.
     * 검증된 기사는 시드(china_news.json)로 적재하고, 신규 기사는 운영자 검수 후 입력한다.
     * AI가 기사를 임의 생성하지 않는다.
     */
    @Transactional
    public WemadeCollectionRun collectNews() {
        WemadeCollectionRun run = startRun("CHINA_NEWS");
        finishRun(run, "PARTIAL", 0, 0, 0, 0,
                "news search API pending; verified items seeded, new items need owner review");
        return run;
    }

    @Transactional(readOnly = true)
    public List<WemadeChinaDisclosure> latestDisclosures() {
        return disclosureRepository.findAllByOrderByAnnouncedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<WemadeChinaNews> latestNews() {
        return newsRepository.findAllByOrderByPublishedAtDesc();
    }

    // ---- helpers ----

    private boolean isWemadeRelated(String title) {
        if (title == null) return false;
        String t = title.toLowerCase();
        return t.contains("wemade") || t.contains("娱美德") || t.contains("neopulse")
                || t.contains("neospher") || t.contains("nexpulse") || t.contains("传奇")
                || t.contains("夜鸦") || t.contains("参股") || t.contains("境外投资");
    }

    private List<CnAnn> parseAnnouncements(String body) {
        List<CnAnn> out = new ArrayList<>();
        // announcements:[{...,"announcementId":"123","announcementTitle":"...","announcementTime":...}]
        Matcher m = Pattern.compile(
                "\"announcementId\"\\s*:\\s*\"(\\d+)\"[^}]*?\"announcementTitle\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"announcementTime\"\\s*:\\s*(\\d+)",
                Pattern.DOTALL).matcher(body);
        while (m.find()) {
            long ts = Long.parseLong(m.group(3));
            LocalDate d = new java.util.Date(ts).toInstant()
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
            out.add(new CnAnn(m.group(1), m.group(2), d));
        }
        return out;
    }

    private String postJson(String url, String payload) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = httpClient.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res.body();
    }

    private WemadeCollectionRun startRun(String source) {
        return runRepository.save(WemadeCollectionRun.builder()
                .source(source).startedAt(LocalDateTime.now()).status("RUNNING").build());
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

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 450 ? s.substring(0, 450) : s;
    }

    private record CnAnn(String announcementId, String title, LocalDate date) {}
}
