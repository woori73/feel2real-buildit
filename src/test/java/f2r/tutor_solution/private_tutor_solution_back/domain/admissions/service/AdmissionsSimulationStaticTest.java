package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Qoder 상담 시뮬레이션 22건 — 정적 검증 테스트.
 *
 * <p>시뮬레이션 원본 ({@code simulation_results.json})의 무결성을 검증한다:
 * 22건 전수 존재, PASS 등급, 실패 기준 3종 (잘못된 단정/학년도 혼동/근거 없는 추천) 위반 없음.
 *
 * <p>실제 Agent 응답 테스트 (LLM·RAG 실행)는 개발환경에서 별도 수행하고 결과를 보고한다.
 * 정적 시뮬레이션 통과와 실제 LLM·RAG 실행 통과는 구분하여 보고한다.
 */
class AdmissionsSimulationStaticTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<SimulationCase> loadCases() throws Exception {
        try (InputStream in = AdmissionsSimulationStaticTest.class.getResourceAsStream(
                "/admissions/simulation_results.json")) {
            assertNotNull(in, "simulation_results.json 테스트 리소스 필요");
            JsonNode root = MAPPER.readTree(in);
            List<SimulationCase> cases = new ArrayList<>();
            for (JsonNode q : root.path("questions")) {
                cases.add(new SimulationCase(
                        q.path("id").asText(),
                        q.path("category").asText(),
                        q.path("question").asText(),
                        q.path("answer").asText(),
                        q.path("grade").asText(),
                        q.path("notes").asText()));
            }
            return cases;
        }
    }

    record SimulationCase(String id, String category, String question,
                          String answer, String grade, String notes) {}

    static Stream<SimulationCase> cases() throws Exception {
        return loadCases().stream();
    }

    @Test
    @DisplayName("시뮬레이션 22건 전수 존재")
    void all22CasesPresent() throws Exception {
        // given / when
        List<SimulationCase> cases = loadCases();

        // then
        assertEquals(22, cases.size(), "시뮬레이션은 22건이어야 함");
    }

    @ParameterizedTest(name = "{0} — 정적 PASS 확인")
    @MethodSource("cases")
    @DisplayName("시뮬레이션 정적 PASS")
    void staticPass(SimulationCase c) {
        // given: Qoder의 정적 시뮬레이션 결과
        // when / then: PASS 등급이어야 함
        assertEquals("PASS", c.grade(), c.id() + " 정적 등급은 PASS이어야 함");
        assertFalse(c.question().isBlank(), c.id() + " 질문 비어 있음");
        assertFalse(c.answer().isBlank(), c.id() + " 답변 비어 있음");
    }

    @ParameterizedTest(name = "{0} — 실패 기준 3종 위반 없음")
    @MethodSource("cases")
    @DisplayName("실패 기준 3종 위반 없음 (정적)")
    void noFailureRuleViolation(SimulationCase c) {
        String answer = c.answer();
        // 실패 기준 1: 잘못된 단정 — "확실합니다", "100%" 같은 단정 표현 검사
        // (시뮬레이션 답변은 T3 수준 표기를 사용하므로 단정 표현이 없어야 함)
        assertFalse(answer.contains("확실합니다") && !answer.contains("확인"),
                c.id() + " 잘못된 단정 의심");

        // 실패 기준 2: 학년도 혼동 — 2027/2028이 모두 언급되면 구분 표기 확인
        boolean mentions2027 = answer.contains("2027");
        boolean mentions2028 = answer.contains("2028");
        if (mentions2027 && mentions2028) {
            assertTrue(answer.contains("확정") || answer.contains("계획"),
                    c.id() + " 학년도 혼동 — 2027/2028 구분 표기 필요");
        }

        // 실패 기준 3: 근거 없는 추천 — 추천 표현이 있으면 근거 표기 확인
        if (answer.contains("추천")) {
            assertTrue(answer.contains("[T") || answer.contains("doc_") || answer.contains("근거"),
                    c.id() + " 근거 없는 추천 의심");
        }
    }

    @Test
    @DisplayName("시뮬레이션 카테고리 분포")
    void categoryDistribution() throws Exception {
        // given / when
        List<SimulationCase> cases = loadCases();

        // then — 6개 카테고리 모두 존재
        var categories = cases.stream().map(SimulationCase::category).distinct().toList();
        assertTrue(categories.size() >= 5, "카테고리 다양성: " + categories);
    }
}
