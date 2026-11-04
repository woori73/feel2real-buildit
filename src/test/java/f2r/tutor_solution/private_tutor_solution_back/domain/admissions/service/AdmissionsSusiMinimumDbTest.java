package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsJeongsiFormulaRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsSusiMinimumRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsSusiMinimumService.Outcome;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 수시 수능최저: 적재 멱등성·변경 시 DRAFT 복귀·승격·판정(모집단위 우선 규칙·최저 없음·입력 부족·확인 필요). */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsActivationService.class, AdmissionsJeongsiScoreService.class,
        AdmissionsSusiMinimumService.class, AdmissionsSusiMinimumDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_susimin;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시상담 — 수시 수능최저 적재·승격·판정")
class AdmissionsSusiMinimumDbTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final String GEN = "low_sum(3, KOR_GRD, MATH_GRD, ENG_GRD, min(INQ1_GRD, INQ2_GRD)) <= 7";
    private static final String MED = "low_sum(3, KOR_GRD, MATH_GRD, ENG_GRD, (INQ1_GRD + INQ2_GRD) / 2) <= 4";

    /** 추천형: 전체(의예과 제외) 합7 / 의예과 합4, 서류형: 최저 없음, 논술: 확인 필요. */
    private static final String SEED = """
        {"ipsi_universities":[{"name":"테스트한양대학교","university_code":"TST"}],
         "ipsi_documents":[{"document_key":"doc_tst_2027_susi","document_name":"테스트한양대학교 2027학년도 수시모집 요강",
            "applicable_academic_year":2027,"institution_name":"테스트한양대학교 입학처","source_url":"https://example.test/susi.pdf"}],
         "susi_minimums":[
           {"university_code":"TST","academic_year":2027,"rule_key":"tst_general","rule_kind":"RULE","label":"전체(의예과 제외)",
            "type_names":["학생부종합(추천형)"],"department_names":[],"unit_scope_text":"전체 (의예과 제외)",
            "condition":"%s","required_areas_text":"국어, 수학, 영어, 탐구(2과목)","evidence_pages":[31],
            "raw_text":"국어, 수학, 영어, 탐구(상위 1개 과목) 중 3개 영역 등급합 7 이내","uncertain":[],"needs_review_reason":null,
            "source_document_key":"doc_tst_2027_susi","source_url":"https://example.test/susi.pdf"},
           {"university_code":"TST","academic_year":2027,"rule_key":"tst_med","rule_kind":"RULE","label":"의예과",
            "type_names":["학생부종합(추천형)"],"department_names":["의예과"],"unit_scope_text":"의예과",
            "condition":"%s","evidence_pages":[31],"raw_text":"탐구(2개 과목 평균) 중 3개 영역 등급합 4 이내","uncertain":[],
            "source_document_key":"doc_tst_2027_susi","source_url":"https://example.test/susi.pdf"},
           {"university_code":"TST","academic_year":2027,"rule_key":"tst_nomin_docs","rule_kind":"NO_MINIMUM","label":"학생부종합(서류형)",
            "type_names":["학생부종합(서류형)"],"evidence_pages":[32],"raw_text":"수능최저학력기준 : 미적용 (32쪽)",
            "source_document_key":"doc_tst_2027_susi","source_url":"https://example.test/susi.pdf"},
           {"university_code":"TST","academic_year":2027,"rule_key":"tst_nonsul","rule_kind":"RULE","label":"논술 계열별",
            "type_names":["논술우수자"],"condition":"%s","evidence_pages":[45],"raw_text":"계열별 기준","uncertain":[],
            "needs_review_reason":"계열별 모집단위 연결 필요",
            "source_document_key":"doc_tst_2027_susi","source_url":"https://example.test/susi.pdf"}]}
        """.formatted(GEN, MED, GEN);

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsActivationService activation;
    @Autowired AdmissionsSusiMinimumService service;
    @Autowired AdmissionsSusiMinimumRepository minimums;
    @Autowired AdmissionsJeongsiFormulaRepository formulas;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsKnowledgeItemRepository knowledgeItems;

    @AfterEach
    void clean() {
        minimums.deleteAll();
        formulas.deleteAll();
        knowledgeItems.deleteAll();
        types.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    private static Map<String, Double> grades(double kor, double math, double eng, double inq1, double inq2) {
        Map<String, Double> m = new HashMap<>();
        m.put("KOR_GRD", kor); m.put("MATH_GRD", math); m.put("ENG_GRD", eng); m.put("INQ1_GRD", inq1); m.put("INQ2_GRD", inq2);
        return m;
    }

    @Test
    @DisplayName("given 같은 시드 when 두 번 적재하면 then 늘지 않고, 내용이 바뀌면 그 규칙만 DRAFT로 돌아간다")
    void reload_isIdempotentAndChangesResetToDraft() throws Exception {
        // given
        var first = loader.loadSeed(SEED);
        var second = loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var third = loader.loadSeed(SEED.replace("등급합 7 이내\",\"uncertain\"", "등급합 6 이내\",\"uncertain\""));
        // then
        assertThat(first.susiMinimums()).isEqualTo(4);
        assertThat(second.susiMinimums()).isZero();
        assertThat(third.susiMinimumsUpdated()).isEqualTo(1);
        assertThat(minimums.findAll()).filteredOn(m -> m.getStatus() == KnowledgeStatus.DRAFT).hasSize(1);
        assertThat(minimums.findAll()).allSatisfy(m -> assertThat(m.getSource()).isNotNull());
    }

    @Test
    @DisplayName("given 해석할 수 없는 조건식·잘못된 종류 when 적재하면 then 전체가 거절된다")
    void badRule_rejectsSeed() {
        assertThatThrownBy(() -> loader.loadSeed(SEED.replace(MED, "KOR_GRD ** 2")))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class).hasMessageContaining("tst_med");
        assertThatThrownBy(() -> loader.loadSeed(SEED.replace("\"rule_kind\":\"NO_MINIMUM\"", "\"rule_kind\":\"NONE\"")))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class).hasMessageContaining("rule_kind");
    }

    @Test
    @DisplayName("given 승인된 규칙 when 일반 모집단위로 확인하면 then 전체 규칙으로 충족·미충족을 판정한다")
    void generalDepartment_usesGeneralRule() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when: 2+2+3=7 충족 / 3+3+3=9 미충족
        var met = service.check("테스트한양대학교", 2027, "학생부종합(추천형)", "컴퓨터소프트웨어학부", grades(2, 2, 3, 4, 3));
        var notMet = service.check("테스트한양대학교", 2027, "학생부종합(추천형)", "컴퓨터소프트웨어학부", grades(3, 3, 3, 4, 4));
        // then
        assertThat(met.rules()).hasSize(1);
        assertThat(met.rules().get(0).ruleKey()).isEqualTo("tst_general");
        assertThat(met.rules().get(0).outcome()).isEqualTo(Outcome.MET);
        assertThat(notMet.rules().get(0).outcome()).isEqualTo(Outcome.NOT_MET);
    }

    @Test
    @DisplayName("given 의예과 규칙 when 의예과로 확인하면 then 전체 규칙 대신 의예과 규칙만 쓴다")
    void namedDepartment_overridesGeneralRule() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when: 전체 규칙이면 충족(1+1+2=4… 이하)이지만 의예과는 탐구 평균 (2+3)/2=2.5 → 1+1+2.5=4.5 > 4 미충족
        var r = service.check("테스트한양대학교", 2027, "학생부종합(추천형)", "의예과", grades(1, 1, 3, 2, 3));
        // then
        assertThat(r.rules()).extracting(AdmissionsSusiMinimumService.RuleResult::ruleKey).containsExactly("tst_med");
        assertThat(r.rules().get(0).outcome()).isEqualTo(Outcome.NOT_MET);
    }

    @Test
    @DisplayName("given 최저 없는 전형·확인 필요 규칙·등급 누락 when 확인하면 then 각각 그대로 알려 준다")
    void noMinimum_withheld_needsInput() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var all = service.check("테스트한양대", 2027, null, null, Map.of("KOR_GRD", 2.0));
        // then: 모집단위를 모르면 그 대학 규칙을 모두 보여 준다(줄임말로도 찾는다)
        assertThat(all.rules()).hasSize(4);
        Map<String, Outcome> byKey = new HashMap<>();
        all.rules().forEach(x -> byKey.put(x.ruleKey(), x.outcome()));
        assertThat(byKey).containsEntry("tst_nomin_docs", Outcome.NO_MINIMUM)
                .containsEntry("tst_nonsul", Outcome.WITHHELD_NEEDS_REVIEW)
                .containsEntry("tst_general", Outcome.NEEDS_INPUT);
        var general = all.rules().stream().filter(x -> x.ruleKey().equals("tst_general")).findFirst().orElseThrow();
        assertThat(general.missingInputs()).containsExactly("ENG_GRD", "INQ1_GRD", "INQ2_GRD", "MATH_GRD");
    }

    @Test
    @DisplayName("given 점수 항목·범위 밖 등급 when 확인하면 then 요청 오류다")
    void invalidGrades() throws Exception {
        // given
        loader.loadSeed(SEED);
        // when / then
        assertThatThrownBy(() -> service.check("테스트한양대학교", 2027, null, null, Map.of("KOR_STD", 130.0)))
                .isInstanceOf(AdmissionsJeongsiScoreService.InvalidScoreRequestException.class).hasMessageContaining("KOR_STD");
        assertThatThrownBy(() -> service.check("테스트한양대학교", 2027, null, null, Map.of("ENG_GRD", 10.0)))
                .isInstanceOf(AdmissionsJeongsiScoreService.InvalidScoreRequestException.class).hasMessageContaining("ENG_GRD");
    }
}
