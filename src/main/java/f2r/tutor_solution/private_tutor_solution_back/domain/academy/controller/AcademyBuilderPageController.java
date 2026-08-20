package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Academy Builder page.
 *
 * <p>기존 Academy가 있는 강사는 빌더 chrome/preview 초기값을 API 재호출 없이 SSR 모델로 받는다.
 * 랜딩에서 새 발행 요청으로 진입한 경우에는 Academy가 아직 없으므로 빈 모델로 렌더하고,
 * 클라이언트가 sessionStorage에 담긴 임시 발행 데이터를 사용한다.
 */
@Controller
@RequiredArgsConstructor
public class AcademyBuilderPageController {

  static final String VIEW = "tutor/academy_builder";

  private final AcademyService academyService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @GetMapping("/builder")
  public Object builder(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model) {
    if (!hasTutorRole(principal)) {
      RedirectView redirect = new RedirectView("/");
      redirect.setExposeModelAttributes(false);
      return redirect;
    }

    AcademyResponse academy = academyService.getMine(principal).orElse(null);

    model.addAttribute("academy", academy);
    model.addAttribute("academyName", academy == null ? null : academy.name());
    model.addAttribute("academySlug", academy == null ? null : academy.slug());
    model.addAttribute("academyPublicUrl", academy == null ? null : academy.publicUrl());
    model.addAttribute("academyPathUrl", academy == null ? null : pathUrl(academy.slug()));
    model.addAttribute("academyDisplayUrl", academy == null ? null : displayUrl(academy.handle(), academy.slug()));
    model.addAttribute("academySectionVisibilityJson", sectionVisibilityJson(academy));
    model.addAttribute("academyJson", academyJson(academy));
    return VIEW;
  }

  private boolean hasTutorRole(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && "TUTOR".equals(principal.role());
  }

  private String academyJson(AcademyResponse academy) {
    if (academy == null) return "";
    try {
      return objectMapper.writeValueAsString(academy);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Academy Builder 초기 데이터를 직렬화하지 못했습니다.", e);
    }
  }

  private String sectionVisibilityJson(AcademyResponse academy) {
    if (academy == null || academy.pageSettings() == null
        || academy.pageSettings().sectionVisibility() == null
        || academy.pageSettings().sectionVisibility().isEmpty()) {
      return "{}";
    }
    StringBuilder json = new StringBuilder("{");
    academy.pageSettings().sectionVisibility().forEach((key, value) -> {
      if (key == null || value == null) return;
      if (json.length() > 1) json.append(',');
      json.append('\"').append(escapeJson(key)).append('\"').append(':').append(value);
    });
    return json.append('}').toString();
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private String pathUrl(String slug) {
    return slug == null || slug.isBlank() ? null : "/academies/" + slug;
  }

  private String displayUrl(String handle, String slug) {
    String key = handle == null || handle.isBlank() ? slug : handle;
    return key == null || key.isBlank() ? null : key + ".feel2real.co.kr";
  }
}
