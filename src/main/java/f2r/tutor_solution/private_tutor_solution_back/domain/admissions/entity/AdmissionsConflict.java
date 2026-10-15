package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admissions_conflicts",
        indexes = {
                @Index(name = "idx_admissions_conflict_status", columnList = "status"),
                @Index(name = "idx_admissions_conflict_year", columnList = "academic_year")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conflict_id")
    private Long conflictId;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "related_knowledge_ids", length = 500)
    private String relatedKnowledgeIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ConflictStatus status = ConflictStatus.OPEN;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public enum ConflictStatus {
        OPEN,
        RESOLVED,
        PENDING
    }
}
