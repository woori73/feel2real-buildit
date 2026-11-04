package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSusiMinimum;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsSusiMinimumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 수시 수능최저학력기준 충족 확인. 승인(ACTIVE)된 대학 규칙에 학생 등급을 넣어 충족·미충족을 알려 준다.
 * 합격 여부는 판단하지 않는다. 확인 필요 규칙은 판정하지 않고 사유만 돌려준다.
 * 모집단위를 주면, 그 모집단위를 지정한 규칙이 있는 전형에서는 전형 전체 규칙을 쓰지 않는다(예: "전체(의예과 제외)" / "의예과").
 */
@Service
@RequiredArgsConstructor
public class AdmissionsSusiMinimumService {

    private final AdmissionsSusiMinimumRepository minimums;
    private final AdmissionsJeongsiScoreService jeongsiScoreService;
    private final ObjectMapper objectMapper;

    public enum Outcome { MET, NOT_MET, NEEDS_INPUT, NO_MINIMUM, WITHHELD_NEEDS_REVIEW, ERROR }

    public record RuleResult(String ruleKey, String label, List<String> typeNames, List<String> departmentNames,
                             String unitScopeText, Outcome outcome, List<String> missingInputs, List<String> defaultedInputs,
                             String requiredAreasText, String rawText, String evidencePages, String sourceUrl, String sourceTitle,
                             List<String> uncertain, String needsReviewReason) {}

    public record CheckResult(String universityName, int academicYear, List<RuleResult> rules, String notice) {}

    /** 등급(1~9 정수)과 선택 표시(0/1)만 받는다. 점수 항목은 쓰지 않는다. */
    static Map<String, Double> validateGrades(Map<String, ? extends Number> raw) {
        if (raw != null) {
            for (String k : raw.keySet()) {
                boolean allowed = k.endsWith("_GRD") || AdmissionsJeongsiScoreService.FLAG_INPUTS.contains(k);
                if (!allowed || !AdmissionsJeongsiScoreService.KNOWN_INPUTS.contains(k)) {
                    throw new AdmissionsJeongsiScoreService.InvalidScoreRequestException("수능최저 확인에 쓰지 않는 입력 항목: " + k);
                }
            }
        }
        return AdmissionsJeongsiScoreService.validateInputs(raw);
    }

    @Transactional(readOnly = true)
    public CheckResult check(String universityName, int academicYear, String typeName, String departmentName,
                             Map<String, ? extends Number> rawGrades) {
        if (universityName == null || universityName.isBlank()) {
            throw new AdmissionsJeongsiScoreService.InvalidScoreRequestException("대학 이름이 필요합니다.");
        }
        Map<String, Double> grades = validateGrades(rawGrades);
        AdmissionsUniversity univ = jeongsiScoreService.resolveUniversity(universityName.strip());
        if (univ == null) throw new AdmissionsJeongsiScoreService.InvalidScoreRequestException("등록된 대학을 찾지 못했습니다: " + universityName);
        String type = typeName == null || typeName.isBlank() ? null : typeName.strip();
        String dept = departmentName == null || departmentName.isBlank() ? null : departmentName.strip();

        List<AdmissionsSusiMinimum> rows = new ArrayList<>();
        for (AdmissionsSusiMinimum m : minimums.findByUniversityUniversityIdAndAcademicYearAndStatus(
                univ.getUniversityId(), academicYear, KnowledgeStatus.ACTIVE)) {
            if (type != null && !readList(m.getTypeNames()).contains(type)) continue;
            rows.add(m);
        }
        // 모집단위 지정 규칙이 그 모집단위를 가리키는 전형: 전형 전체 규칙을 뺀다. 다른 모집단위 지정 규칙도 뺀다.
        Set<String> typesWithDeptRule = new HashSet<>();
        if (dept != null) {
            for (AdmissionsSusiMinimum m : rows) {
                if (readList(m.getDepartmentNames()).contains(dept)) typesWithDeptRule.addAll(readList(m.getTypeNames()));
            }
        }
        List<RuleResult> out = new ArrayList<>();
        for (AdmissionsSusiMinimum m : rows) {
            List<String> depts = readList(m.getDepartmentNames());
            List<String> types = readList(m.getTypeNames());
            if (dept != null) {
                if (!depts.isEmpty() && !depts.contains(dept)) continue;
                if (depts.isEmpty() && types.stream().anyMatch(typesWithDeptRule::contains)) continue;
            }
            out.add(judge(m, types, depts, grades));
        }
        String notice = out.isEmpty()
                ? "이 대학·전형에 등록된 2027 수시 수능최저학력기준 자료가 없습니다. 대학 모집요강을 확인해 주세요."
                : "대학 수시 모집요강의 수능최저학력기준으로 확인한 결과입니다. 가채점 등급이면 실제 성적과 다를 수 있습니다. 합격 여부를 판단하지 않습니다.";
        return new CheckResult(univ.getName(), academicYear, out, notice);
    }

    private RuleResult judge(AdmissionsSusiMinimum m, List<String> types, List<String> depts, Map<String, Double> grades) {
        String sourceTitle = m.getSource() == null ? null : m.getSource().getTitle();
        List<String> uncertain = readList(m.getUncertainJson());
        if (m.getRuleKind() == AdmissionsSusiMinimum.RuleKind.NO_MINIMUM) {
            return result(m, types, depts, Outcome.NO_MINIMUM, List.of(), List.of(), sourceTitle, uncertain);
        }
        if (m.getNeedsReviewReason() != null && !m.getNeedsReviewReason().isBlank()) {
            return result(m, types, depts, Outcome.WITHHELD_NEEDS_REVIEW, List.of(), List.of(), sourceTitle, uncertain);
        }
        try {
            Set<String> vars = JeongsiFormulaEvaluator.variables(m.getConditionExpr());
            Map<String, Double> in = new HashMap<>(grades);
            Set<String> missing = new TreeSet<>();
            Set<String> defaulted = new TreeSet<>();
            for (String v : vars) {
                if (in.containsKey(v)) continue;
                if (AdmissionsJeongsiScoreService.FLAG_INPUTS.contains(v)) { in.put(v, 0.0); defaulted.add(v); }
                else missing.add(v);
            }
            if (!missing.isEmpty()) {
                return result(m, types, depts, Outcome.NEEDS_INPUT, List.copyOf(missing), List.copyOf(defaulted), sourceTitle, uncertain);
            }
            double v = JeongsiFormulaEvaluator.evaluate(m.getConditionExpr(), in, Map.of());
            return result(m, types, depts, v != 0 ? Outcome.MET : Outcome.NOT_MET, List.of(), List.copyOf(defaulted), sourceTitle, uncertain);
        } catch (JeongsiFormulaEvaluator.FormulaException e) {
            return result(m, types, depts, Outcome.ERROR, List.of(), List.of(), sourceTitle, uncertain);
        }
    }

    private static RuleResult result(AdmissionsSusiMinimum m, List<String> types, List<String> depts, Outcome outcome,
                                     List<String> missing, List<String> defaulted, String sourceTitle, List<String> uncertain) {
        return new RuleResult(m.getRuleKey(), m.getLabel(), types, depts, m.getUnitScopeText(), outcome, missing, defaulted,
                m.getRequiredAreasText(), m.getRawText(), m.getEvidencePages(), m.getSourceUrl(), sourceTitle, uncertain,
                m.getNeedsReviewReason());
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
