package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 학교 프린트 HTML 의 bounded 추출 — 실제 확인된 3개 학교 패턴 + "잘못 뽑느니 생략". */
class MidtermShowcaseExtractorTest {

  @Test
  void apgujung_lessonFromHeading_grammarFromBracketAndAllowlist_egSentences() {
    String html = """
        <html><head><title>압구정중2 Lesson 5 필기통합 · 모든 빈칸 정답 완성</title></head>
        <body>
          <h1>[2026 압구정중학교 2학년 영어] Lesson 5 Grammar 1</h1>
          <h1>[2026 압구정중학교 2학년 영어] Lesson 5 Grammar 2 [간접의문문]</h1>
          <h2>Part 1. 개념정리</h2>
          <p>• 5형식 문장에 동사 make, keep, find 등이 온다.</p>
          <p>e.g. The news made him happy. (그 뉴스는 그를 행복하게 만들었다.)</p>
          <li>Watching movies makes me excited. (영화를 보는 것은 나를 신나게 한다.)</li>
          <p>Read each sentence and write the correct word.</p>
        </body></html>
        """;
    var e = MidtermShowcaseExtractor.from(html);
    assertThat(e.lesson()).isEqualTo("Lesson 5");
    assertThat(e.grammarPoints()).contains("간접의문문");
    assertThat(e.grammarPoints()).anyMatch(g -> g.contains("형식"));
    assertThat(e.sampleSentences()).contains("The news made him happy.");
    assertThat(e.sampleSentences()).contains("Watching movies makes me excited.");
    // 지시문은 예문이 아니다.
    assertThat(e.sampleSentences()).noneMatch(s -> s.startsWith("Read "));
  }

  @Test
  void singu2_lessonFromKoreanCounter_grammarFromTitle() {
    String html = """
        <html><head><title>신구중 2학년 5과 학습지 필기통합 - 관계대명사 최종수정</title></head>
        <body>
          <h2>2. used to</h2>
          <p>The cat that followed us to school was black and white.</p>
          <li>Chris found the bike that he lost yesterday.</li>
        </body></html>
        """;
    var e = MidtermShowcaseExtractor.from(html);
    assertThat(e.lesson()).isEqualTo("Lesson 5");
    assertThat(e.grammarPoints()).contains("관계대명사");
    assertThat(e.grammarPoints()).contains("used to");
    assertThat(e.sampleSentences())
        .contains("The cat that followed us to school was black and white.");
  }

  @Test
  void emptyOrJunk_returnsEmptyNotGuess() {
    assertThat(MidtermShowcaseExtractor.from(null)).isEqualTo(MidtermShowcaseExtractor.Extract.EMPTY);
    assertThat(MidtermShowcaseExtractor.from("   ")).isEqualTo(MidtermShowcaseExtractor.Extract.EMPTY);
    var e = MidtermShowcaseExtractor.from("<html><body><p>안녕하세요</p><p>공지사항입니다</p></body></html>");
    assertThat(e.lesson()).isNull();
    assertThat(e.grammarPoints()).isEmpty();
    assertThat(e.sampleSentences()).isEmpty();
  }

  @Test
  void bracketText_thatIsNotGrammar_isDropped() {
    // "[정답 완성]" 같은 대괄호는 문법 용어가 아니므로 채택하지 않는다.
    var e = MidtermShowcaseExtractor.from(
        "<html><head><title>[정답 완성] [빈칸 채우기]</title></head><body></body></html>");
    assertThat(e.grammarPoints()).isEmpty();
  }

  @Test
  void allCapsOrTooShort_notTakenAsSentence() {
    var e = MidtermShowcaseExtractor.from("""
        <html><body>
          <p>WORD LIST</p>
          <p>Go.</p>
          <p>This is a properly formed English sentence for the test.</p>
        </body></html>
        """);
    assertThat(e.sampleSentences()).containsExactly(
        "This is a properly formed English sentence for the test.");
  }

  @Test
  void capsAt3() {
    var e = MidtermShowcaseExtractor.from("""
        <html><body>
          <p>The first sentence goes here today.</p>
          <p>The second sentence goes here today.</p>
          <p>The third sentence goes here today.</p>
          <p>The fourth sentence goes here today.</p>
        </body></html>
        """);
    assertThat(e.sampleSentences()).hasSize(3);
  }
}
