package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EasyCollectionManagementService {

  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionCheckpointRepository checkpointRepository;
  private final EasyCollectionProviderReadinessPolicy readinessPolicy;
  private final EasyCollectionSchedulerService schedulerService;

  @Transactional(readOnly = true)
  public List<EasyCollectionManagementResponse> listConnections(Long businessId) {
    requireBusinessId(businessId);
    return connectionRepository.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(businessId)
        .stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<EasyCollectionCheckpointResponse> listCheckpoints(
      Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    return checkpointRepository.findAllByConnection_ConnectionIdOrderByDatasetTypeAsc(
        connection.getConnectionId()).stream()
        .map(EasyCollectionCheckpointResponse::from)
        .toList();
  }

  @Transactional
  public EasyCollectionManagementResponse disable(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    connection.disable();
    return toResponse(connection);
  }

  @Transactional
  public EasyCollectionManagementResponse enable(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    EasyCollectionProviderReadiness readiness = readinessForActivation(connection);
    if (readiness != EasyCollectionProviderReadiness.READY) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Collection provider is not ready for activation: " + readiness.name());
    }
    connection.enable();
    return toResponse(connection);
  }

  /**
   * All safety checks complete before delegating. The delegate preserves Phase 3A's external-fetch
   * transaction boundary and records MANUAL provenance.
   */
  public EasyCollectionRunResponse triggerManual(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    EasyCollectionProviderReadiness readiness = readiness(connection);
    if (readiness != EasyCollectionProviderReadiness.READY) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Collection provider is not ready: " + readiness.name());
    }
    if (runRepository.existsByConnection_ConnectionIdAndStatus(
        connectionId, EasyCollectionRunStatus.RUNNING)) {
      throw new IllegalStateException("A collection run is already in progress.");
    }
    return schedulerService.triggerManualCollection(businessId, connectionId);
  }

  public EasyCollectionProviderReadiness readiness(EasyCollectionConnection connection) {
    if (connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      return EasyCollectionProviderReadiness.NEEDS_REAUTH;
    }
    if (connection.getStatus() != EasyCollectionConnectionStatus.ACTIVE
        || !connection.getSource().isActive()) {
      return EasyCollectionProviderReadiness.DISABLED;
    }
    return configuredAndVerified(connection);
  }

  private EasyCollectionProviderReadiness readinessForActivation(
      EasyCollectionConnection connection) {
    if (connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      return EasyCollectionProviderReadiness.NEEDS_REAUTH;
    }
    if (!connection.getSource().isActive()) {
      return EasyCollectionProviderReadiness.DISABLED;
    }
    return configuredAndVerified(connection);
  }

  private EasyCollectionProviderReadiness configuredAndVerified(
      EasyCollectionConnection connection) {
    if (connection.getConnectionRef() == null || connection.getConnectionRef().isBlank()) {
      return EasyCollectionProviderReadiness.NOT_CONFIGURED;
    }
    if (!readinessPolicy.isVerified(connection.getSource())) {
      return EasyCollectionProviderReadiness.UNVERIFIED;
    }
    return EasyCollectionProviderReadiness.READY;
  }

  private EasyCollectionManagementResponse toResponse(EasyCollectionConnection connection) {
    EasyCollectionRun latest = runRepository
        .findFirstByConnection_ConnectionIdOrderByRunIdDesc(connection.getConnectionId())
        .orElse(null);
    boolean hasCheckpoint = checkpointRepository
        .existsByConnection_ConnectionId(connection.getConnectionId());
    return EasyCollectionManagementResponse.from(
        connection, latest, readiness(connection), hasCheckpoint);
  }

  private EasyCollectionConnection requireOwnedConnection(Long businessId, Long connectionId) {
    requireBusinessId(businessId);
    EasyCollectionConnection connection = connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Collection connection not found."));
    if (!businessId.equals(connection.getBusiness().getBusinessId())) {
      throw new IllegalArgumentException("Business does not own this collection connection.");
    }
    return connection;
  }

  private static void requireBusinessId(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("Business is required.");
    }
  }
}
