package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 정시 계산식 계산기: 파이썬 판(buildit-admissions scripts/jeongsi_formula.py)과 같은 규칙. */
class JeongsiFormulaEvaluatorTest {

    private static final Map<String, Map<String, ? extends Number>> ENG = Map.of("ENG", Map.of(
            "1", 100, "2", 97, "3", 90, "4", 80, "5", 65, "6", 50, "7", 30, "8", 10, "9", 0));

    @Test
    @DisplayName("given 최고점 대비 식 when 전국 최고점을 넣으면 then 배점 만점이 나온다")
    void maxRatio_fullPoints() {
        // given
        String expr = "KOR_STD / KOR_STD_MAX * 300 + table('ENG', ENG_GRD)";
        // when
        double v = JeongsiFormulaEvaluator.evaluate(expr, Map.of("KOR_STD", 139, "KOR_STD_MAX", 139, "ENG_GRD", 1), ENG);
        // then
        assertThat(v).isCloseTo(400.0, within(1e-9));
    }

    @Test
    @DisplayName("given A형·B형 중 높은 점수 when 계산하면 then 큰 쪽을 쓴다")
    void maxOfTwoTypes() {
        // given / when
        double v = JeongsiFormulaEvaluator.evaluate("max(KOR_STD * 1.1 + MATH_STD * 1.3, KOR_STD * 1.3 + MATH_STD * 1.1)",
                Map.of("KOR_STD", 130, "MATH_STD", 120), null);
        // then: A=299, B=301
        assertThat(v).isCloseTo(301.0, within(1e-9));
    }

    @Test
    @DisplayName("given 과탐 가산 조건 when 과탐이면 then 3% 가산, 아니면 그대로")
    void conditionalBonus() {
        // given
        String expr = "INQ1_CONV * cond(INQ1_SCI == 1, 1.03, 1)";
        // when / then
        assertThat(JeongsiFormulaEvaluator.evaluate(expr, Map.of("INQ1_CONV", 70, "INQ1_SCI", 1), null)).isCloseTo(72.1, within(1e-9));
        assertThat(JeongsiFormulaEvaluator.evaluate(expr, Map.of("INQ1_CONV", 70, "INQ1_SCI", 0), null)).isCloseTo(70.0, within(1e-9));
    }

    @Test
    @DisplayName("given 반올림·버림 when 계산하면 then 은행식이 아닌 반올림과 버림이다")
    void roundingModes() {
        // given / when / then
        assertThat(JeongsiFormulaEvaluator.evaluate("round(2.345, 2)", Map.of(), null)).isEqualTo(2.35);
        assertThat(JeongsiFormulaEvaluator.evaluate("round(2.5)", Map.of(), null)).isEqualTo(3.0);
        assertThat(JeongsiFormulaEvaluator.evaluate("trunc(2.349, 2)", Map.of(), null)).isEqualTo(2.34);
    }

    @Test
    @DisplayName("given 상위 2개 합 when 세 영역이면 then 큰 두 값의 합")
    void topSum() {
        // given / when
        double v = JeongsiFormulaEvaluator.evaluate("top_sum(2, KOR_PCT, MATH_PCT, INQ1_PCT)",
                Map.of("KOR_PCT", 80, "MATH_PCT", 95, "INQ1_PCT", 90), null);
        // then
        assertThat(v).isCloseTo(185.0, within(1e-9));
    }

    @Test
    @DisplayName("given 수능최저 '4개 영역 중 3개 합' when 등급을 넣으면 then 작은 세 등급의 합으로 판정한다")
    void lowSum_forMinimumGrades() {
        // given
        String expr = "low_sum(3, KOR_GRD, MATH_GRD, ENG_GRD, min(INQ1_GRD, INQ2_GRD)) <= 6";
        // when
        double met = JeongsiFormulaEvaluator.evaluate(expr, Map.of("KOR_GRD", 2, "MATH_GRD", 1, "ENG_GRD", 3, "INQ1_GRD", 4, "INQ2_GRD", 2), null);
        double notMet = JeongsiFormulaEvaluator.evaluate(expr, Map.of("KOR_GRD", 3, "MATH_GRD", 3, "ENG_GRD", 3, "INQ1_GRD", 4, "INQ2_GRD", 4), null);
        // then: 1+2+2=5 충족, 3+3+3=9 미충족
        assertThat(met).isEqualTo(1.0);
        assertThat(notMet).isEqualTo(0.0);
    }

    @Test
    @DisplayName("given and·or·not·이어 쓴 비교 when 계산하면 then 참 1, 거짓 0")
    void logicAndChainedCompare() {
        // given
        Map<String, Number> in = new HashMap<>(Map.of("INQ1_SOC", 1, "INQ2_SOC", 1, "ENG_GRD", 3));
        // when / then
        assertThat(JeongsiFormulaEvaluator.evaluate("INQ1_SOC == 1 and INQ2_SOC == 1", in, null)).isEqualTo(1.0);
        assertThat(JeongsiFormulaEvaluator.evaluate("not (INQ1_SOC == 1) or ENG_GRD > 5", in, null)).isEqualTo(0.0);
        assertThat(JeongsiFormulaEvaluator.evaluate("1 <= ENG_GRD <= 4", in, null)).isEqualTo(1.0);
        assertThat(JeongsiFormulaEvaluator.evaluate("-ENG_GRD * 2", in, null)).isEqualTo(-6.0);
    }

    @Test
    @DisplayName("given 입력 누락·0 나눔·없는 표 값 when 계산하면 then 원인을 담은 오류")
    void errors() {
        assertThatThrownBy(() -> JeongsiFormulaEvaluator.evaluate("KOR_STD * 2", Map.of(), null)).hasMessageContaining("KOR_STD");
        assertThatThrownBy(() -> JeongsiFormulaEvaluator.evaluate("1 / (ENG_GRD - 1)", Map.of("ENG_GRD", 1), null)).hasMessageContaining("0으로");
        assertThatThrownBy(() -> JeongsiFormulaEvaluator.evaluate("table('ENG', 10)", Map.of(), ENG)).hasMessageContaining("10");
    }

    @Test
    @DisplayName("given 허용 밖 문법 when 해석하면 then 거절한다")
    void rejectsOtherSyntax() {
        for (String bad : new String[] {"__import__('os')", "KOR_STD.real", "open('a')", "[1,2]", "KOR_STD ** 2", "KOR_STD;1"}) {
            assertThatThrownBy(() -> JeongsiFormulaEvaluator.variables(bad)).as(bad).isInstanceOf(JeongsiFormulaEvaluator.FormulaException.class);
        }
    }

    @Test
    @DisplayName("given 표·함수가 섞인 식 when 변수를 뽑으면 then 함수·표 이름은 빠진다")
    void variablesExcludeFunctionsAndTables() {
        // given / when
        var vars = JeongsiFormulaEvaluator.variables("max(KOR_STD, MATH_STD) + table('ENG', ENG_GRD) + cond(INQ1_SCI == 1, 3, 0)");
        // then
        assertThat(vars).containsExactlyInAnyOrder("KOR_STD", "MATH_STD", "ENG_GRD", "INQ1_SCI");
    }
}
