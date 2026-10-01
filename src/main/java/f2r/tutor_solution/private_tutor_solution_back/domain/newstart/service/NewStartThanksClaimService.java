package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksClaim;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksClaimRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사회원 최초 1회 본인확인 — 현재 네이버 OAuth 회원과 사전자격명부(roster)를 <b>운영자 승인으로</b> 1:1 연결하고
 * {@code THANKS_MEMBER}를 부여한다.
 *
 * <h3>왜 자동 부여가 아닌가</h3>
 * 네이버 OAuth는 실제 네이버 로그인 ID를 주지 않는다(앱별 고유 {@code response.id}만). roster에는 카페 닉네임과
 * 마스킹된 ID 접두부뿐이고, 둘 다 비밀이 아니다. 접두부(예: {@code to})나 닉네임만으로 부여하면 같은 접두부·공개 닉네임을 가진
 * 일반회원이 오인증될 수 있다. 그래서 회원은 "요청"만 만들 수 있고(PENDING), 운영자가 카페 쪽지/댓글의 1회용 인증코드와
 * 보낸 사람의 카페 계정을 확인한 뒤 승인할 때만 연결·부여가 일어난다. 승인 이후에는 별도 입력 없이 네이버 로그인만으로
 * 감사회원 기능이 열린다(판정은 {@link NewStartEntitlementService}가 grant row로 한다).
 *
 * <h3>안전장치</h3>
 * <ul>
 *   <li>네이버 계정이 연결된 회원만 요청 가능, 이미 감사회원이면 요청 불가.</li>
 *   <li>roster 1행 ↔ 회원 1명 1:1 — 이미 연결된 행은 후보로도 나오지 않고, V1017 UNIQUE가 DB에서도 강제한다.</li>
 *   <li>승인 시 요청 시점 네이버 subject와 현재 social_accounts 값이 같아야 한다.</li>
 *   <li>같은 roster 행에 걸린 다른 PENDING 요청은 한 건 승인되면 자동 반려된다.</li>
 *   <li>최근 24시간 반려 {@value #MAX_REJECTED_PER_DAY}건 이상이면 신규 요청 차단(전수 대입 방지).</li>
 *   <li>roster 원본(닉네임·접두부·등급 등)은 수정하지 않는다 — 연결 컬럼만 쓴다.</li>
 * </ul>
 */
@Service
public class NewStartThanksClaimService {

  static final int MAX_REJECTED_PER_DAY = 3;
  static final int MAX_CANDIDATES = 5;
  static final int MIN_NICKNAME_CHARS = 2;
  static final int CODE_LENGTH = 8;
  /** 혼동 문자(0/O, 1/I/L) 제외. */
  private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final NewStartThanksRosterRepository rosterRepository;
  private final NewStartThanksClaimRepository claimRepository;
  private final NewStartMemberGrantRepository grantRepository;
  private final NewStartGrantService grantService;

  public NewStartThanksClaimService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
      NewStartThanksRosterRepository rosterRepository, NewStartThanksClaimRepository claimRepository,
      NewStartMemberGrantRepository grantRepository, NewStartGrantService grantService) {
    this.userRepository = userRepository;
    this.socialAccountRepository = socialAccountRepository;
    this.rosterRepository = rosterRepository;
    this.claimRepository = claimRepository;
    this.grantRepository = grantRepository;
    this.grantService = grantService;
  }

  // ───────────── result / view types ─────────────

  public record Candidate(Long rosterId, String nickname, String maskedId) {}

  public record Result<T>(boolean ok, String errorMessage, T value) {
    public static <T> Result<T> success(T value) { return new Result<>(true, null, value); }
    public static <T> Result<T> failure(String message) { return new Result<>(false, message, null); }
  }

  /** 회원 본인에게 보이는 상태. 코드는 PENDING일 때만 내려간다. */
  public record MyStatus(String state, Long claimId, String rosterNickname, String maskedId, String verifyCode,
      String message) {}

  /** 운영자 화면용(PII는 운영자에게만): 승인 판단 근거를 한 줄로 모아 보여준다. */
  public record AdminClaimView(Long claimId, Long userId, String loginId, String userName, String userNickname,
      Long rosterId, String rosterNickname, String rosterMaskedId, String verifyCode, boolean emailPrefixMatch,
      boolean nicknameEqualsRoster, String status, LocalDateTime requestedAt) {}

  // ───────────── member side ─────────────

  /** 닉네임 정확 일치 후보(미연결 roster만, 최대 {@value #MAX_CANDIDATES}건). 목록 전체는 절대 내려주지 않는다. */
  @Transactional(readOnly = true)
  public Result<List<Candidate>> candidates(Long userId, String nicknameInput) {
    Result<User> gate = requireEligibleNaverMember(userId);
    if (!gate.ok()) return Result.failure(gate.errorMessage());
    String nickname = nicknameInput == null ? "" : nicknameInput.strip();
    if (nickname.length() < MIN_NICKNAME_CHARS) {
      return Result.failure("카페 닉네임을 정확히 입력해 주세요.");
    }
    List<Candidate> out = rosterRepository.findByNicknameAndMatchStatus(nickname, NewStartThanksRoster.MATCH_UNMATCHED)
        .stream()
        .filter(r -> !r.isLinked() && r.getNickname().strip().equalsIgnoreCase(nickname))
        .limit(MAX_CANDIDATES)
        .map(r -> new Candidate(r.getRosterId(), r.getNickname(), maskedId(r)))
        .toList();
    return Result.success(out);
  }

  @Transactional
  public Result<MyStatus> submit(Long userId, Long rosterId) {
    Result<User> gate = requireEligibleNaverMember(userId);
    if (!gate.ok()) return Result.failure(gate.errorMessage());
    User user = gate.value();
    if (rosterId == null) return Result.failure("본인 닉네임을 선택해 주세요.");
    SocialAccount social = socialAccountRepository.findFirstByUserIdAndProvider(userId, SocialProvider.NAVER).orElse(null);
    if (social == null) return Result.failure("네이버 로그인 후 이용할 수 있습니다.");

    Optional<NewStartThanksClaim> pending = claimRepository.findFirstByUserIdAndStatus(userId, NewStartThanksClaim.PENDING);
    if (pending.isPresent()) {
      if (rosterId.equals(pending.get().getRosterId())) {
        return Result.success(toMyStatus(pending.get()));   // 같은 요청은 idempotent
      }
      return Result.failure("이미 진행 중인 본인확인 요청이 있습니다. 취소 후 다시 신청해 주세요.");
    }
    if (claimRepository.countByUserIdAndStatusAndDecidedAtAfter(
        userId, NewStartThanksClaim.REJECTED, LocalDateTime.now().minusHours(24)) >= MAX_REJECTED_PER_DAY) {
      return Result.failure("본인확인 요청이 여러 번 반려되었습니다. 내일 다시 시도하거나 카페 쪽지로 운영자에게 문의해 주세요.");
    }
    NewStartThanksRoster roster = rosterRepository.findById(rosterId).orElse(null);
    if (roster == null || roster.isLinked()) {
      return Result.failure("선택할 수 없는 명단입니다. 닉네임을 다시 확인해 주세요.");
    }

    boolean emailMatch = emailPrefixMatches(user.getEmail(), roster.getNaverIdPrefix());
    NewStartThanksClaim claim = claimRepository.findByUserIdAndRosterId(userId, rosterId)
        .map(existing -> {
          existing.reopen(social.getProviderUserId(), newCode(), emailMatch);
          return existing;
        })
        .orElseGet(() -> NewStartThanksClaim.builder()
            .userId(userId).rosterId(rosterId).providerUserId(social.getProviderUserId())
            .verifyCode(newCode()).emailPrefixMatch(emailMatch).build());
    return Result.success(toMyStatus(claimRepository.save(claim)));
  }

  @Transactional
  public Result<MyStatus> cancel(Long userId) {
    Optional<NewStartThanksClaim> pending = claimRepository.findFirstByUserIdAndStatus(userId, NewStartThanksClaim.PENDING);
    if (pending.isEmpty()) return Result.failure("취소할 요청이 없습니다.");
    pending.get().cancel();
    claimRepository.save(pending.get());
    return Result.success(new MyStatus("NONE", null, null, null, null, "본인확인 요청을 취소했습니다."));
  }

  @Transactional(readOnly = true)
  public MyStatus status(Long userId) {
    if (userId != null && grantRepository.existsByUserIdAndCapability(userId, NewStartCapability.THANKS_MEMBER)) {
      return new MyStatus("APPROVED", null, null, null, null, "감사회원으로 확인되었습니다.");
    }
    List<NewStartThanksClaim> mine = userId == null ? List.of() : claimRepository.findByUserIdOrderByClaimIdDesc(userId);
    if (mine.isEmpty()) return new MyStatus("NONE", null, null, null, null, null);
    NewStartThanksClaim latest = mine.get(0);
    return toMyStatus(latest);
  }

  // ───────────── admin side ─────────────

  @Transactional(readOnly = true)
  public List<AdminClaimView> adminList(String status) {
    String s = status == null || status.isBlank() ? NewStartThanksClaim.PENDING : status.strip().toUpperCase(Locale.ROOT);
    return claimRepository.findByStatusOrderByClaimIdAsc(s).stream().map(this::toAdminView).toList();
  }

  /**
   * 유일한 연결·부여 경로. roster·회원·네이버 subject를 다시 확인하고, 연결과 THANKS_MEMBER 부여, 같은 roster의 다른 PENDING
   * 반려를 한 트랜잭션으로 처리한다. DB UNIQUE 위반은 호출 측(controller)이 409로 번역한다.
   */
  @Transactional
  public Result<AdminClaimView> approve(Long claimId, String operatorMarker) {
    NewStartThanksClaim claim = claimRepository.findById(claimId == null ? -1L : claimId).orElse(null);
    if (claim == null) return Result.failure("요청을 찾을 수 없습니다.");
    if (!claim.isPending()) return Result.failure("이미 처리된 요청입니다.");
    NewStartThanksRoster roster = rosterRepository.findById(claim.getRosterId()).orElse(null);
    if (roster == null) return Result.failure("명단을 찾을 수 없습니다.");
    if (roster.isLinked()) return Result.failure("이미 다른 회원과 연결된 명단입니다.");
    if (userRepository.findById(claim.getUserId()).isEmpty()) return Result.failure("회원을 찾을 수 없습니다.");
    SocialAccount social = socialAccountRepository.findFirstByUserIdAndProvider(claim.getUserId(), SocialProvider.NAVER).orElse(null);
    if (social == null || !claim.getProviderUserId().equals(social.getProviderUserId())) {
      return Result.failure("요청 이후 네이버 계정 연결이 바뀌었습니다. 다시 요청하도록 안내해 주세요.");
    }
    String marker = operatorMarker == null || operatorMarker.isBlank() ? "operator" : operatorMarker.strip();
    NewStartGrantService.GrantResult granted =
        grantService.grant(claim.getUserId(), NewStartCapability.THANKS_MEMBER, "thanks-claim:" + marker);
    if (!granted.ok()) return Result.failure(granted.errorMessage());

    roster.link(claim.getUserId(), social.getProviderUserId());
    rosterRepository.save(roster);
    claim.approve(marker);
    claimRepository.save(claim);
    for (NewStartThanksClaim other : claimRepository.findByRosterIdAndStatus(roster.getRosterId(), NewStartThanksClaim.PENDING)) {
      if (!other.getClaimId().equals(claim.getClaimId())) {
        other.reject(marker, "다른 회원 요청이 승인되었습니다.");
        claimRepository.save(other);
      }
    }
    return Result.success(toAdminView(claim));
  }

  @Transactional
  public Result<AdminClaimView> reject(Long claimId, String reason, String operatorMarker) {
    NewStartThanksClaim claim = claimRepository.findById(claimId == null ? -1L : claimId).orElse(null);
    if (claim == null) return Result.failure("요청을 찾을 수 없습니다.");
    if (!claim.isPending()) return Result.failure("이미 처리된 요청입니다.");
    String marker = operatorMarker == null || operatorMarker.isBlank() ? "operator" : operatorMarker.strip();
    claim.reject(marker, reason);
    claimRepository.save(claim);
    return Result.success(toAdminView(claim));
  }

  // ───────────── helpers ─────────────

  private Result<User> requireEligibleNaverMember(Long userId) {
    if (userId == null) return Result.failure("로그인이 필요합니다.");
    User user = userRepository.findById(userId).orElse(null);
    if (user == null) return Result.failure("회원을 찾을 수 없습니다.");
    if (socialAccountRepository.findFirstByUserIdAndProvider(userId, SocialProvider.NAVER).isEmpty()) {
      return Result.failure("네이버 로그인 후 이용할 수 있습니다.");
    }
    if (grantRepository.existsByUserIdAndCapability(userId, NewStartCapability.THANKS_MEMBER)) {
      return Result.failure("이미 감사회원으로 확인되었습니다.");
    }
    return Result.success(user);
  }

  private MyStatus toMyStatus(NewStartThanksClaim claim) {
    NewStartThanksRoster roster = rosterRepository.findById(claim.getRosterId()).orElse(null);
    String nick = roster == null ? null : roster.getNickname();
    String masked = roster == null ? null : maskedId(roster);
    return switch (claim.getStatus()) {
      case NewStartThanksClaim.PENDING -> new MyStatus("PENDING", claim.getClaimId(), nick, masked, claim.getVerifyCode(),
          "운영자 확인 대기 중입니다. 카페 쪽지(또는 안내 게시글 댓글)로 인증코드를 보내 주세요.");
      case NewStartThanksClaim.APPROVED -> new MyStatus("APPROVED", claim.getClaimId(), nick, masked, null,
          "감사회원으로 확인되었습니다.");
      case NewStartThanksClaim.REJECTED -> new MyStatus("REJECTED", claim.getClaimId(), nick, masked, null,
          "본인확인이 반려되었습니다. 닉네임을 확인한 뒤 다시 신청하거나 카페 쪽지로 문의해 주세요.");
      default -> new MyStatus("NONE", null, null, null, null, null);
    };
  }

  private AdminClaimView toAdminView(NewStartThanksClaim c) {
    User user = userRepository.findById(c.getUserId()).orElse(null);
    NewStartThanksRoster roster = rosterRepository.findById(c.getRosterId()).orElse(null);
    boolean nickEq = user != null && roster != null && user.getNickname() != null
        && user.getNickname().strip().equalsIgnoreCase(roster.getNickname().strip());
    return new AdminClaimView(c.getClaimId(), c.getUserId(), user == null ? null : user.getLoginId(),
        user == null ? null : user.getName(), user == null ? null : user.getNickname(), c.getRosterId(),
        roster == null ? null : roster.getNickname(), roster == null ? null : maskedId(roster), c.getVerifyCode(),
        c.isEmailPrefixMatch(), nickEq, c.getStatus(), c.getCreatedAt());
  }

  static String maskedId(NewStartThanksRoster roster) {
    String p = roster.getNaverIdPrefix();
    return p == null || p.isBlank() ? "(접두부 없음)" : p + "****";
  }

  /** 네이버가 검증한 이메일(@naver.com)의 로컬파트가 roster 접두부로 시작하는지. 보조 증거일 뿐 승인 근거가 아니다. */
  static boolean emailPrefixMatches(String email, String naverIdPrefix) {
    if (email == null || naverIdPrefix == null || naverIdPrefix.isBlank()) return false;
    int at = email.indexOf('@');
    if (at <= 0) return false;
    String domain = email.substring(at + 1).strip().toLowerCase(Locale.ROOT);
    if (!"naver.com".equals(domain)) return false;
    return email.substring(0, at).strip().toLowerCase(Locale.ROOT).startsWith(naverIdPrefix.strip().toLowerCase(Locale.ROOT));
  }

  static String newCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    return sb.toString();
  }
}
