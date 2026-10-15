package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlock;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlockType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto.AcademyBlogPostResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Comparator;
import java.util.List;

/**
 * ⚠ 추후 추가 예정: 이 preview 페이지({@code /academies/{slug}/preview})는 아직 어떤 화면에서도
 * 링크되지 않은 휴면 라우트다(진입점 미연결, 직접 URL로만 도달). 공개 Academy 페이지에 정식
 * 연결하는 작업은 나중에 별도로 진행한다.
 *
 * Academy Builder 공개 렌더(Phase 3) — 저장/기본 레이아웃 schema를 서버에서 Thymeleaf로 그린다.
 *
 * <p>기존 CSR 공개 페이지({@code /academies/{slug}}, academy_public.js)는 그대로 두고
 * 신규 경로 {@code /academies/{slug}/preview}에 layout 기반 SSR 페이지를 병행 추가한다.
 * 레이아웃이 없는 레거시 Academy도 {@link AcademyPageLayoutService#resolveLayoutSchema}가
 * 기본 레이아웃을 결정적으로 만들어주므로 안전하게 렌더된다.
 *
 * <p>block의 텍스트 값은 user-generated content이므로 템플릿에서 {@code th:text}로 escape한다
 * ({@code th:utext} 금지).
 */
@Controller
@RequiredArgsConstructor
public class AcademyLayoutPageController {

  private static final String VIEW = "public/academy_layout_preview";
  private static final int DEFAULT_BLOG_LIMIT = 3;

  private final AcademyService academyService;
  private final AcademyPageLayoutService academyPageLayoutService;
  private final AcademyBlogService academyBlogService;

  @GetMapping("/academies/{slug}/preview")
  public String preview(@PathVariable String slug, Model model) {
    Academy academy = academyService.getPublishedEntity(slug);
    AcademyPageLayoutSchema schema = academyPageLayoutService.resolveLayoutSchema(academy);

    List<AcademyLayoutBlock> blocks = schema.blocks().stream()
        .sorted(Comparator.comparingInt(AcademyLayoutBlock::order))
        .toList();

    model.addAttribute("academyName", academy.getName());
    model.addAttribute("academySlug", slug);
    model.addAttribute("blocks", blocks);
    model.addAttribute("blogPosts", resolveBlogPreview(slug, blocks));
    return VIEW;
  }

  /**
   * blogPreview block이 있으면 해당 Academy의 공개 블로그 글을 최신순으로 limit개 가져온다.
   * block이 없으면 빈 목록을 반환한다.
   */
  private List<AcademyBlogPostResponse> resolveBlogPreview(String slug, List<AcademyLayoutBlock> blocks) {
    return blocks.stream()
        .filter(block -> AcademyLayoutBlockType.BLOG_PREVIEW.id().equals(block.type()))
        .findFirst()
        .map(block -> academyBlogService.listPublic(slug).stream()
            .limit(blogLimit(block))
            .toList())
        .orElseGet(List::of);
  }

  /** blogPreview block config의 {@code limit}(JSON number)을 읽는다. 없거나 비정상이면 기본값. */
  private long blogLimit(AcademyLayoutBlock block) {
    Object limit = block.config() == null ? null : block.config().get("limit");
    if (limit instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    return DEFAULT_BLOG_LIMIT;
  }
}
