package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import java.time.LocalDateTime;

/** 자동수집 연결 응답 — 관리자 API용. 내부 필드(connectionRef 등) 노출 없이 최소 정보만 포함. */
public record EasyCollectionConnectionResponse(
    Long connectionId,
    String label,
    String sourceCode,
    String sourceName,
    EasyCollectionConnectionStatus status,
    boolean hasConnectionRef,
    LocalDateTime lastSuccessAt,
    LocalDateTime createdAt
) {
  public static EasyCollectionConnectionResponse from(EasyCollectionConnection conn) {
    EasyCollectionSource source = conn.getSource();
    return new EasyCollectionConnectionResponse(
        conn.getConnectionId(),
        conn.getLabel(),
        source != null ? source.getCode() : null,
        source != null ? source.getName() : null,
        conn.getStatus(),
        conn.getConnectionRef() != null && !conn.getConnectionRef().isBlank(),
        conn.getLastSuccessAt(),
        conn.getCreatedAt());
  }
}