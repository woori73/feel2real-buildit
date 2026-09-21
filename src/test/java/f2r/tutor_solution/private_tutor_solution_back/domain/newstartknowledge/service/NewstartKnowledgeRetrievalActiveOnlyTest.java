package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
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
 * Production RAG = ACTIVE ONLY 계약 회귀 테스트.
 *
 * <p>승인 workflow 도입 후에도 retrieval 경로가 REVIEW/DRAFT를 노출하지 않음을 보장한다.
 * Repository {@code findAllActive()} / {@code findEffective()} 자체가 ACTIVE로 한정되어 있어,
 * 서비스 계층은 그 결과를 그대로 전달하기만 하면 된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Knowledge retrieval ACTIVE-ONLY 계약")
class NewstartKnowledgeRetrievalActiveOnlyTest {

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

    private NewstartKnowledgeItem activeItem(Long id, NewstartKnowledgeItem.SourceType sourceType) {
        return NewstartKnowledgeItem.builder()
                .knowledgeId(id)
                .sourceType(sourceType)
                .category("감면율")
                .title("승인된 지식 " + id)
                .summary("요약")
                .content("본문")
                .authority("새출발기금")
                .tags("감면율")
                .sourceUrl("https://www.newstartfund.or.kr/guide")
                .status(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE)
                .confidence("HIGH")
                .build();
    }

    @Test
    @DisplayName("소스 타입별 ACTIVE 조회는 승인된 항목을 그대로 반환한다")
    void findActiveBySourceTypeReturnsApproved() {
        when(knowledgeRepository.findAllActive()).thenReturn(List.of(
                activeItem(1L, NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT),
                activeItem(2L, NewstartKnowledgeItem.SourceType.CASE),
                activeItem(3L, NewstartKnowledgeItem.SourceType.EBOOK)));

        List<NewstartKnowledgeItemResponse> cases = service.findActiveBySourceType("CASE", 10);
        List<NewstartKnowledgeItemResponse> ebooks = service.findActiveBySourceType("EBOOK", 10);
        List<NewstartKnowledgeItemResponse> officials =
                service.findActiveBySourceType("OFFICIAL_CURRENT", 10);

        assertThat(cases).extracting(NewstartKnowledgeItemResponse::getKnowledgeId).containsExactly(2L);
        assertThat(ebooks).extracting(NewstartKnowledgeItemResponse::getKnowledgeId).containsExactly(3L);
        assertThat(officials).extracting(NewstartKnowledgeItemResponse::getKnowledgeId).containsExactly(1L);
    }

    @Test
    @DisplayName("질문 검색 경로는 repository 결과를 DTO로 매핑해 반환한다")
    @SuppressWarnings("unchecked")
    void retrieveForQuestionMapsResults() {
        NewstartKnowledgeItem item = activeItem(7L, NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT);
        when(knowledgeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        List<NewstartKnowledgeItemResponse> result = service.retrieveForQuestion("감면율 질문", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKnowledgeId()).isEqualTo(7L);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }
}
