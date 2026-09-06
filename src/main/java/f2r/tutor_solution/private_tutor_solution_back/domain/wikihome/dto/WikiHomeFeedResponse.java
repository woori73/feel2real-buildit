package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 BuildIt Wiki 메인(/wiki) aggregate 응답. 메인 진입 시 1회 호출로 상단 실시간 영역을 모두 채운다.
 * (WIKI LIVE·인기 다운로드의 주기적 갱신은 별도 경량 엔드포인트 — {@code /api/wiki/live-messages},
 * {@code /api/wiki/popular-downloads} — 를 쓴다.)
 *
 * <p>개인정보 없음: 회원은 마스킹 표시명 + 가입일까지만, 다운로드는 자료 정보 + 횟수까지만.
 */
public record WikiHomeFeedResponse(
    List<LatestUpdateItem> latestUpdates,
    List<RecentThreadItem> latestThreads,
    List<RecentMemberItem> recentMembers,
    List<f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse> liveMessages,
    PopularDownloads popularDownloads
) {

  /** NEW 최신 업데이트 항목 — 운영/회원이 최근 올린 자료(원본·분석·학습). */
  public record LatestUpdateItem(
      String kind,          // ORIGINAL | ANALYSIS | LEARNING
      String kindLabel,     // "모의고사 원본" 등
      Integer grade,        // 1|2|3, 없으면 null
      String title,
      LocalDateTime updatedAt,
      boolean isNew,        // 최근 48시간 이내
      String href
  ) {}

  /** 최근 문항 분석(Wiki Thread) 활동 — 문항 단위. */
  public record RecentThreadItem(
      Long questionWikiId,
      Integer grade,
      int questionNumber,
      String examLabel,
      String displayContext,
      LocalDateTime lastActivityAt,
      String href
  ) {}

  /** 새로 함께한 회원 — 마스킹 표시명 + 가입일. (user id/role/loginId/email 없음) */
  public record RecentMemberItem(String displayName, LocalDateTime joinedAt) {}

  /** 지금 많이 받는 자료 TOP N. */
  public record PopularDownloads(
      String periodLabel,   // "최근 24시간" | "최근 7일"
      int periodHours,      // 24 | 168 — 표시 기간과 집계 기간은 항상 일치
      List<PopularDownloadItem> items
  ) {}

  public record PopularDownloadItem(
      int rank,
      Long materialId,
      String category,      // ORIGINAL | ANALYSIS | LEARNING
      String categoryLabel, // "분석 자료" 등
      Integer grade,        // 원본 자료에서 조회. 삭제/미상이면 null
      String title,
      long downloadCount,
      String trend,         // NEW | UP | DOWN | SAME
      int trendAmount,      // UP/DOWN 일 때 순위 변동폭(칸 수), 그 외 0
      String href
  ) {}
}
