package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeEmbedding;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeEmbeddingRepository;
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
 * 입시상담 인메모리 벡터 인덱스 (코사인 유사도, 브루트포스).
 * 지피지기의 KnowledgeVectorIndex 패턴을 입시 전용 테이블로 적용.
 * NewStart 지식과 혼합하지 않는다 — 별도 인덱스 인스턴스.
 * 벡터가 없으면 비어 있고, 호출자는 LIKE로 폴백한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionsVectorIndex {

    private final AdmissionsKnowledgeEmbeddingRepository embeddingRepository;
    private final AdmissionsEmbeddingProperties props;

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
        for (AdmissionsKnowledgeEmbedding e : embeddingRepository.findByModelVersion(model)) {
            float[] vec = bytesToFloats(e.getVector(), dim);
            // knowledgeId 컬럼 필드는 같은 영속성 컨텍스트에서 방금 저장한 엔티티에서는 null이다(읽기 전용 매핑).
            // 그 경우 연관 객체의 id로 대체한다(프록시여도 id 조회는 초기화 없이 가능).
            Long knowledgeId = e.getKnowledgeId() != null ? e.getKnowledgeId()
                    : (e.getKnowledge() != null ? e.getKnowledge().getKnowledgeId() : null);
            if (vec != null && knowledgeId != null) {
                fresh.put(knowledgeId, vec);
            }
        }
        index.clear();
        index.putAll(fresh);
        log.info("입시 벡터 인덱스 로드: {}건 (model={})", index.size(), model);
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

    /** float[] → little-endian bytes (적재용). */
    public static byte[] floatsToBytes(float[] vec) {
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) {
            buf.putFloat(v);
        }
        return buf.array();
    }
}
