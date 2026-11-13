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
 *   <li>질문이 캠퍼스를 말했고(예: "동국대 와이즈") 그 캠퍼스 표시가 붙은 근거가 있으면, 표시가 없는 근거(본교 등)는 뺀다.</li>
 *   <li>질문이 대학을 말하지 않았고 대학 이름이 없는 제도 지식 항목(예: 2028 내신 5등급제)이 관련 근거로 있으면,
 *       특정 대학 근거(제목에 대학 이름)는 뺀다 — 일반 제도 질문에 무관한 대학 시행계획이 출처로 붙지 않게.</li>
 *   <li>일정을 묻는 질문이면 일정 근거에 가산점을 준다(같은 대학 전형 행이 일정 행을 밀어내지 않게).</li>
 *   <li>관련도(주제어 일치 수)가 높은 순, 같으면 공식 자료 등급 순, 그래도 같으면 원래 순서로 상위 N개만 남긴다.
 *       주제어는 띄어쓰기를 무시하고 비교한다("원서접수" ↔ "입학원서 접수").</li>
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
        return select(evidence, question, limit, true);
    }

    /** @param universityScoped 질문이 대학을 말했거나 전체 대학 목록을 묻는지. false면 일반 제도 질문 규칙을 적용한다. */
    static List<Evidence> select(List<Evidence> evidence, String question, int limit, boolean universityScoped) {
        List<Evidence> unique = dedupe(evidence);
        Set<String> terms = topicTerms(question);
        if (!universityScoped && unique.stream().anyMatch(e -> isGeneralPolicy(e) && relevance(e, terms) > 0)) {
            unique = unique.stream().filter(e -> !namesSpecificUniversity(e.getTitle())).toList();
        }
        String track = askedTrack(question);
        String campus = presentCampus(question, unique);
        boolean scheduleQuestion = question != null && SCHEDULE_INTENT.matcher(question).find();

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            Evidence e = unique.get(i);
            if (e.getSourceClass() == SourceClass.GUIDE_NOTE) continue;
            if (track != null && oppositeTrackTitle(e.getTitle(), track)) continue;
            if (campus != null && !compact(e.getTitle()).contains(campus)) continue;
            int score = relevance(e, terms) + (scheduleQuestion && isSchedule(e) ? 1 : 0);
            scored.add(new Scored(e, i, score));
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
        String hay = compact(e.getTitle()) + " " + compact(e.getExcerpt());
        int score = 0;
        for (String term : terms) {
            String t = compact(term);
            if (hay.contains(t) || expand(term).stream().map(AdmissionsSourceSelector::compact).anyMatch(hay::contains)) score++;
        }
        return score;
    }

    /** 일정 질문 판단 — 질문 분석기(AdmissionsQueryParser)의 일정 의도 단어와 같게 두고 "기간"을 더한다. */
    private static final java.util.regex.Pattern SCHEDULE_INTENT =
            java.util.regex.Pattern.compile("일정|언제|접수|원서|발표|마감|까지|기간|며칠|날짜|몇\\s*월");

    /** 일정 근거: 일정 행(sched:) 또는 일정에서 만든 지식 항목(제목 "…학년도 일정: …"). */
    static boolean isSchedule(Evidence e) {
        return (e.getRefId() != null && e.getRefId().startsWith("sched:"))
                || (e.getTitle() != null && e.getTitle().contains("일정:"));
    }

    /**
     * 질문이 말한 캠퍼스 표시(대문자·띄어쓰기 제거, 예: "WISE캠퍼스"). 근거 중 그 표시가 붙은 것이 하나도 없으면 null —
     * "세종대"처럼 캠퍼스 줄임말과 겹치는 대학 이름 질문에서 근거를 모두 지우지 않기 위함이다.
     */
    static String presentCampus(String question, List<Evidence> evidence) {
        String marker = AdmissionsQueryParser.askedCampus(question);
        if (marker == null) return null;
        String m = compact(marker);
        return evidence.stream().anyMatch(e -> compact(e.getTitle()).contains(m)) ? m : null;
    }

    /** 비교용: 띄어쓰기를 지우고 대문자로("WISE"·"wise" 같게). */
    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase();
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

    private static final java.util.regex.Pattern UNIVERSITY_NAME =
            java.util.regex.Pattern.compile("[가-힣]+대학교|(?<![가-힣])[가-힣]{1,5}대(?=\\s*\\d{4})");

    /** 제목에 특정 대학 이름이 있는지(예: "연세대학교 …", "인하대 2027 …"). "대학별"·"대학입학"은 해당하지 않는다. */
    static boolean namesSpecificUniversity(String title) {
        return title != null && UNIVERSITY_NAME.matcher(title).find();
    }

    /** 대학 이름이 없는 지식 항목 — 교육부·평가원·대교협 제도 발표 같은 일반 제도 근거. */
    private static boolean isGeneralPolicy(Evidence e) {
        return e.getRefId() != null && e.getRefId().startsWith("knowledge:") && !namesSpecificUniversity(e.getTitle());
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
