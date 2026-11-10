package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/** 모든 이름은 음절을 나열한 가짜 이름이다(가나·다라·마바…). 실제 학생명단과 무관하다. */
class StudentRosterAnalyzerTest {

  private static StudentRosterAnalyzer.Entry entry(String name, int sessions) {
    return new StudentRosterAnalyzer.Entry(name, sessions);
  }

  @Test
  void givenMonthHeaderWithYear_whenHeader_thenMonthAndYear() {
    // given
    String text = "12월 진행표 (2025년)";
    // when
    int[] result = StudentRosterAnalyzer.header(text);
    // then
    assertArrayEquals(new int[] {12, 2025}, result);
  }

  @Test
  void givenHeaderWithoutYearOrNotHeader_whenHeader_thenYearZeroOrNull() {
    // given / when / then
    assertArrayEquals(new int[] {3, 0}, StudentRosterAnalyzer.header("3월 과외진행표"));
    assertNull(StudentRosterAnalyzer.header("월"));
    assertNull(StudentRosterAnalyzer.header("13월 진행표"));
  }

  @Test
  void givenVariousLineForms_whenLine_thenEachStudentAndSessionCounted() {
    // given / when / then
    assertEquals(List.of(entry("가나", 1)), StudentRosterAnalyzer.line("가나3"));
    assertEquals(List.of(entry("가", 1)), StudentRosterAnalyzer.line("가 7"));
    assertEquals(List.of(entry("가나", 1), entry("다라", 1)), StudentRosterAnalyzer.line("가나5/다라4"));
    assertEquals(List.of(entry("가나", 1), entry("다라", 1)), StudentRosterAnalyzer.line("가나/다라3"));
    assertEquals(List.of(entry("가나", 2)), StudentRosterAnalyzer.line("가나2-3"));
    assertEquals(List.of(entry("가나", 2)), StudentRosterAnalyzer.line("가나11&12"));
    assertEquals(List.of(entry("가나", 1)), StudentRosterAnalyzer.line("가나1(20회)"));
    assertEquals(List.of(entry("가나", 1), entry("가나", 1)), StudentRosterAnalyzer.line("가나4/5"));
    assertTrue(StudentRosterAnalyzer.line("가나다라마바").isEmpty());
  }

  @Test
  void givenCalendarCell_whenCell_thenDayEntriesConsultationAndClosedSeparated() {
    // given
    String text = "7\n가나2\n다라마상담\n마바1(사아)\n";
    // when
    var cell = StudentRosterAnalyzer.cell(text);
    // then
    assertEquals(7, cell.day());
    assertEquals(List.of(entry("가나", 1), entry("마바", 1)), cell.entries());
    assertEquals(1, cell.consultations());
    assertFalse(cell.closed());
    assertTrue(StudentRosterAnalyzer.cell("25성탄절\n휴무").closed());
    assertTrue(StudentRosterAnalyzer.cell("16 설날").entries().isEmpty());
    assertEquals(0, StudentRosterAnalyzer.cell("월").day());
  }

  @Test
  void givenHwpfCellMarkers_whenCell_thenSplitOnWordSeparators() {
    // given
    String text = "3\r가나1\u000b다라2\u0007";
    // when
    var cell = StudentRosterAnalyzer.cell(text);
    // then
    assertEquals(3, cell.day());
    assertEquals(2, cell.entries().size());
  }

  @Test
  void givenFirstWeekCarryOverDays_whenSheet_thenAssignedToPreviousMonth() {
    // given — 2020-01 표 첫 주에 12/30, 12/31 칸이 들어 있다
    List<List<String>> rows =
        List.of(
            List.of("1월 진행표 (2020년)"),
            List.of("월", "화", "수", "목", "금", "토", "일"),
            List.of("30\n가나1", "31\n다라1", "1\n가나2", "2", "3", "4", "5"),
            List.of("6", "7", "8", "9", "10", "11", "12"),
            List.of("13", "14", "15", "16", "17", "18", "19"),
            List.of("27\n마바1", "28", "29", "30", "31\n사아1", "1\n자차1", "2"));
    // when
    var sheet = StudentRosterAnalyzer.sheet(rows, YearMonth.of(2020, 1));
    // then
    var dates = sheet.lessons().stream().map(StudentRosterAnalyzer.Lesson::date).toList();
    assertEquals(
        List.of(
            LocalDate.of(2019, 12, 30),
            LocalDate.of(2019, 12, 31),
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 1, 27),
            LocalDate.of(2020, 1, 31),
            LocalDate.of(2020, 2, 1)),
        dates);
  }

  @Test
  void givenTablesWithoutYear_whenSheets_thenYearInferredFromFileAndOrder() {
    // given — 12월 → 1월 → 2월 순서, 머리글에 연도 없음
    var tables =
        List.of(
            List.of(List.of("12월 진행표"), List.of("1\n가나1")),
            List.of(List.of("1월 진행표"), List.of("1\n가나2")),
            List.of(List.of("메모 표"), List.of("아무 내용")),
            List.of(List.of("2월 진행표"), List.of("1\n가나3")));
    // when
    var sheets = StudentRosterAnalyzer.sheets(tables, 2011);
    // then
    assertEquals(
        List.of(YearMonth.of(2010, 12), YearMonth.of(2011, 1), YearMonth.of(2011, 2)),
        sheets.stream().map(StudentRosterAnalyzer.Sheet::month).toList());
  }

  private static byte[] fakeDocx() throws Exception {
    try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
      var t = doc.createTable(4, 7);
      t.getRow(0).getCell(0).setText("1월 진행표 (2025년)");
      String[] w = {"월", "화", "수", "목", "금", "토", "일"};
      for (int i = 0; i < 7; i++) t.getRow(1).getCell(i).setText(w[i]);
      var cell = t.getRow(2).getCell(0);
      cell.setText("6");
      cell.addParagraph().createRun().setText("가나1");
      cell.addParagraph().createRun().setText("다라2");
      t.getRow(2).getCell(1).setText("7");
      t.getRow(2).getCell(1).addParagraph().createRun().setText("가나2");
      doc.write(out);
      return out.toByteArray();
    }
  }

  @Test
  void givenFakeDocx_whenRead_thenTablesParsedFromWord() throws Exception {
    // given
    byte[] bytes = fakeDocx();
    // when
    var parsed = StudentRosterAnalyzer.read("docx", bytes, null, 2025);
    // then
    assertEquals("docx", parsed.source());
    assertEquals(1, parsed.sheets().size());
    assertEquals(3, parsed.sheets().getFirst().lessons().size());
    assertEquals(LocalDate.of(2025, 1, 6), parsed.sheets().getFirst().lessons().getFirst().date());
  }

  @Test
  void givenFakeLegacyDoc_whenRead_thenHwpfTablesParsed() throws Exception {
    // given — 가짜 이름으로 만든 2개월짜리 Word 97-2003 파일
    byte[] bytes;
    try (var in = getClass().getResourceAsStream("/student-roster/fake-roster-2001.doc")) {
      bytes = Objects.requireNonNull(in).readAllBytes();
    }
    // when
    var parsed = StudentRosterAnalyzer.read("doc", bytes, null, 2001);
    var summary = StudentRosterAnalyzer.summarize(parsed.sheets());
    // then
    assertEquals("doc", parsed.source());
    assertEquals(
        List.of(YearMonth.of(2001, 1), YearMonth.of(2001, 2)),
        parsed.sheets().stream().map(StudentRosterAnalyzer.Sheet::month).toList());
    assertEquals(51, summary.get("totalLessons"));
    assertEquals(8, summary.get("totalStudents"));
  }

  @Test
  void givenBrokenWordAndPdfText_whenRead_thenFallsBackToPdfMonthCounts() throws Exception {
    // given
    byte[] pdf;
    try (var doc = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new ByteArrayOutputStream()) {
      doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      doc.save(out);
      pdf = out.toByteArray();
    }
    // when
    var parsed = StudentRosterAnalyzer.read("doc", "not a word file".getBytes(), pdf, 2009);
    var fromText = StudentRosterAnalyzer.pdfSheets("3월 진행표\n월 화 수\n1 가나1 다라2\n2 가나2\n4월 진행표\n1 마바1", 2009);
    // then
    assertEquals("pdf-text", parsed.source());
    assertEquals(List.of(YearMonth.of(2009, 3), YearMonth.of(2009, 4)), fromText.stream().map(StudentRosterAnalyzer.Sheet::month).toList());
    assertEquals(3, fromText.getFirst().lessons().size());
    assertNull(fromText.getFirst().lessons().getFirst().date());
  }

  private static StudentRosterAnalyzer.Lesson lesson(String date, String name) {
    var d = LocalDate.parse(date);
    return new StudentRosterAnalyzer.Lesson(YearMonth.from(d), d, name, 1);
  }

  @Test
  void givenTwoYears_whenSummarize_thenNewReturningRetentionAndPseudonymsOnly() {
    // given — 2024: 가나·다라, 2025: 가나(유지)·마바(신규), 같은 날 같은 학생 중복 기록 1건
    var sheets =
        List.of(
            new StudentRosterAnalyzer.Sheet(YearMonth.of(2024, 3),
                List.of(lesson("2024-03-04", "가나"), lesson("2024-03-05", "다라"), lesson("2024-03-04", "가나")), 1, 0),
            new StudentRosterAnalyzer.Sheet(YearMonth.of(2025, 3),
                List.of(lesson("2025-03-03", "가나"), lesson("2025-03-04", "마바")), 0, 1));
    // when
    var summary = StudentRosterAnalyzer.summarize(sheets);
    // then
    assertEquals(3, summary.get("totalStudents"));
    assertEquals(4, summary.get("totalLessons"));
    var years = (List<Map<String, Object>>) summary.get("years");
    var y2025 = years.get(1);
    assertEquals(2025, y2025.get("year"));
    assertEquals(1, y2025.get("newStudents"));
    assertEquals(1, y2025.get("returningStudents"));
    assertEquals(1, y2025.get("continuingFromPreviousYear"));
    assertEquals(0.5, y2025.get("retentionRate"));
    assertEquals(1, ((Map<?, ?>) years.get(0).get("lessonsByWeekday")).get("화"));
    var students = (List<Map<String, Object>>) summary.get("students");
    assertEquals(List.of("S001", "S002", "S003"), students.stream().map(s -> s.get("code")).toList());
    assertEquals(2, students.getFirst().get("activeMonths"));
    String json = summary.toString();
    for (String fake : List.of("가나", "다라", "마바")) assertFalse(json.contains(fake), fake);
  }
}
