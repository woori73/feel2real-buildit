package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeEmbedding;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeEmbeddingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 지식 항목 임베딩 쓰기 경로 — 지금까지 비어 있던 부분.
 *
 * <p>검색 측({@code AdmissionsVectorIndex}·{@code AdmissionsResearchService})과
 * 저장 테이블({@code admissions_knowledge_embeddings})은 이미 있었지만 임베딩을 만들어
 * 저장하는 코드가 없어 운영 벡터 인덱스는 항상 비어 있었다. 이 서비스가
 * {@code AdmissionsEmbeddingClient}(embeddinggemma-2, 768차원)로 항목을 임베딩해 저장한다.
 *
 * <p>규칙:
 * <ul>
 *   <li>벡터는 저장 전에 L2 정규화한다(인덱스가 내적=코사인으로 계산하는 전제).</li>
 *   <li>(knowledge_id, model_version) 기준으로 upsert — 재실행해도 행이 중복되지 않는다.</li>
 *   <li>실패는 삼키지 않는다. 항목별 사유를 {@link ReindexResult#failures}로 돌려준다.</li>
 *   <li>인덱스 reload는 트랜잭션 커밋 이후에 실행한다(방금 저장한 행의 knowledge_id가
 *       아직 DB에 반영되지 않은 상태로 읽는 것을 방지).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionsEmbeddingService {

    private final AdmissionsKnowledgeItemRepository knowledgeRepository;
    private final AdmissionsKnowledgeEmbeddingRepository embeddingRepository;
    private final AdmissionsEmbeddingClient embeddingClient;
    private final AdmissionsEmbeddingProperties props;
    private final AdmissionsVectorIndex vectorIndex;

    /**
     * @param requested 대상 항목 수 (reindexMissing은 ACTIVE 전체 수)
     * @param embedded 임베딩 성공 수
     * @param skipped 이미 임베딩이 있어 제외한 수 (reindexMissing만 집계)
     * @param failed 실패 수
     * @param failures 항목별 실패 사유 ("knowledgeId=..: 사유")
     */
    public record ReindexResult(int requested, int embedded, int skipped, int failed, List<String> failures) {}

    /** 항목 1건 재색인. */
    @Transactional
    public ReindexResult reindexItem(Long knowledgeId) {
        return runTargets(List.of(knowledgeId));
    }

    /** 지정한 항목들 재색인. */
    @Transactional
    public ReindexResult reindexItems(List<Long> knowledgeIds) {
        return runTargets(knowledgeIds == null ? List.of() : knowledgeIds);
    }

    /** 상태와 무관하게 전체 항목 재색인. */
    @Transactional
    public ReindexResult reindexAll() {
        List<Long> ids = knowledgeRepository.findAll().stream()
                .map(AdmissionsKnowledgeItem::getKnowledgeId)
                .toList();
        return runTargets(ids);
    }

    /** ACTIVE 항목 중 현재 모델 버전 임베딩이 없는 항목만 재색인한다. */
    @Transactional
    public ReindexResult reindexMissing() {
        List<AdmissionsKnowledgeItem> actives = knowledgeRepository.findAll().stream()
                .filter(k -> k.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)
                .toList();
        Set<Long> have = embeddingRepository.findByModelVersion(props.getModelVersion()).stream()
                .map(AdmissionsKnowledgeEmbedding::getKnowledgeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Long> targets = actives.stream()
                .map(AdmissionsKnowledgeItem::getKnowledgeId)
                .filter(id -> !have.contains(id))
                .toList();
        ReindexResult core = runTargets(targets);
        return new ReindexResult(actives.size(), core.embedded(),
                actives.size() - targets.size(), core.failed(), core.failures());
    }

    // ── 내부 실행 ────────────────────────────────────────────────────

    private ReindexResult runTargets(List<Long> ids) {
        List<String> failures = new ArrayList<>();
        if (!props.isEnabled()) {
            for (Long id : ids) {
                failures.add("knowledgeId=" + id + ": embedding disabled (app.admissions.embedding.enabled=false)");
            }
            log.warn("admissions reindex skipped: embedding disabled, targets={}", ids.size());
            return new ReindexResult(ids.size(), 0, 0, ids.size(), failures);
        }
        int embedded = 0;
        int failed = 0;
        for (Long id : ids) {
            var itemOpt = knowledgeRepository.findById(id);
            if (itemOpt.isEmpty()) {
                failed++;
                failures.add("knowledgeId=" + id + ": 지식 항목이 없습니다");
                continue;
            }
            String reason = embedOne(itemOpt.get());
            if (reason == null) {
                embedded++;
            } else {
                failed++;
                failures.add("knowledgeId=" + id + ": " + reason);
            }
        }
        if (embedded > 0) {
            reloadIndexAfterCommit();
        }
        log.info("admissions reindex: requested={} embedded={} failed={}", ids.size(), embedded, failed);
        return new ReindexResult(ids.size(), embedded, 0, failed, failures);
    }

    /** @return 성공하면 null, 실패하면 사유. */
    private String embedOne(AdmissionsKnowledgeItem item) {
        float[] vec = embeddingClient.embed(item.getTitle() + "\n" + item.getContent());
        if (vec == null) {
            return "임베딩 서비스 응답 없음 (service-url 미설정 또는 호출 실패)";
        }
        if (vec.length != props.getDimension()) {
            return "임베딩 차원 불일치: expected=" + props.getDimension() + " actual=" + vec.length;
        }
        byte[] bytes = AdmissionsVectorIndex.floatsToBytes(l2Normalize(vec));
        var existing = embeddingRepository
                .findByKnowledge_KnowledgeIdAndModelVersion(item.getKnowledgeId(), props.getModelVersion());
        if (existing.isPresent()) {
            AdmissionsKnowledgeEmbedding e = existing.get();
            e.setVector(bytes);
            e.setDimension(vec.length);
            embeddingRepository.save(e);
        } else {
            embeddingRepository.save(AdmissionsKnowledgeEmbedding.builder()
                    .knowledge(item)
                    .modelVersion(props.getModelVersion())
                    .dimension(vec.length)
                    .vector(bytes)
                    .build());
        }
        return null;
    }

    /** L2 정규화. 0 벡터는 그대로 돌려준다(NaN 방지). */
    static float[] l2Normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        float[] out = new float[v.length];
        if (norm == 0) {
            System.arraycopy(v, 0, out, 0, v.length);
            return out;
        }
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    private void reloadIndexAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    vectorIndex.reload();
                }
            });
        } else {
            vectorIndex.reload();
        }
    }
}
