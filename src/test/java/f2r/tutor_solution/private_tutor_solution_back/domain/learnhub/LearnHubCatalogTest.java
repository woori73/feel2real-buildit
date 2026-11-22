package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Card;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Group;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

@DisplayName("LearnHubCatalog — 학습 콘텐츠 모음 카드")
class LearnHubCatalogTest {

  TpExamRepository exams;
  PreHighEnglishStore preHigh;
  PreHighPart2Store part2;
  PreHighPart6Store part6;
  LearnHubCatalog catalog;

  @BeforeEach
  void setUp() throws Exception {
    exams = mock(TpExamRepository.class);
    preHigh = mock(PreHighEnglishStore.class);
    part2 = mock(PreHighPart2Store.class);
    part6 = mock(PreHighPart6Store.class);
    catalog = new LearnHubCatalog(exams, preHigh, part2, part6, new PreHighGrammarBank(new ObjectMapper()));
    when(exams.findByKindAndStatusOrderByExamIdAsc(any(), any())).thenReturn(List.of());
  }

  private static TpExam exam(String code) throws Exception {
    TpExam e = BeanUtils.instantiateClass(TpExam.class.getDeclaredConstructor());
    e.setExamCode(code);
    return e;
  }

  private Card card(String href, String titlePart) {
    return catalog.groups().stream().flatMap(g -> g.cards().stream())
        .filter(c -> c.href().equals(href) && c.title().contains(titlePart)).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("given 모음 when groups then 내신 대비·수능·모의고사·예비고1·iBT·SAT·토익·입시 정보 다섯 묶음이 순서대로 있다")
  void groups_haveFiveInOrder() {
    // when
    List<Group> groups = catalog.groups();

    // then
    assertThat(groups).extracting(Group::key).containsExactly("naesin", "csat", "prehigh", "testprep", "admissions");
    assertThat(groups).allSatisfy(g -> assertThat(g.cards()).isNotEmpty());
  }

  @Test
  @DisplayName("given 공개된 내신 시험 3개 when groups then 전체 회차 수와 학교별 회차 수를 올라온 자료에서 센다")
  void naesin_countsFromPublishedExams() throws Exception {
    // given
    when(exams.findByKindAndStatusOrderByExamIdAsc("NAESIN", "PUBLISHED")).thenReturn(List.of(
        exam("TP-NAESIN-APM2-2602-R01"), exam("TP-NAESIN-APM2-2602-R02"), exam("TP-NAESIN-SGM2-2602-R01")));

    // when & then
    assertThat(card("/naesintest", "학교 고르기").volume()).isEqualTo("3회차");
    Card apm2 = card("/naesintest/school/apgujeong-m2", "압구정중학교 2학년");
    assertThat(apm2.volume()).isEqualTo("2회차");
    assertThat(apm2.audiences()).containsExactly("중2");
    assertThat(apm2.school()).isEqualTo("압구정중학교");
    assertThat(card("/naesintest/school/apgujeong-m1", "1학년").audiences()).containsExactly("중1");
  }

  @Test
  @DisplayName("given 자료가 아직 없는 묶음 when groups then 분량을 0으로 적지 않고 비워 둔다")
  void volume_isOmittedWhenEmpty() {
    // when & then
    assertThat(card("/naesintest/school/singu-m2", "신구중").volume()).isNull();
    assertThat(card("/testprep/toeic", "토익").volume()).isNull();
    assertThat(card("/naesintest/pre-high", "1~4부").volume())
        .isEqualTo("3부 어법 10단원 · 마무리 10회 · 4부 어휘 50회");
  }

  @Test
  @DisplayName("given 카드 when 로그인 안내 then 누구나 보는 화면만 로그인 불필요로, 나머지는 로그인·접속 코드 안내가 붙는다")
  void access_marksLoginNeeded() {
    // when & then
    assertThat(card("/csat/2027-english", "수능").loginNeeded()).isFalse();
    assertThat(card("/admissions/articles", "바뀌는 입시").loginNeeded()).isFalse();
    assertThat(card("/testprep/practice", "iBT").access()).isEqualTo(LearnHubCatalog.LOGIN);
    assertThat(card("/naesintest/app", "재원생").access()).isEqualTo(LearnHubCatalog.CODE);
    assertThat(card("/naesintest", "학교 고르기").loginNeeded()).isTrue();
  }

  @Test
  @DisplayName("given 모든 카드 when 학년 표시 then 화면의 학년 거르기 값 안에 있고, 학교 표시는 학교 거르기 값 안에 있다")
  void tags_matchFilterValues() {
    // when
    List<Card> cards = catalog.groups().stream().flatMap(g -> g.cards().stream()).toList();

    // then
    assertThat(cards).allSatisfy(c -> {
      assertThat(c.audiences()).isNotEmpty();
      assertThat(LearnHubCatalog.AUDIENCES).containsAll(c.audiences());
      if (c.school() != null) assertThat(catalog.schools()).contains(c.school());
    });
    assertThat(catalog.schools()).containsExactly("압구정중학교", "신구중학교");
  }

  @Test
  @DisplayName("given 모든 카드 링크 when 컨트롤러·화면 등록 주소와 대조 then 실제로 있는 GET 주소만 가리킨다")
  void links_pointToExistingRoutes() throws IOException {
    // given
    Set<String> routes = routes();

    // when
    List<String> hrefs = catalog.groups().stream().flatMap(g -> g.cards().stream()).map(Card::href).distinct().toList();

    // then
    assertThat(hrefs).allSatisfy(href -> assertThat(routes.stream().anyMatch(r -> matches(r, href)))
        .as("등록된 주소가 없는 링크: " + href).isTrue());
    assertThat(matches("/naesintest/school/{slug}", "/naesintest/school/apgujeong-m2")).isTrue();
    assertThat(matches("/naesintest/school/{slug}", "/naesintest/nope")).isFalse();
  }

  @Test
  @DisplayName("given 숫자·단위 when count·join·gradeTag then 0은 비우고, 있는 것만 가운뎃점으로 잇고, '2학년'은 '중2'")
  void helpers() {
    // given & when & then
    assertThat(LearnHubCatalog.count(0, "회차")).isNull();
    assertThat(LearnHubCatalog.count(7, "회차")).isEqualTo("7회차");
    assertThat(LearnHubCatalog.count(22, "2부 실전 독해 %d회")).isEqualTo("2부 실전 독해 22회");
    assertThat(LearnHubCatalog.join(null, "가", " ", "나")).isEqualTo("가 · 나");
    assertThat(LearnHubCatalog.join((String) null)).isNull();
    assertThat(LearnHubCatalog.gradeTag("2학년")).isEqualTo("중2");
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
