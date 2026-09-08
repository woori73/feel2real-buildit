package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LinkableVideoView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoAdminPayload;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkAdminView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkCreate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermLiveVideoDtos.LiveVideoLinkUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoLink;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermLiveVideoLinkRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자({@code /lms/{handle}/midterm-live} 의 "LIVE 수업 영상" 섹션)가 기존 AcademyVideo 를
 * 학교/학년/역할 scene 에 연결한다(Phase 4c).
 *
 * <p><b>불변식:</b> 모든 public 메서드가 {@link AcademyService#requireOwnedAcademyByHandle} 로
 * 시작하고(타 학원 TUTOR·ADMIN 차단), 연결하려는 영상이 그 캠페인 소유 학원의 것인지 쿼리
 * 단계에서 재확인한다(다른 학원 videoId IDOR 차단). 새 영상 업로드/트랜스코딩/썸네일 파이프라인을
 * 만들지 않는다 — 연결(association)만 한다.
 */
@Service
@RequiredArgsConstructor
public class MidtermLiveVideoAdminService {

  private final AcademyService academyService;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermLiveVideoLinkRepository linkRepository;
  private final AcademyVideoRepository academyVideoRepository;

  @Transactional(readOnly = true)
  public LiveVideoAdminPayload list(String handle) {
    return payload(requireOwnedCampaign(handle));
  }

  @Transactional
  public LiveVideoAdminPayload create(String handle, LiveVideoLinkCreate req) {
    OwnedContext ctx = requireOwnedCampaign(handle);
    if (req == null || req.academyVideoId() == null) {
      throw badRequest("연결할 영상을 선택해 주세요.");
    }
    requireOwnedVideo(ctx, req.academyVideoId());
    String school = requireSchool(req.school());
    int grade = requireGrade(req.grade());
    MidtermLiveVideoRole role = parseRole(req.role());

    if (linkRepository.existsByCampaignIdAndSchoolNameAndGradeAndRoleAndAcademyVideoId(
        ctx.campaign().getCampaignId(), school, grade, role, req.academyVideoId())) {
      throw badRequest("이미 같은 학교·학년·역할에 연결된 영상입니다.");
    }

    linkRepository.save(MidtermLiveVideoLink.builder()
        .campaignId(ctx.campaign().getCampaignId())
        .academyVideoId(req.academyVideoId())
        .schoolName(school)
        .grade(grade)
        .role(role)
        .visible(Boolean.TRUE.equals(req.visible()))
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .build());
    return payload(ctx);
  }

  @Transactional
  public LiveVideoAdminPayload update(String handle, Long linkId, LiveVideoLinkUpdate req) {
    OwnedContext ctx = requireOwnedCampaign(handle);
    MidtermLiveVideoLink link = requireLink(ctx, linkId);
    boolean visible = req != null && req.visible() != null ? req.visible() : link.isVisible();
    int sortOrder = req != null && req.sortOrder() != null ? req.sortOrder() : link.getSortOrder();

    boolean retarget = req != null
        && (req.school() != null || req.grade() != null || req.role() != null);
    if (retarget) {
      link.update(
          req.school() != null ? requireSchool(req.school()) : link.getSchoolName(),
          req.grade() != null ? requireGrade(req.grade()) : link.getGrade(),
          req.role() != null ? parseRole(req.role()) : link.getRole(),
          visible, sortOrder);
    } else {
      link.updateExposure(visible, sortOrder);
    }
    return payload(ctx);
  }

  @Transactional
  public LiveVideoAdminPayload delete(String handle, Long linkId) {
    OwnedContext ctx = requireOwnedCampaign(handle);
    linkRepository.delete(requireLink(ctx, linkId));
    return payload(ctx);
  }

  // ── 조립 ────────────────────────────────────────────────────────────────
  private LiveVideoAdminPayload payload(OwnedContext ctx) {
    List<AcademyVideo> readyVideos = academyVideoRepository
        .findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
            ctx.academy().getAcademyId(), VideoLifecycleStatus.READY);
    Map<Long, AcademyVideo> byId = readyVideos.stream()
        .collect(Collectors.toMap(AcademyVideo::getVideoId, Function.identity(), (a, b) -> a));
    String slug = ctx.academy().getSlug();

    List<LiveVideoLinkAdminView> links = linkRepository
        .findByCampaignIdOrderBySortOrderAscLinkIdAsc(ctx.campaign().getCampaignId())
        .stream()
        .map(link -> {
          AcademyVideo v = byId.get(link.getAcademyVideoId());
          return new LiveVideoLinkAdminView(
              link.getLinkId(), link.getAcademyVideoId(),
              link.getSchoolName(), link.getGrade(), link.getRole().name(),
              link.isVisible(), link.getSortOrder(),
              thumbnailUrl(slug, link.getAcademyVideoId(), v),
              v == null ? null : v.getOriginalFilename(),
              v == null ? null : v.getSourceKind().name(),
              v == null ? null : v.getExternalUrl());
        })
        .toList();

    List<LinkableVideoView> linkable = readyVideos.stream()
        .map(v -> new LinkableVideoView(
            v.getVideoId(), v.getOriginalFilename(),
            thumbnailUrl(slug, v.getVideoId(), v),
            v.getDurationMs(),
            v.getPurpose() == null ? null : v.getPurpose().name(),
            v.getSourceKind().name(),
            v.getExternalUrl()))
        .toList();
    return new LiveVideoAdminPayload(links, linkable);
  }

  private static String thumbnailUrl(String slug, Long videoId, AcademyVideo video) {
    if (video == null || video.getThumbnailKey() == null) {
      return null;
    }
    return "/api/academies/" + urlEncode(slug) + "/videos/" + videoId + "/thumbnail";
  }

  // ── 가드 ────────────────────────────────────────────────────────────────
  private OwnedContext requireOwnedCampaign(String handle) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캠페인을 찾을 수 없습니다."));
    return new OwnedContext(academy, campaign);
  }

  private void requireOwnedVideo(OwnedContext ctx, Long videoId) {
    academyVideoRepository.findByVideoIdAndAcademy_AcademyId(videoId, ctx.academy().getAcademyId())
        .orElseThrow(() -> badRequest("이 학원의 영상이 아닙니다: videoId=" + videoId));
  }

  private MidtermLiveVideoLink requireLink(OwnedContext ctx, Long linkId) {
    return linkRepository.findByLinkIdAndCampaignId(linkId, ctx.campaign().getCampaignId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "연결을 찾을 수 없습니다."));
  }

  private static String requireSchool(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      throw badRequest("학교명을 입력해 주세요.");
    }
    return value;
  }

  private static int requireGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 9) {
      throw badRequest("학년은 1~9 사이여야 합니다.");
    }
    return grade;
  }

  private static MidtermLiveVideoRole parseRole(String raw) {
    try {
      return MidtermLiveVideoRole.from(raw);
    } catch (IllegalArgumentException e) {
      throw badRequest(e.getMessage());
    }
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private record OwnedContext(Academy academy, MidtermCampaign campaign) {}
}
