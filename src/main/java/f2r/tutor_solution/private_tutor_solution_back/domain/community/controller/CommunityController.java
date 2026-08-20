package f2r.tutor_solution.private_tutor_solution_back.domain.community.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityImageService;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

  /* ── 질문 ── */

  @GetMapping("/posts")
  public ResponseEntity<List<CommunityPostListResponse>> list(
      @RequestParam(required = false) String boardType,
      @RequestParam(required = false) Integer subjectId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "latest") String sort) {
    return ResponseEntity.ok(communityService.list(boardType, subjectId, keyword, sort));
  }

  @GetMapping("/posts/{postId}")
  public ResponseEntity<CommunityPostDetailResponse> detail(@PathVariable Long postId) {
    return ResponseEntity.ok(communityService.detail(postId));
  }

  @PostMapping("/posts")
  public ResponseEntity<CommunityPostDetailResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody CommunityPostRequest request) {
    return ResponseEntity.ok(communityService.createPost(principal, request));
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
}
