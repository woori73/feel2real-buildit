package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class AcademyBlogController {
  private final AcademyBlogService service;

  @GetMapping("/api/tutor/academy/blog-posts")
  public ResponseEntity<List<AcademyBlogPostResponse>> listMine(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(service.listMine(principal));
  }

  @PostMapping("/api/tutor/academy/blog-posts")
  public ResponseEntity<AcademyBlogPostResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AcademyBlogPostRequest request) {
    return ResponseEntity.ok(service.create(principal, request));
  }

  @GetMapping("/api/tutor/academy/blog-posts/{postId}")
  public ResponseEntity<AcademyBlogPostResponse> getMine(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    return ResponseEntity.ok(service.getMine(principal, postId));
  }

  @PutMapping("/api/tutor/academy/blog-posts/{postId}")
  public ResponseEntity<AcademyBlogPostResponse> update(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestBody AcademyBlogPostRequest request) {
    return ResponseEntity.ok(service.update(principal, postId, request));
  }

  @DeleteMapping("/api/tutor/academy/blog-posts/{postId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    service.delete(principal, postId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/api/tutor/academy/blog-sources/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AcademyBlogSourceResponse> uploadImage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(service.uploadImage(principal, image));
  }

  @GetMapping("/api/academy-posts")
  public ResponseEntity<List<AcademyBlogPostResponse>> searchPublicPosts(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "latest") String sort) {
    return ResponseEntity.ok(service.searchPublicPosts(keyword, sort));
  }

  @PostMapping("/api/academy-posts/{postId}/like")
  public ResponseEntity<AcademyBlogPostResponse> toggleLike(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    return ResponseEntity.ok(service.toggleLike(principal, postId));
  }

  @GetMapping("/api/academies/{academySlug}/blog-posts")
  public ResponseEntity<List<AcademyBlogPostResponse>> listPublic(@PathVariable String academySlug) {
    return ResponseEntity.ok(service.listPublic(academySlug));
  }

  @GetMapping("/api/academies/{academySlug}/blog-posts/{postSlug}")
  public ResponseEntity<AcademyBlogPostResponse> getPublic(
      @PathVariable String academySlug,
      @PathVariable String postSlug) {
    return ResponseEntity.ok(service.getPublic(academySlug, postSlug));
  }

  @GetMapping("/api/academies/{academySlug}/blog-sources/{sourceId}/file")
  public ResponseEntity<byte[]> getImage(
      @PathVariable String academySlug,
      @PathVariable Long sourceId) {
    AcademyBlogService.ImageBundle bundle = service.readImage(academySlug, sourceId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
        .contentType(MediaType.parseMediaType(bundle.contentType()))
        .body(bundle.bytes());
  }
}
