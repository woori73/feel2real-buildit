package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("「바뀌는 입시」 첫 3편(V1111)과 상담 AI 근거 분리")
class AdmissionsArticleContentContractTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V1111__add_admissions_articles.sql");

    private static int count(String s, String needle) {
        return s.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    @DisplayName("given V1111 when 글마다 표·링크를 세면 then 원문과 같다(1-① 1·8, 1-② 1·8, 1-③ 1·5), 붙여 넣기 안내는 없다")
    void firstThreeArticlesMatchSource() throws Exception {
        // given
        String sql = Files.readString(MIGRATION);
        // when
        String[] inserts = sql.split("INSERT INTO admissions_articles");
        // then
        assertThat(inserts).hasSize(4);
        assertThat(inserts[1]).contains("'1-①'", "'내신 5등급제, 쉬워지는 게 아닙니다'", "'2026-11-13'");
        assertThat(inserts[2]).contains("'1-②'", "'통합형 수능, 무엇이 바뀌나'");
        assertThat(inserts[3]).contains("'1-③'", "'수능 영어 지문, AI가 만든다'").doesNotContain("1-④");
        int[][] expected = {{1, 8}, {1, 8}, {1, 5}};
        for (int i = 0; i < 3; i++) {
            assertThat(count(inserts[i + 1], "<table")).as("표 " + (i + 1)).isEqualTo(expected[i][0]);
            assertThat(count(inserts[i + 1], "<a href=")).as("링크 " + (i + 1)).isEqualTo(expected[i][1]);
        }
        assertThat(sql).doesNotContain("class='note'", "블로그 글쓰기", "<h1>");
    }

    @Test
    @DisplayName("given 입시 서비스 코드 when 글 저장소 참조를 찾으면 then 글 서비스·화면 말고는 상담 근거 쪽에서 쓰지 않는다")
    void articlesAreNotCounselingEvidence() throws Exception {
        // given
        Path root = Path.of("src/main/java/f2r/tutor_solution/private_tutor_solution_back");
        List<Path> users;
        try (Stream<Path> files = Files.walk(root)) {
            users = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> { try { return Files.readString(p).contains("AdmissionsArticle"); } catch (Exception e) { return false; } })
                    .toList();
        }
        // when / then
        assertThat(users).extracting(p -> p.getFileName().toString()).containsExactlyInAnyOrder(
                "AdmissionsArticle.java", "AdmissionsArticleRepository.java", "AdmissionsArticleService.java",
                "AdmissionsArticleController.java", "AdmissionsArticlePageController.java");
    }
}
