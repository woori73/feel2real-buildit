package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTask;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTaskStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyCollectorTaskDomainTest {

  private EasyCollectorTask task() {
    EasyBusiness business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    EasyLocalCollector collector = EasyLocalCollector.builder()
        .business(business).name("PC-1").tokenHash("a".repeat(64)).build();
    ReflectionTestUtils.setField(collector, "collectorId", 5L);
    return EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역")
        .requestedFrom(LocalDate.of(2026, 9, 15)).requestedTo(LocalDate.of(2026, 9, 22)).build();
  }

  @Test
  void newTaskStartsAtStarting() {
    assertThat(task().getStatus()).isEqualTo(EasyCollectorTaskStatus.STARTING);
  }

  @Test
  void happyPathAdvancesInOrder() {
    EasyCollectorTask task = task();
    task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN);
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.BROWSER_OPEN);
    assertThat(task.getStartedAt()).isNotNull();
    task.advanceTo(EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
    task.advanceTo(EasyCollectorTaskStatus.AUTHENTICATED);
    task.advanceTo(EasyCollectorTaskStatus.COLLECTING);
    task.advanceTo(EasyCollectorTaskStatus.DOWNLOADING);
    task.complete("card-usage-2026-09-15_22.csv", 4096L, "b".repeat(64));
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.COMPLETED);
    assertThat(task.getDownloadedFilename()).isEqualTo("card-usage-2026-09-15_22.csv");
    assertThat(task.getDownloadedSize()).isEqualTo(4096L);
    assertThat(task.getDownloadedSha256()).isEqualTo("b".repeat(64));
    assertThat(task.getDownloadedAt()).isNotNull();
    assertThat(task.getCompletedAt()).isNotNull();
  }

  @Test
  void skippingAStepIsRejected() {
    EasyCollectorTask task = task();
    assertThatThrownBy(() -> task.advanceTo(EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void movingBackwardIsRejected() {
    EasyCollectorTask task = task();
    task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN);
    task.advanceTo(EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
    assertThatThrownBy(() -> task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void advancingAfterCompletionIsRejected() {
    EasyCollectorTask task = task();
    task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN);
    task.advanceTo(EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
    task.advanceTo(EasyCollectorTaskStatus.AUTHENTICATED);
    task.advanceTo(EasyCollectorTaskStatus.COLLECTING);
    task.advanceTo(EasyCollectorTaskStatus.DOWNLOADING);
    task.complete("x.csv", 10L, "c".repeat(64));
    assertThatThrownBy(() -> task.advanceTo(EasyCollectorTaskStatus.COLLECTING))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> task.fail("late"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(task::cancel)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failCanFireFromAnyNonTerminalState() {
    EasyCollectorTask task = task();
    task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN);
    task.advanceTo(EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
    task.fail("site changed layout");
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.FAILED);
    assertThat(task.getErrorSummary()).isEqualTo("site changed layout");
    assertThat(task.getCompletedAt()).isNotNull();
  }

  @Test
  void cancelCanFireFromAnyNonTerminalState() {
    EasyCollectorTask task = task();
    task.cancel();
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.CANCELLED);
    assertThat(task.getCompletedAt()).isNotNull();
  }

  @Test
  void completeRejectsMissingMetadataWithoutCorruptingTaskState() {
    EasyCollectorTask task = atDownloading();
    assertThatThrownBy(() -> task.complete("", 10L, "d".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(task.getStatus()).as("failed validation must not mutate status")
        .isEqualTo(EasyCollectorTaskStatus.DOWNLOADING);

    assertThatThrownBy(() -> task.complete("x.csv", 0L, "d".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> task.complete("x.csv", 10L, "not-a-hash"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.DOWNLOADING);

    task.complete("x.csv", 10L, "d".repeat(64));
    assertThat(task.getStatus()).isEqualTo(EasyCollectorTaskStatus.COMPLETED);
  }

  private EasyCollectorTask atDownloading() {
    EasyCollectorTask task = task();
    task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN);
    task.advanceTo(EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
    task.advanceTo(EasyCollectorTaskStatus.AUTHENTICATED);
    task.advanceTo(EasyCollectorTaskStatus.COLLECTING);
    task.advanceTo(EasyCollectorTaskStatus.DOWNLOADING);
    return task;
  }

  @Test
  void requestedFromAfterToIsRejected() {
    EasyBusiness business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    EasyLocalCollector collector = EasyLocalCollector.builder()
        .business(business).name("PC-1").tokenHash("a".repeat(64)).build();
    assertThatThrownBy(() -> EasyCollectorTask.builder()
        .collector(collector).business(business).sourceCode("CARD_COMPANY_TEST")
        .dataType("카드 이용내역")
        .requestedFrom(LocalDate.of(2026, 9, 22)).requestedTo(LocalDate.of(2026, 9, 15)).build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
