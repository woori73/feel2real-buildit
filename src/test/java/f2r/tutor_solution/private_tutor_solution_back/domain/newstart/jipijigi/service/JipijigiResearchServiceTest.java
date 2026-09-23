package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 강제 조사 사다리(STEP1..5)와 출처 분류 검증.
 * 핵심 규칙: 커뮤니티 자료는 공식 규정으로 분류되지 않고, 근거가 없는 단계는 명시적으로 남긴다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("지피지기 Agent 조사 사다리/출처 분류")
class JipijigiResearchServiceTest {

    @Mock
    private NewstartKnowledgeItemRepository knowledgeRepository;
    @Mock
    private NewstartEmpiricalCaseRepository empiricalCaseRepository;
    @Mock
    private NewStartOfficialUpdateRepository officialUpdateRepository;

    private JipijigiResearchService service() {
        return new JipijigiResearchService(knowledgeRepository, empiricalCaseRepository, officialUpdateRepository);
    }

    private NewstartKnowledgeItem item(NewstartKnowledgeItem.SourceType type, String url) {        return NewstartKnowledgeItem.builder()
                .knowledgeId(1L).sourceType(type).status(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE)
                .title("t").content("c").sourceUrl(url).authority("a").category("감면율")
                .build();
    }

    @ParameterizedTest
    @DisplayName("출처 타입에 따라 근거 분류가 결정된다 (커뮤니티 ≠ 공식)")
    @CsvSource({
            "OFFICIAL_CURRENT, null, OFFICIAL_RULE",
            "AUDIT, null, OFFICIAL_RULE",
            "DERIVED_RULE, null, OFFICIAL_RULE",
            "EBOOK, null, OPERATOR_MATERIAL",
            "OPERATOR_KNOWLEDGE, null, OPERATOR_MATERIAL",
            "CASE, null, INTERNAL_CASE",
            "CAFE_ARTICLE, https://cafe.naver.com/newhopero/1, BORROWER_CAFE_CASE",
            "CAFE_COMMENT, https://cafe.naver.com/newhopero/1, BORROWER_CAFE_CASE",
            "CAFE_ARTICLE, https://cafe.naver.com/ccrs5500/1, CCRS_CAFE_CASE",
            "CAFE_ARTICLE, null, CCRS_CAFE_CASE"
    })
    void classifyBySourceType(NewstartKnowledgeItem.SourceType type, String url, SourceClass expected) {
        assertThat(JipijigiResearchService.classifyKnowledge(item(type, url))).isEqualTo(expected);
    }

    @Test
    @DisplayName("STEP1 근거가 기준 미만이면 외부 조사가 필수임을 gap note로 남긴다")
    void insufficientInternalIsRecorded() {
        JipijigiResearchService service = service();
        when(knowledgeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of(item(NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT, null))))
                .thenReturn(emptyPage());
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(officialUpdateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());

        ResearchPack pack = service.research("개인회생 새출발기금 차이");

        assertThat(pack.getInternalHits()).hasSize(1);
        assertThat(pack.internalInsufficient()).isTrue();
        assertThat(pack.getGapNotes()).anySatisfy(g -> assertThat(g).contains("STEP1").contains("외부 사례조사"));
        assertThat(pack.getGapNotes()).anySatisfy(g -> assertThat(g).contains("STEP2"));
        assertThat(pack.getGapNotes()).anySatisfy(g -> assertThat(g).contains("STEP3"));
    }

    @Test
    @DisplayName("저장된 신복위/부실차주/공식 근거가 각각 해당 STEP에 담기고 출처 URL이 유지된다")
    void eachStepReadsItsOwnStore() {
        JipijigiResearchService service = service();
        when(knowledgeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of(item(NewstartKnowledgeItem.SourceType.CAFE_ARTICLE, "https://cafe.naver.com/newhopero/9"))))
                .thenReturn(emptyPage());
        NewstartEmpiricalCase caseRow = NewstartEmpiricalCase.builder()
                .caseId(3L).articleId("5740").title("감면 사례").keyInsights("핵심").sourceUrl("https://x/5740").build();
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of(caseRow)));
        when(officialUpdateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        ResearchPack pack = service.research("감면율 사례");

        assertThat(pack.getBorrowerHits()).singleElement().satisfies(e -> {
            assertThat(e.getSourceClass()).isEqualTo(SourceClass.BORROWER_CAFE_CASE);
            assertThat(e.getRefType()).isEqualTo(RefType.EMPIRICAL_CASE);
            assertThat(e.getRefUrl()).isEqualTo("https://x/5740");
        });
    }

    @Test
    @DisplayName("법원/추심 관련 질문은 STEP5에서 추가 기관 확인 필요를 기록한다")
    void extraAgencyMarkers() {
        JipijigiResearchService service = service();
        when(knowledgeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(officialUpdateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());

        ResearchPack pack = service.research("지급명령 추심 대응");

        assertThat(pack.getExtraAgencyHits()).anySatisfy(e -> assertThat(e.getNote()).contains("전자소송포털"));
        assertThat(service.keywordsOf("지급명령 추심 대응")).contains("지급명령", "추심");
    }

    @Test
    @DisplayName("빈 질문은 근거 0건으로 안전 종료 (질의 없이 조회하지 않는다)")
    void blankQuestionShortCircuits() {
        JipijigiResearchService service = service();
        assertThat(service.research("  ").getInternalHits()).isEmpty();
    }

    private static <T> Page<T> page(List<T> rows) {
        return new PageImpl<>(rows);
    }

    private static <T> Page<T> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
