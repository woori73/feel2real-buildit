package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeEmbedding;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeEmbeddingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("벡터 인덱스 (코사인 유사도)")
class KnowledgeVectorIndexTest {

    @Mock
    private NewstartKnowledgeEmbeddingRepository repository;

    private static byte[] floatsToBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.asFloatBuffer().put(v);
        return buf.array();
    }

    @Test
    @DisplayName("given 정규화된 벡터 when search then 코사인 유사도 순으로 반환한다")
    void search_returnsByCosineSimilarity() {
        NewstartEmbeddingProperties props = new NewstartEmbeddingProperties();
        // 1번: [1,0,0...], 2번: [0,1,0...], 3번: [0.7,0.7,0...] 정규화
        float[] v1 = new float[768]; v1[0] = 1.0f;
        float[] v2 = new float[768]; v2[1] = 1.0f;
        float[] v3 = new float[768]; v3[0] = 0.7071f; v3[1] = 0.7071f;
        when(repository.findAll()).thenReturn(List.of(
                NewstartKnowledgeEmbedding.builder().knowledgeId(1L)
                        .embedding(floatsToBytes(v1)).modelVersion("embeddinggemma-2")
                        .updatedAt(LocalDateTime.now()).build(),
                NewstartKnowledgeEmbedding.builder().knowledgeId(2L)
                        .embedding(floatsToBytes(v2)).modelVersion("embeddinggemma-2")
                        .updatedAt(LocalDateTime.now()).build(),
                NewstartKnowledgeEmbedding.builder().knowledgeId(3L)
                        .embedding(floatsToBytes(v3)).modelVersion("embeddinggemma-2")
                        .updatedAt(LocalDateTime.now()).build()));

        KnowledgeVectorIndex index = new KnowledgeVectorIndex(repository, props);
        index.reload();

        assertThat(index.size()).isEqualTo(3);
        assertThat(index.isReady()).isTrue();

        float[] query = new float[768]; query[0] = 1.0f;
        List<KnowledgeVectorIndex.VectorHit> hits = index.search(query, 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).getKnowledgeId()).isEqualTo(1L);
        assertThat(hits.get(0).getSimilarity()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
        assertThat(hits.get(1).getKnowledgeId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("given 빈 저장소 when search then isReady는 false다 (LIKE 폴백 신호)")
    void emptyIndex_isNotReady() {
        NewstartEmbeddingProperties props = new NewstartEmbeddingProperties();
        when(repository.findAll()).thenReturn(List.of());

        KnowledgeVectorIndex index = new KnowledgeVectorIndex(repository, props);
        index.reload();

        assertThat(index.isReady()).isFalse();
        assertThat(index.size()).isEqualTo(0);
    }
}
