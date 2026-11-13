package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsArticle;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsArticleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsArticleService.Form;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("「바뀌는 입시」 글 — 허용 태그 정리·공개 규칙·관리자 입력 검증")
class AdmissionsArticleServiceTest {

    AdmissionsArticleRepository repository = mock(AdmissionsArticleRepository.class);
    AdmissionsArticleService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsArticleService(repository);
        when(repository.save(any(AdmissionsArticle.class))).thenAnswer(i -> {
            AdmissionsArticle a = i.getArgument(0);
            if (a.getArticleId() == null) a.setArticleId(10L);
            return a;
        });
    }

    static Form form(String episode, String body) {
        return new Form("바뀌는 입시", episode, "제목", "한 줄 요약", body, "2026-11-13", true, 1);
    }

    @Test
    @DisplayName("given 스크립트·이벤트 속성·style·javascript 링크 when 저장용 정리 then 허용 태그와 http(s) href만 남는다")
    void sanitizeKeepsOnlyAllowlist() {
        // given
        String dirty = "<p onclick=\"x()\" style=\"color:red\" class=\"c\">본문<script>alert(1)</script></p>"
                + "<h2>소제목</h2><div><img src=x onerror=y>그림</div>"
                + "<table><thead><tr><th>등급</th></tr></thead><tbody><tr><td>10%</td></tr></tbody></table>"
                + "<a href=\"javascript:alert(1)\">나쁜 링크</a><a href=\"https://example.com\" target=\"_self\">좋은 링크</a>"
                + "<ul><li><strong>굵게</strong> <em>기울임</em></li></ul>";
        // when
        String clean = AdmissionsArticleService.sanitize(dirty);
        // then
        assertThat(clean).doesNotContain("script", "onclick", "style=", "class=", "<img", "onerror", "<div", "javascript:", "target=");
        assertThat(clean).contains("<p>본문</p>", "<h2>소제목</h2>", "<table>", "<thead>", "<th>등급</th>", "<td>10%</td>",
                "<a href=\"https://example.com\">좋은 링크</a>", "<strong>굵게</strong>", "<em>기울임</em>", "<li>");
    }

    @Test
    @DisplayName("given 저장된 본문 when 화면 출력용 then 외부 링크를 새 창(noopener noreferrer)으로 연다")
    void displayOpensLinksInNewWindow() {
        // given
        String body = "<p>근거 <a href=\"https://example.com/a\">기사</a></p>";
        // when
        String out = AdmissionsArticleService.forDisplay(body);
        // then
        assertThat(out).contains("href=\"https://example.com/a\"", "target=\"_blank\"", "rel=\"noopener noreferrer\"");
    }

    @Test
    @DisplayName("given 숨긴 글·없는 글 when 공개 본문 조회 then 거절한다(화면 404)")
    void hiddenArticleIsNotPublic() {
        // given
        when(repository.findByArticleIdAndVisibleTrue(5L)).thenReturn(Optional.empty());
        // when / then
        assertThatThrownBy(() -> service.getPublic(5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("given 공개 글 when 목록·본문 then 요약 필드와 정리된 본문을 준다")
    void publicListAndDetail() {
        // given
        AdmissionsArticle a = AdmissionsArticle.builder().articleId(1L).series("바뀌는 입시").episode("1-①").title("내신 5등급제")
                .summary("요약").bodyHtml("<p>본문 <a href=\"https://a.b\">링크</a></p>").publishedOn(LocalDate.of(2026, 11, 13))
                .visible(true).sortOrder(1).build();
        when(repository.findAllByVisibleTrueOrderByPublishedOnDescSortOrderAscArticleIdAsc()).thenReturn(List.of(a));
        when(repository.findByArticleIdAndVisibleTrue(1L)).thenReturn(Optional.of(a));
        // when
        var list = service.listPublic();
        var detail = service.getPublic(1L);
        // then
        assertThat(list).singleElement().satisfies(s -> {
            assertThat(s.episode()).isEqualTo("1-①");
            assertThat(s.publishedOn()).isEqualTo(LocalDate.of(2026, 11, 13));
        });
        assertThat(detail.bodyHtml()).contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("given 빈 제목·잘못된 날짜·허용 태그만 지우면 비는 본문·범위 밖 순서 when 저장 then 각각 거절한다")
    void validation() {
        // when / then
        assertThatThrownBy(() -> service.create(new Form("바뀌는 입시", "1-④", " ", "요약", "<p>x</p>", "2026-11-13", true, 1)))
                .hasMessageContaining("제목");
        assertThatThrownBy(() -> service.create(new Form("바뀌는 입시", "1-④", "t", "요약", "<p>x</p>", "2026/11/13", true, 1)))
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> service.create(form("1-④", "<script>alert(1)</script>"))).hasMessageContaining("본문");
        assertThatThrownBy(() -> service.create(new Form("바뀌는 입시", "1-④", "t", "요약", "<p>x</p>", "2026-11-13", true, -1)))
                .hasMessageContaining("정렬");
    }

    @Test
    @DisplayName("given 같은 시리즈·회차가 이미 있음 when 추가 then 거절, 없으면 정리된 본문으로 저장")
    void createRejectsDuplicateEpisode() {
        // given
        when(repository.existsBySeriesAndEpisode("바뀌는 입시", "1-①")).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> service.create(form("1-①", "<p>x</p>"))).hasMessageContaining("회차");
        var saved = service.create(form("1-④", "<p onclick=\"x\">새 글</p>"));
        assertThat(saved.bodyHtml()).isEqualTo("<p>새 글</p>");
        assertThat(saved.visible()).isTrue();
    }

    @Test
    @DisplayName("given 공개 글 when 숨기기 then 공개 여부만 바뀐다")
    void hide() {
        // given
        AdmissionsArticle a = AdmissionsArticle.builder().articleId(3L).series("바뀌는 입시").episode("1-③").title("t").summary("s")
                .bodyHtml("<p>b</p>").publishedOn(LocalDate.of(2026, 11, 13)).visible(true).sortOrder(3).build();
        when(repository.findById(3L)).thenReturn(Optional.of(a));
        // when
        var v = service.setVisible(3L, false);
        // then
        assertThat(v.visible()).isFalse();
        assertThat(a.getTitle()).isEqualTo("t");
    }
}
