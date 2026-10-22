package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 입시 질문 분석기 (규칙 기반, LLM 호출 없음). 순수 함수라 단위 테스트가 쉽다.
 *
 * <p>질문에서 대학(별칭), 학년도, 의도(학과 모집/전형 요건/일정)와 답변 금지 주제
 * (합격 가능성 판단, 수집하지 않은 자료)를 뽑는다.
 */
public final class AdmissionsQueryParser {

    private AdmissionsQueryParser() {}

    /** 상담이 다루는 학년도. */
    public static final Set<Integer> SUPPORTED_YEARS = Set.of(2027, 2028);

    private static final Pattern YEAR = Pattern.compile("(20[2-3]\\d)");

    /**
     * 과거 학년도 표기("2025학년도", "2025년도", "25학년도"). 숫자만 있는 "2022 개정교육과정", "2000명" 같은 표현은
     * 학년도로 보지 않도록 반드시 학년도/년 접미사가 있을 때만 인식한다.
     */
    private static final Pattern YEAR_WITH_SUFFIX = Pattern.compile(
            "(?<!\\d)(20[0-2]\\d)\\s*(?:학년도|학년|년도|년)(?!\\s*개정)");
    private static final Pattern YEAR_TWO_DIGIT = Pattern.compile("(?<![\\d.])(\\d{2})\\s*학년도");

    /** 합격 가능성·합격선·지원 조합 추천 등 판단/예측성 질문 — 답변하지 않는다. */
    private static final Pattern CHANCE = Pattern.compile(
            "합격\\s*(가능|확률|선|컷|권)|지원\\s*가능할까|어디쯤|추천해|소신|안정\\s*지원|상향\\s*지원|하향\\s*지원|붙을까|될까요");

    /** 아직 수집하지 않은 자료(경쟁률·입시결과·권장이수 등) — 만들어 답하지 않는다. */
    private static final Pattern NOT_COLLECTED = Pattern.compile(
            "경쟁률|입시\\s*결과|입결|충원|등급\\s*컷|평균\\s*등급|권장\\s*이수|권장\\s*과목");

    /** 전국 단위 총량 통계(수시/정시 비중, 전체 모집인원 등) — 대학을 특정하지 않으면 수집된 자료가 없다. */
    private static final Pattern AGGREGATE_STATS = Pattern.compile(
            "(수시|정시)\\s*(비중|비율|인원)|전체\\s*모집|총\\s*모집|전국|비중|몇\\s*퍼센트|몇\\s*%");

    private static final Pattern SCHEDULE_INTENT = Pattern.compile(
            "일정|언제|접수|원서|발표|마감|까지|입력|등록|며칠|날짜|몇\\s*월");

    private static final Pattern TYPE_INTENT = Pattern.compile(
            "전형|지원\\s*자격|자격|지원\\s*(가능|되나)|수능\\s*최저|최저|수시|논술|학종|교과|면접|서류|검정고시|자율고|N수|재수");

    private static final Pattern QUOTA_INTENT = Pattern.compile(
            "모집|인원|몇\\s*명|뽑|정원|창구|많이");

    /** 일정 제목과 질문에 같이 나오면 같은 일정으로 보는 용어. */
    static final List<String> SCHEDULE_TERMS = List.of(
            "접수", "원서", "입력", "발표", "면접", "논술", "등록", "서류", "합격자", "납부", "실기", "제출", "추가합격", "고사");

    public record Parsed(
            Set<Integer> years,
            List<String> universities,
            List<String> unknownUniversities,
            boolean mentionsUnsupportedYear,
            boolean asksAdmissionChance,
            boolean asksNotCollected,
            boolean asksAggregateStats,
            boolean wantsSchedule,
            boolean wantsType,
            boolean wantsQuota) {

        /** 질문에 학년도가 명시되지 않았는가. */
        public boolean yearUnspecified() {
            return years.isEmpty();
        }

        /** 질문이 대학을 특정했지만 등록된 자료가 있는 대학이 하나도 없는가. */
        public boolean onlyUnregisteredUniversities() {
            return universities.isEmpty() && !unknownUniversities.isEmpty();
        }
    }

    /**
     * @param question 사용자 질문
     * @param universityNames DB에 저장된 대학 정식 이름들 (별칭 매칭용)
     */
    public static Parsed parse(String question, List<String> universityNames) {
        String q = question == null ? "" : question;
        Set<Integer> years = new LinkedHashSet<>();
        boolean unsupported = false;
        Matcher m = YEAR.matcher(q);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (SUPPORTED_YEARS.contains(y)) years.add(y);
            else if (y >= 2026 && y <= 2035) unsupported = true;
        }
        Matcher past = YEAR_WITH_SUFFIX.matcher(q);
        while (past.find()) unsupported = true;
        Matcher two = YEAR_TWO_DIGIT.matcher(q);
        while (two.find()) {
            int y = 2000 + Integer.parseInt(two.group(1));
            if (SUPPORTED_YEARS.contains(y)) years.add(y);
            else unsupported = true;
        }
        List<String> univs = new ArrayList<>();
        if (universityNames != null) {
            for (String name : universityNames) {
                if (mentionsUniversity(q, name)) univs.add(name);
            }
        }
        List<String> unknown = unknownUniversities(q, universityNames);
        return new Parsed(
                years, univs, unknown, unsupported,
                CHANCE.matcher(q).find(),
                NOT_COLLECTED.matcher(q).find(),
                AGGREGATE_STATS.matcher(q).find(),
                SCHEDULE_INTENT.matcher(q).find(),
                TYPE_INTENT.matcher(q).find(),
                QUOTA_INTENT.matcher(q).find());
    }

    /** "고려대학교(서울)" → {고려대학교, 고려대}. 괄호 부기는 제거한다. */
    static List<String> aliasesOf(String universityName) {
        if (universityName == null) return List.of();
        String base = universityName.replaceAll("\\(.*?\\)", "").trim();
        List<String> out = new ArrayList<>();
        if (base.length() >= 2) out.add(base);
        String shortName = base.replace("학교", "");
        if (shortName.length() >= 2 && !shortName.equals(base)) out.add(shortName);
        out.addAll(STRONG_ALIASES.getOrDefault(base, List.of()));
        return out;
    }

    /** 다른 뜻으로 쓰일 일이 거의 없는 줄임말(그대로 포함되면 해당 대학으로 본다). */
    private static final Map<String, List<String>> STRONG_ALIASES = Map.of(
            "이화여자대학교", List.of("이화여대"),
            "한국외국어대학교", List.of("한국외대"));

    /**
     * 학생들이 쓰는 한 글자 줄임말. 일반 단어와 겹치므로("연대 책임", "고대 그리스", "성대 결절", "중대 사안", "이대로")
     * 입시 문맥이 있고, 앞뒤가 단어 경계일 때만 해당 대학으로 본다.
     */
    private static final Map<String, List<String>> WEAK_ALIASES = Map.of(
            "연세대학교", List.of("연대"),
            "고려대학교", List.of("고대"),
            "이화여자대학교", List.of("이대"),
            "성균관대학교", List.of("성대"),
            "중앙대학교", List.of("중대"),
            "한국외국어대학교", List.of("외대"));

    /** 입시 문맥 판단용 단어. 줄임말 해석은 이 중 하나가 질문에 있을 때만 한다. */
    private static final Pattern ADMISSION_CONTEXT = Pattern.compile(
            "입시|입학|수시|정시|전형|모집|지원|원서|학생부|교과|학종|논술|수능|합격|학년도|요강|면접|등록|접수|지역균형|추천형|학업우수|입학처|최저");

    /** 줄임말 뒤에 올 수 있는 조사(뒤에 한글이 더 이어지면 다른 단어로 본다). "이대로"를 막기 위해 '로'는 뺀다. */
    private static final String WEAK_ALIAS_PARTICLES = "(?:에서|에는|에도|부터|까지|보다|이랑|은|는|이|가|을|를|의|에|도|와|과|만|랑)?";

    /** 질문이 이 대학을 가리키는가: 정식명·"~대" 줄임말·강한 줄임말, 또는 입시 문맥 속의 한 글자 줄임말. */
    public static boolean mentionsUniversity(String question, String universityName) {
        if (question == null || universityName == null) return false;
        for (String alias : aliasesOf(universityName)) {
            if (question.contains(alias)) return true;
        }
        String base = universityName.replaceAll("\\(.*?\\)", "").trim();
        List<String> weak = WEAK_ALIASES.getOrDefault(base, List.of());
        if (weak.isEmpty() || !ADMISSION_CONTEXT.matcher(question).find()) return false;
        for (String alias : weak) {
            Pattern p = Pattern.compile("(?<![가-힣A-Za-z0-9])" + Pattern.quote(alias) + WEAK_ALIAS_PARTICLES + "(?![가-힣])");
            if (p.matcher(question).find()) return true;
        }
        return false;
    }

    /** 자주 묻는 대학 중 이 가이드에 자료가 없을 수 있는 대학(이름·줄임말). DB에 등록된 대학이면 제외된다. */
    private static final List<String> WELL_KNOWN_UNIVERSITIES = List.of(
            "카이스트", "한국과학기술원", "포스텍", "포항공대", "지스트", "유니스트", "디지스트",
            "서울시립대", "시립대", "건국대", "동국대", "홍익대", "국민대", "숭실대", "세종대", "단국대", "아주대", "인하대",
            "부산대", "경북대", "전남대", "충남대", "충북대", "전북대", "경상국립대", "강원대", "제주대",
            "서울과기대", "서울과학기술대", "광운대", "명지대", "상명대", "가톨릭대", "한국항공대", "항공대", "가천대",
            "경기대", "인천대", "서울여대", "숙명여대", "성신여대", "동덕여대", "덕성여대", "한성대", "삼육대", "서경대",
            "한국체대", "한국체육대", "서울교대", "한국교원대", "울산대", "영남대", "조선대", "한동대");
    private static final Pattern ENGLISH_UNIVERSITY = Pattern.compile(
            "(?<![A-Za-z])(KAIST|POSTECH|GIST|UNIST|DGIST)(?![A-Za-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_UNIVERSITY_NAME = Pattern.compile("[가-힣]{2,10}대학교");

    /**
     * 질문이 가리킨 대학 중 등록된 대학이 아닌 것. "카이스트 수시 모집요강" 같은 질문에 다른 대학 자료로 답하지 않도록
     * 먼저 알아낸다. 알려진 대학 이름 목록과 "OO대학교" 형태의 정식명만 본다(일반 명사를 대학으로 오인하지 않기 위해).
     */
    static List<String> unknownUniversities(String question, List<String> registeredNames) {
        if (question == null || question.isBlank()) return List.of();
        List<String> registered = registeredNames == null ? List.of() : registeredNames;
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String name : WELL_KNOWN_UNIVERSITIES) {
            int idx = question.indexOf(name);
            if (idx < 0) continue;
            if (idx > 0 && isHangul(question.charAt(idx - 1))) continue;
            if (registered.stream().anyMatch(r -> aliasesOf(r).contains(name))) continue;
            out.add(name);
        }
        Matcher en = ENGLISH_UNIVERSITY.matcher(question);
        while (en.find()) out.add(en.group(1).toUpperCase(java.util.Locale.ROOT));
        Matcher full = FULL_UNIVERSITY_NAME.matcher(question);
        while (full.find()) {
            String found = full.group();
            boolean known = registered.stream().anyMatch(r -> found.contains(r.replaceAll("\\(.*?\\)", "").trim()));
            if (!known && out.stream().noneMatch(found::contains)) out.add(found);
        }
        return new ArrayList<>(out);
    }

    private static boolean isHangul(char c) {
        return c >= '가' && c <= '힣';
    }

    /** 전형 이름에서 질문 매칭용 조각을 뽑는다. "학생부교과(지역균형)" → {학생부교과, 지역균형}. */
    static List<String> typePieces(String typeName) {
        if (typeName == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String piece : typeName.split("[()/\\s·,]+")) {
            String p = piece.replaceAll("전형$", "").trim();
            if (p.length() >= 2) out.add(p);
        }
        return out;
    }

    /** 학과 핵심어로 쓰지 않는 일반 단어(학과명에 흔히 섞이거나 입시 질문에 늘 나오는 말). */
    private static final Set<String> DEPT_KEYWORD_STOPWORDS = Set.of(
            "전공", "학과", "학부", "계열", "대학", "대학별", "대학교", "모집", "전형", "지원", "수시", "정시", "입시",
            "입학", "선발", "학생부", "논술", "수능", "최저", "자격", "일정", "접수", "원서", "합격", "가능", "추천",
            "경쟁률", "어떻게", "어디", "무엇", "알려", "궁금", "있나요", "되나요", "하려고", "싶은데");

    private static final Pattern HANGUL_RUN = Pattern.compile("[가-힣]{3,}");
    private static final String[] TRAILING_PARTICLES = {
            "으로", "에서", "에게", "까지", "부터", "보다", "처럼", "이나", "이며", "하고", "라고",
            "는", "은", "이", "가", "을", "를", "의", "에", "도", "로", "과", "와", "만", "고", "며", "요"};

    /**
     * 질문에서 학과 핵심어 후보를 뽑는다. 한글 3자 이상 덩어리(조사 한 겹 제거형 포함)에서 일반 단어를 뺀다.
     * 2자 이하 단어는 쓰지 않는다("공학", "전자" 같은 짧은 말이 여러 학과에 걸려 오탐이 나기 때문).
     */
    static Set<String> departmentKeywords(String question) {
        Set<String> out = new LinkedHashSet<>();
        if (question == null) return out;
        Matcher m = HANGUL_RUN.matcher(question);
        while (m.find()) {
            String run = m.group();
            List<String> candidates = new ArrayList<>();
            candidates.add(run);
            for (String particle : TRAILING_PARTICLES) {
                if (run.endsWith(particle) && run.length() - particle.length() >= 3) {
                    candidates.add(run.substring(0, run.length() - particle.length()));
                    break;
                }
            }
            for (String c : candidates) {
                if (c.length() >= 3 && !DEPT_KEYWORD_STOPWORDS.contains(c)) out.add(c);
            }
        }
        return out;
    }

    /**
     * 모집단위 이름이 질문에 언급됐는지(1차, 이름 기준).
     * 이름 전체 포함, 또는 "컴퓨터공학과" ↔ "컴퓨터공학"처럼 접미(학과/학부/과/전공)를 뗀 형태를 인정한다.
     */
    static boolean mentionsDepartment(String question, String departmentName) {
        if (question == null || departmentName == null || departmentName.isBlank()) return false;
        String name = departmentName.trim();
        if (question.contains(name)) return true;
        String stem = name.replaceAll("(학과|학부|전공|과)$", "");
        return stem.length() >= 3 && question.contains(stem)
                || (stem.length() == 2 && question.contains(stem + "과"));
    }

    /**
     * 학과명의 핵심어로 언급됐는지(2차 폴백). "반도체 전공 가고 싶어요" → "시스템반도체공학과", "반도체공학과".
     * 1차 이름 매칭이 하나도 없을 때만 써야 한다. 질문이 "의예과"처럼 정확한 학과명을 말했는데 부분 일치까지 더하면
     * "치의예과"·"한의예과" 같은 다른 학과가 섞이기 때문이다.
     */
    static boolean mentionsDepartmentByKeyword(String question, String departmentName) {
        if (question == null || departmentName == null || departmentName.isBlank()) return false;
        String name = departmentName.trim();
        for (String keyword : departmentKeywords(question)) {
            if (name.contains(keyword)) return true;
        }
        return false;
    }
}
