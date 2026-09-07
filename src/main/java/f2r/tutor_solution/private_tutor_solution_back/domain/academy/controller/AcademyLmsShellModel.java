package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.Optional;

/**
 * 학원 LMS 8개 화면이 공유하는 shell 모델({@code fragments/_lms_shell}) 주입기.
 *
 * <p>공개 화면 컨트롤러와 owner 화면 컨트롤러가 나뉘어 있어도 헤더·사이드바가 동일하게 렌더돼야
 * 하므로, 두 컨트롤러가 같은 한 지점을 쓴다. 여기서 갈라지면 사이드바 노출 규칙이 화면마다
 * 달라진다.
 *
 * <p>주입 항목:
 * <ul>
 *   <li>{@code academyName} — 헤더 학원명(목업 하드코딩 대체)
 *   <li>{@code lmsBase} — {@code /lms/{handle}}. 템플릿이 링크를 만들 때 쓰는 base path
 *   <li>{@code lmsAuthenticated} — 영상 잠금 판정에 쓰는 서버 인증 상태
 *   <li>{@code lmsUserKey} — sessionStorage 키 네임스페이스용. 비인증이면 {@code anon}
 *   <li>{@code lmsIsOwner} — 관리 메뉴 <b>표시</b> 여부. 통제가 아니다(§AcademyLmsManagePageController)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AcademyLmsShellModel {

  private static final String ANONYMOUS_USER_KEY = "anon";

  private final AuthenticatedUserResolver authResolver;
  private final MidtermCampaignRepository midtermCampaignRepository;

  public void populate(Model model, Academy academy, String handle) {
    Optional<JwtTokenProvider.AuthenticatedPrincipal> principal = authResolver.currentPrincipal();

    model.addAttribute("academyName", academy.getName());
    model.addAttribute("academyHandle", handle);
    model.addAttribute("lmsBase", "/lms/" + handle);
    model.addAttribute("lmsAuthenticated", principal.isPresent());
    model.addAttribute("lmsUserKey", userKey(principal));
    model.addAttribute("lmsIsOwner", isOwner(principal, academy));
    // "내신 LIVE 관리" 사이드바 항목은 캠페인 행이 있는 학원(에듀홀릭)에만 노출한다(§40).
    model.addAttribute("lmsMidtermEnabled",
        midtermCampaignRepository.existsByAcademy_AcademyId(academy.getAcademyId()));
  }

  /**
   * sessionStorage 키에 섞을 사용자 식별자. sessionStorage는 origin+tab 단위라 사용자 단위가
   * 아니다 — 같은 탭에서 A 로그아웃 → B 로그인 시 B가 A의 장바구니를 보게 되므로 키를 가른다.
   */
  private String userKey(Optional<JwtTokenProvider.AuthenticatedPrincipal> principal) {
    return principal.map(p -> "u" + p.userId()).orElse(ANONYMOUS_USER_KEY);
  }

  /**
   * "이 학원의 소유자인가". {@code role == TUTOR}가 아니라 소유권으로 판정하는 이유는, role만 보면
   * <b>다른 학원 강사</b>에게도 관리 메뉴가 보이고 누르면 403이 나기 때문이다.
   */
  private boolean isOwner(
      Optional<JwtTokenProvider.AuthenticatedPrincipal> principal, Academy academy) {
    return principal
        .filter(p -> Role.TUTOR.name().equals(p.role()))
        .filter(p -> academy.getTutor() != null)
        .map(p -> academy.getTutor().getUserId().equals(p.userId()))
        .orElse(false);
  }
}
