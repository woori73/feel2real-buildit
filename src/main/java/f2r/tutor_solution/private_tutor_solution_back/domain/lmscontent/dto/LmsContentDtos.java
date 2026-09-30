package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto;

import java.time.LocalDateTime;
import java.util.List;

/** LMS 콘텐츠 관리(`/lms/{handle}/contents`)·학생 학습(`/lms/{handle}/my`, `/review/{lessonId}`) DTO 모음. */
public final class LmsContentDtos {

  private LmsContentDtos() {}

  // ── 강사(운영자) ──────────────────────────────────────────────────────────

  public record LessonOption(Long lessonId, String name, String schoolName, String gradeLabel) {}

  public record AdminContentView(
      Long lmsContentId,
      Long lessonId,
      String lessonName,
      String title,
      String description,
      String status,
      String schoolName,
      String gradeLabel,
      String examName,
      String unitLabel,
      int displayOrder,
      boolean hasVideo,
      Integer durationSec,
      Long sizeBytes,
      LocalDateTime publishedAt,
      LocalDateTime createdAt) {}

  /** 강좌관리 콘텐츠 탭 초기 로드 — 강좌 선택 목록 + 현재 콘텐츠 목록을 한 번에 내려준다. */
  public record AdminContentsView(List<LessonOption> lessons, List<AdminContentView> contents) {}

  public record ContentDetailsUpsert(
      Long lessonId,
      String title,
      String description,
      String schoolName,
      String gradeLabel,
      String examName,
      String unitLabel,
      Integer displayOrder) {}

  // ── 학생(재원생) ──────────────────────────────────────────────────────────

  public record StudentLessonSummary(
      Long lessonId, String lessonName, String schoolName, String gradeLabel, int contentCount) {}

  public record StudentContentItem(
      Long lmsContentId,
      String title,
      String description,
      String examName,
      String unitLabel,
      Integer durationSec,
      String streamUrl) {}

  public record StudentLessonDetail(
      Long lessonId,
      String lessonName,
      String schoolName,
      String gradeLabel,
      List<StudentContentItem> contents) {}
}
