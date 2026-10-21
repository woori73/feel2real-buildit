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

import static org.assertj.core.api.Assertions.assertThat;

/** 학과·전형·일정 DB 데이터가 상담 검색에 연결되는지 — 승인 게이트, 학년도 구분, 출처 연결. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsFactSearchService.class,
        AdmissionsFactSearchServiceDbTest.JacksonTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_fact;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시 사실 검색 — 학과·전형·일정 연결")
class AdmissionsFactSearchServiceDbTest {

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
    @DisplayName("given 승인 전(DRAFT) 데이터 when 학과를 물으면 then 근거가 없다")
    void draft_rows_are_not_searchable() {
        // given: 적재 직후는 전부 DRAFT

        // when
        var result = search.search("테스트대 컴퓨터공학과 몇 명 뽑나요", null);

        // then
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    @DisplayName("given 승인된 학과 when 대학과 학과를 물으면 then 해당 대학 행만 모집인원·출처와 함께 나온다")
    void department_question_returns_quota_with_source() {
        // given
        approveAll();

        // when
        var result = search.search("테스트대 컴퓨터공학과 몇 명 뽑나요", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        var e = result.evidence().get(0);
        assertThat(e.getTitle()).contains("테스트대학교", "2027학년도", "컴퓨터공학과");
        assertThat(e.getExcerpt()).contains("학생부교과 19명", "논술 11명", "합계 30명");
        assertThat(e.getRefId()).startsWith("dept:");
        assertThat(e.getRefUrl()).isEqualTo("https://example.test/tst");
        assertThat(e.getNote()).contains("테스트대 2027 수시 모집요강");
    }

    @Test
    @DisplayName("given 승인된 학과 when 대학을 말하지 않으면 then 모든 대학의 같은 학과 행이 나온다")
    void department_without_university_searches_all() {
        // given
        approveAll();

        // when
        var result = search.search("컴퓨터공학 어디가 많이 뽑나요", null);

        // then
        assertThat(result.evidence()).hasSize(2);
    }

    @Test
    @DisplayName("given 승인된 전형 when 지역균형 수능최저를 물으면 then 2027 전형만 나오고 비어 있는 수능최저는 확인되지 않음으로 표기된다")
    void type_question_returns_2027_only_and_marks_missing_fields() {
        // given
        approveAll();

        // when
        var result = search.search("테스트대 지역균형 수능최저가 얼마인가요?", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        var e = result.evidence().get(0);
        assertThat(e.getAcademicYear()).isEqualTo(2027);
        assertThat(e.getExcerpt()).contains("지원자격: 학교장 추천").contains("수능최저: 확인되지 않음(추정 금지)");
    }

    @Test
    @DisplayName("given 2028 질문 when 해당 학년도 전형이 있으면 then 2028 계획 근거만 나오고 확정 표시가 아니다")
    void year_2028_returns_planned_rows_only() {
        // given
        approveAll();

        // when
        var result = search.search("테스트대 2028 지역균형은 어떻게 달라지나요", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).getAcademicYear()).isEqualTo(2028);
        assertThat(result.evidence().get(0).getYearConfirmed()).isFalse();
        assertThat(result.unpublished()).isFalse();
    }

    @Test
    @DisplayName("given 2028 일정 질문 when 2028 일정 자료가 없으면 then 근거는 없고 미발표로 표시한다")
    void year_2028_schedule_is_unpublished() {
        // given
        approveAll();

        // when
        var result = search.search("테스트대 2028 원서접수는 몇 월인가요", null);

        // then
        assertThat(result.evidence()).isEmpty();
        assertThat(result.unpublished()).isTrue();
    }

    @Test
    @DisplayName("given 승인된 일정 when 학교장추천 입력을 물으면 then 일정 원문과 출처가 나온다")
    void schedule_question_returns_raw_note() {
        // given
        approveAll();

        // when
        var result = search.search("테스트대 학교장추천 입력이 언제까지였죠?", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).getExcerpt()).contains("2026.9.14.~9.18.");
        assertThat(result.evidence().get(0).getRefId()).startsWith("sched:");
    }

    @Test
    @DisplayName("given 승인된 데이터 when 학년도를 말하지 않으면 then 2027 확정 자료만 검색한다")
    void unspecified_year_defaults_to_2027() {
        // given
        approveAll();

        // when
        var result = search.search("테스트대 지역균형 지원자격이 뭐예요", null);

        // then
        assertThat(result.searchedYears()).containsExactly(2027);
        assertThat(result.evidence()).allSatisfy(e -> assertThat(e.getAcademicYear()).isEqualTo(2027));
    }

    private static final String ALL_UNIV_SEED = """
        {"ipsi_universities":[{"name":"가대학교","university_code":"GA"},{"name":"나대학교","university_code":"NA"},{"name":"다대학교","university_code":"DA"}],
         "ipsi_documents":[{"document_key":"doc_ga","document_name":"가대 2027 수시 모집요강","applicable_academic_year":2027,"institution_name":"가대","source_url":"https://example.test/ga"},
                           {"document_key":"doc_na","document_name":"나대 2027 수시 모집요강","applicable_academic_year":2027,"institution_name":"나대","source_url":"https://example.test/na"},
                           {"document_key":"doc_ga_2028","document_name":"가대 2028 시행계획","applicable_academic_year":2028,"institution_name":"가대","source_url":"https://example.test/ga28"}],
         "ipsi_departments":[],"ipsi_admission_types":[],
         "ipsi_schedules":[
           {"university_code":"GA","academic_year":2027,"item":"원서접수","type_name":"수시","date":"2026.9.8.~9.11.","source_document_key":"doc_ga"},
           {"university_code":"GA","academic_year":2027,"item":"원서접수","type_name":"정시","date":"2026.12.29.~12.31.","source_document_key":"doc_ga"},
           {"university_code":"NA","academic_year":2027,"item":"원서접수","type_name":"수시","date":"2026.9.9.~9.12.","source_document_key":"doc_na"},
           {"university_code":"GA","academic_year":2028,"item":"원서접수","type_name":"수시","date":"2027.9.1.~9.4.","source_document_key":"doc_ga_2028"}]}
        """;

    private void reloadAllUniv(boolean approve) throws Exception {
        clean();
        loader.loadSeed(ALL_UNIV_SEED);
        if (approve) approveAll();
    }

    @Test
    @DisplayName("given 대학명 없는 수시 원서접수 질문 when 일부 대학만 자료가 있으면 then 있는 대학 일정을 대학별로 반환하고 없는 대학은 missing으로 표시한다")
    void all_university_schedule_question_returns_per_university_rows() throws Exception {
        // given
        reloadAllUniv(true);

        // when
        var result = search.search("2027학년도 수시 원서접수 일정 알려줘", null);

        // then
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.evidence()).allSatisfy(e -> {
            assertThat(e.getRefId()).startsWith("sched:");
            assertThat(e.getAcademicYear()).isEqualTo(2027);
            assertThat(e.getTitle()).doesNotContain("정시");
            assertThat(e.getRefUrl()).startsWith("https://example.test/");
        });
        assertThat(result.evidence().get(0).getTitle()).startsWith("가대학교 2027학년도");
        assertThat(result.evidence().get(1).getTitle()).startsWith("나대학교 2027학년도");
        assertThat(result.scheduleCoverage().covered()).containsExactly("가대학교", "나대학교");
        assertThat(result.scheduleCoverage().missing()).containsExactly("다대학교");
    }

    @Test
    @DisplayName("given 승인 전(DRAFT) 일정 when 전체 일정을 물으면 then 근거가 없고 전 대학이 missing이다")
    void all_university_schedule_ignores_draft_rows() throws Exception {
        // given
        reloadAllUniv(false);

        // when
        var result = search.search("2027학년도 수시 원서접수 일정 알려줘", null);

        // then
        assertThat(result.evidence()).isEmpty();
        assertThat(result.scheduleCoverage().covered()).isEmpty();
        assertThat(result.scheduleCoverage().missing()).containsExactly("가대학교", "나대학교", "다대학교");
    }

    @Test
    @DisplayName("given 정시 질문 when 대학명 없이 접수 일정을 물으면 then 정시 행만 나온다")
    void all_university_schedule_separates_jeongsi() throws Exception {
        // given
        reloadAllUniv(true);

        // when
        var result = search.search("2027학년도 정시 원서접수 일정 알려줘", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).getExcerpt()).contains("2026.12.29.~12.31.");
    }

    @Test
    @DisplayName("given 2028 질문 when 대학명 없이 접수 일정을 물으면 then 2028 계획 근거만 나오고 확정이 아니다")
    void all_university_schedule_2028_is_planned_only() throws Exception {
        // given
        reloadAllUniv(true);

        // when
        var result = search.search("2028학년도 수시 원서접수 일정 알려줘", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).getYearConfirmed()).isFalse();
        assertThat(result.evidence().get(0).getAcademicYear()).isEqualTo(2028);
    }

    @Test
    @DisplayName("given 대학을 지정한 일정 질문 when 물으면 then 기존처럼 그 대학만 나온다 (회귀)")
    void university_specific_schedule_question_is_unchanged() throws Exception {
        // given
        reloadAllUniv(true);

        // when
        var result = search.search("나대학교 2027학년도 수시 원서접수 일정 알려줘", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).getTitle()).startsWith("나대학교");
        assertThat(result.scheduleCoverage()).isNull();
    }

    private static String deptJson(String name, int total) {
        return "{\"university_code\":\"TST\",\"academic_year\":\"2027\",\"department_name\":\"" + name
                + "\",\"counts_by_type\":{\"학생부종합\":" + total + ",\"합계\":" + total + "},\"source_document_key\":\"doc_tst_2027_susi\"}";
    }

    private void reloadWith(String... deptNames) throws Exception {
        clean();
        StringBuilder depts = new StringBuilder();
        for (int i = 0; i < deptNames.length; i++) depts.append(i > 0 ? "," : "").append(deptJson(deptNames[i], 10 + i));
        loader.loadSeed("{\"ipsi_universities\":[{\"name\":\"테스트대학교\",\"university_code\":\"TST\"}],"
                + "\"ipsi_documents\":[{\"document_key\":\"doc_tst_2027_susi\",\"document_name\":\"테스트대 2027 수시 모집요강\",\"applicable_academic_year\":2027,\"institution_name\":\"테스트대\"}],"
                + "\"ipsi_departments\":[" + depts + "],\"ipsi_admission_types\":[],\"ipsi_schedules\":[]}");
        approveAll();
    }

    @Test
    @DisplayName("given 반도체 모집단위 여러 개 when 반도체만 말해 물으면 then 반도체 관련 행만 모두 나오고 다른 학과는 나오지 않는다 (S21)")
    void keyword_only_question_finds_semiconductor_departments() throws Exception {
        // given
        reloadWith("시스템반도체공학과", "지능형반도체전공", "반도체시스템공학과", "반도체융합공학과", "반도체공학과", "컴퓨터공학과", "경영학과", "자유전공학부");

        // when
        var result = search.search("반도체 전공 가고 싶은데 대학별 길이가 어떻게 돼요?", null);

        // then
        assertThat(result.evidence()).hasSize(5);
        assertThat(result.evidence()).allSatisfy(e -> assertThat(e.getTitle()).contains("반도체"));
    }

    @Test
    @DisplayName("given 의예과와 치의예과가 함께 있을 때 when 의예과를 정확히 물으면 then 키워드 폴백을 쓰지 않아 치의예과는 섞이지 않는다")
    void exact_department_name_suppresses_keyword_fallback() throws Exception {
        // given
        reloadWith("의예과", "치의예과", "한의예과(인문)");

        // when
        var result = search.search("의예과 논술로 지원할 수 있나요", null);

        // then
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).getTitle()).contains("의예과").doesNotContain("치의예과");
    }

    @Test
    @DisplayName("given 학과명과 무관한 일반 질문 when 물으면 then 학과 근거가 하나도 나오지 않는다")
    void generic_question_returns_no_department_rows() throws Exception {
        // given
        reloadWith("시스템반도체공학과", "자유전공학부", "전자공학부");

        // when
        var result = search.search("전공 상관없이 대학별 전형이 궁금해요", null);

        // then
        assertThat(result.evidence()).isEmpty();
    }
    @Test
    void approvedRowWithoutLinkedOfficialSourceIsNotEvidence() {
        approveAll();
        departments.findAll().forEach(d -> { d.setSource(null); departments.save(d); });
        assertThat(search.search("컴퓨터공학과 모집인원",2027).evidence()).isEmpty();
    }

}
