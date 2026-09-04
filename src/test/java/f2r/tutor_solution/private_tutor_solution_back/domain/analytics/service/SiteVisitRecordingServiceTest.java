package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.SitePageVisitRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("SiteVisitRecordingService — F2R 전체/Wiki 방문 기록")
class SiteVisitRecordingServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private SitePageVisitRepository visitRepository;
  private UserRepository userRepository;
  private SiteVisitRecordingService service;

  @BeforeEach
  void setUp() {
    visitRepository = mock(SitePageVisitRepository.class);
    userRepository = mock(UserRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-05T04:00:00Z"), SEOUL);  // 13:00 KST
    service = new SiteVisitRecordingService(visitRepository, userRepository, clock);
  }

  @Test
  @DisplayName("비로그인 방문: role=GUEST, displayName=비회원, userId=null 로 저장")
  void record_guestVisit() {
    service.record("/wiki", true, "S:session-1", null, null);

    ArgumentCaptor<SitePageVisit> captor = ArgumentCaptor.forClass(SitePageVisit.class);
    verify(visitRepository).save(captor.capture());
    SitePageVisit saved = captor.getValue();
    assertThat(saved.getRole()).isEqualTo("GUEST");
    assertThat(saved.getDisplayName()).isEqualTo("비회원");
    assertThat(saved.getUserId()).isNull();
    assertThat(saved.isWiki()).isTrue();
    assertThat(saved.getPath()).isEqualTo("/wiki");
    assertThat(saved.getVisitorKey()).isEqualTo("S:session-1");
    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("로그인 방문: users.name(실명)을 displayName 스냅샷으로 저장 — nickname/loginId 아님")
  void record_loggedInVisit_snapshotsRealName() {
    User tutor = User.builder()
        .loginId("tutor01").password("x").name("박정민").nickname("쌤쌤").role(Role.TUTOR).build();
    when(userRepository.findById(7L)).thenReturn(Optional.of(tutor));

    service.record("/wiki/analysis", true, "U:7", 7L, "TUTOR");

    ArgumentCaptor<SitePageVisit> captor = ArgumentCaptor.forClass(SitePageVisit.class);
    verify(visitRepository).save(captor.capture());
    SitePageVisit saved = captor.getValue();
    assertThat(saved.getRole()).isEqualTo("TUTOR");
    assertThat(saved.getDisplayName()).isEqualTo("박정민");
    assertThat(saved.getUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("F2R 일반 페이지(Wiki 아님)는 wiki=false 로 저장")
  void record_nonWikiPage() {
    service.record("/community", false, "S:session-2", null, null);

    ArgumentCaptor<SitePageVisit> captor = ArgumentCaptor.forClass(SitePageVisit.class);
    verify(visitRepository).save(captor.capture());
    assertThat(captor.getValue().isWiki()).isFalse();
  }

  @Test
  @DisplayName("저장 중 예외가 발생해도 호출자에게 전파하지 않는다(페이지 응답 보호)")
  void record_swallowsRepositoryException() {
    when(visitRepository.save(any())).thenThrow(new RuntimeException("db down"));

    service.record("/wiki", true, "S:session-3", null, null);

    verify(visitRepository, times(1)).save(any());
  }
}
