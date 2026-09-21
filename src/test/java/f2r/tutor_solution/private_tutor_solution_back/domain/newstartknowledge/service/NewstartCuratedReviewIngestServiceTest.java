package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

/**
 * NewstartCuratedReviewIngestService given/when/then 단위 테스트 — 실제 커쳐이션 피처
 * (docs/newstart/ebook/ebook_sections.json, data/newstart-cafe/gamian/articles)를 읽어서
 * PR #361 REVIEW 적재 계약을 검증한다. repository는 Mockito 메모리 fake.
 */
@DisplayName("Curated REVIEW ingest — EBOOK 19 + CASE 11")
class NewstartCuratedReviewIngestServiceTest {

    private static final Path EBOOK_SECTIONS = Paths.get("docs", "newstart", "ebook", "ebook_sections.json");
    private static final Path GAMIAN_DIR = Paths.get("data", "newstart-cafe", "gamian", "articles");

    private final Map<String, NewstartKnowledgeItem> store = new HashMap<>();
    private final List<NewstartKnowledgeRelation> relations = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);

    private NewstartKnowledgeItemRepository knowledgeRepository;
    private NewstartKnowledgeRelationRepository relationRepository;
    private NewstartDataIngestionService dataIngestionService;
    private NewstartCuratedReviewIngestService service;

    @BeforeEach
    void setUp() {
        store.clear();
        relations.clear();
        ids.set(1);

        knowledgeRepository = mock(NewstartKnowledgeItemRepository.class);
        when(knowledgeRepository.findByTitle(any())).thenAnswer(
                (Answer<Optional<NewstartKnowledgeItem>>) inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(knowledgeRepository.save(any())).thenAnswer((Answer<NewstartKnowledgeItem>) inv -> {
            NewstartKnowledgeItem item = inv.getArgument(0);
            item.setKnowledgeId(ids.getAndIncrement());
            store.put(item.getTitle(), item);
            return item;
        });

        relationRepository = mock(NewstartKnowledgeRelationRepository.class);
        when(relationRepository.findAll()).thenAnswer(
                (Answer<List<NewstartKnowledgeRelation>>) inv -> new ArrayList<>(relations));
        when(relationRepository.save(any())).thenAnswer((Answer<NewstartKnowledgeRelation>) inv -> {
            NewstartKnowledgeRelation r = inv.getArgument(0);
            r.setRelationId(ids.getAndIncrement());
            relations.add(r);
            return r;
        });

        dataIngestionService = mock(NewstartDataIngestionService.class);
        when(dataIngestionService.ingestEmpiricalCases(any())).thenAnswer(inv ->
                NewstartDataIngestionService.IngestionResult.builder()
                        .success(11).skipped(0).failed(0).errors(List.of()).build());

        service = new NewstartCuratedReviewIngestService(
                knowledgeRepository, relationRepository, dataIngestionService);
    }

    private NewstartKnowledgeItem byPrefix(String prefix) {
        return store.values().stream().filter(i -> i.getTitle().startsWith(prefix)).findFirst().orElseThrow();
    }

    // ---- A/B: 파싱·매핑·REVIEW 강제 ----

    @Test
    @DisplayName("A. 실제 피처 19 section + CASE 11건이 전부 매핑된다")
    void givenRealFixtures_whenIngest_thenAllMapped() {
        var report = service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        assertThat(report.ebookCreated()).isEqualTo(19);
        assertThat(report.caseCreated()).isEqualTo(11);
        assertThat(report.caseRowsCreated()).isEqualTo(11);
        assertThat(report.errors()).isEmpty();
        assertThat(store).hasSize(30);
    }

    @Test
    @DisplayName("B. 신규 item은 예외 없이 status=REVIEW, sourceType EBOOK/CASE — ACTIVE 생성 0 (I 포함)")
    void givenIngest_whenDone_thenReviewOnly() {
        service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        assertThat(store.values()).allSatisfy(item ->
                assertThat(item.getStatus()).isEqualTo(NewstartKnowledgeItem.KnowledgeStatus.REVIEW));
        assertThat(store.values()).filteredOn(i ->
                i.getSourceType() == NewstartKnowledgeItem.SourceType.EBOOK).hasSize(19);
        assertThat(store.values()).filteredOn(i ->
                i.getSourceType() == NewstartKnowledgeItem.SourceType.CASE).hasSize(11);
        assertThat(store.values()).noneMatch(i ->
                i.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.ACTIVE);
    }

    @Test
    @DisplayName("A2. section 구조 보존: chapter/source_lines/핵심주장/실무판단/주의/relations/relatedCaseIds")
    void givenSection_whenIngest_thenStructurePreserved() {
        service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);
        NewstartKnowledgeItem ebk21 = byPrefix("[전자책] EBK-2-1");

        assertThat(ebk21.getTitle()).contains("제2장").contains("개인회생 vs 부실차주");
        assertThat(ebk21.getContent()).contains("source_lines: L3-55")
                .contains("## 핵심 주장").contains("## 실무 판단").contains("## 주의")
                .contains("CONFLICT_REVIEW")
                .contains("EV-NS-PROP-2026");
        assertThat(ebk21.getRelatedRuleIds()).contains("MX-COMPARE-14").contains("EV-NS-PROP-2026");
        assertThat(ebk21.getSourceType()).isEqualTo(NewstartKnowledgeItem.SourceType.EBOOK);
        assertThat(ebk21.getSourceUrl()).isEqualTo("docs/newstart/ebook/ebook_sections.json");
    }

    // ---- D: confirmed/prediction 구분 ----

    @Test
    @DisplayName("D. 결과확인 5·부분결과 2·운영자예측 4로 분류되고, 예측값은 확정 필드로 이동하지 않는다")
    void givenCases_whenIngest_thenClassificationSeparated() {
        service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        List<NewstartKnowledgeItem> cases = store.values().stream()
                .filter(i -> i.getTitle().startsWith("[사례]")).toList();
        assertThat(cases).filteredOn(i -> i.getTags().contains("RESULT_CONFIRMED")).hasSize(5);
        assertThat(cases).filteredOn(i -> i.getTags().contains("RESULT_PARTIAL")).hasSize(2);
        assertThat(cases).filteredOn(i -> i.getTags().contains("OPERATOR_PREDICTION")).hasSize(4);

        // 5948: 운영자 예측 61%가 있고 확정은 없다 → content에 확정 UNKNOWN
        NewstartKnowledgeItem c5948 = byPrefix("[사례] 5948");
        assertThat(c5948.getContent()).contains("확정 감면율: UNKNOWN")
                .contains("예측 감면율(운영자): 61");
        // 5866: 확정 90 있고 예측 없음
        NewstartKnowledgeItem c5866 = byPrefix("[사례] 5866");
        assertThat(c5866.getContent()).contains("확정 감면율: 90")
                .contains("예측 감면율(운영자): UNKNOWN");
    }

    // ---- E: UNKNOWN 보존 ----

    @Test
    @DisplayName("E. 없는 값은 UNKNOWN으로 보존(추측 금지) — EBOOK confidence 미기재 포함")
    void givenSparseData_whenIngest_thenUnknownPreserved() {
        service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        NewstartKnowledgeItem c5947 = byPrefix("[사례] 5947");
        assertThat(c5947.getContent()).contains("예측 vs 실제: UNKNOWN")
                .contains("확정 감면율: UNKNOWN");
        NewstartKnowledgeItem ebk41 = byPrefix("[전자책] EBK-4-1");
        assertThat(ebk41.getConfidence()).isNull();
    }

    // ---- F: PII ----

    @Test
    @DisplayName("F. RAG 적재물에 개인식별정보(닉네임 필드·이메일·휴대전화)가 없다")
    void givenIngest_whenSerialized_thenNoPii() throws Exception {
        service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);
        ObjectMapper m = new ObjectMapper();
        String dump = m.writeValueAsString(store.values());

        assertThat(dump).doesNotContain("authorNickname");
        assertThat(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(dump).find()).isFalse();
        assertThat(Pattern.compile("01[019]-?\\d{3,4}-?\\d{4}").matcher(dump).find()).isFalse();
    }

    // ---- G/H: 충돌 보존 + 멱등 ----

    @Test
    @DisplayName("G. 공식 evidence/k02 item이 이미 있으면 CONFLICT_REVIEW 태그+CONTRADICTS relation이 보존된다")
    void givenOfficialItemsExist_whenIngest_thenConflictRelationsCreated() {
        seed("[Evidence] [2026 새출발기금 재산심사·감면차등화 evidence]");
        seed("[공식] [부실우려차주 연체기준]");

        var report = service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        // EBK-2-1, EBK-2-2, EBK-7-1, 사례5866, 사례5952 → EV 5건 / EBK-5-1, EBK-6-2 → k02 2건
        assertThat(report.relationsCreated()).isEqualTo(7);
        assertThat(relations).allSatisfy(r ->
                assertThat(r.getRelationType()).isEqualTo(NewstartKnowledgeRelation.RelationType.CONTRADICTS));
        assertThat(byPrefix("[전자책] EBK-2-1").getTags()).contains("CONFLICT_REVIEW");
        assertThat(byPrefix("[사례] 5866").getTags()).contains("CONFLICT_REVIEW");
    }

    @Test
    @DisplayName("G2. 공식 item이 없으면 relation 0 — 오류가 아니라 대기 상태로 끝난다")
    void givenNoOfficialItems_whenIngest_thenNoRelationsNoErrors() {
        var report = service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        assertThat(report.relationsCreated()).isZero();
        assertThat(report.errors()).isEmpty();
        assertThat(relations).isEmpty();
    }

    @Test
    @DisplayName("H. 재실행 시 duplicate 0 (title 멱등 + relation dedupe)")
    void givenSecondRun_whenIngest_thenZeroDuplicates() {
        seed("[Evidence] [2026 새출발기금 재산심사·감면차등화 evidence]");
        seed("[공식] [부실우려차주 연체기준]");

        service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);
        int sizeAfterFirst = store.size();
        int relationsAfterFirst = relations.size();

        var second = service.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

        assertThat(second.ebookCreated()).isZero();
        assertThat(second.caseCreated()).isZero();
        assertThat(second.ebookSkipped()).isEqualTo(19);
        assertThat(second.caseSkipped()).isEqualTo(11);
        assertThat(second.relationsCreated()).isZero();
        assertThat(store).hasSize(sizeAfterFirst);
        assertThat(relations).hasSize(relationsAfterFirst);
    }

    private void seed(String title) {
        NewstartKnowledgeItem item = NewstartKnowledgeItem.builder()
                .knowledgeId(ids.getAndIncrement())
                .sourceType(NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT)
                .category("부실차주 비교")
                .title(title)
                .status(NewstartKnowledgeItem.KnowledgeStatus.REVIEW)
                .build();
        store.put(title, item);
    }
}
