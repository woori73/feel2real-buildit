package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admissions_knowledge_embeddings",
        indexes = {
                @Index(name = "idx_admissions_embedding_knowledge", columnList = "knowledge_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsKnowledgeEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "embedding_id")
    private Long embeddingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_id", nullable = false)
    private AdmissionsKnowledgeItem knowledge;

    /** knowledge_id 직접 접근용 (lazy 로딩 회피). */
    @Column(name = "knowledge_id", insertable = false, updatable = false)
    private Long knowledgeId;

    @Column(name = "model_version", length = 50, nullable = false)
    private String modelVersion;

    @Column(name = "dimension", nullable = false)
    @Builder.Default
    private Integer dimension = 768;

    @Column(name = "vector", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] vector;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
