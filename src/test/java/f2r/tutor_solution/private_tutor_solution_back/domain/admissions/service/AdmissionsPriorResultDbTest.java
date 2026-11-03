package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsPriorResultRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 지난해(2026학년도) 입시결과: 적재 멱등성·변경 시 DRAFT 복귀·승격·지식 생성·상담 검색 연결. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsFactSearchService.class, AdmissionsActivationService.class,
        AdmissionsKnowledgeBuildService.class, AdmissionsPriorResultDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_prior;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시상담 — 지난해 입시결과 적재·승격·검색")
class AdmissionsPriorResultDbTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 테스트대: 교과등급 70%컷, 예시대: 환산점수 평균 — 서로 다른 지표. */
    private static final String SEED = """
        {"ipsi_universities":[{"name":"테스트대학교","university_code":"TST"},{"name":"예시대학교","university_code":"EXA"}],
         "ipsi_documents":[
           {"document_key":"doc_tst_2027_susi","document_name":"테스트대 2027 수시 모집요강","applicable_academic_year":2027,"institution_name":"테스트대","source_url":"https://example.test/tst"},
           {"document_key":"doc_tst_2026_susi_result","document_name":"테스트대학교 2026학년도 수시 입시결과","applicable_academic_year":2027,"institution_name":"테스트대학교 입학처","source_url":"https://example.test/tst-result.pdf"},
           {"document_key":"doc_exa_2026_susi_result","document_name":"예시대학교 2026학년도 수시 입시결과","applicable_academic_year":2027,"institution_name":"예시대학교 입학처","source_url":"https://example.test/exa-result.pdf"}],
         "ipsi_admission_types":[{"university_code":"TST","academic_year":2027,"type_name":"학생부교과(지역균형)","recruitment_count":10,"source_document_key":"doc_tst_2027_susi"}],
         "prior_results":[
           {"university_code":"TST","academic_year":2027,"result_year":2026,"round_type":"SUSI","type_name":"학생부교과(지역균형)","department_name":"컴퓨터공학과",
            "metric_label":null,"recruit_count":10,"applicant_count":125,"competition_rate":12.5,"extra_admitted_count":7,
            "source_document_key":"doc_tst_2026_susi_result","source_url":"https://example.test/tst-result.pdf","institution_name":"테스트대학교 입학처","published_at":"2026-04-20","evidence_pages":[3]},
           {"university_code":"TST","academic_year":2027,"result_year":2026,"round_type":"SUSI","type_name":"학생부교과(지역균형)","department_name":"컴퓨터공학과",
            "metric_label":"최종등록자 교과등급 70%컷","metric_unit":"GRADE","metric_value":2.31,"raw_value_text":"2.31",
            "source_document_key":"doc_tst_2026_susi_result","source_url":"https://example.test/tst-result.pdf","institution_name":"테스트대학교 입학처","published_at":"2026-04-20","evidence_pages":[3]},
           {"university_code":"EXA","academic_year":2027,"result_year":2026,"round_type":"SUSI","type_name":"논술전형","department_name":"컴퓨터공학과",
            "metric_label":"최종등록자 논술 환산점수 평균","metric_unit":"SCORE","metric_value":81.4,"raw_value_text":"81.4",
            "source_document_key":"doc_exa_2026_susi_result","source_url":"https://example.test/exa-result.pdf","institution_name":"예시대학교 입학처","published_at":null,"evidence_pages":[5]}]}
        """;

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsFactSearchService search;
    @Autowired AdmissionsActivationService activation;
    @Autowired AdmissionsKnowledgeBuildService knowledgeBuild;
    @Autowired AdmissionsKnowledgeItemRepository knowledgeItems;
    @Autowired AdmissionsPriorResultRepository priorResults;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsScheduleRepository schedules;

    @AfterEach
    void clean() {
        knowledgeItems.deleteAll();
        priorResults.deleteAll();
        schedules.deleteAll();
        types.deleteAll();
        departments.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    @Test
    @DisplayName("given 같은 시드 when 두 번 적재하면 then 입시결과 행이 늘지 않고 모두 DRAFT다")
    void reload_isIdempotent() throws Exception {
        // given
        var first = loader.loadSeed(SEED);

        // when
        var second = loader.loadSeed(SEED);

        // then
        assertThat(first.priorResults()).isEqualTo(3);
        assertThat(second.priorResults()).isZero();
        assertThat(second.priorResultsUpdated()).isZero();
        assertThat(priorResults.findAll()).hasSize(3)
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(KnowledgeStatus.DRAFT))
                .allSatisfy(r -> assertThat(r.getSource()).isNotNull());
    }

    @Test
    @DisplayName("given 승인된 입시결과 when 원문 값이 바뀐 시드를 다시 적재하면 then 그 행만 값이 바뀌고 DRAFT로 돌아간다")
    void changedRow_isUpdatedAndResetToDraft() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // when
        var r = loader.loadSeed(SEED.replace("\"metric_value\":2.31,\"raw_value_text\":\"2.31\"",
                "\"metric_value\":2.35,\"raw_value_text\":\"2.35\""));

        // then
        assertThat(r.priorResults()).isZero();
        assertThat(r.priorResultsUpdated()).isEqualTo(1);
        List<AdmissionsPriorResult> rows = priorResults.findAll();
        assertThat(rows).hasSize(3);
        AdmissionsPriorResult changed = rows.stream().filter(x -> "최종등록자 교과등급 70%컷".equals(x.getMetricLabel())).findFirst().orElseThrow();
        assertThat(changed.getMetricValue()).isEqualByComparingTo(new BigDecimal("2.35"));
        assertThat(changed.getStatus()).isEqualTo(KnowledgeStatus.DRAFT);
        assertThat(rows.stream().filter(x -> x != changed && !x.getPriorResultId().equals(changed.getPriorResultId())))
                .allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE));
    }

    @Test
    @DisplayName("given DRAFT 입시결과 when 2027을 전체 승격하면 then ACTIVE가 되고 결과 수를 돌려준다")
    void activateAll_promotesPriorResults() throws Exception {
        // given
        loader.loadSeed(SEED);

        // when
        var r = activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // then
        assertThat(r.priorResults()).isEqualTo(3);
        assertThat(priorResults.findAll()).allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE));
    }

    @Test
    @DisplayName("given DRAFT 입시결과 when 경쟁률을 물으면 then 승인 전이라 근거로 쓰지 않는다")
    void draftRows_areNotSearched() throws Exception {
        // given
        loader.loadSeed(SEED);

        // when
        var result = search.search("테스트대 컴퓨터공학과 경쟁률 알려줘", null);

        // then
        assertThat(result.evidence()).noneMatch(e -> e.getSourceClass() == SourceClass.OFFICIAL_PRIOR_RESULT);
    }

    @Test
    @DisplayName("given 승인된 입시결과 when 그 대학 경쟁률을 물으면 then 지난해 표시·원문 지표명·출처가 붙은 근거가 나온다")
    void activeRows_becomePriorEvidence() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // when
        var result = search.search("테스트대 컴퓨터공학과 경쟁률이랑 입결 알려줘", null);

        // then
        var prior = result.evidence().stream().filter(e -> e.getSourceClass() == SourceClass.OFFICIAL_PRIOR_RESULT).toList();
        assertThat(prior).hasSize(1);
        assertThat(prior.get(0).getTitle()).contains("테스트대학교", "2026학년도(지난해)", "수시", "학생부교과(지역균형)", "컴퓨터공학과");
        assertThat(prior.get(0).getExcerpt()).contains("모집인원 10명", "지원자 125명", "경쟁률 12.5:1", "충원(추가합격) 7명",
                "최종등록자 교과등급 70%컷 = 2.31");
        assertThat(prior.get(0).getRefUrl()).isEqualTo("https://example.test/tst-result.pdf");
        assertThat(prior.get(0).getRefId()).startsWith("prior:");
    }

    @Test
    @DisplayName("given 승인된 입시결과 when 다른 대학 경쟁률을 물으면 then 그 대학 결과만 나오고 다른 대학 지표를 섞지 않는다")
    void otherUniversity_isNotMixed() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // when
        var result = search.search("예시대 논술 입결 알려줘", null);

        // then
        var prior = result.evidence().stream().filter(e -> e.getSourceClass() == SourceClass.OFFICIAL_PRIOR_RESULT).toList();
        assertThat(prior).hasSize(1);
        assertThat(prior.get(0).getExcerpt()).contains("최종등록자 논술 환산점수 평균 = 81.4")
                .doesNotContain("교과등급 70%컷");
    }

    @Test
    @DisplayName("given 입시결과 적재 후 when 그 대학 모집요강 시드를 최신본(authoritative)으로 다시 적재하면 then 입시결과 출처 자료는 보관되지 않는다")
    void authoritativeReload_keepsPriorSourceMaterials() throws Exception {
        // given
        loader.loadSeed(SEED);
        String guideOnly = """
            {"ipsi_universities":[{"name":"테스트대학교","university_code":"TST"}],
             "authoritative_universities":["TST"],
             "ipsi_documents":[{"document_key":"doc_tst_2027_susi","document_name":"테스트대 2027 수시 모집요강","applicable_academic_year":2027,"institution_name":"테스트대","source_url":"https://example.test/tst"}],
             "ipsi_admission_types":[{"university_code":"TST","academic_year":2027,"type_name":"학생부교과(지역균형)","recruitment_count":10,"source_document_key":"doc_tst_2027_susi"}]}
            """;

        // when
        loader.loadSeed(guideOnly);

        // then
        assertThat(materials.findAll().stream().filter(m -> m.getTitle().contains("2026학년도 수시 입시결과")))
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.getStatus()).isNotEqualTo(KnowledgeStatus.ARCHIVED));
    }

    @Test
    @DisplayName("given 승인된 입시결과 when 지식을 만들면 then prior: 항목은 만들지 않는다(상담은 원본 행을 직접 조회)")
    void knowledgeBuild_doesNotCreatePriorItems() throws Exception {
        // given
        loader.loadSeed(SEED);
        activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // when
        knowledgeBuild.buildAll();

        // then
        assertThat(knowledgeItems.findAll()).noneSatisfy(k -> assertThat(k.getDedupKey()).startsWith("prior:"));
    }

    @Test
    @DisplayName("given 테스트대 입시결과 자료를 승격 제외 when 2027을 전체 승격하면 then 그 자료의 입시결과만 DRAFT로 남고 제외 하위 행으로 센다")
    void activation_excludedFileKeepsPriorRowsDraft() throws Exception {
        // given
        loader.loadSeed(SEED);

        // when
        var r = activation.activateAll(2027, "ACTIVATE-ALL-2027", java.util.Set.of("doc_tst_2026_susi_result"));

        // then: 테스트대 2행은 제외, 예시대 1행만 승격
        assertThat(r.priorResults()).isEqualTo(1);
        assertThat(r.excludedChildren()).isGreaterThanOrEqualTo(2);
        assertThat(priorResults.findAll()).filteredOn(x -> x.getStatus() == KnowledgeStatus.ACTIVE).hasSize(1);
        assertThat(priorResults.findAll()).filteredOn(x -> x.getStatus() == KnowledgeStatus.DRAFT).hasSize(2);
    }

    @Test
    @DisplayName("given ACTIVE prior: 지식 항목 when 재색인 대상 id를 조회하면 then prior: 항목은 빠지고 일반 항목은 남는다")
    void embeddingTargets_excludePriorItems() {
        // given
        var common = AdmissionsKnowledgeItem.builder().academicYear(2027).yearConfirmed(true).title("일반").content("본문")
                .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL).status(KnowledgeStatus.ACTIVE).dedupKey("type:1:2027:1").build();
        var prior = AdmissionsKnowledgeItem.builder().academicYear(2027).yearConfirmed(true).title("입시결과").content("본문")
                .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL).status(KnowledgeStatus.ACTIVE).dedupKey("prior:1:2027:9").build();
        Long commonId = knowledgeItems.save(common).getKnowledgeId();
        knowledgeItems.save(prior);

        // when
        var active = knowledgeItems.findIdsByStatus(KnowledgeStatus.ACTIVE);
        var all = knowledgeItems.findAllIds();

        // then
        assertThat(active).containsExactly(commonId);
        assertThat(all).containsExactly(commonId);
    }
}
