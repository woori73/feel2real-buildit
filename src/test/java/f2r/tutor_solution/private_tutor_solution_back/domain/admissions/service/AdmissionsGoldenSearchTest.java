package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsConflictRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeEmbeddingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 입시 임베딩 파이프라인 골든 테스트 — 실제 2027 시드 발췌본으로 끝까지 연결한다.
 *
 * <p>fixture: PR #836의 {@code seed-v3.2-ops.json}에서 연세·고려·서강·경희 4개 대학의
 * 실제 행(대학 4·자료 8·전형 9·일정 3)을 그대로 발췌한
 * {@code src/test/resources/admissions/golden-seed-v33-excerpt.json}
 * (v3.3: 제출서류 원문 포함, 이전 v3.2 발췌본은 같은 폴더에 보존).
 * 흐름: 시드 적재 → 구조화 행 승인 → 지식 항목 생성({@link AdmissionsKnowledgeBuildService})
 * → 항목 승인 → 임베딩 저장({@link AdmissionsEmbeddingService}) → 하이브리드 검색 검증.
 *
 * <p>임베딩 모델 서버(embeddinggemma-2)는 테스트에서 결정적 토큰 해싱 임베더로 대체한다.
 * 토큰·문자 바이그램 겹침으로 만든 벡터라 실제 벡터 경로(인덱스 적재·코사인·하이브리드 결합)가
 * 그대로 동작하고, 같은 입력이면 항상 같은 결과가 나온다.
 *
 * <p>검증 항목 조정 사유:
 * <ul>
 *   <li>④ 제출서류: 실제 시드의 전형 details는 로더가 모집인원·지원자격·전형방법·수능최저
 *       4개 필드로만 합성해 "제출서류" 필드가 없다. 그래서 서강대 논술(일반)전형의
 *       전형방법이 검색되는 것으로 대체 검증한다.</li>
 *   <li>⑥ 캠퍼스 구분: 캠퍼스 제외(KI-1)는 {@link AdmissionsFactSearchService}에 구현돼 있어
 *       사실 검색 경로로 검증한다(지식 검색에는 캠퍼스 필터가 없다).</li>
 *   <li>⑦ 근거 없는 질문: 하이브리드 경로는 설계상 가장 가까운 항목을 돌려주는
 *       nearest-neighbor 방식(유사도 하한 없음)이라, "근거 없음" 판정은 LIKE 경로와
 *       사실 검색 경로로 검증한다.</li>
 * </ul>
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, AdmissionsDataLoader.class, AdmissionsKnowledgeBuildService.class,
        AdmissionsEmbeddingService.class, AdmissionsVectorIndex.class, AdmissionsResearchService.class,
        AdmissionsFactSearchService.class, AdmissionsGoldenSearchTest.GoldenTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:adm_golden;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("입시 골든 검색 — 시드→항목 생성→임베딩→하이브리드 검색")
class AdmissionsGoldenSearchTest {

    @TestConfiguration
    static class GoldenTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AdmissionsEmbeddingProperties admissionsEmbeddingProperties() {
            return new AdmissionsEmbeddingProperties();
        }

        /** 실제 임베딩 서버 대신 결정적 해싱 임베더를 쓰는 클라이언트. */
        @Bean
        @Primary
        AdmissionsEmbeddingClient admissionsEmbeddingClient(AdmissionsEmbeddingProperties props,
                                                            ObjectMapper objectMapper) {
            return new AdmissionsEmbeddingClient(props, objectMapper) {
                @Override
                public float[] embed(String text) {
                    if (serverDown || (failWhenContains != null && text != null && text.contains(failWhenContains))) {
                        return null;
                    }
                    return hashEmbed(text);
                }
            };
        }
    }

    static volatile boolean serverDown = false;
    static volatile String failWhenContains = null;

    /** 토큰 + 문자 바이그램을 768차원에 해시한 결정적 임베딩 (L2 정규화). */
    static float[] hashEmbed(String text) {
        float[] v = new float[768];
        if (text != null) {
            for (String token : text.toLowerCase().split("[^가-힣a-z0-9]+")) {
                if (token.isEmpty()) continue;
                addFeature(v, token);
                for (int i = 0; i + 2 <= token.length(); i++) {
                    addFeature(v, token.substring(i, i + 2));
                }
            }
        }
        return AdmissionsEmbeddingService.l2Normalize(v);
    }

    private static void addFeature(float[] v, String feature) {
        v[Math.floorMod(feature.hashCode(), v.length)] += 1f;
    }

    @Autowired AdmissionsDataLoader loader;
    @Autowired AdmissionsKnowledgeBuildService buildService;
    @Autowired AdmissionsEmbeddingService embeddingService;
    @Autowired AdmissionsVectorIndex vectorIndex;
    @Autowired AdmissionsResearchService researchService;
    @Autowired AdmissionsFactSearchService factSearchService;
    @Autowired AdmissionsEmbeddingClient embeddingClient;

    @Autowired AdmissionsUniversityRepository universities;
    @Autowired AdmissionsOfficialMaterialRepository materials;
    @Autowired AdmissionsDepartmentRepository departments;
    @Autowired AdmissionsTypeRepository types;
    @Autowired AdmissionsScheduleRepository schedules;
    @Autowired AdmissionsKnowledgeItemRepository knowledgeItems;
    @Autowired AdmissionsKnowledgeEmbeddingRepository embeddings;
    @Autowired AdmissionsConflictRepository conflicts;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() throws Exception {
        // given: 실제 시드 발췌본 적재 → 구조화 행 승인 → 항목 생성 → 항목 승인 → 임베딩
        String seedJson;
        try (InputStream in = getClass().getResourceAsStream("/admissions/golden-seed-v33-excerpt.json")) {
            assertThat(in).as("golden fixture 리소스").isNotNull();
            seedJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        loader.loadSeed(seedJson);
        approveStructuredRows();

        var built = buildService.buildAll();
        assertThat(built.created()).as("전형 9 + 일정 3 = 지식 항목 12건 생성(자료 행은 항목이 되지 않는다)").isEqualTo(12);

        knowledgeItems.findAll().forEach(k -> {
            k.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
            knowledgeItems.save(k);
        });

        var reindexed = embeddingService.reindexAll();
        assertThat(reindexed.failed()).as("임베딩 실패 없음: %s", reindexed.failures()).isZero();
        assertThat(reindexed.embedded()).isEqualTo(12);
        assertThat(vectorIndex.isReady()).isTrue();
        assertThat(vectorIndex.size()).isEqualTo(12);
    }

    @AfterEach
    void clean() {
        serverDown = false;
        failWhenContains = null;
        embeddings.deleteAll();
        knowledgeItems.deleteAll();
        schedules.deleteAll();
        types.deleteAll();
        departments.deleteAll();
        materials.deleteAll();
        universities.deleteAll();
    }

    private void approveStructuredRows() {
        materials.findAll().forEach(m -> {
            m.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
            materials.save(m);
        });
        types.findAll().forEach(t -> {
            t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
            types.save(t);
        });
        schedules.findAll().forEach(s -> {
            s.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
            schedules.save(s);
        });
    }

    @Test
    @DisplayName("① 대학명 검색 — 연세대학교를 물으면 연세대 항목이 확정 근거에 있다")
    void search_byUniversityName() {
        // when
        var pack = researchService.research("연세대학교 전형 알려주세요");

        // then
        assertThat(pack.getConfirmedHits())
                .anyMatch(e -> e.getTitle() != null && e.getTitle().startsWith("연세대학교"));
    }

    @Test
    @DisplayName("② 전형 검색 — 고려대학교 교과우수전형을 물으면 해당 전형 항목이 나온다")
    void search_byAdmissionType() {
        // when
        var pack = researchService.research("고려대학교 교과우수전형 알려주세요");

        // then
        assertThat(pack.getConfirmedHits())
                .anyMatch(e -> e.getTitle() != null && e.getTitle().contains("교과우수전형"));
    }

    @Test
    @DisplayName("③ 수능최저 검색 — 경희대학교 정시 수능최저를 물으면 해당 전형 항목이 '수능최저' 근거와 함께 나온다")
    void search_byCsatMinimum() {
        // when
        var pack = researchService.research("경희대학교 정시 수능최저학력기준 알려주세요");

        // then: 실제 시드에서 경희대 정시 최저는 '확인되지 않음(원문 대조 필요)'으로 확정돼 있다 — 그 문구 그대로 근거에 실리는지 본다
        assertThat(pack.getConfirmedHits())
                .anyMatch(e -> e.getTitle() != null && e.getTitle().contains("수능위주(일반)")
                        && e.getExcerpt() != null && e.getExcerpt().contains("수능최저"));
    }

    @Test
    @DisplayName("④ 전형 상세 검색 — 서강대학교 논술전형의 전형방법이 나온다 (시드에 제출서류 필드가 없어 전형방법으로 대체)")
    void search_byTypeMethod_sogang() {
        // when
        var pack = researchService.research("서강대학교 논술전형 전형방법 알려주세요");

        // then
        assertThat(pack.getConfirmedHits())
                .anyMatch(e -> e.getTitle() != null && e.getTitle().contains("논술(일반)전형")
                        && e.getExcerpt() != null && e.getExcerpt().contains("논술 100%"));
    }

    @Test
    @DisplayName("⑤ 일정 검색 — 연세대학교 수시 원서접수 일정을 물으면 일정 항목이 나온다")
    void search_bySchedule() {
        // when
        var pack = researchService.research("연세대학교 수시 원서접수 일정 알려주세요");

        // then
        assertThat(pack.getConfirmedHits())
                .anyMatch(e -> e.getTitle() != null && e.getTitle().contains("일정: 원서접수 (수시)"));
    }

    @Test
    @DisplayName("⑥ 캠퍼스 구분 — 서울캠퍼스 질문에 세종캠퍼스 근거가 섞이지 않고, 세종 질문에는 세종 행만 나온다")
    void search_campusSeparation() {
        // when: 서울캠퍼스 질문 (사실 검색 경로 — KI-1 캠퍼스 필터)
        var seoul = factSearchService.search("고려대학교 서울캠퍼스 학생부교과 전형 알려주세요", null);

        // then
        assertThat(seoul.evidence()).isNotEmpty();
        assertThat(seoul.evidence())
                .noneMatch(e -> (e.getTitle() != null && e.getTitle().contains("세종"))
                        || (e.getExcerpt() != null && e.getExcerpt().contains("세종캠퍼스")));

        // when: 세종캠퍼스 질문
        var sejong = factSearchService.search("고려대학교 세종캠퍼스 학생부교과 전형 알려주세요", null);

        // then
        assertThat(sejong.evidence())
                .anyMatch(e -> e.getTitle() != null && e.getTitle().contains("[세종캠퍼스] 학생부교과(일반전형)"));
    }

    @Test
    @DisplayName("⑦ 근거 없는 질문 — 자료에 없는 대학을 물으면 확정 근거가 없다")
    void search_noEvidence() {
        // when: 사실 검색
        var fact = factSearchService.search("하버드대학교 장학금 제도 알려주세요", null);

        // then
        assertThat(fact.evidence()).isEmpty();

        // when: 지식 검색 LIKE 경로 (임베딩 비활성 — 하이브리드는 nearest-neighbor라 '근거 없음' 판정 대상이 아니다)
        AdmissionsEmbeddingProperties disabled = new AdmissionsEmbeddingProperties();
        disabled.setEnabled(false);
        AdmissionsVectorIndex emptyIndex = mock(AdmissionsVectorIndex.class);
        when(emptyIndex.isReady()).thenReturn(false);
        var likeOnlyResearch = new AdmissionsResearchService(
                knowledgeItems, materials, conflicts, disabled, embeddingClient, emptyIndex, types, schedules);
        var pack = new org.springframework.transaction.support.TransactionTemplate(txManager)
                .execute(st -> likeOnlyResearch.research("하버드대학교 장학금 제도 알려주세요"));

        // then
        assertThat(pack.getConfirmedHits()).isEmpty();
        assertThat(pack.hasConfirmedEvidence()).isFalse();
    }

    // ── PR #840 결함 수정 검증 ────────────────────────────────────────────

    private java.util.List<String> confirmedTitles(String question) {
        return researchService.research(question).getConfirmedHits().stream()
                .map(e -> e.getTitle() == null ? "" : e.getTitle()).toList();
    }

    private String sourceTitleOf(AdmissionsResearchService.Evidence e) {
        return e.getNote() == null ? "" : e.getNote();
    }

    @Test
    @DisplayName("⑧ 고려대 서울 질문에는 세종 항목이 없고, 세종 질문에는 세종 항목만 나온다 (지식 검색 경로)")
    void knowledgePath_separatesCampuses() {
        // when
        var seoul = researchService.research("고려대 서울캠퍼스 수시 전형").getConfirmedHits();
        var sejong = researchService.research("고려대 세종캠퍼스 수시 전형").getConfirmedHits();

        // then
        assertThat(seoul).noneMatch(e -> e.getTitle().contains("세종캠퍼스") || sourceTitleOf(e).contains("세종캠퍼스"));
        assertThat(sejong).isNotEmpty().allMatch(e -> e.getTitle().contains("세종캠퍼스"));
    }

    @Test
    @DisplayName("⑨ 수시 질문에 정시 항목이, 정시 질문에 수시 항목이 섞이지 않는다")
    void knowledgePath_separatesSusiAndJungsi() {
        // when
        var jungsi = researchService.research("경희대 정시 수능최저").getConfirmedHits();
        var susi = researchService.research("경희대 수시 전형").getConfirmedHits();

        // then
        assertThat(jungsi).isNotEmpty().allMatch(e -> sourceTitleOf(e).contains("정시") && !sourceTitleOf(e).contains("수시모집"));
        assertThat(susi).isNotEmpty().allMatch(e -> sourceTitleOf(e).contains("수시") && !sourceTitleOf(e).contains("정시모집"));
    }

    @Test
    @DisplayName("⑩ 입시와 무관한 질문에는 확정 근거가 붙지 않는다")
    void irrelevantQuestions_haveNoEvidence() {
        // when / then
        assertThat(confirmedTitles("오늘 점심 뭐 먹을까요")).isEmpty();
        assertThat(confirmedTitles("태양계 행성 순서를 알려줘")).isEmpty();
        assertThat(researchService.research("오늘 점심 뭐 먹을까요").getPlannedHits()).isEmpty();
    }

    @Test
    @DisplayName("⑩-2 유사도 하한 보정 세트 — 무관한 질문 10개는 하한(0.5)에서 모두 근거 없음 (실측 최대 유사도 0.224)")
    void similarityFloor_rejectsAllMeasuredIrrelevantQuestions() {
        // given: 해시 임베더 기준 실측 — 무관 질문의 최근접 유사도는 0.02~0.22. 실제 모델 값은 운영 전 같은 세트로 다시 보정한다.
        String[] irrelevant = {"오늘 점심 뭐 먹을까요", "태양계 행성 순서를 알려줘", "주말에 볼만한 영화 추천해줘", "서울 날씨 어때요",
                "파이썬 반복문 문법", "축구 월드컵 우승국", "비트코인 시세", "김치찌개 레시피", "강아지 훈련 방법", "여행 갈 때 준비물"};

        // when / then
        for (String q : irrelevant) {
            assertThat(confirmedTitles(q)).as(q).isEmpty();
            double top = vectorIndex.search(embeddingClient.embed(q), 1).get(0).getSimilarity();
            assertThat(top).as("최근접 유사도 " + q).isLessThan(new AdmissionsEmbeddingProperties().getMinSimilarity());
        }
    }

    @Test
    @DisplayName("⑪ 제목·URL뿐인 공식자료는 지식 항목(사실 근거)이 되지 않는다")
    void materials_neverBecomeFactItems() {
        // then
        assertThat(knowledgeItems.findAll()).hasSize(12)
                .noneMatch(k -> k.getDedupKey().startsWith("material:"))
                .allMatch(k -> k.getDedupKey().startsWith("type:") || k.getDedupKey().startsWith("schedule:"));
    }

    @Test
    @DisplayName("⑫ 원본 전형 행이 ARCHIVED되면 그 지식 항목은 검색에서 빠진다")
    void archivedSourceRow_isExcludedFromKnowledgeSearch() {
        // given
        var before = confirmedTitles("연세대학교 논술전형");
        assertThat(before).anyMatch(t -> t.contains("논술전형"));
        var row = types.findAll().stream().filter(t -> t.getName().equals("논술전형")).findFirst().orElseThrow();
        row.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
        types.save(row);

        // when
        var after = confirmedTitles("연세대학교 논술전형");

        // then (항목은 ACTIVE로 남아 있어도 원본이 비활성이면 근거가 아니다)
        assertThat(after).noneMatch(t -> t.contains("논술전형"));
    }

    @Test
    @DisplayName("⑬ 출처 자료가 ARCHIVED되면 그 자료에서 나온 항목은 모두 검색에서 빠진다")
    void archivedMaterial_excludesItsItems() {
        // given
        var material = materials.findAll().stream().filter(m -> m.getTitle().contains("연세대학교 2027학년도 수시")).findFirst().orElseThrow();
        material.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
        materials.save(material);

        // when
        var hits = researchService.research("연세대학교 수시 전형").getConfirmedHits();

        // then
        assertThat(hits).noneMatch(e -> sourceTitleOf(e).contains("연세대학교 2027학년도 수시"));
    }

    @Test
    @DisplayName("⑭ 재색인 직후 수동 reload 없이 검색 인덱스에 반영된다")
    void reindex_updatesIndexWithoutManualReload() {
        // given: 항목 하나의 임베딩을 지우고 인덱스를 다시 읽어 11건으로 만든다
        var victim = embeddings.findAll().get(0);
        embeddings.delete(victim);
        vectorIndex.reload();
        assertThat(vectorIndex.size()).isEqualTo(11);

        // when
        var result = embeddingService.reindexMissing();

        // then
        assertThat(result.embedded()).isEqualTo(1);
        assertThat(vectorIndex.size()).as("커밋 직후 자동 reload").isEqualTo(12);
    }

    @Test
    @DisplayName("⑮ 임베딩 서버 장애 시 키워드 검색으로 전환되고 근거가 유지된다")
    void serverDown_fallsBackToKeywordSearch() {
        // given
        serverDown = true;

        // when
        var pack = researchService.research("연세대학교 학생부교과전형 추천형 알려주세요");

        // then
        assertThat(pack.getConfirmedHits()).anyMatch(e -> e.getTitle().contains("학생부교과전형[추천형]"));
    }

    @Test
    @DisplayName("⑯ 부분 실패: 일부 항목만 실패하면 실패 사유를 보고하고, 복구 후 미적재분만 다시 임베딩한다")
    void partialFailure_isReportedAndRecoverable() {
        // given: 서강대 논술 항목 임베딩을 지우고, 그 항목 텍스트에서만 서버가 실패하게 한다
        java.util.Set<Long> sogangIds = knowledgeItems.findAll().stream()
                .filter(k -> k.getTitle().contains("서강대학교 2027학년도 논술"))
                .map(AdmissionsKnowledgeItem::getKnowledgeId).collect(java.util.stream.Collectors.toSet());
        embeddings.findAll().stream().filter(e -> sogangIds.contains(e.getKnowledgeId()))
                .toList().forEach(embeddings::delete);
        vectorIndex.reload();
        failWhenContains = "서강대학교 2027학년도 논술";

        // when
        var failed = embeddingService.reindexMissing();

        // then
        assertThat(failed.failed()).isEqualTo(1);
        assertThat(failed.failures().get(0)).contains("응답 없음");
        assertThat(vectorIndex.size()).isEqualTo(11);

        // when: 서버 복구 후 재실행
        failWhenContains = null;
        var recovered = embeddingService.reindexMissing();

        // then
        assertThat(recovered.embedded()).isEqualTo(1);
        assertThat(recovered.failed()).isZero();
        assertThat(vectorIndex.size()).isEqualTo(12);
        assertThat(embeddings.count()).as("행 중복 없음").isEqualTo(12);
    }

    @Test
    @DisplayName("⑰ 재실행·중복 생성: build를 다시 실행해도 항목·임베딩이 늘지 않는다")
    void rerunDoesNotDuplicate() {
        // when
        var again = buildService.buildAll();
        var reindex = embeddingService.reindexMissing();

        // then
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.unchanged()).isEqualTo(12);
        assertThat(reindex.embedded()).isZero();
        assertThat(knowledgeItems.count()).isEqualTo(12);
        assertThat(embeddings.count()).isEqualTo(12);
    }

    @Test
    @DisplayName("⑱ 제출서류 원문 일치: 시드의 제출서류가 전형 상세에 그대로 들어가고, 제출서류를 물으면 맨 앞에 나온다")
    void submissionDocuments_matchSourceVerbatim() throws Exception {
        // given: 픽스처(v3.3 실제 행)의 제출서류 원문
        com.fasterxml.jackson.databind.JsonNode seed;
        try (InputStream in = getClass().getResourceAsStream("/admissions/golden-seed-v33-excerpt.json")) {
            seed = new ObjectMapper().readTree(in);
        }
        var khuRow = java.util.stream.StreamSupport.stream(seed.path("ipsi_admission_types").spliterator(), false)
                .filter(n -> n.path("university_code").asText().equals("KHU") && n.path("type_name").asText().equals("학생부교과(지역균형전형)"))
                .findFirst().orElseThrow();
        java.util.List<String> docs = new java.util.ArrayList<>();
        khuRow.path("submission_documents").forEach(n -> docs.add(n.asText()));
        assertThat(docs).hasSize(6);

        // when
        var fact = factSearchService.search("경희대학교 수시 지역균형전형 제출서류 알려주세요", null);

        // then
        var evidence = fact.evidence().stream().filter(e -> e.getTitle().contains("학생부교과(지역균형전형)")).findFirst().orElseThrow();
        assertThat(evidence.getExcerpt()).startsWith("제출서류: " + String.join(" | ", docs));
        // 제출서류가 없는 전형은 임의로 채우지 않고 '확인되지 않음'
        var sogang = factSearchService.search("서강대학교 논술전형 제출서류", null).evidence().stream()
                .filter(e -> e.getTitle().contains("논술(일반)전형")).findFirst().orElseThrow();
        assertThat(sogang.getExcerpt()).contains("제출서류: 확인되지 않음");
    }
}
