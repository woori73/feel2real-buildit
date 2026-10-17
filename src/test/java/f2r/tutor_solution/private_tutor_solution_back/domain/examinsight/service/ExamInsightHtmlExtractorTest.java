package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HTML 학습지 추출 — 구조 보존·필기 표시·안전")
class ExamInsightHtmlExtractorTest {

  private final ExamInsightHtmlExtractor extractor = new ExamInsightHtmlExtractor(10);

  private static byte[] fixture(String name) throws IOException {
    Path p = Paths.get("src/test/resources/examinsight", name);
    return Files.readAllBytes(p);
  }

  private ExamInsightHtmlExtractor.Result run(String html) throws IOException {
    return extractor.extract(html.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("원본 학습지: 영어 지문·한글 해석·문제 번호·선택지·서술형이 모두 남는다")
  void original_keepsPassageQuestionsAndChoices() throws IOException {
    // given: 실제 학교 학습지 형태의 HTML 파일
    byte[] html = fixture("worksheet_original.html");

    // when
    String text = extractor.extract(html).text();

    // then
    assertThat(text).contains("Lesson 3. The Value of Sharing");
    assertThat(text).contains("Sharing is not just giving things away.");
    assertThat(text).contains("우리는 시간을 나눌 때 관심을 보여 준다.");
    assertThat(text).contains("1. 윗글의 밑줄 친");
    assertThat(text).contains("① creating confidence").contains("⑤ making noise");
    assertThat(text).contains("2. 빈칸 (A)에 들어갈 말로 알맞은 것은?");
    assertThat(text).contains("① help ② to help ③ helping ④ helped ⑤ helps");
    assertThat(text).contains("서술형 1.").contains("10 words 이내");
  }

  @Test
  @DisplayName("표는 행마다 한 줄, 셀은 | 로 구분하고 셀 안 줄바꿈은 접는다")
  void table_isRenderedRowPerLine() throws IOException {
    // when
    String text = extractor.extract(fixture("worksheet_original.html")).text();

    // then
    assertThat(text).contains("| 번호 | 유형 | 배점 |");
    assertThat(text).contains("| 1 | 어휘 | 3.2점 |");
    assertThat(text).contains("| 2 | 빈칸 추론 (문법 포함) | 3.5점 |");
  }

  @Test
  @DisplayName("밑줄·굵게는 표시로 남기고, 회색조 글자색은 필기로 보지 않는다")
  void inlineFormatting_isMarked_andGrayIsNotAnnotation() throws IOException {
    // when
    String text = extractor.extract(fixture("worksheet_original.html")).text();

    // then
    assertThat(text).contains("__building trust__");
    assertThat(text).contains("**It is important ( A ) others.**");
    assertThat(text).doesNotContain("[색:");
  }

  @Test
  @DisplayName("스크립트·스타일·링크·프레임·주석·noscript는 읽지 않는다")
  void dangerousContent_isDropped() throws IOException {
    // when
    String text = extractor.extract(fixture("worksheet_original.html")).text();

    // then
    assertThat(text).doesNotContain("evil.example").doesNotContain("document.cookie").doesNotContain("alert(1)");
    assertThat(text).doesNotContain("NOSCRIPT-MARKER").doesNotContain("COMMENT-MARKER");
    assertThat(text).doesNotContain(".q{color").doesNotContain("window.location");
  }

  @Test
  @DisplayName("가려진 내용(스타일시트 클래스·display:none·hidden)은 버리지 않고 [숨김] 표시로 남긴다 — 정답 보기 같은 내용")
  void hiddenContent_isKeptButMarked() throws IOException {
    // when
    String text = extractor.extract(fixture("worksheet_original.html")).text();

    // then
    assertThat(text).contains("[숨김]숨겨진 내용: 이 문장은 읽지 않는다 HIDDEN-MARKER[/숨김]");
    assertThat(text).contains("[숨김]DISPLAY-NONE-MARKER[/숨김]");
    assertThat(run("<p hidden>정답: ②</p><p>보이는 문장</p>").text()).contains("[숨김]정답: ②[/숨김]").contains("보이는 문장");
    assertThat(run("<style>#ans, .reveal{display:none}</style><div id=\"ans\">A</div><span class=\"reveal\">B</span><p>C</p>").text())
        .contains("[숨김]A[/숨김]").contains("[숨김]B[/숨김]").contains("C");
  }

  @Test
  @DisplayName("외부 이미지는 가져오지 않고 개수만 알리며, alt 글자만 남긴다")
  void externalImages_areNeverFetched_onlyCounted() throws IOException {
    // when
    ExamInsightHtmlExtractor.Result r = extractor.extract(fixture("worksheet_original.html"));

    // then: http 이미지 1개 + 상대경로 이미지 1개
    assertThat(r.skippedExternalImages()).isEqualTo(2);
    assertThat(r.images()).isEmpty();
    assertThat(r.text()).contains("(그림 설명: 출처 그림)");
    assertThat(r.text()).doesNotContain("tracker.png");
  }

  @Test
  @DisplayName("필기본: 글자색·배경색·font color·답/해설 클래스·mark·취소선이 표시로 남는다")
  void annotated_marksColorAndAnnotations() throws IOException {
    // when
    String text = extractor.extract(fixture("worksheet_annotated.html")).text();

    // then
    assertThat(text).contains("[색:rgb(220,38,38)]not just giving[/색]");
    assertThat(text).contains("[색:blue]핵심: 동명사구가 목적어 → 서술형 출제 가능[/색]");
    assertThat(text).contains("[색:answer]①[/색]");
    assertThat(text).contains("[강조]trust = confidence[/강조]");
    assertThat(text).contains("[색:#ffff00]시험 직전 암기:");
    assertThat(text).contains("~~old rule~~");
    assertThat(text).contains("| [색:#0000ff]해설[/색] | [색:#c00]to부정사의 형용사적 용법[/색] |");
    assertThat(text).contains("회색조 글자는 필기가 아니다.").doesNotContain("[색:#333]");
  }

  @Test
  @DisplayName("필기본: 체크된 선택과 입력한 메모가 남는다")
  void annotated_keepsCheckedChoiceAndTypedMemo() throws IOException {
    // when
    String text = extractor.extract(fixture("worksheet_annotated.html")).text();

    // then
    assertThat(text).contains("[선택됨] ② to help");
    assertThat(text).contains("[ ] ③ helping");
    assertThat(text).contains("[입력: 수행평가 5월 둘째 주]");
  }

  @Test
  @DisplayName("원본과 필기본은 서로 다른 소스로 따로 읽혀 둘 다 분석에 쓸 수 있다")
  void originalAndAnnotated_areDistinctSources() throws IOException {
    // when
    String original = extractor.extract(fixture("worksheet_original.html")).text();
    String annotated = extractor.extract(fixture("worksheet_annotated.html")).text();

    // then
    assertThat(original).doesNotContain("핵심: 동명사구");
    assertThat(annotated).contains("핵심: 동명사구");
    assertThat(annotated).doesNotContain("① creating confidence");
  }

  @Test
  @DisplayName("data URI 이미지는 번호와 자리표시자로 돌려주고, 잘못된·큰 이미지는 읽지 않은 것으로 센다")
  void embeddedImages_areExtractedWithPlaceholders() throws IOException {
    // given
    String png = java.util.Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
    String html = "<p>그림 1</p><img src=\"data:image/png;base64," + png + "\" alt=\"표\">"
        + "<img src=\"data:image/png;base64,@@@@\"><img src=\"data:image/gif;base64,R0lGODlh\">";

    // when
    ExamInsightHtmlExtractor.Result r = run(html);

    // then
    assertThat(r.images()).hasSize(1);
    assertThat(r.images().get(0).index()).isEqualTo(1);
    assertThat(r.images().get(0).mime()).isEqualTo("image/png");
    assertThat(r.text()).contains("{{IMG:1}}");
    assertThat(r.skippedEmbeddedImages()).isEqualTo(2);
  }

  @Test
  @DisplayName("내장 이미지는 개수 상한을 넘으면 더 읽지 않는다 (비용 통제)")
  void embeddedImages_respectCap() throws IOException {
    // given
    String png = java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});
    StringBuilder html = new StringBuilder();
    for (int i = 0; i < 5; i++) html.append("<img src=\"data:image/png;base64,").append(png).append("\">");

    // when
    ExamInsightHtmlExtractor.Result r = new ExamInsightHtmlExtractor(2).extract(html.toString().getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(r.images()).hasSize(2);
    assertThat(r.skippedEmbeddedImages()).isEqualTo(3);
  }

  @Test
  @DisplayName("EUC-KR meta charset 문서도 한글이 깨지지 않는다")
  void legacyKoreanCharset_isDecoded() throws IOException {
    // given
    String html = "<html><head><meta charset=\"euc-kr\"></head><body><p>다음 글을 읽고 답하시오.</p></body></html>";

    // when
    String text = extractor.extract(html.getBytes(Charset.forName("EUC-KR"))).text();

    // then
    assertThat(text).contains("다음 글을 읽고 답하시오.");
  }

  @Test
  @DisplayName("ol 목록은 번호, ul 목록은 글머리표로 남는다")
  void lists_keepNumberingAndBullets() throws IOException {
    // when
    String text = run("<ol><li>first</li><li>second</li></ol><ul><li>bullet</li></ul>").text();

    // then
    assertThat(text).contains("1. first").contains("2. second").contains("- bullet");
  }

  @Test
  @DisplayName("비정상적으로 깊게 중첩된 HTML도 스택 오버플로 없이 글자를 돌려준다")
  void deepNesting_doesNotOverflowStack() throws IOException {
    // given
    String html = "<div>".repeat(5000) + "DEEP-TEXT" + "</div>".repeat(5000);

    // when
    String text = run(html).text();

    // then
    assertThat(text).contains("DEEP-TEXT");
  }

  @Test
  @DisplayName("이벤트 핸들러·javascript: 링크·style 안의 코드는 텍스트로도 남지 않는다")
  void inlineScriptSurfaces_areNotLeaked() throws IOException {
    // when
    String text = run("<a href=\"javascript:alert(1)\" onclick=\"steal()\">보기</a><p style=\"background:url(http://evil.example/x)\">본문</p>").text();

    // then
    assertThat(text).contains("보기").contains("본문");
    assertThat(text).doesNotContain("javascript:").doesNotContain("steal").doesNotContain("evil.example");
  }

  @Test
  @DisplayName("색 판정: 검정·흰색·회색은 아니고 빨강·파랑 계열과 이름 색은 색이다")
  void isColored_classification() {
    assertThat(ExamInsightHtmlExtractor.isColored("#000")).isFalse();
    assertThat(ExamInsightHtmlExtractor.isColored("#FFFFFF")).isFalse();
    assertThat(ExamInsightHtmlExtractor.isColored("rgb(50, 52, 49)")).isFalse();
    assertThat(ExamInsightHtmlExtractor.isColored("gray")).isFalse();
    assertThat(ExamInsightHtmlExtractor.isColored("inherit")).isFalse();
    assertThat(ExamInsightHtmlExtractor.isColored("#ff0000")).isTrue();
    assertThat(ExamInsightHtmlExtractor.isColored("rgb(0, 0, 200)")).isTrue();
    assertThat(ExamInsightHtmlExtractor.isColored("orange")).isTrue();
  }
}
