package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartPasswordResetResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.entity.NewStartAdminPasswordReset;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.repository.NewStartAdminPasswordResetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADMIN이 NewStart 회원을 1회용 임시 비밀번호로 초기화한다.
 *
 * <p>세 겹의 제한: ① {@code /api/admin/**}는 SecurityConfig가 ROLE_ADMIN으로 차단(비로그인 401,
 * 일반·감사회원 403), ② 이 서비스에서 실행 principal의 role을 다시 확인(직접 호출 방어),
 * ③ 대상은 {@code Role.STUDENT} 계정으로 제한(운영 ADMIN 계정은 이 경로로 초기화하지 않는다).
 *
 * <p>임시 비밀번호는 생성되어 응답에 한 번만 실린다. 저장되는 것은 BCrypt hash뿐이고, 원문을
 * 되돌려 주는 조회 경로는 존재하지 않는다. 로그에는 대상/실행자/시각만 남긴다 — 값 자체는 어디에도
 * 남기지 않는다.
 */
@Slf4j
@Service
public class NewStartAdminPasswordResetService {

  private final UserRepository userRepository;
  private final NewStartAdminPasswordResetRepository resetRepository;
  private final NewStartTempPasswordGenerator tempPasswordGenerator;
  private final PasswordEncoder passwordEncoder;

  public NewStartAdminPasswordResetService(UserRepository userRepository,
                                           NewStartAdminPasswordResetRepository resetRepository,
                                           NewStartTempPasswordGenerator tempPasswordGenerator,
                                           PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.resetRepository = resetRepository;
    this.tempPasswordGenerator = tempPasswordGenerator;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AdminNewStartPasswordResetResponse reset(Long targetUserId, AuthenticatedPrincipal principal) {
    requireAdmin(principal);
    User target = userRepository.findById(targetUserId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    if (target.getRole() != Role.STUDENT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NewStart 회원 계정만 초기화할 수 있습니다.");
    }

    String temporaryPassword = tempPasswordGenerator.generate();
    target.updatePassword(passwordEncoder.encode(temporaryPassword));
    target.markPasswordResetRequired();
    userRepository.save(target);

    NewStartAdminPasswordReset saved = resetRepository.save(NewStartAdminPasswordReset.of(
        target.getUserId(), target.getLoginId(), principal.userId(), principal.loginId()));

    // 임시 비밀번호 값은 로그에 남기지 않는다(대상·실행자·시각만).
    log.info("NEWSTART_ADMIN_PASSWORD_RESET targetUserId={} adminLoginId={}",
        target.getUserId(), principal.loginId());

    return new AdminNewStartPasswordResetResponse(
        target.getUserId(),
        target.getLoginId(),
        target.getNickname(),
        temporaryPassword,
        saved.getCreatedAt());
  }

  /**
   * SecurityConfig가 이미 {@code /api/admin/**}를 ROLE_ADMIN으로 막지만, 이 메서드는 서비스를
   * 직접 호출하는 경로(테스트·내부 코드)에서도 같은 판정이 서도록 한다.
   */
  private void requireAdmin(AuthenticatedPrincipal principal) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new AccessDeniedException("운영자만 회원 비밀번호를 초기화할 수 있습니다.");
    }
  }
}
