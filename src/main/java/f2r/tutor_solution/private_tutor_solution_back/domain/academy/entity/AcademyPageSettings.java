package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import java.util.List;
import java.util.Map;

/**
 * Academy 공개페이지 표시전용 설정(쿼리 대상 아님). {@code Academy.page_settings} JSON 컬럼에
 * {@link org.hibernate.annotations.JdbcTypeCode}(SqlTypes.JSON) 네이티브 매핑으로 저장된다.
 *
 * <p>Phase A 범위: 외부 연동 URL(blogUrl/storeUrl/naverMapUrl), 히어로 YouTube URL(heroYoutubeUrl),
 * 섹션 표시토글(sectionVisibility), 섹션 순서(sectionOrder), fixed-chrome footer 사업자 정보(businessInfo).
 * Builder 확장 입력과 홈페이지 업로드 영상 ID는 builderDraft에 문자열 key/value로 보관한다.
 * 모든 필드는 null 을 허용한다.
 */
public record AcademyPageSettings(
    String blogUrl,
    String storeUrl,
    String naverMapUrl,
    String heroYoutubeUrl,
    Map<String, Boolean> sectionVisibility,
    List<String> sectionOrder,
    Map<String, String> builderDraft,
    AcademyBusinessInfo businessInfo) {

  public AcademyPageSettings(
      String blogUrl,
      String storeUrl,
      String naverMapUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility,
      List<String> sectionOrder,
      Map<String, String> builderDraft) {
    this(blogUrl, storeUrl, naverMapUrl, heroYoutubeUrl, sectionVisibility, sectionOrder, builderDraft, null);
  }

  public AcademyPageSettings(
      String blogUrl,
      String storeUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility) {
    this(blogUrl, storeUrl, null, heroYoutubeUrl, sectionVisibility, null, null, null);
  }

  public AcademyPageSettings(
      String blogUrl,
      String storeUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility,
      Map<String, String> builderDraft) {
    this(blogUrl, storeUrl, null, heroYoutubeUrl, sectionVisibility, null, builderDraft, null);
  }

  public AcademyPageSettings(
      String blogUrl,
      String storeUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility,
      List<String> sectionOrder,
      Map<String, String> builderDraft) {
    this(blogUrl, storeUrl, null, heroYoutubeUrl, sectionVisibility, sectionOrder, builderDraft, null);
  }
}
