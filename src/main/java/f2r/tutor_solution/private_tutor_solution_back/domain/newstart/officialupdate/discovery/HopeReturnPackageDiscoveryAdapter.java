package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 희망리턴패키지/소상공인시장진흥공단 공식 사이트 discovery adapter
 * 실제 사이트: https://www.sbiz.or.kr/nhrp/main.do
 */
@Component
@Slf4j
public class HopeReturnPackageDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  @Override
  public SourceType getSourceType() {
    return SourceType.HOPE_RETURN_PACKAGE;
  }

  @Override
  public String getSourceName() {
    return "희망리턴패키지";
  }

  @Override
  public List<DiscoveredCandidate> discover(LocalDateTime since) {
    log.info("희망리턴패키지 discovery 실행 (since={})", since);
    // TODO: 실제 사이트 크롤링/파싱 로직 구현
    // https://www.sbiz.or.kr/nhrp/main.do - 공지사항, 사업공고
    // 주요 키워드: 원스톱폐업지원, 재취업지원, 재창업지원, 재기사업화, 경영개선, 채무조정, 법률자문
    return Collections.emptyList();
  }

  @Override
  public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
    // TODO: 상세 페이지 조회 및 파싱 로직 구현
    return candidate;
  }

  @Override
  public RelevancePriority evaluateRelevance(DiscoveredCandidate candidate) {
    // 희망리턴패키지는 기본적으로 HIGH 우선순위 (재취업/재창업/폐업지원 중심)
    RelevancePriority base = super.evaluateRelevance(candidate);
    if (base == RelevancePriority.NORMAL) {
      return RelevancePriority.HIGH;
    }
    return base;
  }
}