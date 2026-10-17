package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartVisionProperties;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 이미지 인식 모델로 JPG·PNG 시험지 여러 장을 읽어 보는 수동 검증 테스트.
 *
 * <p><b>키가 없으면 건너뛴다</b>(CI·일반 빌드에서는 실행되지 않는다). 키는 환경변수로만 받는다:
 * {@code META_VISION_API_KEY=… ./gradlew test --tests "*ExamInsightVisionRealApiTest"}.
 * 시험지는 코드가 직접 그린다(영어 지문·문제·보기 + 한글 해설 + 표). 한글이 보이려면 실행 PC에 한글 글꼴이 있어야 한다.
 * {@code DC_OCR_OUT}에 폴더를 주면 그린 사진과 인식 결과를 그곳에 저장해 눈으로 비교할 수 있다.
 */
class ExamInsightVisionRealApiTest {

  private static BufferedImage worksheet(int pageNo) {
    BufferedImage img = new BufferedImage(1240, 1000, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 1240, 1000);
    g.setColor(Color.BLACK);
    g.setFont(new Font("Dialog", Font.PLAIN, 26));
    String[] lines = {
        "Lesson " + pageNo + ". The Value of Sharing",
        "Sharing is not just giving things away. It is a way of building trust.",
        "1. Which is closest in meaning to the underlined phrase?",
        "(1) creating confidence  (2) breaking rules  (3) losing time",
        "(4) saving money  (5) making noise",
        "2. Fill in the blank (A):  It is important ( A ) others.",
        "(1) help  (2) to help  (3) helping  (4) helped  (5) helps",
        "해설: 가주어 It ~ to부정사 구문 (3점)"};
    for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 40, 80 + i * 60);
    g.drawLine(40, 600, 1200, 600);
    g.drawString("| No | Type | Points |", 40, 650);
    g.drawString("|  1 | Vocabulary | 3.2 |", 40, 700);
    g.setColor(Color.RED);
    g.drawString("handwritten: answer 1 = (1)", 40, 780);
    g.dispose();
    return img;
  }

  private static byte[] encode(BufferedImage img, String format) throws IOException {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    ImageIO.write(img, format, bo);
    return bo.toByteArray();
  }

  @Test
  @DisplayName("[수동] 실제 모델이 JPG·PNG 시험지 여러 장의 영어 지문·문제·보기·필기를 읽는다")
  void realModel_readsJpgAndPngWorksheets() throws Exception {
    String key = System.getenv("META_VISION_API_KEY");
    assumeTrue(key != null && !key.isBlank(), "META_VISION_API_KEY 가 없어 실제 모델 검증을 건너뜁니다");
    NewStartVisionProperties props = new NewStartVisionProperties();
    props.setApiKey(key);
    if (System.getenv("META_VISION_BASE_URL") != null) props.setBaseUrl(System.getenv("META_VISION_BASE_URL"));
    if (System.getenv("META_VISION_MODEL") != null) props.setModel(System.getenv("META_VISION_MODEL"));
    ExamInsightVisionClient client = new ExamInsightVisionClient(props, 1);
    ExamInsightImagePreprocessor pre = new ExamInsightImagePreprocessor(1800, 100_000_000L);
    String outDir = System.getenv("DC_OCR_OUT");

    int page = 0;
    for (String format : List.of("jpg", "png", "jpg", "png")) {
      page++;
      byte[] original = encode(worksheet(page), format);
      var prepared = pre.prepare(original, "page" + page + "." + format);
      var t = client.transcribe(prepared.jpeg(), "page" + page + "." + format);

      assertThat(t.text()).as("page %d (%s)", page, format).containsIgnoringCase("Sharing").containsIgnoringCase("building trust")
          .containsIgnoringCase("Fill in the blank").containsIgnoringCase("to help");
      assertThat(t.text()).as("필기는 인쇄된 글과 구분해 표시").containsIgnoringCase("answer");
      if (outDir != null) {
        Path dir = Paths.get(outDir);
        Files.createDirectories(dir);
        Files.write(dir.resolve("page" + page + "." + format), original);
        Files.writeString(dir.resolve("page" + page + ".txt"), t.text() + "\n\n[uncertain=" + t.uncertainCount() + "]");
      }
    }
  }
}
