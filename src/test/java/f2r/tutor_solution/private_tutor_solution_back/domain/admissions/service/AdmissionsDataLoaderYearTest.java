package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("입시 적재기 학년도 판정 — 미확정/미지원 연도를 2027로 확정하지 않는다")
class AdmissionsDataLoaderYearTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode field(String json) throws Exception {
        return mapper.readTree(json).path("y");
    }

    @Test
    @DisplayName("given 2027·2028 정수/문자열 when 읽으면 then 그대로 반환한다")
    void supported_years_are_returned() throws Exception {
        // given
        JsonNode intYear = field("{\"y\":2027}");
        JsonNode strYear = field("{\"y\":\" 2028 \"}");

        // when
        Integer a = AdmissionsDataLoader.readSupportedYear(intYear);
        Integer b = AdmissionsDataLoader.readSupportedYear(strYear);

        // then
        assertThat(a).isEqualTo(2027);
        assertThat(b).isEqualTo(2028);
    }

    @Test
    @DisplayName("given 누락·null·빈 문자열·숫자 아님 when 읽으면 then null (2027 기본값 없음)")
    void missing_or_invalid_year_is_null() throws Exception {
        // given
        JsonNode missing = field("{}");
        JsonNode nul = field("{\"y\":null}");
        JsonNode blank = field("{\"y\":\"\"}");
        JsonNode text = field("{\"y\":\"미확정\"}");

        // when / then
        assertThat(AdmissionsDataLoader.readSupportedYear(missing)).isNull();
        assertThat(AdmissionsDataLoader.readSupportedYear(nul)).isNull();
        assertThat(AdmissionsDataLoader.readSupportedYear(blank)).isNull();
        assertThat(AdmissionsDataLoader.readSupportedYear(text)).isNull();
    }

    @Test
    @DisplayName("given 2029·2026 같은 범위 밖 학년도 when 읽으면 then null — 2027/2028과 섞이지 않는다")
    void unsupported_years_are_null() throws Exception {
        // given
        JsonNode y2029 = field("{\"y\":2029}");
        JsonNode y2026 = field("{\"y\":\"2026\"}");

        // when / then
        assertThat(AdmissionsDataLoader.readSupportedYear(y2029)).isNull();
        assertThat(AdmissionsDataLoader.readSupportedYear(y2026)).isNull();
    }
}
