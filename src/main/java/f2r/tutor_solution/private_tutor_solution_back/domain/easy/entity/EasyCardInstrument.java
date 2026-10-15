package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Non-secret identity for a business card. Full PAN and provider credentials are never stored. */
@Entity
@Table(name = "easy_card_instruments",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_card_instrument_token",
        columnNames = {"business_id", "provider_code", "provider_instrument_token_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCardInstrument extends BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "instrument_id")
  private Long instrumentId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connection_id")
  private EasyCollectionConnection connection;

  @Column(name = "provider_code", nullable = false, length = 40)
  private String providerCode;

  @Column(name = "issuer", nullable = false, length = 80)
  private String issuer;

  @Column(name = "provider_instrument_token_hash", nullable = false, length = 64)
  private String providerInstrumentTokenHash;

  @Column(name = "masked_identifier", length = 40)
  private String maskedIdentifier;

  @Column(name = "last4", length = 4)
  private String last4;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Builder
  public EasyCardInstrument(EasyBusiness business, EasyCollectionConnection connection,
      String providerCode, String issuer, String providerInstrumentTokenHash,
      String maskedIdentifier, String last4) {
    this.business = require(business, "business");
    this.connection = connection;
    this.providerCode = text(providerCode, "providerCode", 40);
    this.issuer = text(issuer, "issuer", 80);
    this.providerInstrumentTokenHash = hash(providerInstrumentTokenHash);
    this.maskedIdentifier = optional(maskedIdentifier, 40);
    this.last4 = validateLast4(last4);
  }

  private static <T> T require(T value, String name) {
    if (value == null) throw new IllegalArgumentException(name + " is required.");
    return value;
  }
  private static String text(String value, String name, int max) {
    if (value == null || value.isBlank() || value.length() > max)
      throw new IllegalArgumentException(name + " is invalid.");
    return value.trim();
  }
  private static String hash(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("provider instrument token must be a SHA-256 hash.");
    return value;
  }
  private static String optional(String value, int max) {
    if (value == null || value.isBlank()) return null;
    if (value.length() > max) throw new IllegalArgumentException("masked identifier is too long.");
    return value.trim();
  }
  private static String validateLast4(String value) {
    if (value == null || value.isBlank()) return null;
    if (!value.matches("\\d{4}")) throw new IllegalArgumentException("last4 must contain four digits.");
    return value;
  }
}
