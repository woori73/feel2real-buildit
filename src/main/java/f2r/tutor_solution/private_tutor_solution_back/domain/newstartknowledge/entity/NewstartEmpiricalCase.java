package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "newstart_empirical_cases",
        uniqueConstraints = @UniqueConstraint(name = "uk_newstart_empirical_cases_article", columnNames = "article_id"),
        indexes = {
                @Index(name = "idx_newstart_empirical_reduction", columnList = "final_reduction_rate"),
                @Index(name = "idx_newstart_empirical_debt", columnList = "debt_total"),
                @Index(name = "idx_newstart_empirical_income", columnList = "income_2025"),
                @Index(name = "idx_newstart_empirical_age", columnList = "age"),
                @Index(name = "idx_newstart_empirical_household", columnList = "household_type"),
                @Index(name = "idx_newstart_empirical_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartEmpiricalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "article_id", length = 20, nullable = false)
    private String articleId;

    @Column(name = "board_name", length = 50, nullable = false)
    @Builder.Default
    private String boardName = "감면율";

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "author_nickname", length = 60)
    private String authorNickname;

    @Column(name = "author_grade", length = 30)
    private String authorGrade;

    @Column(name = "written_at")
    private LocalDateTime writtenAt;

    @Column(name = "view_count")
    private Integer viewCount;

    // 채무 정보
    @Column(name = "debt_total")
    private Long debtTotal;

    @Column(name = "debt_breakdown", columnDefinition = "JSON")
    private String debtBreakdown;

    @Column(name = "debt_secured")
    private Long debtSecured;

    @Column(name = "debt_unsecured")
    private Long debtUnsecured;

    @Column(name = "debt_acquired_by_fund")
    private Long debtAcquiredByFund;

    // 자산 정보
    @Column(name = "asset_property")
    private Long assetProperty;

    @Column(name = "asset_vehicle")
    private Long assetVehicle;

    @Column(name = "asset_financial")
    private Long assetFinancial;

    // 가구/소득/개인 정보
    @Column(name = "household_type", length = 30)
    private String householdType;

    @Column(name = "dependents")
    private Integer dependents;

    @Column(name = "age")
    private Integer age;

    @Column(name = "income_2025")
    private Long income2025;

    @Column(name = "income_current")
    private Long incomeCurrent;

    @Column(name = "employment_status", length = 30)
    private String employmentStatus;

    @Column(name = "business_status", length = 255)
    private String businessStatus;

    @Column(name = "application_type", length = 30)
    private String applicationType;

    // 감면율/확정금액 정보
    @Column(name = "predicted_reduction_rate", length = 30)
    private String predictedReductionRate;

    @Column(name = "final_reduction_rate", length = 30)
    private String finalReductionRate;

    @Column(name = "temp_reduction_rate", length = 30)
    private String tempReductionRate;

    @Column(name = "predicted_repayment_amount")
    private Long predictedRepaymentAmount;

    @Column(name = "final_repayment_amount")
    private Long finalRepaymentAmount;

    @Column(name = "repayment_period_months")
    private Integer repaymentPeriodMonths;

    @Column(name = "grace_period_months")
    private Integer gracePeriodMonths;

    @Column(name = "monthly_payment")
    private Long monthlyPayment;

    // 원문 및 메타
    @Column(name = "content_raw", columnDefinition = "LONGTEXT")
    private String contentRaw;

    @Column(name = "manager_prediction", columnDefinition = "JSON")
    private String managerPrediction;

    @Column(name = "author_followup_result", columnDefinition = "TEXT")
    private String authorFollowupResult;

    @Column(name = "prediction_vs_actual", columnDefinition = "JSON")
    private String predictionVsActual;

    @Column(name = "special_circumstances", columnDefinition = "JSON")
    private String specialCircumstances;

    @Column(name = "key_insights", columnDefinition = "JSON")
    private String keyInsights;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}