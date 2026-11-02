package f2r.tutor_solution.private_tutor_solution_back.domain.sns.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsReferenceImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 관리자 API 요청·응답. 토큰·키 같은 비밀값은 어떤 응답에도 넣지 않는다. */
public final class SnsDtos {

  private SnsDtos() {}

  public record Summary(long reviewCount, long generatingCount, long failedCount, long scheduledToday, long publishedThisMonth,
      long generationsThisMonth, long spentThisMonthKrw, Integer monthlyBudgetKrw, boolean budgetConfigured,
      boolean schedulerEnabled, boolean workerConfigured, boolean metaConfigured, boolean tokenKeyConfigured,
      List<String> alerts) {}

  public record AccountView(Long id, String platform, String username, String status, LocalDateTime tokenExpiresAt,
      LocalDateTime tokenRefreshedAt, String lastError, boolean expiringSoon) {
    public static AccountView of(SnsAccount a, boolean expiringSoon) {
      return new AccountView(a.getId(), a.getPlatform().name(), a.getUsername(), a.getStatus().name(), a.getTokenExpiresAt(),
          a.getTokenRefreshedAt(), a.getLastError(), expiringSoon);
    }
  }

  public record BrandRequest(String name, String industry, String audience, String purpose, String instructions,
      String fixedText, String fixedTextPosition, String bannedPhrases, String language) {}

  public record BrandView(Long id, String name, String industry, String audience, String purpose, String instructions,
      String fixedText, String fixedTextPosition, String bannedPhrases, String language, long ruleCount) {
    public static BrandView of(SnsBrandPreset b, long ruleCount) {
      return new BrandView(b.getId(), b.getName(), b.getIndustry(), b.getAudience(), b.getPurpose(), b.getInstructions(),
          b.getFixedText(), b.getFixedTextPosition().name(), b.getBannedPhrases(), b.getLanguage(), ruleCount);
    }
  }

  public record ReferenceView(Long id, String label, String brand, boolean approved, String previewUrl, long ruleCount) {
    public static ReferenceView of(SnsReferenceImage r, long ruleCount) {
      return new ReferenceView(r.getId(), r.getLabel(), r.getBrand(), r.isApproved(), "/api/admin/sns/references/" + r.getId() + "/image",
          ruleCount);
    }
  }

  public record RuleRequest(String title, Long accountId, Long brandId, Long referenceImageId, String imagePrompt,
      String imageStyle, String aspectRatio, String fixedHashtags, Boolean aiHashtags, String scheduleType,
      String scheduleDays, LocalTime runTime, LocalDate startDate, String saveMode, Integer monthlyBudgetKrw) {}

  public record RuleView(Long id, String title, Long accountId, String accountName, Long brandId, String brandName,
      Long referenceImageId, String imagePrompt, String imageStyle, String aspectRatio, int imageCount, String fixedHashtags,
      boolean aiHashtags, String scheduleType, String scheduleDays, LocalTime runTime, LocalDate startDate, String saveMode,
      boolean enabled, Integer monthlyBudgetKrw, LocalDate lastRunOn) {
    public static RuleView of(SnsAutomationRule r, String accountName, String brandName) {
      return new RuleView(r.getId(), r.getTitle(), r.getAccountId(), accountName, r.getBrandId(), brandName,
          r.getReferenceImageId(), r.getImagePrompt(), r.getImageStyle().name(), r.getAspectRatio(), r.getImageCount(),
          r.getFixedHashtags(), r.isAiHashtags(), r.getScheduleType().name(), r.getScheduleDays(), r.getRunTime(),
          r.getStartDate(), r.getSaveMode().name(), r.isEnabled(), r.getMonthlyBudgetKrw(), r.getLastRunOn());
    }
  }

  public record PostView(Long id, Long ruleId, String ruleTitle, boolean trial, String status, String topic, String caption,
      String hashtags, String captionStatus, String imageStatus, String imageUrl, LocalDateTime scheduledAt,
      LocalDateTime publishedAt, String igMediaId, String failReason, LocalDateTime createdAt) {
    public static PostView of(SnsPost p, String ruleTitle) {
      String img = p.getMediaToken() == null ? null : "/api/sns/media/" + p.getMediaToken() + ".jpg";
      return new PostView(p.getId(), p.getRuleId(), ruleTitle, p.isTrial(), p.getStatus().name(), p.getTopic(), p.getCaption(),
          p.getHashtags(), p.getCaptionStatus().name(), p.getImageStatus().name(), img, p.getScheduledAt(), p.getPublishedAt(),
          p.getIgMediaId(), p.getFailReason(), p.getCreatedAt());
    }
  }

  public record PostEditRequest(String caption, String hashtags) {}

  public record ScheduleRequest(LocalDateTime scheduledAt) {}

  public record LogView(Long id, Long ruleId, Long postId, String kind, String model, int costKrw, long durationMs,
      boolean success, String errorMessage, LocalDateTime createdAt) {
    public static LogView of(SnsGenerationLog l) {
      return new LogView(l.getId(), l.getRuleId(), l.getPostId(), l.getKind().name(), l.getModel(), l.getCostKrw(),
          l.getDurationMs(), l.isSuccess(), l.getErrorMessage(), l.getCreatedAt());
    }
  }

  public record CaptionPreview(String topic, String caption, String hashtags, String imagePromptEn) {}

  public record Message(String message) {}
}
