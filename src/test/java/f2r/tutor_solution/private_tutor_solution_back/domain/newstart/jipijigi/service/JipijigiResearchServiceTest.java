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
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
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
    @Mock
    private JipijigiAnswerSessionService answerSessions;

    private JipijigiResearchService service() {
        return new JipijigiResearchService(knowledgeRepository, empiricalCaseRepository,
                officialUpdateRepository, answerSessions);
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

    @Test
    @DisplayName("축적 답변 세션은 STEP1 내부 근거로 들어가되 공식 규정으로 분류되지 않는다")
    void accumulatedSessionsAreInternalCaseOnly() {
        JipijigiResearchService service = service();
        when(knowledgeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(answerSessions.searchAcceptedEvidence(any())).thenReturn(List.of(Evidence.builder()
                .stepNo(1).sourceClass(SourceClass.INTERNAL_CASE).refType(RefType.ANSWER_SESSION)
                .refId("77").title("[축적 답변] 자동차 담보").excerpt("과거 답변 발췌")
                .build()));
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(officialUpdateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());

        ResearchPack pack = service.research("자동차 담보 재산");

        assertThat(pack.getInternalHits()).singleElement().satisfies(e -> {
            assertThat(e.getSourceClass()).isEqualTo(SourceClass.INTERNAL_CASE);
            assertThat(e.getRefType()).isEqualTo(RefType.ANSWER_SESSION);
        });
        assertThat(pack.getInternalHits()).noneSatisfy(e -> assertThat(e.getSourceClass()).isEqualTo(SourceClass.OFFICIAL_RULE));
    }

    @Test
    @DisplayName("내부 근거가 기준 이상이면 신복위 외부 검색(STEP2)만 생략하고 사유를 남긴다")
    void sufficientInternalSkipsExternalCafeSearch() {
        JipijigiResearchService service = service();
        when(knowledgeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of(item(NewstartKnowledgeItem.SourceType.CASE, null),
                        item(NewstartKnowledgeItem.SourceType.CASE, null))));
        when(answerSessions.searchAcceptedEvidence(any())).thenReturn(List.of(Evidence.builder()
                .stepNo(1).sourceClass(SourceClass.INTERNAL_CASE).refType(RefType.ANSWER_SESSION)
                .refId("77").title("축적 답변").build()));
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of(NewstartEmpiricalCase.builder()
                        .caseId(9L).articleId("6010").title("후순위 담보 임시안").keyInsights("기납부")
                        .sourceUrl("https://cafe.naver.com/newhopero/6010").build())));
        when(officialUpdateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());

        ResearchPack pack = service.research("자동차 담보 재산");

        assertThat(pack.getInternalHits()).hasSize(3);
        assertThat(pack.internalInsufficient()).isFalse();
        assertThat(pack.getCcrsHits()).isEmpty();
        assertThat(pack.isExternalSearched()).isFalse();
        assertThat(pack.getGapNotes()).anySatisfy(g -> assertThat(g).contains("STEP2").contains("생략 사유").contains("내부 근거 충분"));
        // STEP3(수집 사례 DB)·STEP4(공식 기준)는 우리 DB 조회 → 내부 근거가 충분해도 생략하지 않는다
        assertThat(pack.getBorrowerHits()).singleElement().satisfies(e -> {
            assertThat(e.getSourceClass()).isEqualTo(SourceClass.BORROWER_CAFE_CASE);
            assertThat(e.getRefType()).isEqualTo(RefType.EMPIRICAL_CASE);
        });
        then(empiricalCaseRepository).should(times(1)).findAll(any(Specification.class), any(Pageable.class));
        then(officialUpdateRepository).should(times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("내부 근거가 부족하면 외부 카페 검색을 수행하고 그 사실을 남긴다")
    void insufficientInternalRunsExternalSearch() {
        JipijigiResearchService service = service();
        when(knowledgeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(answerSessions.searchAcceptedEvidence(any())).thenReturn(List.of());
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());
        when(officialUpdateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage());

        ResearchPack pack = service.research("자동차 담보 재산");

        assertThat(pack.isExternalSearched()).isTrue();
        then(empiricalCaseRepository).should(times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("STEP3 수집 사례는 최신순이 아니라 질문 키워드 일치 수 순으로 골라 쓴다")
    void empiricalCasesAreRankedByKeywordHits() {
        JipijigiResearchService service = service();
        List<String> keywords = service.keywordsOf("후순위 담보대출 기한이익상실 임시안 이자 납부 변제");
        java.util.List<NewstartEmpiricalCase> candidates = new java.util.ArrayList<>();
        // 최신 잡글 8건(질문과 사실상 무관)이 먼저 온 DB 응답
        for (int i = 0; i < 8; i++) {
            candidates.add(NewstartEmpiricalCase.builder().caseId((long) i).articleId("g" + i)
                    .title("가입인사 드립니다 " + i).contentRaw("담보")
                    .writtenAt(java.time.LocalDateTime.of(2026, 9, 1, 0, 0).plusDays(i)).build());
        }
        // 한참 지난 실경험 글(키워드 다수 일치)
        candidates.add(NewstartEmpiricalCase.builder().caseId(99L).articleId("6010")
                .title("후순위 담보대출 기한이익상실 임시안").contentRaw("이자 납부 변제 순서")
                .writtenAt(java.time.LocalDateTime.of(2025, 1, 1, 0, 0)).build());
        when(empiricalCaseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(candidates));

        List<Evidence> hits = service.searchEmpiricalCases(keywords);

        assertThat(hits).hasSize(8);   // 단계당 상한(PER_STEP_LIMIT)만큼만 잘라낸다
        assertThat(hits.get(0).getRefId()).isEqualTo("6010");
    }

    private static <T> Page<T> page(List<T> rows) {
        return new PageImpl<>(rows);
    }

    private static <T> Page<T> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
