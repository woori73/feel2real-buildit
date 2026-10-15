package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 제안의 component 단위 변경 1건.
 *
 * @param componentId 대상 컴포넌트의 {@code attributes.id}
 * @param type {@code text} | {@code image} | {@code style}
 * @param after text는 새 텍스트, image는 새 src(style은 사용하지 않음)
 * @param styles style 변경의 속성→값
 * @param mediaText style의 미디어 쿼리(기본 화면이면 null). 허용값은 {@link #MOBILE_MEDIA}뿐이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProposalChange(
    String componentId, String type, String after, Map<String, String> styles, String mediaText) {

  public static final String TEXT = "text";
  public static final String IMAGE = "image";
  public static final String STYLE = "style";
  public static final String MOBILE_MEDIA = "(max-width: 768px)";
}
