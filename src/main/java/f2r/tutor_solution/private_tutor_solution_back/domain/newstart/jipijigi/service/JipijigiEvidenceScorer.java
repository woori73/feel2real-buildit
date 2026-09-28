package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartReviewWarning;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 근거 1건의 "이 질문과의 관련도"를 계산하는 어휘 스코어.
 *
 * <p>기존 검색은 {@code keywordHits}(일치 키워드 개수)와 최신순만 봤다. 대표 질문처럼 결정적 사건어
 * (기한이익상실·이자·연체)가 12개 토 제한에 밀려 잘려 나가면, 무관한 최신 글이 컨텍스트를 채우고
 * 실경험은 아래로 가라앉는다. 여기서부터는 <b>단어 개수가 아니라 단어의 무게</b>로 순위 매긴다:
 * 사건·상태·기관 어휘는 높게, 일반 주제어는 낮게 두고, 부분어(담보)는 상위 구(후순위담보)에 흡수시킨다.
 *
 * <p>스키마 변경 없이 텍스트 매칭으로 계산한다. 공백은 제거하고 대조하므로 "후순위 담보"와
 * "후순위담보"가 같은 항으로 인식된다(한글 본문은 조사·공백 생략이 흔하다).
 */
public final class JipijigiEvidenceScorer {

    /** 같은 사건 어구(weight ≥ {@value #EVENT_PHRASE_MIN_WEIGHT}) 일치 가점. */
    public static final int SAME_EVENT_BONUS = 4;
    /** 같은 사건은 아니지만 질문의 사건어 2개 이상과 교차할 때의 부분 가점. */
    public static final int PARTIAL_EVENT_BONUS = 2;
    /** 채무 유형 / 진행 단계 / 기관 슬롯이 질문과 일치할 때의 가점(슬롯당). */
    public static final int SLOT_BONUS = 2;
    /** 행동 기록(방문·접수·납부·상담 등 실제 수행 서술) 가점. */
    public static final int ACTION_RECORD_BONUS = 2;
    /** 결과 기록(사후 경과·확정 값) 가점. */
    public static final int RESULT_RECORD_BONUS = 2;
    /** "같은 사건"으로 보는 최소 어휘 가중치. */
    public static final int EVENT_PHRASE_MIN_WEIGHT = 4;

    public enum Category {
        /** 사건·통보·연체류 — 사용자가 새로 겪은 일. */
        EVENT,
        /** 절차 단계·행위(임시안, 접수, 납부…). */
        STAGE,
        /** 기관(신복위, 금감원, 법원…). */
        INSTITUTION,
        /** 채무·담보 유형(후순위, 담보, 중개형…). */
        DEBT_TYPE,
        /** 일반 주제어(감면, 회생…). 순위 신호로는 약하다. */
        TOPIC
    }

    /** 리스크 코드별 감점. ACTIVE라고 동등한 신뢰를 주지 않는다(운영 원칙: 충돌 근거 단독 확정 금지). */
    private static final Map<String, Integer> RISK_PENALTY = Map.of(
            "CONFLICT_RISK", 6,
            "MISSING_SOURCE", 3,
            "UNKNOWN_RISK", 2,
            "HISTORICAL", 2,
            "CASE_DERIVED", 1,
            "UNCLEAR_AUTHORITY", 1);

    private static final Map<String, Term> TERMS = buildTerms();
    /** 긴 항이 먼저 오도록 정렬 — 축약(maximal) 판정을 한 번에 하기 위함. */
    private static final List<Term> TERMS_BY_LENGTH_DESC = new ArrayList<>(TERMS.values());

    static {
        TERMS_BY_LENGTH_DESC.sort((a, b) -> Integer.compare(b.key.length(), a.key.length()));
    }

    private JipijigiEvidenceScorer() {
    }

    /** 검색 질의(OR LIKE)에 넣을 어휘 토큰. 공백 없는 형태라 DB 텍스트 부분 일치에 바로 쓴다. */
    public static List<String> vocabulary() {
        return List.copyOf(TERMS.keySet());
    }

    /** 질문 텍스트에 실제로 등장하는 어휘(카테고리별 슬롯 추출용). 축약 없이 전부 포함. */
    public static List<Term> termsIn(String text) {
        String haystack = compress(text);
        if (haystack.isEmpty()) return List.of();
        List<Term> out = new ArrayList<>();
        for (Term term : TERMS_BY_LENGTH_DESC) {
            if (haystack.contains(term.key)) out.add(term);
        }
        return out;
    }

    /**
     * 근거 텍스트 1건에 대해 <b>유효한</b> 일치 어휘만 남긴다(부분어 축약).
     * 예: "후순위담보"가 매치되면 "후순위"·"담보"는 따로 계산하지 않는다 — 중복 계산이 상위 구의
     * 가중치를 희석하기 때문이다.
     */
    public static List<String> matchedTerms(String text) {
        List<Term> hits = termsIn(text);
        List<String> out = new ArrayList<>();
        for (Term candidate : hits) {
            boolean absorbed = false;
            for (Term other : hits) {
                if (other != candidate && other.key.length() > candidate.key.length()
                        && other.weight >= candidate.weight && other.key.contains(candidate.key)) {
                    absorbed = true;
                    break;
                }
            }
            String label = candidate.display;
            if (!absorbed && !out.contains(label)) out.add(label);
        }
        return out;
    }

    /** 어휘 가중치 합(축약 후). */
    public static int termScore(String text) {
        String haystack = compress(text);
        List<Term> hits = termsIn(haystack);
        int score = 0;
        for (Term candidate : hits) {
            boolean absorbed = false;
            for (Term other : hits) {
                if (other != candidate && other.key.length() > candidate.key.length()
                        && other.weight >= candidate.weight && other.key.contains(candidate.key)) {
                    absorbed = true;
                    break;
                }
            }
            if (!absorbed) score += candidate.weight;
        }
        return score;
    }

    /** 이 근거가 "같은 사건"을 담고 있는지 — weight ≥ {@value #EVENT_PHRASE_MIN_WEIGHT} 사건어. */
    public static boolean hasStrongEventTerm(String text) {
        for (Term term : termsIn(text)) {
            if (term.category == Category.EVENT && term.weight >= EVENT_PHRASE_MIN_WEIGHT) return true;
        }
        return false;
    }

    /** 일반 어휘만 있는(레거시 키워드 질의) 프레임에서 쓰는 카테고리 필터 노출용 게터. */
    static List<String> termsOfCategory(String text, Category category) {
        List<String> out = new ArrayList<>();
        for (Term term : termsIn(text)) {
            if (term.category == category && !out.contains(term.display)) out.add(term.display);
        }
        return out;
    }

    /** ACTIVE 지식의 사전경고 코드를 재사용한다 — 스키마 추가 없이 기존 필드에서 파생된 값. */
    public static List<String> riskCodes(NewstartKnowledgeItem item) {
        List<String> codes = new ArrayList<>();
        for (NewstartReviewWarning warning : NewstartKnowledgeService.computeWarnings(item)) {
            codes.add(warning.getCode());
        }
        return List.copyOf(codes);
    }

    /** 수집 사례에 쓸 수 있는 리스크 코드(출처 URL 유무). */
    public static List<String> riskCodesForSource(String sourceUrl) {
        return sourceUrl == null || sourceUrl.isBlank() ? List.of("MISSING_SOURCE") : List.of();
    }

    public static int riskPenalty(List<String> codes) {
        int penalty = 0;
        for (String code : codes) {
            penalty += RISK_PENALTY.getOrDefault(code, 0);
        }
        return penalty;
    }

    /** 컨텍스트에 붙이는 주의 라벨. 모델이 이 근거를 확정 사실로 쓰지 못하게 표시한다. */
    public static String riskLabel(List<String> codes) {
        if (codes == null || codes.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("주의:");
        for (String code : codes) {
            sb.append(' ').append(code);
        }
        return sb.toString();
    }

    static String compress(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "");
    }

    /** 어휘 1항. {@code key}는 공백 제거 매칭 키, {@code display}는 감사 로그용 표기. */
    public record Term(String key, String display, int weight, Category category) {
    }

    private static Map<String, Term> buildTerms() {
        Map<String, Term> m = new LinkedHashMap<>();
        // ── 사건(EVENT) ──
        put(m, "기한이익상실", 4, Category.EVENT);
        put(m, "기한이익", 2, Category.EVENT);
        put(m, "연체", 2, Category.EVENT);
        put(m, "이자", 2, Category.EVENT);
        put(m, "추심", 2, Category.EVENT);
        put(m, "독촉", 2, Category.EVENT);
        put(m, "압류", 2, Category.EVENT);
        put(m, "경매", 2, Category.EVENT);
        put(m, "민원", 2, Category.EVENT);
        put(m, "담보기관", 2, Category.INSTITUTION);
        // 사건 구(2~3어 조합) — 질문에서 함께 나타나는 실경험 근거를 최우선으로 끌어올린다.
        put(m, "후순위담보", 4, Category.DEBT_TYPE);
        put(m, "담보기한이익상실", 5, Category.EVENT);
        put(m, "기한이익상실통보", 5, Category.EVENT);
        put(m, "기한이익상실신복위", 5, Category.EVENT);
        put(m, "부실차주기한이익상실", 5, Category.EVENT);
        put(m, "이자연체", 5, Category.EVENT);
        put(m, "담보연체", 5, Category.EVENT);
        put(m, "중개형연체", 5, Category.EVENT);
        put(m, "후순위중개형", 5, Category.DEBT_TYPE);
        put(m, "신복위중개형", 5, Category.DEBT_TYPE);
        put(m, "후순위담보기한이익상실", 7, Category.EVENT);
        put(m, "담보중개형연체", 7, Category.EVENT);
        // ── 진행 단계·행위(STAGE) ──
        put(m, "임시안", 2, Category.STAGE);
        put(m, "확정안", 2, Category.STAGE);
        put(m, "접수", 2, Category.STAGE);
        put(m, "방문", 2, Category.STAGE);
        put(m, "상담", 2, Category.STAGE);
        put(m, "변제", 2, Category.STAGE);
        put(m, "납부", 2, Category.STAGE);
        put(m, "부실차주", 2, Category.STAGE);
        put(m, "약정", 1, Category.STAGE);
        put(m, "신청", 1, Category.STAGE);
        put(m, "대처", 1, Category.STAGE);
        // ── 기관(INSTITUTION) ──
        put(m, "신복위", 2, Category.INSTITUTION);
        put(m, "금감원", 2, Category.INSTITUTION);
        put(m, "법원", 2, Category.INSTITUTION);
        put(m, "신용정보원", 2, Category.INSTITUTION);
        // ── 채무·담보 유형(DEBT_TYPE) ──
        put(m, "후순위", 2, Category.DEBT_TYPE);
        put(m, "담보", 2, Category.DEBT_TYPE);
        put(m, "무담보", 2, Category.DEBT_TYPE);
        put(m, "중개형", 2, Category.DEBT_TYPE);
        put(m, "직접형", 2, Category.DEBT_TYPE);
        // ── 일반 주제어(TOPIC) ──
        put(m, "감면", 1, Category.TOPIC);
        put(m, "탕감", 1, Category.TOPIC);
        put(m, "개인회생", 1, Category.TOPIC);
        put(m, "개인워크아웃", 1, Category.TOPIC);
        put(m, "신용회복", 1, Category.TOPIC);
        put(m, "채무조정", 1, Category.TOPIC);
        put(m, "상환", 1, Category.TOPIC);
        put(m, "대출", 1, Category.TOPIC);
        put(m, "재산", 1, Category.TOPIC);
        put(m, "새출발기금", 1, Category.TOPIC);
        return m;
    }

    private static void put(Map<String, Term> m, String display, int weight, Category category) {
        String key = compress(display);
        m.put(key, new Term(key, display, weight, category));
    }
}
