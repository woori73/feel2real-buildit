package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 학원 LMS의 공개·학생 화면 6개 (1단계 clickable prototype).
 *
 * <p><b>URL 체계.</b> 정본은 로컬 형태 {@code /lms/{handle}/{feature}} 하나다. prd의
 * {@code {handle}.feel2real.co.kr/lms/{feature}}는 {@code SubdomainAcademyFilter}가 인가 이전에
 * 이 형태로 정규화하므로, 매핑도 인가 매처도 한 벌만 존재한다.
 *
 * <p><b>식별자는 slug가 아니라 handle이다.</b> slug는 한글을 허용해 서브도메인이 될 수 없다.
 * handle이 없는 Academy는 LMS 주소가 성립하지 않는다(= 404).
 *
 * <p><b>이 클래스의 화면은 owner 전용이 아니다.</b> 앞 2개는 비로그인 포함 전체 공개,
 * 뒤 4개는 로그인만 하면 학생도 접근한다(인증 강제는 {@code SecurityConfig}). 운영 화면 2개는
 * 별도 클래스 {@link AcademyLmsManagePageController}에 격리돼 있다 — 이 클래스에 owner 화면을
 * 추가하지 말 것.
 *
 * <p><b>데이터는 전부 MOCK이다.</b> 서버는 학원 식별과 인증 상태만 내려주고, 강좌·영상·장바구니는
 * 정적 JS({@code /js/lms_mock.js} → {@code /js/lms_data.js})가 담당한다. 그래서 {@code courseId}는
 * 존재 검증 없이 모델로 통과시킨다 — 2단계에서 LessonProduct에 연결할 때 검증이 붙는다.
 */
@Controller
@RequiredArgsConstructor
public class AcademyLmsPageController {

  private final AcademyService academyService;
  private final AcademyLmsShellModel shellModel;

  @GetMapping("/lms/{handle}")
  public String courses(@PathVariable String handle, Model model) {
    populate(model, handle);
    return "lms/lms_courses";
  }

  @GetMapping("/lms/{handle}/courses/{courseId}")
  public String courseDetail(
      @PathVariable String handle, @PathVariable Long courseId, Model model) {
    populate(model, handle);
    model.addAttribute("courseId", courseId);
    return "lms/lms_course_detail";
  }

  @GetMapping("/lms/{handle}/cart")
  public String cart(@PathVariable String handle, Model model) {
    populate(model, handle);
    return "lms/lms_cart";
  }

  @GetMapping("/lms/{handle}/checkout")
  public String checkout(@PathVariable String handle, Model model) {
    populate(model, handle);
    return "lms/lms_checkout";
  }

  @GetMapping("/lms/{handle}/my")
  public String my(@PathVariable String handle, Model model) {
    populate(model, handle);
    return "lms/lms_my";
  }

  @GetMapping("/lms/{handle}/lecture/{courseId}")
  public String lecture(@PathVariable String handle, @PathVariable Long courseId, Model model) {
    populate(model, handle);
    model.addAttribute("courseId", courseId);
    return "lms/lms_lecture";
  }

  /**
   * 공개 조회로 학원을 확인한다. 미발행/숨김 학원의 LMS가 공개로 새지 않도록
   * {@code getPublishedEntityByHandle}을 쓴다(owner 화면과 조회 메서드가 다른 이유).
   */
  private void populate(Model model, String handle) {
    Academy academy = academyService.getPublishedEntityByHandle(handle);
    shellModel.populate(model, academy, handle);
  }
}
