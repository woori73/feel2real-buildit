package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.entity.NewStartAdminPasswordReset;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.repository.NewStartAdminPasswordResetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ADMIN 회원관리 화면의 읽기 쪽. NewStart 로그인 경로를 통해 들어온 {@code Role.STUDENT} 계정을
 * 로그인 ID / 카페 닉네임으로 검색하고 상세를 돌려준다.
 *
 * <p>돌려주지 않는 것: 비밀번호 원문·hash. {@code users.password}는 BCrypt hash라 원래도 원문이
 * 아니지만, 조회 API 자체를 만들지 않아 화면·로그·응답 어느 곳에도 나타나지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
public class NewStartAdminMemberQueryService {

  /** 검색 없이 목록을 열었을 때의 기본 페이지 크기. */
  static final int DEFAULT_SIZE = 20;
  static final int MAX_SIZE = 50;

  private final UserRepository userRepository;
  private final NewStartMemberGrantRepository grantRepository;
  private final NewStartThanksRosterRepository rosterRepository;
  private final NewStartAdminPasswordResetRepository resetRepository;

  public NewStartAdminMemberQueryService(UserRepository userRepository,
                                         NewStartMemberGrantRepository grantRepository,
                                         NewStartThanksRosterRepository rosterRepository,
                                         NewStartAdminPasswordResetRepository resetRepository) {
    this.userRepository = userRepository;
    this.grantRepository = grantRepository;
    this.rosterRepository = rosterRepository;
    this.resetRepository = resetRepository;
  }

  public AdminNewStartMemberPage search(String rawQuery, Integer rawPage, Integer rawSize) {
    String query = rawQuery == null ? "" : rawQuery.strip();
    int page = rawPage == null || rawPage < 0 ? 0 : rawPage;
    int size = rawSize == null || rawSize <= 0 ? DEFAULT_SIZE : Math.min(rawSize, MAX_SIZE);
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<User> found = query.isEmpty()
        ? userRepository.findByRole(Role.STUDENT, pageable)
        : userRepository.searchByRoleAndLoginIdOrNickname(Role.STUDENT, query, pageable);

    List<User> rows = found.getContent();
    Set<Long> thanksIds = rows.isEmpty() ? Set.of() : grantRepository
        .findByUserIdInAndCapability(rows.stream().map(User::getUserId).toList(), NewStartCapability.THANKS_MEMBER)
        .stream()
        .map(grant -> grant.getUserId())
        .collect(Collectors.toSet());

    return new AdminNewStartMemberPage(
        query,
        found.getNumber(),
        // 요청해 확정된 page/size를 그대로 돌려준다 — 상한에 잘린 값이 화면에 보인다.
        size,
        found.getTotalElements(),
        found.getTotalPages(),
        rows.stream().map(user -> toSummary(user, thanksIds.contains(user.getUserId()))).toList());
  }

  public AdminNewStartMemberDetail detail(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    if (user.getRole() != Role.STUDENT) {
      // NewStart 회원관리 화면의 대상은 회원 계정뿐이다. ADMIN/TUTOR 계정은 여기서 제외한다.
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NewStart 회원만 조회할 수 있습니다.");
    }
    boolean thanks = grantRepository.existsByUserIdAndCapability(userId, NewStartCapability.THANKS_MEMBER);
    List<NewStartAdminPasswordReset> history =
        resetRepository.findByTargetUserIdOrderByResetIdDesc(userId, PageRequest.of(0, 1));
    NewStartAdminPasswordReset last = history.isEmpty() ? null : history.get(0);
    return new AdminNewStartMemberDetail(
        user.getUserId(),
        user.getLoginId(),
        user.getNickname(),
        thanks ? AdminNewStartMemberSummary.THANKS : AdminNewStartMemberSummary.GENERAL,
        accountStatus(user),
        user.getCreatedAt(),
        user.getLastLoginAt(),
        user.isPasswordResetRequired(),
        last == null ? null : last.getCreatedAt(),
        last == null ? null : last.getAdminLoginId(),
        rosterRepository.findByLinkedUserId(userId).isPresent());
  }

  private AdminNewStartMemberSummary toSummary(User user, boolean thanks) {
    return new AdminNewStartMemberSummary(
        user.getUserId(),
        user.getLoginId(),
        user.getNickname(),
        thanks ? AdminNewStartMemberSummary.THANKS : AdminNewStartMemberSummary.GENERAL,
        accountStatus(user),
        user.getCreatedAt(),
        user.getLastLoginAt());
  }

  private String accountStatus(User user) {
    return user.isPasswordResetRequired()
        ? AdminNewStartMemberSummary.RESET_REQUIRED
        : AdminNewStartMemberSummary.ACTIVE;
  }
}
