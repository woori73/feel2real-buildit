package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * 공개 BuildIt Wiki(/wiki) 화면 계약.
 * - 자료실(원본·분석)은 새 Wiki 자료실 시스템(/api/wiki/originals, /api/wiki/analysis-materials) — /analyzes 재사용 X.
 * - 게시판 작성/상세는 /wiki 전용 화면(/wiki/board/{key}/new, /wiki/posts/{id}) — /community·/home 로 이동하지 않음.
 * - 문자열 계약 + 실제 Thymeleaf 렌더로 파싱·fragment 오류를 함께 잡는다.
 */
class WikiPageContractTest {

  private static TemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine;
  }

  @Test
  @DisplayName("허브·게시판·전용 화면·관리 템플릿이 layouts/public·fragment 치환까지 실제로 렌더된다")
  void templates_render_withLayoutAndFragmentsResolved() {
    TemplateEngine engine = engine();

    for (String view : new String[] {
        "public/wiki", "public/wiki_board", "public/wiki_post_new", "public/wiki_post",
        "public/wiki_originals", "public/wiki_analysis",
        "public/wiki_learning", "public/wiki_learning_detail"}) {
      String html = engine.process(view, new Context());
      assertThat(html)
          .as(view)
          .contains("<!DOCTYPE html>")
          .contains("GTM-MMJ4MGNN")
          .contains("<body class=\"wiki-body\">")
          .doesNotContain("th:block", "th:replace", "layout:fragment");
    }

    // 자료 업로드 진입점은 principal 무관하게 항상 렌더된다(빈 Context = 익명). "UI 표시 ≠ 권한 부여".
    String hub = engine.process("public/wiki", new Context());
    assertThat(hub).as("hub 업로드 버튼")
        .contains("id=\"btnUploadOriginal\"", "id=\"btnUploadAnalysis\"",
            "id=\"wikiUploadModal\"", "wiki_admin_upload.js")
        .doesNotContain("${wikiAdmin}", "${role");
    String originals = engine.process("public/wiki_originals", new Context());
    assertThat(originals).as("originals 업로드 버튼")
        .contains("id=\"btnUploadOriginal\"", "id=\"wikiUploadModal\"", "wiki_admin_upload.js")
        .doesNotContain("${wikiAdmin}", "${role");
    String analysis = engine.process("public/wiki_analysis", new Context());
    assertThat(analysis).as("analysis 업로드 버튼")
        .contains("id=\"btnUploadAnalysis\"", "id=\"wikiUploadModal\"", "wiki_admin_upload.js")
        .doesNotContain("${wikiAdmin}", "${role");

    // "모의고사 학습 자료"(회원) 진입점도 principal 무관하게 항상 렌더. 다운로드/업로드 권한은 서버가 결정.
    assertThat(hub).as("hub 학습 자료 섹션")
        .contains("모의고사 학습 자료", "id=\"btnUploadLearning\"", "id=\"learningList\"",
            "id=\"wikiLearningModal\"", "wiki_learning_upload.js", "href=\"/wiki/learning\"");
    String learning = engine.process("public/wiki_learning", new Context());
    assertThat(learning).as("학습 자료 전체보기")
        .contains("id=\"btnUploadLearning\"", "id=\"wikiLearningModal\"", "wiki_learning.js")
        .doesNotContain("${wikiAdmin}", "${role");
    // 학습 자료 업로드 모달: 자료 종류에 WEEKLY 포함(어휘/Workbook/변형문제/WEEKLY/기타)
    assertThat(learning).as("학습 자료 종류 WEEKLY")
        .contains("data-type=\"VOCAB\"", "data-type=\"WORKBOOK\"", "data-type=\"VARIANT\"",
            "data-type=\"WEEKLY\"", "data-type=\"ETC\"");
    // 상세/미리보기 화면: 카드 클릭 → 즉시 다운로드가 아니라 상세로 이동한다.
    String detail = engine.process("public/wiki_learning_detail", new Context());
    assertThat(detail).as("학습 자료 상세")
        .contains("id=\"wlDetail\"", "wiki_learning_detail.js", "href=\"/wiki/learning\"")
        .doesNotContain("${wikiAdmin}", "${role");

    String admin = engine.process("admin/wiki_analysis", new Context());
    assertThat(admin)
        .contains("<!DOCTYPE html>")
        .contains("admin-nav")
        // 상단 메뉴 노출은 제거했지만(AdminTopnavMenuContractTest) 화면 자체와 URL은 그대로 렌더된다.
        .contains("id=\"materialForm\"", "id=\"materialModal\"")
        .doesNotContain("th:block", "th:replace");
  }

  @Test
  @DisplayName("허브: 새 Wiki 자료실 시스템 사용 + /analyzes 재사용 안 함 + /wiki 전용 네비")
  void wikiHub_usesWikiResourceSystem() throws IOException {
    String html = read("templates/public/wiki.html");
    String js = read("static/js/wiki.js");

    assertThat(html).contains("layout:decorate=\"~{layouts/public(bodyClass='wiki-body')}\"");
    assertThat(html).contains("id=\"gradeTabs\"", "data-grade=\"3\"", "data-grade=\"2\"", "data-grade=\"1\"");
    assertThat(countOccurrences(html, "/problem-generator")).isEqualTo(1);
    assertThat(html).contains("href=\"/wiki/originals\"", "href=\"#analysis\"", "<h2>실전예상문제</h2>");
    // 업로드 버튼/모달/스크립트는 role 조건부 렌더 없이 항상 존재 — th:if 로 지워지지 않는다.
    assertThat(html).contains("id=\"btnUploadOriginal\"", "id=\"btnUploadAnalysis\"");
    assertThat(html).doesNotContain("th:if=\"${wikiAdmin}\"", "th:if=\"${role");
    assertThat(html).contains("fragments/_wiki_admin_upload :: modal");

    assertThat(js).contains("/api/wiki/originals?grade=");
    assertThat(js).contains("/api/wiki/analysis-materials?grade=");
    assertThat(js).contains("/api/wiki/learning-materials?grade=");
    assertThat(js).contains("/api/wiki/visits");   // 방문 비컨
    assertThat(js).contains("/wiki/learning/");     // 카드 클릭 → 상세(즉시 다운로드 아님)
    assertThat(js).contains("/api/open-community/posts?scope=wiki");
    assertThat(js).contains("/wiki/board/", "/new");
    assertThat(js).contains("/wiki/posts/");
    assertThat(js).doesNotContain("/api/analyzes");
    assertThat(js).doesNotContain("/community/posts/");
  }

  @Test
  @DisplayName("허브 개편: 학년 고1→고2→고3 순 + NEW/인기다운로드/Wiki Thread 4단계/WIKI LIVE + home-feed 연결")
  void wikiHub_revamp_hasNewSectionsAndOrder() throws IOException {
    String html = read("templates/public/wiki.html");
    String js = read("static/js/wiki.js");

    // 학년 바: 고1 → 고2 → 고3 순서(첫 탭이 고1, active)
    int g1 = html.indexOf("data-grade=\"1\"");
    int g2 = html.indexOf("data-grade=\"2\"");
    int g3 = html.indexOf("data-grade=\"3\"");
    assertThat(g1).isGreaterThan(0);
    assertThat(g1).isLessThan(g2);
    assertThat(g2).isLessThan(g3);
    assertThat(html).contains("data-grade=\"1\">고1<small>내신 · 모의고사</small>");
    assertThat(html).contains("data-grade=\"3\">고3<small>수능 · 모의고사</small>");

    // NEW 최신 업데이트 / 인기 다운로드 / Wiki Thread 4단계 / WIKI LIVE 영역
    assertThat(html).contains("id=\"latestUpdates\"", "id=\"popularDownloads\"", "id=\"popularPeriod\"");
    assertThat(html).contains("WIKI THREAD", "01", "02", "03", "04", "AI 변형문제 제작", "id=\"recentThreads\"");
    assertThat(html).contains("WIKI LIVE", "id=\"liveMessages\"", "id=\"recentMembers\"",
        "id=\"liveForm\"", "id=\"liveInput\"");
    // 기존 자료실 3종은 유지된다(개편으로 삭제 금지)
    assertThat(html).contains("모의고사 원본", "분석 자료", "모의고사 학습 자료");
    // 사용자 요청: 미사용 커뮤니티는 허브에서 제거하고 학습 자료 메뉴로 전환한다.
    assertThat(html).doesNotContain("data-wiki-nav=\"community\"", "data-wiki-panel=\"community\"",
        "class=\"board-col\"");
    assertThat(html).contains("id=\"wikiSidebar\"", "data-wiki-nav=\"review\"",
        "id=\"reviewList\"", "id=\"reviewFrame\"");

    // JS: aggregate + 실시간(폴링) 엔드포인트 사용, admin analytics 직접 호출 없음
    assertThat(js).contains("/api/wiki/home-feed");
    assertThat(js).contains("/api/wiki/live-messages");
    assertThat(js).contains("/api/wiki/popular-downloads");
    assertThat(js).contains("sinceId=");
    assertThat(js).doesNotContain("/api/admin/");
    // 폴링 방식(SSE/WebSocket 미사용)
    assertThat(js).doesNotContain("EventSource", "WebSocket");
    assertThat(js).contains("setInterval(pollLive", "grade = 1");
  }

  @Test
  @DisplayName("게시판 목록·작성 화면은 /wiki 문맥에서만 움직인다")
  void wikiBoardAndCompose_stayInWikiContext() throws IOException {
    String boardJs = read("static/js/wiki_board.js");
    assertThat(boardJs).contains("/wiki/board/", "/wiki/posts/");
    assertThat(boardJs).doesNotContain("/community/posts/");

    String newHtml = read("templates/public/wiki_post_new.html");
    String newJs = read("static/js/wiki_post_new.js");
    assertThat(newHtml).contains("← BuildIt Wiki로 돌아가기");
    assertThat(newJs).contains("/wiki/posts/");                 // 저장 후 wiki 상세로
    assertThat(newJs).contains("/api/open-community/posts/", "/attachments");  // 첨부 업로드
    assertThat(newJs).doesNotContain("'/home'").doesNotContain("\"/home\"");

    String postHtml = read("templates/public/wiki_post.html");
    String postJs = read("static/js/wiki_post.js");
    assertThat(postHtml).contains("← BuildIt Wiki로 돌아가기");
    assertThat(postJs).contains("/api/open-community/attachments/");
    assertThat(postJs).doesNotContain("'/home'").doesNotContain("\"/home\"");
    assertThat(postJs).doesNotContain("/community/posts/new");
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
