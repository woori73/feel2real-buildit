package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 학생명단(시간표) Word 진행표를 이름 없는 집계로 바꾼다.
 *
 * <p>자료 구조: 월마다 표 1개. 첫 줄 머리글 "N월 진행표 (YYYY년)", 다음 줄 요일(월~일), 그 아래 달력 칸. 칸의 첫 줄은 날짜 숫자이고 이어지는
 * 줄마다 "이름 + 회차"가 적혀 있다. 실명은 이 클래스 안에서만 쓰이고 결과에는 가명(S001…)만 나간다.
 */
public final class StudentRosterAnalyzer {
  private StudentRosterAnalyzer() {}

  /** 한 칸 안의 수업 한 건. 결과로 내보내지 않는다. */
  record Entry(String name, int sessions) {}

  /** 달력 칸 한 개를 읽은 결과. day=0이면 날짜 칸이 아니다. */
  record Cell(int day, List<Entry> entries, int consultations, boolean closed) {}

  /** 날짜가 정해진 수업 한 건(date가 null이면 PDF 대체 읽기라 날짜를 모른다). */
  record Lesson(YearMonth month, LocalDate date, String name, int sessions) {}

  /** 월 표 하나. */
  record Sheet(YearMonth month, List<Lesson> lessons, int consultations, int closedDays) {}

  /** 연도 파일 하나를 읽은 결과와 읽은 방식(docx·doc·pdf-text). */
  record Parsed(List<Sheet> sheets, String source) {}

  static final Pattern HEADER =
      Pattern.compile("(\\d{1,2})\\s*월[^()\\d]*(?:\\(\\s*(\\d{4})\\s*년?\\s*\\))?");
  static final Pattern DAY = Pattern.compile("^(\\d{1,2})(?!\\d)\\s*(.*)$");
  static final Pattern TOKEN =
      Pattern.compile("^([가-힣]{1,4})?\\s*(\\d{1,3})?(?:\\s*[-~&]\\s*(\\d{1,3}))?$");
  static final Pattern CLOSED = Pattern.compile("휴무|휴원|휴가|휴강|방학|휴일|연휴");
  static final List<String> WEEKDAYS = List.of("월", "화", "수", "목", "금", "토", "일");

  /** 머리글 "12월 진행표 (2025년)" → 2025-12. 연도가 없으면 연도 칸은 null. 월 머리글이 아니면 null. */
  static int[] header(String text) {
    if (text == null) return null;
    Matcher m = HEADER.matcher(text.strip());
    if (!m.find() || m.start() > 2) return null;
    int month = Integer.parseInt(m.group(1));
    if (month < 1 || month > 12) return null;
    return new int[] {month, m.group(2) == null ? 0 : Integer.parseInt(m.group(2))};
  }

  /** 칸 한 줄("가나3", "다라/마바5", "사아2-3", "자차1(20회)", "○○상담", "휴무")을 수업 목록으로 바꾼다. */
  static List<Entry> line(String raw) {
    String text = raw.replaceAll("\\([^)]*\\)|\\[[^]]*]", "").strip();
    List<Entry> out = new ArrayList<>();
    if (text.isEmpty()) return out;
    String previous = null;
    for (String part : text.split("[/,·]+")) {
      Matcher m = TOKEN.matcher(part.strip());
      if (!m.matches()) continue;
      String name = m.group(1);
      if (name == null && m.group(2) == null) continue;
      if (name == null) name = previous; // "가나4/5"처럼 숫자만 이어 쓴 경우 앞 이름의 추가 회차
      if (name == null) continue;
      int sessions = 1;
      if (m.group(2) != null && m.group(3) != null) {
        int from = Integer.parseInt(m.group(2)), to = Integer.parseInt(m.group(3));
        if (to > from && to - from < 10) sessions = to - from + 1;
        else if (to != from) sessions = 2; // "11&12"
      }
      out.add(new Entry(name, sessions));
      previous = name;
    }
    return out;
  }

  /** 달력 칸 한 개. 첫 줄 날짜 뒤 글자(공휴일 이름 등)는 메모로 보고, 이름+숫자 꼴일 때만 수업으로 센다. */
  static Cell cell(String text) {
    if (text == null) return new Cell(0, List.of(), 0, false);
    List<String> lines =
        Arrays.stream(text.split("[\\r\\n\\u000b\\u0007]+"))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .toList();
    if (lines.isEmpty()) return new Cell(0, List.of(), 0, false);
    Matcher day = DAY.matcher(lines.getFirst());
    if (!day.matches()) return new Cell(0, List.of(), 0, false);
    int number = Integer.parseInt(day.group(1));
    if (number < 1 || number > 31) return new Cell(0, List.of(), 0, false);
    List<String> body = new ArrayList<>();
    String rest = day.group(2).strip();
    boolean closed = CLOSED.matcher(rest).find();
    if (!rest.isEmpty() && rest.matches(".*[가-힣]\\s*\\d.*")) body.add(rest);
    body.addAll(lines.subList(1, lines.size()));
    List<Entry> entries = new ArrayList<>();
    int consultations = 0;
    for (String l : body) {
      String plain = l.replaceAll("\\([^)]*\\)", "").strip();
      if (plain.endsWith("상담")) {
        consultations++;
        continue;
      }
      if (CLOSED.matcher(plain).find() && !plain.matches(".*\\d.*")) {
        closed = true;
        continue;
      }
      entries.addAll(line(l));
    }
    return new Cell(number, entries, consultations, closed);
  }

  /** 표 묶음(표 → 줄 → 칸 글자)을 월 표 목록으로. 머리글에 연도가 없으면 파일 연도와 월 순서로 정한다. */
  static List<Sheet> sheets(List<List<List<String>>> tables, int fileYear) {
    List<int[]> headers = new ArrayList<>();
    List<List<List<String>>> months = new ArrayList<>();
    for (var table : tables) {
      if (table.isEmpty() || table.getFirst().isEmpty()) continue;
      int[] h = header(table.getFirst().getFirst());
      if (h == null) continue;
      headers.add(h);
      months.add(table);
    }
    List<Sheet> out = new ArrayList<>();
    int year = fileYear, previousMonth = 0;
    for (int i = 0; i < months.size(); i++) {
      int month = headers.get(i)[0], explicit = headers.get(i)[1];
      if (explicit > 0) year = explicit;
      else if (i == 0)
        year = month == 12 && months.size() > 1 && headers.get(1)[0] == 1 ? fileYear - 1 : fileYear;
      else if (month < previousMonth) year++;
      previousMonth = month;
      out.add(sheet(months.get(i), YearMonth.of(year, month)));
    }
    return out;
  }

  /** 월 표 하나. 첫 주의 20일 넘는 날짜는 앞달, 넷째 줄 이후 15일 전 날짜는 다음달로 본다. */
  static Sheet sheet(List<List<String>> rows, YearMonth month) {
    List<Lesson> lessons = new ArrayList<>();
    int consultations = 0, closed = 0, week = 0;
    for (int r = 1; r < rows.size(); r++) {
      boolean dated = false;
      for (String text : rows.get(r)) {
        Cell c = cell(text);
        if (c.day() == 0) continue;
        dated = true;
        YearMonth target =
            week == 0 && c.day() > 20 ? month.minusMonths(1)
                : week >= 3 && c.day() < 15 ? month.plusMonths(1) : month;
        LocalDate date;
        try {
          date = target.atDay(c.day());
        } catch (DateTimeException e) {
          continue;
        }
        consultations += c.consultations();
        if (c.closed()) closed++;
        for (Entry e : c.entries()) lessons.add(new Lesson(target, date, e.name(), e.sessions()));
      }
      if (dated) week++;
    }
    return new Sheet(month, lessons, consultations, closed);
  }

  /** .docx 표 글자. 칸 안 문단은 줄바꿈으로 잇는다. */
  static List<List<List<String>>> docxTables(byte[] bytes) throws IOException {
    List<List<List<String>>> out = new ArrayList<>();
    try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new ByteArrayInputStream(bytes))) {
      for (var table : doc.getTables()) {
        List<List<String>> rows = new ArrayList<>();
        for (var row : table.getRows()) {
          List<String> cells = new ArrayList<>();
          for (var cell : row.getTableCells()) {
            StringBuilder sb = new StringBuilder();
            for (var p : cell.getParagraphs()) sb.append(p.getText()).append('\n');
            cells.add(sb.toString());
          }
          rows.add(cells);
        }
        out.add(rows);
      }
    }
    return out;
  }

  /** .doc(Word 97-2003) 표 글자. */
  static List<List<List<String>>> docTables(byte[] bytes) throws IOException {
    List<List<List<String>>> out = new ArrayList<>();
    try (var doc = new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes))) {
      var tables = new org.apache.poi.hwpf.usermodel.TableIterator(doc.getRange());
      while (tables.hasNext()) {
        var table = tables.next();
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < table.numRows(); r++) {
          var row = table.getRow(r);
          List<String> cells = new ArrayList<>();
          for (int c = 0; c < row.numCells(); c++) cells.add(row.getCell(c).text());
          rows.add(cells);
        }
        out.add(rows);
      }
    }
    return out;
  }

  /**
   * Word 표를 읽지 못할 때 쓰는 PDF 글자 대체 읽기. 칸 경계를 알 수 없어 날짜·요일 없이 월별 수업 수만 센다.
   */
  static List<Sheet> pdfSheets(String text, int fileYear) {
    List<Sheet> out = new ArrayList<>();
    YearMonth current = null;
    List<Lesson> lessons = new ArrayList<>();
    int previousMonth = 0, year = fileYear;
    for (String raw : text.split("\\R")) {
      String l = raw.strip();
      int[] h = l.matches("^\\d{1,2}\\s*월.*(진행표|시간표).*") ? header(l) : null;
      if (h != null) {
        if (current != null) out.add(new Sheet(current, lessons, 0, 0));
        if (h[1] > 0) year = h[1];
        else if (previousMonth > 0 && h[0] < previousMonth) year++;
        previousMonth = h[0];
        current = YearMonth.of(year, h[0]);
        lessons = new ArrayList<>();
        continue;
      }
      if (current == null) continue;
      for (String word : l.split("\\s+")) {
        if (!word.matches("[가-힣]{1,4}\\d{1,3}")) continue;
        for (Entry e : line(word)) lessons.add(new Lesson(current, null, e.name(), e.sessions()));
      }
    }
    if (current != null) out.add(new Sheet(current, lessons, 0, 0));
    return out;
  }

  /** 확장자에 맞게 Word를 읽고, 실패하거나 월 표가 없으면 PDF 글자로 대신 읽는다. */
  static Parsed read(String extension, byte[] word, byte[] pdf, int fileYear) {
    try {
      var tables = "docx".equals(extension) ? docxTables(word) : docTables(word);
      var sheets = sheets(tables, fileYear);
      if (!sheets.isEmpty()) return new Parsed(sheets, extension);
    } catch (Exception | LinkageError ignored) {
      // 손상·구형 Word는 아래 PDF 대체 읽기로 넘긴다.
    }
    if (pdf != null) {
      try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
        var strip = new org.apache.pdfbox.text.PDFTextStripper();
        strip.setEndPage(Math.min(100, doc.getNumberOfPages()));
        return new Parsed(pdfSheets(strip.getText(doc), fileYear), "pdf-text");
      } catch (IOException ignored) {
        // 읽을 수 없는 연도는 빈 결과로 표시한다.
      }
    }
    return new Parsed(List.of(), "unreadable");
  }

  /**
   * 월 표들을 연도·월·요일 집계로 바꾼다. 같은 날짜·같은 이름은 한 번만 센다(여러 파일에 같은 달이 있을 때). 학생 줄에는 가명만 쓴다.
   */
  static Map<String, Object> summarize(List<Sheet> sheets) {
    Map<String, Lesson> unique = new LinkedHashMap<>();
    Set<YearMonth> dated = new HashSet<>();
    for (Sheet s : sheets)
      for (Lesson l : s.lessons()) if (l.date() != null) dated.add(l.month());
    int undated = 0;
    for (Sheet s : sheets)
      for (Lesson l : s.lessons()) {
        if (l.date() == null) {
          if (dated.contains(l.month())) continue;
          unique.put("u" + undated++, l);
          continue;
        }
        unique.merge(l.date() + "|" + l.name(), l, (a, b) -> a.sessions() >= b.sessions() ? a : b);
      }
    Map<YearMonth, Integer> consultations = new TreeMap<>(), closed = new TreeMap<>();
    for (Sheet s : sheets) {
      consultations.merge(s.month(), s.consultations(), Math::max);
      closed.merge(s.month(), s.closedDays(), Math::max);
    }
    List<Lesson> lessons =
        unique.values().stream()
            .sorted(
                Comparator.comparing(Lesson::month)
                    .thenComparing(l -> l.date() == null ? l.month().atDay(1) : l.date())
                    .thenComparing(Lesson::name))
            .toList();

    // 처음 나온 순서로 가명을 붙인다: 같은 이름은 모든 연도에서 같은 가명.
    Map<String, String> codes = new LinkedHashMap<>();
    for (Lesson l : lessons) codes.computeIfAbsent(l.name(), k -> String.format("S%03d", codes.size() + 1));

    Map<YearMonth, Set<String>> monthStudents = new TreeMap<>();
    Map<YearMonth, Integer> monthLessons = new TreeMap<>();
    Map<Integer, Set<String>> yearStudents = new TreeMap<>();
    Map<Integer, Integer> yearLessons = new TreeMap<>();
    Map<Integer, int[]> yearWeekdays = new TreeMap<>();
    int[] weekdays = new int[7];
    Map<String, YearMonth> first = new HashMap<>(), last = new HashMap<>();
    Map<String, Set<YearMonth>> active = new HashMap<>();
    Map<String, Integer> perStudent = new HashMap<>();
    for (Lesson l : lessons) {
      String code = codes.get(l.name());
      int year = l.month().getYear();
      monthStudents.computeIfAbsent(l.month(), k -> new TreeSet<>()).add(code);
      monthLessons.merge(l.month(), l.sessions(), Integer::sum);
      yearStudents.computeIfAbsent(year, k -> new TreeSet<>()).add(code);
      yearLessons.merge(year, l.sessions(), Integer::sum);
      if (l.date() != null) {
        int w = l.date().getDayOfWeek().getValue() - 1;
        weekdays[w] += l.sessions();
        yearWeekdays.computeIfAbsent(year, k -> new int[7])[w] += l.sessions();
      }
      first.merge(code, l.month(), (a, b) -> a.isBefore(b) ? a : b);
      last.merge(code, l.month(), (a, b) -> a.isAfter(b) ? a : b);
      active.computeIfAbsent(code, k -> new TreeSet<>()).add(l.month());
      perStudent.merge(code, l.sessions(), Integer::sum);
    }

    List<Map<String, Object>> months = new ArrayList<>();
    for (var e : monthStudents.entrySet()) {
      YearMonth m = e.getKey();
      int fresh = (int) e.getValue().stream().filter(c -> first.get(c).equals(m)).count();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("month", m.toString());
      row.put("students", e.getValue().size());
      row.put("lessons", monthLessons.get(m));
      row.put("newStudents", fresh);
      row.put("consultations", consultations.getOrDefault(m, 0));
      row.put("closedDays", closed.getOrDefault(m, 0));
      months.add(row);
    }

    List<Map<String, Object>> years = new ArrayList<>();
    for (var e : yearStudents.entrySet()) {
      int y = e.getKey();
      Set<String> now = e.getValue(), before = yearStudents.getOrDefault(y - 1, Set.of());
      int fresh = (int) now.stream().filter(c -> first.get(c).getYear() == y).count();
      int continuing = (int) now.stream().filter(before::contains).count();
      YearMonth busiest =
          monthLessons.entrySet().stream()
              .filter(m -> m.getKey().getYear() == y)
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(null);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("year", y);
      row.put("students", now.size());
      row.put("lessons", yearLessons.get(y));
      row.put("newStudents", fresh);
      row.put("returningStudents", now.size() - fresh);
      row.put("previousYearStudents", before.size());
      row.put("continuingFromPreviousYear", continuing);
      row.put(
          "retentionRate",
          before.isEmpty() ? null : Math.round(1000.0 * continuing / before.size()) / 1000.0);
      row.put(
          "activeMonths",
          (int) monthStudents.keySet().stream().filter(m -> m.getYear() == y).count());
      row.put("busiestMonth", busiest == null ? null : busiest.toString());
      row.put(
          "consultations",
          consultations.entrySet().stream()
              .filter(m -> m.getKey().getYear() == y)
              .mapToInt(Map.Entry::getValue)
              .sum());
      row.put("lessonsByWeekday", weekdayMap(yearWeekdays.getOrDefault(y, new int[7])));
      years.add(row);
    }

    Map<String, Integer> tenure = new LinkedHashMap<>();
    for (String bucket : List.of("1개월", "2~3개월", "4~6개월", "7~12개월", "13개월 이상"))
      tenure.put(bucket, 0);
    List<Map<String, Object>> students = new ArrayList<>();
    for (String code : codes.values()) {
      int n = active.get(code).size();
      String bucket = n <= 1 ? "1개월" : n <= 3 ? "2~3개월" : n <= 6 ? "4~6개월" : n <= 12 ? "7~12개월" : "13개월 이상";
      tenure.merge(bucket, 1, Integer::sum);
      if (students.size() >= 1000) continue;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", code);
      row.put("firstMonth", first.get(code).toString());
      row.put("lastMonth", last.get(code).toString());
      row.put("activeMonths", n);
      row.put("lessons", perStudent.get(code));
      students.add(row);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("totalStudents", codes.size());
    out.put("totalLessons", lessons.stream().mapToInt(Lesson::sessions).sum());
    out.put("years", years);
    out.put("months", months);
    out.put("lessonsByWeekday", weekdayMap(weekdays));
    out.put("tenureByActiveMonths", tenure);
    out.put("students", students);
    out.put("studentsTruncated", codes.size() > students.size());
    return out;
  }

  private static Map<String, Integer> weekdayMap(int[] counts) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (DayOfWeek d : DayOfWeek.values()) out.put(WEEKDAYS.get(d.getValue() - 1), counts[d.getValue() - 1]);
    return out;
  }
}
