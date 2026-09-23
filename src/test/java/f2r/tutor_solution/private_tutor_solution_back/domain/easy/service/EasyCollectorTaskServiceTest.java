package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTask;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTaskStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectorTaskRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyLocalCollectorRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyCollectorTaskServiceTest {
  private final EasyLocalCollectorRepository collectors = mock(EasyLocalCollectorRepository.class);
  private final EasyCollectorTaskRepository tasks = mock(EasyCollectorTaskRepository.class);
  private EasyCollectorTaskService service;
  private EasyBusiness business;
  private EasyLocalCollector collector;

  @BeforeEach
  void setUp() {
    service = new EasyCollectorTaskService(collectors, tasks);
    business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    collector = EasyLocalCollector.builder()
        .business(business).name("PC-1").tokenHash("a".repeat(64)).build();
    ReflectionTestUtils.setField(collector, "collectorId", 5L);
    when(collectors.findById(5L)).thenReturn(Optional.of(collector));
    when(tasks.save(any())).thenAnswer(invocation -> {
      EasyCollectorTask value = invocation.getArgument(0);
      ReflectionTestUtils.setField(value, "taskId", 900L);
      return value;
    });
  }

  private LocalDate from() { return LocalDate.of(2026, 9, 15); }
  private LocalDate to() { return LocalDate.of(2026, 9, 22); }

  @Test
  void startCreatesQueuedTask() {
    EasyCollectorTask task = service.start(1L, 5L, "CARD_COMPANY_TEST", "카드 이용내역", from(), to());

    assertThat(task.getTaskId()).isEqualTo(900L);
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.STARTING);
    assertThat(task.getCollector()).isSameAs(collector);
  }

  @Test
  void startRejectsCrossBusinessCollector() {
    assertThatThrownBy(() -> service.start(2L, 5L, "CARD_COMPANY_TEST", "카드 이용내역", from(), to()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cross-business");
    verify(tasks, never()).save(any());
  }

  @Test
  void startRejectsRevokedCollector() {
    collector.revoke();
    assertThatThrownBy(() -> service.start(1L, 5L, "CARD_COMPANY_TEST", "카드 이용내역", from(), to()))
        .isInstanceOf(IllegalArgumentException.class);
    verify(tasks, never()).save(any());
  }

  @Test
  void duplicateTaskIsRejected() {
    when(tasks.existsByCollector_CollectorIdAndSourceCodeAndDataTypeAndRequestedFromAndRequestedToAndStatusNotIn(
        eq(5L), eq("CARD_COMPANY_TEST"), eq("카드 이용내역"), eq(from()), eq(to()), any()))
        .thenReturn(true);

    assertThatThrownBy(() -> service.start(1L, 5L, "CARD_COMPANY_TEST", "카드 이용내역", from(), to()))
        .isInstanceOf(IllegalArgumentException.class);
    verify(tasks, never()).save(any());
  }

  @Test
  void claimNextClaimsOldestStartingTaskAndOpensBrowserState() {
    EasyCollectorTask task = EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역").requestedFrom(from()).requestedTo(to()).build();
    ReflectionTestUtils.setField(task, "taskId", 901L);
    when(tasks.findFirstByCollector_CollectorIdAndStatusOrderByTaskIdAsc(5L, EasyCollectorTaskStatus.STARTING))
        .thenReturn(Optional.of(task));

    var claimed = service.claimNext(5L);

    assertThat(claimed).isPresent();
    assertThat(claimed.get().getStatus()).isEqualTo(EasyCollectorTaskStatus.BROWSER_OPEN);
  }

  @Test
  void claimNextReturnsEmptyWhenNoQueuedTask() {
    when(tasks.findFirstByCollector_CollectorIdAndStatusOrderByTaskIdAsc(5L, EasyCollectorTaskStatus.STARTING))
        .thenReturn(Optional.empty());
    assertThat(service.claimNext(5L)).isEmpty();
  }

  @Test
  void advanceRejectsCrossCollectorAccess() {
    EasyLocalCollector otherCollector = EasyLocalCollector.builder()
        .business(business).name("PC-2").tokenHash("b".repeat(64)).build();
    ReflectionTestUtils.setField(otherCollector, "collectorId", 6L);
    EasyCollectorTask task = EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역").requestedFrom(from()).requestedTo(to()).build();
    ReflectionTestUtils.setField(task, "taskId", 902L);
    when(tasks.findById(902L)).thenReturn(Optional.of(task));

    assertThatThrownBy(() -> service.advance(902L, 6L, EasyCollectorTaskStatus.BROWSER_OPEN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cross-collector");
  }

  @Test
  void fullLifecycleThroughAgentMethodsCompletesWithMetadata() {
    EasyCollectorTask task = EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역").requestedFrom(from()).requestedTo(to()).build();
    ReflectionTestUtils.setField(task, "taskId", 903L);
    when(tasks.findById(903L)).thenReturn(Optional.of(task));

    service.advance(903L, 5L, EasyCollectorTaskStatus.BROWSER_OPEN);
    service.advance(903L, 5L, EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
    service.advance(903L, 5L, EasyCollectorTaskStatus.AUTHENTICATED);
    service.advance(903L, 5L, EasyCollectorTaskStatus.COLLECTING);
    service.advance(903L, 5L, EasyCollectorTaskStatus.DOWNLOADING);
    EasyCollectorTask completed = service.complete(903L, 5L, "test.csv", 10L, "c".repeat(64));

    assertThat(completed.getStatus()).isEqualTo(EasyCollectorTaskStatus.COMPLETED);
    assertThat(completed.getDownloadedFilename()).isEqualTo("test.csv");
  }

  @Test
  void failMarksTaskFailedWithReason() {
    EasyCollectorTask task = EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역").requestedFrom(from()).requestedTo(to()).build();
    ReflectionTestUtils.setField(task, "taskId", 904L);
    when(tasks.findById(904L)).thenReturn(Optional.of(task));

    EasyCollectorTask failed = service.fail(904L, 5L, "site changed layout");

    assertThat(failed.getStatus()).isEqualTo(EasyCollectorTaskStatus.FAILED);
    assertThat(failed.getErrorSummary()).isEqualTo("site changed layout");
  }

  @Test
  void cancelRejectsCrossBusinessRequest() {
    EasyCollectorTask task = EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역").requestedFrom(from()).requestedTo(to()).build();
    ReflectionTestUtils.setField(task, "taskId", 905L);
    when(tasks.findById(905L)).thenReturn(Optional.of(task));

    assertThatThrownBy(() -> service.cancel(905L, 2L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cross-business");
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.STARTING);
  }

  @Test
  void cancelSucceedsForOwningBusiness() {
    EasyCollectorTask task = EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역").requestedFrom(from()).requestedTo(to()).build();
    ReflectionTestUtils.setField(task, "taskId", 906L);
    when(tasks.findById(906L)).thenReturn(Optional.of(task));

    EasyCollectorTask cancelled = service.cancel(906L, 1L);

    assertThat(cancelled.getStatus()).isEqualTo(EasyCollectorTaskStatus.CANCELLED);
  }

  @Test
  void listRejectsMissingBusinessId() {
    assertThatThrownBy(() -> service.list(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
