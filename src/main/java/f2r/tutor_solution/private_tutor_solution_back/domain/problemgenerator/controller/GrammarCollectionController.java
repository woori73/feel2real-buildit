package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarCollectionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarCollectionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarDesignCraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarItemsRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankAccessGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarCollectionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarDesignCraftService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 문법 문제은행 선택함·문제집 제작·원고 내보내기 API. */
@RestController
@RequestMapping("/api/problem-generator/grammar-bank/collections")
@RequiredArgsConstructor
public class GrammarCollectionController {

  private final GrammarCollectionService collectionService;
  private final GrammarDesignCraftService designCraftService;
  private final GrammarBankAccessGuard accessGuard;

  @GetMapping
  public ResponseEntity<List<GrammarCollectionResponse>> list(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(collectionService.list(require(principal)));
  }

  @PostMapping
  public ResponseEntity<GrammarCollectionResponse> create(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody GrammarCollectionRequest request) {
    return ResponseEntity.ok(collectionService.create(require(principal), request));
  }

  @GetMapping("/{collectionId}")
  public ResponseEntity<GrammarCollectionResponse> detail(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId) {
    return ResponseEntity.ok(collectionService.detail(require(principal), collectionId));
  }

  @PatchMapping("/{collectionId}")
  public ResponseEntity<GrammarCollectionResponse> update(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId,
      @RequestBody GrammarCollectionRequest request) {
    return ResponseEntity.ok(collectionService.update(require(principal), collectionId, request));
  }

  @DeleteMapping("/{collectionId}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long collectionId) {
    collectionService.delete(require(principal), collectionId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{collectionId}/items")
  public ResponseEntity<GrammarCollectionResponse> addItems(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId,
      @RequestBody GrammarItemsRequest request) {
    return ResponseEntity.ok(
        collectionService.addItems(require(principal), collectionId, safe(request.questionIds())));
  }

  @DeleteMapping("/{collectionId}/items")
  public ResponseEntity<GrammarCollectionResponse> removeItems(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId,
      @RequestBody GrammarItemsRequest request) {
    return ResponseEntity.ok(
        collectionService.removeItems(require(principal), collectionId, safe(request.questionIds())));
  }

  @PostMapping("/{collectionId}/reorder")
  public ResponseEntity<GrammarCollectionResponse> reorder(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId,
      @RequestBody GrammarItemsRequest request) {
    return ResponseEntity.ok(
        collectionService.reorder(require(principal), collectionId, safe(request.questionIds())));
  }

  @PostMapping("/{collectionId}/book")
  public ResponseEntity<GrammarCollectionResponse> promoteToBook(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId,
      @RequestBody(required = false) java.util.Map<String, String> body) {
    return ResponseEntity.ok(collectionService.promoteToBook(require(principal), collectionId,
        body != null ? body.get("title") : null));
  }

  /** 선택함/문제집 → DesignCraft 편집기 전달. 기존 /api/publishing.jobs 파이프라인 재사용. */
  @PostMapping("/{collectionId}/designcraft")
  public ResponseEntity<GrammarDesignCraftService.DesignCraftHandoff> toDesignCraft(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId,
      @RequestBody(required = false) GrammarDesignCraftRequest request) {
    return ResponseEntity.accepted().body(
        designCraftService.export(require(principal), collectionId, request));
  }

  /** 로컬 DesignCraft로 옮길 원고 다운로드(Markdown). 웹 편집기 실행 없이 파일만 생성한다. */
  @GetMapping("/{collectionId}/manuscript.md")
  public ResponseEntity<String> manuscriptMarkdown(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId) {
    require(principal);
    return download(designCraftService.exportMarkdown(principal, collectionId));
  }

  /** 원고 다운로드(HTML). */
  @GetMapping("/{collectionId}/manuscript.html")
  public ResponseEntity<String> manuscriptHtml(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId) {
    require(principal);
    return download(designCraftService.exportHtml(principal, collectionId));
  }

  /** 원고 다운로드(JSON 문항 배열). */
  @GetMapping("/{collectionId}/manuscript.json")
  public ResponseEntity<String> manuscriptJson(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long collectionId) {
    require(principal);
    return download(designCraftService.exportJson(principal, collectionId));
  }

  private ResponseEntity<String> download(GrammarDesignCraftService.ManuscriptExport export) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(export.contentType()))
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + export.filename() + "\"")
        .body(export.content());
  }

  private AuthenticatedPrincipal require(AuthenticatedPrincipal principal) {
    accessGuard.require(principal);
    return principal;
  }

  static List<Long> safe(List<Long> ids) {
    return ids == null ? List.of() : ids;
  }
}
