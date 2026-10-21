package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 학과·전형·일정 DB 데이터가 상담 검색에 연결되는지 — 승인 게이트, 학년도 구분, 출처 연결. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsFactSearchService.class, AdmissionsActivationService.class,
        AdmissionsActivationServiceDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_activate;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시 자료 전체 ACTIVE 승격")
class AdmissionsActivationServiceDbTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final String SEED = """
        {"ipsi_universities":[{"name":"테스트대학교","university_code":"TST"},{"name":"예시대학교","university_code":"EXA"}],
         "ipsi_documents":[{"document_key":"doc_tst_2027_susi","document_name":"테스트대 2027 수시 모집요강","applicable_academic_year":2027,"institution_name":"테스트대","source_url":"https://example.test/tst"},
                           {"document_key":"doc_exa_2027","document_name":"예시대 2027 모집요강","applicable_academic_year":2027,"institution_name":"예시대","source_url":"https://example.test/exa"},
                           {"document_key":"doc_tst_2028","document_name":"테스트대 2028 시행계획","applicable_academic_year":2028,"institution_name":"테스트대","source_url":"https://example.test/tst2028"}],
         "ipsi_departments":[{"university_code":"TST","academic_year":"2027","department_name":"컴퓨터공학과","counts_by_type":{"학생부교과":19,"논술":11,"합계":30},"source_document_key":"doc_tst_2027_susi"},
                             {"university_code":"EXA","academic_year":"2027","department_name":"컴퓨터공학과","counts_by_type":{"합계":5},"source_document_key":"doc_exa_2027"}],
         "ipsi_admission_types":[{"university_code":"TST","academic_year":2027,"type_name":"학생부교과(지역균형)","recruitment_count":10,"eligibility":"학교장 추천","min_academic_standard":"","source_document_key":"doc_tst_2027_susi"},
                                 {"university_code":"TST","academic_year":2028,"type_name":"학생부교과(지역균형)","recruitment_count":12,"eligibility":"시행계획 방향","source_document_key":"doc_tst_2028"}],
         "ipsi_schedules":[{"university_code":"TST","academic_year":2027,"item":"학교장추천 대상자 입력","type_name":"지역균형","date":"2026.9.14.~9.18.","source_document_key":"doc_tst_2027_susi"}]}
        """;

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsFactSearchService search;
    @Autowired AdmissionsActivationService activation;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsScheduleRepository schedules;

    @BeforeEach
    void load() throws Exception {
        loader.loadSeed(SEED);
    }

    @AfterEach
    void clean() {
        schedules.deleteAll();
        types.deleteAll();
        departments.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    private void approveAll() {
        materials.findAll().forEach(m -> { m.setStatus(KnowledgeStatus.ACTIVE); materials.save(m); });
        departments.findAll().forEach(d -> { d.setStatus(KnowledgeStatus.ACTIVE); departments.save(d); });
        types.findAll().forEach(t -> { t.setStatus(KnowledgeStatus.ACTIVE); types.save(t); });
        schedules.findAll().forEach(s -> { s.setStatus(KnowledgeStatus.ACTIVE); schedules.save(s); });
    }

    @Test
    @DisplayName("given 적재 직후 DRAFT when 2027을 전체 승격하면 then 자료·학과·전형·일정이 ACTIVE가 되고 검색 근거로 쓰인다")
    void activateAll_promotesDraftRowsOfTheYear() {
        // given
        assertThat(search.search("테스트대 학교장추천 입력이 언제까지였죠?", null).evidence()).isEmpty();

        // when
        var r = activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // then
        assertThat(r.materials()).isEqualTo(2);
        assertThat(r.departments()).isEqualTo(2);
        assertThat(r.types()).isEqualTo(1);
        assertThat(r.schedules()).isEqualTo(1);
        assertThat(materials.findAll().stream().filter(m -> m.getAcademicYear() == 2027))
                .allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE));
        assertThat(search.search("테스트대 학교장추천 입력이 언제까지였죠?", null).evidence()).hasSize(1);
    }

    @Test
    @DisplayName("given 2027 승격 when 실행하면 then 다른 학년도(2028) 행은 DRAFT로 남는다")
    void activateAll_leavesOtherYearsUntouched() {
        // when
        activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // then
        assertThat(types.findAll().stream().filter(t -> t.getAcademicYear() == 2028))
                .allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));
        assertThat(materials.findAll().stream().filter(m -> m.getAcademicYear() == 2028))
                .allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));
    }

    @Test
    @DisplayName("given ARCHIVED 행 when 전체 승격하면 then ARCHIVED는 되살리지 않는다")
    void activateAll_doesNotResurrectArchived() {
        // given
        var archived = schedules.findAll().get(0);
        archived.setStatus(KnowledgeStatus.ARCHIVED);
        schedules.save(archived);

        // when
        var r = activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // then
        assertThat(r.schedules()).isZero();
        assertThat(schedules.findById(archived.getScheduleId()).orElseThrow().getStatus()).isEqualTo(KnowledgeStatus.ARCHIVED);
    }

    @Test
    @DisplayName("given 같은 승격을 다시 실행 when 이미 ACTIVE면 then 0건이고 오류가 없다 (멱등)")
    void activateAll_isIdempotent() {
        // given
        activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // when
        var again = activation.activateAll(2027, "ACTIVATE-ALL-2027");

        // then
        assertThat(again.materials() + again.departments() + again.types() + again.schedules()).isZero();
    }

    @Test
    @DisplayName("given 제외 fileKey 지정 when 전체 승격하면 then 그 자료와 하위 학과·전형·일정만 DRAFT로 남고 제외 건수가 구분된다")
    void activateAll_excludesMaterialAndItsChildren() {
        // when
        var r = activation.activateAll(2027, "ACTIVATE-ALL-2027", Set.of("doc_tst_2027_susi"));

        // then — 승격 건수에서 제외분만 빠지고, 제외 건수가 따로 잡힌다
        assertThat(r.materials()).isEqualTo(1);
        assertThat(r.departments()).isEqualTo(1);
        assertThat(r.types()).isZero();
        assertThat(r.schedules()).isZero();
        assertThat(r.excludedMaterials()).isEqualTo(1);
        assertThat(r.excludedChildren()).isEqualTo(3);

        // then — 제외 자료와 그 하위 행은 DRAFT 유지, 나머지는 ACTIVE
        assertThat(materials.findAll().stream().filter(m -> "doc_tst_2027_susi".equals(m.getFileKey())))
                .allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));
        assertThat(materials.findAll().stream().filter(m -> "doc_exa_2027".equals(m.getFileKey())))
                .allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE));
        assertThat(departments.findAll().stream().filter(d -> "doc_tst_2027_susi".equals(d.getSource().getFileKey())))
                .allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));
        assertThat(departments.findAll().stream().filter(d -> "doc_exa_2027".equals(d.getSource().getFileKey())))
                .allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(KnowledgeStatus.ACTIVE));
        assertThat(types.findAll().stream().filter(t -> t.getAcademicYear() == 2027))
                .allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));
        assertThat(schedules.findAll())
                .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));

        // then — 제외된 일정은 검색 근거로 쓰이지 않는다
        assertThat(search.search("테스트대 학교장추천 입력이 언제까지였죠?", null).evidence()).isEmpty();
    }

    @Test
    @DisplayName("given 제외를 지정하지 않음(빈 집합) when 전체 승격하면 then 기존과 동일하게 전부 승격되고 제외 건수는 0이다")
    void activateAll_withEmptyExclusionsPromotesEverything() {
        // when
        var r = activation.activateAll(2027, "ACTIVATE-ALL-2027", Set.of());

        // then
        assertThat(r.materials()).isEqualTo(2);
        assertThat(r.departments()).isEqualTo(2);
        assertThat(r.types()).isEqualTo(1);
        assertThat(r.schedules()).isEqualTo(1);
        assertThat(r.excludedMaterials()).isZero();
        assertThat(r.excludedChildren()).isZero();
    }

    @Test
    @DisplayName("given 잘못된 확인 문자열·미지원 학년도 when 실행하면 then 거부하고 아무것도 바꾸지 않는다")
    void activateAll_rejectsBadConfirmationAndYear() {
        // when / then
        assertThatThrownBy(() -> activation.activateAll(2027, "yes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activation.activateAll(2026, "ACTIVATE-ALL-2026")).isInstanceOf(IllegalArgumentException.class);
        assertThat(materials.findAll()).allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(KnowledgeStatus.DRAFT));
    }
}
