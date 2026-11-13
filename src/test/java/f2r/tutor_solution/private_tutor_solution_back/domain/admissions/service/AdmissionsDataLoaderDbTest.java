package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
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
         "ipsi_departments":[{"university_code":"TST","academic_year":"2027","department_name":"컴퓨터공학과","counts_by_type":{"a":1},"source_document_key":"doc_tst_2027_susi"},
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
        // 학과는 출처 자료와 연결되고, 모든 행은 승인 전(DRAFT)으로 적재된다
        assertThat(departments.findAll().stream().filter(d -> "컴퓨터공학과".equals(d.getName())).findFirst().orElseThrow().getSource()).isNotNull();
        assertThat(departments.findAll()).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT));
        assertThat(materials.findAll()).allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT));
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
    @DisplayName("given 승인된 학과·전형 when 인원·지원자격이 보완된 시드를 다시 적재하면 then 새 행 없이 내용이 갱신되고 DRAFT로 돌아간다")
    void reload_with_changed_content_updates_rows_and_resets_to_draft() throws Exception {
        // given
        loader.loadSeed(GOOD_SEED);
        departments.findAll().forEach(d -> { d.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); departments.save(d); });
        types.findAll().forEach(t -> { t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); types.save(t); });
        String filled = GOOD_SEED
                .replace("\"department_name\":\"수학과\",\"counts_by_type\":{\"a\":2}",
                         "\"department_name\":\"수학과\",\"counts_by_type\":{\"a\":2,\"정시 일반전형(나군)\":5}")
                .replace("\"type_name\":\"학생부교과\",\"recruitment_count\":10",
                         "\"type_name\":\"학생부교과\",\"recruitment_count\":10,\"eligibility\":\"고교 졸업(예정)자\"");

        // when
        var result = loader.loadSeed(filled);

        // then
        assertThat(result.departments() + result.types()).isZero();
        assertThat(result.departmentsUpdated()).isEqualTo(1);
        assertThat(result.typesUpdated()).isEqualTo(1);
        assertThat(departments.count()).isEqualTo(2);
        assertThat(types.count()).isEqualTo(1);
        var math = departments.findAll().stream().filter(d -> "수학과".equals(d.getName())).findFirst().orElseThrow();
        assertThat(math.getQuotaInfo()).contains("정시 일반전형(나군)");
        assertThat(math.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
        var cs = departments.findAll().stream().filter(d -> "컴퓨터공학과".equals(d.getName())).findFirst().orElseThrow();
        assertThat(cs.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
        var type = types.findAll().get(0);
        assertThat(type.getDetails()).contains("지원자격: 고교 졸업(예정)자");
        assertThat(type.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
    }

    @Test
    @DisplayName("given 승인된 행 when 같은 시드를 다시 적재하면 then 갱신 0건이고 승인 상태가 유지된다")
    void reload_with_same_content_keeps_approved_status() throws Exception {
        // given
        loader.loadSeed(GOOD_SEED);
        types.findAll().forEach(t -> { t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); types.save(t); });

        // when
        var result = loader.loadSeed(GOOD_SEED);

        // then
        assertThat(result.departmentsUpdated() + result.typesUpdated()).isZero();
        assertThat(types.findAll()).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE));
    }

    @Test
    @DisplayName("given 연세대 자료 키(doc_yon_·doc_yonsei_) when 적재하면 then 자료가 시드 코드 YONSEI의 대학에 연결된다")
    void yonsei_document_keys_link_to_university() throws Exception {
        // given
        String seed = """
            {"ipsi_universities":[{"name":"연세대학교","university_code":"YONSEI"}],
             "ipsi_documents":[{"document_key":"doc_yon_2027_susi","document_name":"연세 2027 수시","applicable_academic_year":2027},
                               {"document_key":"doc_yonsei_2027_jeongsi","document_name":"연세 2027 정시","applicable_academic_year":2027}]}
            """;

        // when
        loader.loadSeed(seed);

        // then
        assertThat(materials.findAll()).hasSize(2)
                .allSatisfy(m -> assertThat(m.getUniversity()).isNotNull());
    }

    @Test
    @DisplayName("given 원문과 다른 기존 일정 when archive_schedules에 적어 적재하면 then 그 일정과 지식 항목이 ARCHIVED가 되고 다른 일정은 그대로다")
    void archive_schedules_retires_listed_rows_and_their_knowledge() throws Exception {
        // given
        loader.loadSeed(GOOD_SEED);
        var wrong = schedules.findAll().get(0);
        wrong.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
        schedules.save(wrong);
        knowledge.save(AdmissionsKnowledgeItem.builder()
                .academicYear(2027).title("테스트대 2027학년도 일정: 원서접수 (수시)").content("원서접수 2026-09-10").sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                .dedupKey("schedule:" + wrong.getUniversity().getUniversityId() + ":2027:" + wrong.getScheduleId())
                .status(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE).build());
        String fix = GOOD_SEED.replaceFirst("\\}\\s*$",
                ",\"ipsi_schedules\":[{\"university_code\":\"TST\",\"academic_year\":\"2027\",\"item\":\"원서접수\",\"type_name\":\"수시\",\"date\":\"2026-09-11\"}],"
                + "\"archive_schedules\":[{\"university_code\":\"TST\",\"academic_year\":2027,\"title\":\"원서접수 (수시)\",\"note\":\"2026-09-10\"}]}")
                .replaceFirst("\"ipsi_schedules\":\\[\\{[^\\]]*2026-09-10[^\\]]*\\],", "");

        // when
        var result = loader.loadSeed(fix);

        // then
        assertThat(result.schedulesArchived()).isEqualTo(1);
        assertThat(result.schedules()).isEqualTo(1);
        assertThat(schedules.findById(wrong.getScheduleId()).orElseThrow().getStatus())
                .isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
        assertThat(knowledge.findAll()).singleElement()
                .satisfies(k -> assertThat(k.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED));
        assertThat(schedules.findAll()).filteredOn(sc -> "2026-09-11".equals(sc.getNote())).singleElement()
                .satisfies(sc -> assertThat(sc.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT));
    }

    @Test
    @DisplayName("given 괄호 없는 이름의 예전 대학 자료 when 같은 대학을 authoritative로 적재하면 then 예전 서울 행·지식·자료는 보관되고 세종캠퍼스 행과 새 행은 남는다")
    void authoritative_university_archives_superseded_rows_but_keeps_other_campus() throws Exception {
        // given: 예전 수집본(대학명 '가대학교', 서울 전형·일정 + 세종캠퍼스 전형)
        loader.loadSeed("""
            {"ipsi_universities":[{"name":"가대학교","university_code":"GA"}],
             "ipsi_documents":[{"document_key":"doc_ga_old","document_name":"가대 2027 수시(예전 수집본)","applicable_academic_year":2027},
                               {"document_key":"doc_ga_sj","document_name":"가대 세종캠퍼스 2027 수시","applicable_academic_year":2027}],
             "ipsi_departments":[{"university_code":"GA","academic_year":"2027","department_name":"국문과","counts_by_type":{"a":1},"source_document_key":"doc_ga_old"}],
             "ipsi_admission_types":[{"university_code":"GA","academic_year":2027,"type_name":"학교추천","recruitment_count":414,"source_document_key":"doc_ga_old"},
                                     {"university_code":"GA","academic_year":2027,"type_name":"[세종캠퍼스] 논술","recruitment_count":50,"source_document_key":"doc_ga_sj"}],
             "ipsi_schedules":[{"university_code":"GA","academic_year":2027,"item":"원서접수","type_name":"수시","date":"2026.9.7.","source_document_key":"doc_ga_old"}]}
            """);
        types.findAll().forEach(t -> { t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); types.save(t); });
        var oldType = types.findAll().stream().filter(t -> "학교추천".equals(t.getName())).findFirst().orElseThrow();
        knowledge.save(AdmissionsKnowledgeItem.builder().academicYear(2027).title("가대학교 2027학년도 학교추천").content("모집인원 414")
                .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                .dedupKey("type:" + oldType.getUniversity().getUniversityId() + ":2027:" + oldType.getAdmissionTypeId())
                .status(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE).build());

        // when: 공식 원문 시드(대학명 '가대학교(서울)')를 최신본으로 적재
        var result = loader.loadSeed("""
            {"ipsi_universities":[{"name":"가대학교(서울)","university_code":"GA"}],
             "ipsi_documents":[{"document_key":"doc_ga_2027_susi","document_name":"가대 2027 수시 모집요강","applicable_academic_year":2027}],
             "ipsi_admission_types":[{"university_code":"GA","academic_year":2027,"type_name":"학교추천전형","recruitment_count":523,"source_document_key":"doc_ga_2027_susi"}],
             "ipsi_schedules":[{"university_code":"GA","academic_year":2027,"item":"원서접수","type_name":"전 전형","date":"2026.9.8.","source_document_key":"doc_ga_2027_susi"}],
             "authoritative_universities":["GA"]}
            """);

        // then
        var A = AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED;
        assertThat(result.supersededArchived()).isEqualTo(4); // 예전 전형·학과·일정·예전 자료
        assertThat(types.findAll()).filteredOn(t -> "학교추천".equals(t.getName())).singleElement().satisfies(t -> assertThat(t.getStatus()).isEqualTo(A));
        assertThat(types.findAll()).filteredOn(t -> t.getName().contains("세종캠퍼스")).singleElement().satisfies(t -> assertThat(t.getStatus()).isNotEqualTo(A));
        assertThat(types.findAll()).filteredOn(t -> "학교추천전형".equals(t.getName())).singleElement().satisfies(t -> assertThat(t.getStatus()).isNotEqualTo(A));
        assertThat(departments.findAll()).singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(A));
        assertThat(schedules.findAll()).filteredOn(sc -> "2026.9.7.".equals(sc.getNote())).singleElement().satisfies(sc -> assertThat(sc.getStatus()).isEqualTo(A));
        assertThat(schedules.findAll()).filteredOn(sc -> "2026.9.8.".equals(sc.getNote())).singleElement().satisfies(sc -> assertThat(sc.getStatus()).isNotEqualTo(A));
        assertThat(knowledge.findAll()).singleElement().satisfies(k -> assertThat(k.getStatus()).isEqualTo(A));
        assertThat(materials.findAll()).filteredOn(m -> m.getTitle().contains("예전 수집본")).singleElement().satisfies(m -> assertThat(m.getStatus()).isEqualTo(A));
        assertThat(materials.findAll()).filteredOn(m -> m.getTitle().contains("세종캠퍼스")).singleElement().satisfies(m -> assertThat(m.getStatus()).isNotEqualTo(A));
    }

    @Test
    @DisplayName("given 본교와 분교(ERICA) 자료 when 분교를 authoritative로 다시 적재하면 then 분교 예전 행만 보관되고 본교 행은 남는다")
    void authoritative_branch_campus_does_not_archive_main_campus() throws Exception {
        // given: 본교 '나대학교(서울)'와 분교 '나대학교(ERICA)'가 각각 적재돼 있다
        loader.loadSeed("""
            {"ipsi_universities":[{"name":"나대학교(서울)","university_code":"NA"},{"name":"나대학교(ERICA)","university_code":"NAERICA"}],
             "ipsi_documents":[{"document_key":"doc_na","document_name":"나대 2027 수시 모집요강","applicable_academic_year":2027},
                               {"document_key":"doc_naerica_old","document_name":"나대 ERICA 2027 수시(예전)","applicable_academic_year":2027}],
             "ipsi_admission_types":[{"university_code":"NA","academic_year":2027,"type_name":"학생부교과","recruitment_count":300,"source_document_key":"doc_na"},
                                     {"university_code":"NAERICA","academic_year":2027,"type_name":"교과(예전)","recruitment_count":100,"source_document_key":"doc_naerica_old"}],
             "ipsi_schedules":[{"university_code":"NA","academic_year":2027,"item":"원서접수","type_name":"전 전형","date":"2026.9.8.","source_document_key":"doc_na"}]}
            """);

        // when: 분교 최신본만 authoritative로 적재
        var result = loader.loadSeed("""
            {"ipsi_universities":[{"name":"나대학교(ERICA)","university_code":"NAERICA"}],
             "ipsi_documents":[{"document_key":"doc_naerica","document_name":"나대 ERICA 2027 수시 모집요강","applicable_academic_year":2027}],
             "ipsi_admission_types":[{"university_code":"NAERICA","academic_year":2027,"type_name":"학생부교과","recruitment_count":120,"source_document_key":"doc_naerica"}],
             "authoritative_universities":["NAERICA"]}
            """);

        // then: 분교 예전 전형·자료만 보관, 본교 전형·일정·자료는 그대로
        var A = AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED;
        assertThat(result.supersededArchived()).isEqualTo(2);
        assertThat(types.findAll()).filteredOn(t -> "교과(예전)".equals(t.getName())).singleElement().satisfies(t -> assertThat(t.getStatus()).isEqualTo(A));
        // 본교 '학생부교과'와 분교 새 '학생부교과' 두 행 모두 남아 있다
        assertThat(types.findAll()).filteredOn(t -> "학생부교과".equals(t.getName())).hasSize(2)
                .allSatisfy(t -> assertThat(t.getStatus()).isNotEqualTo(A));
        assertThat(schedules.findAll()).singleElement().satisfies(sc -> assertThat(sc.getStatus()).isNotEqualTo(A));
        assertThat(materials.findAll()).filteredOn(m -> "나대 2027 수시 모집요강".equals(m.getTitle())).singleElement().satisfies(m -> assertThat(m.getStatus()).isNotEqualTo(A));
    }

    @Test
    @DisplayName("given 제목이 같은 두 대학 자료 when 적재하면 then 각 대학 행은 자기 대학 자료에 연결된다")
    void same_document_title_in_two_universities_links_each_to_its_own() throws Exception {
        // given / when: 두 대학 자료 제목이 똑같이 "2027학년도 수시모집 모집요강"
        loader.loadSeed("""
            {"ipsi_universities":[{"name":"다대학교","university_code":"DA"},{"name":"라대학교","university_code":"RA"}],
             "ipsi_documents":[{"document_key":"doc_da_2027_susi","document_name":"2027학년도 수시모집 모집요강","applicable_academic_year":2027,"source_url":"https://da.example/"},
                               {"document_key":"doc_ra_2027_susi","document_name":"2027학년도 수시모집 모집요강","applicable_academic_year":2027,"source_url":"https://ra.example/"}],
             "ipsi_admission_types":[{"university_code":"DA","academic_year":2027,"type_name":"교과","recruitment_count":10,"source_document_key":"doc_da_2027_susi"},
                                     {"university_code":"RA","academic_year":2027,"type_name":"교과","recruitment_count":20,"source_document_key":"doc_ra_2027_susi"}]}
            """);

        // then: 자료 2건, 전형마다 자기 대학 자료(URL)
        assertThat(materials.findAll()).hasSize(2);
        var urlById = new java.util.HashMap<Long, String>();
        materials.findAll().forEach(m -> urlById.put(m.getMaterialId(), m.getDocumentUrl()));
        assertThat(types.findAll()).hasSize(2).allSatisfy(t ->
                assertThat(urlById.get(t.getSource().getMaterialId()))
                        .isEqualTo(t.getDetails().contains("모집인원: 10") ? "https://da.example/" : "https://ra.example/"));
    }

    @Test
    @DisplayName("given 다른 자료에 잘못 연결된 승인 행 when 같은 내용 시드를 다시 적재하면 then 시드의 자료로 다시 연결되고 DRAFT가 된다")
    void row_linked_to_wrong_material_is_relinked_on_reload() throws Exception {
        // given: 전형·일정이 엉뚱한 자료(wrong)에 연결된 채 승인돼 있다
        String seed = """
            {"ipsi_universities":[{"name":"마대학교","university_code":"MA"}],
             "ipsi_documents":[{"document_key":"doc_ma_2027_susi","document_name":"마대학교 2027학년도 수시 모집요강","applicable_academic_year":2027}],
             "ipsi_admission_types":[{"university_code":"MA","academic_year":2027,"type_name":"교과","recruitment_count":10,"source_document_key":"doc_ma_2027_susi"}],
             "ipsi_schedules":[{"university_code":"MA","academic_year":2027,"item":"원서접수","type_name":"전 전형","date":"2026.9.7.","source_document_key":"doc_ma_2027_susi"}]}
            """;
        loader.loadSeed(seed);
        var right = materials.findAll().get(0);
        var wrong = materials.save(AdmissionsOfficialMaterial.builder().university(right.getUniversity()).academicYear(2027)
                .title("엉뚱한 자료").issuer("x").build());
        types.findAll().forEach(t -> { t.setSource(wrong); t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); types.save(t); });
        schedules.findAll().forEach(sc -> { sc.setSource(wrong); sc.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); schedules.save(sc); });

        // when
        var result = loader.loadSeed(seed);

        // then
        assertThat(result.typesUpdated()).isEqualTo(1);
        assertThat(types.findAll()).singleElement().satisfies(t -> {
            assertThat(t.getSource().getMaterialId()).isEqualTo(right.getMaterialId());
            assertThat(t.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
        });
        assertThat(schedules.findAll()).singleElement().satisfies(sc ->
                assertThat(sc.getSource().getMaterialId()).isEqualTo(right.getMaterialId()));
    }

    @Test
    @DisplayName("given 보관(ARCHIVED)된 같은 대학·같은 제목 자료 when 시드가 그 자료를 다시 쓰면 then 자료가 DRAFT로 되살아난다")
    void archived_material_reused_by_seed_is_revived() throws Exception {
        // given
        String seed = """
            {"ipsi_universities":[{"name":"바대학교","university_code":"BA"}],
             "ipsi_documents":[{"document_key":"doc_ba_2027_jeongsi","document_name":"바대학교 2027학년도 정시모집요강","applicable_academic_year":2027}],
             "ipsi_admission_types":[{"university_code":"BA","academic_year":2027,"type_name":"정시 일반(나군)","recruitment_count":30,"source_document_key":"doc_ba_2027_jeongsi"}]}
            """;
        loader.loadSeed(seed);
        materials.findAll().forEach(m -> { m.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED); materials.save(m); });

        // when
        var result = loader.loadSeed(seed);

        // then
        assertThat(result.materials()).isEqualTo(1);
        assertThat(materials.findAll()).singleElement().satisfies(m ->
                assertThat(m.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT));
    }

    @Test
    @DisplayName("given youtube_knowledge가 있는 시드 when 적재·재적재하면 then YOUTUBE 지식 항목(DRAFT)이 생기고 내용이 바뀐 것만 갱신된다")
    void youtube_knowledge_is_loaded_as_youtube_items() throws Exception {
        // given
        String seed = """
            {"ipsi_universities":[{"name":"사대학교","university_code":"SA"}],
             "youtube_knowledge":[
               {"id":"YT-A001","academic_year":"2028","title":"[전문가 해석] 5등급제","content":"주장: 학생부 중심","tags":"내신 5등급","source_url":"https://www.youtube.com/watch?v=a&t=60"},
               {"id":"YT-A002","academic_year":"2028","title":"[전략] 수능","content":"주장: 최저 대비"},
               {"id":"YT-A003","academic_year":"2026","title":"지원 안 하는 학년도","content":"x"}]}
            """;

        // when
        var first = loader.loadSeed(seed);
        var second = loader.loadSeed(seed.replace("주장: 최저 대비", "주장: 최저 대비(수정)"));

        // then
        assertThat(first.knowledgeItems()).as("2026 항목은 넣지 않는다").isEqualTo(2);
        assertThat(second.knowledgeItems()).as("바뀐 1건만 갱신").isEqualTo(1);
        assertThat(knowledge.findAll()).hasSize(2).allSatisfy(k -> {
            assertThat(k.getSourceClass()).isEqualTo(AdmissionsKnowledgeItem.SourceClass.YOUTUBE);
            assertThat(k.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
        });
        assertThat(knowledge.findByDedupKey("youtube:YT-A001")).get()
                .satisfies(k -> assertThat(k.getSourceUrl()).isEqualTo("https://www.youtube.com/watch?v=a&t=60"));
    }

    @Test
    @DisplayName("given official_knowledge가 있는 시드 when 적재·재적재하면 then OFFICIAL 사실 항목(DRAFT)이 생기고 바뀐 것만 갱신된다")
    void official_knowledge_is_loaded_as_official_fact_items() throws Exception {
        // given
        String seed = """
            {"ipsi_universities":[{"name":"사대학교","university_code":"SA"}],
             "official_knowledge":[
               {"id":"R2028-NAESIN","academic_year":2028,"title":"2028 내신 5등급제","content":"교육부 확정안: 1등급 10%","tags":"내신 5등급","source_url":"https://www.moe.go.kr/x"},
               {"id":"R2028-CSAT","academic_year":2028,"title":"2028 통합형 수능","content":"선택과목 폐지"},
               {"id":"R2031","academic_year":2031,"title":"지원 안 하는 학년도","content":"x"}]}
            """;

        // when
        var first = loader.loadSeed(seed);
        var second = loader.loadSeed(seed.replace("선택과목 폐지", "선택과목 폐지(수정)"));

        // then
        assertThat(first.knowledgeItems()).as("2031 항목은 넣지 않는다").isEqualTo(2);
        assertThat(second.knowledgeItems()).as("바뀐 1건만 갱신").isEqualTo(1);
        assertThat(knowledge.findAll()).hasSize(2).allSatisfy(k -> {
            assertThat(k.getSourceClass()).isEqualTo(AdmissionsKnowledgeItem.SourceClass.OFFICIAL);
            assertThat(k.getRole()).isEqualTo(AdmissionsKnowledgeItem.Role.FACT);
            assertThat(k.getYearConfirmed()).as("2028은 계획 단계").isFalse();
            assertThat(k.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
        });
        assertThat(knowledge.findByDedupKey("official:R2028-NAESIN")).get()
                .satisfies(k -> assertThat(k.getSourceUrl()).isEqualTo("https://www.moe.go.kr/x"));
    }

    @Test
    @DisplayName("given authoritative 표시가 없는 시드 when 적재하면 then 예전 행을 보관하지 않는다")
    void without_authoritative_flag_nothing_is_superseded() throws Exception {
        // given
        loader.loadSeed(GOOD_SEED);

        // when
        var result = loader.loadSeed(GOOD_SEED.replace("\"학생부교과\"", "\"학생부교과(새 이름)\""));

        // then
        assertThat(result.supersededArchived()).isZero();
        assertThat(types.findAll()).noneSatisfy(t -> assertThat(t.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED));
    }

    @Test
    @DisplayName("given 출처 없이 들어간 기존 일정 when 출처가 붙은 같은 일정을 다시 적재하면 then 출처가 연결되고 DRAFT로 돌아가며 새 행은 생기지 않는다")
    void existing_schedule_without_source_gets_source_on_reload() throws Exception {
        // given
        loader.loadSeed(GOOD_SEED.replace(",\"source_document_key\":\"doc_tst_2027_susi\"}]}", "}]}"));
        var before = schedules.findAll().get(0);
        assertThat(before.getSource()).isNull();
        before.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
        schedules.save(before);

        // when
        var result = loader.loadSeed(GOOD_SEED);

        // then
        assertThat(result.schedules()).isZero();
        assertThat(result.schedulesSourced()).isEqualTo(1);
        assertThat(schedules.findAll()).singleElement().satisfies(sc -> {
            assertThat(sc.getSource()).isNotNull();
            assertThat(sc.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
        });
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
        assertThat(knowledge.findAll()).allSatisfy(k -> {
            assertThat(k.getStatus()).isEqualTo(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
            // Q1~Q9는 답변 형식 안내문이지 입시 사실이 아니다
            assertThat(k.getRole()).isEqualTo(AdmissionsKnowledgeItem.Role.GUIDE);
        });
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
