package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PHASE 2.5 최종 검증 — docs/newstart/research-official/ 실제 자료를 로컬 MySQL 테스트 DB에
 * 실제 REVIEW로 적재하고, 메모리 fake로만 검증했던 계약(DB 세션·unique 제약·멱등성·관계)을 확인한다.
 *
 * <p>로컬 DB가 없으면 실행되지 않도록 환경변수로 gate한다(운영 DB와 무관, prd profile 미사용).
 * 활성화: NEWSTART_WIKI_MYSQL_TEST=1 && SPRING_DATASOURCE_URL=localhost:3308/f2r_db
 *
 * <p>불변식 검증: ACTIVE 0건 · title 멱등성 · CONTRADICTS 1건 · UNKNOWN 보존 · CONFLICT_REVIEW 보존.
 */
@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "spring.flyway.clean-disabled=true"
})
@EnabledIfEnvironmentVariable(named = "NEWSTART_WIKI_MYSQL_TEST", matches = ".+")
@DisplayName("NewStart Wiki — 실제 MySQL 테스트 DB REVIEW 적재·최종 검증")
class NewstartOfficialReviewIngestMySqlTest {

  private static final Path RESEARCH_OFFICIAL = Paths.get("docs", "newstart", "research-official");
  private static final String INGEST_AUTHORITY = "부실차주 Wiki REVIEW ingest";

  @Autowired NewstartOfficialReviewIngestService ingestService;
  @Autowired NewstartKnowledgeItemRepository knowledgeRepository;
  @Autowired NewstartKnowledgeRelationRepository relationRepository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void resetIngestedRows() {
    // 재실행 가능하도록 이 ingest가 만든 행만 제거한다(수동 ADMIN entry는 건드리지 않는다).
    jdbc.update("DELETE FROM newstart_knowledge_relations WHERE created_by = ?", "INGESTION_BOT");
    jdbc.update("DELETE FROM newstart_knowledge_status_log WHERE changed_by = ?", "INGESTION_BOT");
    jdbc.update("DELETE FROM newstart_knowledge_items WHERE authority = ?", INGEST_AUTHORITY);
  }

  private List<NewstartKnowledgeItem> ingested() {
    return jdbc.query("SELECT * FROM newstart_knowledge_items WHERE authority = ?",
        (rs, i) -> NewstartKnowledgeItem.builder()
            .knowledgeId(rs.getLong("knowledge_id"))
            .title(rs.getString("title"))
            .tags(rs.getString("tags"))
            .relatedRuleIds(rs.getString("related_rule_ids"))
            .confidence(rs.getString("confidence"))
            .status(NewstartKnowledgeItem.KnowledgeStatus.valueOf(rs.getString("status")))
            .sourceType(NewstartKnowledgeItem.SourceType.valueOf(rs.getString("source_type")))
            .build(),
        INGEST_AUTHORITY);
  }

  @Test
  @DisplayName("FIRST INGEST: 실제 공식자료가 MySQL에 적재되고 전 건 REVIEW, ACTIVE 0건")
  void firstIngestPersistsAllReviewNoActive() {
    var report = ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);

    assertThat(report.errors()).isEmpty();
    assertThat(report.created()).isPositive();

    List<NewstartKnowledgeItem> rows = ingested();
    assertThat(rows).hasSize(report.created());
    assertThat(rows).allMatch(r -> r.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.REVIEW);

    Integer active = jdbc.queryForObject(
        "SELECT COUNT(*) FROM newstart_knowledge_items WHERE authority = ? AND status = 'ACTIVE'",
        Integer.class, INGEST_AUTHORITY);
    assertThat(active).isZero();
  }

  @Test
  @DisplayName("SECOND INGEST: DB unique 제약 하에서 title 멱등성 — 추가 생성 0건")
  void secondIngestIsIdempotent() {
    var first = ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);
    var second = ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);

    assertThat(second.created()).isZero();
    assertThat(second.skipped()).isEqualTo(first.created());
    assertThat(ingested()).hasSize(first.created());
  }

  @Test
  @DisplayName("CONFLICT: 10일 vs 31일 연체하한 CONTRADICTS 관계 1건, 재ingest 중복 없음")
  void conflictRelationPersistedOnce() {
    ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);
    ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);

    List<NewstartKnowledgeRelation> rels = relationRepository.findAll().stream()
        .filter(r -> r.getRelationType() == NewstartKnowledgeRelation.RelationType.CONTRADICTS)
        .toList();
    assertThat(rels).hasSize(1);

    Long from = knowledgeRepository.findByTitle("[공식] [부실우려차주 연체기준]")
        .map(NewstartKnowledgeItem::getKnowledgeId).orElse(null);
    Long to = knowledgeRepository.findByTitle("[공식] [부실우려 연체하한 evidence: 10일 vs 31일]")
        .map(NewstartKnowledgeItem::getKnowledgeId).orElse(null);
    assertThat(from).isNotNull();
    assertThat(to).isNotNull();
    assertThat(rels.get(0).getFromKnowledgeId()).isEqualTo(from);
    assertThat(rels.get(0).getToKnowledgeId()).isEqualTo(to);
  }

  @Test
  @DisplayName("UNKNOWN: 추론으로 채우지 않고 confidence UNKNOWN으로 보존된다")
  void unknownItemsPreserved() {
    ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);

    List<NewstartKnowledgeItem> unknown = ingested().stream()
        .filter(r -> "UNKNOWN".equals(r.getConfidence()))
        .toList();
    assertThat(unknown).isNotEmpty();
    assertThat(unknown).allMatch(r -> r.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
  }

  @Test
  @DisplayName("Top10: DB 적재 근거만으로 10-10 ANSWERABLE 판정")
  void top10AnswerableFromDbRows() throws Exception {
    ingestService.ingestOfficialReview(RESEARCH_OFFICIAL);

    List<NewstartTop10Answerability.ItemView> views = ingested().stream()
        .map(r -> new NewstartTop10Answerability.ItemView(r.getTitle(), r.getTags(),
            r.getRelatedRuleIds(), r.getSourceType().name()))
        .toList();
    JsonNode top10 = new ObjectMapper().readTree(
        Files.readString(RESEARCH_OFFICIAL.resolve("comparison").resolve("top10_questions.json")));

    var results = NewstartTop10Answerability.check(top10, views);

    assertThat(results).hasSize(10);
    assertThat(results)
        .allMatch(r -> r.verdict() == NewstartTop10Answerability.Verdict.ANSWERABLE,
            "ANSWERABLE 아닌 질문: " + results.stream()
                .filter(r -> r.verdict() != NewstartTop10Answerability.Verdict.ANSWERABLE)
                .map(r -> r.question() + "=" + r.verdict()).toList());
  }
}
