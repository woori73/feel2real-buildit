package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.RagContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeRetrievalService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * §12 TOP10 REAL QUESTION REGRESSION.
 *
 * <p>{@code docs/newstart/research-official/comparison/top10_questions.json}의 실제 차주 질문
 * 10개를 그대로 읽어, {@link NewStartKnowledgeContextService} 파이프라인(검색→버킷→랭킹→충돌
 * 감지→prompt/sources 조립)에 통과시킨다.
 *
 * <p>중요: 2026-09-22 기준 production/로컬 DB 모두 ACTIVE Knowledge가 0건이다(PR #361 승인
 * 워크플로만 병합됐고 실제 승격은 Agent 2/ADMIN 몫 — 이 작업에서 임의로 ACTIVE 승격하지 않는다).
 * 그래서 이 테스트는 실 DB 대신, 소수의 ACTIVE fixture(OFFICIAL 4 + EBOOK 1 + CASE 1)로 {@link
 * NewstartKnowledgeRetrievalService#retrieveForQuestion}의 실제 keyword LIKE 매칭 동작을 그대로
 * 재현한다({@link #simulateRetrieveForQuestion}는 그 service의 {@code extractKeywords}/OR-LIKE
 * 로직과 동일하다). fixture는 10개 질문 중 일부 주제만 의도적으로 커버한다 — 나머지는 실제로도
 * "근거 없음"이 나와야 정직한 결과이기 때문이다. 10문항 전원 ANSWERABLE을 요구하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewStart RAG — TOP10 실제 질문 회귀(fixture 기반, production ACTIVE=0 명시)")
class NewStartTop10RagRegressionTest {

  @Mock private NewstartKnowledgeRetrievalService retrievalService;
  @Mock private NewstartKnowledgeRelationRepository relationRepository;

  private NewStartKnowledgeContextService service;
  private List<NewstartKnowledgeItemResponse> catalog;

  @BeforeEach
  void setUp() {
    service = new NewStartKnowledgeContextService(retrievalService, relationRepository);
    catalog =
        List.of(
            fixture(1L, "OFFICIAL_CURRENT", "새출발기금 재산 평가 기준", "재산,새출발기금,감면", "재산",
                "새출발기금은 순재산을 채무조정 심사에 반영한다."),
            fixture(2L, "OFFICIAL_CURRENT", "개인워크아웃 재산 평가 기준", "재산,개인워크아웃", "재산",
                "개인워크아웃은 재산을 새출발기금과 다른 기준으로 반영한다."),
            fixture(3L, "OFFICIAL_CURRENT", "새출발기금 채무 범위와 제외 대상", "채무,제외,새출발기금", "채무범위",
                "새출발기금에서 제외되는 채무 유형을 정리한다."),
            fixture(4L, "OFFICIAL_CURRENT", "통신요금 채무의 취급", "통신요금,채무", "채무범위",
                "통신요금 채무가 섞여 있을 때의 처리 방식이다."),
            fixture(5L, "EBOOK", "재산이 있을 때 감면액이 달라지는 이유", "재산,감면액,실무", "재산",
                "실무적으로 재산이 감면액 산정에 미치는 영향을 설명한다."),
            fixture(6L, "CASE", "[감면율 사례] 재산 보유자 40% 확정", "감면율,실제사례,재산", "재산",
                "재산이 있었던 실제 차주의 감면율 확정 사례다."));
    when(retrievalService.retrieveForQuestion(anyString(), anyInt()))
        .thenAnswer(inv -> simulateRetrieveForQuestion(catalog, inv.getArgument(0)));
  }

  @Test
  @DisplayName("TOP10 실제 질문 각각에 대해 ACTIVE-only·source 다양성·grounded fallback을 확인한다")
  void top10Questions_produceGroundedOrExplicitNoEvidenceResults() throws IOException {
    List<String> questions = loadTop10Questions();
    assertThat(questions).hasSize(10);

    int answerable = 0;
    Set<String> sourceTypesSeenAcrossAll = new LinkedHashSet<>();
    for (String question : questions) {
      RagContext ctx = service.retrieve(question);
      String block = service.toPromptBlock(ctx);

      // ACTIVE-only: fixture는 전부 status=ACTIVE이므로, 매칭된 게 있다면 전부 여기서 왔어야 한다.
      List<Long> resultIds =
          java.util.stream.Stream.of(ctx.official(), ctx.ebook(), ctx.caseItems())
              .flatMap(List::stream)
              .map(NewstartKnowledgeItemResponse::getKnowledgeId)
              .toList();
      assertThat(catalog.stream().map(NewstartKnowledgeItemResponse::getKnowledgeId))
          .containsAll(resultIds); // fixture 밖 id가 섞여 나올 수 없다.

      if (ctx.isEmpty()) {
        // 근거 없음도 정직한 결과다 — 반드시 그 사실을 명시한 block이어야 한다(hallucination 방지).
        assertThat(block).contains("자료가 없습니다");
      } else {
        answerable++;
        ctx.official().forEach(i -> sourceTypesSeenAcrossAll.add("OFFICIAL"));
        ctx.ebook().forEach(i -> sourceTypesSeenAcrossAll.add("EBOOK"));
        ctx.caseItems().forEach(i -> sourceTypesSeenAcrossAll.add("CASE"));
      }
    }

    // fixture가 10문항 중 일부만 커버하도록 의도적으로 구성했으므로 전원 매칭은 기대하지 않는다.
    // "자동차·예금·보험·가상자산·비상장주식" 질문(Q4)은 fixture에 없는 세부 자산유형이라 0건이
    // 나와야 정상이며, 이게 곧 production 현재 상태(ACTIVE 0건)가 의미하는 바를 보여준다.
    assertThat(answerable).isBetween(1, 9);
    assertThat(sourceTypesSeenAcrossAll).contains("OFFICIAL", "EBOOK", "CASE");
  }

  private List<String> loadTop10Questions() throws IOException {
    Path path = Path.of("docs/newstart/research-official/comparison/top10_questions.json");
    String json = Files.readString(path);
    JsonNode root = new ObjectMapper().readTree(json);
    JsonNode questions = root.get("questions");
    List<String> out = new ArrayList<>();
    questions.forEach(q -> out.add(q.get("q").asText()));
    return out;
  }

  /** {@code NewstartKnowledgeRetrievalService.retrieveForQuestion}의 ACTIVE + OR-LIKE 매칭을 그대로 흉내낸다. */
  private static List<NewstartKnowledgeItemResponse> simulateRetrieveForQuestion(
      List<NewstartKnowledgeItemResponse> catalog, String question) {
    Set<String> keywords = tokenize(question);
    if (keywords.isEmpty()) return List.of();
    return catalog.stream()
        .filter(item -> "ACTIVE".equals(item.getStatus()))
        .filter(
            item ->
                keywords.stream()
                    .anyMatch(
                        kw ->
                            contains(item.getTitle(), kw)
                                || contains(item.getContent(), kw)
                                || contains(item.getTags(), kw)
                                || contains(item.getCategory(), kw)))
        .collect(Collectors.toList());
  }

  private static boolean contains(String field, String kw) {
    return field != null && field.contains(kw);
  }

  private static Set<String> tokenize(String text) {
    if (text == null) return Set.of();
    return Arrays.stream(text.replaceAll("[^가-힣a-zA-Z0-9]", " ").split("\\s+"))
        .filter(s -> s.length() >= 2)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static NewstartKnowledgeItemResponse fixture(
      Long id, String sourceType, String title, String tags, String category, String summary) {
    return NewstartKnowledgeItemResponse.builder()
        .knowledgeId(id)
        .status("ACTIVE")
        .sourceType(sourceType)
        .title(title)
        .tags(tags)
        .category(category)
        .summary(summary)
        .content(summary)
        .authority("새출발기금")
        .sourceUrl("https://www.newstartfund.or.kr/fixture/" + id)
        .build();
  }
}
