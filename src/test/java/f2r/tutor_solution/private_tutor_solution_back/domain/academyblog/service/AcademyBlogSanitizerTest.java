package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyBlogSanitizerTest {

  private final AcademyBlogSanitizer sanitizer = new AcademyBlogSanitizer();

  @Test
  void sanitize_keepsKoreanTextAndImageButRemovesScript() {
    // given
    String html = "<h2>압구정고 내신 전략</h2><p>한글 본문입니다.</p>"
        + "<script>alert('x')</script>"
        + "<img src=\"/api/academies/academy/blog-sources/1/file\" alt=\"성적표\">";

    // when
    String sanitized = sanitizer.sanitize(html);

    // then
    assertThat(sanitized).contains("압구정고 내신 전략", "한글 본문입니다.");
    assertThat(sanitized).contains("<img", "성적표", "src=\"/api/academies/academy/blog-sources/1/file\"");
    assertThat(sanitized).doesNotContain("script", "alert");
  }

  @Test
  void sanitize_keepsImageSizeClass() {
    // given: 표시 크기 프리셋 class가 달린 이미지
    String html = "<img src=\"/api/academies/a/blog-sources/1/file\" alt=\"x\" class=\"blog-img-sm\">";

    // when
    String sanitized = sanitizer.sanitize(html);

    // then: class 속성이 보존되어 공개 페이지에서 크기 CSS가 적용된다
    assertThat(sanitized).contains("class=\"blog-img-sm\"");
  }

  @Test
  void sanitize_keepsStrikethroughTag() {
    // given: Tiptap 취소선은 <s> 태그로 렌더된다
    String html = "<p>이건 <s>취소선</s> 입니다.</p>";

    // when
    String sanitized = sanitizer.sanitize(html);

    // then: <s> 태그가 보존된다
    assertThat(sanitized).contains("<s>취소선</s>");
  }

  @Test
  void sanitize_keepsParagraphAlignmentClass() {
    // given: 가운데 정렬 class가 달린 문단/제목
    String html = "<p class=\"text-center\">가운데</p><h2 class=\"text-right\">오른쪽</h2>";

    // when
    String sanitized = sanitizer.sanitize(html);

    // then: 정렬 class가 보존된다
    assertThat(sanitized).contains("class=\"text-center\"", "class=\"text-right\"");
  }

  @Test
  void sanitize_keepsImageAlignAttribute() {
    // given: 가운데 정렬(data-align) + 크기 class가 함께 달린 이미지
    String html = "<img src=\"/api/academies/a/blog-sources/1/file\" alt=\"x\" class=\"blog-img-md\" data-align=\"center\">";

    // when
    String sanitized = sanitizer.sanitize(html);

    // then: 크기 class와 정렬 data-align이 모두 보존된다
    assertThat(sanitized).contains("class=\"blog-img-md\"", "data-align=\"center\"");
  }
}
