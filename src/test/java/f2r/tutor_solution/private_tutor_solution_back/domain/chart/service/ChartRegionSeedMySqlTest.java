package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * V950 시드 검증: "경기도 성남시 분당구"가 BuildIt Chart Site tools의 정상 조사 지역으로
 * 등록되어 get_next_regions(= nextRegions) 응답에 나타나는지 확인한다.
 * chart_regions 레지스트리 구조를 그대로 쓰며 지역명을 코드에 하드코딩하지 않는다.
 */
@SpringBootTest
@Transactional
class ChartRegionSeedMySqlTest {

  @Autowired ChartSiteToolService service;

  @Test
  void nextRegions_includesGyeonggiSeongnamBundang_afterV950Seed() {
    // given: V947 chart_regions + V949/V950 시드가 Flyway로 적용된 로컬 DB

    // when
    List<Map<String, Object>> regions = service.nextRegions(10);

    // then
    Map<String, Object> bundang = regions.stream()
        .filter(r -> "gyeonggi-seongnam-bundang".equals(r.get("regionId")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("분당구가 조사 대상 지역에 없음: " + regions));
    assertThat(bundang).containsEntry("province", "경기도");
    assertThat(bundang).containsEntry("cityDistrict", "성남시 분당구");
    assertThat(bundang).containsEntry("status", "UNRESEARCHED");
  }

  @Test
  void nextRegions_stillIncludesSeoulGangnam_v949SeedUntouched() {
    // given / when
    List<Map<String, Object>> regions = service.nextRegions(10);

    // then: 기존 seoul-gangnam 행은 그대로 남아 있어야 한다
    assertThat(regions).anyMatch(r -> "seoul-gangnam".equals(r.get("regionId")));
  }

  @Test
  void claimRegion_transitionsBundangToInProgress() {
    // given: 조사 대기 상태의 분당구
    assertThat(service.nextRegions(10))
        .anyMatch(r -> "gyeonggi-seongnam-bundang".equals(r.get("regionId")));

    // when
    Map<String, Object> claimed = service.claimRegion("gyeonggi-seongnam-bundang");

    // then (@Transactional 이라 테스트 종료 시 롤백)
    assertThat(claimed).containsEntry("status", "IN_PROGRESS");
  }
}
