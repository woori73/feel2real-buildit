package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 공개 BuildIt Wiki(/wiki) 화면 계약.
 * - 기존 /analyzes · open-community · 신규 /api/wiki/originals 를 재사용한다.
 * - layouts/public 데코레이트(공개 방문자 분석 범위), 내신 Wiki 링크는 1개만.
 */
class WikiPageContractTest {

  @Test
  @DisplayName("허브: layouts/public 데코레이트 + 학년 탭 + 세 자료 소스 재사용")
  void wikiHub_reusesExistingSourcesUnderPublicLayout() throws IOException {
    String html = read("templates/public/wiki.html");
    String js = read("static/js/wiki.js");

    assertThat(html).contains("layout:decorate=\"~{layouts/public(bodyClass='wiki-body')}\"");
    assertThat(html).contains("id=\"gradeTabs\"", "data-grade=\"3\"", "data-grade=\"2\"", "data-grade=\"1\"");
    // 내신 Wiki 연결은 딱 1곳
    assertThat(countOccurrences(html, "/problem-generator")).isEqualTo(1);

    // 기존 공개 API 재사용
    assertThat(js).contains("/api/analyzes?grade=");
    assertThat(js).contains("/api/wiki/originals?grade=");
    assertThat(js).contains("/api/open-community/posts?scope=wiki");
    // 글쓰기는 기존 커뮤니티 작성 화면 재사용(게시판 고정 + wiki 복귀)
    assertThat(js).contains("/community/posts/new?board=");
    assertThat(js).contains("return=");
  }

  @Test
  @DisplayName("게시판: /wiki/board/{key} 는 scope=wiki 로 open-community 목록을 재사용")
  void wikiBoard_usesWikiScope() throws IOException {
    String html = read("templates/public/wiki_board.html");
    String js = read("static/js/wiki_board.js");

    assertThat(html).contains("layout:decorate=\"~{layouts/public(bodyClass='wiki-body')}\"");
    assertThat(js).contains("scope', 'wiki'");
    assertThat(js).contains("EXAM_REVIEW", "VARIANT", "MATERIAL");
    assertThat(js).contains("/community/posts/");
  }

  @Test
  @DisplayName("커뮤니티 작성 화면: ?board= wiki 게시판이면 picker 잠금 + return 복귀")
  void communityNew_handlesWikiPreset() throws IOException {
    String js = read("static/js/community_new.js");
    assertThat(js).contains("EXAM_REVIEW", "VARIANT", "MATERIAL");
    assertThat(js).contains("returnUrl");
    assertThat(js).contains("locked");
  }

  private static String read(String classpath) throws IOException {
    try (var in = new ClassPathResource(classpath).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
      count++;
    }
    return count;
  }
}
