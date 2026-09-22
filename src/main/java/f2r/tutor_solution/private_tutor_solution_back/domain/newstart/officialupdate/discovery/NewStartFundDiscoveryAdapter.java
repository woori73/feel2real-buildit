package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 새출발기금 공식 사이트 discovery adapter
 * 실제 사이트: https://www.kinfa.or.kr (또는 관련 공식 도메인)
 */
@Component
@Slf4j
public class NewStartFundDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  @Override
  public SourceType getSourceType() {
    return SourceType.NEWSTART_FUND;
  }

  @Override
  public String getSourceName() {
    return "새출발기금";
  }

  @Override
  public List<DiscoveredCandidate> discover(LocalDateTime since) {
    log.info("새출발기금 discovery 실행 (since={})", since);
    // TODO: 실제 사이트 크롤링/파싱 로직 구현
    // 현재는 빈 리스트 반환 (구현 대기)
    return Collections.emptyList();
  }

  @Override
  public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
    // TODO: 상세 페이지 조회 및 파싱 로직 구현
    return candidate;
  }
}