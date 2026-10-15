package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 질문에서 구조화 검색 조건을 추출하는 로직 검증.
 * 핵심 원칙: 추출 실패 시 null (임의로 채우지 않음).
 */
@DisplayName("사례 조건 추출기")
class CaseConditionExtractorTest {

    @Test
    @DisplayName("3인가구 추출")
    void extractHousehold() {
        CaseConditions c = CaseConditionExtractor.extract("3인가구인데 채무 5천만원 있습니다");
        assertThat(c.getHouseholdType()).isEqualTo("3인가구");
    }

    @Test
    @DisplayName("채무액 추출 - 천만원 단위")
    void extractDebtCheonman() {
        CaseConditions c = CaseConditionExtractor.extract("채무 5천만원 있습니다");
        assertThat(c.getDebtTotalMin()).isEqualTo(35_000_000L); // 5000만 * 0.7
        assertThat(c.getDebtTotalMax()).isEqualTo(65_000_000L); // 5000만 * 1.3
    }

    @Test
    @DisplayName("채무액 추출 - 억원 단위")
    void extractDebtEok() {
        CaseConditions c = CaseConditionExtractor.extract("총채무액 1억원에 이자가 포함되나요?");
        assertThat(c.getDebtTotalMin()).isEqualTo(70_000_000L);
        assertThat(c.getDebtTotalMax()).isEqualTo(130_000_000L);
    }

    @Test
    @DisplayName("감면율 범위 추출")
    void extractRateRange() {
        CaseConditions c = CaseConditionExtractor.extract("감면율 70~80% 받은 사례 있나요?");
        assertThat(c.getReductionRateMin()).isEqualTo(70);
        assertThat(c.getReductionRateMax()).isEqualTo(80);
    }

    @Test
    @DisplayName("감면율 단일 추출 (±10%)")
    void extractRateSingle() {
        CaseConditions c = CaseConditionExtractor.extract("감면율 90% 특별감면");
        assertThat(c.getReductionRateMin()).isEqualTo(80);
        assertThat(c.getReductionRateMax()).isEqualTo(100);
    }

    @Test
    @DisplayName("담보대출 있음")
    void extractSecured() {
        CaseConditions c = CaseConditionExtractor.extract("담보대출이 있는데 신청 가능한가요?");
        assertThat(c.getHasSecuredDebt()).isTrue();
    }

    @Test
    @DisplayName("무담보")
    void extractUnsecured() {
        CaseConditions c = CaseConditionExtractor.extract("무담보 채무만 있습니다");
        assertThat(c.getHasSecuredDebt()).isFalse();
    }

    @Test
    @DisplayName("부실차주 / 부실우려차주 구분")
    void extractApplicationType() {
        assertThat(CaseConditionExtractor.extract("부실차주입니다").getApplicationType())
                .isEqualTo("INSOLVENCY");
        assertThat(CaseConditionExtractor.extract("부실우려차주입니다").getApplicationType())
                .isEqualTo("INSOLVENCY_RISK");
    }

    @Test
    @DisplayName("조건 없으면 빈 객체 (null 아님)")
    void emptyConditions() {
        CaseConditions c = CaseConditionExtractor.extract("감면율이 궁금합니다");
        assertThat(c).isNotNull();
        assertThat(c.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("null/빈 질문도 빈 객체")
    void nullQuestion() {
        assertThat(CaseConditionExtractor.extract(null).isEmpty()).isTrue();
        assertThat(CaseConditionExtractor.extract("").isEmpty()).isTrue();
        assertThat(CaseConditionExtractor.extract("   ").isEmpty()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "'75%', 75",
        "'75~80%', 75",
        "'90% 특별감면', 90",
        "'감면율 없음', ''",
        "'', ''"
    })
    @DisplayName("감면율 VARCHAR 파싱 - 실패 시 null (제외하지 않음)")
    void parseReductionRate(String input, String expected) {
        String normalized = "''".equals(input) ? null : input.replace("'", "");
        Integer result = CaseConditionExtractor.parseReductionRateLower(normalized);
        if (expected == null || expected.isEmpty() || "''".equals(expected)) {
            assertThat(result).isNull();
        } else {
            assertThat(result).isEqualTo(Integer.parseInt(expected.replace("'", "")));
        }
    }

    @Test
    @DisplayName("이자 질문은 채무액으로 오인하지 않음 (이자 단독 컬럼 없음)")
    void interestNotMisparsed() {
        // "이자"만 있고 채무 문맥이 없으면 채무액 추출 안 함
        CaseConditions c = CaseConditionExtractor.extract("이자는 어떻게 되나요?");
        assertThat(c.getDebtTotalMin()).isNull();
    }
}
