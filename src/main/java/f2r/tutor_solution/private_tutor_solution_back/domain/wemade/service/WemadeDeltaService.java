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

    /** 후보 항목 */
    public record Candidate(
            String sourceKind,   // CHINA_DISCLOSURE, CHINA_NEWS, DART, GAME, POLICY
            Long id,
            String titleKr,
            String summaryKr,
            String sourceName,
            String sourceUrl,
            LocalDateTime eventAt,
            String grade,
            String contentHash
    ) {}

    /** 선정된 delta 항목 */
    public record DeltaItem(
            int issueNumber,
            String issueShortName,
            String dateLabel,
            String kindLabel,
            String title,
            String summary,
            String sourceUrl,
            String sourceKind
    ) {}

    public List<DeltaItem> buildDelta() {
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
        record Scored(Candidate c, int issue, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            int issue = matchIssue(c);
            if (issue < 1) continue; // 매핑 안 되면 제외
            scored.add(new Scored(c, issue, score(c)));
        }

        // 공시 우선: 같은 사실이면 공시만 (같은 issue+날짜에서 공시가 있으면 언론 제외)
        // 상위 3건, 같은 쟁점 최대 1건
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        Set<Integer> usedIssues = new HashSet<>();
        List<DeltaItem> result = new ArrayList<>();
        for (Scored s : scored) {
            if (result.size() >= 3) break;
            if (!usedIssues.add(s.issue())) continue;
            result.add(toDeltaItem(s.c(), s.issue()));
        }
        return result;
    }

    // ---- 수집 ----

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
        } catch (Exception e) { log.warn("delta chinaDisclosure collect failed", e); }
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
        } catch (Exception e) { log.warn("delta chinaNews collect failed", e); }
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
        } catch (Exception e) { log.warn("delta dart collect failed", e); }
        return out;
    }

    private List<Candidate> collectGame(LocalDateTime cutoff) {
        List<Candidate> out = new ArrayList<>();
        try {
            for (var g : gameTrackingRepo.findAllByOrderByTrackedAtDesc()) {
                LocalDateTime at = toDateTime(g.getTrackedAt(), g.getCollectedAt());
                if (at == null || at.isBefore(cutoff)) continue;
                if (out.size() >= 20) break;
                String kind = "GAME";
                out.add(new Candidate(kind, g.getId(),
                        nz(g.getGameName(), "") + " · " + nz(g.getItem(), ""),
                        nz(g.getDetail(), ""), nz(g.getSourceName(), "게임 트래킹"),
                        g.getSourceUrl(), at, "B", g.getContentHash()));
            }
        } catch (Exception e) { log.warn("delta game collect failed", e); }
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
        } catch (Exception e) { log.warn("delta policy collect failed", e); }
        return out;
    }

    // ---- 매핑 (§5-2) ----

    int matchIssue(Candidate c) {
        String text = (nz(c.titleKr(), "") + " " + nz(c.summaryKr(), "")).toLowerCase();
        List<Map<String, Object>> issues = issueService.getIssues();
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
    }

    // ---- 점수 (§5-3) ----

    int score(Candidate c) {
        int s = switch (c.sourceKind()) {
            case "CHINA_DISCLOSURE", "DART" -> 100;
            case "POLICY" -> "A".equals(c.grade()) ? 90 : 50;
            case "GAME" -> 80;
            case "CHINA_NEWS" -> 50;
            default -> 30;
        };
        // 최근성 가산 (24h 이내 +20)
        if (c.eventAt() != null && c.eventAt().isAfter(LocalDateTime.now(KST).minusHours(24))) {
            s += 20;
        }
        return s;
    }

    // ---- 변환 ----

    private DeltaItem toDeltaItem(Candidate c, int issueNumber) {
        String shortName = issueService.findByNumber(issueNumber)
                .map(m -> String.valueOf(m.get("shortName"))).orElse("");
        String dateLabel = c.eventAt() != null
                ? c.eventAt().getMonthValue() + "월 " + c.eventAt().getDayOfMonth() + "일" : "";
        String kindLabel = switch (c.sourceKind()) {
            case "CHINA_DISCLOSURE" -> "중국 공시";
            case "CHINA_NEWS" -> "중국 뉴스";
            case "DART" -> "국내 공시";
            case "GAME" -> "나이트 크로우";
            case "POLICY" -> "제도";
            default -> "기타";
        };
        String summary = firstTwoSentences(c.summaryKr());
        return new DeltaItem(issueNumber, shortName, dateLabel, kindLabel,
                c.titleKr(), summary, c.sourceUrl(), c.sourceKind());
    }

    private static String firstTwoSentences(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.split("(?<=[.!?。])\\s*");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (i > 0) sb.append(" ");
            sb.append(parts[i].trim());
        }
        return sb.toString().trim();
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
