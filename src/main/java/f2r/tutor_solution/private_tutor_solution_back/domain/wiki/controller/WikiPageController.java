package f2r.tutor_solution.private_tutor_solution_back.domain.wiki.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 공개 BuildIt Wiki 허브·전체보기 화면(/wiki, /wiki/originals, /wiki/analysis).
 *
 * <p><b>role 조건부 렌더 없음.</b> 자료 업로드 버튼·모달·스크립트는 principal 과 무관하게 항상 HTML 에
 * 포함된다(`fragments/_wiki_admin_upload`). 실제 권한은 {@code wiki_admin_upload.js} 가 클릭 시점에
 * {@code GET /api/auth/me} 로 확인하고, 최종 권한은 {@code POST /api/admin/wiki/**}
 * (Spring Security {@code hasRole('ADMIN')}) 이 결정한다. "UI 표시 ≠ 권한 부여".
 *
 * <p>view-controller 대신 컨트롤러로 두는 이유는 {@code Cache-Control: no-store} 뿐이다
 * (계정 상태에 따라 계정 메뉴 등이 클라이언트에서 바뀌므로 앞단 캐시가 HTML 을 공유하지 않게).
 *
 * <p>게시판 화면(/wiki/board/**, /wiki/posts/**)은 {@code WebMvcConfig} view-controller 로 둔다.
 */
@Controller
public class WikiPageController {

  @GetMapping("/wiki")
  public String hub(HttpServletResponse response) {
    return noStore(response, "public/wiki");
  }

  @GetMapping("/wiki/originals")
  public String originals(HttpServletResponse response) {
    return noStore(response, "public/wiki_originals");
  }

  @GetMapping("/wiki/analysis")
  public String analysis(HttpServletResponse response) {
    return noStore(response, "public/wiki_analysis");
  }

  private String noStore(HttpServletResponse response, String view) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return view;
  }
}
