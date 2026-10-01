package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartThanksProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksVerification;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksVerificationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 감사회원 최초 1회 <b>셀프 인증</b> — 운영자 승인 없이 roster ↔ OAuth userId 를 1:1 로 연결하고 THANKS_MEMBER 를 부여한다.
 *
 * <h3>자동 인증 조건 (전부 만족해야 한다 — 어느 하나만으로는 인증하지 않는다)</h3>
 * <ol>
 *   <li>회원이 입력한 카페 닉네임이 503명 roster 와 <b>정확히 일치</b>하고 후보가 <b>정확히 1명</b></li>
 *   <li>네이버 OAuth 이메일이 {@code @naver.com} 이고 로컬파트(= 네이버 로그인 ID)가 그 roster 의 {@code naver_id_prefix} 로 시작
 *       — 접두부는 회원에게 보여주지 않는다. 접두부 길이(2자 포함)와 무관하게 503명 전원에게 같은 로직을 적용한다.</li>
 *   <li>해당 roster 가 다른 계정과 연결되지 않았고, 현재 OAuth 계정도 다른 roster 와 연결되지 않음</li>
 * </ol>
 * 위를 못 지키는 경우 중 "안전하게 자동 인증할 수 없는" 상황만 예외(ADMIN 예외함 + 운영자 이메일 알림)로 보내고, 단순 불일치는
 * 실패로 기록하며 반복되면 예외로 승격한다. 정상 인증은 운영자에게 알리지 않는다.
 *
 * <h3>ADMIN</h3>
 * ADMIN 은 이 절차를 거치지 않는다(상태 {@code ADMIN}, 어떤 기록도 만들지 않음). 감사회원 전용 기능 판정은
 * {@link NewStartEntitlementService} 단일 지점에서 ADMIN 을 항상 통과시킨다.
 *
 * <p>roster 원본(닉네임·접두부·등급 등)은 수정하지 않고 연결 컬럼만 쓴다. 1:1 은 V1017 UNIQUE 가 DB 에서도 강제한다.
 */
@Service
public class NewStartThanksVerificationService {

  static final int MIN_NICKNAME_CHARS = 2;
  static final int MAX_NICKNAME_CHARS = 50;
  static final String GRANTED_BY_SELF = "thanks-self";
  private static final String NAVER_DOMAIN = "naver.com";

  private final UserRepository userRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final NewStartThanksRosterRepository rosterRepository;
  private final NewStartThanksVerificationRepository verificationRepository;
  private final NewStartMemberGrantRepository grantRepository;
  private final NewStartGrantService grantService;
  private final ThanksExceptionNotifier notifier;
  private final NewStartThanksProperties props;

  public NewStartThanksVerificationService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
      NewStartThanksRosterRepository rosterRepository, NewStartThanksVerificationRepository verificationRepository,
      NewStartMemberGrantRepository grantRepository, NewStartGrantService grantService, ThanksExceptionNotifier notifier,
      NewStartThanksProperties props) {
    this.userRepository = userRepository;
    this.socialAccountRepository = socialAccountRepository;
    this.rosterRepository = rosterRepository;
    this.verificationRepository = verificationRepository;
    this.grantRepository = grantRepository;
    this.grantService = grantService;
    this.notifier = notifier;
    this.props = props;
  }

  // ───────────── types ─────────────

  /** 회원 화면용 결과. state: NONE / APPROVED / FAILED / EXCEPTION / ADMIN / ERROR. */
  public record Outcome(String state, String message) {
    static Outcome of(String state, String message) { return new Outcome(state, message); }
  }

  public record Result<T>(boolean ok, String errorMessage, T value) {
    public static <T> Result<T> success(T value) { return new Result<>(true, null, value); }
    public static <T> Result<T> failure(String message) { return new Result<>(false, message, null); }
  }

  public record RosterOption(Long rosterId, String nickname, boolean linked) {}

  /** 운영자 예외함 한 줄. 이메일 주소·네이버 ID·접두부·OAuth 식별값은 담지 않는다. */
  public record ExceptionView(Long verificationId, String reasonCode, String reasonLabel, String nicknameInput,
      Long userId, String userNickname, Long rosterId, String rosterNickname, List<RosterOption> candidates,
      String emailStatus, String status, LocalDateTime occurredAt) {}

  static final String MSG_ADMIN = "운영자 계정은 감사회원 인증 없이 모든 기능을 사용할 수 있습니다.";
  static final String MSG_APPROVED = "감사회원으로 확인되었습니다.";
  static final String MSG_EXCEPTION = "자동으로 확인하지 못해 운영자에게 전달했습니다. 확인되면 안내 없이 바로 이용할 수 있습니다.";
  static final String MSG_FAILED = "입력하신 정보로 감사회원을 확인하지 못했습니다. 카페에서 쓰는 닉네임을 정확히 입력해 주세요.";

  // ───────────── member side ─────────────

  @Transactional(readOnly = true)
  public Outcome status(Long userId, String role) {
    if ("ADMIN".equals(role)) return Outcome.of("ADMIN", MSG_ADMIN);
    if (userId == null) return Outcome.of("NONE", null);
    if (grantRepository.existsByUserIdAndCapability(userId, NewStartCapability.THANKS_MEMBER)) {
      return Outcome.of("APPROVED", MSG_APPROVED);
    }
    if (verificationRepository.findFirstByUserIdAndOutcomeAndStatus(userId, NewStartThanksVerification.EXCEPTION,
        NewStartThanksVerification.OPEN).isPresent()) {
      return Outcome.of("EXCEPTION", MSG_EXCEPTION);
    }
    return Outcome.of("NONE", null);
  }

  /** 카페 닉네임 1회 입력 → 자동 인증. 안전하게 끝낼 수 없으면 예외로 적재하고 운영자에게 알린다. */
  @Transactional
  public Outcome verify(Long userId, String role, String nicknameInput) {
    if ("ADMIN".equals(role)) return Outcome.of("ADMIN", MSG_ADMIN);   // 어떤 기록도 만들지 않는다
    if (userId == null) return Outcome.of("ERROR", "로그인이 필요합니다.");
    User user = userRepository.findById(userId).orElse(null);
    if (user == null) return Outcome.of("ERROR", "회원을 찾을 수 없습니다.");
    SocialAccount social = socialAccountRepository.findFirstByUserIdAndProvider(userId, SocialProvider.NAVER).orElse(null);
    if (social == null) return Outcome.of("ERROR", "네이버 로그인 후 이용할 수 있습니다.");

    if (grantRepository.existsByUserIdAndCapability(userId, NewStartCapability.THANKS_MEMBER)) {
      return Outcome.of("APPROVED", MSG_APPROVED);
    }
    if (verificationRepository.findFirstByUserIdAndOutcomeAndStatus(userId, NewStartThanksVerification.EXCEPTION,
        NewStartThanksVerification.OPEN).isPresent()) {
      return Outcome.of("EXCEPTION", MSG_EXCEPTION);   // 이미 운영자에게 전달됨 — 같은 회원의 반복 시도를 쌓지 않는다
    }

    String nickname = normalizeNickname(nicknameInput);
    if (nickname.length() < MIN_NICKNAME_CHARS || nickname.length() > MAX_NICKNAME_CHARS) {
      return Outcome.of("ERROR", "카페 닉네임을 정확히 입력해 주세요.");   // 검증 오류는 실패 횟수에 넣지 않는다
    }

    List<NewStartThanksRoster> candidates = rosterRepository.findByNickname(nickname).stream()
        .filter(r -> r.getNickname() != null && r.getNickname().strip().equalsIgnoreCase(nickname))
        .toList();

    if (candidates.isEmpty()) {
      return failed(userId, null, nickname, social);
    }
    if (candidates.size() > 1) {
      return exception(userId, null, nickname, social, ThanksExceptionReason.AMBIGUOUS_CANDIDATES);
    }
    NewStartThanksRoster roster = candidates.get(0);

    if (roster.isLinked()) {
      if (userId.equals(roster.getLinkedUserId())) {   // 이미 내 명단과 연결됨 — 권한만 보장(idempotent)
        grantService.grant(userId, NewStartCapability.THANKS_MEMBER, GRANTED_BY_SELF);
        return Outcome.of("APPROVED", MSG_APPROVED);
      }
      return exception(userId, roster.getRosterId(), nickname, social, ThanksExceptionReason.ROSTER_ALREADY_LINKED);
    }
    Optional<NewStartThanksRoster> mine = rosterRepository.findByLinkedUserId(userId);
    if (mine.isPresent()) {
      return exception(userId, roster.getRosterId(), nickname, social, ThanksExceptionReason.DUPLICATE_OAUTH_LINK);
    }

    String email = social.getProviderEmail();
    if (email == null || email.isBlank()) {
      return exception(userId, roster.getRosterId(), nickname, social, ThanksExceptionReason.EMAIL_MISSING);
    }
    String naverId = naverIdFromEmail(email);
    if (naverId == null) {
      return exception(userId, roster.getRosterId(), nickname, social, ThanksExceptionReason.EMAIL_NOT_NAVER);
    }
    String prefix = roster.getNaverIdPrefix() == null ? "" : roster.getNaverIdPrefix().strip().toLowerCase(Locale.ROOT);
    // 접두부 길이 제한은 없다(2자 포함 전원 동일). 단, 접두부 데이터가 비어 있으면 startsWith("")가 항상 참이라 닉네임 단독 인증이 되므로 막는다.
    if (prefix.isEmpty() || !naverId.startsWith(prefix)) {
      return failed(userId, roster.getRosterId(), nickname, social);   // 닉네임만 맞고 네이버 ID가 다르다 — 인증하지 않는다
    }

    // 모든 조건 충족 → 운영자 승인 없이 1:1 연결 + THANKS_MEMBER 부여(한 트랜잭션, DB UNIQUE가 마지막 방어선).
    NewStartGrantService.GrantResult granted = grantService.grant(userId, NewStartCapability.THANKS_MEMBER, GRANTED_BY_SELF);
    if (!granted.ok()) {
      return Outcome.of("ERROR", "잠시 후 다시 시도해 주세요.");
    }
    roster.link(userId, social.getProviderUserId());
    rosterRepository.save(roster);
    verificationRepository.save(NewStartThanksVerification.builder().userId(userId).rosterId(roster.getRosterId())
        .nicknameInput(nickname).providerUserId(social.getProviderUserId())
        .outcome(NewStartThanksVerification.AUTO_APPROVED).build());
    return Outcome.of("APPROVED", MSG_APPROVED);
  }

  /** 동시 인증 경합으로 DB UNIQUE 가 걸린 경우(controller 가 호출) — 별도 트랜잭션으로 예외를 남기고 운영자에게 알린다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Outcome recordConflict(Long userId, String nicknameInput) {
    SocialAccount social = socialAccountRepository.findFirstByUserIdAndProvider(userId, SocialProvider.NAVER).orElse(null);
    if (social == null) return Outcome.of("ERROR", "잠시 후 다시 시도해 주세요.");
    return exception(userId, null, normalizeNickname(nicknameInput), social, ThanksExceptionReason.CONCURRENT_CONFLICT);
  }

  // ───────────── admin side (예외 건만) ─────────────

  @Transactional(readOnly = true)
  public List<ExceptionView> adminListExceptions(String status) {
    String s = status == null || status.isBlank() ? NewStartThanksVerification.OPEN : status.strip().toUpperCase(Locale.ROOT);
    return verificationRepository.findByOutcomeAndStatusOrderByVerificationIdAsc(NewStartThanksVerification.EXCEPTION, s)
        .stream().map(this::toView).toList();
  }

  /**
   * 운영자가 예외 1건을 연결 승인한다. 자동 인증과 같은 불변식(명단 미연결·계정 미연결·네이버 subject 동일)을 다시 확인한다.
   * {@code rosterIdOverride}는 후보가 여러 명이거나 명단이 특정되지 않은 예외에서 운영자가 대상을 고를 때 쓴다.
   */
  @Transactional
  public Result<ExceptionView> adminResolve(Long verificationId, Long rosterIdOverride, String operator) {
    NewStartThanksVerification v = verificationRepository.findById(verificationId == null ? -1L : verificationId).orElse(null);
    if (v == null) return Result.failure("예외를 찾을 수 없습니다.");
    if (!v.isOpenException()) return Result.failure("이미 처리된 예외입니다.");
    Long rosterId = rosterIdOverride != null ? rosterIdOverride : v.getRosterId();
    if (rosterId == null) return Result.failure("연결할 명단을 선택해 주세요.");
    NewStartThanksRoster roster = rosterRepository.findById(rosterId).orElse(null);
    if (roster == null) return Result.failure("명단을 찾을 수 없습니다.");
    if (roster.isLinked()) return Result.failure("이미 다른 계정과 연결된 명단입니다.");
    if (rosterRepository.findByLinkedUserId(v.getUserId()).isPresent()) {
      return Result.failure("이 계정은 이미 다른 명단과 연결되어 있습니다.");
    }
    if (userRepository.findById(v.getUserId()).isEmpty()) return Result.failure("회원을 찾을 수 없습니다.");
    SocialAccount social = socialAccountRepository.findFirstByUserIdAndProvider(v.getUserId(), SocialProvider.NAVER).orElse(null);
    if (social == null || !v.getProviderUserId().equals(social.getProviderUserId())) {
      return Result.failure("네이버 계정 연결이 바뀌었습니다. 회원이 다시 인증하도록 안내해 주세요.");
    }
    String marker = marker(operator);
    NewStartGrantService.GrantResult granted =
        grantService.grant(v.getUserId(), NewStartCapability.THANKS_MEMBER, "thanks-exception:" + marker);
    if (!granted.ok()) return Result.failure(granted.errorMessage());
    roster.link(v.getUserId(), social.getProviderUserId());
    rosterRepository.save(roster);
    v.resolve(marker, "명단 연결: " + roster.getRosterId());
    verificationRepository.save(v);
    return Result.success(toView(v));
  }

  @Transactional
  public Result<ExceptionView> adminDismiss(Long verificationId, String note, String operator) {
    NewStartThanksVerification v = verificationRepository.findById(verificationId == null ? -1L : verificationId).orElse(null);
    if (v == null) return Result.failure("예외를 찾을 수 없습니다.");
    if (!v.isOpenException()) return Result.failure("이미 처리된 예외입니다.");
    v.dismiss(marker(operator), note);
    verificationRepository.save(v);
    return Result.success(toView(v));
  }

  // ───────────── internals ─────────────

  private Outcome failed(Long userId, Long rosterId, String nickname, SocialAccount social) {
    verificationRepository.save(NewStartThanksVerification.builder().userId(userId).rosterId(rosterId)
        .nicknameInput(nickname).providerUserId(social.getProviderUserId())
        .outcome(NewStartThanksVerification.FAILED).build());
    long recent = verificationRepository.countByUserIdAndOutcomeAndCreatedAtAfter(
        userId, NewStartThanksVerification.FAILED, LocalDateTime.now().minusHours(24));
    if (recent >= Math.max(1, props.getMaxFailuresPerDay())) {
      return exception(userId, rosterId, nickname, social, ThanksExceptionReason.REPEATED_FAILURE);
    }
    // 어느 항목이 틀렸는지 알려주지 않는다(닉네임/이메일 어느 쪽이 맞는지 추측 방지).
    return Outcome.of("FAILED", MSG_FAILED);
  }

  /** 예외 적재 + 커밋 후 운영자 이메일 알림. 같은 회원의 처리 대기 예외가 이미 있으면 새로 만들지도 알리지도 않는다. */
  private Outcome exception(Long userId, Long rosterId, String nickname, SocialAccount social, String reason) {
    if (verificationRepository.findFirstByUserIdAndOutcomeAndStatus(userId, NewStartThanksVerification.EXCEPTION,
        NewStartThanksVerification.OPEN).isPresent()) {
      return Outcome.of("EXCEPTION", MSG_EXCEPTION);
    }
    NewStartThanksVerification saved = verificationRepository.save(NewStartThanksVerification.builder()
        .userId(userId).rosterId(rosterId).nicknameInput(nickname).providerUserId(social.getProviderUserId())
        .outcome(NewStartThanksVerification.EXCEPTION).exceptionReason(reason).build());
    afterCommit(() -> notifier.notifyAsync(saved.getVerificationId()));
    return Outcome.of("EXCEPTION", MSG_EXCEPTION);
  }

  private static void afterCommit(Runnable task) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          task.run();
        }
      });
    } else {
      task.run();
    }
  }

  private ExceptionView toView(NewStartThanksVerification v) {
    User user = userRepository.findById(v.getUserId()).orElse(null);
    NewStartThanksRoster roster = v.getRosterId() == null ? null : rosterRepository.findById(v.getRosterId()).orElse(null);
    List<RosterOption> options = roster != null ? List.of()
        : rosterRepository.findByNickname(v.getNicknameInput()).stream()
            .filter(r -> r.getNickname() != null && r.getNickname().strip().equalsIgnoreCase(v.getNicknameInput()))
            .map(r -> new RosterOption(r.getRosterId(), r.getNickname(), r.isLinked())).toList();
    return new ExceptionView(v.getVerificationId(), v.getExceptionReason(), ThanksExceptionReason.label(v.getExceptionReason()),
        v.getNicknameInput(), v.getUserId(), user == null ? null : user.getNickname(),
        v.getRosterId(), roster == null ? null : roster.getNickname(), options, v.getEmailStatus(), v.getStatus(),
        v.getCreatedAt());
  }

  private static String marker(String operator) {
    String m = operator == null || operator.isBlank() ? "operator" : operator.strip();
    return m.length() > 40 ? m.substring(0, 40) : m;
  }

  /** NFC 정규화 + 앞뒤/연속 공백 정리. 대소문자는 비교 시 무시한다(roster 닉네임은 정확히 일치해야 한다). */
  static String normalizeNickname(String raw) {
    if (raw == null) return "";
    return Normalizer.normalize(raw, Normalizer.Form.NFC).replaceAll("\\s+", " ").strip();
  }

  /** {@code @naver.com} 이메일이면 로컬파트(= 네이버 로그인 ID, 소문자)를, 아니면 null. */
  static String naverIdFromEmail(String email) {
    if (email == null) return null;
    int at = email.indexOf('@');
    if (at <= 0) return null;
    String domain = email.substring(at + 1).strip().toLowerCase(Locale.ROOT);
    if (!NAVER_DOMAIN.equals(domain)) return null;
    String local = email.substring(0, at).strip().toLowerCase(Locale.ROOT);
    return local.isEmpty() ? null : local;
  }
}
