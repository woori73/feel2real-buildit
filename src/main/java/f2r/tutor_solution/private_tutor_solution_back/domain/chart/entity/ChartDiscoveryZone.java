package f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChartDiscoveryZone {
  APGUJEONG_SINSA(
      "apgujeong-sinsa",
      "압구정·신사",
      List.of("압구정", "압구정동", "신사", "신사동"),
      List.of("압구정중", "신구중", "압구정고등학교", "신사중"),
      true),
  CHEONGDAM("cheongdam", "청담", List.of("청담", "청담동"), List.of(), false),
  NONHYEON("nonhyeon", "논현", List.of("논현", "논현동"), List.of(), false),
  YEOKSAM("yeoksam", "역삼", List.of("역삼", "역삼동"), List.of(), false),
  DOGOK("dogok", "도곡", List.of("도곡", "도곡동"), List.of(), false),
  GAEPO("gaepo", "개포", List.of("개포", "개포동"), List.of(), false),
  ILWON_SUSEO("ilwon-suseo", "일원·수서", List.of("일원", "일원동", "수서", "수서동"), List.of(), false),
  DAECHI("daechi", "대치", List.of("대치", "대치동"), List.of(), false);

  private final String id;
  private final String label;
  private final List<String> searchAreas;
  private final List<String> schoolTerms;
  private final boolean enabledForDiscovery;

  public static ChartDiscoveryZone fromId(String id) {
    return Arrays.stream(values())
        .filter(zone -> zone.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 검색권역입니다."));
  }
}
