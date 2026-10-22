package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCounselingSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeEmbeddingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검색 정확도 회귀: 실제 연구·사실 검색 서비스와 5개 대학 시드(서울대·고려대·연세대·성균관대·중앙대, 중앙대 수시는 사설 미러본)로
 * 상담 서비스 전체 흐름을 돌린다. LLM은 목(유료 API 호출 없음)이며, 어떤 근거가 프롬프트·출처로 전달되는지와
 * LLM 호출 여부를 본다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsKnowledgeBuildService.class,
        AdmissionsEmbeddingService.class, AdmissionsVectorIndex.class, AdmissionsResearchService.class,
        AdmissionsFactSearchService.class, AdmissionsGoldenSearchTest.GoldenTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_accuracy;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시 검색 정확도 회귀 — 대학 별칭·학년도·미등록 대학·사설 미러본")
class AdmissionsSearchAccuracyDbTest {

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsKnowledgeBuildService buildService;
    @Autowired AdmissionsEmbeddingService embeddingService;
    @Autowired AdmissionsResearchService researchService;
    @Autowired AdmissionsFactSearchService factSearchService;
    @Autowired AdmissionsVectorIndex vectorIndex;

    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsScheduleRepository schedules;
    @Autowired AdmissionsKnowledgeItemRepository knowledgeItems;
    @Autowired AdmissionsKnowledgeEmbeddingRepository embeddings;

    private AdmissionsLlmClient llm;
    private AdmissionsChatService chat;

    @BeforeEach
    void setUp() throws Exception {
        String seed;
        try (InputStream in = getClass().getResourceAsStream("/admissions/accuracy-seed.json")) {
            assertThat(in).isNotNull();
            seed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        loader.loadSeed(seed);
        materials.findAll().forEach(m -> { m.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); materials.save(m); });
        types.findAll().forEach(t -> { t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); types.save(t); });
        schedules.findAll().forEach(s -> { s.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); schedules.save(s); });
        buildService.buildAll();
        knowledgeItems.findAll().forEach(k -> { k.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); knowledgeItems.save(k); });
        assertThat(embeddingService.reindexAll().failed()).isZero();
        assertThat(vectorIndex.isReady()).isTrue();

        llm = mock(AdmissionsLlmClient.class);
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn(NewStartLlmCompletion.success("근거 자료 기준으로 안내드립니다.", "t", "m"));
        chat = new AdmissionsChatService(llm, researchService, factSearchService,
                mock(AdmissionsCounselingSessionRepository.class));
    }

    @AfterEach
    void clean() {
        embeddings.deleteAll();
        knowledgeItems.deleteAll();
        schedules.deleteAll();
        types.deleteAll();
        departments.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    private static void assertOnlyUniversity(AdmissionsChatService.AdmissionsAnswer a, String universityPrefix) {
        assertThat(a.sources()).as("출처가 있어야 한다").isNotEmpty();
        assertThat(a.sources()).allSatisfy(s -> assertThat(s.title()).startsWith(universityPrefix));
    }

    @Test
    @DisplayName("① 연대 교과전형 수능최저 있어? — 줄임말을 연세대학교로 인식하고 연세대 근거만 쓴다")
    void q1_yeondae_alias() {
        // when
        var a = chat.ask("연대 교과전형 수능최저 있어?", null, null);

        // then
        assertThat(a.ok()).isTrue();
        assertOnlyUniversity(a, "연세대학교");
        assertThat(a.sources()).anyMatch(s -> s.title().contains("학생부교과전형[추천형]"));
    }

    @Test
    @DisplayName("② 고대 2027 수시 지역균형 전형은? — 고려대학교 자료만 쓰고 서울대 지역균형을 대신 쓰지 않는다")
    void q2_godae_alias_does_not_borrow_other_university() {
        // when
        var a = chat.ask("고대 2027 수시 지역균형 전형은?", null, null);

        // then: 답변이 되든 거절되든, 서울대학교 근거가 섞이면 안 된다
        assertThat(a.sources()).noneMatch(s -> s.title().contains("서울대학교"));
        if (a.ok() && !a.sources().isEmpty()) {
            assertOnlyUniversity(a, "고려대학교");
        }
        org.mockito.Mockito.mockingDetails(llm).getInvocations().forEach(inv ->
                assertThat(String.valueOf(inv.getArguments()[0])).doesNotContain("서울대학교 2027학년도 학생부종합전형(지역균형전형)"));
    }

    @Test
    @DisplayName("③ 2025학년도 서울대 수시 모집요강 — 지원하지 않는 학년도라고 안내하고 2027 자료로 답하지 않는다")
    void q3_past_year_is_refused() {
        // when
        var a = chat.ask("2025학년도 서울대 수시 모집요강", null, null);

        // then
        assertThat(a.answer()).isEqualTo(AdmissionsChatService.MSG_UNSUPPORTED_YEAR);
        assertThat(a.sources()).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("④ 카이스트 수시 모집요강 — 등록되지 않은 대학이라고 안내하고 다른 대학 모집요강을 근거로 쓰지 않는다")
    void q4_unregistered_university() {
        // when
        var a = chat.ask("카이스트 수시 모집요강", null, null);

        // then
        assertThat(a.answer()).startsWith(AdmissionsChatService.MSG_UNIV_NO_INFO).contains("카이스트");
        assertThat(a.sources()).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("⑤ 중앙대 수시 일정과 모집요강 — 사설 미러본·정시 일정을 수시 공식 근거로 답하지 않는다")
    void q5_cau_susi_mirror_not_official() {
        // when
        var a = chat.ask("중앙대 수시 일정과 모집요강", null, null);

        // then
        assertThat(a.answer()).startsWith(AdmissionsChatService.MSG_UNIV_NO_INFO);
        assertThat(a.sources()).isEmpty();
        verify(llm, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("⑤-2 중앙대 정시 일정은 정상 안내된다 (수시 거절과 정시 정상 구분)")
    void q5b_cau_jeongsi_still_answered() {
        // when
        var a = chat.ask("중앙대 정시 원서접수 일정", null, null);

        // then
        assertThat(a.ok()).isTrue();
        assertOnlyUniversity(a, "중앙대학교");
    }

    @Test
    @DisplayName("⑥ 연세대학교 2027 학생부교과 추천형 지원자격 — 정확한 전형 근거를 쓴다")
    void q6_exact_university_type() {
        // when
        var a = chat.ask("연세대학교 2027 학생부교과 추천형 지원자격", null, null);

        // then
        assertThat(a.ok()).isTrue();
        assertOnlyUniversity(a, "연세대학교");
        assertThat(a.sources()).anyMatch(s -> s.title().contains("추천형"));
    }

    @Test
    @DisplayName("⑦ 성균관대 수시 원서접수 일정 — 성균관대 일정 근거만 쓴다")
    void q7_schedule() {
        // when
        var a = chat.ask("성균관대 수시 원서접수 일정", null, null);

        // then
        assertThat(a.ok()).isTrue();
        assertOnlyUniversity(a, "성균관대학교");
        assertThat(a.sources()).anyMatch(s -> s.title().contains("원서접수"));
    }

    @Test
    @DisplayName("등록된 대학과 미등록 대학을 함께 물으면 등록된 대학만 근거로 쓰고 프롬프트로 미등록 대학 안내를 지시한다")
    void known_and_unknown_university_together() {
        // when
        var a = chat.ask("연세대학교와 카이스트 교과전형 알려주세요", null, null);

        // then
        assertThat(a.ok()).isTrue();
        assertOnlyUniversity(a, "연세대학교");
        var user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), user.capture(), anyInt());
        assertThat(user.getValue()).contains("카이스트").contains("등록된 공식 입시자료가 없다");
    }

    @Test
    @DisplayName("지원 학년도와 과거 학년도를 함께 물으면 과거 학년도 자료는 없다고 밝히도록 프롬프트에 지시한다")
    void supported_and_unsupported_year_together() {
        // when
        var a = chat.ask("2025학년도와 2027학년도 연세대학교 추천형 비교해 주세요", null, null);

        // then
        assertThat(a.ok()).isTrue();
        var user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), user.capture(), anyInt());
        assertThat(user.getValue()).contains("지원하지 않는 학년도");
    }

    @Test
    @DisplayName("대학을 지정한 모집요강 질문에는 다른 대학의 모집요강 문서(제목)가 근거로 붙지 않는다")
    void university_specific_question_excludes_other_universities_documents() {
        // when: "수시"·"모집요강"은 모든 대학 수시모집요강 제목에 있는 공통 단어
        var pack = researchService.research("연세대학교 수시 모집요강 알려주세요");

        // then
        assertThat(pack.getOfficialDocHits()).isNotEmpty();
        assertThat(pack.getOfficialDocHits()).allSatisfy(e -> assertThat(e.getTitle()).startsWith("연세대학교"));
    }
}
