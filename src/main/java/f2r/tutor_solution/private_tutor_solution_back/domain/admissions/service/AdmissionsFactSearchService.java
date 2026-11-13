package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsDepartment;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSusiMinimum;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsType;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsPriorResultRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsSusiMinimumRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 학과(모집단위)·전형·일정 실제 DB 데이터를 상담 근거로 만든다.
 *
 * <p>규칙:
 * <ul>
 *   <li>승인(ACTIVE)된 행만 사용한다. DRAFT/REVIEW 행은 검색되지 않는다.</li>
 *   <li>학년도로 먼저 거른다. 질문에 학년도가 없으면 2027(확정)만 본다. 2027과 2028을 한 근거 목록에 섞는 것은
 *       질문이 둘 다 명시한 경우에만 한다.</li>
 *   <li>값이 비어 있는 필드는 "확인되지 않음"으로 표기한다(추정 금지).</li>
 * </ul>
 * 데이터 규모(수천 행 이하)를 전제로 메모리에서 거른다. 커지면 대학·학년도 인덱스 쿼리로 바꾼다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionsFactSearchService {

    static final int STEP_FACT = 0;
    static final int MAX_DEPARTMENTS = 10;
    static final int MAX_TYPES = 8;
    static final int MAX_SCHEDULES = 8;
    /** 지난해 입시결과: 근거로 쓰는 전형·모집단위 수 상한. */
    static final int MAX_PRIOR_UNITS = 8;
    /** 학과를 말한 지난해 입시결과 질문: 그 학과의 세부모집·전형이 잘리지 않도록 쓰는 상한(답변 근거 전체 상한 16 중 4건은 올해 학과·전형 자료 몫으로 남긴다). */
    static final int MAX_PRIOR_DEPARTMENT_UNITS = 12;
    /** 수시 수능최저: 근거로 쓰는 규칙 수 상한. */
    static final int MAX_MINIMUM_RULES = 8;
    /** 대학을 지정하지 않은 전체 일정 질문: 대학당 근거 수와 전체 상한. */
    static final int MAX_SCHEDULES_PER_UNIVERSITY = 2;
    static final int MAX_ALL_UNIVERSITY_SCHEDULES = 14;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AdmissionsUniversityRepository universityRepository;
    private final AdmissionsDepartmentRepository departmentRepository;
    private final AdmissionsTypeRepository typeRepository;
    private final AdmissionsScheduleRepository scheduleRepository;
    private final AdmissionsPriorResultRepository priorResultRepository;
    private final AdmissionsSusiMinimumRepository susiMinimumRepository;

    /**
     * @param evidence 근거(없으면 빈 목록)
     * @param parsed 질문 분석 결과
     * @param searchedYears 실제로 조회한 학년도
     * @param unpublished 2028처럼 아직 발표 자료가 없는 학년도를 물었고 근거가 하나도 없는 경우
     * @param scheduleCoverage 대학을 지정하지 않은 일정 질문에서 일정 근거가 있는 대학/없는 대학 (해당 없으면 null)
     */
    public record FactResult(List<Evidence> evidence, AdmissionsQueryParser.Parsed parsed,
                             Set<Integer> searchedYears, boolean unpublished, ScheduleCoverage scheduleCoverage) {

        public FactResult(List<Evidence> evidence, AdmissionsQueryParser.Parsed parsed,
                          Set<Integer> searchedYears, boolean unpublished) {
            this(evidence, parsed, searchedYears, unpublished, null);
        }
    }

    /** 전체 일정 질문의 대학별 근거 현황. */
    public record ScheduleCoverage(List<String> covered, List<String> missing) {}

    public FactResult search(String question, Integer yearOverride) {
        List<String> univNames = universityRepository.findAll().stream()
                .map(AdmissionsUniversity::getName).toList();
        AdmissionsQueryParser.Parsed parsed = AdmissionsQueryParser.parse(question, univNames);

        Set<Integer> years = new LinkedHashSet<>();
        if (yearOverride != null) years.add(yearOverride);
        else if (!parsed.years().isEmpty()) years.addAll(parsed.years());
        else years.add(2027); // 학년도 미지정: 확정 자료(2027)만. 답변에 기준 학년도를 명시한다.

        List<Evidence> out = new ArrayList<>();
        // 지난해 입시결과는 질문 학년도와 상관없이(올해 자료와 다른 학년도임을 표시해) 맨 앞에 둔다.
        out.addAll(searchPriorResults(question, parsed));
        out.addAll(searchSusiMinimums(question, parsed, years));
        out.addAll(searchDepartments(question, parsed, years));
        out.addAll(searchTypes(question, parsed, years));
        ScheduleCoverage coverage = null;
        if (parsed.universities().isEmpty() && parsed.wantsSchedule()) {
            List<Evidence> all = searchAllUniversitySchedules(question, parsed, years);
            out.addAll(all);
            coverage = coverageOf(all, univNames);
        } else {
            out.addAll(searchSchedules(question, parsed, years));
        }

        boolean askedUnpublished = years.contains(2028) && !years.contains(2027);
        return new FactResult(out, parsed, years, askedUnpublished && out.isEmpty(), coverage);
    }

    // ── 수시 수능최저학력기준 ─────────────────────────────────────────

    /**
     * 질문에 '최저'와 대학이 있으면 그 대학의 승인된(ACTIVE) 수능최저 규칙을 원문 문장 그대로 근거로 만든다.
     * 전형·모집단위를 말했으면 그 규칙만, 아니면 앞에서부터 {@value #MAX_MINIMUM_RULES}개까지.
     * 확인 필요(needs_review) 규칙은 원문과 사유를 함께 넣고 '판정하지 않음'을 밝힌다.
     */
    List<Evidence> searchSusiMinimums(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        if (p.universities().isEmpty() || question == null || !question.contains("최저")) return List.of();
        List<AdmissionsSusiMinimum> rules = new ArrayList<>();
        for (AdmissionsUniversity u : universityRepository.findAll()) {
            if (!p.universities().contains(u.getName())) continue;
            for (Integer y : years) {
                rules.addAll(susiMinimumRepository.findByUniversityUniversityIdAndAcademicYearAndStatus(
                        u.getUniversityId(), y, KnowledgeStatus.ACTIVE));
            }
        }
        List<AdmissionsSusiMinimum> usable = rules.stream()
                .filter(r -> approvedSource(r.getSource(), r.getAcademicYear()))
                .filter(r -> campusAllowed(question, r.getSource(), r.getLabel()))
                .toList();
        List<AdmissionsSusiMinimum> mentioned = usable.stream().filter(r -> minimumMentioned(question, r)).toList();
        return (mentioned.isEmpty() ? usable : mentioned).stream()
                .sorted(java.util.Comparator.comparing(AdmissionsSusiMinimum::getMinimumId))
                .limit(MAX_MINIMUM_RULES)
                .map(AdmissionsFactSearchService::minimumEvidence)
                .toList();
    }

    /** 질문이 규칙의 전형명 또는 모집단위를 가리키는지. */
    static boolean minimumMentioned(String question, AdmissionsSusiMinimum r) {
        for (String t : jsonStrings(r.getTypeNames())) {
            if (minimumTypeMentioned(question, t)) return true;
        }
        for (String d : jsonStrings(r.getDepartmentNames())) {
            if (AdmissionsQueryParser.mentionsDepartment(question, d)) return true;
        }
        return false;
    }

    private static final Set<String> BROAD_TYPE_WORDS = Set.of("학생부교과", "학생부종합", "학생부", "수시", "정시", "교과", "종합");

    /**
     * 전형명이 질문에 언급됐는지(수능최저용). 세부 이름(예: "학생부종합(추천형)"의 "추천형")이 있으면 그것이 맞아야 한다.
     * "학생부종합"만 맞는 것으로는 서류형·추천형을 가르지 못하기 때문이다. 세부 이름이 없는 전형명은 입시결과와 같은 규칙.
     */
    static boolean minimumTypeMentioned(String question, String typeName) {
        if (question == null || typeName == null) return false;
        String q = question.replaceAll("\\s+", "");
        if (q.contains(typeName.replaceAll("\\s+", ""))) return true;
        List<String> detail = new ArrayList<>();
        for (String piece : typeName.split("[\\[\\]()/\\s·,_]+")) {
            String p = piece.replaceAll("전형$", "").trim();
            if (p.length() >= 2 && !BROAD_TYPE_WORDS.contains(p)) detail.add(p);
        }
        if (detail.isEmpty()) return priorTypeMentioned(question, typeName);
        return detail.stream().anyMatch(q::contains);
    }

    static Evidence minimumEvidence(AdmissionsSusiMinimum r) {
        String types = String.join(", ", jsonStrings(r.getTypeNames()));
        String scope = r.getUnitScopeText() == null || r.getUnitScopeText().isBlank() ? "" : " / 적용 모집단위: " + r.getUnitScopeText();
        StringBuilder ex = new StringBuilder();
        ex.append("전형: ").append(types.isEmpty() ? r.getLabel() : types).append(scope).append("\n");
        if (r.getRuleKind() == AdmissionsSusiMinimum.RuleKind.NO_MINIMUM) {
            ex.append("수능최저학력기준 없음. 원문: ").append(r.getRawText());
        } else {
            ex.append("수능최저학력기준 원문: ").append(r.getRawText());
            if (r.getRequiredAreasText() != null && !r.getRequiredAreasText().isBlank()) {
                ex.append("\n응시 조건 원문: ").append(r.getRequiredAreasText());
            }
        }
        if (r.getEvidencePages() != null && !r.getEvidencePages().isBlank()) {
            ex.append("\n요강 근거: ").append(r.getEvidencePages().replaceAll("[\\[\\]\\s]", "")).append("쪽");
        }
        if (r.getNeedsReviewReason() != null && !r.getNeedsReviewReason().isBlank()) {
            ex.append("\n원문 확인 필요: ").append(r.getNeedsReviewReason())
                    .append(" — 이 규칙으로는 충족 여부를 판정하지 않는다.");
        }
        String title = r.getUniversity().getName() + " " + r.getAcademicYear() + "학년도 수시 수능최저학력기준: " + r.getLabel();
        return evidence("susimin", r.getMinimumId(), r.getAcademicYear(), title, ex.toString(), r.getSource());
    }

    private static List<String> jsonStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode n : MAPPER.readTree(json)) if (n.isTextual() && !n.asText().isBlank()) out.add(n.asText());
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── 지난해 입시결과 ─────────────────────────────────────────────

    /**
     * 경쟁률·입결·충원·등급컷 또는 합격 가능성 질문이고 대학을 말했을 때, 그 대학의 승인된(ACTIVE) 지난해 입시결과를
     * 전형·모집단위 단위로 묶어 근거로 만든다. 출처 공식자료도 승인돼 있어야 한다.
     * 학과·전형을 말했으면 그 단위만, 말하지 않았으면 앞에서부터 {@value #MAX_PRIOR_UNITS}개까지.
     * 학과를 말했으면 그 학과의 세부모집·전형이 빠지지 않도록 {@value #MAX_PRIOR_DEPARTMENT_UNITS}개까지 쓰고, 같은 전형끼리 붙여 둔다.
     */
    List<Evidence> searchPriorResults(String question, AdmissionsQueryParser.Parsed p) {
        if (p.universities().isEmpty() || !(p.asksPriorResult() || p.asksAdmissionChance())) return List.of();
        boolean asksSusi = question.contains("수시");
        boolean asksJeongsi = question.contains("정시");
        java.util.Map<String, List<AdmissionsPriorResult>> units = new java.util.LinkedHashMap<>();
        // 질문이 말한 대학 행만 DB에서 읽는다(입시결과는 대학당 수천 행이라 전체를 읽지 않는다).
        List<Long> univIds = universityRepository.findAll().stream()
                .filter(u -> p.universities().contains(u.getName()))
                .map(AdmissionsUniversity::getUniversityId).toList();
        if (univIds.isEmpty()) return List.of();
        for (AdmissionsPriorResult r : priorResultRepository.findByUniversityUniversityIdInAndStatus(univIds, KnowledgeStatus.ACTIVE)) {
            if (r.getSource() == null || r.getSource().getStatus() != KnowledgeStatus.ACTIVE) continue;
            if (!univSelected(p, r.getUniversity())) continue;
            // OTHER(원문에 수시·정시 구분 없음, 예: 한국예술종합학교)는 수시·정시 어느 질문에서도 빼지 않고 그대로 표시한다.
            if (asksSusi && !asksJeongsi && "JEONGSI".equals(r.getRoundType())) continue;
            if (asksJeongsi && !asksSusi && "SUSI".equals(r.getRoundType())) continue;
            if (!campusAllowed(question, r.getSource(), r.getTypeName() + " " + r.getDepartmentName())) continue;
            // 결과 학년도가 다른 행(지난해 입시결과·올해 원서접수 경쟁률)은 같은 전형·모집단위여도 한 단위로 합치지 않는다.
            units.computeIfAbsent(r.getUniversity().getUniversityId() + "|" + r.getResultYear() + "|" + r.unitKey(), k -> new ArrayList<>()).add(r);
        }
        List<List<AdmissionsPriorResult>> all = byYearIntent(question, new ArrayList<>(units.values()));
        List<List<AdmissionsPriorResult>> matched = all.stream()
                .filter(rows -> priorDepartmentMentioned(question, rows.get(0).getDepartmentName())
                        || priorTypeMentioned(question, rows.get(0).getTypeName()))
                .toList();
        if (matched.isEmpty()) {
            matched = all.stream()
                    .filter(rows -> AdmissionsQueryParser.mentionsDepartmentByKeyword(question, rows.get(0).getDepartmentName()))
                    .toList();
        }
        // 학과와 전형을 둘 다 말했으면 둘 다 맞는 단위를 앞에 둔다.
        // 같은 순위 안에서는 같은 전형끼리 붙여 둔다(예: 일반 연기(여)·연기(남)·통합 정원이 흩어지지 않게).
        List<List<AdmissionsPriorResult>> chosen = new ArrayList<>(matched.isEmpty() ? all : matched);
        java.util.Map<String, Integer> typeOrder = new java.util.HashMap<>();
        for (List<AdmissionsPriorResult> rows : chosen) typeOrder.putIfAbsent(String.valueOf(rows.get(0).getTypeName()), typeOrder.size());
        chosen.sort(java.util.Comparator.comparingInt((List<AdmissionsPriorResult> rows) -> {
            AdmissionsPriorResult r = rows.get(0);
            boolean dept = priorDepartmentMentioned(question, r.getDepartmentName());
            boolean type = priorTypeMentioned(question, r.getTypeName());
            return dept && type ? 0 : dept || type ? 1 : 2;
        }).thenComparingInt(rows -> typeOrder.get(String.valueOf(rows.get(0).getTypeName()))));
        // 학과를 말했으면 그 학과의 세부모집(예: 연기(여)·연기(남))이 상한에 잘려 한쪽 값이 전체처럼 보이지 않게 상한을 넓힌다.
        boolean deptNamed = chosen.stream().anyMatch(rows -> priorDepartmentMentioned(question, rows.get(0).getDepartmentName()));
        int cap = deptNamed ? MAX_PRIOR_DEPARTMENT_UNITS : MAX_PRIOR_UNITS;
        List<Evidence> out = new ArrayList<>();
        for (List<AdmissionsPriorResult> rows : chosen) {
            out.add(priorEvidence(rows));
            if (out.size() >= cap) break;
        }
        return out;
    }

    /**
     * 질문이 입시결과의 전형을 가리키는지. 원문 전형명은 대학마다 "학생부종합[활동우수형]", "학생부교과 (추천형)"처럼 괄호가 섞여
     * 있어 괄호([]·()) 안팎 조각을 따로 본다. "학생부교과"·"학생부종합"처럼 대분류만 맞는 조각보다 세부 이름이 맞는 쪽을 쓴다.
     */
    /** 질문이 이 모집단위의 학과를 말했는가 — 전체 이름 또는 '학과 / 세부모집'의 학과 부분. */
    static boolean priorDepartmentMentioned(String question, String departmentName) {
        return AdmissionsQueryParser.mentionsDepartment(question, departmentName)
                || AdmissionsQueryParser.mentionsDepartment(question, AdmissionsQueryParser.departmentHead(departmentName));
    }

    static boolean priorTypeMentioned(String question, String typeName) {
        if (typeName == null) return false;
        String q = question.replaceAll("\\s+", "");
        // "전체(농생명계열 제외)"의 '농생명계열'은 빠진 대상이라, 질문이 '제외'를 말하지 않으면 그 조각으로 맞추지 않는다.
        String name = q.contains("제외") ? typeName : typeName.replaceAll("[^\\[\\]()/\\s·,_]+\\s*제외", "");
        for (String piece : name.split("[\\[\\]()/\\s·,_]+")) {
            String p = piece.replaceAll("전형$", "").trim();
            if (p.length() < 2 || p.equals("학생부") || p.equals("수시") || p.equals("정시")) continue;
            if (q.contains(p)) return true;
            // "학생부교과" ↔ 질문의 "교과", "학생부종합" ↔ "학종"/"종합"
            if (p.equals("학생부교과") && q.contains("교과")) return true;
            if (p.equals("학생부종합") && (q.contains("학종") || q.contains("종합"))) return true;
        }
        return false;
    }

    static Evidence priorEvidence(List<AdmissionsPriorResult> rows) {
        AdmissionsPriorResult first = rows.stream().min(java.util.Comparator.comparing(AdmissionsPriorResult::getPriorResultId)).orElseThrow();
        AdmissionsOfficialMaterial source = first.getSource();
        String url = first.getSourceUrl() != null && !first.getSourceUrl().isBlank() ? first.getSourceUrl()
                : source == null ? null : source.getDocumentUrl();
        boolean current = isCurrentYear(first);
        String title = first.getUniversity().getName() + " " + first.getResultYear()
                + (current ? "학년도" + CURRENT_YEAR_TAG + " " : "학년도(지난해) ")
                + roundLabel(first.getRoundType()) + (first.getGroupName() == null ? "" : " " + first.getGroupName() + "군")
                + (current ? " 경쟁률: " : " 입시결과: ") + first.getTypeName() + " " + first.getDepartmentName();
        return Evidence.builder()
                .stepNo(STEP_FACT)
                .sourceClass(SourceClass.OFFICIAL_PRIOR_RESULT)
                .refId("prior:" + first.getPriorResultId())
                .refUrl(url)
                .title(title)
                .excerpt(withCaveat(priorCaveat(source == null ? null : source.getTitle()),
                        (current ? "[" + first.getResultYear() + "학년도 올해 원서접수 최종 경쟁률]\n" : "") + formatPriorUnit(rows)))
                .note((current ? "대학 입학처 공식 원서접수 최종 경쟁률(" + first.getResultYear() + "학년도, 올해 — 합격·등록 결과 아님) / 출처: "
                        : "대학 입학처 공식 입시결과(" + first.getResultYear() + "학년도, 지난해 — 올해 결과 아님) / 출처: ")
                        + (source == null ? first.getInstitutionName() : source.getTitle()))
                .academicYear(first.getResultYear())
                .yearConfirmed(true)
                .build();
    }

    private static final java.util.regex.Pattern ASKS_LAST_YEAR = java.util.regex.Pattern.compile("작년|지난해|전년도|2026");
    private static final java.util.regex.Pattern ASKS_THIS_YEAR = java.util.regex.Pattern.compile("올해|금년|이번\\s*(해|년도|입시|수시|정시)|2027");

    /**
     * 질문이 한 학년도만 가리키면(예: "작년 경쟁률", "올해 경쟁률") 다른 학년도 단위를 뺀다. 남는 단위가 없으면 그대로 둔다
     * (예: 올해 자료가 없는 대학에 "올해 경쟁률"을 물으면 지난해 결과를 지난해 표시와 함께 보여 준다).
     */
    static List<List<AdmissionsPriorResult>> byYearIntent(String question, List<List<AdmissionsPriorResult>> units) {
        boolean last = ASKS_LAST_YEAR.matcher(question).find();
        boolean current = ASKS_THIS_YEAR.matcher(question).find();
        if (last == current) return units;
        List<List<AdmissionsPriorResult>> kept = units.stream().filter(rows -> isCurrentYear(rows.get(0)) == current).toList();
        return kept.isEmpty() ? units : new ArrayList<>(kept);
    }

    /** 결과 학년도가 상담 학년도와 같은 행(올해 원서접수 최종 경쟁률)의 근거 제목 표시. 채팅 서비스가 이 표시로 지난해 결과와 구분한다. */
    static final String CURRENT_YEAR_TAG = "(올해 원서접수)";

    /** 결과 학년도가 상담 학년도와 같은가 — 예: 2027 상담 자료로 들어온 2027학년도 수시 최종 경쟁률. */
    static boolean isCurrentYear(AdmissionsPriorResult r) {
        return r.getResultYear() != null && r.getResultYear().equals(r.getAcademicYear());
    }

    /** 근거 내용 맨 앞에 붙이는 원문 산출 단서 머리말. 상담 답변이 이 단서를 빠뜨리면 서버가 덧붙인다. */
    static final String CAVEAT_PREFIX = "산출 기준(원문): ";

    /**
     * 출처 문서 제목의 괄호 안에 '반영방법'·'모집단위 기준'이 있으면 그 문장을 원문 그대로 돌려준다.
     * 예: 2026 결과를 2027학년도 반영방법으로 다시 산출한 표(한남대·대구가톨릭대). 없으면 null.
     */
    static String priorCaveat(String sourceTitle) {
        if (sourceTitle == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^()]*(?:반영방법|모집단위 기준)[^()]*)\\)").matcher(sourceTitle);
        return m.find() ? m.group(1).trim() : null;
    }

    static String withCaveat(String caveat, String excerpt) {
        return caveat == null ? excerpt : CAVEAT_PREFIX + caveat + "\n" + excerpt;
    }

    static String roundLabel(String roundType) {
        if ("JEONGSI".equals(roundType)) return "정시";
        if ("SUSI".equals(roundType)) return "수시";
        if ("OTHER".equals(roundType)) return "수시·정시 구분 없음(학교 자체 입시)";
        return roundType;
    }

    /**
     * 한 전형·모집단위의 기본 행(모집·지원·경쟁률·충원)과 지표 행을 한 줄로. 지표명은 원문 그대로 쓰고 값은 원문 문자열을
     * 우선한다. 서로 다른 지표를 합치거나 환산하지 않는다. 원문에 없는 값은 쓰지 않는다.
     */
    static String formatPriorUnit(List<AdmissionsPriorResult> rows) {
        StringBuilder sb = new StringBuilder();
        AdmissionsPriorResult base = rows.stream().filter(AdmissionsPriorResult::isBaseRow).findFirst().orElse(null);
        List<String> basics = new ArrayList<>();
        if (base != null) {
            if (base.getRecruitCount() != null) basics.add("모집인원 " + base.getRecruitCount() + "명");
            if (base.getApplicantCount() != null) basics.add("지원자 " + base.getApplicantCount() + "명");
            if (base.getCompetitionRate() != null) basics.add("경쟁률 " + base.getCompetitionRate().stripTrailingZeros().toPlainString() + ":1");
            if (base.getExtraAdmittedCount() != null) basics.add("충원(추가합격) " + base.getExtraAdmittedCount() + "명");
        }
        sb.append(basics.isEmpty() ? "모집·지원·경쟁률 자료 없음" : String.join(", ", basics));
        List<String> metrics = new ArrayList<>();
        for (AdmissionsPriorResult r : rows) {
            if (r.isBaseRow()) continue;
            String value = r.getRawValueText() != null && !r.getRawValueText().isBlank() ? r.getRawValueText()
                    : r.getMetricValue() == null ? "값 없음" : r.getMetricValue().stripTrailingZeros().toPlainString();
            metrics.add(r.getMetricLabel() + " = " + value);
        }
        if (!metrics.isEmpty()) sb.append("\n성적 지표(대학 원문 표기 그대로, 지표끼리 합치거나 환산하지 않음): ").append(String.join("; ", metrics));
        return sb.toString();
    }

    // ── 모집단위(학과) ───────────────────────────────────────────────

    private List<Evidence> searchDepartments(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        List<AdmissionsDepartment> candidates = new ArrayList<>();
        for (AdmissionsDepartment d : departmentRepository.findAll()) {
            if (d.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(d.getAcademicYear())) continue;
            if (!approvedSource(d.getSource(), d.getAcademicYear())) continue;
            if (!trackAllowed(question, d.getSource())) continue;
            if (!campusAllowed(question, d.getSource(), d.getName())) continue;
            if (!univSelected(p, d.getUniversity())) continue;
            candidates.add(d);
        }
        // 1차: 학과명(접미 제거 포함) 매칭. 하나도 없으면 2차: 핵심어 부분 일치(예: "반도체").
        List<AdmissionsDepartment> matched = candidates.stream()
                .filter(d -> AdmissionsQueryParser.mentionsDepartment(question, d.getName())).toList();
        if (matched.isEmpty()) {
            matched = candidates.stream()
                    .filter(d -> AdmissionsQueryParser.mentionsDepartmentByKeyword(question, d.getName())).toList();
        }
        List<Evidence> out = new ArrayList<>();
        for (AdmissionsDepartment d : matched) {
            out.add(evidence("dept", d.getDepartmentId(), d.getAcademicYear(),
                    d.getUniversity().getName() + " " + d.getAcademicYear() + "학년도 " + d.getName() + " 모집인원",
                    "전형별 모집인원: " + formatQuota(d.getQuotaInfo()), d.getSource()));
            if (out.size() >= MAX_DEPARTMENTS) break;
        }
        return out;
    }

    // ── 전형 ─────────────────────────────────────────────────────────

    private List<Evidence> searchTypes(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        if (p.universities().isEmpty()) return List.of();
        List<AdmissionsType> candidates = new ArrayList<>();
        for (AdmissionsType t : typeRepository.findAll()) {
            if (t.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(t.getAcademicYear())) continue;
            if (!approvedSource(t.getSource(), t.getAcademicYear())) continue;
            if (!trackAllowed(question, t.getSource())) continue;
            if (!campusAllowed(question, t.getSource(), t.getName())) continue;
            if (!univSelected(p, t.getUniversity())) continue;
            candidates.add(t);
        }
        List<AdmissionsType> matched = candidates.stream()
                .filter(t -> AdmissionsQueryParser.typePieces(t.getName()).stream().anyMatch(question::contains))
                .toList();
        List<AdmissionsType> chosen = mainCampusFirst(question,
                // 합격 가능성 질문은 판단 대신 참고할 전형 정보(모집인원·전형방법·수능최저)를 근거로 쓴다.
                !matched.isEmpty() ? matched : (p.wantsType() || p.asksAdmissionChance() ? candidates : List.of()),
                t -> (t.getSource() == null ? "" : t.getSource().getTitle()) + " " + t.getName());
        List<Evidence> out = new ArrayList<>();
        for (AdmissionsType t : chosen) {
            out.add(evidence("type", t.getAdmissionTypeId(), t.getAcademicYear(),
                    t.getUniversity().getName() + " " + t.getAcademicYear() + "학년도 " + t.getName(),
                    detailsFor(question, cleanDetails(t.getDetails())), t.getSource()));
            if (out.size() >= MAX_TYPES) break;
        }
        return out;
    }

    // ── 일정 ─────────────────────────────────────────────────────────

    private List<Evidence> searchSchedules(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        if (p.universities().isEmpty() || !p.wantsSchedule()) return List.of();
        // "OO대 정시 일정 알려줘"처럼 구체 항목(접수·발표 등) 없이 일정만 물으면 전체 일정 질문과 같은 규칙으로
        // 원서접수 행을 근거로 쓰고, 수시/정시 한쪽만 말했으면 반대쪽 일정은 뺀다.
        boolean genericQuestion = AdmissionsQueryParser.SCHEDULE_TERMS.stream().noneMatch(question::contains);
        boolean asksSusi = question.contains("수시");
        boolean asksJeongsi = question.contains("정시");
        List<AdmissionsSchedule> rows = new ArrayList<>();
        for (AdmissionsSchedule s : scheduleRepository.findAll()) {
            if (s.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(s.getAcademicYear())) continue;
            if (!approvedSource(s.getSource(), s.getAcademicYear())) continue;
            if (!trackAllowed(question, s.getSource())) continue;
            if (!campusAllowed(question, s.getSource(), s.getTitle())) continue;
            if (s.getUniversity() == null || !univSelected(p, s.getUniversity())) continue;
            String title = s.getTitle();
            if (genericQuestion) {
                if (!(title.contains("접수") || title.contains("원서"))) continue;
                if (asksSusi && !asksJeongsi && title.contains("정시")) continue;
                if (asksJeongsi && !asksSusi && !title.contains("정시")) continue;
            } else if (!scheduleMatches(question, title)) continue;
            rows.add(s);
        }
        List<Evidence> out = new ArrayList<>();
        for (AdmissionsSchedule s : mainCampusFirst(question, rows,
                x -> (x.getSource() == null ? "" : x.getSource().getTitle()) + " " + x.getTitle())) {
            out.add(evidence("sched", s.getScheduleId(), s.getAcademicYear(),
                    s.getUniversity().getName() + " " + s.getAcademicYear() + "학년도 " + s.getTitle(),
                    "일정 원문: " + (s.getNote() == null || s.getNote().isBlank() ? "확인되지 않음" : s.getNote()),
                    s.getSource()));
            if (out.size() >= MAX_SCHEDULES) break;
        }
        return out;
    }

    /** 서울(본교)이 아닌 캠퍼스 표기(세종·글로벌·다빈치·ERICA·WISE 등)가 있으면 true. */
    static boolean otherCampusMarked(String text) {
        return campusMarker(text) != null;
    }

    /** 글에 들어 있는 다른 캠퍼스 표시(예: "ERICA", "세종캠퍼스"). 없으면 null(서울·본교). */
    static String campusMarker(String text) {
        if (text == null) return null;
        String t = text.replace(" ", "");
        for (String marker : OTHER_CAMPUS_MARKERS) {
            if (t.contains(marker)) return marker;
        }
        return null;
    }

    private static final List<String> OTHER_CAMPUS_MARKERS = List.of(
            "세종캠퍼스", "글로벌캠퍼스", "다빈치캠퍼스", "안성캠퍼스", "ERICA", "WISE캠퍼스", "경주캠퍼스",
            "글로컬캠퍼스", "충주캠퍼스", "국제캠퍼스", "미래캠퍼스", "원주캠퍼스", "천안캠퍼스", "자연캠퍼스", "용인캠퍼스");

    /**
     * 질문이 캠퍼스를 말하지 않았으면 서울(본교) 행을 앞에 둔다(다른 캠퍼스 행은 뒤로, 순서는 유지).
     * 근거 상한(MAX_*) 때문에 다른 캠퍼스 일정이 본교 일정을 밀어내던 문제를 막는다.
     */
    static <T> List<T> mainCampusFirst(String question, List<T> rows, java.util.function.Function<T, String> label) {
        if (campusOf(question) != null || rows.size() < 2) return rows;
        List<T> main = new ArrayList<>();
        List<T> other = new ArrayList<>();
        for (T r : rows) (otherCampusMarked(label.apply(r)) ? other : main).add(r);
        main.addAll(other);
        return main;
    }

    /**
     * 대학명 없이 "수시 원서접수 일정 알려줘"처럼 전체 일정을 물은 경우: 승인된 일정 행을 대학별로 모은다.
     * 자료가 없는 대학이 있어도 있는 대학의 근거는 그대로 쓴다(전체 질문을 포기하지 않는다).
     */
    private List<Evidence> searchAllUniversitySchedules(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        boolean genericQuestion = AdmissionsQueryParser.SCHEDULE_TERMS.stream().noneMatch(question::contains);
        boolean asksSusi = question.contains("수시");
        boolean asksJeongsi = question.contains("정시");
        java.util.Map<Long, List<Evidence>> byUniversity = new java.util.LinkedHashMap<>();
        for (AdmissionsSchedule s : scheduleRepository.findAll()) {
            if (s.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(s.getAcademicYear())) continue;
            if (!approvedSource(s.getSource(), s.getAcademicYear())) continue;
            if (s.getUniversity() == null) continue;
            if (!campusAllowed(question, s.getSource(), s.getTitle())) continue;
            String title = s.getTitle();
            boolean relevant = genericQuestion ? (title.contains("접수") || title.contains("원서")) : scheduleMatches(question, title);
            if (!relevant) continue;
            if (asksSusi && !asksJeongsi && title.contains("정시")) continue;
            if (asksJeongsi && !asksSusi && title.contains("수시")) continue;
            List<Evidence> rows = byUniversity.computeIfAbsent(s.getUniversity().getUniversityId(), k -> new ArrayList<>());
            if (rows.size() >= MAX_SCHEDULES_PER_UNIVERSITY) continue;
            rows.add(evidence("sched", s.getScheduleId(), s.getAcademicYear(),
                    s.getUniversity().getName() + " " + s.getAcademicYear() + "학년도 " + title,
                    "일정 원문: " + (s.getNote() == null || s.getNote().isBlank() ? "확인되지 않음" : s.getNote()),
                    s.getSource()));
        }
        List<Evidence> out = new ArrayList<>();
        for (List<Evidence> rows : byUniversity.values()) out.addAll(rows);
        return out.size() > MAX_ALL_UNIVERSITY_SCHEDULES ? out.subList(0, MAX_ALL_UNIVERSITY_SCHEDULES) : out;
    }

    /** 근거 제목이 "{대학명} {학년도}학년도 ..." 형태이므로 대학명 접두로 대학별 현황을 계산한다. */
    static ScheduleCoverage coverageOf(List<Evidence> schedules, List<String> universityNames) {
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : universityNames) {
            boolean has = schedules.stream().anyMatch(e -> e.getTitle() != null && e.getTitle().startsWith(name + " "));
            (has ? covered : missing).add(name);
        }
        return new ScheduleCoverage(covered, missing);
    }

    static boolean scheduleMatches(String question, String title) {
        for (String term : AdmissionsQueryParser.SCHEDULE_TERMS) {
            if (question.contains(term) && title.contains(term)) return true;
        }
        return AdmissionsQueryParser.typePieces(title).stream()
                .filter(piece -> piece.length() >= 3)
                .anyMatch(question::contains);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static boolean univSelected(AdmissionsQueryParser.Parsed p, AdmissionsUniversity u) {
        return p.universities().isEmpty() || (u != null && p.universities().contains(u.getName()));
    }

    /**
     * 질문이 수시 또는 정시 한쪽만 말하면, 출처 자료 제목이 반대쪽(예: 정시모집요강)인 행은 제외한다.
     * 중앙대 수시처럼 수시 내용 행이 없는 대학에 정시 행이 수시 답변처럼 섞이는 것을 막는다.
     * 둘 다 말했거나 둘 다 없거나, 출처 제목에 수시·정시 표기가 없으면 거르지 않는다.
     */
    static boolean trackAllowed(String question, AdmissionsOfficialMaterial source) {
        boolean susi = question.contains("수시");
        boolean jeongsi = question.contains("정시");
        if (susi == jeongsi || source == null || source.getTitle() == null) return true;
        String title = source.getTitle();
        boolean titleSusi = title.contains("수시");
        boolean titleJeongsi = title.contains("정시");
        if (titleSusi == titleJeongsi) return true;
        return susi ? titleSusi : titleJeongsi;
    }

    /** 텍스트에 표기된 캠퍼스("서울캠퍼스"/"세종캠퍼스", 공백 무시). 표기가 없으면 null. */
    static String campusOf(String text) {
        if (text == null) return null;
        String t = text.replace(" ", "");
        if (t.contains("세종캠퍼스")) return "세종";
        if (t.contains("서울캠퍼스")) return "서울";
        return null;
    }

    /**
     * 질문이 캠퍼스를 말했을 때 다른 캠퍼스로 표기된 근거를 제외한다(KI-1: 서울캠퍼스 질문에 세종캠퍼스 자료가 섞이던 문제).
     * 질문이 서울캠퍼스를 말하면 세종 표기 행만 제외하고, 세종캠퍼스를 말하면 세종 표기 행만 남긴다.
     * 질문에 캠퍼스가 없으면 거르지 않는다(근거 제목의 캠퍼스 표기는 그대로 노출된다).
     */
    static boolean campusAllowed(String question, AdmissionsOfficialMaterial source, String rowName) {
        String asked = campusOf(question);
        if (asked == null) return true;
        String rowCampus = campusOf((source == null ? "" : source.getTitle()) + " " + (rowName == null ? "" : rowName));
        if (rowCampus == null) return "서울".equals(asked);
        return asked.equals(rowCampus);
    }

    private static boolean approvedSource(AdmissionsOfficialMaterial source, Integer year) {
        return source != null && source.getStatus()==KnowledgeStatus.ACTIVE
                && java.util.Objects.equals(source.getAcademicYear(),year);
    }

    private static Evidence evidence(String kind, Long id, Integer year, String title, String excerpt,
                                     AdmissionsOfficialMaterial source) {
        boolean confirmed = year != null && year == 2027;
        String sourceTitle = source == null ? "출처 자료 연결 없음" : source.getTitle();
        return Evidence.builder()
                .stepNo(STEP_FACT)
                .sourceClass(confirmed ? SourceClass.OFFICIAL_CONFIRMED : SourceClass.OFFICIAL_PLANNED)
                .refId(kind + ":" + id)
                .refUrl(source == null ? null : source.getDocumentUrl())
                .title(title)
                .excerpt(excerpt)
                .note("공식자료 DB(" + kind + ") / 출처: " + sourceTitle)
                .academicYear(year)
                .yearConfirmed(confirmed)
                .build();
    }

    /** {"학교추천":19,"논술":0,"합계":81} → "학교추천 19명, 합계 81명" (0명은 생략). */
    static String formatQuota(String quotaJson) {
        if (quotaJson == null || quotaJson.isBlank()) return "확인되지 않음";
        try {
            JsonNode n = MAPPER.readTree(quotaJson);
            List<String> parts = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isNumber() && e.getValue().asInt() > 0) {
                    parts.add(e.getKey() + " " + e.getValue().asInt() + "명");
                }
            }
            return parts.isEmpty() ? "확인되지 않음" : String.join(", ", parts);
        } catch (Exception e) {
            return "확인되지 않음";
        }
    }

    /**
     * 질문이 제출서류를 묻는 경우(서류·제출) 증거 본문 길이 제한에 잘리지 않도록 제출서류 줄을 맨 앞에 둔다.
     * 줄 내용은 바꾸지 않고 순서만 바꾼다.
     */
    static String detailsFor(String question, String details) {
        if (question == null || details == null || !(question.contains("서류") || question.contains("제출"))) {
            return details;
        }
        StringBuilder first = new StringBuilder();
        StringBuilder rest = new StringBuilder();
        for (String line : details.split("\n")) {
            (line.startsWith("제출서류") ? first : rest).append(line).append("\n");
        }
        return (first.toString() + rest).trim();
    }

    /** 비어 있는 항목("수능최저: ")은 "확인되지 않음"으로 바꿔 LLM이 추정하지 않게 한다. */
    static String cleanDetails(String details) {
        if (details == null || details.isBlank()) return "확인되지 않음";
        StringBuilder sb = new StringBuilder();
        for (String line : details.split("\n")) {
            String trimmed = line.stripTrailing();
            if (trimmed.endsWith(":") || trimmed.endsWith(": ")) {
                sb.append(trimmed.stripTrailing()).append(" 확인되지 않음(추정 금지)\n");
            } else {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
