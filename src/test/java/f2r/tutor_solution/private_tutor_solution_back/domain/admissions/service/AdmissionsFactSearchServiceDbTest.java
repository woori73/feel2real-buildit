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
         "ipsi_documents":[{"document_key":"doc_tst_2027_susi","document_name":"테스트대 2027 수시 모집요강","applicable_academic_year":2027,"institution_name":"테스트대","source_url":"https://example.test/tst"}],
         "ipsi_departments":[{"university_code":"TST","academic_year":"2027","department_name":"컴퓨터공학과","counts_by_type":{"학생부교과":19,"논술":11,"합계":30},"source_document_key":"doc_tst_2027_susi"},
                             {"university_code":"EXA","academic_year":"2027","department_name":"컴퓨터공학과","counts_by_type":{"합계":5}}],
         "ipsi_admission_types":[{"university_code":"TST","academic_year":2027,"type_name":"학생부교과(지역균형)","recruitment_count":10,"eligibility":"학교장 추천","min_academic_standard":"","source_document_key":"doc_tst_2027_susi"},
                                 {"university_code":"TST","academic_year":2028,"type_name":"학생부교과(지역균형)","recruitment_count":12,"eligibility":"시행계획 방향"}],
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
}
