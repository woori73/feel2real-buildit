package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.repository.LandingVideoSlotRepository;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("LandingVideoSlotTransaction")
class LandingVideoSlotTransactionTest {

  @Test
  @DisplayName("swap/insert-race/visibility methods는 cleanup 호출자와 분리된 fresh transaction이다")
  void writeMethodsRequireNewTransactions() throws Exception {
    // given
    Method replace =
        LandingVideoSlotTransaction.class.getMethod(
            "replace",
            LandingVideoSlotKey.class,
            String.class,
            String.class,
            String.class,
            Integer.class,
            Long.class,
            Integer.class,
            Integer.class);
    Method retry =
        LandingVideoSlotTransaction.class.getMethod(
            "replaceAfterInsertRace",
            LandingVideoSlotKey.class,
            String.class,
            String.class,
            String.class,
            Integer.class,
            Long.class,
            Integer.class,
            Integer.class);
    Method visibility =
        LandingVideoSlotTransaction.class.getMethod(
            "updateVisibility", LandingVideoSlotKey.class, boolean.class);

    // when / then
    assertThat(replace.getAnnotation(Transactional.class).propagation())
        .isEqualTo(Propagation.REQUIRES_NEW);
    assertThat(retry.getAnnotation(Transactional.class).propagation())
        .isEqualTo(Propagation.REQUIRES_NEW);
    assertThat(visibility.getAnnotation(Transactional.class).propagation())
        .isEqualTo(Propagation.REQUIRES_NEW);
  }

  @Test
  @DisplayName("given hidden row when asset 교체 then pessimistic 조회 후 visibility 보존")
  void replacePreservesVisibility() {
    // given
    LandingVideoSlotRepository repository = mock(LandingVideoSlotRepository.class);
    LandingVideoSlot hidden =
        LandingVideoSlot.bundled(LandingVideoSlotKey.HERO_BACKGROUND, false);
    when(repository.findBySlotForUpdate(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(hidden));
    when(repository.saveAndFlush(hidden)).thenReturn(hidden);
    LandingVideoSlotTransaction transaction = new LandingVideoSlotTransaction(repository);

    // when
    var result =
        transaction.replace(
            LandingVideoSlotKey.HERO_BACKGROUND,
            "new.mp4",
            "new-key",
            "thumb-key",
            10,
            20L,
            720,
            1280);

    // then
    verify(repository).findBySlotForUpdate(LandingVideoSlotKey.HERO_BACKGROUND);
    assertThat(result.current().visible()).isFalse();
    assertThat(result.current().storageKey()).isEqualTo("new-key");
  }

  @Test
  @DisplayName("given row 없음 when hide then bundled row를 hidden으로 저장")
  void visibilityCreatesDefensiveBundledRow() {
    // given
    LandingVideoSlotRepository repository = mock(LandingVideoSlotRepository.class);
    when(repository.findBySlotForUpdate(LandingVideoSlotKey.PARENT_REPORT_DEMO))
        .thenReturn(Optional.empty());
    when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    LandingVideoSlotTransaction transaction = new LandingVideoSlotTransaction(repository);

    // when
    var result =
        transaction.updateVisibility(LandingVideoSlotKey.PARENT_REPORT_DEMO, false);

    // then
    assertThat(result.visible()).isFalse();
    assertThat(result.hasManagedAsset()).isFalse();
  }
}
