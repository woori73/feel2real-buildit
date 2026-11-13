package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** 「바뀌는 입시」 목록·본문 화면(비로그인 공개, SSR) + 관리자 글 관리 화면. */
@Controller
@RequiredArgsConstructor
public class AdmissionsArticlePageController {

    private final AdmissionsArticleService service;

    @GetMapping("/admissions/articles")
    public String list(Model model) {
        model.addAttribute("articles", service.listPublic());
        return "admissions/articles";
    }

    @GetMapping("/admissions/articles/{id:[0-9]{1,18}}")
    public String detail(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("article", service.getPublic(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다.");
        }
        return "admissions/article";
    }

    @GetMapping("/admin/admissions/articles")
    public String admin() {
        return "admissions/articles_admin";
    }
}
