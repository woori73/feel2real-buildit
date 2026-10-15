package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPostType;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.service.LessonCommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class LessonCommunityController {
  private final LessonCommunityService service;

  @GetMapping("/lessons/{lessonId}/community")
  public String page(@PathVariable Long lessonId, Model model) {
    model.addAttribute("lessonId", lessonId);
    return "lesson_community";
  }

  @ResponseBody
  @GetMapping("/api/lessons/{lessonId}/community/context")
  public LessonCommunityContextResponse context(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId) {
    return service.context(principal, lessonId);
  }

  @ResponseBody
  @GetMapping("/api/lessons/{lessonId}/community/categories")
  public List<LessonCommunityCategoryResponse> categories(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId) {
    return service.listCategories(principal, lessonId);
  }

  @ResponseBody
  @PostMapping("/api/lessons/{lessonId}/community/categories")
  public LessonCommunityCategoryResponse createCategory(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId,
      @RequestBody LessonCommunityCategoryRequest request) {
    return service.createCategory(principal, lessonId, request);
  }

  @ResponseBody
  @PutMapping("/api/lessons/{lessonId}/community/categories/{categoryId}")
  public LessonCommunityCategoryResponse updateCategory(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId,
      @PathVariable Long categoryId,
      @RequestBody LessonCommunityCategoryRequest request) {
    return service.updateCategory(principal, lessonId, categoryId, request);
  }

  @ResponseBody
  @DeleteMapping("/api/lessons/{lessonId}/community/categories/{categoryId}")
  public ResponseEntity<Void> deleteCategory(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId,
      @PathVariable Long categoryId) {
    service.deleteCategory(principal, lessonId, categoryId);
    return ResponseEntity.noContent().build();
  }

  @ResponseBody
  @GetMapping("/api/lessons/{lessonId}/community/posts")
  public LessonCommunityPostListResponse posts(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId,
      @RequestParam(required = false) String tab,
      @RequestParam(required = false) Long categoryId) {
    return service.listPosts(principal, lessonId, tab, categoryId);
  }

  @ResponseBody
  @PostMapping(value = "/api/lessons/{lessonId}/community/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public LessonCommunityPostDetailResponse createPost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId,
      @RequestParam(value = "postType", required = false) LessonCommunityPostType postType,
      @RequestParam("title") String title,
      @RequestParam("content") String content,
      @RequestParam(value = "categoryId", required = false) Long categoryId,
      @RequestParam(value = "qnaPublic", defaultValue = "false") boolean qnaPublic,
      @RequestParam(value = "files", required = false) List<MultipartFile> files,
      @RequestParam(value = "files[]", required = false) List<MultipartFile> bracketFiles) {
    return service.createPost(principal, lessonId,
        new LessonCommunityPostRequest(postType, title, content, categoryId, qnaPublic), mergeFiles(files, bracketFiles));
  }

  @ResponseBody
  @GetMapping("/api/community/posts/{postId}")
  public LessonCommunityPostDetailResponse getPost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    return service.getPost(principal, postId);
  }

  @ResponseBody
  @PutMapping("/api/community/posts/{postId}")
  public LessonCommunityPostDetailResponse updatePost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestBody LessonCommunityPostRequest request) {
    return service.updatePost(principal, postId, request);
  }

  @ResponseBody
  @DeleteMapping("/api/community/posts/{postId}")
  public ResponseEntity<Void> hidePost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    service.hidePost(principal, postId);
    return ResponseEntity.noContent().build();
  }

  @ResponseBody
  @PostMapping(value = "/api/community/posts/{postId}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public LessonCommunityPostDetailResponse createComment(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestParam("content") String content) {
    return service.createComment(principal, postId, new LessonCommunityCommentRequest(content));
  }

  @ResponseBody
  @PutMapping("/api/community/comments/{commentId}")
  public LessonCommunityPostDetailResponse updateComment(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long commentId,
      @RequestBody LessonCommunityCommentRequest request) {
    return service.updateComment(principal, commentId, request);
  }

  @ResponseBody
  @DeleteMapping("/api/community/comments/{commentId}")
  public ResponseEntity<Void> hideComment(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long commentId) {
    service.hideComment(principal, commentId);
    return ResponseEntity.noContent().build();
  }


  @ResponseBody
  @GetMapping("/api/admin/lesson-communities")
  public List<AdminLessonCommunitySummaryResponse> adminCommunities(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) String keyword) {
    return service.searchAdminCommunities(principal, keyword);
  }

  @GetMapping("/api/community/attachments/{attachmentId}/file")
  public ResponseEntity<byte[]> attachment(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long attachmentId) {
    LessonCommunityService.AttachmentFileBundle bundle = service.getAttachmentFile(principal, attachmentId);
    MediaType mediaType = safeMediaType(bundle.contentType());
    boolean inline = isInlineSafe(mediaType);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(bundle.filename(), inline))
        .contentType(mediaType)
        .body(bundle.bytes());
  }

  private List<MultipartFile> mergeFiles(List<MultipartFile> files, List<MultipartFile> bracketFiles) {
    if (files == null || files.isEmpty()) return bracketFiles;
    if (bracketFiles == null || bracketFiles.isEmpty()) return files;
    java.util.ArrayList<MultipartFile> merged = new java.util.ArrayList<>(files);
    merged.addAll(bracketFiles);
    return merged;
  }

  private MediaType safeMediaType(String contentType) {
    if (contentType == null || contentType.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
    try {
      MediaType parsed = MediaType.parseMediaType(contentType);
      return isInlineSafe(parsed) ? parsed : MediaType.APPLICATION_OCTET_STREAM;
    } catch (Exception e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private boolean isInlineSafe(MediaType mediaType) {
    if (mediaType == null) return false;
    if (MediaType.APPLICATION_PDF.includes(mediaType)) return true;
    String type = mediaType.getType();
    String subtype = mediaType.getSubtype();
    return "image".equalsIgnoreCase(type) && !"svg+xml".equalsIgnoreCase(subtype);
  }

  private String contentDisposition(String filename, boolean inline) {
    String safe = filename == null || filename.isBlank() ? "attachment" : filename;
    String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    String asciiFallback = safe.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
    String disposition = inline ? "inline" : "attachment";
    return disposition + "; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
  }
}
