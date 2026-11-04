package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsJeongsiFormula;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsJeongsiFormulaRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 정시 수능 환산점수 계산. 승인(ACTIVE)된 대학 계산식에 학생 성적을 넣어 대학 기준 환산점수를 낸다.
 * 합격 판정은 하지 않는다. 확인 필요(needsReviewReason) 계산식은 점수를 내지 않고 사유만 돌려준다.
 * 전국 최고 표준점수·대학 변환표준점수처럼 수능 성적 발표 뒤 정해지는 값이 없으면 점수 대신 필요한 값을 알려 준다.
 */
@Service
@RequiredArgsConstructor
public class AdmissionsJeongsiScoreService {

    private final AdmissionsJeongsiFormulaRepository formulas;
    private final AdmissionsUniversityRepository universities;
    private final ObjectMapper objectMapper;

    public enum Outcome { CALCULATED, NEEDS_INPUT, WITHHELD_NEEDS_REVIEW, ERROR }

    public record FormulaScore(String formulaKey, String label, List<String> typeNames, List<String> departmentNames,
                               String unitScopeText, BigDecimal csatPoints, Outcome outcome, BigDecimal score,
                               List<String> missingInputs, List<String> pendingInputs, List<String> defaultedInputs,
                               String needsReviewReason, String roundingText, String eligibilityText, String notInCsatScoreText,
                               String evidencePages, String sourceUrl, String sourceTitle, List<String> uncertain, String error) {}

    public record ScoreResult(String universityName, int academicYear, List<FormulaScore> formulas, String notice) {}

    public static class InvalidScoreRequestException extends IllegalArgumentException {
        public InvalidScoreRequestException(String message) { super(message); }
    }

    /** 학생이 넣는 값(선택 표시 0/1 포함). 표시 값은 넣지 않으면 0으로 계산하고 defaultedInputs로 알린다. */
    static final Set<String> FLAG_INPUTS = Set.of("KOR_SEL_HW", "KOR_SEL_EM", "MATH_SEL_PROB", "MATH_SEL_CALC", "MATH_SEL_GEOM",
            "FL_TAKEN", "INQ1_SOC", "INQ1_SCI", "INQ1_VOC", "INQ1_SCI2", "INQ2_SOC", "INQ2_SCI", "INQ2_VOC", "INQ2_SCI2");

    /** 수능 성적 발표(평가원 최고점) 또는 대학 변환표준점수표 발표 뒤 정해지는 값. */
    static final Set<String> PENDING_INPUTS = Set.of("KOR_STD_MAX", "MATH_STD_MAX", "INQ1_STD_MAX", "INQ2_STD_MAX",
            "INQ1_CONV", "INQ2_CONV", "INQ1_CONV_MAX", "INQ2_CONV_MAX");

    static final Set<String> KNOWN_INPUTS;
    static {
        Set<String> s = new java.util.HashSet<>(FLAG_INPUTS);
        for (String a : List.of("KOR", "MATH", "INQ1", "INQ2")) {
            s.add(a + "_STD"); s.add(a + "_PCT"); s.add(a + "_GRD"); s.add(a + "_STD_MAX");
        }
        s.addAll(List.of("ENG_GRD", "HIST_GRD", "FL_GRD", "INQ1_CONV", "INQ2_CONV", "INQ1_CONV_MAX", "INQ2_CONV_MAX"));
        KNOWN_INPUTS = Set.copyOf(s);
    }

    /** 입력 이름·범위 검사. 표준점수·변환표준점수 0~200, 백분위 0~100, 등급 1~9, 선택 표시 0/1. */
    static Map<String, Double> validateInputs(Map<String, ? extends Number> raw) {
        Map<String, Double> out = new HashMap<>();
        if (raw == null) return out;
        for (var e : raw.entrySet()) {
            String k = e.getKey();
            if (!KNOWN_INPUTS.contains(k)) throw new InvalidScoreRequestException("알 수 없는 입력 항목: " + k);
            if (e.getValue() == null) continue;
            double v = e.getValue().doubleValue();
            boolean ok;
            if (FLAG_INPUTS.contains(k)) ok = v == 0 || v == 1;
            else if (k.endsWith("_GRD")) ok = v >= 1 && v <= 9 && v == Math.rint(v);
            else if (k.endsWith("_PCT")) ok = v >= 0 && v <= 100;
            else ok = v >= 0 && v <= 200;
            if (!ok) throw new InvalidScoreRequestException("입력 범위를 벗어남: " + k + "=" + e.getValue());
            out.put(k, v);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ScoreResult calculate(String universityName, int academicYear, String typeName, String departmentName,
                                 Map<String, ? extends Number> rawInputs) {
        if (universityName == null || universityName.isBlank()) throw new InvalidScoreRequestException("대학 이름이 필요합니다.");
        Map<String, Double> inputs = validateInputs(rawInputs);
        AdmissionsUniversity univ = resolveUniversity(universityName.strip());
        if (univ == null) throw new InvalidScoreRequestException("등록된 대학을 찾지 못했습니다: " + universityName);
        List<FormulaScore> out = new ArrayList<>();
        for (AdmissionsJeongsiFormula f : formulas.findByUniversityUniversityIdAndAcademicYearAndStatus(
                univ.getUniversityId(), academicYear, KnowledgeStatus.ACTIVE)) {
            List<String> types = readList(f.getTypeNames());
            List<String> depts = readList(f.getDepartmentNames());
            if (typeName != null && !typeName.isBlank() && !types.contains(typeName.strip())) continue;
            if (departmentName != null && !departmentName.isBlank() && !depts.isEmpty() && !depts.contains(departmentName.strip())) continue;
            out.add(score(f, types, depts, inputs));
        }
        String notice = out.isEmpty()
                ? "이 대학·전형·모집단위에 등록된 2027 정시 수능 환산 계산식이 없습니다."
                : "대학 정시 모집요강의 수능 반영 방법으로 계산한 수능 환산점수입니다. 합격 여부나 가능성을 판단하지 않습니다.";
        return new ScoreResult(univ.getName(), academicYear, out, notice);
    }

    private FormulaScore score(AdmissionsJeongsiFormula f, List<String> types, List<String> depts, Map<String, Double> inputs) {
        String sourceTitle = f.getSource() == null ? null : f.getSource().getTitle();
        List<String> uncertain = readList(f.getUncertainJson());
        Set<String> vars;
        try {
            vars = JeongsiFormulaEvaluator.variables(f.getExpression());
        } catch (JeongsiFormulaEvaluator.FormulaException e) {
            return result(f, types, depts, Outcome.ERROR, null, List.of(), List.of(), List.of(), sourceTitle, uncertain, "계산식을 해석할 수 없습니다.");
        }
        if (f.getNeedsReviewReason() != null && !f.getNeedsReviewReason().isBlank()) {
            return result(f, types, depts, Outcome.WITHHELD_NEEDS_REVIEW, null, List.of(), List.of(), List.of(), sourceTitle, uncertain, null);
        }
        Map<String, Double> in = new HashMap<>(inputs);
        Set<String> defaulted = new TreeSet<>();
        Set<String> missing = new TreeSet<>();
        Set<String> pending = new TreeSet<>();
        for (String v : vars) {
            if (in.containsKey(v)) continue;
            if (FLAG_INPUTS.contains(v)) { in.put(v, 0.0); defaulted.add(v); }
            else if (PENDING_INPUTS.contains(v)) pending.add(v);
            else missing.add(v);
        }
        if (!missing.isEmpty() || !pending.isEmpty()) {
            return result(f, types, depts, Outcome.NEEDS_INPUT, null, List.copyOf(missing), List.copyOf(pending),
                    List.copyOf(defaulted), sourceTitle, uncertain, null);
        }
        try {
            double v = JeongsiFormulaEvaluator.evaluate(f.getExpression(), in, readTables(f.getTablesJson()));
            BigDecimal score = new BigDecimal(Double.toString(v)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
            return result(f, types, depts, Outcome.CALCULATED, score, List.of(), List.of(), List.copyOf(defaulted), sourceTitle, uncertain, null);
        } catch (JeongsiFormulaEvaluator.FormulaException e) {
            return result(f, types, depts, Outcome.ERROR, null, List.of(), List.of(), List.copyOf(defaulted), sourceTitle, uncertain, e.getMessage());
        }
    }

    private static FormulaScore result(AdmissionsJeongsiFormula f, List<String> types, List<String> depts, Outcome outcome,
                                       BigDecimal score, List<String> missing, List<String> pending, List<String> defaulted,
                                       String sourceTitle, List<String> uncertain, String error) {
        return new FormulaScore(f.getFormulaKey(), f.getLabel(), types, depts, f.getUnitScopeText(), f.getCsatPoints(), outcome, score,
                missing, pending, defaulted, f.getNeedsReviewReason(), f.getRoundingText(), f.getEligibilityText(),
                f.getNotInCsatScoreText(), f.getEvidencePages(), f.getSourceUrl(), sourceTitle, uncertain, error);
    }

    /** 정식명 그대로, 아니면 줄임말("연세대", "공주대")이 같은 대학. 여러 곳이면 계산식이 있는 대학만 남긴다. */
    AdmissionsUniversity resolveUniversity(String name) {
        var exact = universities.findByName(name);
        if (exact.isPresent()) return exact.get();
        List<AdmissionsUniversity> matches = new ArrayList<>();
        for (AdmissionsUniversity u : universities.findAll()) {
            if (AdmissionsQueryParser.aliasesOf(u.getName()).contains(name)) matches.add(u);
        }
        if (matches.size() <= 1) return matches.isEmpty() ? null : matches.get(0);
        List<AdmissionsUniversity> withFormulas = matches.stream()
                .filter(u -> !formulas.findByUniversityUniversityIdIn(List.of(u.getUniversityId())).isEmpty()).toList();
        if (withFormulas.size() == 1) return withFormulas.get(0);
        throw new InvalidScoreRequestException("같은 이름의 대학이 여러 곳입니다. 정식 이름(캠퍼스 포함)으로 지정해 주세요: "
                + matches.stream().map(AdmissionsUniversity::getName).toList());
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Map<String, ? extends Number>> readTables(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Map<String, Double>> t = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Map<String, Double>>>() {});
            return new HashMap<>(t);
        } catch (Exception e) {
            throw new JeongsiFormulaEvaluator.FormulaException("등급표를 읽을 수 없습니다.");
        }
    }
}
