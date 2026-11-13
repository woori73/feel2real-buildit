package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCounselingSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCounselingSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 입시상담 가이드 Q&A service — {@code /api/admissions/chat}과 입시 DB를 연결한다.
 *
 * <p>질문이 오면 {@link AdmissionsResearchService}의 강제 조사 사다리로
 * 승인(ACTIVE)된 공식 입시 자료를 먼저 검색하고, 그 근거를 프롬프트에 조립한 뒤
 * 입시상담 전용 {@link AdmissionsLlmClient}에 위임한다.
 *
 * <p>상담 동작 규칙 (사용자 2차 지시):
 * <ul>
 *   <li>공식 모집요강 우선, 학년도 명시, 대학·전형 구분, 공식자료 출처 표시</li>
 *   <li>2027 확정과 2028 계획 구분</li>
 *   <li>유튜브 의견과 공식자료 분리</li>
 *   <li>충돌·보류 자료의 확정적 답변 금지</li>
 *   <li>근거가 없으면 모른다고 답변</li>
 * </ul>
 *
 * <p>NewStart 지피지기 DB를 검색하거나 NewStart 상담 데이터를 참조하지 않는다.
 */
@Service
public class AdmissionsChatService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsChatService.class);

    /** 질문 길이 상한. */
    static final int MAX_QUESTION_CHARS = 2000;
    /** 답변 출력 상한. */
    static final int MAX_ANSWER_CHARS = 4000;
    /** 컨텍스트에 실리는 근거 총량 상한. */
    /** 답변 근거 전체 상한. 두 학년도 입시결과(최대 16) + 올해 학과·전형 자료 4건(2026-11, 16→20). */
    static final int MAX_CONTEXT_EVIDENCE = 20;
    /** 답변에 붙이는 출처 상한. */
    static final int MAX_SOURCES = 6;
    /** 근거 1건 요약 상한. */
    static final int MAX_EVIDENCE_CHARS = 1500;

    /** 입시상담 가이드 공통 규칙. */
    private static final String BASE_PROMPT =
            "너는 대학 입시 정보를 돕는 한국어 안내자 '입시상담 가이드'다. 이해하기 쉬운 문장으로 간결하게 답한다."
                    + " 답변에는 사용자에게 바로 보여도 되는 최종 문장만 한국어로 담는다."
                    + " 상황 분석·문제 해석·'Here's a thinking process' 같은 사고 과정이나 영문 메모는 출력하지 않고,"
                    + " 답변 끝에 영문 문구·메모를 붙이지 않는다."
                    + " 확인되지 않은 입시 정보를 확정적으로 말하지 않고, 근거가 없으면 모른다고 말한다."
                    + " 합격선·합격 확률·전년도 입시결과를 만들어내지 않는다."
                    + " 유튜브 의견이나 분석가 전망을 공식 모집요강처럼 말하지 않는다."
                    + " 답변에는 반드시 적용 학년도를 명시한다 (예: '2027학년도 기준')."
                    + " 대학별 2028학년도 전형 자료(시행계획)는 '계획 단계로 변경될 수 있다'는 점을 함께 밝힌다."
                    + " 교육부·평가원·대교협이 발표한 2028 제도(내신 5등급제·통합형 수능 등)는 발표 기관과 발표 시기를 밝혀 안내한다."
                    + " 근거에 없는 대학명·전형명·날짜·인원·비율을 만들어내지 않는다."
                    + " 근거의 출처가 '사설 미러본'이면 공식 모집요강이라고 말하지 말고, 공식 원본이 아니라 확인이 필요한 자료라고 밝히며,"
                    + " 그 자료만으로 모집인원·일정·자격·전형방법을 확정적으로 말하지 않는다."
                    + " 근거 제목에 [세종캠퍼스]처럼 캠퍼스가 표기되어 있으면 그 캠퍼스 자료로만 말하고,"
                    + " 질문이 다른 캠퍼스(예: 서울캠퍼스)를 묻는데 해당 캠퍼스 자료가 없으면 없다고 답한다."
                    + " 답변 본문에는 '[1]', '[출처1]' 같은 대괄호 번호 인용표를 쓰지 말고,"
                    + " 근거는 출처 성격만 자연어로 밝힌다 (예: '서울대학교 2027학년도 수시모집요강에 따르면')."
                    + " 간결하게 답한다. 불필요하게 장황하게 설명하지 않는다.";

    private final AdmissionsLlmClient admissionsLlmClient;
    private final AdmissionsResearchService researchService;
    private final AdmissionsFactSearchService factSearchService;
    private final AdmissionsCounselingSessionRepository sessionRepository;

    public AdmissionsChatService(AdmissionsLlmClient admissionsLlmClient,
                                 AdmissionsResearchService researchService,
                                 AdmissionsFactSearchService factSearchService,
                                 AdmissionsCounselingSessionRepository sessionRepository) {
        this.admissionsLlmClient = admissionsLlmClient;
        this.researchService = researchService;
        this.factSearchService = factSearchService;
        this.sessionRepository = sessionRepository;
    }

    static final String MSG_CHANCE =
            "합격 가능성·합격선·지원 조합 추천은 제공하지 않습니다. 입시상담 가이드는 대학이 공개한 모집요강 정보"
                    + "(모집단위, 전형, 일정 등)만 안내합니다. 개인별 합격 판단은 학교 진학 상담과 각 대학 입학처를 이용해 주세요.";
    /** 합격 가능성 질문인데 대학을 특정하지 않은 경우: 판단 대신 참고 정보를 줄 수 있음을 안내한다. */
    static final String MSG_CHANCE_NEED_TARGET =
            "합격 가능성은 판단해 드릴 수 없습니다. 대신 지원하려는 대학과 전형을 알려 주시면 모집인원·전형방법·수능최저처럼 "
                    + "지원 판단에 참고할 공식 모집요강 정보와 관련 유튜브 전문가 해석을 정리해 드립니다. "
                    + "예: \"성균관대 학생부종합 가능성\", \"아주대 교과전형 합격 가능성\"";
    /** 지난해 입시결과 근거에 반드시 붙이는 안내. 답변에 없으면 서버가 앞에 붙인다. */
    static final String PRIOR_DISCLAIMER = "아래 수치는 2026학년도(지난해) 결과이며 올해와 다를 수 있습니다.";

    /** 올해 원서접수 최종 경쟁률 근거에 반드시 붙이는 안내(합격·등록 결과가 아님). 답변에 없으면 서버가 앞에 붙인다. */
    static final String CURRENT_RATIO_NOTICE = "아래 경쟁률은 2027학년도(올해) 원서접수 최종 경쟁률이며, 합격·등록 결과가 아닙니다.";

    /** 올해 원서접수 경쟁률과 지난해 결과를 함께 근거로 쓴 답변의 안내(두 안내를 따로 붙이면 서로 어긋나 보인다). */
    static final String BOTH_YEARS_NOTICE = "아래에는 2027학년도(올해) 원서접수 최종 경쟁률(합격·등록 결과 아님)과 2026학년도(지난해) 결과가 함께 있습니다. 수치마다 학년도를 확인해 주세요.";

    /**
     * 근거에 없는 지난해 값을 다루는 규칙. 충원 표처럼 원문이 일부 전형·모집단위만 적은 표에서 빠진 값을
     * '공개되지 않음'(비공개)으로 단정하지 않게 한다. 비공개는 원문이 그렇게 적은 경우에만 쓴다.
     */
    static final String PRIOR_MISSING_RULE = "근거에 없는 지표·수치는 쓰지 말고 '지난해 자료에 해당 값이 없다'고만 하라. "
            + "근거에 없다는 이유로 '공개되지 않음'·'비공개'라고 단정하지 마라(근거 내용이 비공개라고 적은 경우에만 그렇게 말한다). "
            + "충원 인원처럼 일부 전형·모집단위만 값이 있으면 값이 있는 것만 적고, 나머지는 '지난해 자료에 해당 값이 없다'고 하라. ";

    /** 입시결과 근거의 출처를 밝히는 규칙. 대행사·대학어디가·사설 자료를 대학 입학처 공식 자료라고 부르지 않게 한다. */
    static final String PRIOR_SOURCE_RULE = "출처는 근거마다 밝혀라: '출처 종류'가 없는 근거는 '대학 입학처 공식 입시결과(지난해)' 또는 "
            + "'대학 입학처 공식 경쟁률(올해 원서접수)', '출처 종류'가 있는 근거는 그 이름(예: '유웨이 경쟁률 서비스', '대학어디가')으로 밝히고 "
            + "대학 입학처 공식 자료라고 하지 마라. 출처 종류가 '사설 추정치'면 대학이 공개한 결과가 아니라 추정치라고 함께 밝혀라. ";

    /** 입시결과를 물었지만 그 대학의 승인된 지난해 입시결과가 없을 때. */
    static String msgPriorNotCollected(List<String> names) {
        return String.join(", ", names) + "의 2026학년도(지난해) 공식 입시결과(경쟁률·입결·충원)는 아직 수집되지 않아 "
                + "안내할 수 없습니다. 해당 대학 입학처의 입시결과 발표를 확인해 주세요.";
    }

    static final String MSG_NOT_COLLECTED =
            "경쟁률·전년도 입시결과·충원 현황·권장 이수 과목은 아직 수집된 공식 자료가 없어 안내할 수 없습니다. "
                    + "해당 정보는 각 대학 입학처 발표를 확인해 주세요.";
    /** 2028 이후 입시 중 아직 이 상담에 수집하지 않은 자료. 2028 질문의 프롬프트와 미발표 안내에 함께 쓴다. */
    static final String RESEARCH_STATUS_2028 =
            "대학별 2028학년도 시행계획 세부(전형별 모집인원·평가요소·수능최저), 대학별 5등급 교과 반영 방법"
                    + "(등급 점수표·성취도 반영), 정시 영역 반영비율, 대학별 권장과목 전체 목록, 2029·2030학년도 대학별 계획";

    /** 미발표: 대학별 2028학년도 모집요강이 아직 나오지 않았다(수집 여부와 무관). */
    static final String MSG_UNPUBLISHED_2028 =
            "대학별 2028학년도 모집요강(전형·일정·모집인원)은 대부분의 대학에서 아직 발표 전입니다. 지금 안내할 수 있는 2028학년도 "
                    + "자료는 교육부·평가원·대교협이 발표한 제도(내신 5등급제, 통합형 수능, 시험 일정, 전국 모집 규모 등)라서 "
                    + "질문에 해당하는 대학별 일정이나 수치는 안내할 수 없습니다. 이 상담에서 지금 수집 중인 자료: "
                    + RESEARCH_STATUS_2028 + ". 각 대학 입학처의 2028학년도 대학입학전형 시행계획을 확인해 주세요.";

    /** 현 고1(2029학년도)·예비 고1(2030학년도) 질문에 붙이는 안내. */
    static final String LATER_COHORT_NOTE =
            "현재 발표된 제도 기준으로 2029학년도(현 고1)와 2030학년도(예비 고1, 현 중3)도 2028학년도와 같은 "
                    + "내신 5등급제·통합형 수능을 적용받는다. 대학별 계획은 2029학년도가 2027년 4월, 2030학년도가 2028년 4월에 발표된다.";

    private static final java.util.regex.Pattern LATER_COHORT = java.util.regex.Pattern.compile(
            "2029|2030|(?<!\\d)(?:29|30)\\s*학년도|예비\\s*고\\s*1|예비\\s*고등|중학생|중학교|(?<![가-힣])중[23](?!\\d)|(?<![가-힣])고1(?!\\d)");

    /** 올해 입시(2027학년도)를 가리키는 표현. '예비 고3'(현 고2)은 2028학년도라 제외한다. */
    private static final java.util.regex.Pattern CURRENT_CYCLE = java.util.regex.Pattern.compile(
            "올해|금년|이번\\s*(?:입시|수시|정시|원서)|작년|지난해|전년도|(?<!예비\\s?)(?<!예비)고3");

    /**
     * 상담에 쓸 학년도. 질문에 학년도가 적혀 있으면 그 학년도(둘 이상이면 null=전체)를,
     * 적혀 있지 않고 '올해·작년·고3' 같은 올해 입시 표현이 있으면 2027을, 둘 다 없으면 화면에서 고른 학년도를 쓴다.
     */
    public static Integer effectiveYear(String question, Integer requestedYear) {
        java.util.Set<Integer> years = AdmissionsQueryParser.parse(question, List.of()).years();
        if (!years.isEmpty()) return years.size() == 1 ? years.iterator().next() : null;
        if (question != null && CURRENT_CYCLE.matcher(question).find()) return 2027;
        return requestedYear;
    }

    /** 질문이 2028학년도 뒤 학년(현 고1·예비 고1·중학생)을 가리키는가. */
    static boolean mentionsLaterCohort(String question) {
        return question != null && LATER_COHORT.matcher(question).find();
    }

    /** 미수집: 자료가 발표되었을 수 있으나 이 가이드가 수집하지 않았다(발표 여부와 무관). */
    static final String MSG_AGGREGATE_NOT_COLLECTED =
            "전국 단위 모집인원·수시/정시 비중 같은 총량 통계(예: 대교협 발표 자료)는 이 가이드에 수집된 공식 자료가 없어 "
                    + "정확한 수치를 안내할 수 없습니다. 통계가 발표되지 않았다는 뜻이 아니라 아직 수집하지 않았다는 뜻이니 "
                    + "대교협 등 공식 발표 자료를 직접 확인해 주세요. 대학별 전형·모집단위·일정은 대학 이름을 넣어 물어보세요.";
    static final String MSG_UNSUPPORTED_YEAR =
            "입시상담 가이드는 2027학년도(확정)와 2028학년도(계획) 자료만 다룹니다. 다른 학년도는 안내할 수 없습니다.";
    /** 질문이 특정 대학을 가리켰지만 그 대학에서 요청한 정보를 찾지 못했을 때의 공통 첫 문장. */
    static final String MSG_UNIV_NO_INFO =
            "현재 등록된 공식 입시자료에서 해당 대학의 요청하신 전형 정보를 확인할 수 없습니다.";

    /** 대학 전체 자료가 없는 경우(등록되지 않은 대학). 다른 대학 자료로 대신 답하지 않는다. */
    static String msgUniversityNotRegistered(List<String> names) {
        return MSG_UNIV_NO_INFO + " 질문하신 " + String.join(", ", names)
                + "의 입시자료는 아직 등록되어 있지 않습니다. 해당 대학 입학처의 모집요강 원문을 확인해 주세요.";
    }

    /** 대학은 등록돼 있지만 요청한 전형·일정 자료만 없는 경우. */
    static String msgUniversityInfoMissing(List<String> names) {
        return MSG_UNIV_NO_INFO + " " + String.join(", ", names)
                + "의 다른 전형·일정 자료는 등록되어 있을 수 있으니, 전형명이나 수시/정시를 바꿔 질문하시거나 "
                + "해당 대학 입학처의 모집요강 원문을 확인해 주세요.";
    }
    static final String MSG_UNGROUNDED_NUMBER =
            "근거 자료에서 확인되지 않는 수치가 답변에 포함되어 답변을 제공하지 않습니다. 질문을 더 구체적으로 "
                    + "(대학·학과·전형·학년도) 다시 입력해 주세요.";
    static final String MSG_TITLE_ONLY =
            "관련 공식 모집요강 문서의 제목만 확인되고 질문에 답할 수 있는 본문·세부 자료(모집단위·전형·일정)는 아직 없습니다. "
                    + "문서 제목만으로는 내용을 안내할 수 없으니 대학 모집요강 관련 내용은 해당 대학 입학처 모집요강 원문을, "
                    + "수능 출제 과목·대입 제도 등 대학 외 정책은 교육부·한국교육과정평가원 발표 자료를 확인해 주세요. "
                    + "2027학년도(확정)와 2028학년도(계획) 자료는 구분되어 있으니 확인하실 때 학년도도 함께 확인해 주세요.";
    static final String MSG_NO_EVIDENCE =
            "승인된 공식 입시 자료에서 질문에 해당하는 정보를 찾지 못했습니다. 대학명과 궁금한 내용(전형·모집인원·일정·학과)을 "
                    + "함께 넣어 다시 질문해 주세요. 예: \"아주대 2027 수시 원서접수 일정 알려줘\", \"고려대 학교추천전형 모집인원은?\" "
                    + "자료에 없는 내용은 해당 대학 입학처의 모집요강 원문을 확인해 주세요.";
    /** 검색 단계 오류: 자료 부족(MSG_NO_EVIDENCE)·AI 호출 실패와 구분한다. */
    static final String MSG_SEARCH_FAILED =
            "입시 자료를 검색하는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    public record AnswerSource(String kind, String title, String url, String id) {}

    public record AdmissionsAnswer(boolean ok, String answer, String errorMessage,
                                   String model, List<AnswerSource> sources,
                                   Integer academicYear, boolean yearConfirmed) {}

    /**
     * 입시 질문에 답변한다.
     *
     * @param rawQuestion 사용자 질문
     * @param academicYearFilter null=전체, 2027/2028 필터
     * @param sessionToken 비회원 세션 토큰 (상담 이력 저장용)
     */
    public AdmissionsAnswer ask(String rawQuestion, Integer academicYearFilter, String sessionToken) {
        return ask(rawQuestion, academicYearFilter, sessionToken, null);
    }

    public AdmissionsAnswer ask(String rawQuestion, Integer academicYearFilter, String sessionToken,
                                String studentProfile) {
        return ask(rawQuestion,academicYearFilter,sessionToken,studentProfile,null);
    }

    public AdmissionsAnswer ask(String rawQuestion, Integer requestedYear, String sessionToken,
                                String studentProfile, String retrievalContext) {
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        // 화면의 학년도 선택보다 질문에 적은 학년도·'올해' 같은 표현을 앞세운다(기본 화면이 2028이어도 2027 질문에 답하도록).
        Integer academicYearFilter = effectiveYear(question, requestedYear);
        if (question.isEmpty()) {
            return failure("질문을 입력해 주세요.");
        }
        if (question.length() > MAX_QUESTION_CHARS) {
            return failure("질문이 너무 깁니다. " + MAX_QUESTION_CHARS + "자 이내로 입력해 주세요.");
        }

        String searchQuestion=question;
        if (retrievalContext!=null && !retrievalContext.isBlank())
            searchQuestion += "\n상담 문맥: "+retrievalContext.substring(0,Math.min(retrievalContext.length(),2400));
        // 1. 질문 분석 + 학과·전형·일정 DB 조회
        AdmissionsFactSearchService.FactResult facts;
        try {
            facts = factSearchService.search(searchQuestion, academicYearFilter);
        } catch (RuntimeException e) {
            log.error("admissions fact search failed", e);
            return failure(MSG_SEARCH_FAILED);
        }
        AdmissionsQueryParser.Parsed parsed = facts.parsed();

        // 2. LLM 없이 차단하는 질문: 합격 가능성 판단/추천, 수집하지 않은 자료, 미지원 학년도
        // 합격 가능성은 판단하지 않는다. 대학을 말했으면 판단 대신 참고할 공식 정보를 정리하고(아래 프롬프트 지시),
        // 대학이 없으면 무엇을 알려 주면 되는지 안내한다.
        if (parsed.asksAdmissionChance() && parsed.universities().isEmpty()) {
            return refuse(MSG_CHANCE_NEED_TARGET, sessionToken, question, academicYearFilter, studentProfile);
        }
        boolean hasPrior = facts.evidence().stream().anyMatch(AdmissionsChatService::isPrior);
        if (parsed.asksNotCollected() && !(parsed.asksPriorResult() && hasPrior && !asksRecommendedCourses(question))) {
            // 대학을 말한 입시결과 질문인데 그 대학 자료가 없으면 "그 대학은 아직 수집 안 됨"으로 구체적으로 안내한다.
            String msg = parsed.asksPriorResult() && !parsed.universities().isEmpty() && !asksRecommendedCourses(question)
                    ? msgPriorNotCollected(parsed.universities()) : MSG_NOT_COLLECTED;
            return refuse(msg, sessionToken, question, academicYearFilter, studentProfile);
        }
        // 2028 전국 모집 규모는 대교협 발표 자료를 지식 항목으로 넣었다. 2028 질문은 근거를 찾아본 뒤 없을 때만 미수집으로 답한다.
        boolean asks2028 = facts.searchedYears().equals(java.util.Set.of(2028));
        if (parsed.asksAggregateStats() && parsed.universities().isEmpty() && !asks2028) {
            return refuse(MSG_AGGREGATE_NOT_COLLECTED, sessionToken, question, academicYearFilter, studentProfile);
        }
        if (parsed.mentionsUnsupportedYear() && parsed.years().isEmpty()) {
            return refuse(MSG_UNSUPPORTED_YEAR, sessionToken, question, academicYearFilter, studentProfile);
        }

        // 2-1. 질문이 등록되지 않은 대학만 가리키면 다른 대학 자료로 대신 답하지 않는다.
        if (parsed.onlyUnregisteredUniversities()) {
            return refuse(msgUniversityNotRegistered(parsed.unknownUniversities()), sessionToken, question,
                    academicYearFilter, studentProfile);
        }

        // 3. 조사 사다리 + 사실 근거 병합 (GUIDE는 근거가 아니다)
        ResearchPack pack;
        try {
            pack = researchService.research(searchQuestion, academicYearFilter);
        } catch (RuntimeException e) {
            log.error("admissions research failed", e);
            return failure(MSG_SEARCH_FAILED);
        }
        List<Evidence> usable = mergeUsable(facts.evidence(), usableEvidence(pack));

        // 4. 2028 질문은 2028 근거만 인정한다. 없으면 "미발표"로 답하고 2027 자료로 대신 답하지 않는다.
        if (asks2028) {
            usable = usable.stream().filter(e -> Integer.valueOf(2028).equals(e.getAcademicYear())).toList();
            // 문서 제목만 있는 자료(material:)로는 2028 일정·수치를 답할 수 없다. 학과·전형·일정 행 또는 사실 지식이 있어야 한다.
            if (usable.stream().noneMatch(e -> isFactRow(e.getRefId()))) {
                if (parsed.asksAggregateStats() && parsed.universities().isEmpty()) {
                    return refuse(MSG_AGGREGATE_NOT_COLLECTED, sessionToken, question, academicYearFilter, studentProfile);
                }
                return refuseWithYoutube(MSG_UNPUBLISHED_2028, pack, sessionToken, question, academicYearFilter, studentProfile);
            }
        }

        // 5. 사실 근거가 하나도 없으면(GUIDE만 검색된 경우 포함) LLM을 호출하지 않는다.
        if (usable.isEmpty()) {
            return refuseWithYoutube(parsed.asksAdmissionChance() ? MSG_CHANCE
                            : parsed.universities().isEmpty() ? MSG_NO_EVIDENCE : msgUniversityInfoMissing(parsed.universities()), pack,
                    sessionToken, question, academicYearFilter, studentProfile);
        }

        // 5-1. 문서 제목·발행기관만 있는 공식자료(material:)로는 내용을 알 수 없다. 사실 근거(학과·전형·일정 행 또는
        //      FACT 지식)가 하나도 없으면 LLM이 제목만 보고 내용을 지어내지 못하도록 호출하지 않는다.
        if (usable.stream().noneMatch(e -> isFactRow(e.getRefId()))) {
            return refuseWithYoutube(parsed.universities().isEmpty() ? MSG_TITLE_ONLY : MSG_UNIV_NO_INFO + " " + MSG_TITLE_ONLY, pack,
                    sessionToken, question, academicYearFilter, studentProfile);
        }

        // 6. 근거를 프롬프트에 조립
        String evidenceContext = buildEvidenceContext(usable);
        String systemPrompt = BASE_PROMPT + "\n\n[입시 공식자료 근거]\n" + evidenceContext
                + buildGuideBlock(pack) + buildYoutubeBlock(pack);
        List<String> comparisons = hasPrior
                ? priorComparisonLines(usable, AdmissionsQueryParser.studentScores(
                        question + (studentProfile == null ? "" : "\n" + studentProfile)))
                : List.of();
        boolean hasLastYear = facts.evidence().stream().anyMatch(AdmissionsChatService::isLastYearPrior);
        boolean hasCurrentRatio = facts.evidence().stream().anyMatch(AdmissionsChatService::isCurrentRatio);
        String userPrompt = buildUserPrompt(searchQuestion, academicYearFilter, pack, parsed, studentProfile, facts.scheduleCoverage(),
                hasLastYear, hasCurrentRatio, comparisons)
                + reformNotes(question + (studentProfile == null ? "" : "\n" + studentProfile),
                        facts.searchedYears().contains(2028));

        // 7. LLM 호출
        NewStartLlmCompletion completion = admissionsLlmClient.complete(systemPrompt, userPrompt, MAX_ANSWER_CHARS);
        if (!completion.ok()) {
            log.warn("admissions llm failed: {}", completion.errorMessage());
            return failure("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        // 8. 사후 검증: 답변의 수치가 제공한 근거(또는 질문)에 없으면 폐기한다.
        List<String> ungrounded = ungroundedNumbers(completion.text(), question, usable);
        if (!ungrounded.isEmpty()) {
            log.warn("admissions answer rejected: ungrounded numbers count={}", ungrounded.size());
            return refuse(MSG_UNGROUNDED_NUMBER, sessionToken, question, academicYearFilter, studentProfile);
        }

        String answer = completion.text();
        boolean usesPrior = usable.stream().anyMatch(AdmissionsChatService::isPrior);
        if (usesPrior || parsed.asksAdmissionChance()) {
            // 8-1. 합격·불합격 판정 문장이 있으면 LLM 답변을 버리고 근거를 그대로 나열한 답변으로 바꾼다.
            if (containsVerdict(answer)) {
                log.warn("admissions answer replaced: verdict phrase");
                answer = usesPrior ? priorFallbackAnswer(usable, comparisons) : MSG_CHANCE;
            }
        }
        // 8-2. 지난해 결과를 썼으면 "지난해 결과이며 올해와 다를 수 있음"을 반드시 밝힌다.
        //      올해 원서접수 경쟁률을 썼으면 "올해 원서접수 최종 경쟁률이며 합격·등록 결과가 아님"을 밝힌다.
        boolean usesLastYear = usable.stream().anyMatch(AdmissionsChatService::isLastYearPrior);
        boolean usesCurrentRatio = usable.stream().anyMatch(AdmissionsChatService::isCurrentRatio);
        if (usesCurrentRatio && usesLastYear) {
            if (!answer.contains(BOTH_YEARS_NOTICE)) answer = BOTH_YEARS_NOTICE + "\n" + answer;
        } else if (usesCurrentRatio && !answer.contains("원서접수 최종 경쟁률")) {
            answer = CURRENT_RATIO_NOTICE + "\n" + answer;
        } else if (usesLastYear && !answer.contains("올해와 다를 수 있")) {
            answer = PRIOR_DISCLAIMER + "\n" + answer;
        }
        if (usable.stream().anyMatch(e -> AdmissionsFactSearchService.SOURCE_PRIVATE.equals(priorSourceLabel(e)))
                && !answer.contains(PRIVATE_ESTIMATE_NOTICE)) answer = PRIVATE_ESTIMATE_NOTICE + "\n" + answer;
        // 8-3. 다음 학년도 반영방법으로 다시 산출한 표 같은 원문 산출 단서가 있으면 답변에 반드시 남긴다.
        if (usesPrior) answer = withPriorCaveats(answer, usable);

        // 9. 출처 조립 (URL이 없어도 문서 ID와 제목으로 표시, GUIDE는 제외)
        List<AnswerSource> sources = new ArrayList<>(buildSources(usable, question));
        // 답변이 유튜브 해석 단락을 썼으면 그 영상들을 공식 출처와 구분된 "참고" 출처로 붙인다.
        if (answer.contains("유튜브")) sources.addAll(youtubeSources(pack));

        saveSession(sessionToken, question, answer, pack, academicYearFilter, studentProfile, sources);

        log.info("admissions chat ok questionChars={} evidenceCount={} answerChars={} model={}",
                question.length(), usable.size(), answer.length(), completion.model());
        return new AdmissionsAnswer(true, answer, null, completion.model(), sources,
                academicYearFilter, usable.stream().anyMatch(e -> Boolean.TRUE.equals(e.getYearConfirmed())));
    }

    /**
     * 사용 가능한 근거: STEP1(확정) + STEP2(계획) + STEP3(원문).
     * STEP4(유튜브)는 별도 섹션으로만, STEP5(충돌)는 "확인 중" 안내로만 사용.
     */
    List<Evidence> usableEvidence(ResearchPack pack) {
        List<Evidence> out = new ArrayList<>();
        out.addAll(pack.getConfirmedHits());
        out.addAll(pack.getPlannedHits());
        out.addAll(pack.getOfficialDocHits());
        // 유튜브·충돌은 확정 근거에서 제외
        return out.stream()
                .sorted(Comparator.comparingInt((Evidence e) -> e.getStepNo())
                        .thenComparing(e -> e.getTitle()))
                .limit(MAX_CONTEXT_EVIDENCE)
                .toList();
    }

    /** 내용이 있는 사실 근거(학과·전형·일정 행, 지난해 입시결과 행, 사실 지식). 문서 제목만 있는 material은 제외. */
    static boolean isFactRow(String refId) {
        return refId != null && (refId.startsWith("dept:") || refId.startsWith("type:")
                || refId.startsWith("sched:") || refId.startsWith("prior:") || refId.startsWith("knowledge:"));
    }

    /**
     * 지난해 입시결과 근거의 '산출 기준(원문)' 단서(예: 2027학년도 반영방법에 따라 산출)를 대학별로 모아,
     * 답변에 그 문장이 없으면 끝에 덧붙인다. 같은 대학·같은 문장은 한 번만.
     */
    static String withPriorCaveats(String answer, List<Evidence> evidence) {
        java.util.LinkedHashMap<String, String> lines = new java.util.LinkedHashMap<>();
        for (Evidence e : evidence) {
            if (!isPrior(e) || e.getExcerpt() == null || !e.getExcerpt().startsWith(AdmissionsFactSearchService.CAVEAT_PREFIX)) continue;
            String caveat = e.getExcerpt().substring(AdmissionsFactSearchService.CAVEAT_PREFIX.length()).split("\n", 2)[0].trim();
            if (caveat.isEmpty() || answer.contains(caveat)) continue;
            String univ = e.getTitle() == null ? "" : e.getTitle().split(" ", 2)[0];
            lines.putIfAbsent(univ + "|" + caveat, "※ " + univ + " 지난해 결과의 산출 기준(원문): " + caveat);
        }
        if (lines.isEmpty()) return answer;
        return answer + "\n\n" + String.join("\n", lines.values());
    }

    static boolean isPrior(Evidence e) {
        return e.getSourceClass() == AdmissionsResearchService.SourceClass.OFFICIAL_PRIOR_RESULT;
    }

    /** 입시결과 근거의 출처 이름(대학 입학처 원문이 아니면 '유웨이 경쟁률 서비스' 등). 입학처 원문이면 null. */
    static String priorSourceLabel(Evidence e) {
        if (!isPrior(e) || e.getTitle() == null) return null;
        int i = e.getTitle().lastIndexOf(AdmissionsFactSearchService.SOURCE_TAG);
        if (i < 0) return null;
        int j = e.getTitle().indexOf('〕', i);
        return j < 0 ? null : e.getTitle().substring(i + AdmissionsFactSearchService.SOURCE_TAG.length(), j);
    }

    /** 출처 패널 종류 이름. 입학처 원문은 '공식 …', 그 밖은 출처 이름을 괄호에 붙인다. */
    static String priorSourceKind(Evidence e) {
        String label = priorSourceLabel(e);
        if (isCurrentRatio(e)) return label == null ? "공식 경쟁률 (올해 원서접수)" : "경쟁률 (올해 원서접수, " + label + ")";
        return label == null ? "공식 입시결과 (지난해)" : "입시결과 (지난해, " + label + ")";
    }

    /** 사설 추정치 근거를 썼을 때 답변 앞에 붙이는 안내. */
    static final String PRIVATE_ESTIMATE_NOTICE = "아래 일부 수치는 대학이 공개한 결과가 아니라 사설 기관의 추정치입니다.";

    /** 입시결과 행 중 결과 학년도가 상담 학년도와 같은 것(올해 원서접수 최종 경쟁률). */
    static boolean isCurrentRatio(Evidence e) {
        return isPrior(e) && e.getTitle() != null && e.getTitle().contains(AdmissionsFactSearchService.CURRENT_YEAR_TAG);
    }

    /** 지난해 입시결과 근거(올해 원서접수 경쟁률이 아닌 입시결과 행). */
    static boolean isLastYearPrior(Evidence e) {
        return isPrior(e) && !isCurrentRatio(e);
    }

    /** 권장 이수 과목은 여전히 수집하지 않은 자료다. */
    static boolean asksRecommendedCourses(String question) {
        return question != null && question.matches("(?s).*권장\\s*(이수|과목).*");
    }

    /**
     * 합격·불합격을 판정하거나 가능성을 높다/낮다로 말하는 문장. "합격 가능성은 판단해 드릴 수 없지만" 같은 안내 문장은 해당하지 않는다.
     */
    private static final java.util.regex.Pattern VERDICT = java.util.regex.Pattern.compile(
            "(합격|붙)\\s*(할|을)\\s*(수\\s*있|것\\s*같|것으로|가능성이\\s*(높|크|있))"
                    + "|떨어질|불합격|합격권|안정권|적정권|상향권|하향권|합격\\s*안정"
                    + "|가능성(이|은)?\\s*(높|낮|크|작|충분|희박)"
                    + "|충분히\\s*(합격|가능|노려|도전|해\\s*볼)"
                    + "|(유리|불리)(합니다|해요|하다|한\\s*편|할\\s*수)"
                    + "|(합격|지원)\\s*(이|은)?\\s*(어렵|힘들|무난)");

    static boolean containsVerdict(String answer) {
        return answer != null && VERDICT.matcher(answer).find();
    }

    /**
     * 학생이 적은 성적과 같은 체계의 지난해 지표만 나란히 놓는 줄(서버가 만든다 — LLM이 비교 대상을 고르지 않게).
     * 판정·확률은 쓰지 않는다. 체계가 다르거나 알 수 없으면 비교하지 않았다고 적는다.
     * 등급의 내신/수능 구분이 질문에 없으면 수시 지표는 내신, 정시 지표는 수능 등급으로 보고 그 사실을 밝힌다.
     */
    static final int MAX_COMPARISONS_PER_UNIT = 6;

    static List<String> priorComparisonLines(List<Evidence> evidence, List<AdmissionsQueryParser.StudentScore> scores) {
        if (scores.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (Evidence e : evidence) {
            if (!isPrior(e) || e.getExcerpt() == null) continue;
            boolean jeongsi = e.getTitle() != null && e.getTitle().contains(" 정시");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([^;:\\n]+?) = ([^;\\n]+)")
                    .matcher(e.getExcerpt().substring(Math.max(0, e.getExcerpt().indexOf("성적 지표"))));
            List<String> pairs = new ArrayList<>();
            boolean anyMetric = false;
            while (m.find()) {
                anyMetric = true;
                String label = m.group(1).strip();
                String value = m.group(2).strip();
                String kind = AdmissionsQueryParser.metricKind(label, null);
                for (AdmissionsQueryParser.StudentScore s : scores) {
                    String sk = s.kind();
                    boolean assumed = false;
                    if ("GRADE".equals(sk)) { sk = jeongsi ? "CSAT_GRADE" : "SCHOOL_GRADE"; assumed = true; }
                    if (kind == null || !kind.equals(sk)) continue;
                    pairs.add("지난해 " + label + " " + value + ", 입력 성적 " + s.text()
                            + (assumed ? (jeongsi ? "(수능 등급으로 보고 비교)" : "(내신 등급으로 보고 비교)") : ""));
                }
            }
            String head = e.getTitle() + ": ";
            if (pairs.size() > MAX_COMPARISONS_PER_UNIT) pairs = pairs.subList(0, MAX_COMPARISONS_PER_UNIT);
            if (!pairs.isEmpty()) out.add(head + String.join(" / ", pairs));
            else if (anyMetric) out.add(head + "입력하신 성적과 같은 방식의 지표가 없어 비교하지 않음");
            else out.add(head + "성적 지표가 공개되지 않아 비교하지 않음");
        }
        return out;
    }

    /** 판정 문장이 섞인 답변 대신 보여 줄, 근거를 그대로 옮긴 답변(LLM 문장 없음). */
    static String priorFallbackAnswer(List<Evidence> evidence, List<String> comparisons) {
        boolean lastYear = evidence.stream().anyMatch(AdmissionsChatService::isLastYearPrior);
        boolean current = evidence.stream().anyMatch(AdmissionsChatService::isCurrentRatio);
        StringBuilder sb = new StringBuilder();
        if (current) sb.append(CURRENT_RATIO_NOTICE);
        if (lastYear) sb.append(sb.isEmpty() ? "" : "\n").append(PRIOR_DISCLAIMER);
        boolean other = evidence.stream().anyMatch(e -> priorSourceLabel(e) != null);
        sb.append(" 합격 가능성은 판단해 드릴 수 없어, ").append(other ? "공개된 " : "대학 입학처가 공개한 ")
                .append(lastYear ? "지난해 결과" : "경쟁률").append("를 그대로 옮겨 드립니다.");
        for (Evidence e : evidence) {
            if (!isPrior(e)) continue;
            sb.append("\n\n").append(e.getTitle()).append("\n").append(e.getExcerpt());
        }
        if (!comparisons.isEmpty()) {
            sb.append("\n\n[입력하신 성적과 나란히 보기 — 판정 아님]");
            for (String c : comparisons) sb.append("\n- ").append(c);
        }
        sb.append("\n\n지원 판단은 학교 진학 상담과 대학 입학처 상담을 함께 이용해 주세요.");
        return sb.toString();
    }

    /** 사실 테이블 근거를 앞에 두고 중복(refId) 없이 합친다. */
    static List<Evidence> mergeUsable(List<Evidence> facts, List<Evidence> researched) {
        java.util.LinkedHashMap<String, Evidence> byId = new java.util.LinkedHashMap<>();
        for (Evidence e : facts) byId.putIfAbsent(e.getRefId(), e);
        for (Evidence e : researched) byId.putIfAbsent(e.getRefId(), e);
        return byId.values().stream()
                .filter(e -> e.getSourceClass() != AdmissionsResearchService.SourceClass.GUIDE_NOTE)
                .limit(MAX_CONTEXT_EVIDENCE)
                .toList();
    }

    /** 답변 형식 안내문(GUIDE)은 사실이 아니라는 표시와 함께 형식 힌트로만 덧붙인다. */
    private String buildGuideBlock(ResearchPack pack) {
        if (pack.getGuideHits() == null || pack.getGuideHits().isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n[답변 형식 안내 — 사실 근거가 아니며 이 안내에서 입시 사실을 가져오지 않는다]\n");
        for (Evidence g : pack.getGuideHits()) {
            sb.append("- ").append(g.getTitle()).append(": ").append(firstLine(g.getExcerpt())).append("\n");
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /**
     * 공식 근거가 없어 답하지 못할 때, 관련 유튜브 전문가 해석이 있으면 LLM 없이 목록(채널·요지)과 영상 링크를 덧붙인다.
     * 공식 자료가 아님을 문구와 출처 종류에 밝히고, 내용을 새로 만들지 않는다(저장된 요지를 그대로 보여 준다).
     */
    private AdmissionsAnswer refuseWithYoutube(String message, ResearchPack pack, String sessionToken, String question,
                                               Integer year, String studentProfile) {
        List<AnswerSource> yt = youtubeSources(pack);
        if (yt.isEmpty()) return refuse(message, sessionToken, question, year, studentProfile);
        String answer = message + "\n\n" + youtubeReferenceText(pack);
        saveSession(sessionToken, question, answer, null, year, studentProfile, yt);
        return new AdmissionsAnswer(true, answer, null, null, yt, year, false);
    }

    /** 근거 없음 안내 뒤에 붙는 유튜브 해석 참고 목록. */
    static String youtubeReferenceText(ResearchPack pack) {
        StringBuilder sb = new StringBuilder("참고 — 관련 유튜브 전문가 해석(공식 자료 아님, 영상 설명·챕터를 바탕으로 정리한 요지이며 발언을 직접 확인한 것은 아닙니다):");
        for (Evidence e : pack.getYoutubeHits()) {
            sb.append("\n- ").append(e.getTitle());
        }
        return sb.toString();
    }

    private AdmissionsAnswer refuse(String message, String sessionToken, String question, Integer year, String studentProfile) {
        saveSession(sessionToken, question, message, null, year, studentProfile);
        return new AdmissionsAnswer(true, message, null, null, List.of(), year, false);
    }

    /** 답변 속 두 자리 이상 숫자 중 근거·질문에 없는 것(2026~2030 연도 제외). */
    static List<String> ungroundedNumbers(String answer, String question, List<Evidence> evidence) {
        StringBuilder allowed = new StringBuilder(question == null ? "" : question).append(' ');
        for (Evidence e : evidence) {
            allowed.append(e.getTitle()).append(' ').append(e.getExcerpt()).append(' ').append(e.getNote()).append(' ');
        }
        java.util.Set<String> allowedNums = new java.util.HashSet<>();
        // 천 단위 쉼표(3,558)는 떼고 비교한다 — 근거 "3558명"과 답변 "3,558명"을 같은 수로 본다.
        java.util.regex.Matcher am = java.util.regex.Pattern.compile("\\d+").matcher(withoutThousandsSeparators(allowed.toString()));
        while (am.find()) allowedNums.add(am.group());
        List<String> bad = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(withoutThousandsSeparators(answer == null ? "" : answer));
        while (m.find()) {
            String n = m.group();
            if (n.length() < 2 || allowedNums.contains(n)) continue;
            if (n.length() == 4 && n.compareTo("2026") >= 0 && n.compareTo("2030") <= 0) continue;
            bad.add(n);
        }
        return bad;
    }

    /** "14,541" → "14541". 쉼표 뒤가 정확히 세 자리 숫자일 때만 뗀다("2, 3등급" 같은 나열은 그대로). */
    static String withoutThousandsSeparators(String text) {
        return text.replaceAll("(?<=\\d),(?=\\d{3}(?!\\d))", "");
    }

    private String buildEvidenceContext(List<Evidence> evidence) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Evidence e : evidence) {
            sb.append("── 근거 ").append(i++).append(" ──\n");
            sb.append("제목: ").append(e.getTitle()).append("\n");
            if (priorSourceLabel(e) != null) {
                sb.append("출처 종류: ").append(priorSourceLabel(e)).append(" (대학 입학처 원문 아님)\n");
            }
            if (isCurrentRatio(e)) {
                sb.append("결과 학년도: ").append(e.getAcademicYear())
                        .append("학년도 (올해 원서접수 최종 경쟁률 — 지난해 결과가 아니며 합격·등록 결과도 아님)\n");
            } else if (isPrior(e)) {
                sb.append("결과 학년도: ").append(e.getAcademicYear())
                        .append("학년도 (지난해 입시결과 — 올해 모집요강이 아니며 올해와 다를 수 있음)\n");
            } else if (e.getAcademicYear() != null) {
                sb.append("적용 학년도: ").append(e.getAcademicYear()).append("학년도");
                sb.append(Boolean.TRUE.equals(e.getYearConfirmed()) ? " (확정)" : " (계획 단계)").append("\n");
            }
            sb.append("출처: ").append(e.getNote()).append("\n");
            if (e.getExcerpt() != null) {
                String excerpt = e.getExcerpt().length() > MAX_EVIDENCE_CHARS
                        ? e.getExcerpt().substring(0, MAX_EVIDENCE_CHARS) + "…"
                        : e.getExcerpt();
                sb.append("내용: ").append(excerpt).append("\n");
            }
            if (e.getRefUrl() != null) {
                sb.append("원문: ").append(e.getRefUrl()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 2028 질문에는 아직 수집하지 않은 자료 목록을, 현 고1·예비 고1·중학생 질문에는 같은 제도라는 안내를 덧붙인다.
     * 근거에 없는 대학별 수치를 만들지 않게 하고, 뒤 학년도 질문을 2028 자료로 답하는 이유를 밝히게 한다.
     */
    static String reformNotes(String question, boolean asks2028) {
        StringBuilder sb = new StringBuilder();
        if (asks2028) {
            sb.append("[2028 수집 현황] 이 상담에 아직 수집하지 않은 자료: ").append(RESEARCH_STATUS_2028)
                    .append(". 질문이 이 내용을 물으면 근거를 지어내지 말고 '아직 수집 중'이라고 밝혀라.\n");
        }
        if (mentionsLaterCohort(question)) {
            sb.append("[뒤 학년도 안내] ").append(LATER_COHORT_NOTE)
                    .append(" 이 점을 답변 첫머리에 한 문장으로 밝히고, 근거의 학년도(2028)를 함께 적어라.\n");
        }
        return sb.toString();
    }

    /** 질문에 학년도가 없을 때 답변 첫머리에 밝힐 학년도. 화면에서 고른(또는 '올해' 표현으로 정한) 학년도를 쓰고, 없으면 2027. */
    static String yearLabelNote(Integer answerYear) {
        int year = answerYear == null ? 2027 : answerYear;
        return "질문에 학년도가 없으므로 답변 첫머리에 '" + year + "학년도 기준'이라고 밝혀라.\n";
    }

    private String buildUserPrompt(String question, Integer answerYear, ResearchPack pack, AdmissionsQueryParser.Parsed parsed, String studentProfile,
                                   AdmissionsFactSearchService.ScheduleCoverage coverage, boolean hasPrior,
                                   boolean hasCurrentRatio, List<String> comparisons) {
        StringBuilder sb = new StringBuilder();
        if (studentProfile != null && !studentProfile.isBlank()) {
            sb.append("[학생 정보 — 맞춤 설명에만 사용하며 합격 가능성을 추정하지 않는다]\n")
                    .append(studentProfile).append("\n\n");
        }
        sb.append("질문: ").append(question).append("\n\n");
        sb.append("위 [입시 공식자료 근거]만을 사용해 답변하라. 근거에 '확인되지 않음'이라고 적힌 항목은 추정하지 말고 "
                + "확인되지 않는다고 답하라. 근거에 없는 날짜·인원·비율·등급은 절대 쓰지 마라.\n");
        if (parsed.yearUnspecified()) {
            sb.append(yearLabelNote(answerYear));
        }
        if (!parsed.unknownUniversities().isEmpty()) {
            sb.append("[주의] 질문에 언급된 ").append(String.join(", ", parsed.unknownUniversities()))
                    .append("은(는) 등록된 공식 입시자료가 없다. 답변 첫머리에 '")
                    .append(String.join(", ", parsed.unknownUniversities()))
                    .append("의 자료는 등록되어 있지 않아 안내할 수 없다'고 밝히고, 그 대학에 대해서는 어떤 내용도 쓰지 마라.\n");
        }
        if (parsed.mentionsUnsupportedYear() && !parsed.years().isEmpty()) {
            sb.append("[주의] 질문에 지원하지 않는 학년도가 함께 언급되었다. 그 학년도의 자료는 없다고 밝히고, "
                    + "아래 근거를 그 학년도의 내용인 것처럼 쓰지 마라. 근거의 학년도를 명시해 답하라.\n");
        }
        if (coverage != null && !coverage.covered().isEmpty()) {
            sb.append("[전체 일정 질문] 대학별로 대학명을 구분해 나열하고 각 일정의 원문 날짜를 그대로 써라. 일정이 확인된 대학: ")
                    .append(String.join(", ", coverage.covered())).append(".");
            if (!coverage.missing().isEmpty()) {
                sb.append(" 일정이 확인되지 않은 대학: ").append(String.join(", ", coverage.missing()))
                        .append(" — 이 대학들은 '확인된 일정 자료가 없다'고 밝히고 각 대학 입학처 확인을 안내하라.");
            }
            sb.append(" 답변 첫머리에 기준 학년도를 밝혀라.\n");
        }
        if (hasPrior) {
            sb.append("[지난해 입시결과] 근거 중 '결과 학년도: 2026학년도'인 항목은 지난해 입시결과다. 이 수치를 쓸 때는 답변 첫머리에 '")
                    .append(PRIOR_DISCLAIMER).append("'라고 밝히고, 올해(2027학년도) 모집요강 수치와 섞지 마라. "
                    + "지표 이름은 근거에 적힌 원문 그대로 쓰고(예: '최종등록자 교과등급 70%컷'을 '평균'이나 '합격선'으로 바꾸지 않음), "
                    + "서로 다른 지표나 서로 다른 대학의 지표를 하나로 합치거나 평균내거나 환산하지 마라. 대학마다 지표가 다르면 대학별로 따로 적어라. "
                    + PRIOR_MISSING_RULE + PRIOR_SOURCE_RULE
                    + "근거 내용에 '산출 기준(원문)'이 있으면 그 대학 수치를 쓸 때 그 문장을 그대로 함께 적어라. "
                    + "합격·불합격 판정, 합격 확률, '가능성이 높다/낮다', '안정권/상향' 같은 표현은 절대 쓰지 마라.\n");
            if (!comparisons.isEmpty()) {
                sb.append("[성적 나란히 보기 — 서버가 만든 줄, 판정 없이 그대로 옮겨라]\n");
                for (String c : comparisons) sb.append("- ").append(c).append("\n");
                sb.append("위 줄 외에 학생 성적과 지표를 새로 비교하거나 차이를 해석하지 마라.\n");
            }
        }
        if (hasCurrentRatio) {
            sb.append("[올해 원서접수 경쟁률] 근거 중 '올해 원서접수 최종 경쟁률'이라고 적힌 항목은 올해(2027학년도) 원서접수 마감 경쟁률이다. "
                    + "이 수치를 쓸 때는 답변 첫머리에 '").append(CURRENT_RATIO_NOTICE).append("'라고 밝히고, 지난해 결과라고 부르지 마라. "
                    + "지난해 입시결과 근거가 함께 있으면 답변 첫머리에 '" + BOTH_YEARS_NOTICE + "'라고 밝히고, 올해 경쟁률과 지난해 결과를 "
                    + "학년도별 단락으로 나눠 적어라. 같은 전형이 두 학년도 근거에 모두 있어도 각 수치는 그 수치가 적힌 근거의 학년도로만 적고, "
                    + "한 줄에 두 학년도 수치를 섞지 마라. "
                    + "근거에 없는 수치는 쓰지 마라. " + PRIOR_SOURCE_RULE
                    + "합격·불합격 판정, 합격 확률, '가능성이 높다/낮다' 같은 표현은 절대 쓰지 마라.\n");
        }
        if (parsed.asksAdmissionChance()) {
            sb.append("[합격 가능성 질문] 합격 가능성·합격 확률·합격선은 판단하거나 추정하지 마라. 답변 첫 문장에서 "
                    + "'합격 가능성은 판단해 드릴 수 없지만, 지원 판단에 참고할 공식 정보를 정리해 드립니다.'라고 밝힌 뒤, "
                    + "근거에 있는 모집인원·전형방법(반영 비율)·수능최저·제출서류를 전형별로 정리하라. 학생 성적과 비교해 "
                    + "유불리를 말하지 말고, 끝에 학교 진학 상담과 대학 입학처 상담을 함께 권하라.\n");
        }
        if (!pack.getYoutubeHits().isEmpty()) {
            sb.append("[유튜브 전문가 해석] 시스템의 '참고: 유튜브 전문가 해석' 항목 중 질문과 직접 관련된 것이 있으면, 공식 정보 뒤에 "
                    + "'참고 — 유튜브 전문가 해석(공식 자료 아님)' 단락을 따로 두고 1~3문장으로 요약하라. 채널 이름과 대상 학년도를 밝히고, "
                    + "영상 설명·챕터를 바탕으로 정리한 내용이라 발언을 직접 확인한 것은 아니라고 덧붙여라. 이 단락에는 숫자를 쓰지 말고 "
                    + "확정 사실처럼 쓰지 마라. 관련이 없으면 이 단락을 쓰지 마라.\n");
        }
        if (!pack.getConflictHits().isEmpty()) {
            sb.append("[주의] 충돌 중인 정보 ").append(pack.getConflictHits().size()).append("건이 있다. "
                    + "관련 내용은 확정적으로 답하지 말고 '현재 확인 중인 정보입니다'라고 밝혀라.\n");
        }
        if (!pack.getGapNotes().isEmpty()) {
            sb.append("[부족] ").append(String.join(" / ", pack.getGapNotes())).append("\n");
        }
        return sb.toString();
    }

    /** 유튜브 전문가 해석 블록: 공식 근거와 분리해 "공식 자료 아님"을 표시한다. 답변 사후 검증(수치 근거)에는 넣지 않는다. */
    static String buildYoutubeBlock(ResearchPack pack) {
        if (pack == null || pack.getYoutubeHits() == null || pack.getYoutubeHits().isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n[참고: 유튜브 전문가 해석 — 공식 자료 아님, 사실 근거로 쓰지 말 것]\n");
        for (Evidence e : pack.getYoutubeHits()) {
            sb.append("- ").append(e.getTitle()).append("\n  ").append(e.getExcerpt() == null ? "" : e.getExcerpt().replace("\n", " ")).append("\n");
        }
        return sb.toString();
    }

    /** 유튜브 해석 출처(참고). */
    static List<AnswerSource> youtubeSources(ResearchPack pack) {
        if (pack == null || pack.getYoutubeHits() == null) return List.of();
        List<AnswerSource> out = new ArrayList<>();
        for (Evidence e : pack.getYoutubeHits()) {
            String url = e.getRefUrl() == null || e.getRefUrl().isBlank() ? null : e.getRefUrl();
            out.add(new AnswerSource("유튜브 전문가 해석 (참고, 공식 아님)", e.getTitle(), url, e.getRefId()));
        }
        return out;
    }

    /** 출처 패널용: 질문과 관련된 근거만 중복 없이 상위 {@link #MAX_SOURCES}개. LLM에 준 근거 자체는 바꾸지 않는다. */
    List<AnswerSource> buildSources(List<Evidence> evidence, String question) {
        List<AnswerSource> out = new ArrayList<>();
        for (Evidence e : AdmissionsSourceSelector.select(evidence, question, MAX_SOURCES)) {
            String kind = switch (e.getSourceClass()) {
                case OFFICIAL_CONFIRMED -> "공식 자료 (확정)";
                case OFFICIAL_PLANNED -> "공식 자료 (계획)";
                case OFFICIAL_DOCUMENT -> "공식자료 원문";
                case OFFICIAL_PRIOR_RESULT -> priorSourceKind(e);
                case UNVERIFIED_MIRROR -> "사설 미러본 (공식 원본 아님)";
                default -> "참고 자료";
            };
            String url = e.getRefUrl() == null || e.getRefUrl().isBlank() ? null : e.getRefUrl();
            out.add(new AnswerSource(kind, e.getTitle(), url, e.getRefId()));
        }
        // 지난해 결과를 근거로 줬으면 출처에도 반드시 하나 이상 보인다.
        if (out.stream().noneMatch(s -> s.id() != null && s.id().startsWith("prior:"))) {
            evidence.stream().filter(AdmissionsChatService::isPrior).findFirst().ifPresent(e -> out.add(0,
                    new AnswerSource(priorSourceKind(e), e.getTitle(),
                            e.getRefUrl() == null || e.getRefUrl().isBlank() ? null : e.getRefUrl(), e.getRefId())));
        }
        return out;
    }

    private void saveSession(String sessionToken, String question, String answer,
                             ResearchPack pack, Integer academicYear, String studentProfile) {
        saveSession(sessionToken,question,answer,pack,academicYear,studentProfile,List.of());
    }

    private void saveSession(String sessionToken,String question,String answer,ResearchPack pack,
                             Integer academicYear,String studentProfile,List<AnswerSource> sources) {
        try {
            String token = (sessionToken != null && sessionToken.matches("[A-Za-z0-9_-]{8,64}"))
                    ? sessionToken
                    : UUID.randomUUID().toString().replace("-", "");
            String fingerprint = sha256(question);
            StringBuilder evidenceSummary = new StringBuilder();
            if (pack != null) {
                for (Evidence e : usableEvidence(pack)) {
                    evidenceSummary.append(e.getStepNo()).append(":").append(e.getRefId()).append(";");
                }
            }
            AdmissionsCounselingSession session = AdmissionsCounselingSession.builder()
                    .sessionToken(token)
                    .question(question.length() > 2000 ? question.substring(0, 2000) : question)
                    .questionFingerprint(fingerprint)
                    .answer(answer.length() > 8000 ? answer.substring(0, 8000) : answer)
                    .evidenceSummary(sources.isEmpty()?evidenceSummary.toString():new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sources))
                    .studentProfile(studentProfile)
                    .academicYear(academicYear)
                    .build();
            sessionRepository.save(session);
        } catch (Exception e) {
            // 세션 저장 실패는 답변에 영향 없음
            log.warn("admissions session save failed");
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static AdmissionsAnswer failure(String message) {
        return new AdmissionsAnswer(false, "", message, null, List.of(), null, false);
    }
}
