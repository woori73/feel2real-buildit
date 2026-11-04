package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsJeongsiFormula;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsConversionScore;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCsatStdMax;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsConversionScoreRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCsatStdMaxRepository;
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
 * 전국 최고 표준점수·대학 변환표준점수처럼 수능 성적 발표 뒤 정해지는 값은 승인된 발표 자료(평가원 채점 결과,
 * 대학 변환표준점수표)에서 채우고(derivedInputs에 출처와 함께 알림), 자료가 아직 없으면 점수 대신 필요한 값을 알려 준다.
 */
@Service
@RequiredArgsConstructor
public class AdmissionsJeongsiScoreService {

    private final AdmissionsJeongsiFormulaRepository formulas;
    private final AdmissionsUniversityRepository universities;
    private final AdmissionsCsatStdMaxRepository csatStdMax;
    private final AdmissionsConversionScoreRepository conversionScores;
    private final ObjectMapper objectMapper;

    public enum Outcome { CALCULATED, NEEDS_INPUT, WITHHELD_NEEDS_REVIEW, ERROR }

    public record FormulaScore(String formulaKey, String label, List<String> typeNames, List<String> departmentNames,
                               String unitScopeText, BigDecimal csatPoints, Outcome outcome, BigDecimal score,
                               List<String> missingInputs, List<String> pendingInputs, List<String> defaultedInputs,
                               String needsReviewReason, String roundingText, String eligibilityText, String notInCsatScoreText,
                               String evidencePages, String sourceUrl, String sourceTitle, List<String> uncertain, String error,
                               List<DerivedInput> derivedInputs) {}

    /** 발표 자료에서 채운 값. basis는 어떤 값으로 찾았는지(과목명·계열·백분위). */
    public record DerivedInput(String name, BigDecimal value, String basis, String sourceTitle, String sourceUrl) {}

    /** 학생 입력이 더 필요할 때 missingInputs에 나가는 이름(점수 변수가 아닌 것). */
    static final String INQ_SUBJECT = "_SUBJECT";
    static final String INQ_TRACK = "_TRACK";

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
        return calculate(universityName, academicYear, typeName, departmentName, rawInputs, null);
    }

    /**
     * @param inquirySubjects 탐구 과목명({"INQ1": "생활과 윤리", "INQ2": "사회·문화"}). 탐구 표준점수 최고점을 찾을 때만 쓴다.
     */
    @Transactional(readOnly = true)
    public ScoreResult calculate(String universityName, int academicYear, String typeName, String departmentName,
                                 Map<String, ? extends Number> rawInputs, Map<String, String> inquirySubjects) {
        if (universityName == null || universityName.isBlank()) throw new InvalidScoreRequestException("대학 이름이 필요합니다.");
        Map<String, Double> inputs = validateInputs(rawInputs);
        if (inquirySubjects != null) {
            for (var e : inquirySubjects.entrySet()) {
                if (!Set.of("INQ1", "INQ2").contains(e.getKey())) throw new InvalidScoreRequestException("탐구 과목명은 INQ1·INQ2만 받습니다: " + e.getKey());
                if (e.getValue() != null && e.getValue().length() > 50) throw new InvalidScoreRequestException("탐구 과목명이 너무 깁니다.");
            }
        }
        AdmissionsUniversity univ = resolveUniversity(universityName.strip());
        if (univ == null) throw new InvalidScoreRequestException("등록된 대학을 찾지 못했습니다: " + universityName);
        Published published = loadPublished(univ, academicYear, inputs, inquirySubjects);
        List<FormulaScore> out = new ArrayList<>();
        for (AdmissionsJeongsiFormula f : formulas.findByUniversityUniversityIdAndAcademicYearAndStatus(
                univ.getUniversityId(), academicYear, KnowledgeStatus.ACTIVE)) {
            List<String> types = readList(f.getTypeNames());
            List<String> depts = readList(f.getDepartmentNames());
            if (typeName != null && !typeName.isBlank() && !types.contains(typeName.strip())) continue;
            if (departmentName != null && !departmentName.isBlank() && !depts.isEmpty() && !depts.contains(departmentName.strip())) continue;
            out.add(score(f, types, depts, inputs, published));
        }
        String notice = out.isEmpty()
                ? "이 대학·전형·모집단위에 등록된 2027 정시 수능 환산 계산식이 없습니다."
                : "대학 정시 모집요강의 수능 반영 방법으로 계산한 수능 환산점수입니다. 합격 여부나 가능성을 판단하지 않습니다.";
        return new ScoreResult(univ.getName(), academicYear, out, notice);
    }

    private FormulaScore score(AdmissionsJeongsiFormula f, List<String> types, List<String> depts, Map<String, Double> inputs,
                               Published published) {
        String sourceTitle = f.getSource() == null ? null : f.getSource().getTitle();
        List<String> uncertain = readList(f.getUncertainJson());
        Set<String> vars;
        try {
            vars = JeongsiFormulaEvaluator.variables(f.getExpression());
        } catch (JeongsiFormulaEvaluator.FormulaException e) {
            return result(f, types, depts, Outcome.ERROR, null, List.of(), List.of(), List.of(), sourceTitle, uncertain, "계산식을 해석할 수 없습니다.", List.of());
        }
        if (f.getNeedsReviewReason() != null && !f.getNeedsReviewReason().isBlank()) {
            return result(f, types, depts, Outcome.WITHHELD_NEEDS_REVIEW, null, List.of(), List.of(), List.of(), sourceTitle, uncertain, null, List.of());
        }
        Map<String, Double> in = new HashMap<>(inputs);
        Set<String> defaulted = new TreeSet<>();
        Set<String> missing = new TreeSet<>();
        Set<String> pending = new TreeSet<>();
        List<DerivedInput> derived = new ArrayList<>();
        for (String v : new TreeSet<>(vars)) {
            if (in.containsKey(v)) continue;
            if (FLAG_INPUTS.contains(v)) { in.put(v, 0.0); defaulted.add(v); }
            else if (PENDING_INPUTS.contains(v)) {
                Lookup l = published.lookup(v);
                if (l.value() != null) { in.put(v, l.value().value().doubleValue()); derived.add(l.value()); }
                else if (l.needsStudentInput() != null) missing.add(l.needsStudentInput());
                else pending.add(v);
            }
            else missing.add(v);
        }
        if (!missing.isEmpty() || !pending.isEmpty()) {
            return result(f, types, depts, Outcome.NEEDS_INPUT, null, List.copyOf(missing), List.copyOf(pending),
                    List.copyOf(defaulted), sourceTitle, uncertain, null, derived);
        }
        try {
            double v = JeongsiFormulaEvaluator.evaluate(f.getExpression(), in, readTables(f.getTablesJson()));
            BigDecimal score = new BigDecimal(Double.toString(v)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
            return result(f, types, depts, Outcome.CALCULATED, score, List.of(), List.of(), List.copyOf(defaulted), sourceTitle, uncertain, null, derived);
        } catch (JeongsiFormulaEvaluator.FormulaException e) {
            return result(f, types, depts, Outcome.ERROR, null, List.of(), List.of(), List.copyOf(defaulted), sourceTitle, uncertain, e.getMessage(), derived);
        }
    }

    private static FormulaScore result(AdmissionsJeongsiFormula f, List<String> types, List<String> depts, Outcome outcome,
                                       BigDecimal score, List<String> missing, List<String> pending, List<String> defaulted,
                                       String sourceTitle, List<String> uncertain, String error, List<DerivedInput> derived) {
        return new FormulaScore(f.getFormulaKey(), f.getLabel(), types, depts, f.getUnitScopeText(), f.getCsatPoints(), outcome, score,
                missing, pending, defaulted, f.getNeedsReviewReason(), f.getRoundingText(), f.getEligibilityText(),
                f.getNotInCsatScoreText(), f.getEvidencePages(), f.getSourceUrl(), sourceTitle, uncertain, error, List.copyOf(derived));
    }

    /** 발표 값 찾기 결과: 값, 또는 학생 입력이 더 필요하면 그 이름, 둘 다 없으면 자료 미발표(pending). */
    record Lookup(DerivedInput value, String needsStudentInput) {
        static final Lookup PENDING = new Lookup(null, null);
    }

    /** 이 요청에서 쓸 승인된 발표 자료(평가원 최고점, 이 대학 변환표준점수표)와 학생의 탐구 과목·계열·백분위. */
    record Published(Map<String, AdmissionsCsatStdMax> stdMax, Map<String, AdmissionsConversionScore> conversion,
                     Map<String, Double> inputs, Map<String, String> subjects, ObjectMapper mapper) {

        Lookup lookup(String variable) {
            if (variable.equals("KOR_STD_MAX") || variable.equals("MATH_STD_MAX")) {
                String area = variable.substring(0, variable.indexOf('_'));
                var row = stdMax.get(area);
                return row == null ? Lookup.PENDING : found(variable, row.getStdMax(), area.equals("KOR") ? "국어 영역" : "수학 영역", row.getSource(), row.getSourceUrl());
            }
            String inq = variable.substring(0, 4); // INQ1 / INQ2
            if (variable.endsWith("_STD_MAX")) {
                if (stdMax.values().stream().noneMatch(r -> "INQ".equals(r.getArea()))) return Lookup.PENDING;
                String subject = subjects == null ? null : subjects.get(inq);
                if (subject == null || subject.isBlank()) return new Lookup(null, inq + INQ_SUBJECT);
                var row = stdMax.get("INQ:" + normalizeSubject(subject));
                return row == null ? new Lookup(null, inq + INQ_SUBJECT)
                        : found(variable, row.getStdMax(), row.getSubjectName(), row.getSource(), row.getSourceUrl());
            }
            // INQn_CONV / INQn_CONV_MAX: 학생 탐구 계열의 대학 표
            String track = trackOf(inq);
            if (track == null) return new Lookup(null, inq + INQ_TRACK);
            var table = conversion.get(track);
            if (table == null) return Lookup.PENDING;
            String pct;
            if (variable.endsWith("_CONV_MAX")) {
                pct = "100";
            } else {
                Double p = inputs.get(inq + "_PCT");
                if (p == null || p != Math.rint(p)) return new Lookup(null, inq + "_PCT");
                pct = String.valueOf(p.intValue());
            }
            try {
                Map<String, BigDecimal> scores = mapper.readValue(table.getScoresJson(), new TypeReference<Map<String, BigDecimal>>() {});
                BigDecimal v = scores.get(pct);
                if (v == null) return Lookup.PENDING;
                return found(variable, v, table.getLabel() + " 백분위 " + pct, table.getSource(), table.getSourceUrl());
            } catch (Exception e) {
                return Lookup.PENDING;
            }
        }

        private String trackOf(String inq) {
            if (Double.valueOf(1).equals(inputs.get(inq + "_SOC"))) return "SOC";
            if (Double.valueOf(1).equals(inputs.get(inq + "_SCI")) || Double.valueOf(1).equals(inputs.get(inq + "_SCI2"))) return "SCI";
            if (Double.valueOf(1).equals(inputs.get(inq + "_VOC"))) return "VOC";
            return null;
        }

        private static Lookup found(String name, BigDecimal value, String basis,
                                    f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial src, String url) {
            BigDecimal v = value.stripTrailingZeros();
            if (v.scale() < 0) v = v.setScale(0);
            return new Lookup(new DerivedInput(name, v, basis, src == null ? null : src.getTitle(), url), null);
        }
    }

    private Published loadPublished(AdmissionsUniversity univ, int academicYear, Map<String, Double> inputs, Map<String, String> subjects) {
        Map<String, AdmissionsCsatStdMax> std = new HashMap<>();
        for (var r : csatStdMax.findByAcademicYearAndStatus(academicYear, KnowledgeStatus.ACTIVE)) {
            std.put("INQ".equals(r.getArea()) ? "INQ:" + normalizeSubject(r.getSubjectName()) : r.getSubjectKey(), r);
        }
        Map<String, AdmissionsConversionScore> conv = new HashMap<>();
        for (var r : conversionScores.findByUniversityUniversityIdAndAcademicYearAndStatus(univ.getUniversityId(), academicYear, KnowledgeStatus.ACTIVE)) {
            conv.put(r.getTrack(), r);
        }
        return new Published(std, conv, inputs, subjects, objectMapper);
    }

    /**
     * 탐구 과목명 맞춤용 정규화(표시는 원문 그대로 둔다). 띄어쓰기·가운뎃점을 지우고 로마 숫자 Ⅰ/Ⅱ, 영문 I/II, 아라비아 1/2를 같게 본다.
     * 예: "물리학Ⅰ" = "물리학 I" = "물리학1", "사회·문화" = "사회문화".
     */
    static String normalizeSubject(String name) {
        if (name == null) return "";
        String s = name.replaceAll("[\\s·ㆍ・.\\-]", "");
        s = s.replace("Ⅱ", "2").replace("Ⅰ", "1");
        s = s.replaceAll("II$", "2").replaceAll("I$", "1");
        return s;
    }

    /** 승인된 평가원 탐구 과목명(원문 표기)과 계열. 화면 입력 도움말에 쓴다. */
    @Transactional(readOnly = true)
    public List<Map<String, String>> inquirySubjects(int academicYear) {
        List<Map<String, String>> out = new ArrayList<>();
        for (var r : csatStdMax.findByAcademicYearAndStatus(academicYear, KnowledgeStatus.ACTIVE)) {
            if ("INQ".equals(r.getArea())) out.add(Map.of("subject", r.getSubjectName(), "track", r.getTrack()));
        }
        out.sort(java.util.Comparator.comparing((Map<String, String> m) -> m.get("track")).thenComparing(m -> m.get("subject")));
        return out;
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
