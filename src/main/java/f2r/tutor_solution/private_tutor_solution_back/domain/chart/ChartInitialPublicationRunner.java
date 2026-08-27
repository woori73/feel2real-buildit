package f2r.tutor_solution.private_tutor_solution_back.domain.chart;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 최초 공개 부트스트랩. 아직 PUBLISHED publication 이 하나도 없는데 이미 조사·선정된 교육자
 * (Legacy V3 import + 신규 조사)가 있으면 앱 시작 시 1회 전국 누적 snapshot 을 만든다.
 *
 * <p>이후에는 지역 조사 완료(complete_region)가 publication 을 갱신하므로 이 runner 는 다시 동작하지
 * 않는다(멱등). AI/WebMCP 명령이 아니라 서버 내부 부트스트랩이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartInitialPublicationRunner {

  private final JdbcTemplate jdbcTemplate;
  private final ChartSiteToolService chartSiteToolService;

  @EventListener(ApplicationReadyEvent.class)
  public void bootstrapPublicationIfMissing() {
    try {
      Integer published = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM chart_publications WHERE status = 'PUBLISHED'", Integer.class);
      if (published != null && published > 0) {
        return;
      }
      var result = chartSiteToolService.rebuildNationwidePublication();
      if (Boolean.TRUE.equals(result.get("publicationChanged"))) {
        log.info("BuildIt Chart 최초 publication 생성: {}", result);
      } else {
        log.info("BuildIt Chart 최초 publication 건너뜀: {}", result.get("reason"));
      }
    } catch (Exception e) {
      // 부트스트랩 실패가 앱 기동을 막지 않도록 한다. 다음 재시작 또는 관리자 rebuild 로 복구 가능.
      log.warn("BuildIt Chart 최초 publication 부트스트랩 실패(무시하고 계속)", e);
    }
  }
}
