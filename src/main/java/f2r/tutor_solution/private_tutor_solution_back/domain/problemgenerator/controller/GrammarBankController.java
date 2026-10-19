package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarAiFindService;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankAccessGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 영어 문법 문제은행 API. 내신위키 React SPA(/problem-generator/wiki)의 '문법 문제은행' 화면과
 * AI·MCP 검색이 동일 엔드포인트를 사용한다. 기존 questions/세션 DB를 재사용하고 확장 프로필만 별도 관리.
 */
@RestController
@RequestMapping("/api/problem-generator/grammar-bank")
@RequiredArgsConstructor
public class GrammarBankController {

  private final GrammarBankService bankService;
  private final GrammarBankImportService importService;
  private final GrammarAiFindService aiFindService;
  private final GrammarBankAccessGuard accessGuard;

  @GetMapping("/tags")
  public ResponseEntity<List<GrammarTagResponse>> tags(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.tags().stream()
        .map(t -> new GrammarTagResponse(t.getTagId(), t.getNameKo(), t.getNameEn(), t.getAliases(),
            t.getSortOrder())).toList());
  }

  @GetMapping("/stats")
  public ResponseEntity<GrammarStatsResponse> stats(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.stats());
  }

  @GetMapping("/questions")
  public ResponseEntity<Page<GrammarQuestionItemResponse>> search(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam(required = false) String schoolLevel,
      @RequestParam(required = false) String questionFormat,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String reviewStatus,
      @RequestParam(required = false) String rightsStatus,
      @RequestParam(required = false) BigDecimal minConfidence,
      @RequestParam(required = false) BigDecimal maxConfidence,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) List<Long> tagIds,
      @RequestParam(required = false) String recommendedGrade,
      @PageableDefault(size = 20) Pageable pageable) {
    requirePrincipal(principal);
    GrammarBankService.SearchFilters filters = new GrammarBankService.SearchFilters(
        enumOrNull(SchoolLevel.class, schoolLevel),
        enumOrNull(GrammarQuestionFormat.class, questionFormat),
        enumOrNull(GrammarDifficulty.class, difficulty),
        enumOrNull(GrammarReviewStatus.class, reviewStatus),
        enumOrNull(GrammarRightsStatus.class, rightsStatus),
        minConfidence, maxConfidence, search, tagIds, enumOrNull(SchoolLevel.class, recommendedGrade));
    return ResponseEntity.ok(bankService.search(filters, pageable));
  }

  @GetMapping("/questions/{questionId}")
  public ResponseEntity<GrammarQuestionDetailResponse> detail(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long questionId) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.detail(questionId));
  }

  @PatchMapping("/questions/{questionId}")
  public ResponseEntity<GrammarQuestionDetailResponse> updateQuestion(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long questionId,
      @RequestBody GrammarQuestionUpdateRequest request) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.updateQuestion(principal, questionId, request));
  }

  @PatchMapping("/questions/{questionId}/profile")
  public ResponseEntity<GrammarQuestionDetailResponse> updateProfile(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long questionId,
      @RequestBody GrammarProfileUpdateRequest request) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.updateProfile(principal, questionId, request));
  }

  @PostMapping("/questions/{questionId}/review")
  public ResponseEntity<GrammarQuestionDetailResponse> review(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long questionId,
      @RequestBody GrammarReviewRequest request) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.review(principal, questionId, request));
  }

  @PostMapping("/questions/{questionId}/variants")
  public ResponseEntity<GrammarVariantResponse> createVariant(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long questionId,
      @RequestBody GrammarVariantCreateRequest request) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.createVariant(principal, questionId, request));
  }

  @PostMapping("/variants/{variantId}/review")
  public ResponseEntity<GrammarVariantResponse> reviewVariant(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long variantId,
      @RequestBody java.util.Map<String, String> body) {
    requirePrincipal(principal);
    return ResponseEntity.ok(bankService.reviewVariant(principal, variantId,
        body.get("status"), body.get("reviewNote")));
  }

  @PostMapping("/import")
  public ResponseEntity<GrammarImportResultResponse> importJson(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody GrammarImportRequest request) {
    requirePrincipal(principal);
    return ResponseEntity.ok(importService.importJson(principal, request));
  }

  /** CSV 파일 수입 (2,824개 파일 추출 결과 등록용). */
  @PostMapping(value = "/import/csv", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<GrammarImportResultResponse> importCsvFile(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) Long materialId,
      @RequestParam(required = false) String defaultRightsStatus) throws java.io.IOException {
    requirePrincipal(principal);
    String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
    return ResponseEntity.ok(
        importService.importCsv(principal, materialId, defaultRightsStatus, csv));
  }

  @PostMapping(value = "/import/csv", consumes = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<GrammarImportResultResponse> importCsvText(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam(required = false) Long materialId,
      @RequestParam(required = false) String defaultRightsStatus,
      @RequestBody String csv) {
    requirePrincipal(principal);
    return ResponseEntity.ok(importService.importCsv(principal, materialId, defaultRightsStatus, csv));
  }

  /** 자연어 문제 검색 — "중2 수동태 객관식 문제 30개 찾아줘" 등. 결과는 실제 DB 조회만 반환. */
  @PostMapping("/ai/find")
  public ResponseEntity<GrammarAiFindResponse> aiFind(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody GrammarAiFindRequest request) {
    requirePrincipal(principal);
    return ResponseEntity.ok(aiFindService.find(principal, request.request()));
  }

  private void requirePrincipal(AuthenticatedPrincipal principal) {
    accessGuard.require(principal);
  }

  static <E extends Enum<E>> E enumOrNull(Class<E> type, String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, raw.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "필터 값이 올바르지 않습니다: " + raw);
    }
  }

  /** 태그 목록 응답용 — entity 노출을 좁힌다. */
  public record GrammarTagResponse(Long tagId, String nameKo, String nameEn, String aliases,
      Integer sortOrder) {
  }
}
