package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists provider observations and advances a dataset checkpoint atomically. No method in this
 * service creates import rows, transactions, journal entries, or accounting income.
 */
@Service
@RequiredArgsConstructor
public class EasyCollectionLifecycleService {

  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyEconomicSaleRepository saleRepository;
  private final EasyCollectionEventRepository eventRepository;
  private final EasyCollectionCheckpointRepository checkpointRepository;

  @Transactional(readOnly = true)
  public Optional<EasyCollectionCheckpoint> checkpoint(Long connectionId,
      EasyCollectionDatasetType datasetType) {
    return checkpointRepository.findByConnection_ConnectionIdAndDatasetType(
        connectionId, requireDataset(datasetType));
  }

  /**
   * This transaction is the commit boundary: all events/relations must persist before the cursor
   * can advance. Any exception rolls back both observations and checkpoint.
   */
  @Transactional
  public int recordSuccessfulBatch(Long connectionId, EasyCollectionDatasetType datasetType,
      List<EasyCollectionEventObservation> observations, String nextCursor,
      String nextCheckpoint) {
    EasyCollectionDatasetType dataset = requireDataset(datasetType);
    EasyCollectionConnection connection = connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Collection connection not found."));
    List<EasyCollectionEventObservation> safe =
        observations == null ? List.of() : observations;
    int created = 0;

    for (EasyCollectionEventObservation observation : safe) {
      validate(observation);
      String eventKey = eventKey(dataset, observation);
      if (eventRepository.findByConnection_ConnectionIdAndEventKey(connectionId, eventKey)
          .isPresent()) {
        continue;
      }
      EasyEconomicSale sale = saleRepository
          .findByConnection_ConnectionIdAndEconomicKey(connectionId, observation.economicKey())
          .orElseGet(() -> saleRepository.save(EasyEconomicSale.builder()
              .connection(connection)
              .business(connection.getBusiness())
              .economicKey(observation.economicKey())
              .build()));

      EasyCollectionEvent original = null;
      if (observation.originalExternalEventId() != null
          && !observation.originalExternalEventId().isBlank()) {
        original = eventRepository
            .findFirstByConnection_ConnectionIdAndExternalEventIdOrderByEventIdAsc(
                connectionId, observation.originalExternalEventId())
            .orElseThrow(() -> new EasyCollectionException(
                EasyCollectionErrorCode.INVALID_DATA,
                "Referenced original provider event was not observed."));
      }

      EasyCollectionEvent event = EasyCollectionEvent.builder()
          .connection(connection)
          .business(connection.getBusiness())
          .economicSale(sale)
          .rawRecord(observation.rawRecord())
          .originalEvent(original)
          .datasetType(dataset)
          .eventKey(eventKey)
          .externalEventId(observation.externalEventId())
          .occurredAt(observation.occurredAt())
          .providerCursor(observation.providerCursor())
          .providerVersion(observation.providerVersion())
          .build();
      eventRepository.save(event);
      created++;

      if (dataset == EasyCollectionDatasetType.CANCELLATION) {
        if (original == null) {
          throw new EasyCollectionException(EasyCollectionErrorCode.INVALID_DATA,
              "Cancellation must reference an original provider event.");
        }
        original.markReversed();
        original.getEconomicSale().markReversed();
      } else if (dataset == EasyCollectionDatasetType.CORRECTION && original != null) {
        original.markSuperseded();
        original.getEconomicSale().markSuperseded();
      }
    }

    EasyCollectionCheckpoint checkpoint = checkpointRepository
        .findByConnection_ConnectionIdAndDatasetType(connectionId, dataset)
        .orElseGet(() -> EasyCollectionCheckpoint.builder()
            .connection(connection)
            .datasetType(dataset)
            .build());
    checkpoint.advance(nextCursor, nextCheckpoint, LocalDateTime.now());
    checkpointRepository.save(checkpoint);
    return created;
  }

  private static EasyCollectionDatasetType requireDataset(EasyCollectionDatasetType dataset) {
    if (dataset == null || dataset == EasyCollectionDatasetType.UNKNOWN) {
      throw new EasyCollectionException(EasyCollectionErrorCode.INVALID_DATA,
          "A concrete provider dataset type is required for lifecycle collection.");
    }
    return dataset;
  }

  private static void validate(EasyCollectionEventObservation observation) {
    if (observation == null || observation.economicKey() == null
        || observation.economicKey().isBlank()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.INVALID_DATA,
          "Economic sale key is required.");
    }
  }

  private static String eventKey(EasyCollectionDatasetType dataset,
      EasyCollectionEventObservation observation) {
    if (observation.externalEventId() != null && !observation.externalEventId().isBlank()) {
      return dataset.name() + ":EXT:" + observation.externalEventId().trim();
    }
    String material = dataset.name() + "|" + observation.economicKey() + "|"
        + Objects.toString(observation.occurredAt(), "") + "|"
        + Objects.toString(observation.providerVersion(), "");
    return dataset.name() + ":HASH:" + sha256(material);
  }

  private static String sha256(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
