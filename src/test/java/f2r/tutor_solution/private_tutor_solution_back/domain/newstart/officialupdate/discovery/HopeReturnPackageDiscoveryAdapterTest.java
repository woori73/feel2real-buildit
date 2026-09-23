package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun.RunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialDiscoveryRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService.DiscoveryResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 희망리턴패키지는 공지 출처가 sbiz24 SPA 로 이전되어 어댑터가 미구현이다.
 * 미구현 상태가 SUCCESS 나 empty-success 로 기록되는 것을 차단하는 회귀 테스트.
 */
class HopeReturnPackageDiscoveryAdapterTest {

  private final HopeReturnPackageDiscoveryAdapter adapter = new HopeReturnPackageDiscoveryAdapter();

  @Test
  void discoverReportsNotImplementedInsteadOfEmptySuccess() {
    adapter.httpClient = mock(DiscoveryHttpClient.class);

    assertThatThrownBy(() -> adapter.discover(LocalDateTime.now().minusDays(1)))
        .isInstanceOf(DiscoverySourceException.class)
        .extracting(e -> ((DiscoverySourceException) e).getReason())
        .isEqualTo(DiscoverySourceException.Reason.NOT_IMPLEMENTED);
  }

  @Test
  void notImplementedSourceCannotProduceFalseSuccessRunRecord() {
    adapter.httpClient = mock(DiscoveryHttpClient.class);
    NewStartOfficialUpdateRepository updateRepository = mock(NewStartOfficialUpdateRepository.class);
    NewStartOfficialDiscoveryRunRepository runRepository = mock(NewStartOfficialDiscoveryRunRepository.class);
    NewStartOfficialUpdateService updateService = mock(NewStartOfficialUpdateService.class);
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    NewStartOfficialUpdateDiscoveryService service = new NewStartOfficialUpdateDiscoveryService(
        List.of(adapter), updateRepository, runRepository, updateService);

    DiscoveryResult result = service.runDiscoveryForSource(SourceType.HOPE_RETURN_PACKAGE, null);

    assertThat(result.isSuccess()).as("NOT_IMPLEMENTED 소스는 SUCCESS 위장 금지").isFalse();
    ArgumentCaptor<NewStartOfficialDiscoveryRun> captor = ArgumentCaptor.forClass(NewStartOfficialDiscoveryRun.class);
    verify(runRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(RunStatus.FAILED);
    verify(updateService, never()).createReview(any(), any(), any());
  }
}
