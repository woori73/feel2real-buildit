package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 감사회원 최초 1회 본인확인 — 핵심 계약: ① 회원은 요청(PENDING)만 만들 수 있고 권한은 ADMIN 승인에서만 부여된다,
 * ② roster ↔ 회원 1:1, ③ 접두부·닉네임·이메일 일치는 보조 정보일 뿐 자동 부여 근거가 아니다.
 */
@DisplayName("NewStartThanksClaimService — 감사회원 최초 1회 본인확인 (운영자 승인 기반 1:1 연결)")
class NewStartThanksClaimServiceTest {

  private UserRepository userRepository;
  private SocialAccountRepository socialRepository;
  private NewStartThanksRosterRepository rosterRepository;
  private NewStartThanksClaimRepository claimRepository;
  private NewStartMemberGrantRepository grantRepository;
  private NewStartGrantService grantService;
  private NewStartThanksClaimService service;

  private User user;
  private SocialAccount social;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    socialRepository = mock(SocialAccountRepository.class);
    rosterRepository = mock(NewStartThanksRosterRepository.class);
    claimRepository = mock(NewStartThanksClaimRepository.class);
    grantRepository = mock(NewStartMemberGrantRepository.class);
    grantService = mock(NewStartGrantService.class);
    service = new NewStartThanksClaimService(userRepository, socialRepository, rosterRepository, claimRepository,
        grantRepository, grantService);

    user = mock(User.class);
    when(user.getEmail()).thenReturn("ssgw1234@naver.com");
    when(user.getNickname()).thenReturn("네이버닉");
    when(user.getLoginId()).thenReturn("naver_abc");
    when(user.getName()).thenReturn("홍길동");
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));

    social = mock(SocialAccount.class);
    when(social.getProviderUserId()).thenReturn("naver-subject-1");
    when(socialRepository.findFirstByUserIdAndProvider(7L, SocialProvider.NAVER)).thenReturn(Optional.of(social));
    when(grantRepository.existsByUserIdAndCapability(anyLong(), eq(NewStartCapability.THANKS_MEMBER))).thenReturn(false);
    when(claimRepository.save(any(NewStartThanksClaim.class))).thenAnswer(inv -> inv.getArgument(0));
    when(claimRepository.countByUserIdAndStatusAndDecidedAtAfter(anyLong(), anyString(), any())).thenReturn(0L);
    when(claimRepository.findFirstByUserIdAndStatus(anyLong(), anyString())).thenReturn(Optional.empty());
    when(claimRepository.findByUserIdAndRosterId(anyLong(), anyLong())).thenReturn(Optional.empty());
    when(claimRepository.findByRosterIdAndStatus(anyLong(), anyString())).thenReturn(List.of());
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

  private static NewStartThanksClaim claim(long id, long userId, long rosterId, String subject) {
    NewStartThanksClaim c = NewStartThanksClaim.builder().userId(userId).rosterId(rosterId).providerUserId(subject)
        .verifyCode("ABCD2345").emailPrefixMatch(false).build();
    ReflectionTestUtils.setField(c, "claimId", id);
    return c;
  }

  // ───────── candidates ─────────

  @Test
  @DisplayName("후보: 닉네임 정확 일치 + 미연결 roster만, 마스킹 ID만 노출(카페 멤버 태그·전체 목록 비노출)")
  void candidates_exactNicknameUnlinkedOnly() {
    NewStartThanksRoster match = roster(11, "카페닉", "ssgw");
    NewStartThanksRoster linked = roster(12, "카페닉", "zzzz");
    linked.link(99L, "other-subject");
    when(rosterRepository.findByNicknameAndMatchStatus("카페닉", NewStartThanksRoster.MATCH_UNMATCHED))
        .thenReturn(List.of(match, linked));

    var r = service.candidates(7L, "  카페닉 ");

    assertThat(r.ok()).isTrue();
    assertThat(r.value()).hasSize(1);
    assertThat(r.value().get(0).rosterId()).isEqualTo(11L);
    assertThat(r.value().get(0).maskedId()).isEqualTo("ssgw****");
  }

  @Test
  @DisplayName("후보: 너무 짧은 입력·네이버 미연결·이미 감사회원은 거절 (일반회원 오인증/전수조회 차단)")
  void candidates_guards() {
    assertThat(service.candidates(7L, "a").ok()).isFalse();
    when(socialRepository.findFirstByUserIdAndProvider(8L, SocialProvider.NAVER)).thenReturn(Optional.empty());
    when(userRepository.findById(8L)).thenReturn(Optional.of(mock(User.class)));
    assertThat(service.candidates(8L, "카페닉").errorMessage()).contains("네이버 로그인");
    when(grantRepository.existsByUserIdAndCapability(7L, NewStartCapability.THANKS_MEMBER)).thenReturn(true);
    assertThat(service.candidates(7L, "카페닉").errorMessage()).contains("이미 감사회원");
  }

  // ───────── submit ─────────

  @Test
  @DisplayName("요청: PENDING + 8자 인증코드 + 이메일 접두부 보조 증거만 기록하고, THANKS_MEMBER는 절대 부여하지 않는다")
  void submit_createsPendingOnly_neverGrants() {
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(roster(11, "카페닉", "ssgw")));

    var r = service.submit(7L, 11L);

    assertThat(r.ok()).isTrue();
    assertThat(r.value().state()).isEqualTo("PENDING");
    assertThat(r.value().verifyCode()).hasSize(8).matches("[A-HJKMNP-Z2-9]{8}");
    verify(grantService, never()).grant(anyLong(), any(), anyString());
    verify(claimRepository).save(org.mockito.ArgumentMatchers.argThat(
        (NewStartThanksClaim c) -> c.isPending() && c.isEmailPrefixMatch() && "naver-subject-1".equals(c.getProviderUserId())));
  }

  @Test
  @DisplayName("요청: 이미 다른 회원과 연결된 roster, 네이버 미연결, 이미 감사회원, 반려 남용은 모두 거절")
  void submit_guards() {
    NewStartThanksRoster linked = roster(12, "카페닉", "zzzz");
    linked.link(99L, "other-subject");
    when(rosterRepository.findById(12L)).thenReturn(Optional.of(linked));
    assertThat(service.submit(7L, 12L).ok()).isFalse();

    when(claimRepository.countByUserIdAndStatusAndDecidedAtAfter(eq(7L), eq(NewStartThanksClaim.REJECTED), any())).thenReturn(3L);
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(roster(11, "카페닉", "ssgw")));
    assertThat(service.submit(7L, 11L).errorMessage()).contains("반려");
    verify(claimRepository, never()).save(any(NewStartThanksClaim.class));
  }

  @Test
  @DisplayName("요청: 진행 중 요청이 있으면 같은 roster는 idempotent, 다른 roster는 거절(한 회원 1요청)")
  void submit_onePendingPerUser() {
    NewStartThanksClaim pending = claim(5, 7, 11, "naver-subject-1");
    when(claimRepository.findFirstByUserIdAndStatus(7L, NewStartThanksClaim.PENDING)).thenReturn(Optional.of(pending));
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(roster(11, "카페닉", "ssgw")));

    assertThat(service.submit(7L, 11L).value().claimId()).isEqualTo(5L);
    assertThat(service.submit(7L, 12L).errorMessage()).contains("이미 진행 중");
  }

  // ───────── approve (유일한 연결·부여 경로) ─────────

  @Test
  @DisplayName("승인: roster 1:1 연결 + THANKS_MEMBER 부여 + claim APPROVED + 같은 roster의 다른 PENDING 자동 반려")
  void approve_linksGrantsAndRejectsCompetingClaims() {
    NewStartThanksClaim c = claim(5, 7, 11, "naver-subject-1");
    NewStartThanksClaim competitor = claim(6, 8, 11, "naver-subject-2");
    NewStartThanksRoster r = roster(11, "카페닉", "ssgw");
    when(claimRepository.findById(5L)).thenReturn(Optional.of(c));
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(r));
    when(claimRepository.findByRosterIdAndStatus(11L, NewStartThanksClaim.PENDING)).thenReturn(List.of(c, competitor));

    var res = service.approve(5L, "admin-f2r");

    assertThat(res.ok()).isTrue();
    assertThat(r.getLinkedUserId()).isEqualTo(7L);
    assertThat(r.getLinkedProviderUserId()).isEqualTo("naver-subject-1");
    assertThat(r.getMatchStatus()).isEqualTo(NewStartThanksRoster.MATCH_MATCHED);
    assertThat(c.getStatus()).isEqualTo(NewStartThanksClaim.APPROVED);
    assertThat(competitor.getStatus()).isEqualTo(NewStartThanksClaim.REJECTED);
    verify(grantService).grant(7L, NewStartCapability.THANKS_MEMBER, "thanks-claim:admin-f2r");
  }

  @Test
  @DisplayName("승인: 이미 연결된 roster / 네이버 계정이 바뀐 경우 / 처리 끝난 요청은 거절하고 아무것도 부여하지 않는다")
  void approve_refusesUnsafeStates() {
    NewStartThanksClaim c = claim(5, 7, 11, "naver-subject-1");
    NewStartThanksRoster linked = roster(11, "카페닉", "ssgw");
    linked.link(99L, "other");
    when(claimRepository.findById(5L)).thenReturn(Optional.of(c));
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(linked));
    assertThat(service.approve(5L, "admin").errorMessage()).contains("이미 다른 회원");

    NewStartThanksRoster free = roster(11, "카페닉", "ssgw");
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(free));
    when(social.getProviderUserId()).thenReturn("changed-subject");
    assertThat(service.approve(5L, "admin").errorMessage()).contains("네이버 계정 연결이 바뀌었");

    c.reject("admin", "x");
    assertThat(service.approve(5L, "admin").errorMessage()).contains("이미 처리");
    verify(grantService, never()).grant(anyLong(), any(), anyString());
    assertThat(free.isLinked()).isFalse();
  }

  @Test
  @DisplayName("반려: REJECTED로만 바뀌고 권한·연결은 변하지 않는다")
  void reject_doesNotGrantOrLink() {
    NewStartThanksClaim c = claim(5, 7, 11, "naver-subject-1");
    NewStartThanksRoster r = roster(11, "카페닉", "ssgw");
    when(claimRepository.findById(5L)).thenReturn(Optional.of(c));
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(r));

    assertThat(service.reject(5L, "닉네임 불일치", "admin").ok()).isTrue();

    assertThat(c.getStatus()).isEqualTo(NewStartThanksClaim.REJECTED);
    assertThat(c.getRejectReason()).isEqualTo("닉네임 불일치");
    assertThat(r.isLinked()).isFalse();
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  // ───────── 보조 증거 / 상태 ─────────

  @Test
  @DisplayName("이메일 접두부 증거: @naver.com 로컬파트가 roster 접두부로 시작할 때만 true (gmail·빈 접두부·무이메일은 false)")
  void emailPrefixMatches_rules() {
    assertThat(NewStartThanksClaimService.emailPrefixMatches("ssgw1234@naver.com", "ssgw")).isTrue();
    assertThat(NewStartThanksClaimService.emailPrefixMatches("SSGW@Naver.com", "ssgw")).isTrue();
    assertThat(NewStartThanksClaimService.emailPrefixMatches("ssgw1234@gmail.com", "ssgw")).isFalse();
    assertThat(NewStartThanksClaimService.emailPrefixMatches("xssgw@naver.com", "ssgw")).isFalse();
    assertThat(NewStartThanksClaimService.emailPrefixMatches(null, "ssgw")).isFalse();
    assertThat(NewStartThanksClaimService.emailPrefixMatches("ssgw@naver.com", "")).isFalse();
  }

  @Test
  @DisplayName("상태: 이미 THANKS_MEMBER면 APPROVED, 요청 없으면 NONE, PENDING이면 코드가 내려간다")
  void status_states() {
    assertThat(service.status(7L).state()).isEqualTo("NONE");

    NewStartThanksClaim c = claim(5, 7, 11, "naver-subject-1");
    when(claimRepository.findByUserIdOrderByClaimIdDesc(7L)).thenReturn(List.of(c));
    when(rosterRepository.findById(11L)).thenReturn(Optional.of(roster(11, "카페닉", "ssgw")));
    var pending = service.status(7L);
    assertThat(pending.state()).isEqualTo("PENDING");
    assertThat(pending.verifyCode()).isEqualTo("ABCD2345");

    when(grantRepository.existsByUserIdAndCapability(7L, NewStartCapability.THANKS_MEMBER)).thenReturn(true);
    var approved = service.status(7L);
    assertThat(approved.state()).isEqualTo("APPROVED");
    assertThat(approved.verifyCode()).isNull();
  }

  @Test
  @DisplayName("roster 원본 값(닉네임·접두부·등급)은 연결 후에도 그대로다 — link는 연결 컬럼만 쓴다")
  void link_preservesRosterSnapshot() {
    NewStartThanksRoster r = roster(11, "카페닉", "ssgw");
    r.link(7L, "naver-subject-1");
    assertThat(r.getNickname()).isEqualTo("카페닉");
    assertThat(r.getNaverIdPrefix()).isEqualTo("ssgw");
    assertThat(r.getGrade()).isEqualTo("감사멤버");
    assertThat(r.getCafeMemberTag()).isEqualTo("tag11");
  }
}
