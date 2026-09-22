package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialDiscoveryRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 운영 스케줄러 계약: 11:00/21:00 Asia/Seoul, 기본 DISABLED.
 * 머지만으로 production 자동 실행되어서는 안 되며 config property 로 명시 enable 시에만 동작한다.
 */
class NewStartOfficialUpdateDiscoverySchedulerTest {

  private final NewStartOfficialUpdateRepository updateRepository = mock(NewStartOfficialUpdateRepository.class);
  private final NewStartOfficialDiscoveryRunRepository runRepository = mock(NewStartOfficialDiscoveryRunRepository.class);
  private final NewStartOfficialUpdateService updateService = mock(NewStartOfficialUpdateService.class);

  private static class NoopAdapter implements OfficialUpdateDiscoveryAdapter {
    @Override
    public SourceType getSourceType() {
      return SourceType.NEWSTART_FUND;
    }

    @Override
    public String getSourceName() {
      return "새출발기금";
    }

    @Override
    public List<DiscoveredCandidate> discover(LocalDateTime since) {
      return List.of();
    }

    @Override
    public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
      return candidate;
    }

    @Override
    public RelevancePriority evaluateRelevance(DiscoveredCandidate candidate) {
      return RelevancePriority.NORMAL;
    }

    @Override
    public String buildDedupKey(DiscoveredCandidate candidate) {
      return "DOC:NEWSTART_FUND:NOOP";
    }
  }

  private NewStartOfficialUpdateDiscoveryService service() {
    return new NewStartOfficialUpdateDiscoveryService(List.of(new NoopAdapter()), updateRepository, runRepository,
        updateService);
  }

  @Test
  void morningCronIs1100InAsiaSeoul() throws Exception {
    Method method = NewStartOfficialUpdateDiscoveryService.class.getMethod("scheduledDiscoveryMorning");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);
    assertThat(scheduled).isNotNull();
    assertThat(scheduled.cron()).isEqualTo("0 0 11 * * *");
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void eveningCronIs2100InAsiaSeoul() throws Exception {
    Method method = NewStartOfficialUpdateDiscoveryService.class.getMethod("scheduledDiscoveryEvening");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);
    assertThat(scheduled).isNotNull();
    assertThat(scheduled.cron()).isEqualTo("0 0 21 * * *");
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void schedulerFlagIsBoundToConfigPropertyDefaultingToFalse() throws Exception {
    Field field = NewStartOfficialUpdateDiscoveryService.class.getDeclaredField("schedulerEnabled");
    Value value = field.getAnnotation(Value.class);
    assertThat(value).as("스케줄러는 config property 로만 활성화되어야 한다").isNotNull();
    assertThat(value.value()).isEqualTo("${app.newstart.discovery.scheduler-enabled:false}");
  }

  @Test
  void applicationYamlShipsSchedulerDisabled() throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/application.yaml")) {
      assertThat(in).isNotNull();
      String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(yaml).contains("scheduler-enabled: false");
    }
  }

  @Test
  void scheduledRunsAreNoOpsWhileDisabledByDefault() {
    NewStartOfficialUpdateDiscoveryService svc = service();
    assertThat(svc.isSchedulerEnabled()).isFalse();

    svc.scheduledDiscoveryMorning();
    svc.scheduledDiscoveryEvening();

    verifyNoInteractions(runRepository, updateService);
  }

  @Test
  void scheduledRunsExecuteOnceFlagIsExplicitlyEnabled() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    NewStartOfficialUpdateDiscoveryService svc = service();
    svc.setSchedulerEnabled(true);

    svc.scheduledDiscoveryMorning();

    verify(runRepository, org.mockito.Mockito.atLeastOnce()).save(any(NewStartOfficialDiscoveryRun.class));
    verify(updateService, never()).createPublished(any(), any(), any());
  }
}
