package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 「오늘 새롭게 알게 된 것」 파이프라인 (§5).
 * 수집(기존 스케줄러) → 후보 조회(최근 48h KST) → 쟁점 매핑(규칙) → 중복 제거 → 점수 → 상위 3건.
 * AI 호출 없음.
 *
 * 방어 설계: 어떤 경우에도 예외를 던지지 않는다. 실패 시 빈 목록 반환.
 * Thymeleaf 호환을 위해 Map<String, Object>로 반환 (record 접근 이슈 회피).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeDeltaService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaNewsRepository chinaNewsRepo;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaDisclosureRepository chinaDisclosureRepo;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeDisclosureRepository disclosureRepo;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeGameTrackingRepository gameTrackingRepo;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePolicyTrackingRepository policyTrackingRepo;
    private final WemadeIssueService issueService;

    /** 내부 후보 항목 */
    private record Candidate(
            String sourceKind,
            Long id,
            String titleKr,
            String summaryKr,
            String sourceName,
            String sourceUrl,
            LocalDateTime eventAt,
            String grade,
            String contentHash
    ) {}

    /**
     * Delta 항목 목록 반환. 절대 예외를 던지지 않음.
     * 각 항목은 Map: {issueNumber, issueShortName, dateLabel, kindLabel, title, summary, sourceUrl, sourceKind}
     */
    public List<Map<String, Object>> buildDelta() {
        try {
            return buildDeltaInternal();
        } catch (Exception e) {
            log.warn("WemadeDeltaService.buildDelta failed, returning empty", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> buildDeltaInternal() {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusHours(48);
        List<Candidate> candidates = new ArrayList<>();
        candidates.addAll(collectChinaDisclosures(cutoff));
        candidates.addAll(collectChinaNews(cutoff));
        candidates.addAll(collectDart(cutoff));
        candidates.addAll(collectGame(cutoff));
        candidates.addAll(collectPolicy(cutoff));

        // 중복 제거 (contentHash)
        Set<String> seen = new HashSet<>();
        candidates = candidates.stream()
                .filter(c -> c.contentHash() == null || seen.add(c.contentHash()))
                .collect(Collectors.toList());

        // 쟁점 매핑 + 점수
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Integer> usedIssues = new HashSet<>();

        List<Candidate> sorted = candidates.stream()
                .map(c -> new AbstractMap.SimpleEntry<>(c, matchIssue(c)))
                .filter(e -> e.getValue() >= 1)
                .sorted((a, b) -> Integer.compare(score(b.getKey()), score(a.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (Candidate c : sorted) {
            if (result.size() >= 3) break;
            int issue = matchIssue(c);
            if (!usedIssues.add(issue)) continue;
            result.add(toMap(c, issue));
        }
        return result;
    }

    // ---- 수집 (각각 try/catch) ----

    private List<Candidate> collectChinaDisclosures(LocalDateTime cutoff) {
        List<Candidate> out = new ArrayList<>();
        try {
            for (var d : chinaDisclosureRepo.findAllByOrderByAnnouncedAtDesc()) {
                LocalDateTime at = toDateTime(d.getAnnouncedAt(), d.getCollectedAt());
                if (at == null || at.isBefore(cutoff)) continue;
                if (out.size() >= 20) break;
                out.add(new Candidate("CHINA_DISCLOSURE", d.getId(),
                        nz(d.getTitleKr(), d.getTitleCn()), nz(d.getSummaryKr(), ""),
                        "중국 공시", d.getSourceUrl(), at, nz(d.getGrade(), "A"), d.getContentHash()));
            }
        } catch (Exception e) { log.debug("delta chinaDisclosure collect failed", e); }
        return out;
    }

    private List<Candidate> collectChinaNews(LocalDateTime cutoff) {
        List<Candidate> out = new ArrayList<>();
        try {
            for (var n : chinaNewsRepo.findAllByOrderByPublishedAtDesc()) {
                LocalDateTime at = toDateTime(n.getPublishedAt(), n.getCollectedAt());
                if (at == null || at.isBefore(cutoff)) continue;
                if (out.size() >= 20) break;
                out.add(new Candidate("CHINA_NEWS", n.getId(),
                        nz(n.getTitleKr(), n.getTitleCn()), nz(n.getSummaryKr(), ""),
                        nz(n.getSourceName(), "중국 뉴스"), n.getSourceUrl(), at, nz(n.getGrade(), "C"), n.getContentHash()));
            }
        } catch (Exception e) { log.debug("delta chinaNews collect failed", e); }
        return out;
    }

    private List<Candidate> collectDart(LocalDateTime cutoff) {
        List<Candidate> out = new ArrayList<>();
        try {
            for (var d : disclosureRepo.findAllByOrderByDisclosedAtDesc()) {
                LocalDateTime at = toDateTime(d.getDisclosedAt(), d.getCollectedAt());
                if (at == null || at.isBefore(cutoff)) continue;
                if (out.size() >= 20) break;
                out.add(new Candidate("DART", d.getId(), nz(d.getTitle(), ""),
                        "", "DART 공시", d.getSourceUrl(), at, "A", d.getContentHash()));
            }
        } catch (Exception e) { log.debug("delta dart collect failed", e); }
        return out;
    }

    private List<Candidate> collectGame(LocalDateTime cutoff) {
        List<Candidate> out = new ArrayList<>();
        try {
            for (var g : gameTrackingRepo.findAllByOrderByTrackedAtDesc()) {
                LocalDateTime at = toDateTime(g.getTrackedAt(), g.getCollectedAt());
                if (at == null || at.isBefore(cutoff)) continue;
                if (out.size() >= 20) break;
                out.add(new Candidate("GAME", g.getId(),
                        nz(g.getGameName(), "") + " · " + nz(g.getItem(), ""),
                        nz(g.getDetail(), ""), nz(g.getSourceName(), "게임 트래킹"),
                        g.getSourceUrl(), at, "B", g.getContentHash()));
            }
        } catch (Exception e) { log.debug("delta game collect failed", e); }
        return out;
    }

    private List<Candidate> collectPolicy(LocalDateTime cutoff) {
        List<Candidate> out = new ArrayList<>();
        try {
            for (var p : policyTrackingRepo.findAllByOrderByPolicyKeyAsc()) {
                LocalDateTime at = toDateTime(p.getAsOfDate(), null);
                if (at == null || at.isBefore(cutoff)) continue;
                if (out.size() >= 20) break;
                out.add(new Candidate("POLICY", p.getId(), nz(p.getTitle(), ""),
                        nz(p.getSummary(), ""), nz(p.getSourceName(), "정책"),
                        p.getSourceUrl(), at, nz(p.getGrade(), "U"), p.getContentHash()));
            }
        } catch (Exception e) { log.debug("delta policy collect failed", e); }
        return out;
    }

    // ---- 매핑 ----

    int matchIssue(Candidate c) {
        try {
            String text = (nz(c.titleKr(), "") + " " + nz(c.summaryKr(), "")).toLowerCase();
            List<Map<String, Object>> issues = issueService.getIssues();
            if (issues == null || issues.isEmpty()) return -1;
            int best = -1;
            int bestHits = 0;
            for (Map<String, Object> issue : issues) {
                Object kw = issue.get("keywords");
                if (!(kw instanceof List)) continue;
                int hits = 0;
                for (Object k : (List<?>) kw) {
                    String keyword = String.valueOf(k).toLowerCase();
                    if (!keyword.isBlank() && text.contains(keyword)) hits++;
                }
                if (hits > bestHits) {
                    bestHits = hits;
                    best = toInt(issue.get("number"));
                }
            }
            return bestHits > 0 ? best : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    int score(Candidate c) {
        int s = switch (c.sourceKind()) {
            case "CHINA_DISCLOSURE", "DART" -> 100;
            case "POLICY" -> "A".equals(c.grade()) ? 90 : 50;
            case "GAME" -> 80;
            case "CHINA_NEWS" -> 50;
            default -> 30;
        };
        try {
            if (c.eventAt() != null && c.eventAt().isAfter(LocalDateTime.now(KST).minusHours(24))) {
                s += 20;
            }
        } catch (Exception ignored) {}
        return s;
    }

    private Map<String, Object> toMap(Candidate c, int issueNumber) {
        Map<String, Object> m = new LinkedHashMap<>();
        String shortName = "";
        try {
            shortName = issueService.findByNumber(issueNumber)
                    .map(x -> String.valueOf(x.get("shortName"))).orElse("");
        } catch (Exception ignored) {}
        m.put("issueNumber", issueNumber);
        m.put("issueShortName", shortName);
        m.put("dateLabel", c.eventAt() != null
                ? c.eventAt().getMonthValue() + "월 " + c.eventAt().getDayOfMonth() + "일" : "");
        m.put("kindLabel", switch (c.sourceKind()) {
            case "CHINA_DISCLOSURE" -> "중국 공시";
            case "CHINA_NEWS" -> "중국 뉴스";
            case "DART" -> "국내 공시";
            case "GAME" -> "나이트 크로우";
            case "POLICY" -> "제도";
            default -> "기타";
        });
        m.put("title", nz(c.titleKr(), ""));
        m.put("summary", firstTwoSentences(c.summaryKr()));
        m.put("sourceUrl", nz(c.sourceUrl(), ""));
        m.put("sourceKind", c.sourceKind());
        return m;
    }

    private static String firstTwoSentences(String s) {
        if (s == null || s.isBlank()) return "";
        try {
            String[] parts = s.split("(?<=[.!?。])\\s*");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(2, parts.length); i++) {
                if (i > 0) sb.append(" ");
                sb.append(parts[i].trim());
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return s.length() > 200 ? s.substring(0, 200) : s;
        }
    }

    private static LocalDateTime toDateTime(LocalDate d, LocalDateTime fallback) {
        if (d != null) return d.atStartOfDay();
        return fallback;
    }

    private static String nz(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return -1; }
    }
}
