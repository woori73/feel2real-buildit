package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsConversionScoreRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCsatStdMaxRepository;
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

/** 정시 환산용 발표 값(평가원 표준점수 최고점·대학 변환표준점수표): 적재·승격·계산식 입력 채우기. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsActivationService.class, AdmissionsJeongsiScoreService.class,
        AdmissionsJeongsiStatsDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_jeongsi_stats;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시상담 — 정시 환산용 발표 값 적재·승격·계산")
class AdmissionsJeongsiStatsDbTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 국어 최고점 비율 + 탐구 변환표준점수 비율을 쓰는 계산식 하나. */
    private static final String FORMULA_SEED = """
        {"ipsi_universities":[{"name":"테스트이화대학교","university_code":"TST"}],
         "ipsi_documents":[{"document_key":"doc_tst_2027_jeongsi","document_name":"테스트이화대학교 2027학년도 정시모집 요강",
            "applicable_academic_year":2027,"institution_name":"테스트이화대학교 입학처","source_url":"https://example.test/j.pdf"}],
         "jeongsi_formulas":[
           {"university_code":"TST","academic_year":2027,"formula_key":"tst_hum","label":"인문",
            "type_names":["정시 일반전형(가군)"],"department_names":[],"csat_points":1000,
            "expression":"KOR_STD / KOR_STD_MAX * 500 + INQ1_CONV / INQ1_CONV_MAX * 250 + INQ2_STD / INQ2_STD_MAX * 250",
            "tables":{},"evidence_pages":[6],"raw_text":"국어 표준점수/최고점, 탐구 변환표준점수","uncertain":[],
            "source_document_key":"doc_tst_2027_jeongsi","source_url":"https://example.test/j.pdf"}]}
        """;

    /** 백분위 p → 40 + 0.3p (백분위 100 = 70). */
    private static final String STATS_SEED = """
        {"ipsi_universities":[{"name":"테스트이화대학교","university_code":"TST"}],
         "ipsi_documents":[
           {"document_key":"doc_kice_2027_csat_scores","document_name":"2027학년도 수능 채점 결과","applicable_academic_year":2027,
            "institution_name":"한국교육과정평가원","source_url":"https://example.test/kice.pdf"},
           {"document_key":"doc_tst_2027_jeongsi_conversion","document_name":"테스트이화대학교 2027 탐구 변환표준점수","applicable_academic_year":2027,
            "institution_name":"테스트이화대학교 입학처","source_url":"https://example.test/conv"}],
         "csat_std_max":[
           {"academic_year":2027,"subject_key":"KOR","area":"KOR","subject_name":null,"track":null,"std_max":140,"evidence_pages":[3],
            "source_document_key":"doc_kice_2027_csat_scores","source_url":"https://example.test/kice.pdf"},
           {"academic_year":2027,"subject_key":"MATH","area":"MATH","subject_name":null,"track":null,"std_max":145,"evidence_pages":[3],
            "source_document_key":"doc_kice_2027_csat_scores","source_url":"https://example.test/kice.pdf"},
           {"academic_year":2027,"subject_key":"INQ:물리학Ⅰ","area":"INQ","subject_name":"물리학Ⅰ","track":"SCI","std_max":70,"evidence_pages":[4],
            "source_document_key":"doc_kice_2027_csat_scores","source_url":"https://example.test/kice.pdf"}],
         "conversion_scores":[
           {"university_code":"TST","academic_year":2027,"track":"SCI","label":"과학탐구","scores":{%s},"evidence_pages":[1],
            "source_document_key":"doc_tst_2027_jeongsi_conversion","source_url":"https://example.test/conv"}]}
        """.formatted("\"0\":40.0,\"1\":40.3,\"2\":40.6,\"3\":40.9,\"4\":41.2,\"5\":41.5,\"6\":41.8,\"7\":42.1,\"8\":42.4,\"9\":42.7,\"10\":43.0,\"11\":43.3,\"12\":43.6,\"13\":43.9,\"14\":44.2,\"15\":44.5,\"16\":44.8,\"17\":45.1,\"18\":45.4,\"19\":45.7,\"20\":46.0,\"21\":46.3,\"22\":46.6,\"23\":46.9,\"24\":47.2,\"25\":47.5,\"26\":47.8,\"27\":48.1,\"28\":48.4,\"29\":48.7,\"30\":49.0,\"31\":49.3,\"32\":49.6,\"33\":49.9,\"34\":50.2,\"35\":50.5,\"36\":50.8,\"37\":51.1,\"38\":51.4,\"39\":51.7,\"40\":52.0,\"41\":52.3,\"42\":52.6,\"43\":52.9,\"44\":53.2,\"45\":53.5,\"46\":53.8,\"47\":54.1,\"48\":54.4,\"49\":54.7,\"50\":55.0,\"51\":55.3,\"52\":55.6,\"53\":55.9,\"54\":56.2,\"55\":56.5,\"56\":56.8,\"57\":57.1,\"58\":57.4,\"59\":57.7,\"60\":58.0,\"61\":58.3,\"62\":58.6,\"63\":58.9,\"64\":59.2,\"65\":59.5,\"66\":59.8,\"67\":60.1,\"68\":60.4,\"69\":60.7,\"70\":61.0,\"71\":61.3,\"72\":61.6,\"73\":61.9,\"74\":62.2,\"75\":62.5,\"76\":62.8,\"77\":63.1,\"78\":63.4,\"79\":63.7,\"80\":64.0,\"81\":64.3,\"82\":64.6,\"83\":64.9,\"84\":65.2,\"85\":65.5,\"86\":65.8,\"87\":66.1,\"88\":66.4,\"89\":66.7,\"90\":67.0,\"91\":67.3,\"92\":67.6,\"93\":67.9,\"94\":68.2,\"95\":68.5,\"96\":68.8,\"97\":69.1,\"98\":69.4,\"99\":69.7,\"100\":70.0");

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsActivationService activation;
    @Autowired AdmissionsJeongsiScoreService scores;
    @Autowired AdmissionsJeongsiFormulaRepository formulas;
    @Autowired AdmissionsCsatStdMaxRepository stdMax;
    @Autowired AdmissionsConversionScoreRepository conversions;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsKnowledgeItemRepository knowledgeItems;

    @AfterEach
    void clean() {
        formulas.deleteAll();
        stdMax.deleteAll();
        conversions.deleteAll();
        knowledgeItems.deleteAll();
        types.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    /** 국어 표준 126, 탐구1 과학 백분위 90, 탐구2 물리학Ⅰ 표준 63. */
    private static Map<String, Double> student() {
        Map<String, Double> m = new HashMap<>();
        m.put("KOR_STD", 126.0);
        m.put("INQ1_PCT", 90.0);
        m.put("INQ1_SCI", 1.0);
        m.put("INQ2_STD", 63.0);
        return m;
    }

    @Test
    @DisplayName("given 같은 발표 값 시드 when 두 번 적재하면 then 늘지 않고 DRAFT이며, 승격하면 ACTIVE다")
    void reload_isIdempotent_andActivates() throws Exception {
        // given
        var first = loader.loadSeed(STATS_SEED);
        // when
        var second = loader.loadSeed(STATS_SEED);
        var act = activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // then
        assertThat(first.csatStdMax()).isEqualTo(3);
        assertThat(first.conversionScores()).isEqualTo(1);
        assertThat(second.csatStdMax()).isZero();
        assertThat(second.conversionScores()).isZero();
        assertThat(act.csatStdMax()).isEqualTo(3);
        assertThat(act.conversionScores()).isEqualTo(1);
        assertThat(stdMax.findAll()).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE));
    }

    @Test
    @DisplayName("given 승인된 발표 값 when 계산하면 then 최고점·변환표준점수를 채워 점수를 내고 채운 값을 알려 준다")
    void publishedValues_fillPendingInputs() throws Exception {
        // given
        loader.loadSeed(FORMULA_SEED);
        loader.loadSeed(STATS_SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when: 탐구2 과목명은 띄어쓰기·숫자 표기가 달라도 같은 과목으로 찾는다
        var r = scores.calculate("테스트이화대학교", 2027, null, null, student(), Map.of("INQ2", "물리학 1"));
        // then: 126/140*500 + 67/70*250 + 63/70*250 = 450 + 239.2857 + 225
        var f = r.formulas().get(0);
        assertThat(f.outcome()).isEqualTo(Outcome.CALCULATED);
        assertThat(f.score()).isEqualByComparingTo(new BigDecimal("914.2857"));
        assertThat(f.derivedInputs()).extracting(AdmissionsJeongsiScoreService.DerivedInput::name)
                .containsExactly("INQ1_CONV", "INQ1_CONV_MAX", "INQ2_STD_MAX", "KOR_STD_MAX");
        assertThat(f.derivedInputs().get(0).value()).isEqualByComparingTo("67");
    }

    @Test
    @DisplayName("given 과목명·계열을 안 넣은 학생 when 계산하면 then 발표 대기가 아니라 학생 입력을 요청한다")
    void missingSubjectOrTrack_asksStudent() throws Exception {
        // given
        loader.loadSeed(FORMULA_SEED);
        loader.loadSeed(STATS_SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        Map<String, Double> noTrack = new HashMap<>(student());
        noTrack.remove("INQ1_SCI");
        // when
        var r = scores.calculate("테스트이화대학교", 2027, null, null, noTrack, null);
        // then
        var f = r.formulas().get(0);
        assertThat(f.outcome()).isEqualTo(Outcome.NEEDS_INPUT);
        assertThat(f.missingInputs()).containsExactly("INQ1_TRACK", "INQ2_SUBJECT");
        assertThat(f.pendingInputs()).isEmpty();
    }

    @Test
    @DisplayName("given 발표 값이 아직 없음 when 계산하면 then 기존처럼 발표 대기 값으로 알린다")
    void noPublishedValues_staysPending() throws Exception {
        // given
        loader.loadSeed(FORMULA_SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var r = scores.calculate("테스트이화대학교", 2027, null, null, student(), Map.of("INQ2", "물리학Ⅰ"));
        // then
        assertThat(r.formulas().get(0).pendingInputs()).containsExactly("INQ1_CONV", "INQ1_CONV_MAX", "INQ2_STD_MAX", "KOR_STD_MAX");
    }

    @Test
    @DisplayName("given 백분위가 낮은데 점수가 더 높은 표 when 적재하면 then 전체가 거절된다")
    void nonMonotonicTable_rejected() {
        // given / when / then
        assertThatThrownBy(() -> loader.loadSeed(STATS_SEED.replace("\"50\":55.0", "\"50\":99.0")))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class).hasMessageContaining("백분위 50");
    }

    @Test
    @DisplayName("given 평가원 최고점만 담은 시드(대학 없음) when 적재하면 then 받아들인다")
    void stdMaxOnlySeed_isAccepted() throws Exception {
        // given
        String seed = """
            {"ipsi_universities":[],
             "ipsi_documents":[{"document_key":"doc_kice_2027_csat_scores","document_name":"2027학년도 수능 채점 결과",
                "applicable_academic_year":2027,"institution_name":"한국교육과정평가원","source_url":"https://example.test/kice.pdf"}],
             "csat_std_max":[{"academic_year":2027,"subject_key":"KOR","area":"KOR","std_max":140,
                "source_document_key":"doc_kice_2027_csat_scores"}]}
            """;
        // when
        var r = loader.loadSeed(seed);
        // then
        assertThat(r.csatStdMax()).isEqualTo(1);
        assertThat(stdMax.findAll().get(0).getSource()).isNotNull();
    }
}
