package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsBuilderSettingsDtos.LmsSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsBuilderSettingsDtos.LmsSettingsView;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builder의 "LMS 설정" 패널(작업지시서 §7). 학원마다 다른 표시명·타이틀·소개 문구를
 * 코드에 고정하지 않기 위해, 기존 {@code page_settings.builderDraft}(Map&lt;String,String&gt;) JSON
 * 패턴을 그대로 재사용한다 — {@code academies}에 문구 컬럼을 새로 추가하지 않는다.
 *
 * <p>{@code AcademyController}의 기존 {@code PUT /api/tutor/academy/page-settings}는 의도적으로
 * builderDraft를 받지 않는다(다른 builderDraft 키를 조용히 지우는 사고를 막기 위해서다). 그래서 이
 * 설정은 별도 좁은 엔드포인트로 뺐고, 여기서도 builderDraft의 다른 키(heroVideoId 등)는 항상
 * 보존한 채 LMS 5개 키만 갈아 끼운다.
 */
@Service
@RequiredArgsConstructor
public class LmsBuilderSettingsService {

  /** {@code AcademyLmsShellModel}도 같은 키/기본값으로 LMS 화면에 렌더한다 — 한 군데만 바꾸면 되게. */
  public static final String KEY_ENABLED = "lmsEnabled";
  public static final String KEY_DISPLAY_NAME = "lmsDisplayName";
  public static final String KEY_MAIN_TITLE = "lmsMainTitle";
  public static final String KEY_INTRO = "lmsIntro";
  public static final String KEY_SECTION_NAME = "lmsSectionName";

  public static final String DEFAULT_DISPLAY_NAME = "에듀홀릭 학습실";
  public static final String DEFAULT_MAIN_TITLE = "시험 전 복습수업";
  public static final String DEFAULT_INTRO = "수업에서 배운 핵심 내용을 시험 전에 다시 확인하세요.";
  public static final String DEFAULT_SECTION_NAME = "내 복습수업";

  private final AcademyService academyService;

  @Transactional(readOnly = true)
  public LmsSettingsView get(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    return toView(draftOf(academy));
  }

  @Transactional
  public LmsSettingsView update(
      JwtTokenProvider.AuthenticatedPrincipal principal, LmsSettingsUpdateRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("LMS 설정 정보가 필요합니다.");
    }
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyPageSettings current = academy.getPageSettings();
    Map<String, String> draft = new HashMap<>(draftOf(academy));
    draft.put(KEY_ENABLED, String.valueOf(request.enabled()));
    draft.put(KEY_DISPLAY_NAME, blankToDefault(request.displayName(), DEFAULT_DISPLAY_NAME));
    draft.put(KEY_MAIN_TITLE, blankToDefault(request.mainTitle(), DEFAULT_MAIN_TITLE));
    draft.put(KEY_INTRO, blankToDefault(request.intro(), DEFAULT_INTRO));
    draft.put(KEY_SECTION_NAME, blankToDefault(request.sectionName(), DEFAULT_SECTION_NAME));

    AcademyPageSettings merged = new AcademyPageSettings(
        current == null ? null : current.blogUrl(),
        current == null ? null : current.storeUrl(),
        current == null ? null : current.naverMapUrl(),
        current == null ? null : current.heroYoutubeUrl(),
        current == null ? null : current.sectionVisibility(),
        current == null ? null : current.sectionOrder(),
        draft,
        current == null ? null : current.businessInfo());

    try {
      AuditorContextHolder.set(principal.loginId());
      academy.updatePageSettings(merged);
    } finally {
      AuditorContextHolder.clear();
    }
    return toView(draft);
  }

  private static Map<String, String> draftOf(Academy academy) {
    AcademyPageSettings settings = academy.getPageSettings();
    if (settings == null || settings.builderDraft() == null) {
      return Map.of();
    }
    return settings.builderDraft();
  }

  private static LmsSettingsView toView(Map<String, String> draft) {
    return new LmsSettingsView(
        !"false".equals(draft.get(KEY_ENABLED)),
        blankToDefault(draft.get(KEY_DISPLAY_NAME), DEFAULT_DISPLAY_NAME),
        blankToDefault(draft.get(KEY_MAIN_TITLE), DEFAULT_MAIN_TITLE),
        blankToDefault(draft.get(KEY_INTRO), DEFAULT_INTRO),
        blankToDefault(draft.get(KEY_SECTION_NAME), DEFAULT_SECTION_NAME));
  }

  private static String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
