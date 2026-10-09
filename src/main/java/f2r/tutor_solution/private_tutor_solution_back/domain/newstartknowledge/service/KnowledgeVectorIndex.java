package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeEmbedding;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeEmbeddingRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 벡터 인덱스 (코사인 유사도, 브루트포스).
 * 4,722건 × 768차원 ≈ 14MB. 검색 10ms 이내.
 * 벡터가 없으면 비어 있고, 호출자는 LIKE로 폴백한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVectorIndex {

    private final NewstartKnowledgeEmbeddingRepository embeddingRepository;
    private final NewstartEmbeddingProperties props;

    private final Map<Long, float[]> index = new ConcurrentHashMap<>();

    @Getter
    public static class VectorHit {
        private final long knowledgeId;
        private final double similarity;

        public VectorHit(long knowledgeId, double similarity) {
            this.knowledgeId = knowledgeId;
            this.similarity = similarity;
        }
    }

    @PostConstruct
    public void load() {
        reload();
    }

    /** 벡터 재로드 (관리자 재인덱싱용). */
    public synchronized void reload() {
        Map<Long, float[]> fresh = new ConcurrentHashMap<>();
        String model = props.getModelVersion();
        int dim = props.getDimension();
        for (NewstartKnowledgeEmbedding e : embeddingRepository.findAll()) {
            if (!model.equals(e.getModelVersion())) {
                continue;
            }
            float[] vec = bytesToFloats(e.getEmbedding(), dim);
            if (vec != null) {
                fresh.put(e.getKnowledgeId(), vec);
            }
        }
        index.clear();
        index.putAll(fresh);
        log.info("벡터 인덱스 로드: {}건 (model={})", index.size(), model);
    }

    public int size() {
        return index.size();
    }

    public boolean isReady() {
        return !index.isEmpty();
    }

    /**
     * 코사인 유사도 상위 limit건. 벡터는 L2 정규화되어 있어 내적 = 코사인 유사도.
     */
    public List<VectorHit> search(float[] query, int limit) {
        List<VectorHit> hits = new ArrayList<>(index.size());
        for (Map.Entry<Long, float[]> e : index.entrySet()) {
            double sim = dotProduct(query, e.getValue());
            hits.add(new VectorHit(e.getKey(), sim));
        }
        hits.sort(Comparator.comparingDouble(VectorHit::getSimilarity).reversed());
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    private static double dotProduct(float[] a, float[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    static float[] bytesToFloats(byte[] bytes, int dim) {
        if (bytes == null || bytes.length != dim * 4) {
            return null;
        }
        float[] out = new float[dim];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
        return out;
    }
}
