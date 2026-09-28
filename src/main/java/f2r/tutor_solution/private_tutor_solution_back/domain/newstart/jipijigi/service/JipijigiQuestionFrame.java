package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiEvidenceScorer.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiEvidenceScorer.Term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 질문을 검색 전에 해석한 결과 — 의도(intent)와 "이미 아는 사실 / 새로 생긴 사건 / 실제로 묻는 필요"의 분리.
 *
 * <p>대표 질문은 본인이 이미 아는 내용(후순위 담보는 신복위 중개형으로 처리됨, 10/1 방문 예정)과
 * 방금 터진 사건(이번 달 이자 미납, 9/30 담보기관 기한이익상실 통보, 금감원 민원 접수)을 한 문단에 담고
 * 있다. 이전 구현은 문장을 쪼개지 않고 토큰 12개만 잘라 질의에 넣었기 때문에 결정적 사건어
 * (기한이익상실·이자·연체)가 질의에서 사라졌고, 답변은 사용자가 이미 아는 제도 설명부터 시작했다.
 *
 * <p>DB 구조는 바꾸지 않는다. 텍스트 규칙으로 위 슬롯과 사건 중심 다중 질의
 * ({@link #searchQueries()})를 만들고, 실제 SQL은 어휘 항 하나의 OR LIKE 질의(= 다중 질의의 합집합)로
 * 실행한 뒤 순위만 메모리에서 계산한다.
 */
public final class JipijigiQuestionFrame {

    /** OR LIKE 질의에 넣는 항 최대 개수 — 질의 폭주 방지 상한. */
    static final int MAX_QUERY_TERMS = 24;

    private static final String[] EVENT_MARKERS = {"통보", "상실", "않았", "넣었", "받았", "도착", "발생",
            "당했", "왔어요", "왔습니다", "연락와", "독촉", "압류", "추심", "연체", "미납"};
    /** "저쪽에서 온 통보·연락"은 사건, "내가 하기로 한 계획"은 이미 알려진 문맥으로 본다. */
    private static final String[] KNOWN_MARKERS = {"알고 있", "이미", "예정입니다", "예정이고", "중입니다",
            "하고 있습니다", "갈 예정", "할 예정"};
    private static final String[] NEED_MARKERS = {"해야 하나요", "해야 합니까", "어떻", "무엇을", "되나요",
            "인가요", "방법", "어느 쪽"};
    private static final String[] EXPERIENCE_ASK_MARKERS = {"사례", "경험", "후기", "했던", "했나요", "받은 적",
            "된 분", "어땠", "어떻게 대처했"};
    private static final String[] ACTION_ASK_MARKERS = {"어떻게", "어떻하", "해야", "대처", "대응", "방법",
            "무엇을", "하면 된"};
    private static final String[] POLICY_ASK_MARKERS = {"기준", "요건", "제도", "무엇", "차이", "언제부터",
            "며칠", "얼마", "기간", "되나요", "절차", "구분", "사실인가요", "뭐"};
    /** 근거 텍스트에서 "본인이 실제로 한 행동"을 가르는 서술어. */
    private static final String[] ACTION_RECORD_MARKERS = {"방문", "접수", "납부", "상담", "제출", "연락",
            "확인", "신청", "대처", " 통화"};

    private final String question;
    private final JipijigiIntent intent;
    private final List<String> knownContext;
    private final List<String> newEvents;
    private final String actualNeed;
    /** 사건 문장(+실제로 묻는 문장)에서 추출한 결정적 사건어. */
    private final List<String> eventTerms;
    private final List<String> debtTypeTerms;
    private final List<String> stageTerms;
    private final List<String> institutionTerms;
    /** 실제 DB OR 질의에 넣는 항. */
    private final List<String> queryTerms;
    /** 사건 중심 다중 질의(감사 로그·디버깅용). */
    private final List<String> searchQueries;
    private final List<String> keywords;
    private final boolean legacy;

    private JipijigiQuestionFrame(String question, JipijigiIntent intent, List<String> knownContext,
                                  List<String> newEvents, String actualNeed, List<String> eventTerms,
                                  List<String> debtTypeTerms, List<String> stageTerms,
                                  List<String> institutionTerms, List<String> queryTerms,
                                  List<String> searchQueries, List<String> keywords, boolean legacy) {
        this.question = question;
        this.intent = intent;
        this.knownContext = List.copyOf(knownContext);
        this.newEvents = List.copyOf(newEvents);
        this.actualNeed = actualNeed;
        this.eventTerms = List.copyOf(eventTerms);
        this.debtTypeTerms = List.copyOf(debtTypeTerms);
        this.stageTerms = List.copyOf(stageTerms);
        this.institutionTerms = List.copyOf(institutionTerms);
        this.queryTerms = List.copyOf(queryTerms);
        this.searchQueries = List.copyOf(searchQueries);
        this.keywords = List.copyOf(keywords);
        this.legacy = legacy;
    }

    /**
     * 레거시 키워드 질의 전용 프레임 — 의도 해석과 어휘 가점을 꺼서 기존
     * {@code keywordHits + 최신순} 계약(호출부·테스트)을 그대로 유지한다.
     */
    public static JipijigiQuestionFrame legacy(List<String> keywords) {
        List<String> kws = keywords == null ? List.of() : keywords;
        return new JipijigiQuestionFrame("", JipijigiIntent.POLICY, List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), kws, List.of(), kws, true);
    }

    public static JipijigiQuestionFrame analyze(String questionText, List<String> keywords) {
        String text = questionText == null ? "" : questionText;
        List<String> known = new ArrayList<>();
        List<String> events = new ArrayList<>();
        String need = null;
        for (String sentence : sentencesOf(text)) {
            if (need == null && containsAny(sentence, NEED_MARKERS)) {
                need = sentence;
                continue;
            }
            if (containsAny(sentence, EVENT_MARKERS)) {
                events.add(sentence);
                continue;
            }
            known.add(sentence); // 배경·계획·알고 있다는 서술 = 이미 알려진 정보
        }

        JipijigiIntent intent = classify(
                containsAny(text, EXPERIENCE_ASK_MARKERS),
                containsAny(text, ACTION_ASK_MARKERS),
                containsAny(text, POLICY_ASK_MARKERS),
                !events.isEmpty());

        // 사건어는 "새로 생긴 사건 + 실제로 묻는 문장"에서만 뽑는다. 이미 아는 내용에 적힌
        // 제도어(감면·신용회복 등)가 순위 신호를 덮지 않게 하기 위해서다.
        String eventText = need == null ? String.join(" ", events) : String.join(" ", events) + " " + need;
        List<String> eventTerms = displayTerms(eventText, Category.EVENT);
        List<String> debtTypes = displayTerms(text, Category.DEBT_TYPE);
        List<String> stages = displayTerms(text, Category.STAGE);
        List<String> institutions = displayTerms(text, Category.INSTITUTION);

        Set<String> query = new LinkedHashSet<>();
        for (Term term : JipijigiEvidenceScorer.termsIn(text)) {
            query.add(term.key());
        }
        if (keywords != null) {
            query.addAll(keywords); // 레거시 토큰도 남겨 두지 않으면 축적 답변 세션·기존 질의 동작이 바뀐다.
        }
        List<String> queryTerms = trimTo(query, MAX_QUERY_TERMS);

        return new JipijigiQuestionFrame(text, intent, known, events, need, eventTerms, debtTypes, stages,
                institutions, queryTerms, buildSearchQueries(eventTerms, debtTypes, institutions),
                keywords == null ? List.of() : keywords, false);
    }

    private static JipijigiIntent classify(boolean experience, boolean action, boolean policy, boolean personalEvent) {
        if (personalEvent && (action || experience)) return JipijigiIntent.EXPERIENCE_ACTION;
        if (experience) return policy ? JipijigiIntent.MIXED : JipijigiIntent.EXPERIENCE;
        if (action) return policy ? JipijigiIntent.MIXED : JipijigiIntent.ACTION;
        return JipijigiIntent.POLICY; // 기본값 — 기존 공식 우선 동작을 유지한다.
    }

    /**
     * 사건어 × 채무유형/기관 조합의 사건 중심 질의. 대표 질문에서
     * "후순위담보 기한이익상실", "기한이익상실 신복위", "이자 연체"류 질의가 만들어진다.
     */
    private static List<String> buildSearchQueries(List<String> eventTerms, List<String> debtTypes,
                                                   List<String> institutions) {
        Set<String> out = new LinkedHashSet<>(eventTerms);
        for (String event : eventTerms) {
            for (String debt : debtTypes) {
                out.add(debt + " " + event);
            }
            for (String institution : institutions) {
                out.add(event + " " + institution);
            }
        }
        for (String debt : debtTypes) {
            for (String institution : institutions) {
                out.add(debt + " " + institution);
            }
        }
        return trimTo(out, MAX_QUERY_TERMS);
    }

    /** 같은 사건 일치 등급(2=같은 사건, 1=사건어 교차, 0=무관). */
    private int eventMatchLevel(String rowText) {
        if (legacy || rowText == null || rowText.isBlank() || eventTerms.isEmpty()) return 0;
        String haystack = JipijigiEvidenceScorer.compress(rowText);
        int crossed = 0;
        for (String event : eventTerms) {
            if (haystack.contains(JipijigiEvidenceScorer.compress(event))) crossed++;
        }
        if (crossed == 0) return 0;
        for (Term term : JipijigiEvidenceScorer.termsIn(haystack)) {
            boolean questionEvent = term.category() == Category.EVENT
                    && term.weight() >= JipijigiEvidenceScorer.EVENT_PHRASE_MIN_WEIGHT
                    && eventTerms.contains(term.display());
            if (questionEvent) return 2;
        }
        return crossed >= 2 ? 1 : 0;
    }

    /**
     * 근거가 질문의 "정확히 같은 사건"을 담고 있는지. DB에 같은 사건이 없을 때 답변이 없는 사례를
     * 지어내지 않도록 정직하게 알리는 경로({@link #EXPERIENCE_NOT_FOUND_MESSAGE})의 판정 근거다.
     */
    public boolean exactEventMatch(String rowText) {
        return eventMatchLevel(rowText) == 2;
    }

    /** 질문 슬롯(같은 사건·채무 유형·진행 단계·기관) 일치 가점. */
    public int alignmentBonus(String rowText) {
        if (legacy) return 0;
        int level = eventMatchLevel(rowText);
        int bonus = level == 2 ? JipijigiEvidenceScorer.SAME_EVENT_BONUS
                : level == 1 ? JipijigiEvidenceScorer.PARTIAL_EVENT_BONUS : 0;
        if (anyPresent(debtTypeTerms, rowText)) bonus += JipijigiEvidenceScorer.SLOT_BONUS;
        if (anyPresent(stageTerms, rowText)) bonus += JipijigiEvidenceScorer.SLOT_BONUS;
        if (anyPresent(institutionTerms, rowText)) bonus += JipijigiEvidenceScorer.SLOT_BONUS;
        return bonus;
    }

    /**
     * 행동 기록 가점 여부 — 근거 글에 실제 수행(방문·접수·납부·상담·제출·연락)이 서술됐는지.
     * 제도 인용글과 실경험을 가르는 신호다.
     */
    public boolean hasActionRecord(String rowText) {
        return !legacy && containsAny(rowText, ACTION_RECORD_MARKERS);
    }

    /** 답변에서 되풀이하지 말아야 할 "사용자가 이미 말한 사실". */
    public List<String> knownFacts() {
        List<String> out = new ArrayList<>(knownContext);
        if (intent == JipijigiIntent.EXPERIENCE_ACTION) out.addAll(newEvents);
        return List.copyOf(out);
    }

    public JipijigiIntent intent() {
        return intent;
    }

    public boolean experienceFirst() {
        return !legacy && intent.experienceFirst();
    }

    public boolean legacy() {
        return legacy;
    }

    public String question() {
        return question;
    }

    public List<String> knownContext() {
        return knownContext;
    }

    public List<String> newEvents() {
        return newEvents;
    }

    public String actualNeed() {
        return actualNeed;
    }

    public List<String> eventTerms() {
        return eventTerms;
    }

    public List<String> debtTypeTerms() {
        return debtTypeTerms;
    }

    public List<String> stageTerms() {
        return stageTerms;
    }

    public List<String> institutionTerms() {
        return institutionTerms;
    }

    public List<String> queryTerms() {
        return queryTerms;
    }

    public List<String> searchQueries() {
        return searchQueries;
    }

    public List<String> keywords() {
        return keywords;
    }

    /** 같은 사건이 DB에 없을 때 쓰는 고정 문장(요구사항 10). 답변 생성 프롬프트에 그대로 실는다. */
    public static final String EXPERIENCE_NOT_FOUND_MESSAGE =
            "현재 지피지기 DB에서는 정확히 같은 사례를 찾지 못했습니다.";

    private static List<String> displayTerms(String text, Category category) {
        Set<String> out = new LinkedHashSet<>();
        for (Term term : JipijigiEvidenceScorer.termsIn(text)) {
            if (term.category() == category) out.add(term.display());
        }
        return new ArrayList<>(out);
    }

    private static List<String> sentencesOf(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("[\\n]+|(?<=[.?!])")) {
            String s = part.strip();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static boolean anyPresent(List<String> terms, String rowText) {
        if (rowText == null) return false;
        String haystack = JipijigiEvidenceScorer.compress(rowText);
        for (String term : terms) {
            if (haystack.contains(JipijigiEvidenceScorer.compress(term))) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String[] needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (!needle.isBlank() && text.contains(needle)) return true;
        }
        return false;
    }

    private static List<String> trimTo(Set<String> in, int max) {
        List<String> out = new ArrayList<>(in);
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }
}
