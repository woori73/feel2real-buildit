package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 답변 아래 "출처" 패널에 보여줄 근거를 고른다. 화면 표시만 정리하며, LLM 프롬프트에 들어간 근거와 답변 생성 규칙은
 * 바꾸지 않는다.
 *
 * <ol>
 *   <li>같은 문서·같은 제목(전형 행과 지식 행 등)은 하나로 합친다.</li>
 *   <li>질문에서 뽑은 주제어가 제목·내용에 거의 없으면 뺀다(질문과 무관한 논술·실기·다른 트랙 일정). 공식 문서(material:)
 *       링크는 최대 {@value #MAX_BARE_MATERIALS}건까지 예외로 남긴다.</li>
 *   <li>질문이 수시/정시 한쪽만 말하면 반대 트랙 제목은 뺀다.</li>
 *   <li>관련도(주제어 일치 수)가 높은 순, 같으면 공식 자료 등급 순, 그래도 같으면 원래 순서로 상위 N개만 남긴다.</li>
 * </ol>
 * 걸러낸 뒤 남는 것이 없으면 원래 순서의 앞 {@value #FALLBACK_COUNT}건을 그대로 보여준다(출처 없는 답변 방지).
 */
final class AdmissionsSourceSelector {

    static final int FALLBACK_COUNT = 3;
    /** 최고 관련도의 이 비율 미만이면 약한 관련으로 보고 뺀다. */
    private static final double RELATIVE_CUTOFF = 0.6;
    /** 주제어와 맞지 않아도 공식 문서(모집요강 원문) 링크는 이 개수까지 남긴다 — 학과·전형 행의 상위 문서. */
    private static final int MAX_BARE_MATERIALS = 2;

    private static final Set<String> IGNORED_TERMS = Set.of(
            "알려줘", "알려주세요", "있어", "있나요", "있나", "어떻게", "어떤", "무엇", "언제", "얼마", "궁금", "일정",
            "관련", "경우", "질문", "대해", "대한", "입시", "대입", "학년도", "모집", "자료");

    private AdmissionsSourceSelector() {}

    static List<Evidence> select(List<Evidence> evidence, String question, int limit) {
        List<Evidence> unique = dedupe(evidence);
        Set<String> terms = topicTerms(question);
        String track = askedTrack(question);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            Evidence e = unique.get(i);
            if (e.getSourceClass() == SourceClass.GUIDE_NOTE) continue;
            if (track != null && oppositeTrackTitle(e.getTitle(), track)) continue;
            scored.add(new Scored(e, i, relevance(e, terms)));
        }
        if (scored.isEmpty()) return unique.stream().limit(FALLBACK_COUNT).toList();

        int best = scored.stream().mapToInt(Scored::score).max().orElse(0);
        int min = terms.isEmpty() ? 0 : Math.max(1, (int) Math.ceil(best * RELATIVE_CUTOFF));
        List<Scored> kept = scored.stream()
                .filter(s -> s.score() >= min || isOfficialMaterial(s.evidence()))
                .toList();
        if (kept.isEmpty()) return unique.stream().limit(FALLBACK_COUNT).toList();

        List<Scored> sorted = kept.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparingInt(s -> classRank(s.evidence().getSourceClass()))
                        .thenComparingInt(Scored::index))
                .toList();
        List<Evidence> out = new ArrayList<>();
        int materials = 0;
        for (Scored s : sorted) {
            if (out.size() >= limit) break;
            if (isOfficialMaterial(s.evidence()) && s.score() < min && ++materials > MAX_BARE_MATERIALS) continue;
            out.add(s.evidence());
        }
        return out;
    }

    /** refId가 같거나, 구분(등급·제목·URL)이 같은 근거는 처음 것만 남긴다. */
    static List<Evidence> dedupe(List<Evidence> evidence) {
        Map<String, Evidence> byKey = new LinkedHashMap<>();
        Set<String> seenRef = new LinkedHashSet<>();
        for (Evidence e : evidence) {
            if (!seenRef.add(String.valueOf(e.getRefId()))) continue;
            String key = e.getSourceClass() + "|" + normalize(e.getTitle()) + "|" + normalize(e.getRefUrl());
            byKey.putIfAbsent(key, e);
        }
        return new ArrayList<>(byKey.values());
    }

    /** 질문에서 대학명·학년도·잡음을 뺀 주제어. 「교과전형」은 「교과」로 줄여 제목과 맞춘다. */
    static Set<String> topicTerms(String question) {
        Set<String> out = new LinkedHashSet<>();
        if (question == null) return out;
        for (String raw : question.replaceAll("[^가-힣a-zA-Z0-9]", " ").split("\\s+")) {
            String t = AdmissionsResearchService.stripParticle(raw);
            if (t.length() < 2 || IGNORED_TERMS.contains(t) || IGNORED_TERMS.contains(raw)) continue;
            if (t.matches("\\d+.*") || t.endsWith("대") || t.endsWith("대학교") || t.endsWith("대학")) continue;
            if (t.equals("수시") || t.equals("정시")) continue; // 트랙은 별도 규칙으로 처리
            if (t.length() > 2 && t.endsWith("전형")) t = t.substring(0, t.length() - 2);
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }

    private static int relevance(Evidence e, Set<String> terms) {
        String hay = normalize(e.getTitle()) + " " + normalize(e.getExcerpt());
        int score = 0;
        for (String term : terms) {
            if (hay.contains(term) || expand(term).stream().anyMatch(hay::contains)) score++;
        }
        return score;
    }

    private static List<String> expand(String term) {
        return switch (term) {
            case "수능최저" -> List.of("최저학력", "수능 최저");
            case "지원자격" -> List.of("지원 자격", "자격");
            case "교과" -> List.of("학생부교과");
            case "학종" -> List.of("학생부종합");
            default -> List.of();
        };
    }

    /** 질문이 수시 또는 정시 하나만 언급하면 그 값, 둘 다 있거나 없으면 null. */
    static String askedTrack(String question) {
        if (question == null) return null;
        boolean susi = question.contains("수시");
        boolean jungsi = question.contains("정시");
        if (susi == jungsi) return null;
        return susi ? "수시" : "정시";
    }

    private static boolean oppositeTrackTitle(String title, String asked) {
        if (title == null) return false;
        String other = asked.equals("수시") ? "정시" : "수시";
        return title.contains(other) && !title.contains(asked);
    }

    /** 공식 문서 자체(material:) — 제목에 주제어가 없어도 학과·전형 행의 출처 문서일 수 있다. 사설 미러본은 제외. */
    private static boolean isOfficialMaterial(Evidence e) {
        return e.getRefId() != null && e.getRefId().startsWith("material:")
                && e.getSourceClass() != SourceClass.UNVERIFIED_MIRROR;
    }

    private static int classRank(SourceClass c) {
        return switch (c) {
            case OFFICIAL_CONFIRMED -> 0;
            case OFFICIAL_PLANNED -> 1;
            case OFFICIAL_DOCUMENT -> 2;
            case UNVERIFIED_MIRROR -> 3;
            default -> 4;
        };
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private record Scored(Evidence evidence, int index, int score) {}
}
