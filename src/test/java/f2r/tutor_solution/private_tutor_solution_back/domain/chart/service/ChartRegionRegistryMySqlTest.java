package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V952 전국 chart_regions seed 검증.
 *   · 운영자 확정 조사 완료 지역(서울 25개 구 + 부산 해운대구 + 분당구 = 27개)은 get_next_regions 에서 제외된다.
 *   · 아직 조사하지 않은 전국 시·군·구는 UNRESEARCHED 로 반환 대상이 된다.
 *   · 기존 운영 region ID(seoul-gangnam, gyeonggi-seongnam-bundang) 데이터는 깨지지 않는다.
 */
@SpringBootTest
@Transactional
class ChartRegionRegistryMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void nationwideSeed_registersEveryProvinceAndKeepsExistingRegionIds() {
    // given: V952 적용 후
    Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_regions", Integer.class);
    // when / then: 전국 규모(최소 220개 이상) + 기존 운영 ID 보존
    assertThat(total).isGreaterThanOrEqualTo(220);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_regions WHERE region_id='gyeonggi-seongnam-bundang'", Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_regions WHERE region_id='busan-haeundae'", Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForList(
        "SELECT DISTINCT province FROM chart_regions", String.class)).hasSizeGreaterThanOrEqualTo(17);
  }

  @Test
  void getNextRegions_excludesResearchedRegions_andReturnsUnresearched() {
    // when
    List<Map<String, Object>> next = siteToolService.nextRegions(10);
    List<String> ids = next.stream().map(r -> String.valueOf(r.get("regionId"))).toList();

    // then: 조사 완료 지역(서울 전 구 + 해운대 + 분당)은 나오지 않는다
    assertThat(ids).doesNotContain("seoul-seocho", "seoul-gangnam", "seoul-jung", "busan-haeundae", "gyeonggi-seongnam-bundang");
    // 그리고 모든 반환 지역은 재조사 대상 상태다
    assertThat(next).allSatisfy(r ->
        assertThat(r.get("status")).isIn("UNRESEARCHED", "DUE", "ERROR"));
    // 조사 완료 승계 지역은 RESEARCHED 로 남아 있다
    assertThat(jdbcTemplate.queryForObject(
        "SELECT status FROM chart_regions WHERE region_id='seoul-seocho'", String.class)).isEqualTo("RESEARCHED");
  }

  @Test
  void researchedLegacyRegions_haveNextResearchDateForNinetyDayCycle() {
    // then: 승계 지역은 next_research_at 이 채워져 90일 뒤 DUE 로 순환된다
    Integer withoutDate = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_regions WHERE status='RESEARCHED' AND next_research_at IS NULL", Integer.class);
    assertThat(withoutDate).isZero();
  }
}
