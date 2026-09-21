package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-neutral business-card review foundation.
 * No method writes easy_transactions or journal entries; admin confirmation/posting remains a
 * separate future boundary. Settlement allocation reduces liability only and never expense.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyBusinessCardAccountingService {
  private final EasyBusinessRepository businessRepository;
  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCardInstrumentRepository instrumentRepository;
  private final EasyCardEventRepository eventRepository;
  private final EasyCardPayableRepository payableRepository;
  private final EasyCardBillingGroupRepository billingRepository;
  private final EasyCardSettlementRepository settlementRepository;
  private final EasyCardSettlementAllocationRepository allocationRepository;

  public record PurchaseCommand(Long businessId, Long connectionId, Long instrumentId,
                                String providerCode, String externalId,
                                LocalDateTime occurredAt, long amount, String currency,
                                String approvalReference) {}
  public record SettlementCommand(Long businessId, Long connectionId, Long instrumentId,
                                  String providerCode, String externalId, LocalDate paidOn,
                                  long amount, String currency, String bankTransactionRef) {}
  public record EventResult(EasyCardEvent event, EasyCardPayable payable, boolean duplicate) {}
  public record SettlementResult(EasyCardSettlement settlement, boolean duplicate) {}
  public record AllocationResult(EasyCardSettlementAllocation allocation, boolean duplicate) {}

  @Transactional
  public EasyCardInstrument registerInstrument(Long businessId, Long connectionId,
      String providerCode, String issuer, String providerInstrumentToken,
      String maskedIdentifier, String last4) {
    EasyBusiness business = business(businessId);
    EasyCollectionConnection connection = connection(connectionId, businessId);
    String tokenHash = sha256(required(providerInstrumentToken, "providerInstrumentToken"));
    return instrumentRepository
        .findByBusiness_BusinessIdAndProviderCodeAndProviderInstrumentTokenHash(
            businessId, required(providerCode, "providerCode"), tokenHash)
        .orElseGet(() -> instrumentRepository.save(EasyCardInstrument.builder()
            .business(business).connection(connection).providerCode(providerCode).issuer(issuer)
            .providerInstrumentTokenHash(tokenHash).maskedIdentifier(maskedIdentifier).last4(last4)
            .build()));
  }

  @Transactional
  public EventResult observePurchase(PurchaseCommand command) {
    EasyBusiness business = business(command.businessId());
    EasyCardInstrument instrument = instrument(command.instrumentId(), command.businessId());
    EasyCollectionConnection connection = connection(command.connectionId(), command.businessId());
    String fingerprint = eventFingerprint(command.providerCode(), command.connectionId(),
        instrument.getProviderInstrumentTokenHash(), EasyCardEvent.Type.PURCHASE,
        command.occurredAt(), command.amount(), command.externalId(), command.approvalReference());
    var existing = eventRepository.findByBusiness_BusinessIdAndFingerprint(command.businessId(), fingerprint);
    if (existing.isPresent()) {
      return new EventResult(existing.get(), payableRepository
          .findByPurchaseEvent_CardEventId(existing.get().getCardEventId()).orElseThrow(), true);
    }
    EasyCardEvent event = eventRepository.save(EasyCardEvent.builder()
        .business(business).connection(connection).instrument(instrument)
        .eventType(EasyCardEvent.Type.PURCHASE).providerExternalId(command.externalId())
        .fingerprint(fingerprint).occurredAt(command.occurredAt()).amount(command.amount())
        .currency(command.currency()).approvalReferenceHash(hashOptional(command.approvalReference()))
        .build());
    EasyCardPayable payable = payableRepository.save(EasyCardPayable.builder()
        .business(business).instrument(instrument).purchaseEvent(event)
        .originalAmount(command.amount()).currency(command.currency()).build());
    return new EventResult(event, payable, false);
  }

  @Transactional
  public EventResult observeCancellation(Long originalEventId, String providerCode,
      String externalId, LocalDateTime occurredAt, long amount) {
    EasyCardEvent original = eventRepository.findById(originalEventId)
        .orElseThrow(() -> new IllegalArgumentException("Original card event not found."));
    EasyCardPayable payable = payableRepository.findByPurchaseEvent_CardEventId(originalEventId)
        .orElseThrow(() -> new IllegalArgumentException("Purchase payable not found."));
    String fingerprint = eventFingerprint(providerCode,
        original.getConnection() == null ? null : original.getConnection().getConnectionId(),
        original.getInstrument().getProviderInstrumentTokenHash(), EasyCardEvent.Type.CANCELLATION,
        occurredAt, amount, externalId, String.valueOf(originalEventId));
    var existing = eventRepository.findByBusiness_BusinessIdAndFingerprint(
        original.getBusiness().getBusinessId(), fingerprint);
    if (existing.isPresent()) return new EventResult(existing.get(), payable, true);
    EasyCardEvent event = eventRepository.save(EasyCardEvent.builder()
        .business(original.getBusiness()).connection(original.getConnection())
        .instrument(original.getInstrument()).originalEvent(original)
        .eventType(EasyCardEvent.Type.CANCELLATION).providerExternalId(externalId)
        .fingerprint(fingerprint).occurredAt(occurredAt).amount(amount)
        .currency(original.getCurrency()).build());
    payable.cancel(amount);
    return new EventResult(event, payable, false);
  }

  @Transactional
  public EasyCardEvent observeCorrection(Long originalEventId, String providerCode,
      String externalId, LocalDateTime occurredAt, long replacementOrDeltaAmount) {
    EasyCardEvent original = eventRepository.findById(originalEventId)
        .orElseThrow(() -> new IllegalArgumentException("Original card event not found."));
    String fingerprint = eventFingerprint(providerCode,
        original.getConnection() == null ? null : original.getConnection().getConnectionId(),
        original.getInstrument().getProviderInstrumentTokenHash(), EasyCardEvent.Type.CORRECTION,
        occurredAt, replacementOrDeltaAmount, externalId, String.valueOf(originalEventId));
    return eventRepository.findByBusiness_BusinessIdAndFingerprint(
            original.getBusiness().getBusinessId(), fingerprint)
        .orElseGet(() -> eventRepository.save(EasyCardEvent.builder()
            .business(original.getBusiness()).connection(original.getConnection())
            .instrument(original.getInstrument()).originalEvent(original)
            .eventType(EasyCardEvent.Type.CORRECTION).providerExternalId(externalId)
            .fingerprint(fingerprint).occurredAt(occurredAt).amount(replacementOrDeltaAmount)
            .currency(original.getCurrency()).build()));
  }

  @Transactional
  public EasyCardBillingGroup observeBilling(Long businessId, Long instrumentId,
      String providerCode, String externalId, LocalDate periodStart, LocalDate periodEnd,
      LocalDate paymentDueOn, long observedTotal, String currency, List<Long> eventIds) {
    EasyBusiness business = business(businessId);
    EasyCardInstrument instrument = instrument(instrumentId, businessId);
    String fingerprint = sha256(String.join("|", required(providerCode, "providerCode"),
        value(externalId), value(instrumentId), value(periodStart), value(periodEnd),
        value(paymentDueOn), String.valueOf(observedTotal)));
    EasyCardBillingGroup group = billingRepository
        .findByBusiness_BusinessIdAndFingerprint(businessId, fingerprint)
        .orElseGet(() -> billingRepository.save(EasyCardBillingGroup.builder()
            .business(business).instrument(instrument).fingerprint(fingerprint)
            .periodStart(periodStart).periodEnd(periodEnd).paymentDueOn(paymentDueOn)
            .observedTotal(observedTotal).currency(currency).build()));
    for (Long eventId : eventIds == null ? List.<Long>of() : eventIds) {
      EasyCardEvent event = eventRepository.findById(eventId)
          .orElseThrow(() -> new IllegalArgumentException("Billing event not found."));
      requireBusiness(event.getBusiness(), businessId);
      event.assignBillingGroup(group);
    }
    return group;
  }

  @Transactional
  public SettlementResult observeSettlement(SettlementCommand command) {
    EasyBusiness business = business(command.businessId());
    EasyCardInstrument instrument = instrument(command.instrumentId(), command.businessId());
    EasyCollectionConnection connection = connection(command.connectionId(), command.businessId());
    LocalDateTime occurredAt = command.paidOn().atStartOfDay();
    String fingerprint = eventFingerprint(command.providerCode(), command.connectionId(),
        instrument.getProviderInstrumentTokenHash(), EasyCardEvent.Type.PAYMENT_SETTLEMENT,
        occurredAt, command.amount(), command.externalId(), command.bankTransactionRef());
    var existing = settlementRepository.findByBusiness_BusinessIdAndFingerprint(
        command.businessId(), fingerprint);
    if (existing.isPresent()) return new SettlementResult(existing.get(), true);
    EasyCardEvent event = eventRepository
        .findByBusiness_BusinessIdAndFingerprint(command.businessId(), fingerprint)
        .orElseGet(() -> eventRepository.save(EasyCardEvent.builder()
            .business(business).connection(connection).instrument(instrument)
            .eventType(EasyCardEvent.Type.PAYMENT_SETTLEMENT)
            .providerExternalId(command.externalId()).fingerprint(fingerprint)
            .occurredAt(occurredAt).amount(command.amount()).currency(command.currency()).build()));
    return new SettlementResult(settlementRepository.save(EasyCardSettlement.builder()
        .business(business).instrument(instrument).settlementEvent(event)
        .bankTransactionRefHash(hashOptional(command.bankTransactionRef()))
        .fingerprint(fingerprint).paidOn(command.paidOn()).amount(command.amount())
        .currency(command.currency()).build()), false);
  }

  @Transactional
  public AllocationResult allocate(Long settlementId, Long payableId, long amount) {
    var existing = allocationRepository.findBySettlement_SettlementIdAndPayable_PayableId(
        settlementId, payableId);
    if (existing.isPresent()) return new AllocationResult(existing.get(), true);
    EasyCardSettlement settlement = settlementRepository.findById(settlementId)
        .orElseThrow(() -> new IllegalArgumentException("Settlement not found."));
    EasyCardPayable payable = payableRepository.findById(payableId)
        .orElseThrow(() -> new IllegalArgumentException("Payable not found."));
    if (!settlement.getBusiness().getBusinessId().equals(payable.getBusiness().getBusinessId()))
      throw new IllegalArgumentException("Cross-business allocation is forbidden.");
    payable.settle(amount);
    settlement.allocate(amount);
    return new AllocationResult(allocationRepository.save(EasyCardSettlementAllocation.builder()
        .settlement(settlement).payable(payable).allocatedAmount(amount).build()), false);
  }

  @Transactional
  public void approveEvent(Long eventId, String adminActor) {
    eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("Card event not found."))
        .approve(adminActor);
  }

  public long expenseCandidateTotal(Long businessId) {
    return payableRepository.findAllByBusiness_BusinessId(businessId).stream()
        .mapToLong(EasyCardPayable::recognizedExpenseCandidate).sum();
  }

  private EasyBusiness business(Long id) {
    return businessRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Business not found."));
  }
  private EasyCardInstrument instrument(Long id, Long businessId) {
    EasyCardInstrument value = instrumentRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Card instrument not found."));
    requireBusiness(value.getBusiness(), businessId);
    return value;
  }
  private EasyCollectionConnection connection(Long id, Long businessId) {
    if (id == null) return null;
    EasyCollectionConnection value = connectionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Collection connection not found."));
    requireBusiness(value.getBusiness(), businessId);
    return value;
  }
  private static void requireBusiness(EasyBusiness business, Long id) {
    if (business == null || business.getBusinessId() == null || !business.getBusinessId().equals(id))
      throw new IllegalArgumentException("Cross-business reference is forbidden.");
  }
  private static String eventFingerprint(String provider, Long connectionId, String instrumentHash,
      EasyCardEvent.Type type, LocalDateTime occurredAt, long amount, String externalId,
      String approvalReference) {
    String stableId = externalId == null || externalId.isBlank()
        ? String.join("|", value(occurredAt), String.valueOf(amount), value(approvalReference))
        : externalId.trim();
    return sha256(String.join("|", required(provider, "provider"), value(connectionId),
        instrumentHash, type.name(), stableId));
  }
  private static String hashOptional(String value) {
    return value == null || value.isBlank() ? null : sha256(value.trim());
  }
  private static String required(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required.");
    return value.trim();
  }
  private static String value(Object value) { return value == null ? "" : String.valueOf(value); }
  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 is unavailable.", e);
    }
  }
}
