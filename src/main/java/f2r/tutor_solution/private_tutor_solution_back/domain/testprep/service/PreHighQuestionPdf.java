package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.core.io.ClassPathResource;

public final class PreHighQuestionPdf {
  private static final float MARGIN = 48;
  private static final PDRectangle PAPER = PDRectangle.A4;
  private static final float TEXT_WIDTH = PAPER.getWidth() - 2 * MARGIN;
  private static final String FONT = "pre-high-english/fonts/NotoSansKR-Regular.ttf";
  private static final List<String> OPTION_NUMBERS = List.of("①", "②", "③", "④", "⑤");

  private PreHighQuestionPdf() {}

  public static byte[] reading(String title, String passage, String question) throws IOException {
    try (var document = new PDDocument()) {
      var font = loadFont(document);
      if (passage != null) {
        imagePage(document, font, title + " · 공통 지문", passage);
      }
      imagePage(document, font, title, question);
      return save(document, title);
    }
  }

  public static byte[] choices(String title, String instruction, String question, List<String> choices)
      throws IOException {
    try (var document = new PDDocument()) {
      var font = loadFont(document);
      var rows = new ArrayList<TextRow>();
      addRows(rows, font, instruction, 13, 16);
      addRows(rows, font, question, 15, 22);
      for (int index = 0; index < choices.size(); index++) {
        addRows(rows, font, OPTION_NUMBERS.get(index) + " " + choices.get(index), 13, 12);
      }

      int index = 0;
      while (index < rows.size()) {
        var page = new PDPage(PAPER);
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
          float y = heading(canvas, font, title);
          while (index < rows.size() && y >= MARGIN) {
            var row = rows.get(index++);
            line(canvas, font, row.text(), row.size(), y);
            y -= row.size() + 5 + row.spaceAfter();
          }
        }
      }
      return save(document, title);
    }
  }

  private record TextRow(String text, float size, float spaceAfter) {}

  private static void addRows(List<TextRow> rows, PDType0Font font, String text, float size, float spaceAfter)
      throws IOException {
    var lines = wrap(font, text, size);
    for (int index = 0; index < lines.size(); index++) {
      rows.add(new TextRow(lines.get(index), size, index + 1 == lines.size() ? spaceAfter : 0));
    }
  }

  private static PDType0Font loadFont(PDDocument document) throws IOException {
    try (var input = new ClassPathResource(FONT).getInputStream()) {
      return PDType0Font.load(document, input);
    }
  }

  private static void imagePage(PDDocument document, PDType0Font font, String title, String encoded)
      throws IOException {
    var bytes = Base64.getDecoder().decode(encoded);
    var image = ImageIO.read(new ByteArrayInputStream(bytes));
    if (image == null) throw new IOException("독해 문제 이미지를 읽을 수 없습니다.");
    var drawing = LosslessFactory.createFromImage(document, image);
    var page = new PDPage(PAPER);
    document.addPage(page);
    try (var canvas = new PDPageContentStream(document, page)) {
      float top = heading(canvas, font, title) + 4;
      float scale = Math.min(TEXT_WIDTH / drawing.getWidth(), (top - MARGIN) / drawing.getHeight());
      float width = drawing.getWidth() * scale;
      float height = drawing.getHeight() * scale;
      canvas.drawImage(drawing, (PAPER.getWidth() - width) / 2, top - height, width, height);
    }
  }

  private static float heading(PDPageContentStream canvas, PDType0Font font, String title) throws IOException {
    float y = PAPER.getHeight() - MARGIN;
    line(canvas, font, "예비고1 디지털 영어 모의고사", 19, y);
    line(canvas, font, title, 12, y - 24);
    canvas.setLineWidth(1);
    canvas.moveTo(MARGIN, y - 37);
    canvas.lineTo(PAPER.getWidth() - MARGIN, y - 37);
    canvas.stroke();
    return y - 65;
  }

  private static List<String> wrap(PDType0Font font, String text, float size) throws IOException {
    var lines = new ArrayList<String>();
    for (String paragraph : text.strip().split("\\R", -1)) {
      String current = "";
      for (String word : paragraph.strip().split("\\s+")) {
        String candidate = current.isEmpty() ? word : current + " " + word;
        if (!current.isEmpty() && width(font, candidate, size) > TEXT_WIDTH) {
          lines.add(current);
          current = word;
        } else {
          current = candidate;
        }
      }
      lines.add(current);
    }
    return lines;
  }

  private static float width(PDType0Font font, String text, float size) throws IOException {
    return font.getStringWidth(text) * size / 1000;
  }

  private static void line(PDPageContentStream canvas, PDType0Font font, String text, float size, float y)
      throws IOException {
    if (text.isEmpty()) return;
    float lineWidth = width(font, text, size);
    canvas.beginText();
    canvas.setFont(font, lineWidth > TEXT_WIDTH ? size * TEXT_WIDTH / lineWidth : size);
    canvas.newLineAtOffset(MARGIN, y);
    canvas.showText(text);
    canvas.endText();
  }

  private static byte[] save(PDDocument document, String title) throws IOException {
    document.getDocumentInformation().setTitle(title);
    var output = new ByteArrayOutputStream();
    document.save(output);
    return output.toByteArray();
  }
}
