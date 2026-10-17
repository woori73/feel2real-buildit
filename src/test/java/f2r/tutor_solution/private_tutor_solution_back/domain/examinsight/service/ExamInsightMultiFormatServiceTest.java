package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 문서·HTML·사진을 한 번에 올려 통합 분석하는 흐름. 추출기·전처리기·한도는 실제 코드를 쓰고,
 * 모델 호출(이미지 인식·분석 LLM)만 가짜로 바꿔 요청 내용과 호출 횟수를 검증한다.
 */
@DisplayName("기출분석 — 문서·HTML·사진 통합 입력")
class ExamInsightMultiFormatServiceTest {

  ExamInsightLlmClient llm = mock(ExamInsightLlmClient.class);
  ExamInsightVisionClient vision = mock(ExamInsightVisionClient.class);
  ExamInsightRateLimiter limiter;
  ExamInsightImageBudget budget;
  ExamInsightService service;

  @BeforeEach
  void setUp() {
    limiter = new ExamInsightRateLimiter(3, 100);
    budget = new ExamInsightImageBudget(60, 600);
    when(vision.available()).thenReturn(true);
    when(vision.transcribe(any(), anyString()))
        .thenAnswer(inv -> new ExamInsightVisionClient.Transcription("OCR<" + inv.getArgument(1) + ">", 0, "m"));
    when(llm.complete(anyString(), anyString(), anyInt()))
        .thenReturn(NewStartLlmCompletion.success(ExamInsightPromptBuilderTest.valid(), "p", "m"));
    service = newService(20, 20);
  }

  ExamInsightService newService(int maxFiles, int maxImages) {
    return new ExamInsightService(
        new ExamInsightTextExtractor(mock(DocumentUploadValidator.class), 10), limiter, llm,
        new ExamInsightImagePreprocessor(1800, 100_000_000L), vision, budget, true, maxFiles, maxImages, 10_485_760L,
        40000, 16000);
  }

  // ───────────────────────── 도우미 ─────────────────────────

  /** 사진마다 다른 글자를 그린 시험지 사진. 글자가 다르므로 SHA-256도 모두 다르다. */
  static byte[] photo(String format, int seed) throws IOException {
    BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 640, 480);
    g.setColor(Color.BLACK);
    for (int i = 0; i < 12; i++) g.drawString(seed + ". Question " + (seed * 31 + i) + " choose the best word", 20, 30 + i * 30);
    g.setColor(new Color(seed * 7 % 255, seed * 13 % 255, seed * 29 % 255));
    g.fillRect(seed % 500, 400, 60, 40);
    g.dispose();
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    ImageIO.write(img, format, bo);
    return bo.toByteArray();
  }

  static MockMultipartFile jpg(String name, int seed) throws IOException {
    return new MockMultipartFile("f", name, "image/jpeg", photo("jpg", seed));
  }

  static MockMultipartFile png(String name, int seed) throws IOException {
    return new MockMultipartFile("f", name, "image/png", photo("png", seed));
  }

  static MockMultipartFile html(String name, String resource) throws IOException {
    return new MockMultipartFile("f", name, "text/html", Files.readAllBytes(Paths.get("src/test/resources/examinsight", resource)));
  }

  static MockMultipartFile txt(String name, String body) {
    return new MockMultipartFile("f", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
  }

  ExamInsightService.Input input(
      List<MockMultipartFile> past, List<MockMultipartFile> scope) {
    return new ExamInsightService.Input(new ArrayList<>(past), new ArrayList<>(scope), null, null,
        new Meta("A고", "고2", "능률", "중간고사"), null);
  }

  String analyzedPrompt() {
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(llm, atLeastOnce()).complete(anyString(), prompt.capture(), anyInt());
    return prompt.getValue();
  }

  // ───────────────────────── 통합 분석 ─────────────────────────

  @Test
  @DisplayName("기출 JPG 10장 + 시험범위 HTML 3개·PNG 5장을 한 번에 올려 통합 분석한다")
  void tenJpgPast_plusHtmlAndPngScope_areAnalyzedTogether() throws Exception {
    // given
    List<MockMultipartFile> past = new ArrayList<>();
    for (int i = 1; i <= 10; i++) past.add(jpg(String.format("past_%02d.jpg", i), i));
    List<MockMultipartFile> scope = new ArrayList<>();
    scope.add(html("lesson3_original.html", "worksheet_original.html"));
    scope.add(html("lesson3_notes.htm", "worksheet_annotated.html"));
    scope.add(html("lesson4.html", "worksheet_original.html"));
    for (int i = 11; i <= 15; i++) scope.add(png(String.format("scope_%02d.png", i), i));

    // when
    ExamInsightService.Result r = service.analyze(1L, input(past, scope));

    // then: 한 번의 분석, 사진 15장을 각각 한 번씩만 읽음
    assertThat(r.analysisMarkdown()).startsWith("# 기출문제 분석");
    verify(vision, times(15)).transcribe(any(), anyString());
    assertThat(budget.remaining(1L)).isEqualTo(60 - 15);
    assertThat(limiter.remaining(1L)).isEqualTo(2);
    String prompt = analyzedPrompt();
    for (int i = 1; i <= 10; i++) assertThat(prompt).contains("OCR<" + String.format("past_%02d.jpg", i) + ">");
    for (int i = 11; i <= 15; i++) assertThat(prompt).contains("OCR<" + String.format("scope_%02d.png", i) + ">");
    assertThat(prompt).contains("[사진] past_01.jpg").contains("[HTML] lesson3_original.html")
        .contains("[HTML] lesson3_notes.htm").contains("[HTML] lesson4.html");
    assertThat(prompt).contains("Sharing is not just giving things away.").contains("[색:answer]①[/색]");
    // 사진은 올린 순서대로 들어간다
    assertThat(prompt.indexOf("past_01.jpg")).isLessThan(prompt.indexOf("past_10.jpg"));
    assertThat(prompt.indexOf("[기출문제 자료]")).isLessThan(prompt.indexOf("[시험범위 자료]"));
  }

  @Test
  @DisplayName("PDF 대신 쓰는 문서(txt·md)와 HTML·사진이 섞여도 한 요청으로 읽는다")
  void documentsHtmlAndPhotos_mixInOneRequest() throws Exception {
    // when
    service.analyze(1L, input(
        List.of(txt("past.txt", "1. Choose the best word."), jpg("past_photo.jpg", 3)),
        List.of(html("scope.html", "worksheet_original.html"), png("scope.png", 4), txt("scope.md", "# Lesson 3"))));

    // then
    String prompt = analyzedPrompt();
    assertThat(prompt).contains("1. Choose the best word.").contains("OCR<past_photo.jpg>")
        .contains("Lesson 3. The Value of Sharing").contains("OCR<scope.png>").contains("# Lesson 3");
  }

  @Test
  @DisplayName("같은 사진을 두 번 올려도 이미지 인식은 한 번만 호출하고 한 장만 차감한다")
  void duplicatePhotos_areReadOnce() throws Exception {
    // given
    byte[] same = photo("jpg", 9);

    // when
    service.analyze(1L, input(
        List.of(new MockMultipartFile("f", "a.jpg", "image/jpeg", same), new MockMultipartFile("f", "b.jpg", "image/jpeg", same)),
        List.of(txt("s.txt", "scope"))));

    // then
    verify(vision, times(1)).transcribe(any(), anyString());
    assertThat(budget.remaining(1L)).isEqualTo(59);
  }

  @Test
  @DisplayName("HTML 속 data URI 이미지는 읽어서 자리표시자 자리에 넣고, 외부 이미지는 가져오지 않았다고 알린다")
  void htmlEmbeddedImage_isInlinedAndExternalIsNotFetched() throws Exception {
    // given
    String b64 = Base64.getEncoder().encodeToString(photo("png", 21));
    String html = "<html><body><p>Look at the chart.</p><img src=\"data:image/png;base64," + b64 + "\" alt=\"chart\">"
        + "<img src=\"https://cdn.example.com/chart2.png\"></body></html>";

    // when
    service.analyze(1L, input(List.of(txt("past.txt", "q")),
        List.of(new MockMultipartFile("f", "chart.html", "text/html", html.getBytes(StandardCharsets.UTF_8)))));

    // then
    String prompt = analyzedPrompt();
    assertThat(prompt).contains("Look at the chart.").contains("[내장 이미지 1 내용]").contains("OCR<chart.html 내장 이미지 1>");
    assertThat(prompt).doesNotContain("{{IMG:").doesNotContain("cdn.example.com");
    verify(vision, times(1)).transcribe(any(), anyString());
  }

  @Test
  @DisplayName("읽기 불확실한 표시가 있으면 알림에 개수를 싣는다")
  void uncertainMarkers_produceNotice() throws Exception {
    // given
    when(vision.transcribe(any(), anyString()))
        .thenReturn(new ExamInsightVisionClient.Transcription("3. [확인필요: wether] is nice [판독불가]", 2, "m"));

    // when
    ExamInsightService.Result r = service.analyze(1L, input(List.of(jpg("p.jpg", 5)), List.of(txt("s.txt", "scope"))));

    // then
    assertThat(String.join(" ", r.notices())).contains("[사진] p.jpg").contains("2곳").contains("사실로 쓰지 않아요");
  }

  @Test
  @DisplayName("확인·수정한 텍스트(items)는 사진을 다시 읽지 않고 사용자가 정한 순서로 분석에 들어간다")
  void reviewedItems_areUsedWithoutOcr() {
    // given
    var in = new ExamInsightService.Input(null, null, null, null, new Meta("", "", "", ""), null,
        List.of(new ExamInsightService.Item("[사진] second.jpg", "SECOND-FIXED-TEXT"),
            new ExamInsightService.Item("[사진] first.jpg", "FIRST-FIXED-TEXT"),
            new ExamInsightService.Item("[사진] blank.jpg", "   ")),
        List.of(new ExamInsightService.Item("[HTML] scope.html", "SCOPE-TEXT")));

    // when
    service.analyze(1L, in);

    // then
    String prompt = analyzedPrompt();
    assertThat(prompt.indexOf("SECOND-FIXED-TEXT")).isLessThan(prompt.indexOf("FIRST-FIXED-TEXT"));
    assertThat(prompt).doesNotContain("blank.jpg").contains("SCOPE-TEXT");
    verify(vision, never()).transcribe(any(), anyString());
    assertThat(budget.remaining(1L)).isEqualTo(60);
  }

  // ───────────────────────── 한도·실패 ─────────────────────────

  @Test
  @DisplayName("사진 분석이 설정돼 있지 않으면 사진은 읽은 척하지 않고 거절한다 (분석 횟수·LLM 호출 없음)")
  void visionNotConfigured_rejectsPhotosHonestly() throws Exception {
    // given
    when(vision.available()).thenReturn(false);
    when(vision.transcribe(any(), anyString()))
        .thenThrow(new ExamInsightException(HttpStatus.SERVICE_UNAVAILABLE, "VISION_UNAVAILABLE", "사진 분석은 지금 사용할 수 없어요."));

    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input(List.of(jpg("p.jpg", 1)), List.of(txt("s.txt", "scope")))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("VISION_UNAVAILABLE"));
    verify(llm, never()).complete(anyString(), anyString(), anyInt());
    assertThat(limiter.remaining(1L)).isEqualTo(3);
    assertThat(budget.remaining(1L)).isEqualTo(60);
  }

  @Test
  @DisplayName("분석 횟수가 이미 없으면 사진 인식에 돈을 쓰기 전에 거절한다")
  void noAnalysesLeft_failsBeforeAnyVisionCall() throws Exception {
    // given
    for (int i = 0; i < 3; i++) limiter.tryAcquire(1L);

    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input(List.of(jpg("p.jpg", 1)), List.of(txt("s.txt", "scope")))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("USER_LIMIT"));
    verify(vision, never()).transcribe(any(), anyString());
  }

  @Test
  @DisplayName("한 요청의 사진 수가 상한을 넘으면 읽기 전에 거절한다")
  void tooManyImages_isRejectedUpFront() throws Exception {
    // given
    var small = newService(20, 3);
    List<MockMultipartFile> past = new ArrayList<>();
    for (int i = 1; i <= 4; i++) past.add(jpg("p" + i + ".jpg", i));

    // when / then
    assertThatThrownBy(() -> small.analyze(1L, input(past, List.of(txt("s.txt", "scope")))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_IMAGES"));
    verify(vision, never()).transcribe(any(), anyString());
  }

  @Test
  @DisplayName("분석 도중 사진 인식이 실패하면 분석은 실패하고 차감한 사진 수는 돌려준다")
  void visionFailure_failsAnalysisAndRefundsImage() throws Exception {
    // given
    when(vision.transcribe(any(), anyString()))
        .thenThrow(new ExamInsightException(HttpStatus.BAD_GATEWAY, "VISION_FAILED", "읽지 못했어요."));

    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input(List.of(jpg("p.jpg", 1)), List.of(txt("s.txt", "scope")))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("VISION_FAILED"));
    assertThat(budget.remaining(1L)).isEqualTo(60);
    assertThat(limiter.remaining(1L)).isEqualTo(3);
    verify(llm, never()).complete(anyString(), anyString(), anyInt());
  }

  // ───────────────────────── 파일별 추출(확인·수정 단계) ─────────────────────────

  @Test
  @DisplayName("extract: 한 파일이 실패해도 나머지는 읽고, 파일별 상태·메시지를 돌려준다")
  void extract_reportsPerFileStatus() throws Exception {
    // given
    when(vision.transcribe(any(), anyString())).thenAnswer(inv -> {
      if (inv.getArgument(1).equals("bad.jpg")) {
        throw new ExamInsightException(HttpStatus.BAD_GATEWAY, "VISION_FAILED", "읽지 못했어요.");
      }
      return new ExamInsightVisionClient.Transcription("OCR<" + inv.getArgument(1) + ">", 1, "m");
    });
    List<org.springframework.web.multipart.MultipartFile> files = List.of(
        jpg("good.jpg", 1), jpg("bad.jpg", 2), html("w.html", "worksheet_original.html"), txt("n.txt", "notes"),
        new MockMultipartFile("f", "fake.jpg", "image/jpeg", "not an image".getBytes(StandardCharsets.UTF_8)));

    // when
    ExamInsightService.ExtractResult r = service.extract(1L, "past", files);

    // then
    assertThat(r.items()).extracting(ExamInsightService.ExtractedItem::status)
        .containsExactly("ok", "error", "ok", "ok", "error");
    assertThat(r.items().get(0).text()).isEqualTo("OCR<good.jpg>");
    assertThat(r.items().get(0).uncertainCount()).isEqualTo(1);
    assertThat(r.items().get(0).kind()).isEqualTo("image");
    assertThat(r.items().get(1).code()).isEqualTo("VISION_FAILED");
    assertThat(r.items().get(2).kind()).isEqualTo("html");
    assertThat(r.items().get(2).text()).contains("Lesson 3. The Value of Sharing");
    assertThat(r.items().get(4).code()).isEqualTo("IMAGE_UNREADABLE");
    assertThat(r.remainingImagesToday()).isEqualTo(59); // good만 차감, bad는 환불, fake는 인식 전 거절
  }

  @Test
  @DisplayName("extract: 하루 사진 한도를 넘는 사진은 그 파일만 IMAGE_LIMIT 오류가 된다")
  void extract_imageDailyLimit_isPerFileError() throws Exception {
    // given
    budget = new ExamInsightImageBudget(1, 600);
    service = newService(20, 20);

    // when
    ExamInsightService.ExtractResult r = service.extract(1L, "past", List.of(jpg("a.jpg", 1), jpg("b.jpg", 2), txt("c.txt", "doc")));

    // then
    assertThat(r.items()).extracting(ExamInsightService.ExtractedItem::status).containsExactly("ok", "error", "ok");
    assertThat(r.items().get(1).code()).isEqualTo("IMAGE_LIMIT");
  }

  @Test
  @DisplayName("extract: 비활성화되어 있으면 읽지 않는다")
  void extract_whenDisabled_isRejected() throws Exception {
    // given
    var off = new ExamInsightService(new ExamInsightTextExtractor(mock(DocumentUploadValidator.class), 10), limiter, llm,
        new ExamInsightImagePreprocessor(1800, 100_000_000L), vision, budget, false, 20, 20, 10_485_760L, 40000, 16000);

    // when / then
    assertThatThrownBy(() -> off.extract(1L, "past", List.of(jpg("a.jpg", 1))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("DISABLED"));
    verify(vision, never()).transcribe(any(), anyString());
  }

  @Test
  @DisplayName("extract: 파일 수·사진 수·큰 사진은 정해진 코드로 거절한다")
  void extract_enforcesCountsAndSize() throws Exception {
    // given
    var tiny = newService(2, 1);

    // when / then
    assertThatThrownBy(() -> tiny.extract(1L, "past", List.of(txt("a.txt", "a"), txt("b.txt", "b"), txt("c.txt", "c"))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_FILES"));
    assertThatThrownBy(() -> tiny.extract(1L, "past", List.of(jpg("a.jpg", 1), jpg("b.jpg", 2))))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_IMAGES"));
    assertThatThrownBy(() -> tiny.extract(1L, "past", List.of()))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("NO_FILES"));

    var small = new ExamInsightService(new ExamInsightTextExtractor(mock(DocumentUploadValidator.class), 10), limiter, llm,
        new ExamInsightImagePreprocessor(1800, 100_000_000L), vision, budget, true, 20, 20, 1_000L, 40000, 16000);
    ExamInsightService.ExtractResult r = small.extract(1L, "past", List.of(jpg("big.jpg", 1)));
    assertThat(r.items().get(0).code()).isEqualTo("IMAGE_TOO_BIG");
  }
}
