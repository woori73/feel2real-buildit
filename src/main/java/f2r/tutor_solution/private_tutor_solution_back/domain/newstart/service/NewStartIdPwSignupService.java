package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterClaimRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 자체 ID/PW 가입(2026-10-01 긴급: 네이버 OAuth 승인 대기 중 임시 경로). 로그인은 기존 {@code /api/auth/login} 을 그대로 쓴다.
 *
 * <ul>
 *   <li>ID = 사용자의 네이버 ID(소문자 정규화), PW = 사용자가 직접 설정(기존 {@link PasswordEncoder} 로 저장), 카페 닉네임 입력.</li>
 *   <li>누구나 가입 가능. 기존 감사회원 명단과 <b>모두</b> 일치할 때만 감사회원, 아니면 일반회원(권한 부여 없음):
 *       ① 카페 닉네임 정확 일치 ② 후보 정확히 1명 ③ 그 명단이 아직 미연결 ④ 입력한 네이버 ID 가 명단 접두부로 시작.
 *       애매하면(후보 0/2+, 접두부 불일치, 이미 연결됨) 일반회원이며 어느 조건이 틀렸는지 알려주지 않는다.</li>
 *   <li>감사회원 권한은 기존 {@code THANKS_MEMBER} grant 그대로이며, 운영자는 기존 ADMIN grant 화면으로 나중에 일반→감사회원 변경할 수 있다.</li>
 *   <li>⚠ 한계: 이 경로는 네이버 계정 소유를 확인하지 못한다(OAuth 아님). 카페에 공개된 닉네임·ID 앞부분을 아는 사람이 가장하면
 *       감사회원으로 가입될 수 있다 — 네이버 OAuth 승인 후 OAuth 인증(#463)으로 대체/보강해야 한다.</li>
 * </ul>
 */
@Service
public class NewStartIdPwSignupService {

  static final String GRANTED_BY = "thanks-idpw";
  static final int MIN_PASSWORD = 8;
  private static final Pattern NAVER_ID = Pattern.compile("^[a-z0-9_-]{3,30}$");

  public enum MemberType { THANKS, GENERAL }

  public record Result(boolean ok, String errorMessage, Long userId, String loginId, String role, MemberType memberType) {
    static Result fail(String message) { return new Result(false, message, null, null, null, null); }
  }

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final NewStartThanksRosterRepository rosterRepository;
  private final NewStartThanksRosterClaimRepository claimRepository;
  private final NewStartGrantService grantService;

  public NewStartIdPwSignupService(UserRepository userRepository, PasswordEncoder passwordEncoder,
      NewStartThanksRosterRepository rosterRepository, NewStartThanksRosterClaimRepository claimRepository,
      NewStartGrantService grantService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.rosterRepository = rosterRepository;
    this.claimRepository = claimRepository;
    this.grantService = grantService;
  }

  @Transactional
  public Result signup(String loginIdInput, String password, String cafeNicknameInput) {
    String loginId = loginIdInput == null ? "" : loginIdInput.strip().toLowerCase(Locale.ROOT);
    if (!NAVER_ID.matcher(loginId).matches()) {
      return Result.fail("네이버 ID를 영문 소문자·숫자·_·- 3~30자로 입력해 주세요.");
    }
    if (password == null || password.length() < MIN_PASSWORD || password.length() > 100) {
      return Result.fail("비밀번호는 " + MIN_PASSWORD + "자 이상으로 직접 정해 주세요.");
    }
    String nickname = normalizeNickname(cafeNicknameInput);
    if (nickname.length() < 2 || nickname.length() > 50) {
      return Result.fail("카페 닉네임을 정확히 입력해 주세요.");
    }
    if (f2r.tutor_solution.private_tutor_solution_back.domain.user.service.ReservedLoginIds.isReserved(loginId)) {
      return Result.fail("사용할 수 없는 아이디입니다.");
    }
    if (userRepository.existsByLoginId(loginId)) {
      return Result.fail("이미 가입된 아이디입니다. 로그인해 주세요.");
    }

    User user;
    try {
      AuditorContextHolder.set(loginId);
      user = userRepository.saveAndFlush(User.builder()
          .loginId(loginId)
          .password(passwordEncoder.encode(password))
          .name(nickname.length() > 30 ? nickname.substring(0, 30) : nickname)
          .nickname(nickname)
          .role(Role.STUDENT)   // 별도 일반회원 role 이 없다 — 기존 NewStart 가입(OAuth)과 동일
          .build());
    } finally {
      AuditorContextHolder.clear();
    }

    MemberType type = MemberType.GENERAL;
    NewStartThanksRoster roster = uniqueMatch(loginId, nickname);
    if (roster != null && claimRepository.claim(roster.getRosterId(), user.getUserId()) == 1) {
      if (grantService.grant(user.getUserId(), NewStartCapability.THANKS_MEMBER, GRANTED_BY).ok()) {
        type = MemberType.THANKS;
      }
    }
    return new Result(true, null, user.getUserId(), user.getLoginId(), user.getRole().name(), type);
  }

  /** 닉네임 정확 일치 + 후보 1명 + 미연결 + ID 접두부 일치일 때만 명단 1건을 돌려준다(아니면 null → 일반회원). */
  NewStartThanksRoster uniqueMatch(String loginId, String nickname) {
    List<NewStartThanksRoster> candidates = rosterRepository.findByNickname(nickname).stream()
        .filter(r -> r.getNickname() != null && normalizeNickname(r.getNickname()).equalsIgnoreCase(nickname))
        .toList();
    if (candidates.size() != 1) return null;
    NewStartThanksRoster r = candidates.get(0);
    if (r.getLinkedUserId() != null) return null;
    String prefix = r.getNaverIdPrefix() == null ? "" : r.getNaverIdPrefix().strip().toLowerCase(Locale.ROOT);
    if (prefix.isEmpty() || !loginId.startsWith(prefix)) return null;
    return r;
  }

  static String normalizeNickname(String raw) {
    if (raw == null) return "";
    return Normalizer.normalize(raw, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
  }
}
