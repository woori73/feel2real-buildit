package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V999 스키마 계약: 지피지기 Agent는 신규 테이블만 추가하고 기존 production 데이터를 건드리지 않는다.
 */
@DisplayName("지피지기 Agent V999 마이그레이션 계약")
class JipijigiAgentMigrationTest {

    private static final String PATH = "/db/migration/V999__add_newstart_jipijigi_agent.sql";

    private String sql() throws Exception {
        try (var input = getClass().getResourceAsStream(PATH)) {
            assertThat(input).as("V999 migration resource").isNotNull();
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
}
