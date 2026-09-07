package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 학부모/학생용 공개 페이지({@code /eduholic-midterm-2026.html})와 팝업이 소비하는 payload.
 * storage key 는 절대 싣지 않는다 — 파일은 전용 endpoint URL 로만 노출한다.
 */
public final class MidtermPublicDtos {

  private MidtermPublicDtos() {}

  public record CampaignView(
      String examName,
      int examYear,
      String semester,
      String examType,
      String state,
      LocalDate periodStart,
      LocalDate periodEnd,
      Hero hero,
      List<LiveChip> liveChips,
      List<SchoolCollectionView> schoolCollections,
      List<MaterialCard> campaignMaterials,
      List<UpdateLogView> campaignUpdateLogs,
      NextExamView nextExam,
      PopupView popup) {}

  public record Hero(String eyebrow, String title, String body, String ctaLabel) {}

  public record LiveChip(String label, LocalDate updatedOn) {}

  public record SchoolCollectionView(
      Long id,
      String schoolName,
      int grade,
      String examTitle,
      String statusLabel,
      String prepHeadline,
      String examFeatureNote,
      int materialCount,
      LocalDate lastUpdate,
      Briefing briefing,
      List<PrepStep> prepStatuses,
      List<MaterialCard> materials,
      List<UpdateLogView> updateLogs) {}

  public record Briefing(
      Long videoId,
      String videoUrl,
      String thumbnailUrl,
      String title,
      String description,
      LocalDate shootDate) {}

  public record PrepStep(String stepKey, String label, String state) {}

  /** 목록 카드. 파일 접근 가능 여부는 {@link #access} 로만 판단(URL 유무가 아님). */
  public record MaterialCard(
      Long id,
      String category,
      String displayTitle,
      String blurb,
      String principalComment,
      List<String> checklist,
      String visibility,
      String access,
      String thumbnailUrl,
      String fileUrl,
      String sourceLabel) {}

  public record UpdateLogView(LocalDate date, String headline, String body, String scope) {}

  public record NextExamView(
      String nextExamName,
      String headline,
      String description,
      String ctaLabel,
      String ctaDestination) {}

  public record PopupView(String headline, String body, String visualUrl, List<String> schoolChips) {}
}
