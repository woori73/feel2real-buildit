package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 공개 BuildIt Wiki 문항별 화면.
 *
 * <p>{@code /wiki/questions} — 학년·모의고사별 문항 네비게이션.
 * <p>{@code /wiki/questions/{id}} — 문항 1건: 문맥 → 학습자료 → THREAD → 변형문제 → 기출 THREAD → (향후) AI SUMMARY.
 *
 * <p>role 조건부 렌더 없음. 로그인/권한은 클라이언트({@code /api/auth/me})와 서버 API 가 최종 결정한다("UI 표시 ≠ 권한 부여").
 * {@code Cache-Control: no-store} 때문에 view-controller 대신 컨트롤러로 둔다(다른 /wiki 화면과 동일).
 */
@Controller
public class QuestionWikiPageController {

  @GetMapping("/wiki/questions")
  public String list(HttpServletResponse response) {
    return noStore(response, "public/question_wiki_list");
  }

  @GetMapping("/wiki/questions/{questionWikiId}")
  public String detail(HttpServletResponse response) {
    return noStore(response, "public/question_wiki");
  }

  private String noStore(HttpServletResponse response, String view) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return view;
  }
}
