package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
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
 * PHASE 2 curated REVIEW ingest — 로컬 MySQL 테스트 DB 최종 검증.
 *
 * <p>순서: 공식 ingest(EV/k02 시딩) → curated ingest → 불변식 확인. 운영 DB와 무관하며
 * 환경변수 게이트 없이는 실행되지 않는다. 정리 대상은 본 테스트들이 만든 행으로 한정한다.
 *
 * <p>검증: ACTIVE 0 · title/articleId 멱등(재실행 +0) · CONTRADICTS 7 · confirmed/prediction
 * 분류 · REVIEW retrieval 미노출 · empirical case 11.
 */
@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "spring.flyway.clean-disabled=true"
})
@EnabledIfEnvironmentVariable(named = "NEWSTART_WIKI_MYSQL_TEST", matches = ".+")
@DisplayName("NewStart Wiki — curated(EBOOK+CASE) MySQL REVIEW 적재 검증")
class NewstartCuratedReviewIngestMySqlTest {

  private static final Path EBOOK_SECTIONS =
      Paths.get("docs", "newstart", "ebook", "ebook_sections.json");
  private static final Path GAMIAN_DIR =
      Paths.get("data", "newstart-cafe", "gamian", "articles");
  private static final Path RESEARCH_OFFICIAL = Paths.get("docs", "newstart", "research-official");
  private static final List<String> OUR_AUTHORITIES = List.of(
      NewstartCuratedReviewIngestService.EBOOK_AUTHORITY,
      NewstartCuratedReviewIngestService.CASE_AUTHORITY);
  private static final List<String> CASE_IDS = List.of(
      "5862", "5866", "5867", "5884", "5904", "5947", "5948", "5952", "5957", "5988", "6010");

  @Autowired NewstartCuratedReviewIngestService curatedService;
  @Autowired NewstartOfficialReviewIngestService officialService;
  @Autowired NewstartDataIngestionService dataIngestionService;
  @Autowired NewstartKnowledgeRetrievalService retrievalService;
  @Autowired NewstartKnowledgeItemRepository knowledgeRepository;
  @Autowired NewstartKnowledgeRelationRepository relationRepository;
  @Autowired NewstartEmpiricalCaseRepository empiricalCaseRepository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void resetCuratedRowsAndSeedOfficial() {
    jdbc.update("""
        DELETE r FROM newstart_knowledge_relations r
        WHERE r.from_knowledge_id IN (SELECT knowledge_id FROM (
              SELECT knowledge_id FROM newstart_knowledge_items WHERE authority IN (?, ?)) t)
        """,
        OUR_AUTHORITIES.get(0), OUR_AUTHORITIES.get(1));
    jdbc.update("DELETE FROM newstart_knowledge_status_log WHERE changed_by = ?", "INGESTION_BOT");
    jdbc.update("DELETE FROM newstart_knowledge_items WHERE authority IN (?, ?)",
        OUR_AUTHORITIES.get(0), OUR_AUTHORITIES.get(1));
    for (String id : CASE_IDS) {
      jdbc.update("DELETE FROM newstart_empirical_cases WHERE article_id = ?", id);
    }

    // 충돌 관계 resolving용 공식 item 시딩 (멱등 — 이미 있으면 skip)
    officialService.ingestOfficialReview(RESEARCH_OFFICIAL);
  }

  private List<NewstartKnowledgeItem> curatedRows() {
    return jdbc.query("SELECT * FROM newstart_knowledge_items WHERE authority IN (?, ?)",
        (rs, i) -> NewstartKnowledgeItem.builder()
            .knowledgeId(rs.getLong("knowledge_id"))
            .title(rs.getString("title"))
            .tags(rs.getString("tags"))
            .status(NewstartKnowledgeItem.KnowledgeStatus.valueOf(rs.getString("status")))
            .sourceType(NewstartKnowledgeItem.SourceType.valueOf(rs.getString("source_type")))
            .build(),
        OUR_AUTHORITIES.get(0), OUR_AUTHORITIES.get(1));
  }

  @Test
  @DisplayName("FIRST INGEST: EBOOK 19 + CASE 11 + empirical case 11, 전 건 REVIEW · ACTIVE 0")
  void firstIngestPersistsReviewOnly() {
    var report = curatedService.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

    assertThat(report.errors()).isEmpty();
    assertThat(report.ebookCreated()).isEqualTo(19);
    assertThat(report.caseCreated()).isEqualTo(11);
    assertThat(report.caseRowsCreated()).isEqualTo(11);

    List<NewstartKnowledgeItem> rows = curatedRows();
    assertThat(rows).hasSize(30);
    assertThat(rows).allMatch(r -> r.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
    Integer active = jdbc.queryForObject(
        "SELECT COUNT(*) FROM newstart_knowledge_items WHERE authority IN (?, ?) AND status = 'ACTIVE'",
        Integer.class, OUR_AUTHORITIES.get(0), OUR_AUTHORITIES.get(1));
    assertThat(active).isZero();
    assertThat(CASE_IDS.stream()
        .filter(id -> empiricalCaseRepository.findByArticleId(id).isPresent()).count()).isEqualTo(11);
  }

  @Test
  @DisplayName("SECOND INGEST: knowledge title·case articleId 멱등 — duplicate 0, relation 중복 0")
  void secondIngestIsIdempotent() {
    var first = curatedService.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);
    var second = curatedService.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);
    var rows = dataIngestionService.ingestEmpiricalCases(GAMIAN_DIR.toString());

    assertThat(second.ebookCreated()).isZero();
    assertThat(second.caseCreated()).isZero();
    assertThat(second.ebookSkipped()).isEqualTo(19);
    assertThat(second.caseSkipped()).isEqualTo(11);
    assertThat(second.relationsCreated()).isZero();
    assertThat(rows.getSkipped()).isEqualTo(11);
    assertThat(rows.getSuccess()).isZero();
    assertThat(curatedRows()).hasSize(30);
    assertThat(first.relationsCreated()).isEqualTo(7);
  }

  @Test
  @DisplayName("CONFLICT: CONTRADICTS 7건(EV-NS-PROP↔EBK3+CASE2, k02↔EBK2) 원문 수치 무수정")
  void conflictRelationsPersisted() {
    curatedService.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

    List<NewstartKnowledgeRelation> rels = relationRepository.findAll().stream()
        .filter(r -> r.getRelationType() == NewstartKnowledgeRelation.RelationType.CONTRADICTS)
        .filter(r -> curatedRows().stream().anyMatch(c ->
            c.getKnowledgeId().equals(r.getFromKnowledgeId())))
        .toList();
    assertThat(rels).hasSize(7);

    NewstartKnowledgeItem ebk21 = knowledgeRepository.findByTitle(
        "[전자책] EBK-2-1 제2장 :: 개인회생 vs 부실차주: 왜 새출발기금이어야 하는가").orElseThrow();
    assertThat(ebk21.getTags()).contains("CONFLICT_REVIEW");
    assertThat(jdbc.queryForObject(
        "SELECT content FROM newstart_knowledge_items WHERE knowledge_id = ?",
        String.class, ebk21.getKnowledgeId())).contains("60~80%").doesNotContain("30~80%로 수정");
  }

  @Test
  @DisplayName("CLASSIFICATION: RESULT_CONFIRMED 5 · RESULT_PARTIAL 2 · OPERATOR_PREDICTION 4")
  void caseClassificationPersisted() {
    curatedService.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

    List<NewstartKnowledgeItem> cases = curatedRows().stream()
        .filter(r -> r.getTitle().startsWith("[사례]")).toList();
    assertThat(cases).filteredOn(r -> r.getTags().contains("RESULT_CONFIRMED")).hasSize(5);
    assertThat(cases).filteredOn(r -> r.getTags().contains("RESULT_PARTIAL")).hasSize(2);
    assertThat(cases).filteredOn(r -> r.getTags().contains("OPERATOR_PREDICTION")).hasSize(4);
  }

  @Test
  @DisplayName("RETRIEVAL: REVIEW 적재물은 production ACTIVE-only 검색에 0건 (J)")
  void reviewItemsNotVisibleInRetrieval() {
    curatedService.ingestCuratedReview(EBOOK_SECTIONS, GAMIAN_DIR);

    List<NewstartKnowledgeItemResponse> hits =
        retrievalService.retrieveForQuestion("전자책 코인 도박 청산가치 배우자 재산 산정", 50);
    assertThat(hits).noneMatch(h ->
        curatedRows().stream().anyMatch(c -> c.getKnowledgeId().equals(h.getKnowledgeId())));

    List<NewstartKnowledgeItemResponse> effective = retrievalService.findActiveByCategory("부실차주 사례", 50);
    assertThat(effective).isEmpty();
  }
}
