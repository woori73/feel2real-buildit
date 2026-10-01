package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksPreregistration;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksPreregistrationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Outcome;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Result;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 감사회원 <b>사전등록</b>(카페 roster 503명에 없는 회원). 운영자가 ADMIN API 로 네이버 이메일을 등록해 두면,
 * 그 회원이 네이버 로그인했을 때 <b>OAuth 가 실제 반환한 이메일</b>({@code user_social_accounts.provider_email})이
 * 등록값과 정확히 일치할 때만 OAuth userId 와 1:1 연결하고 THANKS_MEMBER 를 부여한다. 회원이 이메일을 직접 입력하는 경로는 없다.
 * 이메일은 소스에 두지 않고 DB 에만 저장하며, 회원/공개 응답에는 절대 담지 않는다(운영자 API 만 조회).
 * 기존 roster 503명의 자동 인증({@link NewStartThanksVerificationService})은 그대로 둔다.
 */
@Service
public class NewStartThanksPreregistrationService {

  static final String GRANTED_BY_PREREG = "thanks-prereg";
  private static final Pattern NAVER_EMAIL = Pattern.compile("^[a-z0-9._-]{2,40}@naver\\.com$");

  private final NewStartThanksPreregistrationRepository repository;
  private final NewStartThanksRosterRepository rosterRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final NewStartMemberGrantRepository grantRepository;
  private final NewStartGrantService grantService;

  public NewStartThanksPreregistrationService(NewStartThanksPreregistrationRepository repository,
      NewStartThanksRosterRepository rosterRepository, SocialAccountRepository socialAccountRepository,
      NewStartMemberGrantRepository grantRepository, NewStartGrantService grantService) {
    this.repository = repository;
    this.rosterRepository = rosterRepository;
    this.socialAccountRepository = socialAccountRepository;
    this.grantRepository = grantRepository;
    this.grantService = grantService;
  }

  /** 운영자 화면용. 이메일은 ADMIN 응답에만 포함한다. */
  public record PreregView(Long preregId, String email, String memo, boolean linked, LocalDateTime linkedAt) {}

  // ───────────── member side (자동 인증) ─────────────

  /**
   * OAuth 이메일이 사전등록과 정확히 일치하면 연결 + 부여. 해당 없으면 empty(= 기존 흐름 계속).
   * ADMIN·비로그인·네이버 계정/이메일 없음·이미 부여됨·충돌(다른 계정이 이미 연결, 이 회원이 이미 다른 대상과 연결)은 모두 empty.
   */
  @Transactional
  public Optional<Outcome> tryAutoVerify(Long userId, String role) {
    if (userId == null || "ADMIN".equals(role)) return Optional.empty();
    SocialAccount social = socialAccountRepository.findFirstByUserIdAndProvider(userId, SocialProvider.NAVER).orElse(null);
    if (social == null || social.getProviderEmail() == null || social.getProviderEmail().isBlank()) return Optional.empty();
    if (grantRepository.existsByUserIdAndCapability(userId, NewStartCapability.THANKS_MEMBER)) return Optional.empty();

    NewStartThanksPreregistration prereg = repository.findByEmail(normalize(social.getProviderEmail())).orElse(null);
    if (prereg == null) return Optional.empty();
    if (prereg.isLinked()) {
      if (!userId.equals(prereg.getLinkedUserId())) return Optional.empty();   // 다른 계정이 이미 사용 — 부여하지 않는다
    } else {
      if (repository.findByLinkedUserId(userId).isPresent() || rosterRepository.findByLinkedUserId(userId).isPresent()) {
        return Optional.empty();   // 이 계정은 이미 다른 대상과 1:1 연결됨
      }
      prereg.link(userId, social.getProviderUserId());
      repository.save(prereg);
    }
    grantService.grant(userId, NewStartCapability.THANKS_MEMBER, GRANTED_BY_PREREG);
    return Optional.of(Outcome.of("APPROVED", NewStartThanksVerificationService.MSG_APPROVED));
  }

  // ───────────── admin side ─────────────

  @Transactional(readOnly = true)
  public List<PreregView> list() {
    return repository.findAllByOrderByPreregIdAsc().stream().map(NewStartThanksPreregistrationService::view).toList();
  }

  @Transactional
  public Result<PreregView> register(String emailInput, String memo) {
    String email = normalize(emailInput);
    if (!NAVER_EMAIL.matcher(email).matches()) {
      return Result.failure("@naver.com 이메일 형식이 아닙니다.");
    }
    if (repository.findByEmail(email).isPresent()) {
      return Result.failure("이미 사전등록된 이메일입니다.");
    }
    String m = memo == null ? "" : memo.strip();
    if (m.length() > 100) m = m.substring(0, 100);
    NewStartThanksPreregistration saved = repository.save(new NewStartThanksPreregistration(email, m.isEmpty() ? null : m));
    return Result.success(view(saved));
  }

  /** 아직 연결되지 않은 사전등록만 삭제한다(연결된 건은 grant 이력 보존을 위해 삭제 불가). */
  @Transactional
  public Result<Void> remove(Long preregId) {
    NewStartThanksPreregistration p = repository.findById(preregId).orElse(null);
    if (p == null) return Result.failure("사전등록을 찾을 수 없습니다.");
    if (p.isLinked()) return Result.failure("이미 연결된 사전등록은 삭제할 수 없습니다.");
    repository.delete(p);
    return Result.success(null);
  }

  static String normalize(String email) {
    return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
  }

  private static PreregView view(NewStartThanksPreregistration p) {
    return new PreregView(p.getPreregId(), p.getEmail(), p.getMemo(), p.isLinked(), p.getLinkedAt());
  }
}
