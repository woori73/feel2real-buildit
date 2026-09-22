package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Canonical evidence composer 배선 테스트 (retrieval → Answer Contract 블록).
 * DB 없이 실행된다. 판정 기준은 evidence classification / provenance / forbidden conclusion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Canonical evidence composer (retrieval→answer wiring)")
class NewstartCanonicalEvidenceComposerTest {

    @Mock
    private NewstartKnowledgeItemRepository knowledgeRepository;
    @Mock
    private NewstartEmpiricalCaseRepository empiricalCaseRepository;
    @Mock
    private NewstartKnowledgeRelationRepository relationRepository;

    private NewstartCanonicalEvidenceComposer composer() {
        return new NewstartCanonicalEvidenceComposer(
                new NewstartKnowledgeRetrievalService(
                        knowledgeRepository, empiricalCaseRepository, relationRepository));
    }

    private NewstartKnowledgeItem activeItem(Long id, NewstartKnowledgeItem.SourceType sourceType) {
        return NewstartKnowledgeItem.builder()
                .knowledgeId(id)
                .sourceType(sourceType)
                .category("감면율")
                .title("지식 " + id)
                .summary("요약 " + id)
                .content("본문")
                .authority("새출발기금")
                .tags("감면율")
                .sourceUrl(id == 1L ? "https://www.newstartfund.or.kr/k" : null)
                .status(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE)
                .confidence("HIGH")
                .build();
    }

    @Test
    @DisplayName("sourceType이 근거 층위로 분류되고 OFFICIAL/CASE가 한 사실로 합성되지 않는다")
    @SuppressWarnings("unchecked")
    void classifiesItemsByLayer() {
        when(knowledgeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(
                        activeItem(1L, NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT),
                        activeItem(2L, NewstartKnowledgeItem.SourceType.OPERATOR_KNOWLEDGE),
                        activeItem(3L, NewstartKnowledgeItem.SourceType.EBOOK),
                        activeItem(4L, NewstartKnowledgeItem.SourceType.CASE))));

        String block = composer().composeForQuestion("감면율 자격이 어떻게 되나요");

        assertThat(block).contains("[공식적으로 확인되는 내용]");
        assertThat(block).contains("[운영자 가이드 — 공식 규정이 아님]");
        assertThat(block).contains("[전자책 정리]");
        assertThat(block).contains("[실제 차주 사례에서 확인되는 내용]");
        assertThat(NewstartCanonicalAnswerPolicy.containsForbiddenVerdictPhrase(block)).isFalse();
    }

    @Test
    @DisplayName("SOURCE_LINKS: 실재 sourceUrl만 전달되고 없는 항목에 URL을 생성하지 않는다")
    void carriesOnlyRealLinks() {
        var withUrl = NewstartKnowledgeItemResponse.builder()
                .knowledgeId(1L).sourceType("OFFICIAL_CURRENT").title("공식 문서")
                .summary("s").status("ACTIVE").sourceUrl("https://www.newstartfund.or.kr/doc").build();
        var withoutUrl = NewstartKnowledgeItemResponse.builder()
                .knowledgeId(2L).sourceType("CASE").title("사례")
                .summary("s").status("ACTIVE").build();

        String block = composer().composeFromItems(List.of(withUrl, withoutUrl));

        assertThat(block).contains("[출처 링크]");
        assertThat(block).contains("https://www.newstartfund.or.kr/doc");
        assertThat(block).doesNotContain("K-2] 사례 (");
    }

    @Test
    @DisplayName("근거 없음 → 빈 블록 (UNKNOWN 강제 출력 없음, ask() 경로와 동일 동작)")
    void emptyRetrievalYieldsEmptyBlock() {
        assertThat(composer().composeFromItems(List.of())).isEmpty();
        assertThat(composer().composeFromItems(null)).isEmpty();
    }

    @Test
    @DisplayName("retrieval 예외 시 빈 블록 — AI chat 무근거 모드 유지 (기존 ask() 동작 보존)")
    @SuppressWarnings("unchecked")
    void retrievalFailureDegradesToNoEvidence() {
        when(knowledgeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThat(composer().composeForQuestion("감면율 질문")).isEmpty();
    }
}
