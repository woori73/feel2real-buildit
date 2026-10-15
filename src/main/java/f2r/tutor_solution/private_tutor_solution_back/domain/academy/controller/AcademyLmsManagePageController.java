package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 학원 LMS의 <b>운영자 전용</b> 화면 2개 (강좌관리 · 콘텐츠 관리).
 *
 * <p><b>클래스 불변식: 이 클래스의 모든 handler는 {@link #requireOwner}로 시작한다.</b>
 * 공개/학생 화면과 클래스를 나눈 이유가 이것뿐이다 — owner 표면 전체가 한 파일에 모여 있어야
 * 리뷰어가 가드 누락을 눈으로 잡을 수 있다. 3번째 handler를 추가할 때도 같은 규칙을 지킬 것.
 *
 * <p><b>2중 방어.</b> {@code SecurityConfig}의 {@code /lms/*&#47;manage|contents → hasRole("TUTOR")}가
 * 익명·STUDENT·ADMIN을 필터체인에서 끊는다. 여기 도달하는 건 TUTOR뿐이고, 남은 위험인
 * "<b>다른 학원</b>의 TUTOR"를 {@code requireOwnedAcademyByHandle}이 막는다. role 검사만으로는
 * 이걸 절대 못 막는다.
 *
 * <p><b>차단 시 응답은 HTML 에러 페이지가 아니다.</b> {@code AccessDeniedException}은
 * {@code GlobalExceptionHandler}(@RestControllerAdvice)가 잡아 403 + 평문 본문으로 변환한다.
 * ADMIN도 통과하지 못한다({@code verifyOwnsAcademy}에 ADMIN 우회 없음).
 */
@Controller
@RequiredArgsConstructor
public class AcademyLmsManagePageController {

  private final AcademyService academyService;
  private final AcademyLmsShellModel shellModel;

  @GetMapping("/lms/{handle}/manage")
  public String manage(@PathVariable String handle, Model model) {
    requireOwner(model, handle);
    return "lms/lms_manage";
  }

  @GetMapping("/lms/{handle}/contents")
  public String contents(@PathVariable String handle, Model model) {
    requireOwner(model, handle);
    return "lms/lms_contents";
  }

  /**
   * handle 학원의 소유자만 통과. 미발행/숨김 학원도 owner는 관리해야 하므로 공개 필터 없이 조회한다.
   *
   * @throws org.springframework.web.server.ResponseStatusException handle이 없으면 404
   * @throws org.springframework.security.access.AccessDeniedException 소유자가 아니면 403
   */
  private void requireOwner(Model model, String handle) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    shellModel.populate(model, academy, handle);
  }
}
