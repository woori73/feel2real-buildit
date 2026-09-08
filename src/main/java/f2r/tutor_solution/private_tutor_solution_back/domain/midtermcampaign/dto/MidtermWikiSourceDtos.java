package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto;

import java.util.List;

/**
 * TUTOR Wiki HTML 자료를 Midterm LIVE의 read-only visual로만 쓰기 위한 payload.
 * 자료 본문과 metadata의 Source of Truth는 Wiki이며, 공개 여부만 별도 exposure로 관리한다.
 * storageKey, 원본 URL, download locator는 어떤 DTO에도 포함하지 않는다.
 */
public final class MidtermWikiSourceDtos {

  private MidtermWikiSourceDtos() {}

  public record WikiSourceView(
      String sourceId,
      String kind,
      String kindLabel,
      String title,
      String school,
      String grade,
      Integer year,
      String semester,
      String exam,
      String createdAt,
      String previewUrl) {}

  /** Signed TUTOR Wiki candidate endpoint의 eligible row. */
  public record WikiSourceRow(
      String kind,
      long id,
      String title,
      String school,
      String grade,
      int year,
      String semester,
      String exam,
      String createdAt) {}

  /** school/grade 없이 published campaign이 서버에서 결정하는 exact scope. */
  public record WikiCampaignScope(int year, String semester, String exam) {}

  /** 공개 목록 결과. diagnostics는 서버 내부 확인용이며 공개 controller는 views만 반환한다. */
  public record WikiSourceListResult(
      List<WikiSourceView> views, List<WikiCampaignScope> unmatchedContexts) {}

  /** 운영자 후보 목록: Wiki metadata + 최소 exposure 상태. */
  public record WikiCandidateAdminView(
      String sourceId,
      String sourceKind,
      String school,
      String grade,
      Integer year,
      String semester,
      String exam,
      String createdAt,
      boolean hasHtml,
      boolean visible,
      boolean featured,
      int sortOrder) {}

  public record WikiExposureUpdate(boolean visible, boolean featured, int sortOrder) {}
}
