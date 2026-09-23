package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import java.time.LocalDateTime;

public record EasyLocalCollectorResponse(
    Long collectorId,
    Long businessId,
    String name,
    String status,
    LocalDateTime lastHeartbeatAt
) {
  public static EasyLocalCollectorResponse from(EasyLocalCollector collector) {
    return new EasyLocalCollectorResponse(
        collector.getCollectorId(),
        collector.getBusiness().getBusinessId(),
        collector.getName(),
        collector.getStatus().name(),
        collector.getLastHeartbeatAt());
  }
}
