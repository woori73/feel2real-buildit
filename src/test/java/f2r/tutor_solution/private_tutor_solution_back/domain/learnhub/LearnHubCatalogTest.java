package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Action;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Section;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Tab;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart6Store;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

@DisplayName("LearnHubCatalog — 학생 풀이 첫 화면의 탭·시험 목록")
class LearnHubCatalogTest {

  TpExamRepository exams;
  TpQuestionRepository questions;
  PreHighEnglishStore preHigh;
  PreHighPart2Store part2;
  PreHighPart6Store part6;
  LearnHubCatalog catalog;

  @BeforeEach
  void setUp() throws Exception {
    exams = mock(TpExamRepository.class);
    questions = mock(TpQuestionRepository.class);
    preHigh = mock(PreHighEnglishStore.class);
    part2 = mock(PreHighPart2Store.class);
    part6 = mock(PreHighPart6Store.class);
    catalog = new LearnHubCatalog(exams, questions, preHigh, part2, part6, new PreHighGrammarBank(new ObjectMapper()));
    when(exams.findByKindAndStatusOrderByExamIdAsc(any(), any())).thenReturn(List.of());
    when(questions.countByExamIds(any())).thenReturn(List.of());
    when(questions.findFocusRows(any())).thenReturn(List.of());
  }

  private static TpExam exam(long id, String kind, String code, String title, String subtitle, int limitSec) throws Exception {
    TpExam e = BeanUtils.instantiateClass(TpExam.class.getDeclaredConstructor());
    e.setExamId(id);
    e.setKind(kind);
    e.setExamCode(code);
    e.setTitle(title);
    e.setSubtitle(subtitle);
    e.setTimeLimitSec(limitSec);
    return e;
  }

  private void publish(String kind, TpExam... list) {
    when(exams.findByKindAndStatusOrderByExamIdAsc(eq(kind), eq("PUBLISHED"))).thenReturn(List.of(list));
  }

  private Tab tab(String key) {
    return catalog.tabs().stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow();
  }

  private static Section section(Tab tab, String key) {
    return tab.sections().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
  }

  private static List<Object[]> rows(Object[]... rows) {
    return List.of(rows);
  }

  @Test
  @DisplayName("given 풀이 화면 when tabs then 내신·수능·iBT·SAT·TOEIC·국어·수학·사회·과학 아홉 탭이 순서대로 있다")
  void tabs_nineInOrder() {
    // when
    List<Tab> tabs = catalog.tabs();

    // then
    assertThat(tabs).extracting(Tab::key).containsExactlyElementsOf(LearnHubCatalog.TAB_KEYS);
    assertThat(tabs).extracting(Tab::label).containsExactly("내신", "수능", "iBT", "SAT", "TOEIC", "국어", "수학", "사회", "과학");
  }

  @Test
  @DisplayName("given 주소의 tab 값 when tabOrDefault then 아는 탭은 그대로, 모르는 값·빈 값은 내신")
  void tabOrDefault_fallsBackToNaesin() {
    // given & when & then
    assertThat(LearnHubCatalog.tabOrDefault("toeic")).isEqualTo("toeic");
    assertThat(LearnHubCatalog.tabOrDefault("nope")).isEqualTo("naesin");
    assertThat(LearnHubCatalog.tabOrDefault(null)).isEqualTo("naesin");
  }

  @Test
  @DisplayName("given 게시된 내신 시험 when 내신 탭 then 학교별 묶음에 회차가 들어가고 풀기는 디지털 모의고사 화면으로 시작한다")
  void naesin_groupsBySchoolAndStartsDigitalExam() throws Exception {
    // given
    publish("NAESIN",
        exam(1, "NAESIN", "TP-NAESIN-APM2-2602-R01", "압구정중2 2학기 기말 1회 · Lesson 7 예상문제 1회", "Lesson 7 학습지 · 40문항 · 134점", 1200),
        exam(2, "NAESIN", "TP-NAESIN-SGM2-2602-R01", "신구중2 2학기 기말 1회 · Lesson 7 예상문제 1회", "Lesson 7 · 40문항 · 100점", 1200));
    when(questions.countByExamIds(any())).thenReturn(rows(new Object[] {1L, 40L}, new Object[] {2L, 40L}));

    // when
    Tab naesin = tab("naesin");

    // then
    Section apm2 = section(naesin, "apgujeong-m2");
    assertThat(apm2.school()).isEqualTo("압구정중학교");
    assertThat(apm2.grade()).isEqualTo("중2");
    assertThat(apm2.rows()).hasSize(1);
    Row row = apm2.rows().get(0);
    assertThat(row.title()).isEqualTo("1회 · Lesson 7 예상문제 1회");
    assertThat(row.volume()).isEqualTo("40문항 · 20분 · 134점");
    assertThat(row.actions()).containsExactly(new Action("풀기", "NAESIN", "TP-NAESIN-APM2-2602-R01", null, null, null));
    assertThat(section(naesin, "apgujeong-m1").rows()).isEmpty();
    assertThat(section(naesin, "apgujeong-m1").emptyText()).contains("준비 중");
    assertThat(naesin.access()).isEqualTo(LearnHubCatalog.NAESIN_ACCESS);
    assertThat(catalog.grades()).containsExactly("중1", "중2");
    assertThat(catalog.schools()).containsExactly("압구정중학교", "신구중학교");
  }

  @Test
  @DisplayName("given 예비고1 자료 when 수능 탭 then 부마다 회차 수를 자료에서 세고 회차 단추가 풀이 화면 주소를 가리킨다")
  void csat_listsRoundsFromStores() {
    // given
    when(preHigh.list()).thenReturn(List.of(new PreHighEnglishStore.RoundInfo(1, 50), new PreHighEnglishStore.RoundInfo(2, 50)));
    when(part2.list()).thenReturn(List.of(new PreHighPart2Store.Info(1, "출처")));

    // when
    Section mock = section(tab("csat"), "csat-mock");

    // then
    Row part1 = mock.rows().get(0);
    assertThat(part1.title()).startsWith("1부");
    assertThat(part1.volume()).isEqualTo("2회");
    assertThat(part1.more()).extracting(Action::href).containsExactly("/naesintest/pre-high/1", "/naesintest/pre-high/2");
    assertThat(mock.rows().get(1).more()).extracting(Action::href).containsExactly("/naesintest/pre-high/part2/1");
    assertThat(mock.rows()).extracting(Row::title).noneMatch(t -> t.startsWith("5부") || t.startsWith("6부"));
    assertThat(mock.rows()).extracting(Row::title).anyMatch(t -> t.startsWith("3부")).anyMatch(t -> t.startsWith("4부"));
  }

  @Test
  @DisplayName("given 게시된 iBT·SAT 세트 when 탭 then 문항 수·시간이 붙고 iBT는 공통 시험 화면, SAT는 전용 시험 화면으로 시작한다")
  void ibtAndSat_startTheirRunScreens() throws Exception {
    // given
    publish("PRACTICE", exam(10, "PRACTICE", "TP-PRACTICE-01", "iBT Practice Set 01", "Reading · Listening", 3600));
    publish("SAT", exam(20, "SAT", "TP-SAT-01", "Digital SAT Practice 01", "RW · Math", 8040));
    when(questions.countByExamIds(any())).thenReturn(rows(new Object[] {10L, 120L}), rows(new Object[] {20L, 98L}));

    // when
    Row ibt = section(tab("ibt"), "ibt-practice").rows().get(0);
    Row sat = section(tab("sat"), "sat-sets").rows().get(0);

    // then
    assertThat(ibt.volume()).isEqualTo("120문항 · 60분");
    assertThat(ibt.actions().get(0).run()).isEqualTo("TP");
    assertThat(sat.volume()).isEqualTo("98문항 · 모듈별 시간");
    assertThat(sat.actions().get(0).run()).isEqualTo("SAT");
    assertThat(section(tab("ibt"), "ibt-vocab").emptyText()).contains("준비 중");
  }

  @Test
  @DisplayName("given TOEIC 세트 2개 when TOEIC 탭 then Part 5·6 집중이 맨 위이고 파트·유형별 문항 수를 문항에서 세어 골라 풀게 한다")
  void toeic_groupsByPartAndType() throws Exception {
    // given
    publish("TOEIC",
        exam(31, "TOEIC", "TP-TOEIC-FULL-01", "TOEIC Practice Test 01 — Listening & Reading (기출 유사 1회)", "sub", 7200),
        exam(32, "TOEIC", "TP-TOEIC-RC-01", "TOEIC Reading Practice 01 — Part 5·6·7 (독해 추가 세트 1)", "sub", 4500));
    when(questions.countByExamIds(any())).thenReturn(rows(new Object[] {31L, 7L}, new Object[] {32L, 4L}));
    when(questions.findFocusRows(any())).thenReturn(rows(
        new Object[] {31L, "TOEIC_P2", "[Part2-의문사(Where)] 정답 (A)."},
        new Object[] {31L, "TOEIC_P5", "[문법-품사(부사 자리)] 정답 (A)."},
        new Object[] {31L, "TOEIC_P5", "[문법-품사(명사 자리)] 정답 (B)."},
        new Object[] {31L, "TOEIC_P5", "[어휘-동사] 정답 (C)."},
        new Object[] {31L, "TOEIC_P6", "[Part6-문장삽입] 정답 (D)."},
        new Object[] {31L, "TOEIC_P7", "[세부 사항] 정답 (A).", 147},
        new Object[] {31L, "TOEIC_P7", "[세부 사항] 정답 (B).", 176},
        new Object[] {31L, "TOEIC_P7", "[연계] 정답 (B).", 200},
        new Object[] {32L, "TOEIC_P5", "[문법-품사(형용사 자리)] 정답 (A)."},
        new Object[] {32L, "TOEIC_P5", "[전치사(by와 until)] 정답 (A)."},
        new Object[] {32L, "TOEIC_P6", "[Part6-어휘(문맥)] 정답 (A)."},
        new Object[] {32L, "TOEIC_P7", "[연계] 정답 (A).", 175}));

    // when
    Tab toeic = tab("toeic");

    // then — 강조 묶음이 맨 위
    assertThat(toeic.sections()).extracting(Section::key).containsExactly("toeic-focus", "toeic-p7-last", "toeic-types-5", "toeic-types-6",
        "toeic-p1", "toeic-p2", "toeic-p3", "toeic-p4", "toeic-p7", "toeic-full");
    Section focus = section(toeic, "toeic-focus");
    assertThat(focus.focus()).isTrue();
    assertThat(focus.rows().get(0).title()).isEqualTo("기출 유사 1회");
    assertThat(focus.rows().get(0).actions()).containsExactly(
        new Action("Part 5·6 · 4문항", "TP", "TP-TOEIC-FULL-01", null, "TOEIC_P5,TOEIC_P6", null),
        new Action("Part 5 · 3문항", "TP", "TP-TOEIC-FULL-01", null, "TOEIC_P5", null),
        new Action("Part 6 · 1문항", "TP", "TP-TOEIC-FULL-01", null, "TOEIC_P6", null));
    // 독해 끝부분(176~200번)은 그 구간 문항만 세어 두 번째 강조 묶음으로
    Section last = section(toeic, "toeic-p7-last");
    assertThat(last.focus()).isTrue();
    assertThat(last.rows()).hasSize(1);
    assertThat(last.rows().get(0).sub()).isEqualTo("세부 사항 1 · 연계 1");
    assertThat(last.rows().get(0).actions()).containsExactly(
        new Action("176~200번 · 2문항", "TP", "TP-TOEIC-FULL-01", null, "TOEIC_P7", null, 176, 200));
    // 유형: 어법 먼저(문항 많은 순), 그다음 어휘. 세트마다 그 유형 문항 수.
    Section types = section(toeic, "toeic-types-5");
    assertThat(types.rows()).extracting(Row::title).containsExactly("품사", "전치사", "동사 어휘");
    assertThat(types.rows().get(0).volume()).isEqualTo("3문항");
    assertThat(types.rows().get(0).badge()).isEqualTo("어법");
    assertThat(types.rows().get(0).actions()).containsExactly(
        new Action("기출 유사 1회 · 2문항", "TP", "TP-TOEIC-FULL-01", null, "TOEIC_P5", "문법-품사"),
        new Action("독해 추가 세트 1 · 1문항", "TP", "TP-TOEIC-RC-01", null, "TOEIC_P5", "문법-품사"));
    assertThat(types.rows().get(2).badge()).isEqualTo("어휘");
    assertThat(section(toeic, "toeic-types-6").rows()).extracting(Row::title).containsExactly("문장삽입", "문맥 어휘");
    // 문항이 없는 파트는 준비 중
    assertThat(section(toeic, "toeic-p1").rows()).isEmpty();
    assertThat(section(toeic, "toeic-p1").emptyText()).contains("준비 중");
    assertThat(section(toeic, "toeic-p3").rows()).isEmpty();
    assertThat(section(toeic, "toeic-p7").rows().get(0).sub()).isEqualTo("세부 사항 2 · 연계 1");
    assertThat(section(toeic, "toeic-p2").note()).contains("참고용");
    assertThat(section(toeic, "toeic-full").rows()).hasSize(2);
    assertThat(section(toeic, "toeic-full").rows().get(0).actions().get(0).parts()).isNull();
  }

  @Test
  @DisplayName("given 게시된 타과목 시험이 없음 when 국어·수학·사회·과학 탭 then 문항을 지어내지 않고 준비 중 안내만 보인다")
  void subjects_emptyUntilPublished() {
    // when & then
    for (LearnHubCatalog.Subject s : LearnHubCatalog.SUBJECTS) {
      Tab t = tab(s.tab());
      assertThat(t.rowCount()).isZero();
      assertThat(t.sections()).hasSize(1);
      assertThat(t.sections().get(0).emptyText()).isEqualTo(LearnHubCatalog.SUBJECT_EMPTY);
    }
  }

  @Test
  @DisplayName("given 게시 계약대로 넣은 국어 시험(kind=NAESIN, 코드 머리 TP-NAESIN-KOR-) when tabs then 국어 탭에만 나오고 내신 탭에는 나오지 않는다")
  void subjects_showUpByCodePrefix() throws Exception {
    // given
    publish("NAESIN", exam(41, "NAESIN", "TP-NAESIN-KOR-M2-2602-R01", "중2 국어 2학기 기말 1회", "문학 12~45쪽 · 25문항 · 100점", 750));
    when(questions.countByExamIds(any())).thenReturn(rows(new Object[] {41L, 25L}));

    // when
    List<Tab> tabs = catalog.tabs();

    // then
    Tab korean = tabs.stream().filter(t -> t.key().equals("korean")).findFirst().orElseThrow();
    Row row = korean.sections().get(0).rows().get(0);
    assertThat(row.title()).isEqualTo("중2 국어 2학기 기말 1회");
    assertThat(row.volume()).isEqualTo("25문항 · 13분");
    assertThat(row.actions()).containsExactly(new Action("풀기", "NAESIN", "TP-NAESIN-KOR-M2-2602-R01", null, null, null));
    assertThat(tabs.stream().filter(t -> t.key().equals("math")).findFirst().orElseThrow().rowCount()).isZero();
    assertThat(korean.access()).isEqualTo(LearnHubCatalog.SUBJECT_ACCESS).contains("회원 누구나 전 회차");
    Tab naesin = tabs.stream().filter(t -> t.key().equals("naesin")).findFirst().orElseThrow();
    assertThat(naesin.sections().stream().flatMap(s -> s.rows().stream()).flatMap(r -> r.actions().stream()))
        .extracting(Action::examCode).doesNotContain("TP-NAESIN-KOR-M2-2602-R01");
  }

  @Test
  @DisplayName("given 과목 코드 머리 when 학교 코드 머리와 견줌 then 서로 겹치지 않고 naesin_common.js의 되돌아가기 표와 같다")
  void subjectPrefixes_distinctAndInSyncWithScript() throws IOException {
    // given
    String script = Files.readString(Path.of("src/main/resources/static/js/naesin_common.js"), StandardCharsets.UTF_8);
    String hub = Files.readString(Path.of("src/main/resources/static/js/learn_hub.js"), StandardCharsets.UTF_8);

    // when & then
    for (LearnHubCatalog.Subject s : LearnHubCatalog.SUBJECTS) {
      assertThat(script).contains("'" + s.codePrefix() + "': '" + s.tab() + "'");
      assertThat(hub).contains("'" + s.codePrefix() + "'");
      assertThat(f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.NaesinAccessService.isSubjectExam(
          s.codePrefix() + "X-R02")).isTrue();
      assertThat(f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.NaesinTestPageController.SCHOOLS)
          .noneMatch(school -> school.codePrefix().startsWith(s.codePrefix()) || s.codePrefix().startsWith(school.codePrefix()));
      assertThat(s.codePrefix()).startsWith("TP-NAESIN-").endsWith("-");
    }
  }

  @Test
  @DisplayName("given 모든 탭의 링크 when 컨트롤러·화면 등록 주소와 대조 then 실제로 있는 GET 주소만 가리킨다")
  void links_pointToExistingRoutes() throws IOException {
    // given
    when(preHigh.list()).thenReturn(List.of(new PreHighEnglishStore.RoundInfo(1, 50)));
    when(preHigh.listGrade2()).thenReturn(List.of(new PreHighEnglishStore.RoundInfo(1, 50)));
    when(part2.list()).thenReturn(List.of(new PreHighPart2Store.Info(1, "출처")));
    when(part6.list()).thenReturn(List.of(new PreHighPart2Store.Info(1, "출처")));
    Set<String> routes = routes();

    // when
    List<String> hrefs = catalog.tabs().stream().flatMap(t -> t.sections().stream()).flatMap(s -> s.rows().stream())
        .flatMap(r -> Stream.concat(r.actions().stream(), r.more().stream())).filter(Action::link).map(Action::href).distinct().toList();

    // then
    assertThat(hrefs).isNotEmpty().allSatisfy(href -> assertThat(routes.stream().anyMatch(r -> matches(r, href)))
        .as("등록된 주소가 없는 링크: " + href).isTrue());
    assertThat(matches("/naesintest/school/{slug}", "/naesintest/school/apgujeong-m2")).isTrue();
    assertThat(matches("/naesintest/school/{slug}", "/naesintest/nope")).isFalse();
  }

  @Test
  @DisplayName("given 숫자·제목 when count·join·gradeTag·minutes·setName·typeSummary then 0은 비우고, 있는 것만 잇고, 괄호 이름을 꺼낸다")
  void helpers() {
    // given & when & then
    assertThat(LearnHubCatalog.count(0, "%d회")).isNull();
    assertThat(LearnHubCatalog.count(22, "마무리 %d회")).isEqualTo("마무리 22회");
    assertThat(LearnHubCatalog.join(null, "가", " ", "나")).isEqualTo("가 · 나");
    assertThat(LearnHubCatalog.join((String) null)).isNull();
    assertThat(LearnHubCatalog.gradeTag("2학년")).isEqualTo("중2");
    assertThat(LearnHubCatalog.minutes(4500)).isEqualTo("75분");
    assertThat(LearnHubCatalog.setName("TOEIC Reading Practice 01 — Part 5·6·7 (독해 추가 세트 1)")).isEqualTo("독해 추가 세트 1");
    assertThat(LearnHubCatalog.setName("iBT Practice Set 01")).isEqualTo("iBT Practice Set 01");
    assertThat(LearnHubCatalog.typeSummary(Map.of("세부 사항", 3, "연계", 5, "", 2))).isEqualTo("연계 5 · 세부 사항 3");
    assertThat(LearnHubCatalog.typeSummary(Map.of("", 2))).isNull();
  }

  /** 소스에서 GET 화면 주소를 모은다: @GetMapping 글자 값, 클래스의 PAGE 상수, WebMvcConfig의 view(...) 등록. */
  private static Set<String> routes() throws IOException {
    Set<String> routes = new HashSet<>();
    Pattern constant = Pattern.compile("String (\\w+) = \"(/[^\"]*)\"");
    Pattern mapping = Pattern.compile("@GetMapping\\((?:value\\s*=\\s*)?\\{?([^)]*)\\)");
    Pattern literal = Pattern.compile("(\\w+\\s*\\+\\s*)?\"(/[^\"]*)\"|\\b([A-Z_]+)\\b");
    Pattern view = Pattern.compile("view\\(registry, \"(/[^\"]*)\"");
    Pattern prefix = Pattern.compile("@RequestMapping\\(\"(/[^\"]*)\"\\)");
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher v = view.matcher(source);
        while (v.find()) routes.add(v.group(1));
        if (!source.contains("@GetMapping")) continue;
        java.util.Map<String, String> constants = new java.util.HashMap<>();
        Matcher c = constant.matcher(source);
        while (c.find()) constants.put(c.group(1), c.group(2));
        Matcher p = prefix.matcher(source);
        String base = p.find() ? p.group(1) : "";
        Matcher m = mapping.matcher(source);
        while (m.find()) {
          Matcher l = literal.matcher(m.group(1));
          while (l.find()) {
            if (l.group(2) != null) {
              String head = l.group(1) == null ? "" : constants.getOrDefault(l.group(1).replace("+", "").strip(), "");
              routes.add(base + head + l.group(2));
            } else if (constants.containsKey(l.group(3))) {
              routes.add(base + constants.get(l.group(3)));
            }
          }
        }
      }
    }
    return routes;
  }

  private static boolean matches(String route, String href) {
    List<String> a = new ArrayList<>(List.of(route.split("/")));
    List<String> b = new ArrayList<>(List.of(href.split("/")));
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).startsWith("{") && !a.get(i).equals(b.get(i))) return false;
    }
    return true;
  }
}
