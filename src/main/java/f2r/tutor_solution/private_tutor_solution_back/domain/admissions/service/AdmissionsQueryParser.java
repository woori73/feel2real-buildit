package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    /** 합격 가능성·합격선·지원 조합 추천 등 판단/예측성 질문 — 답변하지 않는다. */
    private static final Pattern CHANCE = Pattern.compile(
            "합격\\s*(가능|확률|선|컷|권)|지원\\s*가능할까|어디쯤|추천해|소신|안정\\s*지원|상향\\s*지원|하향\\s*지원|붙을까|될까요");

    /** 아직 수집하지 않은 자료(경쟁률·입시결과·권장이수 등) — 만들어 답하지 않는다. */
    private static final Pattern NOT_COLLECTED = Pattern.compile(
            "경쟁률|입시\\s*결과|입결|충원|등급\\s*컷|평균\\s*등급|권장\\s*이수|권장\\s*과목");

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
            boolean mentionsUnsupportedYear,
            boolean asksAdmissionChance,
            boolean asksNotCollected,
            boolean wantsSchedule,
            boolean wantsType,
            boolean wantsQuota) {

        /** 질문에 학년도가 명시되지 않았는가. */
        public boolean yearUnspecified() {
            return years.isEmpty();
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
        List<String> univs = new ArrayList<>();
        if (universityNames != null) {
            for (String name : universityNames) {
                for (String alias : aliasesOf(name)) {
                    if (q.contains(alias)) {
                        univs.add(name);
                        break;
                    }
                }
            }
        }
        return new Parsed(
                years, univs, unsupported,
                CHANCE.matcher(q).find(),
                NOT_COLLECTED.matcher(q).find(),
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
        return out;
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

    /** 모집단위 이름이 질문에 언급됐는지. "컴퓨터공학과" ↔ "컴퓨터공학"처럼 접미(학과/학부/과/전공)를 뗀 형태도 인정. */
    static boolean mentionsDepartment(String question, String departmentName) {
        if (question == null || departmentName == null || departmentName.isBlank()) return false;
        String name = departmentName.trim();
        if (question.contains(name)) return true;
        String stem = name.replaceAll("(학과|학부|전공|과)$", "");
        return stem.length() >= 3 && question.contains(stem)
                || (stem.length() == 2 && question.contains(stem + "과"));
    }
}
