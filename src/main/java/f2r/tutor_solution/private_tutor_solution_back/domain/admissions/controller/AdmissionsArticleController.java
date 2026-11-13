package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsArticleService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsArticleService.AdminView;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsArticleService.Form;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsArticleService.Summary;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「바뀌는 입시」 글 API. 공개 목록(GET /api/admissions/articles)은 비로그인도 읽는다.
 * 추가·수정·숨김은 /api/admin/** (ADMIN 전용 보안 규칙)에서만 한다. 삭제 API는 없다(숨김으로 내린다).
 */
@RestController
@RequiredArgsConstructor
public class AdmissionsArticleController {

    private final AdmissionsArticleService service;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @GetMapping("/api/admissions/articles")
    public List<Summary> list() {
        return service.listPublic();
    }

    @GetMapping("/api/admin/admissions/articles")
    public List<AdminView> adminList() {
        return service.listAll();
    }

    @PostMapping("/api/admin/admissions/articles")
    public AdminView create(@RequestBody Form form) {
        return service.create(form);
    }

    @PutMapping("/api/admin/admissions/articles/{id}")
    public AdminView update(@PathVariable Long id, @RequestBody Form form) {
        return service.update(id, form);
    }

    public record Visibility(Boolean visible) {}

    @PostMapping("/api/admin/admissions/articles/{id}/visibility")
    public AdminView visibility(@PathVariable Long id, @RequestBody Visibility body) {
        if (body == null || body.visible() == null) throw new IllegalArgumentException("공개 여부를 보내 주세요.");
        return service.setVisible(id, body.visible());
    }
}
