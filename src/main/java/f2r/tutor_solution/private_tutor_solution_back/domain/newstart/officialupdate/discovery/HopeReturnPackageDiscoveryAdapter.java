package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 희망리턴패키지 어댑터.
 * 2026-09-22 실사 결과 구 URL(sbiz.or.kr/nhrp/notice/list.do) 은 404 이고,
 * 공지 목록은 소상공인24(sbiz24.kr) SPA 의 세션토큰 발급 API(/api/cmmn/gnrl/Bbs/4/pst) 를 통해
 * 렌더링되어 단순 HTML 파싱이 불가능하다.
 * 빈 리스트 반환으로 SUCCESS 를 위장하지 않도록 명시적 NOT_IMPLEMENTED failure 를 기록한다.
 * (Phase 2.3 에서 auth 토큰 플로우 구현 예정 - HANDOFF 참조)
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
  protected List<DiscoveredCandidate> doDiscover(LocalDateTime since) {
    throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.NOT_IMPLEMENTED,
        "공지 출처가 sbiz24.kr SPA(세션토큰 API)로 이전됨 - HTML 파싱 어댑터 미작동, Phase 2.3 API 연 필요");
  }

  @Override
  public RelevancePriority evaluateRelevance(DiscoveredCandidate candidate) {
    RelevancePriority base = super.evaluateRelevance(candidate);
    if (base == RelevancePriority.NORMAL) {
      return RelevancePriority.HIGH;
    }
    return base;
  }
}
