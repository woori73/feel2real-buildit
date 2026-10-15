package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto;

import java.util.List;

/**
 * 내신 LIVE 학교/학년 영상 연결(Phase 4c) payload.
 *
 * <p>공개 DTO({@link LiveVideoView})에는 storage key / 원본 파일명 / 로컬 경로 / S3 key /
 * videoId / admin metadata 를 절대 싣지 않는다 — serve endpoint URL 만 준다.
 */
public final class MidtermLiveVideoDtos {

  private MidtermLiveVideoDtos() {}

  /**
   * 공개 LIVE 페이지가 소비하는 최소 뷰. role 은 scene 순서 결정용이며 화면에 표시하지 않는다.
   *
   * <p>{@code sourceKind == "EXTERNAL_LINK"} 면 서버 storage 가 없어 {@code videoUrl}·
   * {@code thumbnailUrl} 은 null 이다. {@code externalUrl}(외부 페이지) / {@code embedUrl}(iframe
   * 가능 provider 만) / {@code provider} / {@code title} 로 카드 안에서 embed 또는 CTA 를 정한다.
   */
  public record LiveVideoView(
      String school,
      int grade,
      String role,
      String videoUrl,
      String thumbnailUrl,
      String sourceKind,
      String externalUrl,
      String embedUrl,
      String provider,
      String title,
      int sortOrder) {}

  /** 운영자 화면: 연결된 link 1건(소유자라 videoId/파일명 노출 가능). */
  public record LiveVideoLinkAdminView(
      Long linkId,
      Long academyVideoId,
      String school,
      int grade,
      String role,
      boolean visible,
      int sortOrder,
      String thumbnailUrl,
      String originalFilename,
      String sourceKind,
      String externalUrl) {}

  /** 운영자 화면: 연결 가능한 공개 준비된 AcademyVideo 1건. */
  public record LinkableVideoView(
      Long academyVideoId,
      String originalFilename,
      String thumbnailUrl,
      Long durationMs,
      String purpose,
      String sourceKind,
      String externalUrl) {}

  /** 운영자 GET 응답: 현재 link 목록 + 연결 가능한 영상 목록. */
  public record LiveVideoAdminPayload(
      List<LiveVideoLinkAdminView> links,
      List<LinkableVideoView> linkableVideos) {}

  /** 운영자 신규 연결. */
  public record LiveVideoLinkCreate(
      Long academyVideoId,
      String school,
      Integer grade,
      String role,
      Boolean visible,
      Integer sortOrder) {}

  /** 운영자 수정. 대상(학교/학년/역할) 재지정과 공개/정렬 변경을 함께 받는다. */
  public record LiveVideoLinkUpdate(
      String school,
      Integer grade,
      String role,
      Boolean visible,
      Integer sortOrder) {}
}
