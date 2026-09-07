package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 운영자 화면({@code /lms/{handle}/midterm-live})이 소비/전송하는 payload. 공개 payload 와 달리
 * 미발행 행과 편집용 원본 필드를 모두 포함한다. 파일 storage key 는 여전히 싣지 않는다.
 */
public final class MidtermAdminDtos {

  private MidtermAdminDtos() {}

  // ── 조회 ────────────────────────────────────────────────────────────────
  public record AdminView(
      boolean exists,
      Long campaignId,
      String examName,
      Integer examYear,
      String semester,
      String examType,
      LocalDate periodStart,
      LocalDate periodEnd,
      String state,
      String heroEyebrow,
      String heroTitle,
      String heroBody,
      String heroCtaLabel,
      boolean published,
      boolean popupEnabled,
      String popupHeadline,
      String popupBody,
      String popupVisualUrl,
      NextExamView nextExam,
      List<SchoolCollectionAdminView> schoolCollections,
      List<MaterialAdminView> materials,
      List<UpdateLogAdminView> updateLogs,
      List<MakingOfAdminView> makingOf) {}

  public record SchoolCollectionAdminView(
      Long id,
      String schoolName,
      int grade,
      String examTitle,
      String statusLabel,
      String prepHeadline,
      String examFeatureNote,
      int sortOrder,
      boolean published,
      Long briefingVideoId,
      String briefingVideoTitle,
      String briefingTitle,
      String briefingDescription,
      LocalDate briefingShootDate,
      LocalDate briefingPublishDate,
      boolean briefingPublished,
      List<PrepStepView> prepStatuses,
      AfterExamView afterExam) {}

  public record PrepStepView(String stepKey, String label, String state, int sortOrder) {}

  public record MaterialAdminView(
      Long id,
      Long schoolCollectionId,
      String category,
      String displayTitle,
      String blurb,
      String principalComment,
      String checklist,
      String sourceType,
      Long sourceMaterialId,
      String sourceLabel,
      String visibility,
      Integer previewPageCap,
      int sortOrder,
      boolean published) {}

  public record UpdateLogAdminView(
      Long id,
      Long schoolCollectionId,
      LocalDate logDate,
      String headline,
      String body,
      boolean published,
      int sortOrder) {}

  public record MakingOfAdminView(
      Long id,
      Long schoolCollectionId,
      String stage,
      String caption,
      Long videoId,
      boolean published,
      int sortOrder) {}

  public record AfterExamView(
      String examFeature,
      String preparedNote,
      String connectionNote,
      String nextImprovement,
      boolean published) {}

  public record NextExamView(
      boolean enabled,
      String nextExamName,
      String headline,
      String description,
      String ctaLabel,
      String ctaDestination) {}

  // ── 전송(요청) ──────────────────────────────────────────────────────────
  public record CampaignUpsert(
      String examName,
      Integer examYear,
      String semester,
      String examType,
      LocalDate periodStart,
      LocalDate periodEnd,
      String state,
      String heroEyebrow,
      String heroTitle,
      String heroBody,
      String heroCtaLabel,
      Boolean popupEnabled,
      String popupHeadline,
      String popupBody,
      String popupVisualKey) {}

  public record PublishRequest(boolean published) {}

  public record SchoolCollectionUpsert(
      String schoolName,
      Integer grade,
      String examTitle,
      String statusLabel,
      String prepHeadline,
      String examFeatureNote,
      Integer sortOrder,
      Boolean published) {}

  public record BriefingUpsert(
      Long briefingVideoId,
      String briefingTitle,
      String briefingDescription,
      LocalDate briefingShootDate,
      LocalDate briefingPublishDate,
      Boolean briefingPublished) {}

  public record PrepStatusReplace(List<PrepStepView> steps) {}

  public record MaterialUpsert(
      Long schoolCollectionId,
      String category,
      String displayTitle,
      String blurb,
      String principalComment,
      String checklist,
      String sourceType,
      Long sourceMaterialId,
      String visibility,
      Integer previewPageCap,
      String thumbnailKey,
      Integer sortOrder,
      Boolean published) {}

  public record UpdateLogUpsert(
      Long schoolCollectionId,
      LocalDate logDate,
      String headline,
      String body,
      Boolean published,
      Integer sortOrder) {}

  public record MakingOfUpsert(
      Long schoolCollectionId,
      String stage,
      String caption,
      String imageKey,
      Long videoId,
      Boolean published,
      Integer sortOrder) {}

  public record AfterExamUpsert(
      String examFeature,
      String preparedNote,
      String connectionNote,
      String nextImprovement,
      Boolean published) {}

  public record NextExamUpsert(
      Boolean enabled,
      String nextExamName,
      String headline,
      String description,
      String ctaLabel,
      String ctaDestination) {}
}
