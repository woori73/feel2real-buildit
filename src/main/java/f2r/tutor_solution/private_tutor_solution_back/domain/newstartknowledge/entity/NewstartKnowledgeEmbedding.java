package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RAG 벡터 인덱스 (EmbeddingGemma 2, 768차원).
 * 읽기 전용 벡터 저장소 — newstart_knowledge_items 원문은 변경하지 않는다.
 */
@Entity
@Table(name = "newstart_knowledge_embedding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NewstartKnowledgeEmbedding {

    /** NewstartKnowledgeItem.knowledgeId와 동일. */
    @Id
    @Column(name = "knowledge_id")
    private Long knowledgeId;

    /** 768 float32 little-endian, L2 정규화. */
    @Column(name = "embedding", nullable = false, columnDefinition = "BLOB")
    private byte[] embedding;

    /** 임베딩 모델 버전 (예: embeddinggemma-2). */
    @Column(name = "model_version", length = 50, nullable = false)
    private String modelVersion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
