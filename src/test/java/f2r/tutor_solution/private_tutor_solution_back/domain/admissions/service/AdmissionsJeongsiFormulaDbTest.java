package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsJeongsiFormulaRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 정시 수능 환산 계산식: 적재 멱등성·변경 시 DRAFT 복귀·승격·점수 계산(입력 부족·확인 필요·대학 줄임말). */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsActivationService.class, AdmissionsJeongsiScoreService.class,
        AdmissionsJeongsiFormulaDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_jeongsi;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시상담 — 정시 수능 환산 계산식 적재·승격·계산")
class AdmissionsJeongsiFormulaDbTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 인문(국어 300·영어 등급표)과 확인 필요 계산식 하나. */
    private static final String SEED = """
        {"ipsi_universities":[{"name":"테스트시립대학교","university_code":"TST"}],
         "ipsi_documents":[{"document_key":"doc_tst_2027_jeongsi","document_name":"테스트시립대학교 2027학년도 정시모집 신입생 모집요강",
            "applicable_academic_year":2027,"institution_name":"테스트시립대학교 입학처","source_url":"https://example.test/j.pdf"}],
         "jeongsi_formulas":[
           {"university_code":"TST","academic_year":2027,"formula_key":"tst_hum","label":"인문계열",
            "type_names":["정시 일반전형(가군)"],"department_names":["경영학부"],"unit_scope_text":"인문계열: 경영학부",
            "csat_points":400,"perfect_score_expected":400,
            "expression":"KOR_STD / KOR_STD_MAX * 300 + table('ENG', ENG_GRD) + cond(INQ1_SOC == 1, 3, 0)",
            "tables":{"ENG":{"1":100,"2":97,"3":90,"4":80,"5":65,"6":50,"7":30,"8":10,"9":0}},
            "rounding_text":null,"eligibility_text":"국어, 영어","not_in_csat_score_text":null,"evidence_pages":[6,7],
            "raw_text":"국어 300점(표준점수 취득점/최고점×반영점수), 영어 등급별 점수","uncertain":[],"needs_review_reason":null,
            "source_document_key":"doc_tst_2027_jeongsi","source_url":"https://example.test/j.pdf"},
           {"university_code":"TST","academic_year":2027,"formula_key":"tst_art","label":"예체능",
            "type_names":["정시 실기전형(나군)"],"department_names":[],"unit_scope_text":null,"csat_points":200,"perfect_score_expected":null,
            "expression":"KOR_PCT * 2","tables":{},"evidence_pages":[8],"raw_text":"백분위 환산","uncertain":["환산 방법 원문 없음"],
            "needs_review_reason":"원문에 환산 방법이 없음 — 대학 확인 필요",
            "source_document_key":"doc_tst_2027_jeongsi","source_url":"https://example.test/j.pdf"}]}
        """;

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsActivationService activation;
    @Autowired AdmissionsJeongsiScoreService scores;
    @Autowired AdmissionsJeongsiFormulaRepository formulas;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsKnowledgeItemRepository knowledgeItems;

    @AfterEach
    void clean() {
        formulas.deleteAll();
        knowledgeItems.deleteAll();
        types.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    private static Map<String, Double> student() {
        Map<String, Double> m = new HashMap<>();
        m.put("KOR_STD", 130.0);
        m.put("KOR_PCT", 95.0);
        m.put("ENG_GRD", 2.0);
        return m;
    }

    @Test
    @DisplayName("given 같은 시드 when 두 번 적재하면 then 계산식이 늘지 않고 출처 자료가 연결된 DRAFT다")
    void reload_isIdempotent() throws Exception {
        // given
        var first = loader.loadSeed(SEED);
        // when
        var second = loader.loadSeed(SEED);
        // then
        assertThat(first.jeongsiFormulas()).isEqualTo(2);
        assertThat(second.jeongsiFormulas()).isZero();
        assertThat(second.jeongsiFormulasUpdated()).isZero();
        assertThat(formulas.findAll()).hasSize(2)
                .allSatisfy(f -> assertThat(f.getStatus()).isEqualTo(KnowledgeStatus.DRAFT))
                .allSatisfy(f -> assertThat(f.getSource()).isNotNull());
    }

    @Test
    @DisplayName("given 승인된 계산식 when 내용이 바뀐 시드를 적재하면 then 그 계산식만 바뀌고 DRAFT로 돌아간다")
    void changedFormula_isUpdatedAndResetToDraft() throws Exception {
        // given
        loader.loadSeed(SEED);
        var act = activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var r = loader.loadSeed(SEED.replace("KOR_STD / KOR_STD_MAX * 300", "KOR_STD / KOR_STD_MAX * 350"));
        // then
        assertThat(act.jeongsiFormulas()).isEqualTo(2);
        assertThat(r.jeongsiFormulasUpdated()).isEqualTo(1);
        var hum = formulas.findAll().stream().filter(f -> f.getFormulaKey().equals("tst_hum")).findFirst().orElseThrow();
        var art = formulas.findAll().stream().filter(f -> f.getFormulaKey().equals("tst_art")).findFirst().orElseThrow();
        assertThat(hum.getExpression()).contains("* 350");
        assertThat(hum.getStatus()).isEqualTo(KnowledgeStatus.DRAFT);
        assertThat(art.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE);
    }

    @Test
    @DisplayName("given 해석할 수 없는 계산식 when 적재하면 then 전체가 거절된다")
    void badExpression_rejectsSeed() {
        // given / when / then
        assertThatThrownBy(() -> loader.loadSeed(SEED.replace("KOR_PCT * 2", "KOR_PCT ** 2")))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class)
                .hasMessageContaining("tst_art");
    }

    @Test
    @DisplayName("given 승인 전 계산식 when 계산하면 then 쓰이지 않는다")
    void draftFormula_isNotUsed() throws Exception {
        // given
        loader.loadSeed(SEED);
        // when
        var r = scores.calculate("테스트시립대학교", 2027, null, null, student());
        // then
        assertThat(r.formulas()).isEmpty();
        assertThat(r.notice()).contains("계산식이 없습니다");
    }

    @Test
    @DisplayName("given 승인된 계산식 when 최고점까지 넣으면 then 환산점수, 최고점이 없으면 필요한 값을 알려 준다")
    void score_calculatedOrNeedsInput() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        Map<String, Double> full = student();
        full.put("KOR_STD_MAX", 140.0);
        // when
        var calculated = scores.calculate("테스트시립대학교", 2027, "정시 일반전형(가군)", "경영학부", full);
        var waiting = scores.calculate("테스트시립대학교", 2027, "정시 일반전형(가군)", null, student());
        // then: 130/140×300 + 97 = 375.5714…
        var c = calculated.formulas().get(0);
        assertThat(calculated.formulas()).hasSize(1);
        assertThat(c.outcome()).isEqualTo(Outcome.CALCULATED);
        assertThat(c.score()).isEqualByComparingTo(new BigDecimal("375.5714"));
        assertThat(c.defaultedInputs()).containsExactly("INQ1_SOC");
        assertThat(c.sourceTitle()).isEqualTo("테스트시립대학교 2027학년도 정시모집 신입생 모집요강");
        var w = waiting.formulas().get(0);
        assertThat(w.outcome()).isEqualTo(Outcome.NEEDS_INPUT);
        assertThat(w.pendingInputs()).containsExactly("KOR_STD_MAX");
        assertThat(w.score()).isNull();
    }

    @Test
    @DisplayName("given 확인 필요 계산식 when 계산하면 then 점수를 내지 않고 사유만 돌려준다")
    void needsReview_isWithheld() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var r = scores.calculate("테스트시립대", 2027, "정시 실기전형(나군)", null, student());
        // then: 줄임말("테스트시립대")로도 찾는다
        var f = r.formulas().get(0);
        assertThat(f.outcome()).isEqualTo(Outcome.WITHHELD_NEEDS_REVIEW);
        assertThat(f.score()).isNull();
        assertThat(f.needsReviewReason()).contains("대학 확인 필요");
    }

    @Test
    @DisplayName("given 알 수 없는 입력·범위 밖 값·없는 대학 when 계산하면 then 요청 오류다")
    void invalidRequests() throws Exception {
        // given
        loader.loadSeed(SEED);
        // when / then
        assertThatThrownBy(() -> scores.calculate("테스트시립대학교", 2027, null, null, Map.of("KOREAN", 1.0)))
                .isInstanceOf(AdmissionsJeongsiScoreService.InvalidScoreRequestException.class).hasMessageContaining("KOREAN");
        assertThatThrownBy(() -> scores.calculate("테스트시립대학교", 2027, null, null, Map.of("ENG_GRD", 0.0)))
                .isInstanceOf(AdmissionsJeongsiScoreService.InvalidScoreRequestException.class).hasMessageContaining("ENG_GRD");
        assertThatThrownBy(() -> scores.calculate("없는대학교", 2027, null, null, student()))
                .isInstanceOf(AdmissionsJeongsiScoreService.InvalidScoreRequestException.class);
    }
}
