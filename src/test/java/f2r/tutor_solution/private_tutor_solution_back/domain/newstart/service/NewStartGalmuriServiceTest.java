package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 갈무리 feed(설계 §6) 계약 — 새 테이블 없이 knowledge_items를 읽고, 신뢰 라벨(§4)을 서버가 결정한다.
 *
 * <p>보는 것: (1) ACTIVE + 비만료만 화면에 나간다(REVIEW/DRAFT·만료는 운영자 검토함 영역),
 * (2) source_type → 라벨/톤 대응이 "사례를 공식 규칙처럼 쓰지 않는다"를 지키는지,
 * (3) 상세는 목록과 같은 기준(만료·미승인 = 404 안내), (4) 회원 개인정보성 필드(created_by,
 * related_*_ids)는 view에 아예 존재하지 않는다, (5) 페이지 크기 상한.
 */
class NewStartGalmuriServiceTest {

  private NewstartKnowledgeItemRepository repository;
  private NewStartGalmuriService service;

  @BeforeEach
  void setUp() {
    repository = mock(NewstartKnowledgeItemRepository.class);
    service = new NewStartGalmuriService(repository);
  }

  private static NewstartKnowledgeItem item(long id, SourceType type, KnowledgeStatus status,
      LocalDate effectiveTo) {
    NewstartKnowledgeItem item = new NewstartKnowledgeItem();
    item.setKnowledgeId(id);
    item.setSourceType(type);
    item.setStatus(status);
    item.setCategory("추심 대응");
    item.setTitle("채권추심 연락 절차");
    item.setSummary("연락 빈도 제한을 요청할 수 있습니다");
    item.setContent("갈무리 본문");
    item.setAuthority("금융감독원");
    item.setTags("추심,연락, 절차 ,");
    item.setSourceUrl("https://www.fss.or.kr");
    item.setSourceDate(LocalDate.of(2026, 9, 1));
    item.setEffectiveFrom(LocalDate.of(2026, 1, 1));
    item.setEffectiveTo(effectiveTo);
    return item;
  }

  @Test
  @DisplayName("given ACTIVE 항목 / when feed / then 오늘 기준 window로 조회하고 카드에 라벨·톤·tag가 오른다")
  void feedUsesActiveStatusAndTodayWindow() {
    when(repository.findFeed(eq(KnowledgeStatus.ACTIVE), any(LocalDate.class), any()))
        .thenReturn(new PageImpl<>(List.of(item(1L, SourceType.OFFICIAL_CURRENT,
            KnowledgeStatus.ACTIVE, null)), PageRequest.of(0, 20), 1));

    GalmuriPage page = service.feed(null, null, null);

    verify(repository).findFeed(eq(KnowledgeStatus.ACTIVE), eq(LocalDate.now()),
        eq(PageRequest.of(0, 20)));
    assertThat(page.items()).hasSize(1);
    var card = page.items().get(0);
    assertThat(card.label()).isEqualTo(NewStartGalmuriService.LABEL_OFFICIAL_CURRENT);
    assertThat(card.tone()).isEqualTo("official");
    assertThat(card.authority()).isEqualTo("금융감독원");
    assertThat(card.sourceDate()).isEqualTo("2026-09-01");
    // 공백·빈 항목 정리, 상한 8개.
    assertThat(card.tags()).containsExactly("추심", "연락", "절차");
  }

  @Test
  @DisplayName("given category 지정 / when feed / then 분류 쿼리로 갈리고 page/size 상한(50)이 적용된다")
  void feedClampsSizeAndFiltersCategory() {
    when(repository.findFeedByCategory(eq(KnowledgeStatus.ACTIVE), eq("추심 대응"), any(LocalDate.class), any()))
        .thenReturn(new PageImpl<>(List.of()));

    service.feed("  추심 대응  ", 2, 999);

    verify(repository, never()).findFeed(any(), any(), any());
    verify(repository).findFeedByCategory(eq(KnowledgeStatus.ACTIVE), eq("추심 대응"),
        eq(LocalDate.now()), eq(PageRequest.of(2, 50)));
  }

  @Test
  @DisplayName("given source_type 11종 / when 라벨 결정 / then 공식/운영/사례 등급이 섞이지 않는다")
  void labelsMatchTrustTiers() {
    assertThat(NewStartGalmuriService.labelOf(SourceType.OFFICIAL_CURRENT))
        .isEqualTo(NewStartGalmuriService.LABEL_OFFICIAL_CURRENT);
    assertThat(NewStartGalmuriService.labelOf(SourceType.OFFICIAL_HISTORICAL))
        .isEqualTo(NewStartGalmuriService.LABEL_OFFICIAL_HISTORICAL);
    assertThat(NewStartGalmuriService.labelOf(SourceType.OPERATOR_KNOWLEDGE))
        .isEqualTo(NewStartGalmuriService.LABEL_OPERATOR_EXPERIENCE);
    assertThat(NewStartGalmuriService.labelOf(SourceType.AUDIT))
        .isEqualTo(NewStartGalmuriService.LABEL_OPERATOR_EXPERIENCE);
    // §4: 사례를 공식 규칙처럼 쓰지 않는다 — 채팅·질문요청 갈무리도 사례 등급으로 시작한다.
    assertThat(NewStartGalmuriService.labelOf(SourceType.CHAT_DISCOVERY))
        .isEqualTo(NewStartGalmuriService.LABEL_CASE_REFERENCE);
    assertThat(NewStartGalmuriService.labelOf(SourceType.REQUEST_DISCOVERY))
        .isEqualTo(NewStartGalmuriService.LABEL_CASE_REFERENCE);
    assertThat(NewStartGalmuriService.labelOf(SourceType.CAFE_ARTICLE))
        .isEqualTo(NewStartGalmuriService.LABEL_CASE_REFERENCE);
    assertThat(NewStartGalmuriService.labelOf(SourceType.EBOOK))
        .isEqualTo(NewStartGalmuriService.LABEL_EBOOK_SUMMARY);
    assertThat(NewStartGalmuriService.labelOf(SourceType.DERIVED_RULE))
        .isEqualTo(NewStartGalmuriService.LABEL_EBOOK_SUMMARY);
    assertThat(NewStartGalmuriService.toneOf(SourceType.OFFICIAL_HISTORICAL)).isEqualTo("official");
    assertThat(NewStartGalmuriService.toneOf(SourceType.AUDIT)).isEqualTo("operator");
    assertThat(NewStartGalmuriService.toneOf(SourceType.CHAT_DISCOVERY)).isEqualTo("case");
  }

  @Test
  @DisplayName("given REVIEW/DRAFT·만료 항목 / when detail / then 404 안내로 숨기고 본문을 내리지 않는다")
  void detailHidesUnapprovedAndExpired() {
    NewstartKnowledgeItem review = item(2L, SourceType.CASE, KnowledgeStatus.REVIEW, null);
    when(repository.findById(2L)).thenReturn(Optional.of(review));
    assertThat(service.detail(2L).errorMessage()).contains("찾을 수 없습니다");
    assertThat(service.detail(2L).item()).isNull();

    NewstartKnowledgeItem draft = item(5L, SourceType.CAFE_ARTICLE, KnowledgeStatus.DRAFT, null);
    when(repository.findById(5L)).thenReturn(Optional.of(draft));
    assertThat(service.detail(5L).ok()).isFalse();

    NewstartKnowledgeItem expired = item(3L, SourceType.OFFICIAL_CURRENT, KnowledgeStatus.ACTIVE,
        LocalDate.now().minusDays(1));
    when(repository.findById(3L)).thenReturn(Optional.of(expired));
    assertThat(service.detail(3L).ok()).isFalse();
    // 만료 항목의 본문은 상세에서 회수된다 — [공식·과거]로 재분류되기 전까지 노출하지 않는다.
    assertThat(service.detail(3L).item()).isNull();

    NewstartKnowledgeItem archived = item(6L, SourceType.CASE, KnowledgeStatus.ARCHIVED, null);
    when(repository.findById(6L)).thenReturn(Optional.of(archived));
    assertThat(service.detail(6L).ok()).isFalse();
  }

  @Test
  @DisplayName("given ACTIVE 상세 / when detail / then 본문·원출처 링크가 내려온다")
  void detailReturnsCuratedContent() {
    when(repository.findById(9L)).thenReturn(Optional.of(item(9L, SourceType.CASE, KnowledgeStatus.ACTIVE, null)));

    GalmuriResult result = service.detail(9L);

    assertThat(result.ok()).isTrue();
    assertThat(result.item().content()).isEqualTo("갈무리 본문");
    assertThat(result.item().sourceUrl()).isEqualTo("https://www.fss.or.kr");
    assertThat(result.item().label()).isEqualTo(NewStartGalmuriService.LABEL_CASE_REFERENCE);
  }
}
