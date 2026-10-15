package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessageStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.repository.WikiLiveMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("WikiLiveMessageService — 검증 · escape · 도배 방지 · 마스킹")
class WikiLiveMessageServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private WikiLiveMessageRepository repository;
  private UserRepository userRepository;
  private WikiLiveMessageService service;

  @BeforeEach
  void setUp() {
    repository = mock(WikiLiveMessageRepository.class);
    userRepository = mock(UserRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-06T04:00:00Z"), SEOUL);
    service = new WikiLiveMessageService(repository, userRepository, clock);

    when(userRepository.findById(1L)).thenReturn(Optional.of(
        User.builder().loginId("stu1").password("p").name("김철수").nickname("철수").email(null).phone(null)
            .role(Role.STUDENT).build()));
    when(repository.findLastCreatedAtByUserId(any())).thenReturn(Optional.empty());
    when(repository.save(any(WikiLiveMessage.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("정상 작성: 실명은 '김OO' 로 마스킹되어 스냅샷 저장된다")
  void create_masksRealName() {
    WikiLiveMessageResponse res = service.create(1L, "고1 9모 자료 보러 왔어요!");

    assertThat(res.displayName()).isEqualTo("김OO");
    assertThat(res.message()).isEqualTo("고1 9모 자료 보러 왔어요!");
  }

  @Test
  @DisplayName("XSS 페이로드는 저장 전에 HTML escape 된다")
  void create_escapesHtml() {
    WikiLiveMessageResponse res = service.create(1L, "<script>alert('x')</script>");

    assertThat(res.message()).doesNotContain("<script>", "</script>");
    assertThat(res.message()).contains("&lt;script&gt;", "&#39;");
  }

  @Test
  @DisplayName("빈 내용 / null 은 400")
  void create_rejectsEmpty() {
    assertThatThrownBy(() -> service.create(1L, "   \n  "))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    assertThatThrownBy(() -> service.create(1L, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("100자 초과는 400, 100자는 허용")
  void create_enforcesMaxLength() {
    assertThatThrownBy(() -> service.create(1L, "가".repeat(101)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    assertThat(service.create(1L, "나".repeat(100)).message()).hasSize(100);
  }

  @Test
  @DisplayName("익명(userId=null)은 401")
  void create_rejectsAnonymous() {
    assertThatThrownBy(() -> service.create(null, "안녕하세요"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
  }

  @Test
  @DisplayName("동일 사용자 연속 작성은 rate limit(429)")
  void create_rateLimitsRapidRepeat() {
    service.create(1L, "첫 인사");

    assertThatThrownBy(() -> service.create(1L, "바로 또 인사"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("429");
  }

  @Test
  @DisplayName("관리자 숨김: VISIBLE 이면 hide, 없으면 404")
  void adminHide() {
    WikiLiveMessage m = WikiLiveMessage.of(1L, "김OO", "hi");
    when(repository.findById(10L)).thenReturn(Optional.of(m));
    when(repository.findById(99L)).thenReturn(Optional.empty());

    service.adminHide(10L);
    assertThat(m.getDeletedAt()).isNotNull();

    assertThatThrownBy(() -> service.adminHide(99L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  @DisplayName("since: VISIBLE 만 조회하는 repo 쿼리 결과를 그대로 통과시킨다")
  void since_passesThroughRepositoryFilter() {
    when(repository.findAfterId(
            org.mockito.ArgumentMatchers.eq(WikiLiveMessageStatus.VISIBLE),
            org.mockito.ArgumentMatchers.eq(5L), any()))
        .thenReturn(java.util.List.of(WikiLiveMessage.of(1L, "박OO", "hello")));

    assertThat(service.since(5L)).hasSize(1);
  }
}
