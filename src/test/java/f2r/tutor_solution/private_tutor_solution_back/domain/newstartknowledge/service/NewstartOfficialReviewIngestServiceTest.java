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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;

/**
 * NewstartOfficialReviewIngestService given/when/then 단위 테스트.
 * repository는 Mockito 메모리 fake — DB 없이 ingest 계약(REVIEW 강제·멱등성·관계)을 검증한다.
 */
class NewstartOfficialReviewIngestServiceTest {

    @TempDir Path tempDir;

    private NewstartKnowledgeItemRepository knowledgeRepository;
    private NewstartKnowledgeRelationRepository relationRepository;
    private NewstartOfficialReviewIngestService service;
    private Map<String, NewstartKnowledgeItem> store;
    private List<NewstartKnowledgeRelation> relations;
    private AtomicLong ids;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        relations = new ArrayList<>();
        ids = new AtomicLong(1);
        knowledgeRepository = mock(NewstartKnowledgeItemRepository.class);
        when(knowledgeRepository.findByTitle(any())).thenAnswer((Answer<Optional<NewstartKnowledgeItem>>)
                inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(knowledgeRepository.save(any())).thenAnswer((Answer<NewstartKnowledgeItem>) inv -> {
            NewstartKnowledgeItem item = inv.getArgument(0);
            item.setKnowledgeId(ids.getAndIncrement());
            store.put(item.getTitle(), item);
            return item;
        });
        relationRepository = mock(NewstartKnowledgeRelationRepository.class);
        when(relationRepository.findAll()).thenAnswer((Answer<List<NewstartKnowledgeRelation>>) inv ->
                new ArrayList<>(relations));
        when(relationRepository.save(any())).thenAnswer((Answer<NewstartKnowledgeRelation>) inv -> {
            NewstartKnowledgeRelation r = inv.getArgument(0);
            relations.add(r);
            return r;
        });
        service = new NewstartOfficialReviewIngestService(knowledgeRepository, relationRepository);
    }

    private Path fixtureDir() throws Exception {
        Path base = tempDir.resolve("research-official");
        Path comparison = base.resolve("comparison");
        Path ccrs = base.resolve("ccrs");
        Files.createDirectories(comparison);
        Files.createDirectories(ccrs);
        Files.writeString(comparison.resolve("comparison_matrix.json"), """
                {"matrix_id":"T","rows":[
                {"item":"원금감면","ns":"순부채 30~80%","pw":"미상각 30%","evidence_ns":"EV-A","evidence_pw":"EV-B"},
                {"item":"통신채무","ns":"대상 아님","pw":"제5조 대상","evidence_ns":"EV-C","evidence_pw":"EV-D"}]}""");
        Files.writeString(comparison.resolve("property_matrix.json"), """
                {"matrix_id":"T","rows":[
                {"item":"가상자산","ns":"반영","pw":"미확인","status":"UNKNOWN"}]}""");
        Files.writeString(comparison.resolve("debt_matrix.json"), """
                {"matrix_id":"T","rows":[
                {"debt":"세금","status":"OTHER_SYSTEM","note":"별도 체계"}]}""");
        Files.writeString(comparison.resolve("evidence_ns_property_2026.json"), """
                {"evidence_id":"EV-A","title":"T","source_url":"https://example.com","source_date":"2026-06-25",
                "findings":{"rate":{"content":"30~80%"}}}""");
        Files.writeString(comparison.resolve("concepts.json"), """
                {"concepts":{"A":{"definition":"상태 개념"}}}""");
        Files.writeString(ccrs.resolve("k01_x.json"), """
                {"title":"[부실우려차주 정의]","source_type":"OFFICIAL_CURRENT","category":["부실우려"],
                "normalized_content":"c","source_name":"T","source_url":"https://example.com",
                "source_date":"2026-09-21","tags":"t","status":"ACTIVE"}}""");
        Files.writeString(ccrs.resolve("k02_y.json"), """
                {"title":"[부실우려차주 연체기준]","source_type":"OFFICIAL_CURRENT","category":["부실우려"],
                "normalized_content":"c","source_name":"T","source_url":"https://example.com",
                "source_date":"2026-09-21","tags":"t","status":"REVIEW","review_state":"CONFLICT_REVIEW"}""");
        Files.writeString(ccrs.resolve("k02_evidence_10day_vs_31day.json"), """
                {"title":"[부실우려 연체하한 evidence: 10일 vs 31일]","source_type":"OFFICIAL_CURRENT",
                "category":["부실우려"],"source_url":"https://example.com","source_date":"2026-09-21",
                "tags":"t","status":"REVIEW","review_state":"CONFLICT_REVIEW"}""");
        return base;
    }

    @Test
    @DisplayName("FIRST INGEST: 생성된 모든 item은 REVIEW이며 ACTIVE가 없다")
    void firstIngestCreatesOnlyReview() throws Exception {
        // given
        Path dir = fixtureDir();

        // when
        NewstartOfficialReviewIngestService.IngestReport report = service.ingestOfficialReview(dir);

        // then
        assertThat(report.created()).isGreaterThan(0);
        assertThat(report.errors()).isEmpty();
        assertThat(store).isNotEmpty();
        assertThat(store.values())
                .allMatch(i -> i.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        assertThat(store.values()).noneMatch(i -> i.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.ACTIVE);
        // ACTIVE 표기 JSON 1건은 REVIEW로 downgrade
        assertThat(report.downgraded()).isEqualTo(1);
    }

    @Test
    @DisplayName("SECOND INGEST: 추가 생성 0건 — title 기준 멱등성")
    void secondIngestIsIdempotent() throws Exception {
        // given
        Path dir = fixtureDir();
        NewstartOfficialReviewIngestService.IngestReport first = service.ingestOfficialReview(dir);
        int sizeAfterFirst = store.size();

        // when
        NewstartOfficialReviewIngestService.IngestReport second = service.ingestOfficialReview(dir);

        // then
        assertThat(second.created()).isEqualTo(0);
        assertThat(second.skipped()).isEqualTo(first.created());
        assertThat(store.size()).isEqualTo(sizeAfterFirst);
    }

    @Test
    @DisplayName("k02 쌍방 저장 시 CONTRADICTS 관계 1건 생성, 재ingest 중복 없음")
    void conflictRelationCreatedOnce() throws Exception {
        // given
        Path dir = fixtureDir();

        // when
        service.ingestOfficialReview(dir);
        service.ingestOfficialReview(dir);

        // then
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getRelationType())
                .isEqualTo(NewstartKnowledgeRelation.RelationType.CONTRADICTS);
    }

    @Test
    @DisplayName("UNKNOWN 항목은 confidence UNKNOWN으로 보존된다")
    void unknownPreserved() throws Exception {
        // given
        Path dir = fixtureDir();

        // when
        service.ingestOfficialReview(dir);

        // then
        assertThat(store.get("[재산/UNKNOWN] 가상자산").getConfidence()).isEqualTo("UNKNOWN");
        assertThat(store.get("[재산/UNKNOWN] 가상자산").getTags()).contains("UNKNOWN");
    }

    @Test
    @DisplayName("answerability: 근거 충분성 판정 로직")
    void answerabilityVerdicts() {
        // given
        List<NewstartTop10Answerability.ItemView> views = List.of(
                new NewstartTop10Answerability.ItemView("[비교/새출발기금] 원금감면",
                        "dimension:PRINCIPAL_REDUCTION,REVIEW,evidence:EV-A", "EV-A", "OFFICIAL_CURRENT"),
                new NewstartTop10Answerability.ItemView("[공식] [부실우려차주 연체기준]",
                        "REVIEW,CONFLICT_REVIEW", "", "OFFICIAL_CURRENT"));

        // when
        var full = NewstartTop10Answerability.check(new ObjectMapper().createObjectNode()
                .set("questions", new ObjectMapper().createArrayNode()), views);

        // then
        assertThat(full).isEmpty();

        // given — needs 전부 충족
        var node = checkNode("[{\"q\":\"Q1\",\"needs\":[\"EV-A\"]},"
                + "{\"q\":\"Q2\",\"needs\":[\"EV-Z\"]},"
                + "{\"q\":\"Q3\",\"needs\":[\"EV-A\",\"EV-Z\"]},"
                + "{\"q\":\"Q4\",\"needs\":[\"CONFLICT_REVIEW\"]}]");

        // when
        var results = NewstartTop10Answerability.check(node, views);

        // then
        assertThat(results.get(0).verdict())
                .isEqualTo(NewstartTop10Answerability.Verdict.ANSWERABLE);
        assertThat(results.get(0).sourceTypes()).contains("OFFICIAL_CURRENT");
        assertThat(results.get(1).verdict()).isEqualTo(NewstartTop10Answerability.Verdict.UNKNOWN);
        assertThat(results.get(2).verdict()).isEqualTo(NewstartTop10Answerability.Verdict.PARTIAL);
        assertThat(results.get(3).verdict()).isEqualTo(NewstartTop10Answerability.Verdict.CONFLICT);
    }

    private com.fasterxml.jackson.databind.JsonNode checkNode(String questionsJson) {
        try {
            return new ObjectMapper().readTree("{\"questions\":" + questionsJson + "}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
