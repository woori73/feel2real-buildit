package f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout;

import java.util.Arrays;
import java.util.Optional;

/**
 * Academy 페이지 레이아웃에서 허용되는 semantic block 타입의 allowlist.
 *
 * <p>각 상수는 layout_json 에 직렬화되는 안정적인 {@code id}(camelCase) 를 가진다.
 * 이 enum 에 없는 block 타입(자유 HTML/CSS 포함)은 저장이 거부된다.
 */
public enum AcademyLayoutBlockType {
  TOP_NAV("topNav"),
  HERO("hero"),
  QUOTE("quote"),
  TEACHERS("teachers"),
  CURRICULUM("curriculum"),
  CONSULTATION_CTA("consultationCta"),
  BLOG_PREVIEW("blogPreview");

  private final String id;

  AcademyLayoutBlockType(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  /** id 로 block 타입을 찾는다. 알 수 없는 id 면 empty. */
  public static Optional<AcademyLayoutBlockType> fromId(String id) {
    return Arrays.stream(values())
        .filter(type -> type.id.equals(id))
        .findFirst();
  }

  public static boolean isAllowed(String id) {
    return fromId(id).isPresent();
  }
}
