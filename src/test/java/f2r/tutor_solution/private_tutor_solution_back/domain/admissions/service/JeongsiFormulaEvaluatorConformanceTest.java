package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자바 계산기가 파이썬 판과 같은 값을 내는지, 실제 계산식 전부(서울 19개교 156개 × 만점·표본 입력 = 312건)로 확인한다.
 * 자료: src/test/resources/admissions/jeongsi_formula_conformance.json
 * (buildit-admissions {@code python3 scripts/build_jeongsi_formula_seed.py --conformance-out ...}로 만든다).
 */
class JeongsiFormulaEvaluatorConformanceTest {

    @Test
    @DisplayName("given 파이썬 계산기로 낸 312건 when 자바 계산기로 계산하면 then 모두 같은 값이다")
    void allFormulasMatchPython() throws Exception {
        // given
        List<Map<String, Object>> cases;
        try (InputStream in = getClass().getResourceAsStream("/admissions/jeongsi_formula_conformance.json")) {
            assertThat(in).as("conformance fixture").isNotNull();
            cases = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
        ObjectMapper om = new ObjectMapper();
        List<String> mismatches = new ArrayList<>();

        // when
        for (Map<String, Object> c : cases) {
            String expr = (String) c.get("expression");
            Map<String, Double> inputs = om.convertValue(c.get("inputs"), new TypeReference<HashMap<String, Double>>() {});
            Map<String, Map<String, Double>> tables = om.convertValue(c.get("tables"), new TypeReference<HashMap<String, Map<String, Double>>>() {});
            double expected = ((Number) c.get("expected")).doubleValue();
            double got = JeongsiFormulaEvaluator.evaluate(expr, inputs, new HashMap<>(tables));
            if (Math.abs(got - expected) > 1e-9) mismatches.add(c.get("formula_key") + "/" + c.get("case") + ": " + got + " ≠ " + expected);
        }

        // then
        assertThat(cases).hasSize(312);
        assertThat(mismatches).isEmpty();
    }
}
