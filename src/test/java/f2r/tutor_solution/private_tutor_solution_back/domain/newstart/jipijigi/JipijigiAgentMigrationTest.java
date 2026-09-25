package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V1001 스키마 계약: 지피지기 Agent는 신규 테이블만 추가하고 기존 production 데이터를 건드리지 않는다.
 */
@DisplayName("지피지기 Agent V1001 마이그레이션 계약")
class JipijigiAgentMigrationTest {

    private static final String PATH = "/db/migration/V1001__add_newstart_jipijigi_agent.sql";

    private String sql() throws Exception {
        try (var input = getClass().getResourceAsStream(PATH)) {
            assertThat(input).as("V1001 migration resource").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void createsSixAgentTablesAndPreservesProvenance() throws Exception {
        assertThat(sql())
                .contains("CREATE TABLE newstart_jipijigi_target_boards")
                .contains("CREATE TABLE newstart_jipijigi_commands")
                .contains("CREATE TABLE newstart_jipijigi_questions")
                .contains("CREATE TABLE newstart_jipijigi_drafts")
                .contains("CREATE TABLE newstart_jipijigi_draft_status_log")
                .contains("CREATE TABLE newstart_jipijigi_research_traces")
                .contains("uk_jipijigi_board")
                .contains("uk_jipijigi_question_article")
                .contains("menu_id        BIGINT       NULL");
    }

    @Test
    void isSchemaOnly_andDoesNotTouchExistingRows() throws Exception {
        String s = sql();
        assertThat(s)
                .doesNotContain("INSERT INTO")
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE FROM")
                .doesNotContain("DROP TABLE")
                .doesNotContain("ALTER TABLE");
    }

    /** V1002: 답변 세션 축적 저장소 — dedupe 키와 승인 상태 분리, 출처별 근거 컬럼 분리. */
    @Test
    void answerSessionTableCarriesDedupeKeyAndProvenance() throws Exception {
        String s = sql("/db/migration/V1002__add_newstart_jipijigi_answer_session.sql");
        assertThat(s)
                .contains("CREATE TABLE newstart_jipijigi_answer_sessions")
                .contains("uk_jipijigi_session_fp (fingerprint)")
                .contains("uk_jipijigi_session_draft (draft_id)")
                .contains("review_state")
                .contains("ACCEPTED")
                .contains("evidence_internal")
                .contains("evidence_ccrs")
                .contains("evidence_borrower")
                .contains("evidence_official")
                .contains("db_hits")
                .contains("external_searched")
                .contains("operator_edited")
                .contains("published_as")
                .contains("follow_up_ref")
                .contains("source_comment_id");
        assertThat(s)
                .doesNotContain("INSERT INTO")
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE FROM")
                .doesNotContain("DROP TABLE")
                .doesNotContain("ALTER TABLE");
    }

    private String sql(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("migration resource " + path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
