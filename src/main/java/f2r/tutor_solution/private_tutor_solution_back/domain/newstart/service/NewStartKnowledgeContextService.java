package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeRetrievalService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * NewStart AI 질문에 ACTIVE Knowledge를 붙이는 RAG context 조립기.
 *
 * <p>{@link NewstartKnowledgeRetrievalService#retrieveForQuestion}이 이미 {@code status =
 * 'ACTIVE'}로 query를 제한하지만, 이 경계에서 그 보장을 맹신하지 않고 반환된 각 항목의 status를
 * 다시 확인한다(defense-in-depth) — REVIEW/DRAFT/ARCHIVED가 어떤 경로로도 Qwen context에
 * 들어가지 않게 하기 위해서다.
 *
 * <p>실증사례 원본({@code NewstartEmpiricalCase})은 검토 상태 컬럼이 없어(승인 워크플로 대상이
 * 아님) 여기서 직접 조회하지 않는다 — CASE 근거는 승인 절차를 거쳐 {@code
 * NewstartKnowledgeItem}(sourceType=CASE/CAFE_ARTICLE/CAFE_COMMENT/DERIVED_RULE)으로 파생된
 * 것만 쓴다({@code NewstartDataIngestionService.generateKnowledgeFromCases}). 이 경로는
 * nickname 등 PII 필드 자체가 없어 CASE 근거에 실명/닉네임이 섞일 수 없다.
 *
 * <p>V1 retrieval은 vector/embedding/FULLTEXT 없이 기존 keyword 검색 위에 가벼운 관련도
 * 점수(제목/태그/카테고리/본문 매칭 개수)만 얹는다. {@code NewstartKnowledgeRelation}의 {@code
 * CONTRADICTS} 관계가 pool 안에서 양쪽 다 발견되면 순위 가산점을 줘 우선 노출하고 {@code
 * hasConflict} 플래그로 system prompt가 "임의로 합치지 말라"고 지시하게 한다 — 완벽한 pair 보장은
 * 아니고 ranking 가산점 방식이라는 한계는 있다(§7, V1 범위).
 */
@Service
public class NewStartKnowledgeContextService {

  static final int RETRIEVAL_POOL_SIZE = 20;
  static final int MAX_OFFICIAL = 3;
  static final int MAX_EBOOK = 2;
  static final int MAX_CASE = 2;
  static final int MAX_SNIPPET_CHARS = 480;
  private static final int CONFLICT_SCORE_BONUS = 50;

  private static final Set<String> OFFICIAL_TYPES = Set.of("OFFICIAL_CURRENT", "OFFICIAL_HISTORICAL", "AUDIT");
  private static final Set<String> EBOOK_TYPES = Set.of("EBOOK", "OPERATOR_KNOWLEDGE");
  private static final Set<String> CASE_TYPES = Set.of("CASE", "CAFE_ARTICLE", "CAFE_COMMENT", "DERIVED_RULE");

  private final NewstartKnowledgeRetrievalService retrievalService;
  private final NewstartKnowledgeRelationRepository relationRepository;

  public NewStartKnowledgeContextService(
      NewstartKnowledgeRetrievalService retrievalService,
      NewstartKnowledgeRelationRepository relationRepository) {
    this.retrievalService = retrievalService;
    this.relationRepository = relationRepository;
  }

  /** 질문 하나에 대해 ACTIVE OFFICIAL/EBOOK/CASE 근거를 조회하고 카테고리별로 나눈다. */
  public RagContext retrieve(String question) {
    List<NewstartKnowledgeItemResponse> pool = retrievalService.retrieveForQuestion(question, RETRIEVAL_POOL_SIZE);
    List<NewstartKnowledgeItemResponse> active =
        pool.stream().filter(item -> "ACTIVE".equals(item.getStatus())).collect(Collectors.toList());

    Set<Long> conflictingIds = findConflictingIds(active);
    Set<String> keywords = tokenize(question);

    List<NewstartKnowledgeItemResponse> ranked =
        active.stream()
            .sorted(
                Comparator.comparingInt(
                        (NewstartKnowledgeItemResponse item) -> score(item, keywords, conflictingIds))
                    .reversed()
                    .thenComparing(NewstartKnowledgeItemResponse::getKnowledgeId))
            .collect(Collectors.toList());

    List<NewstartKnowledgeItemResponse> official = pickBucket(ranked, OFFICIAL_TYPES, MAX_OFFICIAL);
    List<NewstartKnowledgeItemResponse> ebook = pickBucket(ranked, EBOOK_TYPES, MAX_EBOOK);
    List<NewstartKnowledgeItemResponse> caseItems = pickBucket(ranked, CASE_TYPES, MAX_CASE);

    boolean hasConflict =
        java.util.stream.Stream.of(official, ebook, caseItems)
            .flatMap(List::stream)
            .anyMatch(item -> conflictingIds.contains(item.getKnowledgeId()));

    return new RagContext(official, ebook, caseItems, hasConflict);
  }

  /** Qwen에 붙일 context block. 근거가 없으면 "근거 없음"을 명시적으로 알리는 block을 돌려준다. */
  public String toPromptBlock(RagContext ctx) {
    if (ctx.isEmpty()) {
      return "[근거 자료]\n현재 이 질문과 일치하는 승인된(ACTIVE) 자료가 없습니다. 일반적인 안내만 하고"
          + " 구체적인 수치·조건·결과는 만들어내지 마세요.";
    }
    StringBuilder sb = new StringBuilder();
    appendSection(sb, "OFFICIAL", ctx.official());
    appendSection(sb, "EBOOK", ctx.ebook());
    appendSection(sb, "CASE", ctx.caseItems());
    if (ctx.hasConflict()) {
      sb.append("[안내] 위 자료 중 적용 범위나 기준이 서로 다른 근거가 함께 있습니다. 하나로 임의")
          .append(" 통합하지 말고 출처별로 구분해서 설명하세요.\n");
    }
    return sb.toString().stripTrailing();
  }

  /** ChatResponse에 실릴 출처 목록. OFFICIAL → EBOOK → CASE 순서를 유지한다. */
  public List<SourceInfo> toSources(RagContext ctx) {
    List<SourceInfo> sources = new ArrayList<>();
    for (NewstartKnowledgeItemResponse item : ctx.official()) sources.add(toSourceInfo(item));
    for (NewstartKnowledgeItemResponse item : ctx.ebook()) sources.add(toSourceInfo(item));
    for (NewstartKnowledgeItemResponse item : ctx.caseItems()) sources.add(toSourceInfo(item));
    return sources;
  }

  private List<NewstartKnowledgeItemResponse> pickBucket(
      List<NewstartKnowledgeItemResponse> ranked, Set<String> types, int max) {
    return ranked.stream()
        .filter(item -> types.contains(item.getSourceType()))
        .limit(max)
        .collect(Collectors.toList());
  }

  private int score(NewstartKnowledgeItemResponse item, Set<String> keywords, Set<Long> conflictingIds) {
    int s = 0;
    s += 3 * countMatches(item.getTitle(), keywords);
    s += 2 * countMatches(item.getTags(), keywords);
    s += 2 * countMatches(item.getCategory(), keywords);
    s += countMatches(item.getSummary(), keywords);
    s += countMatches(item.getContent(), keywords);
    if (conflictingIds.contains(item.getKnowledgeId())) {
      s += CONFLICT_SCORE_BONUS;
    }
    return s;
  }

  private int countMatches(String field, Set<String> keywords) {
    if (field == null || field.isBlank()) return 0;
    int c = 0;
    for (String kw : keywords) {
      if (field.contains(kw)) c++;
    }
    return c;
  }

  private Set<String> tokenize(String text) {
    if (text == null) return Set.of();
    return Arrays.stream(text.replaceAll("[^가-힣a-zA-Z0-9]", " ").split("\\s+"))
        .filter(s -> s.length() >= 2)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** pool 안에서 양쪽 다 ACTIVE로 잡힌 CONTRADICTS 쌍의 knowledgeId만 모은다. */
  private Set<Long> findConflictingIds(List<NewstartKnowledgeItemResponse> pool) {
    Set<Long> poolIds = pool.stream().map(NewstartKnowledgeItemResponse::getKnowledgeId).collect(Collectors.toSet());
    Set<Long> conflicting = new HashSet<>();
    for (Long id : poolIds) {
      List<NewstartKnowledgeRelation> rels = new ArrayList<>();
      rels.addAll(relationRepository.findByFromKnowledgeId(id));
      rels.addAll(relationRepository.findByToKnowledgeId(id));
      for (NewstartKnowledgeRelation r : rels) {
        if (r.getRelationType() != NewstartKnowledgeRelation.RelationType.CONTRADICTS) continue;
        Long other = r.getFromKnowledgeId().equals(id) ? r.getToKnowledgeId() : r.getFromKnowledgeId();
        if (poolIds.contains(other)) {
          conflicting.add(id);
          conflicting.add(other);
        }
      }
    }
    return conflicting;
  }

  private void appendSection(StringBuilder sb, String label, List<NewstartKnowledgeItemResponse> items) {
    if (items.isEmpty()) return;
    sb.append('[').append(label).append("]\n");
    for (NewstartKnowledgeItemResponse item : items) {
      sb.append("- ").append(item.getTitle());
      if (item.getAuthority() != null && !item.getAuthority().isBlank()) {
        sb.append(" (").append(item.getAuthority()).append(')');
      }
      sb.append('\n').append(snippet(item)).append('\n');
    }
    sb.append('\n');
  }

  private String snippet(NewstartKnowledgeItemResponse item) {
    String text = (item.getSummary() != null && !item.getSummary().isBlank()) ? item.getSummary() : item.getContent();
    if (text == null) return "";
    text = text.strip();
    if (text.length() > MAX_SNIPPET_CHARS) {
      text = text.substring(0, MAX_SNIPPET_CHARS) + "…";
    }
    return text;
  }

  private SourceInfo toSourceInfo(NewstartKnowledgeItemResponse item) {
    return new SourceInfo(
        item.getKnowledgeId(),
        item.getSourceType(),
        item.getTitle(),
        item.getSourceUrl(),
        item.getSourceDate(),
        item.getAuthority(),
        item.getCategory());
  }

  /** OFFICIAL/EBOOK/CASE로 나뉜 ACTIVE 근거 묶음. {@code hasConflict}는 CONTRADICTS pool 매칭 결과. */
  public record RagContext(
      List<NewstartKnowledgeItemResponse> official,
      List<NewstartKnowledgeItemResponse> ebook,
      List<NewstartKnowledgeItemResponse> caseItems,
      boolean hasConflict) {

    public boolean isEmpty() {
      return official.isEmpty() && ebook.isEmpty() && caseItems.isEmpty();
    }
  }

  /** ChatResponse에 실리는 출처 메타데이터. 원문 URL은 적재 시 저장된 값 그대로이며 여기서 생성하지 않는다. */
  public record SourceInfo(
      Long knowledgeId,
      String sourceType,
      String title,
      String sourceUrl,
      LocalDate sourceDate,
      String authority,
      String category) {}
}
