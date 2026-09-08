package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermLiveVideoLinkRepository
    extends JpaRepository<MidtermLiveVideoLink, Long> {

  /** 운영자 목록: 이 캠페인의 모든 link, 결정적 순서. */
  List<MidtermLiveVideoLink> findByCampaignIdOrderBySortOrderAscLinkIdAsc(Long campaignId);

  /**
   * 공개 resolver: visible link 만, 결정적 순서(sort_order → link_id tie-breaker).
   * DB 반환 순서에 대표 영상 선택이 흔들리지 않도록 명시 정렬한다.
   */
  List<MidtermLiveVideoLink>
      findByCampaignIdAndVisibleTrueOrderBySortOrderAscLinkIdAsc(Long campaignId);

  /** 운영자 수정/삭제: link 를 캠페인 스코프로 잠근다. */
  Optional<MidtermLiveVideoLink> findByLinkIdAndCampaignId(Long linkId, Long campaignId);

  boolean existsByCampaignIdAndSchoolNameAndGradeAndRoleAndAcademyVideoId(
      Long campaignId, String schoolName, int grade,
      f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoRole role,
      Long academyVideoId);
}
