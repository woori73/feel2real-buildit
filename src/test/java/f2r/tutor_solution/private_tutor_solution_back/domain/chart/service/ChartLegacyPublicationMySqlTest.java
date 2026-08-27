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
 * PR-B: Legacy V3 43명 import(V953) + 최초 전국 publication 부트스트랩 검증.
 *   · Legacy 선정자는 확보된 정보(선정 이유·활동 근거)까지만 노출하고 placeholder Score는 쓰지 않는다.
 *   · /api/chart/publication/current 가 실제 교육자를 반환한다(빈 화면 아님).
 */
@SpringBootTest
@Transactional
class ChartLegacyPublicationMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired ChartPublicService publicService;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void v953_importsLegacySelectedEducators_withoutPii_orPlaceholderScore() {
    Integer legacy = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_candidates WHERE legacy_source IN ('V3_AUTO','V3_MANUAL')", Integer.class);
    assertThat(legacy).isEqualTo(43);

    // 개인정보/placeholder score 미저장
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_candidates WHERE legacy_source IS NOT NULL AND (source_url LIKE '%@%' OR content_summary REGEXP '01[0-9]-[0-9]{3,4}-[0-9]{4}')",
        Integer.class)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_score_snapshots s JOIN chart_candidates c ON c.candidate_id=s.candidate_id WHERE c.legacy_source IS NOT NULL AND s.total_score = 2",
        Integer.class)).as("legacy placeholder 2.0을 현행 Score로 저장하지 않음").isZero();
  }

  @Test
  void rebuildNationwidePublication_publishesLegacyEducators() {
    Map<String, Object> result = siteToolService.rebuildNationwidePublication();
    assertThat((Boolean) result.get("publicationChanged")).isTrue();

    Map<String, Object> current = publicService.currentPublication();
    assertThat(current.get("publicationId")).isNotEqualTo("legacy");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ranked = (List<Map<String, Object>>) current.get("rankedTop100");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> discovered = (List<Map<String, Object>>) current.get("discovered");
    assertThat(ranked.size() + discovered.size()).isGreaterThanOrEqualTo(43);

    // 상세 카드가 확보된 정보(선정 이유)를 담고, 없는 교육철학은 비워둔다
    Map<String, Object> any = !ranked.isEmpty() ? ranked.get(0) : discovered.get(0);
    assertThat(any).containsKeys("name", "role", "activityRegion", "buildItScore", "discoveryReason", "activities");
  }

  @Test
  void currentPublication_isNoLongerEmpty_afterRebuild() {
    siteToolService.rebuildNationwidePublication();
    Map<String, Object> current = publicService.currentPublication();
    int shown = ((List<?>) current.get("rankedTop100")).size() + ((List<?>) current.get("discovered")).size();
    assertThat(shown).as("웹에 실제 표시되는 교육자 수").isPositive();
  }
}
