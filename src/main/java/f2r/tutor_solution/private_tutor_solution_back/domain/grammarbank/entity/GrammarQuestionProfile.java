package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** 문법 문제은행 확장 프로필 — questions 행 1:1. */
@Entity
@Table(name = "grammar_question_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrammarQuestionProfile extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "profile_id")
  private Long profileId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false, unique = true)
  private Question question;

  @Enumerated(EnumType.STRING)
  @Column(name = "school_level", nullable = false, length = 10)
  private SchoolLevel schoolLevel = SchoolLevel.UNKNOWN;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_format", length = 30)
  private GrammarQuestionFormat questionFormat;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private GrammarDifficulty difficulty;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String keywords;

  @Column(name = "auto_classification", columnDefinition = "JSON")
  private String autoClassification;

  @Column(name = "classification_confidence", precision = 3, scale = 2)
  private BigDecimal classificationConfidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 20)
  private GrammarReviewStatus reviewStatus = GrammarReviewStatus.AUTO;

  @Enumerated(EnumType.STRING)
  @Column(name = "rights_status", nullable = false, length = 20)
  private GrammarRightsStatus rightsStatus = GrammarRightsStatus.UNKNOWN;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id")
  private ProblemGeneratorMaterial material;

  /** 원본 내 위치 (예: 페이지 12 / 문항 5). */
  @Column(name = "source_position", length = 80)
  private String sourcePosition;

  /** 수입 멱등 키 (material 기준 외부 식별자). */
  @Column(name = "external_ref", length = 120)
  private String externalRef;

  @Column(name = "owner_user_id")
  private Long ownerUserId;

  public enum GrammarDifficulty { EASY, MEDIUM, HARD }

  @Builder
  public GrammarQuestionProfile(Question question, SchoolLevel schoolLevel,
      GrammarQuestionFormat questionFormat, GrammarDifficulty difficulty, String keywords,
      String autoClassification, BigDecimal classificationConfidence,
      GrammarReviewStatus reviewStatus, GrammarRightsStatus rightsStatus,
      String contentHash, ProblemGeneratorMaterial material, String sourcePosition,
      String externalRef, Long ownerUserId) {
    this.question = question;
    this.schoolLevel = schoolLevel != null ? schoolLevel : SchoolLevel.UNKNOWN;
    this.questionFormat = questionFormat;
    this.difficulty = difficulty;
    this.keywords = keywords;
    this.autoClassification = autoClassification;
    this.classificationConfidence = classificationConfidence;
    this.reviewStatus = reviewStatus != null ? reviewStatus : GrammarReviewStatus.AUTO;
    this.rightsStatus = rightsStatus != null ? rightsStatus : GrammarRightsStatus.UNKNOWN;
    this.contentHash = contentHash;
    this.material = material;
    this.sourcePosition = sourcePosition;
    this.externalRef = externalRef;
    this.ownerUserId = ownerUserId;
  }

  public void updateClassification(SchoolLevel schoolLevel, GrammarQuestionFormat questionFormat,
      GrammarDifficulty difficulty, String keywords) {
    if (schoolLevel != null) {
      this.schoolLevel = schoolLevel;
    }
    if (questionFormat != null) {
      this.questionFormat = questionFormat;
    }
    if (difficulty != null) {
      this.difficulty = difficulty;
    }
    if (keywords != null) {
      this.keywords = keywords;
    }
  }

  public void updateContentHash(String hash) { this.contentHash = hash; }

  public void updateReviewMetadata(String json) { this.autoClassification = json; }

  public void updateReviewStatus(GrammarReviewStatus reviewStatus) {
    this.reviewStatus = reviewStatus;
  }

  public void updateRightsStatus(GrammarRightsStatus rightsStatus) {
    this.rightsStatus = rightsStatus;
  }
}
