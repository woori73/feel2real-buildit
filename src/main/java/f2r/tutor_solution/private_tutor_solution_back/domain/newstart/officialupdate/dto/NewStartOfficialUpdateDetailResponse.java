package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import java.time.LocalDateTime;

/**
 * NewStart 내부 상세보기 화면 모델. 본문은 저장된 공식 원문만 사용한다:
 * content(수집 원문) > summary(수집 답변; 300자 초과 시 잘림) > 없음(CONTENT MISSING).
 * 어느 경우에도 저장되지 않은 내용은 생성하지 않는다.
 */
public record NewStartOfficialUpdateDetailResponse(
    Long updateId,
    String sourceType,
    String agencyName,
    String sourceName,
    String title,
    String body,
    boolean bodyIsHtml,
    ContentState contentState,
    LocalDateTime publishedAt,
    LocalDateTime discoveredAt,
    String verifiedSourceUrl) {

  public enum ContentState {
    /** 저장된 공식 원문 본문이 완전한 상태로 존재 (content 컬럼 또는 잘리지 않은 summary). */
    FULL,
    /** summary 가 300자에서 잘린 형태 — 저장된 일부만 표시, 나머지는 미수집. */
    PARTIAL,
    /** 본문 미저장(제목만/요약이 제목과 동일한 빈 답변 폴백) — AI 생성 금지, MISSING 표시. */
    MISSING
  }

  public static NewStartOfficialUpdateDetailResponse from(NewStartOfficialUpdate update,
      String verifiedSourceUrl) {
    String content = update.getContent();
    String summary = update.getSummary();
    String body;
    boolean bodyIsHtml;
    ContentState state;
    if (content != null && !content.isBlank()) {
      body = content;
      bodyIsHtml = true;
      state = ContentState.FULL;
    } else if (summary == null || summary.isBlank() || summary.equals(update.getTitle())) {
      body = null;
      bodyIsHtml = false;
      state = ContentState.MISSING;
    } else if (summary.endsWith("...")) {
      body = summary;
      bodyIsHtml = false;
      state = ContentState.PARTIAL;
    } else {
      body = summary;
      bodyIsHtml = false;
      state = ContentState.FULL;
    }
    return new NewStartOfficialUpdateDetailResponse(
        update.getUpdateId(),
        update.getSourceType().name(),
        agencyName(update.getSourceType()),
        update.getSourceName(),
        update.getTitle(),
        body,
        bodyIsHtml,
        state,
        update.getPublishedAt(),
        update.getDiscoveredAt(),
        verifiedSourceUrl);
  }

  /** 출처 기관명은 updates 화면 필터에 이미 등록된 명명만 사용 (추측 금지) — 미등록 타입은 null. */
  private static String agencyName(NewStartOfficialUpdate.SourceType sourceType) {
    return switch (sourceType) {
      case NEWSTART_FUND -> "새출발기금";
      case FSC -> "금융위원회";
      case CCRS -> "신용회복위원회";
      case HOPE_RETURN_PACKAGE -> "희망리턴패키지";
      case FSS -> "금융감독원";
      case CREDIT4U -> "Credit4U";
      case COURT -> "법원";
      case PUBLIC_AGENCY -> null;
    };
  }
}
