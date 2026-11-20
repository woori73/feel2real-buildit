package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank.Question;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;

public final class PreHighGrammarQuestionPdf {
  private static final String FONT = "pre-high-english/fonts/NotoSansKR-Regular.ttf";
  private static final List<String> CHOICE_LABELS = List.of("①", "②", "③", "④", "⑤");
  private static final float MARGIN = 44;
  private static final float LINE_HEIGHT = 19;

  private PreHighGrammarQuestionPdf() {}

  public static byte[] render(String title, int number, Question question) throws IOException {
    try (var document = new PDDocument(); var fontInput = new ClassPathResource(FONT).getInputStream()) {
      var font = PDType0Font.load(document, fontInput);
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      document.getDocumentInformation().setTitle(title + " " + number + "번");
      document.getDocumentInformation().setAuthor("F2R 영어");
      float width = page.getMediaBox().getWidth() - 2 * MARGIN;
      float top = page.getMediaBox().getHeight() - MARGIN;
      try (var stream = new PDPageContentStream(document, page)) {
        line(stream, font, "F2R 영어", 11, MARGIN, top);
        line(stream, font, "예비고1 어법 모의고사", 22, MARGIN, top - 31);
        line(stream, font, title, 13, MARGIN, top - 57);
        stream.setStrokingColor(0.18f, 0.22f, 0.30f);
        stream.moveTo(MARGIN, top - 72);
        stream.lineTo(MARGIN + width, top - 72);
        stream.stroke();

        float y = paragraph(stream, font, number + ". " + question.instruction(), 13, MARGIN, top - 101, width);
        if (!question.text().isBlank()) {
          y = paragraph(stream, font, question.text(), 12, MARGIN, y - 7, width);
        }
        y -= 8;
        for (int i = 0; i < question.choices().size(); i++) {
          line(stream, font, CHOICE_LABELS.get(i), 12, MARGIN, y);
          y = paragraph(stream, font, question.choices().get(i), 12, MARGIN + 24, y, width - 24) - 7;
        }
      }

      var output = new ByteArrayOutputStream();
      document.save(output);
      return output.toByteArray();
    }
  }

  private static float paragraph(PDPageContentStream stream, PDType0Font font, String text, float size,
      float x, float y, float width) throws IOException {
    for (String paragraph : text.split("\\R", -1)) {
      for (String textLine : wrap(paragraph, font, size, width)) {
        if (!textLine.isEmpty()) line(stream, font, textLine, size, x, y);
        y -= LINE_HEIGHT;
      }
    }
    return y;
  }

  private static List<String> wrap(String text, PDType0Font font, float size, float width) throws IOException {
    var lines = new ArrayList<String>();
    String current = "";
    for (String word : text.strip().split("\\s+")) {
      String candidate = current.isEmpty() ? word : current + " " + word;
      if (!current.isEmpty() && font.getStringWidth(candidate) * size / 1000 > width) {
        lines.add(current);
        current = word;
      } else {
        current = candidate;
      }
    }
    lines.add(current);
    return lines;
  }

  private static void line(PDPageContentStream stream, PDType0Font font, String text, float size, float x, float y)
      throws IOException {
    stream.beginText();
    stream.setFont(font, size);
    stream.newLineAtOffset(x, y);
    stream.showText(text);
    stream.endText();
  }
}
