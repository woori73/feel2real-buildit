package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto;

/** Builder "LMS 설정" 패널(작업지시서 §7) DTO. {@code Academy.pageSettings.builderDraft}에 저장된다. */
public final class LmsBuilderSettingsDtos {

  private LmsBuilderSettingsDtos() {}

  public record LmsSettingsView(
      boolean enabled, String displayName, String mainTitle, String intro, String sectionName) {}

  public record LmsSettingsUpdateRequest(
      boolean enabled, String displayName, String mainTitle, String intro, String sectionName) {}
}
