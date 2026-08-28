package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

/** 교육자가 공개적으로 운영하는 공식 채널. 개인 연락처가 아닌 공개 URL만 담는다. */
public record ChartPublicOfficialChannels(
    String homepage,
    String blog,
    String youtube,
    String social
) {
  public boolean isEmpty() {
    return homepage == null && blog == null && youtube == null && social == null;
  }
}
