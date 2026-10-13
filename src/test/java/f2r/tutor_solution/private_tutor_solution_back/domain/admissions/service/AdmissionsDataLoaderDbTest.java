package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시드 적재기의 안전성: 정상 적재 · 재실행(멱등) · 부분 실패(전체 롤백) · 재시도 · 미지원 학년도 · 지식베이스.
 * 적재기의 자체 트랜잭션 경계(커밋/롤백)를 검증하려고 테스트 트랜잭션은 끈다(NOT_SUPPORTED).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsDataLoaderDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_loader;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시 시드 적재기 — 멱등성·실패 시 롤백·재시도")
class AdmissionsDataLoaderDbTest {

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsScheduleRepository schedules;
    @Autowired AdmissionsKnowledgeItemRepository knowledge;

    @AfterEach
    void clean() {
        schedules.deleteAll();
        types.deleteAll();
        departments.deleteAll();
        materials.deleteAll();
        knowledge.deleteAll();
        universities.deleteAll();
    }

    private static final String GOOD_SEED = """
        {"ipsi_universities":[{"name":"테스트대","university_code":"TST","region":"서울"}],
         "ipsi_documents":[{"document_key":"doc_tst_2027_susi","document_name":"테스트대 2027 수시","applicable_academic_year":2027,"institution_name":"테스트대"}],
         "ipsi_departments":[{"university_code":"TST","academic_year":"2027","department_name":"컴퓨터공학과","counts_by_type":{"a":1}},
                             {"university_code":"TST","academic_year":"2027","department_name":"수학과","counts_by_type":{"a":2}}],
         "ipsi_admission_types":[{"university_code":"TST","academic_year":2027,"type_name":"학생부교과","recruitment_count":10}],
         "ipsi_schedules":[{"university_code":"TST","academic_year":"2027","item":"원서접수","type_name":"수시","date":"2026-09-10","source_document_key":"doc_tst_2027_susi"}]}
        """;

    @Test
    @DisplayName("given 정상 시드 when 적재하면 then 대학·자료·학과·전형·일정이 저장되고 일정은 출처 자료와 연결된다")
    void loads_valid_seed() throws Exception {
        // given / when
        var result = loader.loadSeed(GOOD_SEED);

        // then
        assertThat(result.universities()).isEqualTo(1);
        assertThat(result.materials()).isEqualTo(1);
        assertThat(result.departments()).isEqualTo(2);
        assertThat(result.types()).isEqualTo(1);
        assertThat(result.schedules()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(schedules.findAll()).extracting(AdmissionsSchedule::getSource).doesNotContainNull();
    }

    @Test
    @DisplayName("given 이미 적재한 시드 when 다시 적재하면 then 행이 늘지 않고 일정의 출처 연결도 유지된다")
    void rerun_is_idempotent() throws Exception {
        // given
        loader.loadSeed(GOOD_SEED);

        // when
        var second = loader.loadSeed(GOOD_SEED);

        // then
        assertThat(second.universities() + second.materials() + second.departments() + second.types() + second.schedules()).isZero();
        assertThat(universities.count()).isEqualTo(1);
        assertThat(materials.count()).isEqualTo(1);
        assertThat(departments.count()).isEqualTo(2);
        assertThat(types.count()).isEqualTo(1);
        assertThat(schedules.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("given 알 수 없는 대학 코드가 섞인 시드 when 적재하면 then 예외가 나고 아무것도 저장되지 않는다(전체 롤백)")
    void partial_failure_rolls_back_everything() {
        // given
        String bad = GOOD_SEED.replace("\"ipsi_admission_types\":[{\"university_code\":\"TST\"",
                "\"ipsi_admission_types\":[{\"university_code\":\"NOPE\"");

        // when / then
        assertThatThrownBy(() -> loader.loadSeed(bad))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class)
                .hasMessageContaining("NOPE");
        assertThat(universities.count()).isZero();
        assertThat(materials.count()).isZero();
        assertThat(departments.count()).isZero();
        assertThat(types.count()).isZero();
        assertThat(schedules.count()).isZero();
    }

    @Test
    @DisplayName("given 실패한 적재 when 시드를 고쳐 재시도하면 then 정상 적재된다")
    void retry_after_failure_succeeds() throws Exception {
        // given
        String bad = GOOD_SEED.replace("\"university_code\":\"TST\",\"academic_year\":2027,\"type_name\"",
                "\"university_code\":\"NOPE\",\"academic_year\":2027,\"type_name\"");
        assertThatThrownBy(() -> loader.loadSeed(bad)).isInstanceOf(AdmissionsDataLoader.SeedLoadException.class);

        // when
        var result = loader.loadSeed(GOOD_SEED);

        // then
        assertThat(result.departments()).isEqualTo(2);
        assertThat(departments.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("given 대학 목록이 없는 시드 when 적재하면 then 성공으로 처리하지 않고 거부한다")
    void rejects_seed_without_universities() {
        // given / when / then
        assertThatThrownBy(() -> loader.loadSeed("{\"ipsi_documents\":[]}"))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class);
        assertThatThrownBy(() -> loader.loadSeed("{\"ipsi_universities\":[]}"))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class);
    }

    @Test
    @DisplayName("given 2029·학년도 누락 행 when 적재하면 then 2027로 바꾸지 않고 건너뛰며 skipped로 보고한다")
    void unsupported_or_missing_years_are_skipped_not_coerced() throws Exception {
        // given
        String seed = GOOD_SEED
                .replace("\"applicable_academic_year\":2027", "\"applicable_academic_year\":2029")
                .replace("\"academic_year\":\"2027\",\"department_name\":\"수학과\"", "\"department_name\":\"수학과\"");

        // when
        var result = loader.loadSeed(seed);

        // then: 자료(2029) 1건 + 학년도 없는 학과 1건 + 자료가 없어도 일정은 적재(연도 2027)
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(materials.count()).isZero();
        assertThat(departments.findAll()).extracting(d -> d.getName()).containsExactly("컴퓨터공학과");
        assertThat(departments.findAll()).allSatisfy(d -> assertThat(d.getAcademicYear()).isEqualTo(2027));
    }

    @Test
    @DisplayName("given question_types 배열 when 지식베이스를 적재하면 then 전부 DRAFT이고 재실행해도 늘지 않는다")
    void knowledge_base_is_draft_and_idempotent() throws Exception {
        // given
        String kb = "{\"question_types\":[{\"id\":\"Q1\",\"question\":\"a\",\"answer_basis\":\"b\"},{\"id\":\"Q2\",\"question\":\"c\"}]}";

        // when
        int first = loader.loadKnowledgeBase(kb);
        int second = loader.loadKnowledgeBase(kb);

        // then
        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        assertThat(knowledge.findAll()).allSatisfy(k -> assertThat(k.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT));
    }

    @Test
    @DisplayName("given 적재할 항목이 없는 지식베이스 when 적재하면 then 0건 성공이 아니라 거부한다")
    void empty_knowledge_base_is_rejected() {
        // given / when / then
        assertThatThrownBy(() -> loader.loadKnowledgeBase("{\"question_types\":[]}"))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class);
        assertThatThrownBy(() -> loader.loadKnowledgeBase("{\"other\":1}"))
                .isInstanceOf(AdmissionsDataLoader.SeedLoadException.class);
    }
}
