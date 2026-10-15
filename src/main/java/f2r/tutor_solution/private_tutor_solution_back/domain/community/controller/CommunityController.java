package f2r.tutor_solution.private_tutor_solution_back.domain.community.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityImageService;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityPostAttachmentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityService;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.web.FileDownloadResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/**
 * 공개 커뮤니티 Q&A API. 질문(posts) / 답변(answers) / 좋아요. (2계층 — 댓글 없음)
 *
 * <p>GET(목록/상세)만 SecurityConfig permitAll(비로그인 read). 공개 GET은 principal 파라미터 없이
 * service의 currentPrincipal()로 likedByMe 도출. write/answer/like는 `/api/**` catch-all 인증.
 * `/answers/**`는 `/posts/**` permitAll glob 밖이라 자동 인증.
 *
 * <p>⚠ base가 `/api/open-community` — `/api/community/**`는 LessonCommunityController 점유라 분리. SSR 경로는 `/community`.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/open-community")
public class CommunityController {

  private final CommunityService communityService;
  private final CommunityImageService communityImageService;
  private final CommunityPostAttachmentService communityPostAttachmentService;

  /* ── 질문 ── */

  @GetMapping("/posts")
  public ResponseEntity<List<CommunityPostListResponse>> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String boardType,
      @RequestParam(required = false) Integer subjectId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "latest") String sort) {
    requireWikiMember(principal, "wiki".equalsIgnoreCase(scope));
    return ResponseEntity.ok(communityService.list(scope, boardType, subjectId, keyword, sort));
  }

  @GetMapping("/posts/{postId}")
  public ResponseEntity<CommunityPostDetailResponse> detail(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    CommunityPostDetailResponse detail = communityService.detail(postId);
    requireWikiMember(principal, isWikiBoard(detail.boardType()));
    return ResponseEntity.ok(detail);
  }

  @PostMapping(value = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CommunityPostDetailResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody CommunityPostRequest request) {
    return ResponseEntity.ok(communityService.createPost(principal, request));
  }

  /**
   * 글 + 문서/이미지 첨부를 한 번의 multipart 요청으로 원자적으로 생성한다(변형문제·자료 공유·요청).
   * 첨부 검증/저장이 실패하면 글까지 롤백되어 고아 글이 남지 않는다.
   */
  @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommunityPostDetailResponse> createWithAttachments(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam String boardType,
      @RequestParam(required = false) Integer subjectId,
      @RequestParam String title,
      @RequestParam(required = false) String contentJson,
      @RequestParam(required = false) String contentHtml,
      @RequestParam(value = "files", required = false) List<MultipartFile> files,
      @RequestParam(value = "files[]", required = false) List<MultipartFile> bracketFiles) {
    CommunityPostRequest request =
        new CommunityPostRequest(boardType, subjectId, title, contentJson, contentHtml);
    return ResponseEntity.ok(
        communityService.createPostWithAttachments(principal, request, mergeFiles(files, bracketFiles)));
  }

  @PutMapping("/posts/{postId}")
  public ResponseEntity<CommunityPostDetailResponse> update(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestBody CommunityPostRequest request) {
    return ResponseEntity.ok(communityService.updatePost(principal, postId, request));
  }

  @DeleteMapping("/posts/{postId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    communityService.deletePost(principal, postId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/posts/{postId}/like")
  public ResponseEntity<CommunityPostDetailResponse> toggleLike(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    return ResponseEntity.ok(communityService.toggleLike(principal, postId));
  }

  /* ── 답변 ── */

  @PostMapping("/posts/{postId}/answers")
  public ResponseEntity<CommunityPostDetailResponse> createAnswer(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestBody AnswerRequest request) {
    return ResponseEntity.ok(communityService.createAnswer(principal, postId, request));
  }

  @PutMapping("/answers/{answerId}")
  public ResponseEntity<CommunityPostDetailResponse> updateAnswer(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long answerId,
      @RequestBody AnswerRequest request) {
    return ResponseEntity.ok(communityService.updateAnswer(principal, answerId, request));
  }

  @DeleteMapping("/answers/{answerId}")
  public ResponseEntity<Void> deleteAnswer(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long answerId) {
    communityService.deleteAnswer(principal, answerId);
    return ResponseEntity.noContent().build();
  }

  /* ── 본문 이미지 ── */

  @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommunityImageResponse> uploadImage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(communityImageService.upload(principal, image));
  }

  /** 공개 serve. SecurityConfig에서 GET /api/open-community/images/** permitAll. */
  @GetMapping("/images/{imageId}")
  public ResponseEntity<byte[]> getImage(@PathVariable Long imageId) {
    CommunityImageService.ServedImage image = communityImageService.readImage(imageId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  /* ── 게시글 문서 첨부 (변형문제·자료 공유·요청) ── */

  @PostMapping(value = "/posts/{postId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommunityPostDetailResponse> addAttachments(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestParam(value = "files", required = false) List<MultipartFile> files,
      @RequestParam(value = "files[]", required = false) List<MultipartFile> bracketFiles) {
    communityPostAttachmentService.addAttachments(principal, postId, mergeFiles(files, bracketFiles));
    return ResponseEntity.ok(communityService.detail(postId));
  }

  @DeleteMapping("/attachments/{attachmentId}")
  public ResponseEntity<Void> deleteAttachment(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long attachmentId) {
    communityPostAttachmentService.deleteAttachment(principal, attachmentId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 공개 다운로드. SecurityConfig에서 GET /api/open-community/attachments/** permitAll.
   * [내려받기] 버튼이므로 파일 종류(이미지·PDF 포함)와 무관하게 항상 {@code Content-Disposition: attachment}
   * 로 강제한다 — 브라우저가 이미지/PDF를 화면에 펼치지 않고 실제로 내려받게.
   */
  @GetMapping("/attachments/{attachmentId}/file")
  public ResponseEntity<byte[]> getAttachment(@PathVariable Long attachmentId) {
    CommunityPostAttachmentService.AttachmentFileBundle bundle =
        communityPostAttachmentService.getAttachmentFile(attachmentId);
    return FileDownloadResponses.attachment(bundle.bytes(), bundle.contentType(), bundle.filename());
  }

  private boolean isWikiBoard(String boardType) {
    return "EXAM_REVIEW".equals(boardType)
        || "VARIANT".equals(boardType)
        || "MATERIAL".equals(boardType);
  }

  private void requireWikiMember(
      JwtTokenProvider.AuthenticatedPrincipal principal, boolean wikiContent) {
    if (wikiContent && principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
  }

  private List<MultipartFile> mergeFiles(List<MultipartFile> files, List<MultipartFile> bracketFiles) {
    if (files == null || files.isEmpty()) return bracketFiles;
    if (bracketFiles == null || bracketFiles.isEmpty()) return files;
    List<MultipartFile> merged = new java.util.ArrayList<>(files);
    merged.addAll(bracketFiles);
    return merged;
  }
}
