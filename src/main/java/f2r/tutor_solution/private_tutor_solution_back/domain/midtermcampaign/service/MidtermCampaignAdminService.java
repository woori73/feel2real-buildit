package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자({@code /lms/{handle}/midterm-live})의 캠페인 관리 전체.
 *
 * <p><b>클래스 불변식:</b> 모든 public 메서드는 {@link AcademyService#requireOwnedAcademyByHandle}
 * 로 시작한다({@code AcademyLmsManagePageController} 와 같은 규칙 — owner 표면이 한 파일에 모여
 * 리뷰어가 가드 누락을 눈으로 잡을 수 있어야 한다). 타 학원 TUTOR·ADMIN 모두 여기서 차단된다.
 */
@Service
@RequiredArgsConstructor
public class MidtermCampaignAdminService {

  private final AcademyService academyService;
  private final AcademyVideoRepository academyVideoRepository;
  private final MidtermImageService midtermImageService;
  private final MidtermMaterialAccessService materialAccessService;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermSchoolCollectionRepository schoolCollectionRepository;
  private final MidtermPrepStatusRepository prepStatusRepository;
  private final MidtermMaterialRepository materialRepository;
  private final MidtermUpdateLogRepository updateLogRepository;
  private final MidtermMakingOfRepository makingOfRepository;
  private final MidtermAfterExamRepository afterExamRepository;
  private final MidtermNextExamRepository nextExamRepository;

  // ── 조회 ────────────────────────────────────────────────────────────────
  @Transactional(readOnly = true)
  public AdminView getView(String handle) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .orElse(null);
    if (campaign == null) {
      return new AdminView(false, null, null, null, null, null, null, null, null,
          null, null, null, null, false, false, null, null, null, null,
          List.of(), List.of(), List.of(), List.of());
    }
    Long cid = campaign.getCampaignId();

    List<MidtermSchoolCollection> collections = schoolCollectionRepository
        .findByCampaignIdOrderBySortOrderAscSchoolCollectionIdAsc(cid);
    List<Long> collectionIds = collections.stream()
        .map(MidtermSchoolCollection::getSchoolCollectionId).toList();
    Map<Long, List<MidtermPrepStatus>> prepByCollection = collectionIds.isEmpty() ? Map.of()
        : prepStatusRepository.findBySchoolCollectionIdInOrderBySortOrderAscPrepStatusIdAsc(collectionIds)
            .stream().collect(Collectors.groupingBy(MidtermPrepStatus::getSchoolCollectionId));
    Map<Long, MidtermAfterExam> afterByCollection = collectionIds.isEmpty() ? Map.of()
        : afterExamRepository.findBySchoolCollectionIdIn(collectionIds).stream()
            .collect(Collectors.toMap(MidtermAfterExam::getSchoolCollectionId, a -> a));
    Map<Long, String> videoTitles = collections.stream()
        .map(MidtermSchoolCollection::getBriefingVideoId).filter(java.util.Objects::nonNull).distinct()
        .map(vid -> academyVideoRepository.findByVideoIdAndAcademy_AcademyId(vid, academy.getAcademyId()).orElse(null))
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toMap(AcademyVideo::getVideoId, AcademyVideo::getOriginalFilename, (a, b) -> a));

    List<SchoolCollectionAdminView> collectionViews = collections.stream().map(c -> {
      List<PrepStepView> prep = prepByCollection.getOrDefault(c.getSchoolCollectionId(), List.of())
          .stream().map(p -> new PrepStepView(p.getStepKey(), p.getLabel(), p.getState().name(),
              p.getSortOrder())).toList();
      MidtermAfterExam ae = afterByCollection.get(c.getSchoolCollectionId());
      AfterExamView aev = ae == null ? null : new AfterExamView(ae.getExamFeature(),
          ae.getPreparedNote(), ae.getConnectionNote(), ae.getNextImprovement(), ae.isPublished());
      return new SchoolCollectionAdminView(
          c.getSchoolCollectionId(), c.getSchoolName(), c.getGrade(), c.getExamTitle(),
          c.getStatusLabel(), c.getPrepHeadline(), c.getExamFeatureNote(), c.getSortOrder(),
          c.isPublished(), c.getBriefingVideoId(),
          c.getBriefingVideoId() == null ? null : videoTitles.get(c.getBriefingVideoId()),
          c.getBriefingTitle(), c.getBriefingDescription(), c.getBriefingShootDate(),
          c.getBriefingPublishDate(), c.isBriefingPublished(), prep, aev);
    }).toList();

    List<MaterialAdminView> materials = materialRepository
        .findByCampaignIdOrderBySortOrderAscMidtermMaterialIdAsc(cid).stream()
        .map(m -> new MaterialAdminView(m.getMidtermMaterialId(), m.getSchoolCollectionId(),
            m.getCategory(), m.getDisplayTitle(), m.getBlurb(), m.getPrincipalComment(),
            m.getChecklist(), m.getSourceType().name(), m.getSourceMaterialId(),
            materialAccessService.sourceLabel(m), m.getVisibility().name(), m.getPreviewPageCap(),
            m.getSortOrder(), m.isPublished()))
        .toList();

    List<UpdateLogAdminView> logs = updateLogRepository
        .findByCampaignIdOrderByLogDateDescSortOrderAscUpdateLogIdDesc(cid).stream()
        .map(l -> new UpdateLogAdminView(l.getUpdateLogId(), l.getSchoolCollectionId(), l.getLogDate(),
            l.getHeadline(), l.getBody(), l.isPublished(), l.getSortOrder()))
        .toList();

    List<MakingOfAdminView> making = makingOfRepository
        .findByCampaignIdOrderBySortOrderAscMakingOfIdAsc(cid).stream()
        .map(mo -> new MakingOfAdminView(mo.getMakingOfId(), mo.getSchoolCollectionId(), mo.getStage(),
            mo.getCaption(), mo.getVideoId(), mo.isPublished(), mo.getSortOrder()))
        .toList();

    MidtermNextExam ne = nextExamRepository.findByCampaignId(cid).orElse(null);
    NextExamView nev = ne == null ? null : new NextExamView(ne.isEnabled(), ne.getNextExamName(),
        ne.getHeadline(), ne.getDescription(), ne.getCtaLabel(), ne.getCtaDestination());

    return new AdminView(true, cid, campaign.getExamName(), campaign.getExamYear(),
        campaign.getSemester(), campaign.getExamType(), campaign.getPeriodStart(),
        campaign.getPeriodEnd(), campaign.getState().name(), campaign.getHeroEyebrow(),
        campaign.getHeroTitle(), campaign.getHeroBody(), campaign.getHeroCtaLabel(),
        campaign.isPublished(), campaign.isPopupEnabled(), campaign.getPopupHeadline(),
        campaign.getPopupBody(),
        campaign.getPopupVisualKey() == null ? null
            : "/api/academies/" + handle + "/midterm-campaign/popup-visual",
        nev, collectionViews, materials, logs, making);
  }

  // ── 캠페인 ──────────────────────────────────────────────────────────────
  @Transactional
  public AdminView upsertCampaign(String handle, CampaignUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .orElse(null);
    MidtermCampaignState state = parseEnum(MidtermCampaignState.class, req.state(),
        MidtermCampaignState.PREPARING);
    if (campaign == null) {
      campaign = campaignRepository.save(MidtermCampaign.builder()
          .academy(academy)
          .examName(required(req.examName(), "시험명"))
          .examYear(req.examYear() != null ? req.examYear() : java.time.Year.now().getValue())
          .semester(required(req.semester(), "학기"))
          .examType(required(req.examType(), "시험 종류"))
          .periodStart(req.periodStart())
          .periodEnd(req.periodEnd())
          .state(state)
          .heroEyebrow(req.heroEyebrow())
          .heroTitle(req.heroTitle())
          .heroBody(req.heroBody())
          .heroCtaLabel(req.heroCtaLabel())
          .popupEnabled(Boolean.TRUE.equals(req.popupEnabled()))
          .popupHeadline(req.popupHeadline())
          .popupBody(req.popupBody())
          .popupVisualKey(req.popupVisualKey())
          .build());
    } else {
      campaign.updateBasics(required(req.examName(), "시험명"),
          req.examYear() != null ? req.examYear() : campaign.getExamYear(),
          required(req.semester(), "학기"), required(req.examType(), "시험 종류"),
          req.periodStart(), req.periodEnd(), state);
      campaign.updateHero(req.heroEyebrow(), req.heroTitle(), req.heroBody(), req.heroCtaLabel());
      campaign.updatePopup(
          req.popupEnabled() != null ? req.popupEnabled() : campaign.isPopupEnabled(),
          req.popupHeadline(), req.popupBody(),
          req.popupVisualKey() != null ? req.popupVisualKey() : campaign.getPopupVisualKey());
    }
    return getView(handle);
  }

  @Transactional
  public AdminView setPublished(String handle, boolean published) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    requireCampaign(academy).updatePublished(published);
    return getView(handle);
  }

  @Transactional
  public String uploadImage(String handle, String kind, MultipartFile image) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    String hint = "midterm-" + (kind == null ? "image" : kind.replaceAll("[^a-z0-9-]", ""));
    return midtermImageService.save(academy.getTutor().getUserId(), hint, image);
  }

  // ── 학교 컬렉션 ─────────────────────────────────────────────────────────
  @Transactional
  public AdminView createSchoolCollection(String handle, SchoolCollectionUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = requireCampaign(academy);
    schoolCollectionRepository.save(MidtermSchoolCollection.builder()
        .campaignId(campaign.getCampaignId())
        .schoolName(required(req.schoolName(), "학교명"))
        .grade(validGrade(req.grade()))
        .examTitle(req.examTitle())
        .statusLabel(req.statusLabel())
        .prepHeadline(req.prepHeadline())
        .examFeatureNote(req.examFeatureNote())
        .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
        .published(Boolean.TRUE.equals(req.published()))
        .build());
    return getView(handle);
  }

  @Transactional
  public AdminView updateSchoolCollection(String handle, Long id, SchoolCollectionUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermSchoolCollection c = requireCollection(academy, id);
    c.updateBasics(required(req.schoolName(), "학교명"), validGrade(req.grade()), req.examTitle(),
        req.statusLabel(), req.prepHeadline(), req.examFeatureNote(),
        req.sortOrder() != null ? req.sortOrder() : c.getSortOrder(),
        req.published() != null ? req.published() : c.isPublished());
    return getView(handle);
  }

  @Transactional
  public AdminView deleteSchoolCollection(String handle, Long id) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    schoolCollectionRepository.delete(requireCollection(academy, id));
    return getView(handle);
  }

  @Transactional
  public AdminView updateBriefing(String handle, Long id, BriefingUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermSchoolCollection c = requireCollection(academy, id);
    Long videoId = req.briefingVideoId();
    if (videoId != null) {
      academyVideoRepository.findByVideoIdAndAcademy_AcademyId(videoId, academy.getAcademyId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "이 학원의 영상이 아닙니다: videoId=" + req.briefingVideoId()));
    }
    c.updateBriefing(videoId, req.briefingTitle(), req.briefingDescription(),
        req.briefingShootDate(), req.briefingPublishDate(),
        req.briefingPublished() != null ? req.briefingPublished() : c.isBriefingPublished());
    return getView(handle);
  }

  @Transactional
  public AdminView replacePrepStatuses(String handle, Long collectionId, PrepStatusReplace req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermSchoolCollection c = requireCollection(academy, collectionId);
    prepStatusRepository.deleteBySchoolCollectionId(c.getSchoolCollectionId());
    List<PrepStepView> steps = req.steps() == null ? List.of() : req.steps();
    int order = 0;
    List<MidtermPrepStatus> next = new ArrayList<>();
    for (PrepStepView s : steps) {
      next.add(MidtermPrepStatus.builder()
          .schoolCollectionId(c.getSchoolCollectionId())
          .stepKey(required(s.stepKey(), "단계 키"))
          .label(required(s.label(), "단계 이름"))
          .state(parseEnum(MidtermPrepState.class, s.state(), MidtermPrepState.NOT_STARTED))
          .sortOrder(s.sortOrder() != 0 ? s.sortOrder() : order)
          .build());
      order++;
    }
    prepStatusRepository.saveAll(next);
    return getView(handle);
  }

  @Transactional
  public AdminView upsertAfterExam(String handle, Long collectionId, AfterExamUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermSchoolCollection c = requireCollection(academy, collectionId);
    MidtermAfterExam ae = afterExamRepository.findBySchoolCollectionId(c.getSchoolCollectionId())
        .orElse(null);
    boolean published = Boolean.TRUE.equals(req.published());
    if (ae == null) {
      afterExamRepository.save(MidtermAfterExam.builder()
          .schoolCollectionId(c.getSchoolCollectionId())
          .examFeature(req.examFeature())
          .preparedNote(req.preparedNote())
          .connectionNote(req.connectionNote())
          .nextImprovement(req.nextImprovement())
          .published(published)
          .build());
    } else {
      ae.update(req.examFeature(), req.preparedNote(), req.connectionNote(), req.nextImprovement(),
          published);
    }
    return getView(handle);
  }

  // ── 자료 Showcase ───────────────────────────────────────────────────────
  @Transactional
  public AdminView createMaterial(String handle, MaterialUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = requireCampaign(academy);
    Long collectionId = validateOptionalCollection(academy, req.schoolCollectionId());
    materialRepository.save(MidtermMaterial.builder()
        .campaignId(campaign.getCampaignId())
        .schoolCollectionId(collectionId)
        .category(req.category())
        .displayTitle(required(req.displayTitle(), "자료 제목"))
        .blurb(req.blurb())
        .principalComment(req.principalComment())
        .checklist(req.checklist())
        .sourceType(parseEnum(MidtermMaterialSourceType.class, req.sourceType(),
            MidtermMaterialSourceType.NONE))
        .sourceMaterialId(req.sourceMaterialId())
        .visibility(parseEnum(MidtermMaterialVisibility.class, req.visibility(),
            MidtermMaterialVisibility.PRIVATE))
        .previewPageCap(req.previewPageCap())
        .thumbnailKey(req.thumbnailKey())
        .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
        .published(Boolean.TRUE.equals(req.published()))
        .build());
    return getView(handle);
  }

  @Transactional
  public AdminView updateMaterial(String handle, Long id, MaterialUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermMaterial m = requireMaterial(academy, id);
    validateOptionalCollection(academy, req.schoolCollectionId());
    // schoolCollectionId 는 항상 전달된다(null=캠페인 공용). 편집 화면이 현재 값을 되보낸다.
    m.setSchoolCollectionId(req.schoolCollectionId());
    m.update(req.category(), required(req.displayTitle(), "자료 제목"), req.blurb(),
        req.principalComment(), req.checklist(),
        parseEnum(MidtermMaterialSourceType.class, req.sourceType(), m.getSourceType()),
        req.sourceMaterialId(),
        parseEnum(MidtermMaterialVisibility.class, req.visibility(), m.getVisibility()),
        req.previewPageCap(),
        req.thumbnailKey() != null ? req.thumbnailKey() : m.getThumbnailKey(),
        req.sortOrder() != null ? req.sortOrder() : m.getSortOrder(),
        req.published() != null ? req.published() : m.isPublished());
    return getView(handle);
  }

  @Transactional
  public AdminView deleteMaterial(String handle, Long id) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    materialRepository.delete(requireMaterial(academy, id));
    return getView(handle);
  }

  // ── 업데이트 로그 ───────────────────────────────────────────────────────
  @Transactional
  public AdminView createUpdateLog(String handle, UpdateLogUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = requireCampaign(academy);
    Long collectionId = validateOptionalCollection(academy, req.schoolCollectionId());
    updateLogRepository.save(MidtermUpdateLog.builder()
        .campaignId(campaign.getCampaignId())
        .schoolCollectionId(collectionId)
        .logDate(req.logDate() != null ? req.logDate() : java.time.LocalDate.now())
        .headline(required(req.headline(), "업데이트 제목"))
        .body(req.body())
        .published(req.published() == null || req.published())
        .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
        .build());
    return getView(handle);
  }

  @Transactional
  public AdminView updateUpdateLog(String handle, Long id, UpdateLogUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermUpdateLog l = requireUpdateLog(academy, id);
    Long collectionId = validateOptionalCollection(academy, req.schoolCollectionId());
    l.update(collectionId, req.logDate() != null ? req.logDate() : l.getLogDate(),
        required(req.headline(), "업데이트 제목"), req.body(),
        req.published() != null ? req.published() : l.isPublished(),
        req.sortOrder() != null ? req.sortOrder() : l.getSortOrder());
    return getView(handle);
  }

  @Transactional
  public AdminView deleteUpdateLog(String handle, Long id) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    updateLogRepository.delete(requireUpdateLog(academy, id));
    return getView(handle);
  }

  // ── Making-of (스키마 + 입력만) ─────────────────────────────────────────
  @Transactional
  public AdminView createMakingOf(String handle, MakingOfUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = requireCampaign(academy);
    Long collectionId = validateOptionalCollection(academy, req.schoolCollectionId());
    Long videoId = validateOptionalVideo(academy, req.videoId());
    makingOfRepository.save(MidtermMakingOf.builder()
        .campaignId(campaign.getCampaignId())
        .schoolCollectionId(collectionId)
        .stage(required(req.stage(), "단계"))
        .caption(req.caption())
        .imageKey(req.imageKey())
        .videoId(videoId)
        .published(Boolean.TRUE.equals(req.published()))
        .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
        .build());
    return getView(handle);
  }

  @Transactional
  public AdminView updateMakingOf(String handle, Long id, MakingOfUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermMakingOf mo = requireMakingOf(academy, id);
    Long collectionId = validateOptionalCollection(academy, req.schoolCollectionId());
    Long videoId = validateOptionalVideo(academy, req.videoId());
    mo.update(collectionId, required(req.stage(), "단계"), req.caption(),
        req.imageKey() != null ? req.imageKey() : mo.getImageKey(), videoId,
        req.published() != null ? req.published() : mo.isPublished(),
        req.sortOrder() != null ? req.sortOrder() : mo.getSortOrder());
    return getView(handle);
  }

  @Transactional
  public AdminView deleteMakingOf(String handle, Long id) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    makingOfRepository.delete(requireMakingOf(academy, id));
    return getView(handle);
  }

  // ── Next Exam CTA ──────────────────────────────────────────────────────
  @Transactional
  public AdminView upsertNextExam(String handle, NextExamUpsert req) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = requireCampaign(academy);
    MidtermNextExam ne = nextExamRepository.findByCampaignId(campaign.getCampaignId()).orElse(null);
    boolean enabled = Boolean.TRUE.equals(req.enabled());
    if (ne == null) {
      nextExamRepository.save(MidtermNextExam.builder()
          .campaignId(campaign.getCampaignId())
          .enabled(enabled)
          .nextExamName(req.nextExamName())
          .headline(req.headline())
          .description(req.description())
          .ctaLabel(req.ctaLabel())
          .ctaDestination(req.ctaDestination())
          .build());
    } else {
      ne.update(enabled, req.nextExamName(), req.headline(), req.description(), req.ctaLabel(),
          req.ctaDestination());
    }
    return getView(handle);
  }

  // ── 헬퍼 ────────────────────────────────────────────────────────────────
  private MidtermCampaign requireCampaign(Academy academy) {
    return campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "먼저 캠페인을 만들어 주세요."));
  }

  private MidtermSchoolCollection requireCollection(Academy academy, Long id) {
    MidtermCampaign campaign = requireCampaign(academy);
    return schoolCollectionRepository.findBySchoolCollectionIdAndCampaignId(id, campaign.getCampaignId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학교 컬렉션을 찾을 수 없습니다."));
  }

  private MidtermMaterial requireMaterial(Academy academy, Long id) {
    MidtermCampaign campaign = requireCampaign(academy);
    return materialRepository.findByMidtermMaterialIdAndCampaignId(id, campaign.getCampaignId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
  }

  private MidtermUpdateLog requireUpdateLog(Academy academy, Long id) {
    MidtermCampaign campaign = requireCampaign(academy);
    return updateLogRepository.findByUpdateLogIdAndCampaignId(id, campaign.getCampaignId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업데이트 로그를 찾을 수 없습니다."));
  }

  private MidtermMakingOf requireMakingOf(Academy academy, Long id) {
    MidtermCampaign campaign = requireCampaign(academy);
    return makingOfRepository.findByMakingOfIdAndCampaignId(id, campaign.getCampaignId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Making-of 항목을 찾을 수 없습니다."));
  }

  private Long validateOptionalCollection(Academy academy, Long collectionId) {
    if (collectionId == null) {
      return null;
    }
    requireCollection(academy, collectionId);
    return collectionId;
  }

  private Long validateOptionalVideo(Academy academy, Long videoId) {
    if (videoId == null) {
      return null;
    }
    academyVideoRepository.findByVideoIdAndAcademy_AcademyId(videoId, academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "이 학원의 영상이 아닙니다: videoId=" + videoId));
    return videoId;
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않은 값입니다: " + raw);
    }
  }

  private static String required(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을(를) 입력해 주세요.");
    }
    return value.trim();
  }

  private static int validGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년은 1~3 사이로 입력해 주세요.");
    }
    return grade;
  }
}
