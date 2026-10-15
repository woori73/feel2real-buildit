package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 갈무리 게시판(설계 §6) 회원 feed — 새 테이블 없이 {@code newstart_knowledge_items}를 읽되,
 * <b>community 수집 출처만</b> 갈무리 항목으로 노출한다.
 *
 * <p>갈무리 = NewStart에 하루 동안 모인 유용한 실제 정보(지피지기 Q&A·질문요청+운영자 답변·
 * 회원채팅·실제 사례)를 정리해서 회원이 다시 읽기 쉽게 만든 지식 게시판이다. Wiki 지식
 * ({@code OFFICIAL_*}, {@code AUDIT}, {@code EBOOK}, {@code DERIVED_RULE},
 * {@code OPERATOR_KNOWLEDGE})은 지피지기 RAG의 보조 KB일 뿐 갈무리 게시물이 아니므로 feed에서
 * 제외한다(2026-09-29 source 교정 — 이전에는 ACTIVE 전체를 그대로 노출해 갈무리가 Wiki 목록이었다).
 *
 * <p>원칙: 원문 복사 금지, PII 제거·요약을 거친 항목만 ACTIVE로 승인되어 feed에 오른다
 * (REVIEW/DRAFT는 운영자 검토함 안에 남는다). 자동 적재(scheduler/Agent pipeline)는 여전히
 * 범위 밖 — production 자동 write 금지, PHASE 2에서 AI 수집 → ADMIN preview → publish 순으로 배선한다.
 *
 * <p>§4 신뢰 등급을 라벨로 내려준다. <b>사례를 공식 규칙처럼 쓰지 않는다</b>가 목적이라, 라벨은
 * source_type에서 서버가 결정하고 프론트가 임의로 고치지 않는다.
 */
@Service
public class NewStartGalmuriService {

  static final int DEFAULT_PAGE_SIZE = 20;
  static final int MAX_PAGE_SIZE = 50;
  private static final int MAX_TAGS = 8;

  /** 갈무리 feed에 오를 수 있는 출처(커뮤니티 수집·사례 계열)만. Wiki/OFFICIAL 계열은 KB 전용이다. */
  static final Set<SourceType> FEED_SOURCE_TYPES = Set.of(
      SourceType.CASE, SourceType.CAFE_ARTICLE, SourceType.CAFE_COMMENT,
      SourceType.CHAT_DISCOVERY, SourceType.REQUEST_DISCOVERY);

  /**
   * [공식·현재] / [공식·과거] / [운영자 실제 경험] / [사례 참고] / [전자책·운영 정리].
   *
   * <p>설계 §4의 3라벨(공식·현재 / 운영자 실제 경험 / 사례 참고)을 지키되, 만료된 공식 자료와
   * 2차 정리 자료(전자책·도출 규칙)를 같은 칸에 넣으면 "사례를 공식 규칙처럼" 읽히는 문제가 있어
   * 두 칸을 더 뒀다. 만료 공식 자료를 사례로 취급하지 않기 위한 구분이다.
   */
  public static final String LABEL_OFFICIAL_CURRENT = "[공식·현재]";
  public static final String LABEL_OFFICIAL_HISTORICAL = "[공식·과거]";
  public static final String LABEL_OPERATOR_EXPERIENCE = "[운영자 실제 경험]";
  public static final String LABEL_CASE_REFERENCE = "[사례 참고]";
  public static final String LABEL_EBOOK_SUMMARY = "[전자책·운영 정리]";

  private final NewstartKnowledgeItemRepository knowledgeRepository;

  public NewStartGalmuriService(NewstartKnowledgeItemRepository knowledgeRepository) {
    this.knowledgeRepository = knowledgeRepository;
  }

  /** 지금 효력이 있는 ACTIVE 갈무리 항목(최신 출처 기준시점 순). {@code category} 지정 시 그 분류만. */
  @Transactional(readOnly = true)
  public GalmuriPage feed(String category, Integer page, Integer size) {
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pageable = PageRequest.of(p, s);
    LocalDate today = LocalDate.now();
    String cleaned = category == null || category.isBlank() ? null : category.strip();
    Page<NewstartKnowledgeItem> found = cleaned == null
        ? knowledgeRepository.findFeed(KnowledgeStatus.ACTIVE, FEED_SOURCE_TYPES, today, pageable)
        : knowledgeRepository.findFeedByCategory(KnowledgeStatus.ACTIVE, cleaned, FEED_SOURCE_TYPES, today, pageable);
    List<GalmuriCard> cards = found.getContent().stream().map(NewStartGalmuriService::toCard).toList();
    return new GalmuriPage(cards, found.hasNext());
  }

  /** 상세 — feed에 오른 항목만 열린다(비-ACTIVE·만료·Wiki 출처는 "찾을 수 없습니다"). */
  @Transactional(readOnly = true)
  public GalmuriResult detail(Long knowledgeId) {
    if (knowledgeId == null) return GalmuriResult.failed("항목이 올바르지 않습니다.");
    NewstartKnowledgeItem item = knowledgeRepository.findById(knowledgeId).orElse(null);
    if (item == null || item.getStatus() != KnowledgeStatus.ACTIVE || isExpired(item, LocalDate.now())
        || !FEED_SOURCE_TYPES.contains(item.getSourceType())) {
      return GalmuriResult.failed("갈무리 항목을 찾을 수 없습니다.");
    }
    return GalmuriResult.success(toDetail(item));
  }

  /** ACTIVE + 비만료 여부는 feed 쿼리(window)와 상세 판정이 같은 기준이어야 한다. */
  private static boolean isExpired(NewstartKnowledgeItem item, LocalDate today) {
    return item.getEffectiveTo() != null && item.getEffectiveTo().isBefore(today);
  }

  static String labelOf(SourceType sourceType) {
    if (sourceType == null) return LABEL_CASE_REFERENCE;
    return switch (sourceType) {
      case OFFICIAL_CURRENT -> LABEL_OFFICIAL_CURRENT;
      case OFFICIAL_HISTORICAL -> LABEL_OFFICIAL_HISTORICAL;
      case OPERATOR_KNOWLEDGE, AUDIT -> LABEL_OPERATOR_EXPERIENCE;
      case EBOOK, DERIVED_RULE, RULE -> LABEL_EBOOK_SUMMARY;
      case CASE, CAFE_ARTICLE, CAFE_COMMENT, CHAT_DISCOVERY, REQUEST_DISCOVERY -> LABEL_CASE_REFERENCE;
    };
  }

  /** 라벨 톤은 "공식 / 운영 / 사례" 3계 — 화면이 신뢰 등급을 색으로 즉시 구분하게 서버가 정한다. */
  static String toneOf(SourceType sourceType) {
    String label = labelOf(sourceType);
    if (label.equals(LABEL_OFFICIAL_CURRENT) || label.equals(LABEL_OFFICIAL_HISTORICAL)) return "official";
    if (label.equals(LABEL_OPERATOR_EXPERIENCE)) return "operator";
    return "case";
  }

  private static GalmuriCard toCard(NewstartKnowledgeItem item) {
    return new GalmuriCard(item.getKnowledgeId(), item.getTitle(), item.getSummary(),
        item.getCategory(), labelOf(item.getSourceType()), toneOf(item.getSourceType()),
        item.getAuthority(), text(item.getSourceDate()), text(item.getEffectiveTo()),
        tagsOf(item.getTags()));
  }

  private static GalmuriDetail toDetail(NewstartKnowledgeItem item) {
    // DB 원본(structured/RAG용)은 그대로 두고, 사용자에게 보이는 content·tags만 사람용으로 변환.
    return new GalmuriDetail(item.getKnowledgeId(), item.getTitle(), item.getSummary(),
        NewStartGalmuriDisplay.humanizeContent(item.getContent()),
        item.getCategory(), labelOf(item.getSourceType()), toneOf(item.getSourceType()),
        item.getAuthority(), text(item.getSourceDate()), text(item.getEffectiveFrom()),
        text(item.getEffectiveTo()), NewStartGalmuriDisplay.humanizeTags(tagsOf(item.getTags())),
        item.getSourceUrl());
  }

  private static List<String> tagsOf(String tags) {
    if (tags == null || tags.isBlank()) return List.of();
    return Arrays.stream(tags.split(",")).map(String::strip).filter(t -> !t.isEmpty())
        .limit(MAX_TAGS).toList();
  }

  private static String text(LocalDate date) {
    return date == null ? null : date.toString();
  }

  /** @param effectiveTo 만료 예정일이 임박하면 화면이 "확인 필요"로 전환할 수 있게 내린다(미리 계산하지 않는다). */
  public record GalmuriCard(Long id, String title, String summary, String category, String label,
      String tone, String authority, String sourceDate, String effectiveTo, List<String> tags) {}

  /** @param content 갈무리 본문(요약/구조화 완료 문장). @param sourceUrl 원출처 공식 링크. */
  public record GalmuriDetail(Long id, String title, String summary, String content, String category,
      String label, String tone, String authority, String sourceDate, String effectiveFrom,
      String effectiveTo, List<String> tags, String sourceUrl) {}

  public record GalmuriPage(List<GalmuriCard> items, boolean hasMore) {}

  /** @param errorMessage 실패 시 사용자 안내만 담는다(내부 id·예외 상세 미노출). */
  public record GalmuriResult(boolean ok, String errorMessage, GalmuriDetail item) {
    public static GalmuriResult success(GalmuriDetail detail) {
      return new GalmuriResult(true, null, detail);
    }

    public static GalmuriResult failed(String errorMessage) {
      return new GalmuriResult(false, errorMessage, null);
    }
  }
}
