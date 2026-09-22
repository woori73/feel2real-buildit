package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "newstart_knowledge_relations",
        uniqueConstraints = @UniqueConstraint(name = "uk_newstart_knowledge_relation", columnNames = {"from_knowledge_id", "to_knowledge_id", "relation_type"}),
        indexes = {
                @Index(name = "idx_newstart_knowledge_relations_from", columnList = "from_knowledge_id"),
                @Index(name = "idx_newstart_knowledge_relations_to", columnList = "to_knowledge_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_id")
    private Long relationId;

    @Column(name = "from_knowledge_id", nullable = false)
    private Long fromKnowledgeId;

    @Column(name = "to_knowledge_id", nullable = false)
    private Long toKnowledgeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", length = 30, nullable = false)
    private RelationType relationType;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RelationType {
        CASE,
        RULE,
        PREREQUISITE,
        SIMILAR,
        CONTRADICTS,
        // Canonical relation vocabulary
        // (docs/newstart/knowledge/NEWSTART_CANONICAL_RELATIONS_2026-09-22.json).
        // VARCHAR(30) 컬럼에 그대로 들어가며 기존 값과 충돌하지 않는다. DB migration 불필요.
        SUPPORTED_BY,
        PARTIALLY_SUPPORTED_BY,
        RELATED_CASE,
        RELATED_FAQ,
        UNEXPLAINED_BY_OFFICIAL,
        CONFLICT_REVIEW
    }
}