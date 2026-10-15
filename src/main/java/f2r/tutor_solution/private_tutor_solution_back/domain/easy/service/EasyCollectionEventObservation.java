package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import java.time.LocalDateTime;

/**
 * Provider-neutral event input. Partial-cancellation arithmetic is intentionally absent until an
 * official provider contract defines it.
 */
public record EasyCollectionEventObservation(
    String externalEventId,
    String economicKey,
    String originalExternalEventId,
    LocalDateTime occurredAt,
    String providerCursor,
    String providerVersion,
    EasyCollectionRawRecord rawRecord
) {}
