package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartEmpiricalCaseResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Canonical CASE retrieval 회귀 테스트.
 *
 * <p>공식자료가 없다는 이유로 관련 CASE가 제거되지 않음을 보장한다.
 * 확정 감면율이 없는(UNRESOLVED/PARTIAL/ONGOING) 사례가 retrieval을 통과한다.
 * DB 없이 실행된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Canonical case retrieval (outcome-independent)")
class NewstartKnowledgeRetrievalCaseOutcomeTest {

    @Mock
    private NewstartKnowledgeItemRepository knowledgeRepository;
    @Mock
    private NewstartEmpiricalCaseRepository empiricalCaseRepository;
    @Mock
    private NewstartKnowledgeRelationRepository relationRepository;

    private NewstartKnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new NewstartKnowledgeRetrievalService(
                knowledgeRepository, empiricalCaseRepository, relationRepository);
    }

    private NewstartEmpiricalCase unresolvedCase() {
        return NewstartEmpiricalCase.builder()
                .caseId(1L)
                .articleId("5987")
                .boardName("질문게시판")
                .title("지급명령 후 이의신청")
                .authorFollowupResult(null)
                .finalReductionRate(null)
                .predictionVsActual("UNKNOWN")
                .build();
    }

    @Test
    @DisplayName("미확정 사례가 findRelatedCasesRegardlessOfOutcome을 통과한다")
    @SuppressWarnings("unchecked")
    void relatedRegardlessOfOutcomePassesUnresolved() {
        // given: 확정 감면율이 없는 사례
        when(empiricalCaseRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(unresolvedCase())));

        // when: 결과 무관 조회
        List<NewstartEmpiricalCaseResponse> result =
                service.findRelatedCasesRegardlessOfOutcome(null, null, null, null, null, null, null, 10);

        // then: 제거되지 않고 반환되며 상태는 미확정으로 분류된다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFinalReductionRate()).isNull();
        assertThat(NewstartCanonicalAnswerPolicy.classifyCaseOutcome(
                result.get(0).getFinalReductionRate(),
                result.get(0).getAuthorFollowupResult(),
                result.get(0).getPredictionVsActual()))
                .isEqualTo("UNRESOLVED_CASE");
    }

    @Test
    @DisplayName("미확정 사례가 findSimilarCasesRegardlessOfOutcome을 통과한다")
    @SuppressWarnings("unchecked")
    void similarRegardlessOfOutcomePassesUnresolved() {
        // given: 확정 감면율이 없는 사례
        when(empiricalCaseRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(unresolvedCase())));

        // when: 결과 무관 유사 조회
        List<NewstartEmpiricalCaseResponse> result =
                service.findSimilarCasesRegardlessOfOutcome(100000000L, 30000000L, 40, null, 30, 10);

        // then: 제거되지 않고 반환된다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getArticleId()).isEqualTo("5987");
    }

    @Test
    @DisplayName("RelationType이 canonical relation 어휘를 포함하고 DB 컬럼 길이에 맞는다")
    void relationTypeCoversCanonicalVocabulary() {
        // given/when: canonical relations 어휘 6종
        List<String> canonical = List.of(
                "SUPPORTED_BY",
                "PARTIALLY_SUPPORTED_BY",
                "RELATED_CASE",
                "RELATED_FAQ",
                "UNEXPLAINED_BY_OFFICIAL",
                "CONFLICT_REVIEW");

        // then: enum에 존재하고 VARCHAR(30)에 들어간다
        for (String name : canonical) {
            NewstartKnowledgeRelation.RelationType type =
                    NewstartKnowledgeRelation.RelationType.valueOf(name);
            assertThat(type.name()).isEqualTo(name);
            assertThat(name.length()).isLessThanOrEqualTo(30);
        }
        // 기존 값도 그대로 보존된다
        assertThat(NewstartKnowledgeRelation.RelationType.valueOf("CONTRADICTS")).isNotNull();
    }
}
