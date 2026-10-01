package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 감사회원 셀프 인증 — 운영자 승인 없이 끝나는 자동 인증과, 안전하게 끝낼 수 없는 예외(운영자 알림)의 경계를 고정한다.
 * 핵심: 접두부 단독·닉네임 단독으로는 절대 인증하지 않는다 / roster·계정 1:1 / ADMIN 은 절차 없이 통과 / 정상 인증은 알리지 않는다.
 */
@DisplayName("NewStartThanksVerificationService — 최초 1회 셀프 인증(자동 1:1 연결) + 예외 처리")
class NewStartThanksVerificationServiceTest {

  private UserRepository userRepository;
  private SocialAccountRepository socialRepository;
  private NewStartThanksRosterRepository rosterRepository;
  private NewStartThanksVerificationRepository verificationRepository;
  private NewStartMemberGrantRepository grantRepository;
  private NewStartGrantService grantService;
  private ThanksExceptionNotifier notifier;
  private NewStartThanksProperties props;
  private NewStartThanksVerificationService service;
  private SocialAccount social;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    socialRepository = mock(SocialAccountRepository.class);
    rosterRepository = mock(NewStartThanksRosterRepository.class);
    verificationRepository = mock(NewStartThanksVerificationRepository.class);
    grantRepository = mock(NewStartMemberGrantRepository.class);
    grantService = mock(NewStartGrantService.class);
    notifier = mock(ThanksExceptionNotifier.class);
    props = new NewStartThanksProperties();
    service = new NewStartThanksVerificationService(userRepository, socialRepository, rosterRepository,
        verificationRepository, grantRepository, grantService, notifier, props);

    User user = mock(User.class);
    when(user.getNickname()).thenReturn("네이버닉");
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    social = mock(SocialAccount.class);
    when(social.getProviderUserId()).thenReturn("naver-subject-1");
    when(social.getProviderEmail()).thenReturn("ssgw1234@naver.com");
    when(socialRepository.findFirstByUserIdAndProvider(7L, SocialProvider.NAVER)).thenReturn(Optional.of(social));
    when(grantRepository.existsByUserIdAndCapability(anyLong(), eq(NewStartCapability.THANKS_MEMBER))).thenReturn(false);
    when(verificationRepository.findFirstByUserIdAndOutcomeAndStatus(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
    when(verificationRepository.save(any(NewStartThanksVerification.class))).thenAnswer(inv -> {
      NewStartThanksVerification v = inv.getArgument(0);
      if (v.getVerificationId() == null) ReflectionTestUtils.setField(v, "verificationId", 100L);
      return v;
    });
    when(verificationRepository.countByUserIdAndOutcomeAndCreatedAtAfter(anyLong(), anyString(), any())).thenReturn(1L);
    when(rosterRepository.findByLinkedUserId(anyLong())).thenReturn(Optional.empty());
    when(grantService.grant(anyLong(), any(), anyString())).thenReturn(
        NewStartGrantService.GrantResult.success(new NewStartGrantService.GrantView(1L, 7L, NewStartCapability.THANKS_MEMBER, "x")));
  }

  private static NewStartThanksRoster roster(long id, String nick, String prefix) {
    NewStartThanksRoster r = NewStartThanksRoster.builder().cafeMemberTag("tag" + id).naverIdPrefix(prefix).nickname(nick)
        .grade("감사멤버").memberLevel(150).source("NAVER_CAFE").snapshotDate(LocalDate.of(2026, 10, 1))
        .matchStatus(NewStartThanksRoster.MATCH_UNMATCHED).build();
    ReflectionTestUtils.setField(r, "rosterId", id);
    return r;
  }

  private void rosterFor(String nick, NewStartThanksRoster... rows) {
    when(rosterRepository.findByNickname(nick)).thenReturn(List.of(rows));
  }

  private NewStartThanksVerification lastSaved() {
    ArgumentCaptor<NewStartThanksVerification> c = ArgumentCaptor.forClass(NewStartThanksVerification.class);
    verify(verificationRepository, org.mockito.Mockito.atLeastOnce()).save(c.capture());
    return c.getValue();
  }

  // ───────── 자동 인증 (운영자 개입 없음) ─────────

  @Test
  @DisplayName("닉네임 정확 일치 + @naver.com 로컬파트가 접두부로 시작 + 둘 다 미연결 + 후보 1명 → 운영자 없이 1:1 연결 + THANKS_MEMBER, 운영자 알림 없음")
  void autoVerify_linksAndGrants_withoutOperator() {
    NewStartThanksRoster r = roster(11, "카페닉", "ssgw");
    rosterFor("카페닉", r);

    var out = service.verify(7L, "STUDENT", "  카페닉 ");

    assertThat(out.state()).isEqualTo("APPROVED");
    assertThat(r.getLinkedUserId()).isEqualTo(7L);
    assertThat(r.getLinkedProviderUserId()).isEqualTo("naver-subject-1");
    assertThat(r.getMatchStatus()).isEqualTo(NewStartThanksRoster.MATCH_MATCHED);
    verify(grantService).grant(7L, NewStartCapability.THANKS_MEMBER, "thanks-self");
    assertThat(lastSaved().getOutcome()).isEqualTo(NewStartThanksVerification.AUTO_APPROVED);
    verify(notifier, never()).notifyAsync(anyLong());          // 정상 인증은 운영자에게 알리지 않는다
  }

  @Test
  @DisplayName("닉네임만 맞고 네이버 ID가 접두부와 다르면 인증하지 않는다(닉네임 단독 인증 금지) — 실패로만 기록, 부여·연결 없음")
  void nicknameAloneIsNeverEnough() {
    NewStartThanksRoster r = roster(11, "카페닉", "zzzz");
    rosterFor("카페닉", r);

    var out = service.verify(7L, "STUDENT", "카페닉");

    assertThat(out.state()).isEqualTo("FAILED");
    assertThat(out.message()).doesNotContain("이메일").doesNotContain("접두").doesNotContain("zzzz");
    verify(grantService, never()).grant(anyLong(), any(), anyString());
    assertThat(r.isLinked()).isFalse();
    verify(notifier, never()).notifyAsync(anyLong());
  }

  @Test
  @DisplayName("이메일(접두부)만 맞고 닉네임이 roster에 없으면 인증하지 않는다(접두부 단독 인증 금지)")
  void prefixAloneIsNeverEnough() {
    rosterFor("없는닉", new NewStartThanksRoster[0]);

    var out = service.verify(7L, "STUDENT", "없는닉");

    assertThat(out.state()).isEqualTo("FAILED");
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("접두부가 2글자여도 닉네임 정확 일치 + 이메일 접두부 일치 + 양쪽 미연결이면 자동 인증한다(길이 제한 없음, 예외함 미적재)")
  void twoCharPrefix_autoVerifies() {
    when(social.getProviderEmail()).thenReturn("tomato99@naver.com");
    NewStartThanksRoster r = roster(11, "카페닉", "to");
    rosterFor("카페닉", r);

    var out = service.verify(7L, "STUDENT", "카페닉");

    assertThat(out.state()).isEqualTo("APPROVED");
    verify(grantService).grant(eq(7L), eq(NewStartCapability.THANKS_MEMBER), anyString());
    assertThat(r.isLinked()).isTrue();
    verify(notifier, never()).notifyAsync(anyLong());
  }

  @Test
  @DisplayName("접두부가 2글자이고 닉네임만 맞으면(이메일 접두부 불일치) 인증하지 않는다 — 예외함이 아니라 실패로만 기록")
  void twoCharPrefix_nicknameAloneStillFails() {
    when(social.getProviderEmail()).thenReturn("zz99@naver.com");
    NewStartThanksRoster r = roster(11, "카페닉", "to");
    rosterFor("카페닉", r);

    var out = service.verify(7L, "STUDENT", "카페닉");

    assertThat(out.state()).isEqualTo("FAILED");
    verify(grantService, never()).grant(anyLong(), any(), anyString());
    assertThat(r.isLinked()).isFalse();
  }

  @Test
  @DisplayName("roster 접두부 데이터가 비어 있으면 닉네임 단독 인증이 되지 않도록 인증하지 않는다")
  void blankPrefix_neverAutoVerifies() {
    NewStartThanksRoster r = roster(11, "카페닉", "");
    rosterFor("카페닉", r);

    var out = service.verify(7L, "STUDENT", "카페닉");

    assertThat(out.state()).isEqualTo("FAILED");
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  // ───────── 예외: 자동 인증 중단 + ADMIN 예외함 + 운영자 알림 ─────────

  @Test
  @DisplayName("OAuth 이메일이 없으면 예외(EMAIL_MISSING): 연결·부여 없이 예외함에 적재하고 커밋 후 운영자에게 알린다")
  void emailMissing_exception() {
    when(social.getProviderEmail()).thenReturn(null);
    rosterFor("카페닉", roster(11, "카페닉", "ssgw"));

    var out = service.verify(7L, "STUDENT", "카페닉");

    assertThat(out.state()).isEqualTo("EXCEPTION");
    NewStartThanksVerification v = lastSaved();
    assertThat(v.getExceptionReason()).isEqualTo(ThanksExceptionReason.EMAIL_MISSING);
    assertThat(v.isOpenException()).isTrue();
    verify(notifier).notifyAsync(100L);
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("이메일이 @naver.com이 아니면 예외(EMAIL_NOT_NAVER)")
  void emailNotNaver_exception() {
    when(social.getProviderEmail()).thenReturn("ssgw1234@gmail.com");
    rosterFor("카페닉", roster(11, "카페닉", "ssgw"));

    assertThat(service.verify(7L, "STUDENT", "카페닉").state()).isEqualTo("EXCEPTION");
    assertThat(lastSaved().getExceptionReason()).isEqualTo(ThanksExceptionReason.EMAIL_NOT_NAVER);
    verify(notifier).notifyAsync(100L);
  }

  @Test
  @DisplayName("이미 다른 계정과 연결된 roster는 예외(ROSTER_ALREADY_LINKED), 내 계정과 이미 연결된 roster는 idempotent 성공")
  void rosterAlreadyLinked() {
    NewStartThanksRoster other = roster(11, "카페닉", "ssgw");
    other.link(99L, "other-subject");
    rosterFor("카페닉", other);
    assertThat(service.verify(7L, "STUDENT", "카페닉").state()).isEqualTo("EXCEPTION");
    assertThat(lastSaved().getExceptionReason()).isEqualTo(ThanksExceptionReason.ROSTER_ALREADY_LINKED);

    NewStartThanksRoster mine = roster(12, "내닉", "ssgw");
    mine.link(7L, "naver-subject-1");
    rosterFor("내닉", mine);
    assertThat(service.verify(7L, "STUDENT", "내닉").state()).isEqualTo("APPROVED");
  }

  @Test
  @DisplayName("현재 OAuth 계정이 이미 다른 roster와 연결돼 있으면 예외(DUPLICATE_OAUTH_LINK) — 한 계정은 한 명단만")
  void duplicateOauthLink() {
    NewStartThanksRoster other = roster(5, "다른닉", "abcd");
    other.link(7L, "naver-subject-1");
    when(rosterRepository.findByLinkedUserId(7L)).thenReturn(Optional.of(other));
    rosterFor("카페닉", roster(11, "카페닉", "ssgw"));

    assertThat(service.verify(7L, "STUDENT", "카페닉").state()).isEqualTo("EXCEPTION");
    assertThat(lastSaved().getExceptionReason()).isEqualTo(ThanksExceptionReason.DUPLICATE_OAUTH_LINK);
  }

  @Test
  @DisplayName("닉네임 일치 후보가 2명 이상이면 자동 확정하지 않고 예외(AMBIGUOUS_CANDIDATES)")
  void ambiguousCandidates() {
    rosterFor("카페닉", roster(11, "카페닉", "ssgw"), roster(12, "카페닉", "ssgx"));

    assertThat(service.verify(7L, "STUDENT", "카페닉").state()).isEqualTo("EXCEPTION");
    assertThat(lastSaved().getExceptionReason()).isEqualTo(ThanksExceptionReason.AMBIGUOUS_CANDIDATES);
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("단순 실패가 하루 허용 횟수에 도달하면 예외(REPEATED_FAILURE)로 승격되고 운영자에게 알린다")
  void repeatedFailure_promotedToException() {
    when(verificationRepository.countByUserIdAndOutcomeAndCreatedAtAfter(anyLong(), anyString(), any())).thenReturn(3L);
    rosterFor("틀린닉", new NewStartThanksRoster[0]);

    assertThat(service.verify(7L, "STUDENT", "틀린닉").state()).isEqualTo("EXCEPTION");
    assertThat(lastSaved().getExceptionReason()).isEqualTo(ThanksExceptionReason.REPEATED_FAILURE);
    verify(notifier).notifyAsync(100L);
  }

  @Test
  @DisplayName("이미 처리 대기 중인 예외가 있는 회원은 새 예외·중복 알림을 만들지 않는다")
  void openException_suppressesDuplicates() {
    NewStartThanksVerification open = NewStartThanksVerification.builder().userId(7L).nicknameInput("카페닉")
        .providerUserId("naver-subject-1").outcome(NewStartThanksVerification.EXCEPTION)
        .exceptionReason(ThanksExceptionReason.EMAIL_MISSING).build();
    when(verificationRepository.findFirstByUserIdAndOutcomeAndStatus(7L, NewStartThanksVerification.EXCEPTION,
        NewStartThanksVerification.OPEN)).thenReturn(Optional.of(open));

    assertThat(service.verify(7L, "STUDENT", "카페닉").state()).isEqualTo("EXCEPTION");
    assertThat(service.status(7L, "STUDENT").state()).isEqualTo("EXCEPTION");
    verify(verificationRepository, never()).save(any(NewStartThanksVerification.class));
    verify(notifier, never()).notifyAsync(anyLong());
  }

  // ───────── ADMIN / 입력 검증 ─────────

  @Test
  @DisplayName("ADMIN은 인증 절차를 거치지 않는다: 상태 ADMIN, verify는 아무 기록·연결·부여·알림도 만들지 않는다")
  void admin_isExempt_noRecordsAtAll() {
    assertThat(service.status(1L, "ADMIN").state()).isEqualTo("ADMIN");
    assertThat(service.verify(1L, "ADMIN", "아무닉").state()).isEqualTo("ADMIN");

    verify(verificationRepository, never()).save(any(NewStartThanksVerification.class));
    verify(rosterRepository, never()).save(any(NewStartThanksRoster.class));
    verify(grantService, never()).grant(anyLong(), any(), anyString());
    verify(notifier, never()).notifyAsync(anyLong());
  }

  @Test
  @DisplayName("네이버 계정 미연결·너무 짧은 닉네임·이미 감사회원은 즉시 응답(실패 횟수에 넣지 않음)")
  void guards() {
    assertThat(service.verify(7L, "STUDENT", "a").state()).isEqualTo("ERROR");
    verify(verificationRepository, never()).save(any(NewStartThanksVerification.class));

    when(socialRepository.findFirstByUserIdAndProvider(8L, SocialProvider.NAVER)).thenReturn(Optional.empty());
    when(userRepository.findById(8L)).thenReturn(Optional.of(mock(User.class)));
    assertThat(service.verify(8L, "STUDENT", "카페닉").message()).contains("네이버 로그인");

    when(grantRepository.existsByUserIdAndCapability(7L, NewStartCapability.THANKS_MEMBER)).thenReturn(true);
    assertThat(service.verify(7L, "STUDENT", "카페닉").state()).isEqualTo("APPROVED");
    assertThat(service.status(7L, "STUDENT").state()).isEqualTo("APPROVED");
  }

  // ───────── 운영자 예외 처리 ─────────

  private NewStartThanksVerification openException(long id, Long rosterId) {
    NewStartThanksVerification v = NewStartThanksVerification.builder().userId(7L).rosterId(rosterId).nicknameInput("카페닉")
        .providerUserId("naver-subject-1").outcome(NewStartThanksVerification.EXCEPTION)
        .exceptionReason(ThanksExceptionReason.EMAIL_MISSING).build();
    ReflectionTestUtils.setField(v, "verificationId", id);
    when(verificationRepository.findById(id)).thenReturn(Optional.of(v));
    return v;
  }

  @Test
  @DisplayName("운영자 처리(연결 승인): 같은 불변식 재확인 후 1:1 연결 + THANKS_MEMBER, 예외 RESOLVED")
  void adminResolve_linksAndGrants() {
    NewStartThanksVerification v = openException(100L, 11L);
    NewStartThanksRoster r = roster(11, "카페닉", "ssgw");
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(r));

    var res = service.adminResolve(100L, null, "admin-f2r");

    assertThat(res.ok()).isTrue();
    assertThat(r.getLinkedUserId()).isEqualTo(7L);
    assertThat(v.getStatus()).isEqualTo(NewStartThanksVerification.RESOLVED);
    verify(grantService).grant(7L, NewStartCapability.THANKS_MEMBER, "thanks-exception:admin-f2r");
  }

  @Test
  @DisplayName("운영자 처리 거절 조건: 명단 미지정·이미 연결된 명단·네이버 계정 변경·이미 처리됨")
  void adminResolve_refusesUnsafe() {
    openException(100L, null);
    assertThat(service.adminResolve(100L, null, "a").errorMessage()).contains("명단을 선택");

    NewStartThanksRoster linked = roster(11, "카페닉", "ssgw");
    linked.link(99L, "other");
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(linked));
    assertThat(service.adminResolve(100L, 11L, "a").errorMessage()).contains("이미 다른 계정");

    NewStartThanksRoster free = roster(12, "카페닉2", "ssgw");
    when(rosterRepository.findById(12L)).thenReturn(Optional.of(free));
    when(social.getProviderUserId()).thenReturn("changed");
    assertThat(service.adminResolve(100L, 12L, "a").errorMessage()).contains("네이버 계정 연결이 바뀌었");
    verify(grantService, never()).grant(anyLong(), any(), anyString());

    NewStartThanksVerification done = openException(101L, 12L);
    done.dismiss("a", "x");
    assertThat(service.adminResolve(101L, 12L, "a").errorMessage()).contains("이미 처리");
  }

  @Test
  @DisplayName("운영자 종료(dismiss)는 연결·부여 없이 예외만 닫는다")
  void adminDismiss_closesOnly() {
    NewStartThanksVerification v = openException(100L, 11L);

    assertThat(service.adminDismiss(100L, "카페에서 본인 아님 확인", "admin").ok()).isTrue();

    assertThat(v.getStatus()).isEqualTo(NewStartThanksVerification.DISMISSED);
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("보조 함수: NFC·공백 정규화, @naver.com 이메일만 네이버 ID로 해석")
  void helpers() {
    assertThat(NewStartThanksVerificationService.normalizeNickname("  카페   닉 ")).isEqualTo("카페 닉");
    assertThat(NewStartThanksVerificationService.naverIdFromEmail("SSGW1234@Naver.com")).isEqualTo("ssgw1234");
    assertThat(NewStartThanksVerificationService.naverIdFromEmail("a@gmail.com")).isNull();
    assertThat(NewStartThanksVerificationService.naverIdFromEmail("@naver.com")).isNull();
    assertThat(NewStartThanksVerificationService.naverIdFromEmail(null)).isNull();
  }

  @Test
  @DisplayName("roster 원본(닉네임·접두부·등급·멤버 태그)은 연결 후에도 그대로다 — link는 연결 컬럼만 쓴다")
  void link_preservesRosterSnapshot() {
    NewStartThanksRoster r = roster(11, "카페닉", "ssgw");
    r.link(7L, "naver-subject-1");
    assertThat(r.getNickname()).isEqualTo("카페닉");
    assertThat(r.getNaverIdPrefix()).isEqualTo("ssgw");
    assertThat(r.getGrade()).isEqualTo("감사멤버");
    assertThat(r.getCafeMemberTag()).isEqualTo("tag11");
    verify(grantService, times(0)).grant(anyLong(), any(), anyString());
  }
}
