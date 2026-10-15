package f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기존 Academy 데이터로부터 기본 semantic 레이아웃을 생성한다.
 *
 * <p>생성 결과는 allowlist block 만 포함하며, block 순서는 다음으로 고정된다:
 * topNav → hero → quote → teachers → curriculum → consultationCta → blogPreview.
 * 아직 콘텐츠가 없는 영역(teachers 등)은 안전한 placeholder 로 채운다.
 */
@Component
public class AcademyDefaultLayoutFactory {

  private static final int SCHEMA_VERSION = 1;

  public AcademyPageLayoutSchema generate(Academy academy) {
    List<AcademyLayoutBlock> blocks = List.of(
        block(AcademyLayoutBlockType.TOP_NAV, 0, topNavConfig(academy)),
        block(AcademyLayoutBlockType.HERO, 1, heroConfig(academy)),
        block(AcademyLayoutBlockType.QUOTE, 2, quoteConfig()),
        block(AcademyLayoutBlockType.TEACHERS, 3, teachersConfig()),
        block(AcademyLayoutBlockType.CURRICULUM, 4, curriculumConfig()),
        block(AcademyLayoutBlockType.CONSULTATION_CTA, 5, consultationCtaConfig()),
        block(AcademyLayoutBlockType.BLOG_PREVIEW, 6, blogPreviewConfig())
    );
    return new AcademyPageLayoutSchema(SCHEMA_VERSION, blocks);
  }

  private AcademyLayoutBlock block(AcademyLayoutBlockType type, int order, Map<String, Object> config) {
    return new AcademyLayoutBlock(type.id(), order, config);
  }

  private Map<String, Object> topNavConfig(Academy academy) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("title", academy.getName());
    return config;
  }

  private Map<String, Object> heroConfig(Academy academy) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("headline", academy.getName());
    config.put("intro", academy.getIntro());
    return config;
  }

  private Map<String, Object> quoteConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("placeholder", true);
    config.put("text", "");
    return config;
  }

  private Map<String, Object> teachersConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("placeholder", true);
    config.put("heading", "선생님 소개");
    config.put("teachers", List.of());
    return config;
  }

  private Map<String, Object> curriculumConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("placeholder", true);
    config.put("items", List.of());
    return config;
  }

  private Map<String, Object> consultationCtaConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("state", "PREPARING");
    config.put("enabled", false);
    return config;
  }

  private Map<String, Object> blogPreviewConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("placeholder", true);
    config.put("limit", 3);
    return config;
  }
}
