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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 입시 임베딩 파이프라인 골든 테스트 — 실제 2027 시드 발췌본으로 끝까지 연결한다.
 *
 * <p>fixture: PR #836의 {@code seed-v3.2-ops.json}에서 연세·고려·서강·경희 4개 대학의
 * 실제 행(대학 4·자료 8·전형 9·일정 3)을 그대로 발췌한
 * {@code src/test/resources/admissions/golden-seed-v32-excerpt.json}.
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
                    return hashEmbed(text);
                }
            };
        }
    }

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

    @BeforeEach
    void setUp() throws Exception {
        // given: 실제 시드 발췌본 적재 → 구조화 행 승인 → 항목 생성 → 항목 승인 → 임베딩
        String seedJson;
        try (InputStream in = getClass().getResourceAsStream("/admissions/golden-seed-v32-excerpt.json")) {
            assertThat(in).as("golden fixture 리소스").isNotNull();
            seedJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        loader.loadSeed(seedJson);
        approveStructuredRows();

        var built = buildService.buildAll();
        assertThat(built.created()).as("전형 9 + 일정 3 + 자료 8 = 지식 항목 20건 생성").isEqualTo(20);

        knowledgeItems.findAll().forEach(k -> {
            k.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE);
            knowledgeItems.save(k);
        });

        var reindexed = embeddingService.reindexAll();
        assertThat(reindexed.failed()).as("임베딩 실패 없음: %s", reindexed.failures()).isZero();
        assertThat(reindexed.embedded()).isEqualTo(20);
        assertThat(vectorIndex.isReady()).isTrue();
        assertThat(vectorIndex.size()).isEqualTo(20);
    }

    @AfterEach
    void clean() {
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
                knowledgeItems, materials, conflicts, disabled, embeddingClient, emptyIndex);
        var pack = likeOnlyResearch.research("하버드대학교 장학금 제도 알려주세요");

        // then
        assertThat(pack.getConfirmedHits()).isEmpty();
        assertThat(pack.hasConfirmedEvidence()).isFalse();
    }
}
