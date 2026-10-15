package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsShellModel;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 학원 LMS "내신 LIVE 관리" 운영자 화면(작업지시서 §9·§33).
 *
 * <p><b>불변식:</b> 유일한 handler 가 {@link #requireOwner} 로 시작한다
 * ({@code AcademyLmsManagePageController} 규칙). 2중 방어 — {@code SecurityConfig} 의
 * {@code /lms/*&#47;midterm-live → hasRole("TUTOR")} 가 익명·STUDENT·ADMIN 을 필터체인에서 끊고,
 * 남은 "다른 학원 TUTOR" 를 {@code requireOwnedAcademyByHandle} 가 막는다(ADMIN 우회 없음).
 */
@Controller
@RequiredArgsConstructor
public class MidtermLivePageController {

  private final AcademyService academyService;
  private final AcademyLmsShellModel shellModel;

  @GetMapping("/lms/{handle}/midterm-live")
  public String midtermLive(@PathVariable String handle, Model model) {
    requireOwner(model, handle);
    return "lms/lms_midterm_live";
  }

  private void requireOwner(Model model, String handle) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    shellModel.populate(model, academy, handle);
  }
}
