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
    static final int MAX_CONTEXT_EVIDENCE = 12;
    /** 답변에 붙이는 출처 상한. */
    static final int MAX_SOURCES = 8;
    /** 근거 1건 요약 상한. */
    static final int MAX_EVIDENCE_CHARS = 400;

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
                    + " 2028학년도 자료는 '계획 단계로 변경될 수 있다'는 점을 함께 밝힌다."
                    + " 근거에 없는 대학명·전형명·날짜·인원·비율을 만들어내지 않는다."
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
    static final String MSG_NOT_COLLECTED =
            "경쟁률·전년도 입시결과·충원 현황·권장 이수 과목은 아직 수집된 공식 자료가 없어 안내할 수 없습니다. "
                    + "해당 정보는 각 대학 입학처 발표를 확인해 주세요.";
    static final String MSG_UNPUBLISHED_2028 =
            "2028학년도 모집요강의 일정·수치는 대부분의 대학에서 아직 발표 전입니다. 현재 확보한 2028학년도 자료가 "
                    + "질문에 해당하지 않아 확정된 일정이나 수치를 안내할 수 없습니다. 각 대학 입학처 발표를 확인해 주세요.";
    static final String MSG_UNSUPPORTED_YEAR =
            "입시상담 가이드는 2027학년도(확정)와 2028학년도(계획) 자료만 다룹니다. 다른 학년도는 안내할 수 없습니다.";
    static final String MSG_UNGROUNDED_NUMBER =
            "근거 자료에서 확인되지 않는 수치가 답변에 포함되어 답변을 제공하지 않습니다. 질문을 더 구체적으로 "
                    + "(대학·학과·전형·학년도) 다시 입력해 주세요.";
    static final String MSG_TITLE_ONLY =
            "관련 공식 모집요강 문서의 제목만 확인되고 질문에 답할 수 있는 본문·세부 자료(모집단위·전형·일정)는 아직 없습니다. "
                    + "문서 제목만으로는 내용을 안내할 수 없으니 해당 대학 입학처의 모집요강 원문을 확인해 주세요.";
    static final String MSG_NO_EVIDENCE =
            "해당 입시 정보를 찾지 못했습니다. 2027학년도 7개 대학(서울대·연세대·고려대·성균관대·한양대·경희대·인하대)의 "
                    + "공식 모집요강 기준으로 답변할 수 있습니다. 다른 질문을 해보세요.";

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
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        if (question.isEmpty()) {
            return failure("질문을 입력해 주세요.");
        }
        if (question.length() > MAX_QUESTION_CHARS) {
            return failure("질문이 너무 깁니다. " + MAX_QUESTION_CHARS + "자 이내로 입력해 주세요.");
        }

        // 1. 질문 분석 + 학과·전형·일정 DB 조회
        AdmissionsFactSearchService.FactResult facts = factSearchService.search(question, academicYearFilter);
        AdmissionsQueryParser.Parsed parsed = facts.parsed();

        // 2. LLM 없이 차단하는 질문: 합격 가능성 판단/추천, 수집하지 않은 자료, 미지원 학년도
        if (parsed.asksAdmissionChance()) {
            return refuse(MSG_CHANCE, sessionToken, question, academicYearFilter);
        }
        if (parsed.asksNotCollected()) {
            return refuse(MSG_NOT_COLLECTED, sessionToken, question, academicYearFilter);
        }
        if (parsed.mentionsUnsupportedYear() && parsed.years().isEmpty()) {
            return refuse(MSG_UNSUPPORTED_YEAR, sessionToken, question, academicYearFilter);
        }

        // 3. 조사 사다리 + 사실 근거 병합 (GUIDE는 근거가 아니다)
        ResearchPack pack = researchService.research(question, academicYearFilter);
        List<Evidence> usable = mergeUsable(facts.evidence(), usableEvidence(pack));

        // 4. 2028 질문은 2028 근거만 인정한다. 없으면 "미발표"로 답하고 2027 자료로 대신 답하지 않는다.
        boolean asks2028Only = facts.searchedYears().equals(java.util.Set.of(2028));
        if (asks2028Only) {
            usable = usable.stream().filter(e -> Integer.valueOf(2028).equals(e.getAcademicYear())).toList();
            // 문서 제목만 있는 자료(material:)로는 2028 일정·수치를 답할 수 없다. 학과·전형·일정 행 또는 사실 지식이 있어야 한다.
            if (usable.stream().noneMatch(e -> isFactRow(e.getRefId()))) {
                return refuse(MSG_UNPUBLISHED_2028, sessionToken, question, academicYearFilter);
            }
        }

        // 5. 사실 근거가 하나도 없으면(GUIDE만 검색된 경우 포함) LLM을 호출하지 않는다.
        if (usable.isEmpty()) {
            return refuse(MSG_NO_EVIDENCE, sessionToken, question, academicYearFilter);
        }

        // 5-1. 문서 제목·발행기관만 있는 공식자료(material:)로는 내용을 알 수 없다. 사실 근거(학과·전형·일정 행 또는
        //      FACT 지식)가 하나도 없으면 LLM이 제목만 보고 내용을 지어내지 못하도록 호출하지 않는다.
        if (usable.stream().noneMatch(e -> isFactRow(e.getRefId()))) {
            return refuse(MSG_TITLE_ONLY, sessionToken, question, academicYearFilter);
        }

        // 6. 근거를 프롬프트에 조립
        String evidenceContext = buildEvidenceContext(usable);
        String systemPrompt = BASE_PROMPT + "\n\n[입시 공식자료 근거]\n" + evidenceContext
                + buildGuideBlock(pack);
        String userPrompt = buildUserPrompt(question, pack, parsed);

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
            return refuse(MSG_UNGROUNDED_NUMBER, sessionToken, question, academicYearFilter);
        }

        // 9. 출처 조립 (URL이 없어도 문서 ID와 제목으로 표시, GUIDE는 제외)
        List<AnswerSource> sources = buildSources(usable);

        String answer = completion.text();
        saveSession(sessionToken, question, answer, pack, academicYearFilter);

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

    /** 내용이 있는 사실 근거(학과·전형·일정 행, 사실 지식). 문서 제목만 있는 material은 제외. */
    static boolean isFactRow(String refId) {
        return refId != null && (refId.startsWith("dept:") || refId.startsWith("type:")
                || refId.startsWith("sched:") || refId.startsWith("knowledge:"));
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

    private AdmissionsAnswer refuse(String message, String sessionToken, String question, Integer year) {
        saveSession(sessionToken, question, message, null, year);
        return new AdmissionsAnswer(true, message, null, null, List.of(), year, false);
    }

    /** 답변 속 두 자리 이상 숫자 중 근거·질문에 없는 것(2026~2030 연도 제외). */
    static List<String> ungroundedNumbers(String answer, String question, List<Evidence> evidence) {
        StringBuilder allowed = new StringBuilder(question == null ? "" : question).append(' ');
        for (Evidence e : evidence) {
            allowed.append(e.getTitle()).append(' ').append(e.getExcerpt()).append(' ').append(e.getNote()).append(' ');
        }
        java.util.Set<String> allowedNums = new java.util.HashSet<>();
        java.util.regex.Matcher am = java.util.regex.Pattern.compile("\\d+").matcher(allowed);
        while (am.find()) allowedNums.add(am.group());
        List<String> bad = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(answer == null ? "" : answer);
        while (m.find()) {
            String n = m.group();
            if (n.length() < 2 || allowedNums.contains(n)) continue;
            if (n.length() == 4 && n.compareTo("2026") >= 0 && n.compareTo("2030") <= 0) continue;
            bad.add(n);
        }
        return bad;
    }

    private String buildEvidenceContext(List<Evidence> evidence) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Evidence e : evidence) {
            sb.append("── 근거 ").append(i++).append(" ──\n");
            sb.append("제목: ").append(e.getTitle()).append("\n");
            if (e.getAcademicYear() != null) {
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

    private String buildUserPrompt(String question, ResearchPack pack, AdmissionsQueryParser.Parsed parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("질문: ").append(question).append("\n\n");
        sb.append("위 [입시 공식자료 근거]만을 사용해 답변하라. 근거에 '확인되지 않음'이라고 적힌 항목은 추정하지 말고 "
                + "확인되지 않는다고 답하라. 근거에 없는 날짜·인원·비율·등급은 절대 쓰지 마라.\n");
        if (parsed.yearUnspecified()) {
            sb.append("질문에 학년도가 없으므로 답변 첫머리에 '2027학년도 기준'이라고 밝혀라.\n");
        }
        if (!pack.getYoutubeHits().isEmpty()) {
            sb.append("[참고] 유튜브 의견 ").append(pack.getYoutubeHits().size()).append("건이 있으나, "
                    + "이는 확정 사실이 아니므로 본 답변에 포함하지 말고, 필요한 경우 '일부 유튜브 분석에서는 ~라는 의견도 있다' 정도로만 언급하라.\n");
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

    private List<AnswerSource> buildSources(List<Evidence> evidence) {
        java.util.LinkedHashMap<String, AnswerSource> byId = new java.util.LinkedHashMap<>();
        for (Evidence e : evidence) {
            if (e.getSourceClass() == AdmissionsResearchService.SourceClass.GUIDE_NOTE) continue; // 사실 근거 아님
            String kind = switch (e.getSourceClass()) {
                case OFFICIAL_CONFIRMED -> "공식 자료 (확정)";
                case OFFICIAL_PLANNED -> "공식 자료 (계획)";
                case OFFICIAL_DOCUMENT -> "공식자료 원문";
                default -> "참고 자료";
            };
            String url = e.getRefUrl() == null || e.getRefUrl().isBlank() ? null : e.getRefUrl();
            byId.putIfAbsent(e.getRefId(), new AnswerSource(kind, e.getTitle(), url, e.getRefId()));
        }
        return byId.values().stream().limit(MAX_SOURCES).toList();
    }

    private void saveSession(String sessionToken, String question, String answer,
                             ResearchPack pack, Integer academicYear) {
        try {
            String token = (sessionToken == null || sessionToken.isBlank())
                    ? UUID.randomUUID().toString().replace("-", "")
                    : sessionToken;
            String fingerprint = sha256(question);
            // 중복 질문은 기존 세션 유지 (fingerprint unique가 아니므로 조회 후 스킵)
            if (sessionRepository.findByQuestionFingerprint(fingerprint).isPresent()) {
                return;
            }
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
                    .evidenceSummary(evidenceSummary.toString())
                    .academicYear(academicYear)
                    .build();
            sessionRepository.save(session);
        } catch (Exception e) {
            // 세션 저장 실패는 답변에 영향 없음
            log.warn("admissions session save failed: {}", e.getMessage());
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
