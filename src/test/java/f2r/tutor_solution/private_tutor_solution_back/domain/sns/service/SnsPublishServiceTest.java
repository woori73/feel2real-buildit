package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram.InstagramApi;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnsPublishServiceTest {

  SnsPostRepository posts = mock(SnsPostRepository.class);
  SnsAutomationRuleRepository rules = mock(SnsAutomationRuleRepository.class);
  SnsAccountRepository accounts = mock(SnsAccountRepository.class);
  SnsGenerationLogRepository logs = mock(SnsGenerationLogRepository.class);
  InstagramApi ig = mock(InstagramApi.class);
  SnsProperties props = new SnsProperties();
  SnsTokenCipher cipher;
  SnsPublishService service;
  SnsPost post;
  SnsAccount account;

  @BeforeEach
  void setUp() {
    props.setTokenKeyBase64(SnsTokenCipherTest.key32());
    props.setPublicBaseUrl("https://feel2real.co.kr/");
    cipher = new SnsTokenCipher(props);
    service = new SnsPublishService(posts, rules, accounts, logs, ig, cipher, props, ms -> {});
    SnsAutomationRule rule = new SnsAutomationRule();
    rule.setId(1L);
    rule.setAccountId(5L);
    when(rules.findById(1L)).thenReturn(Optional.of(rule));
    account = SnsAccount.instagram("1789", "woorinaesin");
    account.setId(5L);
    account.setAccessTokenEnc(cipher.encrypt("LONGTOKEN"));
    when(accounts.findById(5L)).thenReturn(Optional.of(account));
    post = new SnsPost();
    post.setId(9L);
    post.setRuleId(1L);
    post.setStatus(SnsPost.Status.REVIEW);
    post.setCaption("본문\n\n고정");
    post.setHashtags("#a #b");
    post.setImagePaths("key.jpg");
    post.setMediaToken("tok_abcdefghijklmnopqrstu");
    when(posts.findById(9L)).thenReturn(Optional.of(post));
    when(posts.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  @DisplayName("given 연결된 계정 when publishNow then 공개 이미지 주소·문구+해시태그로 올리고 media id 저장")
  void publish_success() {
    // given
    when(ig.createImageContainer(eq("1789"), eq("LONGTOKEN"), anyString(), anyString())).thenReturn("C1");
    when(ig.containerStatus("C1", "LONGTOKEN")).thenReturn("IN_PROGRESS", "FINISHED");
    when(ig.publish("1789", "LONGTOKEN", "C1")).thenReturn("M1");
    // when
    SnsPost p = service.publishNow(9L);
    // then
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> caption = ArgumentCaptor.forClass(String.class);
    verify(ig).createImageContainer(eq("1789"), eq("LONGTOKEN"), url.capture(), caption.capture());
    assertThat(url.getValue()).isEqualTo("https://feel2real.co.kr/api/sns/media/tok_abcdefghijklmnopqrstu.jpg");
    assertThat(caption.getValue()).isEqualTo("본문\n\n고정\n\n#a #b");
    assertThat(p.getStatus()).isEqualTo(SnsPost.Status.PUBLISHED);
    assertThat(p.getIgMediaId()).isEqualTo("M1");
  }

  @Test
  @DisplayName("given 인스타그램 오류 when publishNow then 1회만 다시 시도하고 실패 사유 기록")
  void publish_failsTwice() {
    // given
    when(ig.createImageContainer(any(), any(), any(), any())).thenThrow(new InstagramApi.InstagramApiException("게시물 준비 실패: rate limit"));
    // when
    SnsPost p = service.publishNow(9L);
    // then
    verify(ig, times(2)).createImageContainer(any(), any(), any(), any());
    assertThat(p.getStatus()).isEqualTo(SnsPost.Status.FAILED);
    assertThat(p.getFailReason()).contains("2회").contains("rate limit");
  }

  @Test
  @DisplayName("given 계정 연결 끊김 when publishNow then 발행하지 않고 안내")
  void publish_requiresAccount() {
    // given
    account.setStatus(SnsAccount.Status.EXPIRED);
    // when / then
    assertThatThrownBy(() -> service.publishNow(9L)).hasMessageContaining("계정");
  }

  @Test
  @DisplayName("given OAuth code when connect then 긴 토큰을 암호화해 저장하고 만료일 기록")
  void connect_storesEncryptedToken() {
    // given
    when(ig.exchangeCode("CODE")).thenReturn(new InstagramApi.TokenResult("SHORT", "1789", 3600));
    when(ig.exchangeLongLived("SHORT")).thenReturn(new InstagramApi.TokenResult("LONG2", null, 5_184_000));
    when(ig.me("LONG2")).thenReturn(new InstagramApi.Profile("1789", "woorinaesin"));
    when(accounts.findAll()).thenReturn(List.of());
    when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
    // when
    SnsAccount a = service.connect("CODE#_");
    // then
    assertThat(a.getAccessTokenEnc()).doesNotContain("LONG2");
    assertThat(cipher.decrypt(a.getAccessTokenEnc())).isEqualTo("LONG2");
    assertThat(a.getTokenExpiresAt()).isAfter(SnsTime.now().plusDays(59));
    assertThat(a.getStatus()).isEqualTo(SnsAccount.Status.CONNECTED);
  }

  @Test
  @DisplayName("given 시험 생성 글 when schedule then 예약 불가")
  void schedule_rejectsTrial() {
    // given
    post.setTrial(true);
    // when / then
    assertThatThrownBy(() -> service.schedule(9L, SnsTime.now().plusHours(1))).hasMessageContaining("시험");
  }
}
