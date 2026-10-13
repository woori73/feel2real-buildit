package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAssistantMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantFeatureFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  static final String ASSISTANT_VIEW = "tutor/assistant_home";

  private final AcademyService academyService;
  private final AssistantFeatureFlag assistantFlag;
  private final AcademyAssistantMessageRepository assistantMessages;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @GetMapping("/builder")
  public Object builder(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "classic", required = false) String classic,
      Model model) {
    if (!hasBuilderRole(principal)) {
      RedirectView redirect = new RedirectView("/");
      redirect.setExposeModelAttributes(false);
      return redirect;
    }

    AcademyResponse academy = academyService.getMine(principal).orElse(null);

    if (usesAssistant(principal, academy, classic)) {
      return ASSISTANT_VIEW;
    }

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

  /**
   * 대화형 화면을 쓰는 경우: 기능이 켜져 있고 classic 요청이 아니며, 학원이 아직 없거나 대화로 만든 학원이다.
   * 기존 방식으로 만든 학원(대화 기록 없음)은 공개 데이터 보호를 위해 항상 기존 빌더로 보낸다.
   */
  private boolean usesAssistant(
      JwtTokenProvider.AuthenticatedPrincipal principal, AcademyResponse academy, String classic) {
    if (classic != null || !assistantFlag.isOn(principal)) return false;
    return academy == null || assistantMessages.existsByAcademyId(academy.academyId());
  }

  private boolean hasBuilderRole(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null
        && ("TUTOR".equals(principal.role()) || "ADMIN".equals(principal.role()));
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
