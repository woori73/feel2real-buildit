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
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksPreregistration;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksPreregistrationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신규 감사회원 사전등록 — 테스트용 이메일은 가짜 값이다(실제 회원 이메일은 소스/테스트에 두지 않는다). */
class NewStartThanksPreregistrationServiceTest {

  private static final String EMAIL = "newmember01@naver.com";

  private NewStartThanksPreregistrationRepository repository;
  private NewStartThanksRosterRepository rosterRepository;
  private SocialAccountRepository socialRepository;
  private NewStartMemberGrantRepository grantRepository;
  private NewStartGrantService grantService;
  private NewStartThanksPreregistrationService service;
  private SocialAccount social;

  @BeforeEach
  void setUp() {
    repository = mock(NewStartThanksPreregistrationRepository.class);
    rosterRepository = mock(NewStartThanksRosterRepository.class);
    socialRepository = mock(SocialAccountRepository.class);
    grantRepository = mock(NewStartMemberGrantRepository.class);
    grantService = mock(NewStartGrantService.class);
    service = new NewStartThanksPreregistrationService(repository, rosterRepository, socialRepository, grantRepository, grantService);

    social = mock(SocialAccount.class);
    when(social.getProviderUserId()).thenReturn("naver-subject-9");
    when(social.getProviderEmail()).thenReturn(EMAIL);
    when(socialRepository.findFirstByUserIdAndProvider(7L, SocialProvider.NAVER)).thenReturn(Optional.of(social));
    when(grantRepository.existsByUserIdAndCapability(anyLong(), eq(NewStartCapability.THANKS_MEMBER))).thenReturn(false);
    when(repository.findByLinkedUserId(anyLong())).thenReturn(Optional.empty());
    when(rosterRepository.findByLinkedUserId(anyLong())).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("OAuth 이메일이 사전등록과 정확히 일치하면 OAuth userId와 1:1 연결하고 THANKS_MEMBER를 부여한다")
  void exactEmailMatch_linksAndGrants() {
    NewStartThanksPreregistration p = new NewStartThanksPreregistration(EMAIL, null);
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(p));

    var out = service.tryAutoVerify(7L, "STUDENT");

    assertThat(out).isPresent();
    assertThat(out.get().state()).isEqualTo("APPROVED");
    assertThat(p.getLinkedUserId()).isEqualTo(7L);
    assertThat(p.getLinkedProviderUserId()).isEqualTo("naver-subject-9");
    verify(grantService).grant(eq(7L), eq(NewStartCapability.THANKS_MEMBER), eq("thanks-prereg"));
  }

  @Test
  @DisplayName("OAuth 이메일은 대소문자가 달라도 소문자 정규화 후 정확히 일치할 때만 인정한다")
  void emailIsNormalized() {
    when(social.getProviderEmail()).thenReturn("  NewMember01@Naver.com ");
    NewStartThanksPreregistration p = new NewStartThanksPreregistration(EMAIL, null);
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(p));

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isPresent();
  }

  @Test
  @DisplayName("등록되지 않은 이메일이면 아무것도 하지 않는다(기존 흐름 계속)")
  void unregisteredEmail_noop() {
    when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isEmpty();
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("OAuth 이메일이 없으면(사용자 입력 경로 없음) 인증하지 않는다")
  void noOAuthEmail_noop() {
    when(social.getProviderEmail()).thenReturn(null);

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isEmpty();
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("다른 계정이 이미 연결한 사전등록은 부여하지 않는다")
  void alreadyLinkedToOtherUser_noGrant() {
    NewStartThanksPreregistration p = new NewStartThanksPreregistration(EMAIL, null);
    p.link(99L, "other-subject");
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(p));

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isEmpty();
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("이 계정이 이미 다른 roster와 연결돼 있으면 사전등록으로 중복 연결하지 않는다(1:1)")
  void userAlreadyLinkedToRoster_noGrant() {
    NewStartThanksPreregistration p = new NewStartThanksPreregistration(EMAIL, null);
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(p));
    when(rosterRepository.findByLinkedUserId(7L)).thenReturn(Optional.of(mock(NewStartThanksRoster.class)));

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isEmpty();
    assertThat(p.isLinked()).isFalse();
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("이미 내 계정에 연결된 사전등록이면 idempotent하게 권한만 보장한다")
  void alreadyMine_idempotent() {
    NewStartThanksPreregistration p = new NewStartThanksPreregistration(EMAIL, null);
    p.link(7L, "naver-subject-9");
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(p));

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isPresent();
    verify(grantService).grant(eq(7L), eq(NewStartCapability.THANKS_MEMBER), anyString());
  }

  @Test
  @DisplayName("ADMIN·비로그인은 사전등록 절차를 거치지 않는다")
  void adminAndAnonymous_skip() {
    assertThat(service.tryAutoVerify(7L, "ADMIN")).isEmpty();
    assertThat(service.tryAutoVerify(null, null)).isEmpty();
    verify(repository, never()).findByEmail(anyString());
  }

  @Test
  @DisplayName("이미 THANKS_MEMBER면 다시 처리하지 않는다")
  void alreadyGranted_noop() {
    when(grantRepository.existsByUserIdAndCapability(7L, NewStartCapability.THANKS_MEMBER)).thenReturn(true);

    assertThat(service.tryAutoVerify(7L, "STUDENT")).isEmpty();
    verify(repository, never()).findByEmail(anyString());
  }

  @Test
  @DisplayName("운영자 등록: @naver.com 형식만 허용하고 소문자로 저장하며 중복은 거절한다")
  void register_validatesAndNormalizes() {
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
    when(repository.save(any(NewStartThanksPreregistration.class))).thenAnswer(inv -> inv.getArgument(0));

    var ok = service.register("  NewMember01@Naver.com ", "신규 감사회원");
    assertThat(ok.ok()).isTrue();
    assertThat(ok.value().email()).isEqualTo(EMAIL);

    assertThat(service.register("someone@gmail.com", null).ok()).isFalse();
    assertThat(service.register("", null).ok()).isFalse();

    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(new NewStartThanksPreregistration(EMAIL, null)));
    assertThat(service.register(EMAIL, null).ok()).isFalse();
  }

  @Test
  @DisplayName("연결된 사전등록은 삭제할 수 없고, 미연결만 삭제된다")
  void remove_onlyUnlinked() {
    NewStartThanksPreregistration unlinked = new NewStartThanksPreregistration(EMAIL, null);
    NewStartThanksPreregistration linked = new NewStartThanksPreregistration("x1@naver.com", null);
    linked.link(7L, "s");
    when(repository.findById(1L)).thenReturn(Optional.of(unlinked));
    when(repository.findById(2L)).thenReturn(Optional.of(linked));

    assertThat(service.remove(1L).ok()).isTrue();
    verify(repository).delete(unlinked);
    assertThat(service.remove(2L).ok()).isFalse();
    verify(repository, never()).delete(linked);
  }

  @Test
  @DisplayName("동일인 identity 사전등록: 카페 접두부가 OAuth 이메일과 달라도 이메일 정확 일치로만 연결되고 접두부는 판정에 쓰이지 않는다")
  void identityPrefixDiffersFromEmail_linksByExactEmailOnly() {
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
    when(repository.save(any(NewStartThanksPreregistration.class))).thenAnswer(inv -> inv.getArgument(0));
    var reg = service.register(EMAIL, "신규", "테스트닉1", "Zzzz", "tag-test-0001", "감사멤버");
    assertThat(reg.ok()).isTrue();
    assertThat(reg.value().cafeNickname()).isEqualTo("테스트닉1");
    assertThat(reg.value().naverIdPrefix()).isEqualTo("zzzz");          // 소문자 정규화, 이메일 로컬파트(newmember01)와 다름
    assertThat(reg.value().cafeMemberTag()).isEqualTo("tag-test-0001");
    assertThat(reg.value().memberGrade()).isEqualTo("감사멤버");

    NewStartThanksPreregistration p = new NewStartThanksPreregistration(EMAIL, null, "테스트닉1", "zzzz", "tag-test-0001", "감사멤버");
    when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(p));
    assertThat(service.tryAutoVerify(7L, "STUDENT")).isPresent();
    assertThat(p.getLinkedUserId()).isEqualTo(7L);
    verify(grantService).grant(eq(7L), eq(NewStartCapability.THANKS_MEMBER), eq("thanks-prereg"));
  }

  @Test
  @DisplayName("roster 503명과 카페 멤버 태그/닉네임이 겹치는 사전등록은 거절한다(같은 사람을 두 번째 identity 로 만들지 않음)")
  void identityDuplicateOfRoster_rejected() {
    when(repository.findByEmail(anyString())).thenReturn(Optional.empty());
    when(rosterRepository.existsByCafeMemberTag("tag-dup")).thenReturn(true);
    assertThat(service.register(EMAIL, null, "새닉", "abcd", "tag-dup", "감사멤버").ok()).isFalse();

    when(rosterRepository.findByNickname("기존닉")).thenReturn(java.util.List.of(mock(NewStartThanksRoster.class)));
    assertThat(service.register(EMAIL, null, "기존닉", "abcd", "tag-new", "감사멤버").ok()).isFalse();

    when(repository.existsByCafeNickname("사전닉")).thenReturn(true);
    assertThat(service.register(EMAIL, null, "사전닉", "abcd", "tag-new2", "감사멤버").ok()).isFalse();
    verify(repository, never()).save(any(NewStartThanksPreregistration.class));
  }
}
