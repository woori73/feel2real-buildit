package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeEmbedding;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeEmbeddingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdmissionsEmbeddingService 단위 테스트 — 임베딩 저장·정규화·교체·실패 보고.
 * given/when/then 구조.
 */
class AdmissionsEmbeddingServiceTest {

    private AdmissionsKnowledgeItemRepository knowledgeRepository;
    private AdmissionsKnowledgeEmbeddingRepository embeddingRepository;
    private AdmissionsEmbeddingClient embeddingClient;
    private AdmissionsEmbeddingProperties props;
    private AdmissionsVectorIndex vectorIndex;
    private AdmissionsEmbeddingService service;

    /** knowledgeId → 저장된 임베딩 행 (upsert 흉내). */
    private final Map<Long, AdmissionsKnowledgeEmbedding> embeddingStore = new HashMap<>();

    private AdmissionsKnowledgeItem item(long id, AdmissionsKnowledgeItem.KnowledgeStatus status) {
        return AdmissionsKnowledgeItem.builder()
                .knowledgeId(id)
                .academicYear(2027)
                .yearConfirmed(true)
                .title("항목 " + id)
                .content("본문 " + id)
                .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                .status(status)
                .build();
    }

    private static float[] rawVector(float first, float second) {
        float[] v = new float[768];
        v[0] = first;
        v[1] = second;
        return v;
    }

    @BeforeEach
    void setUp() {
        knowledgeRepository = mock(AdmissionsKnowledgeItemRepository.class);
        embeddingRepository = mock(AdmissionsKnowledgeEmbeddingRepository.class);
        embeddingClient = mock(AdmissionsEmbeddingClient.class);
        vectorIndex = mock(AdmissionsVectorIndex.class);
        props = new AdmissionsEmbeddingProperties();
        props.setEnabled(true);
        props.setIndexRetryBackoffMs(0);
        service = new AdmissionsEmbeddingService(
                knowledgeRepository, embeddingRepository, embeddingClient, props, vectorIndex);

        when(embeddingRepository.findByKnowledge_KnowledgeIdAndModelVersion(anyLong(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(embeddingStore.get(inv.getArgument(0))));
        when(embeddingRepository.save(any(AdmissionsKnowledgeEmbedding.class))).thenAnswer(inv -> {
            AdmissionsKnowledgeEmbedding e = inv.getArgument(0);
            Long kid = e.getKnowledge() != null ? e.getKnowledge().getKnowledgeId() : e.getKnowledgeId();
            embeddingStore.put(kid, e);
            return e;
        });
    }

    @Test
    @DisplayName("저장 — 768차원 벡터가 L2 정규화되어 3072바이트로 저장되고 인덱스를 다시 읽는다")
    void reindexItem_storesNormalizedVector() {
        // given
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.ok(rawVector(3f, 4f))); // norm = 5

        // when
        var result = service.reindexItem(1L);

        // then
        assertEquals(1, result.requested());
        assertEquals(1, result.embedded());
        assertEquals(0, result.failed());
        AdmissionsKnowledgeEmbedding saved = embeddingStore.get(1L);
        assertNotNull(saved);
        assertEquals(3072, saved.getVector().length, "768차원 float32 = 3072바이트");
        assertEquals(768, saved.getDimension());
        assertEquals("embeddinggemma-2", saved.getModelVersion());
        float[] decoded = AdmissionsVectorIndex.bytesToFloats(saved.getVector(), 768);
        assertNotNull(decoded);
        double norm = 0;
        for (float f : decoded) norm += (double) f * f;
        assertEquals(1.0, Math.sqrt(norm), 1e-5, "L2 정규화");
        assertEquals(0.6f, decoded[0], 1e-6);
        assertEquals(0.8f, decoded[1], 1e-6);
        verify(vectorIndex).reload();
    }

    @Test
    @DisplayName("교체 — 같은 항목을 다시 색인해도 임베딩 행은 1개만 유지되고 벡터가 교체된다")
    void reindexItem_secondRunReplacesRow() {
        // given
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.ok(rawVector(3f, 4f)))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.ok(rawVector(1f, 0f)));

        // when
        service.reindexItem(1L);
        var second = service.reindexItem(1L);

        // then
        assertEquals(1, second.embedded());
        assertEquals(1, embeddingStore.size(), "행 중복 없음");
        float[] decoded = AdmissionsVectorIndex.bytesToFloats(embeddingStore.get(1L).getVector(), 768);
        assertEquals(1.0f, decoded[0], 1e-6, "두 번째 벡터로 교체됨");
        assertEquals(0.0f, decoded[1], 1e-6);
    }

    @Test
    @DisplayName("실패 — 임베딩 서비스가 null을 돌려주면 실패로 보고하고 저장하지 않는다")
    void reindexItem_clientReturnsNull_fails() {
        // given
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.fail("임베딩 서비스 응답 없음", false));

        // when
        var result = service.reindexItem(1L);

        // then
        assertEquals(0, result.embedded());
        assertEquals(1, result.failed());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("knowledgeId=1"), "사유에 항목 ID: " + result.failures());
        assertTrue(result.failures().get(0).contains("응답 없음"), "사유 내용: " + result.failures());
        assertTrue(embeddingStore.isEmpty());
        verify(vectorIndex, never()).reload();
    }

    @Test
    @DisplayName("실패 — 임베딩 비활성이면 'disabled' 사유로 실패하고 클라이언트를 부르지 않는다")
    void reindexItem_disabled_failsWithReason() {
        // given
        props.setEnabled(false);
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));

        // when
        var result = service.reindexItem(1L);

        // then
        assertEquals(1, result.failed());
        assertTrue(result.failures().get(0).contains("disabled"), "사유: " + result.failures());
        verify(embeddingClient, never()).embedDocument(anyString());
        assertTrue(embeddingStore.isEmpty());
    }

    @Test
    @DisplayName("실패 — 차원이 다르면 저장하지 않고 사유를 남긴다")
    void reindexItem_dimensionMismatch_fails() {
        // given
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.ok(new float[384]));

        // when
        var result = service.reindexItem(1L);

        // then
        assertEquals(1, result.failed());
        assertTrue(result.failures().get(0).contains("차원"), "사유: " + result.failures());
        assertTrue(embeddingStore.isEmpty());
    }

    @Test
    @DisplayName("누락분만 — ACTIVE 중 임베딩 없는 항목만 색인하고, 있는 항목·DRAFT는 건너뛴다")
    void reindexMissing_onlyActiveWithoutEmbedding() {
        // given
        var activeNoEmb = item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
        var activeWithEmb = item(2L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
        var draftNoEmb = item(3L, AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
        when(knowledgeRepository.findAll()).thenReturn(List.of(activeNoEmb, activeWithEmb, draftNoEmb));
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(activeNoEmb));
        // findByModelVersion 결과는 서비스가 getKnowledgeId()(insertable=false 컬럼)로 읽으므로 그 필드를 채워 둔다.
        var existing = withKnowledgeId(AdmissionsKnowledgeEmbedding.builder()
                .knowledge(activeWithEmb).modelVersion("embeddinggemma-2")
                .dimension(768).vector(new byte[3072]).build(), 2L);
        when(embeddingRepository.findByModelVersion("embeddinggemma-2")).thenReturn(List.of(existing));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.ok(rawVector(0f, 2f)));

        // when
        var result = service.reindexMissing();

        // then
        assertEquals(2, result.requested(), "ACTIVE 전체 수");
        assertEquals(1, result.embedded());
        assertEquals(1, result.skipped(), "이미 임베딩 있는 ACTIVE 1건");
        assertEquals(0, result.failed());
        assertTrue(embeddingStore.containsKey(1L));
        assertFalse(embeddingStore.containsKey(3L), "DRAFT는 대상 아님");
    }

    /** knowledgeId는 insertable=false 컬럼이라 빌더로 못 채운다 — 테스트에서만 리플렉션으로 채운다. */
    private static AdmissionsKnowledgeEmbedding withKnowledgeId(AdmissionsKnowledgeEmbedding e, Long id) {
        try {
            var f = AdmissionsKnowledgeEmbedding.class.getDeclaredField("knowledgeId");
            f.setAccessible(true);
            f.set(e, id);
            return e;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("재시도 — 일시 장애(retryable)는 재시도해 성공하면 저장하고, 시도 횟수는 1+indexRetries를 넘지 않는다")
    void embedItem_retriesTransientFailureThenSucceeds() {
        // given: 두 번 503 → 세 번째 성공
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.fail("503", true))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.fail("503", true))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.ok(rawVector(3f, 4f)));

        // when
        String reason = service.embedItem(1L);

        // then
        assertEquals(null, reason);
        assertEquals(1, embeddingStore.size());
        verify(embeddingClient, org.mockito.Mockito.times(3)).embedDocument(anyString());
    }

    @Test
    @DisplayName("재시도 — 계속 실패하면 1+indexRetries회 후 마지막 사유로 실패하고 저장하지 않는다")
    void embedItem_givesUpAfterRetries() {
        // given
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.fail("타임아웃", true));

        // when
        String reason = service.embedItem(1L);

        // then
        assertEquals("타임아웃", reason);
        assertEquals(0, embeddingStore.size());
        verify(embeddingClient, org.mockito.Mockito.times(1 + props.getIndexRetries())).embedDocument(anyString());
    }

    @Test
    @DisplayName("재시도 — 재시도 불가 실패(4xx 등)는 한 번만 시도한다")
    void embedItem_nonRetryableFailsImmediately() {
        // given
        when(knowledgeRepository.findById(1L)).thenReturn(Optional.of(item(1L, AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE)));
        when(embeddingClient.embedDocument(anyString()))
                .thenReturn(AdmissionsEmbeddingClient.DocResult.fail("status=400", false));

        // when
        String reason = service.embedItem(1L);

        // then
        assertEquals("status=400", reason);
        verify(embeddingClient, org.mockito.Mockito.times(1)).embedDocument(anyString());
    }
}
