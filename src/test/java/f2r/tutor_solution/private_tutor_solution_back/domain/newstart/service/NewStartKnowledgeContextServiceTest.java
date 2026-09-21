package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.RagContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.SourceInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeRetrievalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * NewStartKnowledgeContextService — RAG context 조립 계약 테스트.
 *
 * <p>§10 ACTIVE-ONLY LEAK TEST가 요구하는 회귀를 여기서 직접 검증한다: {@link
 * NewstartKnowledgeRetrievalService#retrieveForQuestion}이 이미 status='ACTIVE'로 query를
 * 제한하지만, 그 upstream 보장이 어떤 이유로든 깨져 REVIEW/DRAFT/ARCHIVED가 섞여 돌아오더라도
 * 이 service의 defense-in-depth 필터가 최종적으로 걸러내는지를 mock으로 강제 재현해 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewStartKnowledgeContextService — ACTIVE-only RAG context 조립")
class NewStartKnowledgeContextServiceTest {

  @Mock private NewstartKnowledgeRetrievalService retrievalService;
  @Mock private NewstartKnowledgeRelationRepository relationRepository;

  private NewStartKnowledgeContextService service;

  @BeforeEach
  void setUp() {
    service = new NewStartKnowledgeContextService(retrievalService, relationRepository);
  }

  private static NewstartKnowledgeItemResponse item(
      Long id, String status, String sourceType, String title, String tags, String summary) {
    return NewstartKnowledgeItemResponse.builder()
        .knowledgeId(id)
        .status(status)
        .sourceType(sourceType)
        .category("감면율")
        .title(title)
        .tags(tags)
        .summary(summary)
        .content(summary)
        .authority("새출발기금")
        .sourceUrl("https://www.newstartfund.or.kr/" + id)
        .build();
  }

  @Test
  @DisplayName("REVIEW/DRAFT/ARCHIVED가 upstream에서 섞여 와도 ACTIVE만 context에 남는다 (§10 MANDATORY)")
  void retrieve_excludesNonActiveEvenIfUpstreamLeaks() {
    when(retrievalService.retrieveForQuestion(eq("감면율 질문"), anyInt()))
        .thenReturn(
            List.of(
                item(1L, "ACTIVE", "OFFICIAL_CURRENT", "ACTIVE 공식자료", "감면율", "요약"),
                item(2L, "REVIEW", "OFFICIAL_CURRENT", "REVIEW 미승인 자료", "감면율", "요약"),
                item(3L, "DRAFT", "EBOOK", "DRAFT 초안", "감면율", "요약"),
                item(4L, "ARCHIVED", "CASE", "ARCHIVED 폐기 사례", "감면율", "요약")));
    when(relationRepository.findByFromKnowledgeId(anyLong())).thenReturn(List.of());
    when(relationRepository.findByToKnowledgeId(anyLong())).thenReturn(List.of());

    RagContext ctx = service.retrieve("감면율 질문");

    List<Long> allIds =
        java.util.stream.Stream.of(ctx.official(), ctx.ebook(), ctx.caseItems())
            .flatMap(List::stream)
            .map(NewstartKnowledgeItemResponse::getKnowledgeId)
            .toList();
    assertThat(allIds).containsExactly(1L);
    assertThat(allIds).doesNotContain(2L, 3L, 4L);
  }

  @Test
  @DisplayName("sourceType이 OFFICIAL/EBOOK/CASE 세 버킷으로 정확히 갈린다")
  void retrieve_bucketsBySourceType() {
    when(retrievalService.retrieveForQuestion(anyString(), anyInt()))
        .thenReturn(
            List.of(
                item(1L, "ACTIVE", "OFFICIAL_CURRENT", "공식-현재", "t", "s"),
                item(2L, "ACTIVE", "OFFICIAL_HISTORICAL", "공식-과거", "t", "s"),
                item(3L, "ACTIVE", "AUDIT", "감사자료", "t", "s"),
                item(4L, "ACTIVE", "EBOOK", "전자책", "t", "s"),
                item(5L, "ACTIVE", "OPERATOR_KNOWLEDGE", "운영지식", "t", "s"),
                item(6L, "ACTIVE", "CASE", "실제사례", "t", "s"),
                item(7L, "ACTIVE", "CAFE_ARTICLE", "카페글", "t", "s"),
                item(8L, "ACTIVE", "CAFE_COMMENT", "카페댓글", "t", "s"),
                item(9L, "ACTIVE", "DERIVED_RULE", "파생규칙", "t", "s")));
    when(relationRepository.findByFromKnowledgeId(anyLong())).thenReturn(List.of());
    when(relationRepository.findByToKnowledgeId(anyLong())).thenReturn(List.of());

    RagContext ctx = service.retrieve("질문");

    assertThat(ctx.official()).extracting(NewstartKnowledgeItemResponse::getKnowledgeId)
        .containsExactlyInAnyOrder(1L, 2L, 3L);
    assertThat(ctx.ebook()).extracting(NewstartKnowledgeItemResponse::getKnowledgeId)
        .containsExactlyInAnyOrder(4L, 5L);
    // CASE 후보는 4개(6~9)이지만 MAX_CASE=2로 캡되므로 그중 2개만 남는다.
    assertThat(ctx.caseItems()).hasSize(2);
    assertThat(ctx.caseItems()).extracting(NewstartKnowledgeItemResponse::getKnowledgeId)
        .allMatch(id -> List.of(6L, 7L, 8L, 9L).contains(id));
  }

  @Test
  @DisplayName("버킷별 상한(OFFICIAL 3 / EBOOK 2 / CASE 2)을 넘지 않는다")
  void retrieve_capsEachBucket() {
    List<NewstartKnowledgeItemResponse> many = new java.util.ArrayList<>();
    for (long i = 1; i <= 5; i++) many.add(item(i, "ACTIVE", "OFFICIAL_CURRENT", "공식" + i, "t", "s"));
    for (long i = 10; i <= 14; i++) many.add(item(i, "ACTIVE", "EBOOK", "전자책" + i, "t", "s"));
    for (long i = 20; i <= 24; i++) many.add(item(i, "ACTIVE", "CASE", "사례" + i, "t", "s"));
    when(retrievalService.retrieveForQuestion(anyString(), anyInt())).thenReturn(many);
    when(relationRepository.findByFromKnowledgeId(anyLong())).thenReturn(List.of());
    when(relationRepository.findByToKnowledgeId(anyLong())).thenReturn(List.of());

    RagContext ctx = service.retrieve("질문");

    assertThat(ctx.official()).hasSize(3);
    assertThat(ctx.ebook()).hasSize(2);
    assertThat(ctx.caseItems()).hasSize(2);
  }

  @Test
  @DisplayName("제목/태그에 질문 키워드가 매칭된 항목이 매칭 안 된 항목보다 먼저 선택된다")
  void retrieve_ranksKeywordMatchHigher() {
    NewstartKnowledgeItemResponse matched = item(1L, "ACTIVE", "OFFICIAL_CURRENT", "감면율 산정 기준", "감면율", "요약");
    NewstartKnowledgeItemResponse unmatched = item(2L, "ACTIVE", "OFFICIAL_CURRENT", "무관한 다른 주제", "기타", "요약");
    // 상한(3) 안에 둘 다 들어가므로, 정렬 순서(랭킹)를 직접 확인한다.
    when(retrievalService.retrieveForQuestion(anyString(), anyInt())).thenReturn(List.of(unmatched, matched));
    when(relationRepository.findByFromKnowledgeId(anyLong())).thenReturn(List.of());
    when(relationRepository.findByToKnowledgeId(anyLong())).thenReturn(List.of());

    RagContext ctx = service.retrieve("감면율이 어떻게 되나요?");

    assertThat(ctx.official()).extracting(NewstartKnowledgeItemResponse::getKnowledgeId)
        .containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("pool 안에서 양쪽 다 ACTIVE인 CONTRADICTS 쌍이 있으면 hasConflict=true이고 안내 문구가 붙는다")
  void retrieve_flagsConflictWhenBothSidesActive() {
    NewstartKnowledgeItemResponse a = item(1L, "ACTIVE", "OFFICIAL_CURRENT", "연체 10일 기준", "연체", "요약A");
    NewstartKnowledgeItemResponse b = item(2L, "ACTIVE", "OFFICIAL_HISTORICAL", "연체 31일 기준", "연체", "요약B");
    when(retrievalService.retrieveForQuestion(anyString(), anyInt())).thenReturn(List.of(a, b));
    when(relationRepository.findByFromKnowledgeId(1L))
        .thenReturn(List.of(contradicts(1L, 2L)));
    when(relationRepository.findByToKnowledgeId(1L)).thenReturn(List.of());
    when(relationRepository.findByFromKnowledgeId(2L)).thenReturn(List.of());
    when(relationRepository.findByToKnowledgeId(2L))
        .thenReturn(List.of(contradicts(1L, 2L)));

    RagContext ctx = service.retrieve("연체 기준이 뭔가요?");

    assertThat(ctx.hasConflict()).isTrue();
    String block = service.toPromptBlock(ctx);
    assertThat(block).contains("임의").contains("통합하지 말고");
  }

  @Test
  @DisplayName("CONTRADICTS 관계의 한쪽만 pool에 있으면 conflict로 취급하지 않는다")
  void retrieve_doesNotFlagConflictWhenOnlyOneSidePresent() {
    NewstartKnowledgeItemResponse a = item(1L, "ACTIVE", "OFFICIAL_CURRENT", "연체 10일 기준", "연체", "요약A");
    when(retrievalService.retrieveForQuestion(anyString(), anyInt())).thenReturn(List.of(a));
    when(relationRepository.findByFromKnowledgeId(1L))
        .thenReturn(List.of(contradicts(1L, 999L))); // 999L은 이번 pool에 없음
    when(relationRepository.findByToKnowledgeId(1L)).thenReturn(List.of());

    RagContext ctx = service.retrieve("연체 기준이 뭔가요?");

    assertThat(ctx.hasConflict()).isFalse();
  }

  @Test
  @DisplayName("근거가 전혀 없으면 근거 없음을 명시하는 fallback block을 돌려준다")
  void toPromptBlock_emptyContextReturnsExplicitNoEvidenceBlock() {
    RagContext empty = new RagContext(List.of(), List.of(), List.of(), false);

    String block = service.toPromptBlock(empty);

    assertThat(block).contains("자료가 없습니다").contains("만들어내지 마세요");
  }

  @Test
  @DisplayName("근거가 있으면 OFFICIAL/EBOOK/CASE 헤더와 함께 본문이 담기고 긴 본문은 잘린다")
  void toPromptBlock_rendersSectionsAndTruncatesLongContent() {
    String longSummary = "가".repeat(600);
    NewstartKnowledgeItemResponse official = item(1L, "ACTIVE", "OFFICIAL_CURRENT", "공식자료", "t", longSummary);
    RagContext ctx = new RagContext(List.of(official), List.of(), List.of(), false);

    String block = service.toPromptBlock(ctx);

    assertThat(block).contains("[OFFICIAL]").doesNotContain("[EBOOK]").doesNotContain("[CASE]");
    assertThat(block).contains("공식자료");
    assertThat(block).contains("…");
    assertThat(block.length()).isLessThan(longSummary.length());
  }

  @Test
  @DisplayName("toSources는 OFFICIAL→EBOOK→CASE 순서로 knowledgeId/sourceType/title/sourceUrl/sourceDate/authority/category만 담는다")
  void toSources_mapsFieldsInBucketOrder() {
    NewstartKnowledgeItemResponse official = item(1L, "ACTIVE", "OFFICIAL_CURRENT", "공식", "t", "s");
    NewstartKnowledgeItemResponse ebook = item(2L, "ACTIVE", "EBOOK", "전자책", "t", "s");
    NewstartKnowledgeItemResponse caseItem = item(3L, "ACTIVE", "CASE", "사례", "t", "s");
    RagContext ctx = new RagContext(List.of(official), List.of(ebook), List.of(caseItem), false);

    List<SourceInfo> sources = service.toSources(ctx);

    assertThat(sources).extracting(SourceInfo::knowledgeId).containsExactly(1L, 2L, 3L);
    assertThat(sources.get(0).sourceUrl()).isEqualTo("https://www.newstartfund.or.kr/1");
    assertThat(sources.get(0).authority()).isEqualTo("새출발기금");
  }

  private static NewstartKnowledgeRelation contradicts(Long from, Long to) {
    return NewstartKnowledgeRelation.builder()
        .relationId(from * 1000 + to)
        .fromKnowledgeId(from)
        .toKnowledgeId(to)
        .relationType(NewstartKnowledgeRelation.RelationType.CONTRADICTS)
        .build();
  }

}
