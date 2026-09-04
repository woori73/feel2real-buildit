package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiDownloadLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("WikiDownloadRecordingService — Wiki 자료 다운로드 기록")
class WikiDownloadRecordingServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private WikiDownloadLogRepository downloadRepository;
  private UserRepository userRepository;
  private WikiDownloadRecordingService service;

  @BeforeEach
  void setUp() {
    downloadRepository = mock(WikiDownloadLogRepository.class);
    userRepository = mock(UserRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-05T04:00:00Z"), SEOUL);
    service = new WikiDownloadRecordingService(downloadRepository, userRepository, clock);
  }

  @Test
  @DisplayName("비회원 다운로드: role=GUEST, displayName=비회원")
  void record_guestDownload() {
    service.record(WikiMaterialCategory.ORIGINAL, 3L, "9월 모의고사 원본", null, null);

    ArgumentCaptor<WikiDownloadLog> captor = ArgumentCaptor.forClass(WikiDownloadLog.class);
    verify(downloadRepository).save(captor.capture());
    WikiDownloadLog saved = captor.getValue();
    assertThat(saved.getMaterialCategory()).isEqualTo(WikiMaterialCategory.ORIGINAL);
    assertThat(saved.getMaterialId()).isEqualTo(3L);
    assertThat(saved.getMaterialTitle()).isEqualTo("9월 모의고사 원본");
    assertThat(saved.getRole()).isEqualTo("GUEST");
    assertThat(saved.getDisplayName()).isEqualTo("비회원");
    assertThat(saved.getUserId()).isNull();
    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("회원(STUDENT) 다운로드: users.name(실명) 스냅샷 저장")
  void record_studentDownload_snapshotsRealName() {
    User student = User.builder()
        .loginId("stu01").password("x").name("김민수").nickname("민수").role(Role.STUDENT).build();
    when(userRepository.findById(11L)).thenReturn(Optional.of(student));

    service.record(WikiMaterialCategory.LEARNING, 9L, "고2 어휘", 11L, "STUDENT");

    ArgumentCaptor<WikiDownloadLog> captor = ArgumentCaptor.forClass(WikiDownloadLog.class);
    verify(downloadRepository).save(captor.capture());
    WikiDownloadLog saved = captor.getValue();
    assertThat(saved.getMaterialCategory()).isEqualTo(WikiMaterialCategory.LEARNING);
    assertThat(saved.getDisplayName()).isEqualTo("김민수");
    assertThat(saved.getRole()).isEqualTo("STUDENT");
    assertThat(saved.getUserId()).isEqualTo(11L);
  }

  @Test
  @DisplayName("제목이 비어있으면 '제목 없음'으로 대체한다")
  void record_blankTitle_fallsBackToPlaceholder() {
    service.record(WikiMaterialCategory.ANALYSIS, 5L, "  ", null, null);

    ArgumentCaptor<WikiDownloadLog> captor = ArgumentCaptor.forClass(WikiDownloadLog.class);
    verify(downloadRepository).save(captor.capture());
    assertThat(captor.getValue().getMaterialTitle()).isEqualTo("제목 없음");
  }

  @Test
  @DisplayName("저장 중 예외가 발생해도 호출자에게 전파하지 않는다(다운로드 응답 보호)")
  void record_swallowsRepositoryException() {
    when(downloadRepository.save(any())).thenThrow(new RuntimeException("db down"));

    service.record(WikiMaterialCategory.ORIGINAL, 1L, "t", null, null);

    verify(downloadRepository, times(1)).save(any());
  }
}
