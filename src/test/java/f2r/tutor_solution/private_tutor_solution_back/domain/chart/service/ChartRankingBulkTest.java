package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.RankingComputation.Ranked;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.RankingComputation.RegionKeyed;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 대량 pool 순위 회귀 테스트.
 *
 * <ul>
 *   <li>100명 이상 EVALUATABLE이어도 ranking이 전체를 반환한다 (83명에서 잘리지 않음).</li>
 *   <li>동점자가 있어도 후보가 누락되지 않고 전원 고유 순위를 가진다.</li>
 *   <li>전국 순위는 1부터 연속이다.</li>
 * </ul>
 */
class ChartRankingBulkTest {

  private static List<RegionKeyed> pool(int size, String province) {
    List<RegionKeyed> out = new ArrayList<>();
    for (long id = 1; id <= size; id++) {
      out.add(new RegionKeyed(id, province, "구" + (id % 5)));
    }
    return out;
  }

  // ---- 8. 100명 초과 pool 전체 반환 (109명) ----

  @Test
  void assign_returnsAllCandidatesBeyond100() {
    Map<Long, Ranked> ranks = RankingComputation.assign(pool(109, "서울특별시"));

    assertThat(ranks).hasSize(109);
    assertThat(ranks.get(109L).overallRank()).isEqualTo(109);
    assertThat(ranks.get(109L).overallTotal()).isEqualTo(109);
    assertThat(ranks.get(100L).top100()).isTrue();
    assertThat(ranks.get(101L).top100()).isFalse();
  }

  // ---- 9. 동점이어도 누락 없음 + 순위 연속 ----

  @Test
  void assign_continuousRanksWithNoMissingCandidate() {
    Map<Long, Ranked> ranks = RankingComputation.assign(pool(83, "경기도"));

    Set<Integer> seen = new HashSet<>();
    for (long id = 1; id <= 83; id++) {
      assertThat(ranks).containsKey(id);
      seen.add(ranks.get(id).overallRank());
    }
    assertThat(seen).hasSize(83);
    for (int rank = 1; rank <= 83; rank++) {
      assertThat(seen).contains(rank);
    }
  }

  // ---- 동점 그룹(같은 지역 다수)이 regionTotal을 공유 ----

  @Test
  void assign_sameRegionGroupSharesTotal() {
    List<RegionKeyed> ordered = List.of(
        new RegionKeyed(1L, "서울특별시", "강남구"),
        new RegionKeyed(2L, "서울특별시", "강남구"),
        new RegionKeyed(3L, "부산광역시", "해운대구"));

    Map<Long, Ranked> ranks = RankingComputation.assign(ordered);

    assertThat(ranks.get(1L).regionTotal()).isEqualTo(2);
    assertThat(ranks.get(2L).regionTotal()).isEqualTo(2);
    assertThat(ranks.get(1L).regionRank()).isEqualTo(1);
    assertThat(ranks.get(2L).regionRank()).isEqualTo(2);
    assertThat(ranks.get(3L).overallRank()).isEqualTo(3);
  }
}
