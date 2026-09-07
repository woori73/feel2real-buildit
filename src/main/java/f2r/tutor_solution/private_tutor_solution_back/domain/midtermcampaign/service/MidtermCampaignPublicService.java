package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.Briefing;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.CampaignView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.Hero;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.LiveChip;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.MaterialCard;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.PopupView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.PrepStep;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.NextExamView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.SchoolCollectionView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.UpdateLogView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermNextExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermPrepStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermSchoolCollection;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermUpdateLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermNextExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermPrepStatusRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermSchoolCollectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermUpdateLogRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 페이지·팝업용 캠페인 aggregate 조립기(작업지시서 §5~§13·§22·§31).
 *
 * <p>미발행 캠페인/부재 → {@link Optional#empty()} → 컨트롤러가 404. 하위 행은 {@code published=true}
 * 인 것만 포함하고 PRIVATE 자료는 목록에서도 제외한다({@link MidtermMaterialAccessService}).
 */
@Service
@RequiredArgsConstructor
public class MidtermCampaignPublicService {

  private final AcademyRepository academyRepository;
  private final AcademyVideoRepository academyVideoRepository;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermSchoolCollectionRepository schoolCollectionRepository;
  private final MidtermPrepStatusRepository prepStatusRepository;
  private final MidtermMaterialRepository materialRepository;
  private final MidtermUpdateLogRepository updateLogRepository;
  private final MidtermNextExamRepository nextExamRepository;
  private final MidtermMaterialAccessService materialAccessService;
  private final MidtermImageService midtermImageService;
  private final AuthenticatedUserResolver authResolver;

  @Transactional(readOnly = true)
  public Optional<CampaignView> findPublishedByHandle(String handle) {
    Academy academy = academyRepository.findByHandle(handle).orElse(null);
    if (academy == null) {
      return Optional.empty();
    }
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElse(null);
    if (campaign == null) {
      return Optional.empty();
    }
    return Optional.of(assemble(academy, campaign));
  }

  /** 팝업 전용 축약 payload. popup_enabled && published 아니면 empty. */
  @Transactional(readOnly = true)
  public Optional<PopupView> findPopupByHandle(String handle) {
    return findPublishedByHandle(handle)
        .map(CampaignView::popup)
        .filter(p -> p != null);
  }

  public MidtermMaterial requirePublishedMaterial(String handle, Long materialId) {
    Academy academy = academyRepository.findByHandle(handle)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캠페인을 찾을 수 없습니다."));
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캠페인을 찾을 수 없습니다."));
    return materialRepository.findByMidtermMaterialIdAndCampaignId(materialId, campaign.getCampaignId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
  }

  public MaterialCard toCard(MidtermMaterial material, String handle) {
    Optional<JwtTokenProvider.AuthenticatedPrincipal> principal = authResolver.currentPrincipal();
    return card(material, handle, principal);
  }

  /** 목록 노출 가능한 자료만 카드로 돌려준다. 아니면 404. */
  @Transactional(readOnly = true)
  public MaterialCard requireListableCard(String handle, Long materialId) {
    MidtermMaterial material = requirePublishedMaterial(handle, materialId);
    if (!materialAccessService.isListable(material)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다.");
    }
    return toCard(material, handle);
  }

  @Transactional(readOnly = true)
  public MidtermImageService.ServedImage readPopupVisual(String handle) {
    MidtermCampaign campaign = requirePublishedCampaign(handle);
    if (!campaign.isPopupEnabled() || campaign.getPopupVisualKey() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팝업 이미지가 없습니다.");
    }
    return midtermImageService.read(campaign.getPopupVisualKey());
  }

  @Transactional(readOnly = true)
  public MidtermImageService.ServedImage readMaterialThumbnail(String handle, Long materialId) {
    MidtermMaterial material = requirePublishedMaterial(handle, materialId);
    if (!materialAccessService.isListable(material) || material.getThumbnailKey() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "썸네일이 없습니다.");
    }
    return midtermImageService.read(material.getThumbnailKey());
  }

  private MidtermCampaign requirePublishedCampaign(String handle) {
    Academy academy = academyRepository.findByHandle(handle)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캠페인을 찾을 수 없습니다."));
    return campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캠페인을 찾을 수 없습니다."));
  }

  // ── 내부 조립 ───────────────────────────────────────────────────────────
  private CampaignView assemble(Academy academy, MidtermCampaign campaign) {
    Optional<JwtTokenProvider.AuthenticatedPrincipal> principal = authResolver.currentPrincipal();
    Long campaignId = campaign.getCampaignId();
    String handle = academy.getHandle();
    String slug = academy.getSlug();

    List<MidtermSchoolCollection> collections = schoolCollectionRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscSchoolCollectionIdAsc(campaignId);
    List<Long> collectionIds = collections.stream()
        .map(MidtermSchoolCollection::getSchoolCollectionId).toList();

    Map<Long, List<MidtermPrepStatus>> prepByCollection = collectionIds.isEmpty()
        ? Map.of()
        : prepStatusRepository.findBySchoolCollectionIdInOrderBySortOrderAscPrepStatusIdAsc(collectionIds)
            .stream().collect(Collectors.groupingBy(MidtermPrepStatus::getSchoolCollectionId));

    List<MidtermMaterial> allMaterials = materialRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscMidtermMaterialIdAsc(campaignId)
        .stream().filter(materialAccessService::isListable).toList();
    Map<Long, List<MidtermMaterial>> materialsByCollection = allMaterials.stream()
        .filter(m -> m.getSchoolCollectionId() != null)
        .collect(Collectors.groupingBy(MidtermMaterial::getSchoolCollectionId));
    List<MaterialCard> campaignMaterials = allMaterials.stream()
        .filter(m -> m.getSchoolCollectionId() == null)
        .map(m -> card(m, handle, principal)).toList();

    List<MidtermUpdateLog> allLogs = updateLogRepository
        .findByCampaignIdAndPublishedTrueOrderByLogDateDescSortOrderAscUpdateLogIdDesc(campaignId);
    Map<Long, List<MidtermUpdateLog>> logsByCollection = allLogs.stream()
        .filter(l -> l.getSchoolCollectionId() != null)
        .collect(Collectors.groupingBy(MidtermUpdateLog::getSchoolCollectionId));
    List<UpdateLogView> campaignLogs = allLogs.stream()
        .map(l -> new UpdateLogView(l.getLogDate(), l.getHeadline(), l.getBody(),
            l.getSchoolCollectionId() == null ? "CAMPAIGN" : "SCHOOL"))
        .toList();

    List<SchoolCollectionView> collectionViews = new ArrayList<>();
    List<LiveChip> liveChips = new ArrayList<>();
    for (MidtermSchoolCollection c : collections) {
      Long cid = c.getSchoolCollectionId();
      List<MaterialCard> mats = materialsByCollection.getOrDefault(cid, List.of()).stream()
          .map(m -> card(m, handle, principal)).toList();
      List<UpdateLogView> logs = logsByCollection.getOrDefault(cid, List.of()).stream()
          .map(l -> new UpdateLogView(l.getLogDate(), l.getHeadline(), l.getBody(), "SCHOOL"))
          .toList();
      LocalDate lastUpdate = logs.stream().map(UpdateLogView::date)
          .max(Comparator.naturalOrder())
          .orElse(c.getUpdatedAt() == null ? null : c.getUpdatedAt().toLocalDate());
      List<PrepStep> prep = prepByCollection.getOrDefault(cid, List.of()).stream()
          .map(p -> new PrepStep(p.getStepKey(), p.getLabel(), p.getState().name())).toList();

      collectionViews.add(new SchoolCollectionView(
          cid, c.getSchoolName(), c.getGrade(), c.getExamTitle(), c.getStatusLabel(),
          c.getPrepHeadline(), c.getExamFeatureNote(), mats.size(), lastUpdate,
          resolveBriefing(c, academy.getAcademyId(), slug), prep, mats, logs));

      if (lastUpdate != null) {
        liveChips.add(new LiveChip(c.getSchoolName() + " " + c.getGrade(), lastUpdate));
      }
    }

    NextExamView nextExam = nextExamRepository.findByCampaignId(campaignId)
        .filter(MidtermNextExam::isEnabled)
        .map(n -> new NextExamView(n.getNextExamName(), n.getHeadline(), n.getDescription(),
            n.getCtaLabel(), n.getCtaDestination()))
        .orElse(null);

    PopupView popup = campaign.isPopupEnabled()
        ? new PopupView(campaign.getPopupHeadline(), campaign.getPopupBody(),
            campaign.getPopupVisualKey() == null ? null
                : "/api/academies/" + handle + "/midterm-campaign/popup-visual",
            collections.stream().map(c -> c.getSchoolName() + " " + c.getGrade()).toList())
        : null;

    return new CampaignView(
        campaign.getExamName(), campaign.getExamYear(), campaign.getSemester(), campaign.getExamType(),
        campaign.getState().name(), campaign.getPeriodStart(), campaign.getPeriodEnd(),
        new Hero(campaign.getHeroEyebrow(), campaign.getHeroTitle(), campaign.getHeroBody(),
            campaign.getHeroCtaLabel()),
        liveChips, collectionViews, campaignMaterials, campaignLogs, nextExam, popup);
  }

  private Briefing resolveBriefing(MidtermSchoolCollection c, Long academyId, String slug) {
    if (!c.isBriefingPublished() || c.getBriefingVideoId() == null) {
      // 영상이 없어도 브리핑 텍스트는 노출한다(제목/설명이 있으면).
      if (c.getBriefingTitle() == null && c.getBriefingDescription() == null) {
        return null;
      }
      return new Briefing(null, null, null, c.getBriefingTitle(), c.getBriefingDescription(),
          c.getBriefingShootDate());
    }
    AcademyVideo video = academyVideoRepository
        .findByVideoIdAndAcademy_AcademyId(c.getBriefingVideoId(), academyId)
        .filter(v -> v.getLifecycleStatus() == VideoLifecycleStatus.READY)
        .orElse(null);
    if (video == null) {
      return new Briefing(null, null, null, c.getBriefingTitle(), c.getBriefingDescription(),
          c.getBriefingShootDate());
    }
    String base = "/api/academies/" + urlEncode(slug) + "/videos/" + video.getVideoId();
    return new Briefing(video.getVideoId(), base + "/stream",
        video.getThumbnailKey() == null ? null : base + "/thumbnail",
        c.getBriefingTitle(), c.getBriefingDescription(), c.getBriefingShootDate());
  }

  private MaterialCard card(MidtermMaterial m, String handle,
      Optional<JwtTokenProvider.AuthenticatedPrincipal> principal) {
    MidtermMaterialAccessService.Access access = materialAccessService.resolveAccess(m, principal);
    String base = "/api/academies/" + handle + "/midterm-campaign/materials/" + m.getMidtermMaterialId();
    String fileUrl = materialAccessService.canExposeFileUrl(access) ? base + "/file" : null;
    String thumbnailUrl = m.getThumbnailKey() == null ? null : base + "/thumbnail";
    return new MaterialCard(
        m.getMidtermMaterialId(), m.getCategory(), m.getDisplayTitle(), m.getBlurb(),
        m.getPrincipalComment(), splitChecklist(m.getChecklist()), m.getVisibility().name(),
        access.name(), thumbnailUrl, fileUrl, materialAccessService.sourceLabel(m));
  }

  static List<String> splitChecklist(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
        .replace("+", "%20");
  }
}
