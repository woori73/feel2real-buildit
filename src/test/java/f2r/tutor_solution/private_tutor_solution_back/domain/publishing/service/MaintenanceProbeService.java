package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;
import java.util.concurrent.CompletableFuture;
import org.springframework.transaction.annotation.Transactional;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance.PublishingMaintenanceGate;
/** Test-only bean in the actual advised package. No DB or existing business QA runs. */
public class MaintenanceProbeService {
  private final PublishingMaintenanceGate gate;
  public MaintenanceProbeService(PublishingMaintenanceGate gate){this.gate=gate;}
  @Transactional public void commitDuringDrain(){gate.drain();}
  public CompletableFuture<String> stage(){return new CompletableFuture<>();}
}
