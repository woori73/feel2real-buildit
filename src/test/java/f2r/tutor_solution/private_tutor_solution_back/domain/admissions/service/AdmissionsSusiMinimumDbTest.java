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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 수시 수능최저: 적재 멱등성·변경 시 DRAFT 복귀·승격·판정(모집단위 우선 규칙·최저 없음·입력 부족·확인 필요). */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsActivationService.class, AdmissionsJeongsiScoreService.class,
        AdmissionsSusiMinimumService.class, AdmissionsFactSearchService.class, AdmissionsSusiMinimumDbTest.JacksonTestConfig.class})
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
    @Autowired AdmissionsFactSearchService facts;
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

    /** 수학 선택과목 조건이 있는 규칙 하나(교과). */
    private static final String SEED_FLAGS = """
        {"ipsi_universities":[{"name":"테스트한양대학교","university_code":"TST"}],
         "ipsi_documents":[{"document_key":"doc_tst_2027_susi","document_name":"테스트한양대학교 2027학년도 수시모집 요강",
            "applicable_academic_year":2027,"institution_name":"테스트한양대학교 입학처","source_url":"https://example.test/susi.pdf"}],
         "susi_minimums":[
           {"university_code":"TST","academic_year":2027,"rule_key":"tst_kyogwa","rule_kind":"RULE","label":"자연계열",
            "type_names":["학생부교과"],"condition":"(MATH_SEL_CALC == 1 or MATH_SEL_GEOM == 1) and MATH_GRD <= 2",
            "evidence_pages":[20],"raw_text":"수학(미적분/기하) 2등급 이내","uncertain":[],
            "source_document_key":"doc_tst_2027_susi","source_url":"https://example.test/susi.pdf"}]}
        """;

    @Test
    @DisplayName("given 선택과목 조건 규칙 when 선택과목을 안 고르면 then 미충족이 아니라 입력 요청이고, 고르면 그 과목으로 판정한다")
    void flagGroupMissing_needsInput() throws Exception {
        // given
        loader.loadSeed(SEED_FLAGS);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var none = service.check("테스트한양대학교", 2027, "학생부교과", null, Map.of("MATH_GRD", 1.0));
        var prob = service.check("테스트한양대학교", 2027, "학생부교과", null, Map.of("MATH_GRD", 1.0, "MATH_SEL_PROB", 1.0));
        var calc = service.check("테스트한양대학교", 2027, "학생부교과", null, Map.of("MATH_GRD", 1.0, "MATH_SEL_CALC", 1.0));
        // then: 고르지 않으면 0으로 두지 않는다(0이면 충족 학생도 미충족으로 나온다)
        assertThat(none.rules().get(0).outcome()).isEqualTo(Outcome.NEEDS_INPUT);
        assertThat(none.rules().get(0).missingInputs()).containsExactly("MATH_SEL");
        assertThat(prob.rules().get(0).outcome()).isEqualTo(Outcome.NOT_MET);
        assertThat(prob.rules().get(0).defaultedInputs()).containsExactly("MATH_SEL_CALC", "MATH_SEL_GEOM");
        assertThat(calc.rules().get(0).outcome()).isEqualTo(Outcome.MET);
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

    // ── 상담 근거: 수능최저 원문 ─────────────────────────────────────

    private List<AdmissionsResearchService.Evidence> minimumEvidence(String question) {
        return facts.search(question, null).evidence().stream()
                .filter(e -> e.getRefId() != null && e.getRefId().startsWith("susimin:")).toList();
    }

    @Test
    @DisplayName("given 승인된 수능최저 when 전형을 말해 최저 기준을 물으면 then 그 전형 규칙만 원문 문장·응시 조건·근거 쪽으로 나온다")
    void minimumQuestion_returnsRawTextForMentionedType() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var ev = minimumEvidence("테스트한양대학교 학생부종합(추천형) 수능최저 기준 알려줘");
        // then: 추천형 두 규칙(전체·의예과)만, 서류형·논술은 제외
        assertThat(ev).hasSize(2);
        String all = ev.toString();
        assertThat(ev.get(0).getTitle()).contains("테스트한양대학교 2027학년도 수시 수능최저학력기준");
        assertThat(all).contains("3개 영역 등급합 7 이내", "등급합 4 이내", "응시 조건 원문: 국어, 수학, 영어, 탐구(2과목)", "요강 근거: 31쪽")
                .doesNotContain("미적용", "계열별 기준");
        assertThat(ev.get(0).getRefUrl()).isEqualTo("https://example.test/susi.pdf");
    }

    @Test
    @DisplayName("given 최저 없는 전형·확인 필요 규칙 when 최저를 물으면 then '없음'과 '판정하지 않음'을 원문과 함께 밝힌다")
    void noMinimumAndNeedsReview_areStatedWithRawText() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        // when
        var docs = minimumEvidence("테스트한양대학교 서류형 수능최저 있어?");
        var all = minimumEvidence("테스트한양대학교 수능최저 알려줘");
        // then
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getExcerpt()).contains("수능최저학력기준 없음. 원문: 수능최저학력기준 : 미적용 (32쪽)");
        assertThat(all).hasSize(4);
        assertThat(all.toString()).contains("원문 확인 필요: 계열별 모집단위 연결 필요 — 이 규칙으로는 충족 여부를 판정하지 않는다.");
    }

    @Test
    @DisplayName("given 수능최저 규칙 when 최저를 묻지 않거나 아직 승인 전이면 then 최저 근거를 붙이지 않는다")
    void minimumEvidence_onlyWhenAskedAndActive() throws Exception {
        // given: 적재만(DRAFT)
        loader.loadSeed(SEED);
        // when / then
        assertThat(minimumEvidence("테스트한양대학교 학생부종합(추천형) 수능최저 기준 알려줘")).isEmpty();
        activation.activateAll(2027, "ACTIVATE-ALL-2027");
        assertThat(minimumEvidence("테스트한양대학교 학생부종합(추천형) 모집인원 알려줘")).isEmpty();
    }

    @Test
    @DisplayName("given 세부 전형명 when 최저용 전형 매칭을 하면 then '학생부종합'만 맞는 것으로는 다른 세부 전형을 고르지 않는다")
    void minimumTypeMentioned_requiresDetailName() {
        // given / when / then
        assertThat(AdmissionsFactSearchService.minimumTypeMentioned("고려대 학교추천 최저", "학생부교과(학교추천전형)")).isTrue();
        assertThat(AdmissionsFactSearchService.minimumTypeMentioned("학생부종합(추천형) 최저", "학생부종합(서류형)")).isFalse();
        assertThat(AdmissionsFactSearchService.minimumTypeMentioned("학종 최저", "학생부종합")).isTrue();
        assertThat(AdmissionsFactSearchService.minimumTypeMentioned("교과 최저", "학생부교과(고교추천전형)")).isFalse();
    }
}
