package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoLink;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermLiveVideoLinkRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 LIVE 페이지가 소비하는 학교/학년 영상 연결(Phase 4c).
 *
 * <p>노출 조건(모두 만족): 캠페인 published · link visible · 연결된 {@code academy_videos} 가
 * 캠페인 소유 학원의 것 · {@code lifecycleStatus == READY}. 하나라도 아니면 응답에서 제외한다
 * (깨진 stream URL 을 공개하지 않는다). storage key / 원본 파일명 / videoId 는 싣지 않고
 * 기존 serve endpoint URL 만 준다.
 */
@Service
@RequiredArgsConstructor
public class MidtermLiveVideoService {

  private final AcademyRepository academyRepository;
  private final MidtermCampaignRepository campaignRepository;
  private final AcademyVideoRepository academyVideoRepository;
  private final MidtermLiveVideoLinkRepository linkRepository;

  @Transactional(readOnly = true)
  public List<LiveVideoView> listPublic(String handle) {
    Academy academy = academyRepository.findByHandle(handle).orElse(null);
    if (academy == null) {
      return List.of();
    }
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElse(null);
    if (campaign == null) {
      return List.of();
    }

    Long academyId = academy.getAcademyId();
    String slug = academy.getSlug();
    List<LiveVideoView> out = new ArrayList<>();
    for (MidtermLiveVideoLink link : linkRepository
        .findByCampaignIdAndVisibleTrueOrderBySortOrderAscLinkIdAsc(campaign.getCampaignId())) {
      // 영상이 이 학원 소유인지 쿼리 단계에서 재확인 + READY 만(tombstone/UNAVAILABLE 제외).
      AcademyVideo video = academyVideoRepository
          .findByVideoIdAndAcademy_AcademyId(link.getAcademyVideoId(), academyId)
          .filter(v -> v.getLifecycleStatus() == VideoLifecycleStatus.READY)
          .orElse(null);
      if (video == null) {
        continue;
      }
      String base = "/api/academies/" + urlEncode(slug) + "/videos/" + video.getVideoId();
      out.add(new LiveVideoView(
          link.getSchoolName(),
          link.getGrade(),
          link.getRole().name(),
          base + "/stream",
          video.getThumbnailKey() == null ? null : base + "/thumbnail",
          link.getSortOrder()));
    }
    return out;
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
