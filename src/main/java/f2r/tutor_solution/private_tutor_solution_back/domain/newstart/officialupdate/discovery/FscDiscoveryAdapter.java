package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 금융위원회 공식 사이트 discovery adapter
 * 실제 사이트: https://www.fsc.go.kr
 */
@Component
@Slf4j
public class FscDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  @Override
  public SourceType getSourceType() {
    return SourceType.FSC;
  }

  @Override
  public String getSourceName() {
    return "금융위원회";
  }

  @Override
  public List<DiscoveredCandidate> discover(LocalDateTime since) {
    log.info("금융위원회 discovery 실행 (since={})", since);
    // TODO: 실제 사이트 크롤링/파싱 로직 구현
    // https://www.fsc.go.kr/po040301 - 보도자료
    // https://www.fsc.go.kr/po040101 - 공지사항
    return Collections.emptyList();
  }

  @Override
  public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
    // TODO: 상세 페이지 조회 및 파싱 로직 구현
    return candidate;
  }
}