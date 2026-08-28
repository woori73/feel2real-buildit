package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

/**
 * 교육자가 공개적으로 운영하는 공식 채널. 개인 연락처가 아닌 공개 URL만 담는다.
 *
 * <p>JSON 응답에는 record component(homepage/blog/youtube/social)만 노출되어야 한다.
 * 헬퍼는 {@code is}/{@code get} 접두사를 피해 Jackson getter 로 직렬화되지 않게 한다.
 */
public record ChartPublicOfficialChannels(
    String homepage,
    String blog,
    String youtube,
    String social
) {
  /** 모든 채널이 비어 있으면 true. Jackson getter 규칙(is/get)에 걸리지 않는 이름을 쓴다. */
  public boolean hasNoChannels() {
    return homepage == null && blog == null && youtube == null && social == null;
  }
}
