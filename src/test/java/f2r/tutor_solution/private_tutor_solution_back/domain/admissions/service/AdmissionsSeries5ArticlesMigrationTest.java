package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("「바뀌는 입시」 시리즈 5 1~3편(V1125)")
class AdmissionsSeries5ArticlesMigrationTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V1125__add_admissions_series5_articles_20261126.sql");

    @Test
    @DisplayName("given V1125 when 글을 나누면 then 5-①~③이 INSERT IGNORE로 2026-11-26 예약 게시일에 순서대로 들어 있다")
    void threeSeries5ArticlesAreIdempotentInserts() throws Exception {
        // given
        String sql = Files.readString(MIGRATION);
        // when
        String[] inserts = sql.split("INSERT IGNORE INTO admissions_articles");
        // then
        assertThat(inserts).hasSize(4);
        assertThat(sql).doesNotContain("INSERT INTO admissions_articles");
        assertThat(inserts[1]).contains("'바뀌는 입시', '5-①'", "'서울대 2028 전형, 수시 문은 넓히고 정시는 학생부를 더 봅니다'", "'2026-11-26', 1, 1");
        assertThat(inserts[2]).contains("'바뀌는 입시', '5-②'", "'2026-11-26', 1, 2");
        assertThat(inserts[3]).contains("'바뀌는 입시', '5-③'", "'2026-11-26', 1, 3");
    }

    @Test
    @DisplayName("given V1125 본문 when 허용 태그로 정리하면 then 내용이 바뀌지 않고, 글마다 '예비 고1이 지금 알아 둘 것'과 '참고한 자료'가 있다")
    void bodiesUseOnlyAllowedTags() throws Exception {
        // given
        String sql = Files.readString(MIGRATION);
        Matcher m = Pattern.compile("\\n  '(<p>.*?)',\\n  '2026-11-26'", Pattern.DOTALL).matcher(sql);
        int bodies = 0;
        while (m.find()) {
            String body = m.group(1).replace("''", "'");
            // when
            String cleaned = Jsoup.clean(body, AdmissionsArticleService.SAFELIST);
            // then
            assertThat(Jsoup.parseBodyFragment(cleaned).text()).isEqualTo(Jsoup.parseBodyFragment(body).text());
            assertThat(body).contains("<h2>예비 고1이 지금 알아 둘 것</h2>", "<h2>참고한 자료</h2>")
                    .doesNotContain("<h1>", "사실일까", "확인 필요", "(공식)", "(분석)", "(논쟁)");
            bodies++;
        }
        assertThat(bodies).isEqualTo(3);
    }
}
