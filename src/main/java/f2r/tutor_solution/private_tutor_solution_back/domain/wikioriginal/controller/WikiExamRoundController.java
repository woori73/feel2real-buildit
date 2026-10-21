package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminVisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamRoundResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamRoundService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모의고사 위키 회차(연도·월) API.
 * 공개 목록·현재 회차는 메타데이터만이라 익명 허용(SecurityConfig), 관리는 /api/admin/** ADMIN 전용.
 */
@RestController
@RequiredArgsConstructor
public class WikiExamRoundController {

  private final WikiExamRoundService service;

  @GetMapping("/api/wiki/rounds")
  public ResponseEntity<List<WikiExamRoundResponse>> listPublic() {
    return ResponseEntity.ok(service.listPublic());
  }

  @GetMapping("/api/wiki/rounds/current")
  public ResponseEntity<WikiExamRoundResponse> currentPublic() {
    return ResponseEntity.ok(service.currentPublic());
  }

  @GetMapping("/api/admin/wiki/rounds")
  public ResponseEntity<List<WikiExamRoundResponse>> listAdmin() {
    return ResponseEntity.ok(service.listAdmin());
  }

  @PostMapping("/api/admin/wiki/rounds")
  public ResponseEntity<WikiExamRoundResponse> createAdmin(
      @RequestParam Integer examYear,
      @RequestParam Integer examMonth,
      @RequestParam String title,
      @RequestParam(required = false) String subtitle,
      @RequestParam(required = false) String bannerPath,
      @RequestParam(defaultValue = "true") boolean published,
      @RequestParam(defaultValue = "false") boolean current) {
    return ResponseEntity.ok(
        service.create(examYear, examMonth, title, subtitle, bannerPath, published, current));
  }

  @PutMapping("/api/admin/wiki/rounds/{id}")
  public ResponseEntity<WikiExamRoundResponse> updateAdmin(
      @PathVariable Long id,
      @RequestParam Integer examYear,
      @RequestParam Integer examMonth,
      @RequestParam String title,
      @RequestParam(required = false) String subtitle,
      @RequestParam(required = false) String bannerPath,
      @RequestParam(defaultValue = "true") boolean published) {
    return ResponseEntity.ok(
        service.update(id, examYear, examMonth, title, subtitle, bannerPath, published));
  }

  @PatchMapping("/api/admin/wiki/rounds/{id}/current")
  public ResponseEntity<WikiExamRoundResponse> setCurrent(@PathVariable Long id) {
    return ResponseEntity.ok(service.setCurrent(id));
  }

  @PatchMapping("/api/admin/wiki/rounds/{id}/published")
  public ResponseEntity<WikiExamRoundResponse> updatePublished(
      @PathVariable Long id, @RequestBody AdminVisibilityUpdateRequest request) {
    return ResponseEntity.ok(service.updatePublished(id, request.isVisible()));
  }

  @DeleteMapping("/api/admin/wiki/rounds/{id}")
  public ResponseEntity<Void> softDelete(@PathVariable Long id) {
    service.softDelete(id);
    return ResponseEntity.noContent().build();
  }
}
