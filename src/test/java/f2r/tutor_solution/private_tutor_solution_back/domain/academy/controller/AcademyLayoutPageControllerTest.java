package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlock;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlockType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto.AcademyBlogPostResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@DisplayName("AcademyLayoutPageController — 레이아웃 기반 공개 렌더 SSR")
class AcademyLayoutPageControllerTest {

  private final AcademyService academyService = mock(AcademyService.class);
  private final AcademyPageLayoutService academyPageLayoutService = mock(AcademyPageLayoutService.class);
  private final AcademyBlogService academyBlogService = mock(AcademyBlogService.class);

  private final AcademyLayoutPageController controller =
      new AcademyLayoutPageController(academyService, academyPageLayoutService, academyBlogService);

  @Test
  @DisplayName("preview — given 공개 Academy + 레이아웃 when 렌더 then 모델에 정렬된 blocks·academyName·뷰 이름")
  void preview_sets_model_and_view() {
    // given
    String slug = "alpha";
    Academy academy = mock(Academy.class);
    when(academy.getName()).thenReturn("알파학원");
    when(academyService.getPublishedEntity(slug)).thenReturn(academy);
    when(academyPageLayoutService.resolveLayoutSchema(academy)).thenReturn(
        new AcademyPageLayoutSchema(1, List.of(
            new AcademyLayoutBlock("hero", 1, Map.of()),
            new AcademyLayoutBlock("topNav", 0, Map.of()))));
    Model model = new ConcurrentModel();

    // when
    String view = controller.preview(slug, model);

    // then
    assertThat(view).isEqualTo("public/academy_layout_preview");
    assertThat(model.getAttribute("academyName")).isEqualTo("알파학원");
    assertThat(model.getAttribute("academySlug")).isEqualTo(slug);
    @SuppressWarnings("unchecked")
    List<AcademyLayoutBlock> blocks = (List<AcademyLayoutBlock>) model.getAttribute("blocks");
    assertThat(blocks).extracting(AcademyLayoutBlock::type).containsExactly("topNav", "hero");
  }

  @Test
  @DisplayName("preview — given blogPreview block(limit=2) when 렌더 then 블로그 글을 limit개로 제한")
  void preview_limits_blog_preview() {
    // given
    String slug = "alpha";
    Academy academy = mock(Academy.class);
    when(academyService.getPublishedEntity(slug)).thenReturn(academy);
    when(academyPageLayoutService.resolveLayoutSchema(academy)).thenReturn(
        new AcademyPageLayoutSchema(1, List.of(
            new AcademyLayoutBlock(AcademyLayoutBlockType.BLOG_PREVIEW.id(), 0, Map.of("limit", 2)))));
    when(academyBlogService.listPublic(slug)).thenReturn(List.of(
        mock(AcademyBlogPostResponse.class),
        mock(AcademyBlogPostResponse.class),
        mock(AcademyBlogPostResponse.class)));
    Model model = new ConcurrentModel();

    // when
    controller.preview(slug, model);

    // then
    @SuppressWarnings("unchecked")
    List<AcademyBlogPostResponse> posts = (List<AcademyBlogPostResponse>) model.getAttribute("blogPosts");
    assertThat(posts).hasSize(2);
  }

  @Test
  @DisplayName("preview — given blogPreview block 없음 when 렌더 then blogPosts 비고 블로그 조회 안 함")
  void preview_skips_blog_when_no_preview_block() {
    // given
    String slug = "alpha";
    Academy academy = mock(Academy.class);
    when(academyService.getPublishedEntity(slug)).thenReturn(academy);
    when(academyPageLayoutService.resolveLayoutSchema(academy)).thenReturn(
        new AcademyPageLayoutSchema(1, List.of(
            new AcademyLayoutBlock("hero", 0, Map.of()))));
    Model model = new ConcurrentModel();

    // when
    controller.preview(slug, model);

    // then
    @SuppressWarnings("unchecked")
    List<AcademyBlogPostResponse> posts = (List<AcademyBlogPostResponse>) model.getAttribute("blogPosts");
    assertThat(posts).isEmpty();
    verify(academyBlogService, never()).listPublic(any());
  }
}
