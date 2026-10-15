package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BuildIt Ranking 순위 배정 — 순수 계산(DB 접근 없음).
 *
 * <p>입력은 이미 <b>현재 publication 순서</b>(= {@code total_score DESC, candidate_id ASC},
 * {@link ChartSiteToolService#autoPublishNationwide}와 동일한 규칙)대로 정렬된 목록이라고 가정한다.
 * 여기서 동점을 새로 해석하지 않는다 — publication 이 이미 확정한 순서를 그대로 위치(1-based)로 읽는다.
 *
 * <ul>
 *   <li>전국 순위 = 입력 순서상의 위치(1..N). 모든 교육자가 고유 순위를 가진다(동점은 candidate_id 로 이미 갈림).</li>
 *   <li>지역 순위 = 같은 (province, cityDistrict) 그룹 안에서의 위치. 그룹 크기가 regionTotal.</li>
 *   <li>TOP100 = 전국 순위 &le; 100 (publication 의 RANKED entry 와 동일 집합).</li>
 * </ul>
 */
final class RankingComputation {

  private RankingComputation() {}

  /** 한 교육자의 순위 결과. */
  record Ranked(long candidateId, int overallRank, int overallTotal,
                int regionRank, int regionTotal, boolean top100) {}

  /**
   * @param orderedCandidates publication 순서대로 정렬된 {@code (candidateId, province, cityDistrict)} 목록
   * @return candidateId → 순위 결과 (입력 순서 유지)
   */
  static Map<Long, Ranked> assign(List<RegionKeyed> orderedCandidates) {
    int total = orderedCandidates.size();
    Map<String, Integer> regionSize = new java.util.HashMap<>();
    for (RegionKeyed c : orderedCandidates) {
      regionSize.merge(regionKey(c), 1, Integer::sum);
    }
    Map<String, Integer> regionSeen = new java.util.HashMap<>();
    Map<Long, Ranked> out = new LinkedHashMap<>();
    int position = 0;
    for (RegionKeyed c : orderedCandidates) {
      position++;
      String rk = regionKey(c);
      int regionRank = regionSeen.merge(rk, 1, Integer::sum);
      out.put(c.candidateId(), new Ranked(
          c.candidateId(), position, total, regionRank, regionSize.get(rk), position <= 100));
    }
    return out;
  }

  /** 특정 지역만 순위를 매길 때: 전국 순위는 전체 목록에서, 지역 순위는 그 지역 부분집합에서. */
  static List<Ranked> region(List<RegionKeyed> orderedCandidates, String province, String cityDistrict) {
    Map<Long, Ranked> all = assign(orderedCandidates);
    List<Ranked> result = new ArrayList<>();
    for (RegionKeyed c : orderedCandidates) {
      if (matches(c, province, cityDistrict)) {
        result.add(all.get(c.candidateId()));
      }
    }
    return result;
  }

  private static boolean matches(RegionKeyed c, String province, String cityDistrict) {
    if (!eq(nz(c.province()), nz(province))) return false;
    if (cityDistrict != null && !cityDistrict.isBlank()) {
      return eq(nz(c.cityDistrict()), nz(cityDistrict));
    }
    return true;
  }

  private static String regionKey(RegionKeyed c) {
    return nz(c.province()) + "|" + nz(c.cityDistrict());
  }

  private static String nz(String s) { return s == null ? "" : s.trim(); }
  private static boolean eq(String a, String b) { return a.equals(b); }

  /** 순위 계산에 필요한 최소 정보. */
  record RegionKeyed(long candidateId, String province, String cityDistrict) {}
}
