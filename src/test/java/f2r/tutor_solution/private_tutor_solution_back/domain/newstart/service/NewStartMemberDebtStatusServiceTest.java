package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberDebtStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberDebtStatusRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService.DebtStatusRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService.DebtStatusView;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NewStartMemberDebtStatusService given/when/then 단위테스트 — 1:1 upsert, allowlist 단계
 * 검증, 메모 길이 검증, 파생 상태(NOT_STARTED/IN_PROGRESS/COMPLETED) 계산을 다룬다.
 * 대상 회원은 항상 호출 측이 넘긴 principal userId뿐이며 요청에 userId 필드가 존재하지 않는다.
 */
@DisplayName("NewStartMemberDebtStatusService — 채무상황 upsert/파생상태 계약")
class NewStartMemberDebtStatusServiceTest {

  private NewStartMemberDebtStatusRepository repository;
  private NewStartMemberDebtStatusService service;

  @BeforeEach
  void setUp() {
    repository = mock(NewStartMemberDebtStatusRepository.class);
    service = new NewStartMemberDebtStatusService(repository);
  }

  private static DebtStatusRequest allFlags(String summary, String stage) {
    return new DebtStatusRequest(true, true, true, true, true, true, summary, stage);
  }

  private static DebtStatusRequest noFlags(String summary, String stage) {
    return new DebtStatusRequest(false, false, false, false, false, false, summary, stage);
  }

  @Test
  @DisplayName("given 저장된 row가 없음 when findMine then 빈 상태(NOT_STARTED)를 반환하고 DB를 쓰지 않는다")
  void findMineReturnsEmptyViewWhenNoRow() {
    when(repository.findById(7L)).thenReturn(Optional.empty());

    DebtStatusView view = service.findMine(7L);

    assertThat(view.status()).isEqualTo("NOT_STARTED");
    assertThat(view.credit4uChecked()).isFalse();
    assertThat(view.debtSummary()).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("given 기존 row 없음 when save 첫 저장 then 새 1:1 row로 생성되고 파생상태가 플래그를 반영한다")
  void saveCreatesRowForFirstTime() {
    when(repository.findById(7L)).thenReturn(Optional.empty());
    when(repository.save(any(NewStartMemberDebtStatus.class))).thenAnswer(inv -> inv.getArgument(0));

    DebtStatusView view = service.save(7L, allFlags("신용도판단정보에 연체 등록 확인", "정보 확인 중"));

    ArgumentCaptor<NewStartMemberDebtStatus> captor =
        ArgumentCaptor.forClass(NewStartMemberDebtStatus.class);
    verify(repository).save(captor.capture());
    NewStartMemberDebtStatus saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(7L);
    assertThat(saved.allChecked()).isTrue();
    assertThat(saved.getDebtSummary()).isEqualTo("신용도판단정보에 연체 등록 확인");
    assertThat(view.status()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("given 기존 row 있음 when save then 새 row 없이 같은 row를 갱신한다(중복 저장 금지)")
  void saveUpdatesExistingRowWithoutDuplicate() {
    NewStartMemberDebtStatus existing = NewStartMemberDebtStatus.builder().userId(7L).build();
    existing.updateProgress(false, false, false, false, false, false, "메모", null);
    when(repository.findById(7L)).thenReturn(Optional.of(existing));
    when(repository.save(any(NewStartMemberDebtStatus.class))).thenAnswer(inv -> inv.getArgument(0));

    DebtStatusView view = service.save(7L, allFlags("업데이트된 메모", "기타"));

    assertThat(view.status()).isEqualTo("COMPLETED");
    ArgumentCaptor<NewStartMemberDebtStatus> captor =
        ArgumentCaptor.forClass(NewStartMemberDebtStatus.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue()).isSameAs(existing);
    assertThat(captor.getValue().getDebtSummary()).isEqualTo("업데이트된 메모");
  }

  @Test
  @DisplayName("given 일부 플래그만 확인 when save then IN_PROGRESS, 0개면 NOT_STARTED로 파생된다")
  void derivesInProgressAndNotStarted() {
    when(repository.findById(7L)).thenReturn(Optional.empty());
    when(repository.save(any(NewStartMemberDebtStatus.class))).thenAnswer(inv -> inv.getArgument(0));

    DebtStatusView partial = service.save(7L,
        new DebtStatusRequest(true, true, false, false, false, false, null, null));
    assertThat(partial.status()).isEqualTo("IN_PROGRESS");

    DebtStatusView none = service.save(7L, noFlags("", "  "));
    assertThat(none.status()).isEqualTo("NOT_STARTED");
    assertThat(none.debtSummary()).isNull();
    assertThat(none.stage()).isNull();
  }

  @Test
  @DisplayName("given 6개 전부 확인 but 메모·단계 없음 when save then 정리 완료로 단정하지 않는다(IN_PROGRESS)")
  void allFlagsWithoutWrittenContentIsNotCompleted() {
    when(repository.findById(7L)).thenReturn(Optional.empty());
    when(repository.save(any(NewStartMemberDebtStatus.class))).thenAnswer(inv -> inv.getArgument(0));

    DebtStatusView view = service.save(7L, allFlags(null, null));

    assertThat(view.status()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @DisplayName("given allowlist 밖 단계 when save then 400성 IllegalArgumentException, 저장은 일어나지 않는다")
  void rejectsStageOutsideAllowlist() {
    assertThatThrownBy(() -> service.save(7L, noFlags("메모", "이미 확정 받음")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("진행 단계");
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("given 1000자 초과 메모 when save then 거부되고 원문을 자르지 않는다")
  void rejectsOversizedSummary() {
    String tooLong = "가".repeat(1001);
    assertThatThrownBy(() -> service.save(7L, noFlags(tooLong, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1000");
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("given 메모 앞뒤 공백 when save then trim해 저장한다")
  void trimsSummary() {
    when(repository.findById(7L)).thenReturn(Optional.empty());
    when(repository.save(any(NewStartMemberDebtStatus.class))).thenAnswer(inv -> inv.getArgument(0));

    DebtStatusView view = service.save(7L, noFlags("  A캐피탈 300만원 연체  ", null));

    assertThat(view.debtSummary()).isEqualTo("A캐피탈 300만원 연체");
  }
}
