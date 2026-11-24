package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ExamListItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.NaesinAccessService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 내신 실전 연습(/naesintest) — TestPrep(New iBT / Digital SAT)과 분리된 화면.
 * 문제·응시·채점 데이터는 TESTPREP 테이블과 /api/testprep API(kind=NAESIN)를 그대로 쓴다.
 * 첫 화면(학교 고르기)과 학교별 회차 목록은 비회원도 볼 수 있고, 응시·결과 화면은 로그인 회원 전용(SecurityConfig).
 */
@Controller
@RequiredArgsConstructor
public class NaesinTestPageController {

  /** 화면 시험 시간: 문항당 30초(40문항 20분). naesin_run.js의 SEC_PER_QUESTION과 같아야 한다. */
  static final int SEC_PER_QUESTION = 30;

  /**
   * 학교별 페이지. 시험 코드 앞부분(codePrefix)으로 그 학교 회차를 고른다(예: TP-NAESIN-APM2-2602-R01).
   * 새 학교는 여기와 naesin_common.js의 SCHOOLS에 함께 추가한다. 문제가 아직 없는 학교는 '준비 중'으로 보인다.
   */
  public record School(String slug, String name, String grade, String codePrefix, String scope) {
    public String title() { return name + " " + grade; }
  }

  public static final List<School> SCHOOLS = List.of(
      new School("apgujeong-m2", "압구정중학교", "2학년", "TP-NAESIN-APM2-", "Lesson 7·8"),
      new School("singu-m2", "신구중학교", "2학년", "TP-NAESIN-SGM2-", "Lesson 7·8"),
      new School("apgujeong-m1", "압구정중학교", "1학년", "TP-NAESIN-APM1-", "Lesson 3~5"),
      new School("deokmyeong-m2", "덕명여자중학교", "2학년", "TP-NAESIN-DMM2-", "Lesson 5~7"));

  private static final Pattern ROUND = Pattern.compile("기말 (\\d+회) · (.+)$");
  private static final Pattern TRAILING_ROUND = Pattern.compile("예상문제\\s*(\\d+회)$");
  private static final Pattern SET_SUFFIX = Pattern.compile("\\s*예상문제\\s*\\d+회$");
  private static final Pattern POINTS = Pattern.compile("(\\d+)점$");

  private final TestPrepService testPrepService;

  /** 목록 한 줄. group은 같은 범위끼리 묶는 이름(Lesson 7, Lesson 7·8 종합, Final). */
  public record Row(String examCode, String round, String setName, String group, String scope,
      long questionCount, int minutes, Integer maxPoints, boolean freeRound) {}

  public record Group(String name, List<Row> rows) {}

  /** 학교 고르기 표의 한 줄: 학교와 올라와 있는 회차 수. */
  public record SchoolRow(School school, int rounds) {}

  @GetMapping("/naesintest")
  public String home(Model model) {
    List<ExamListItem> exams = testPrepService.listExams(TestPrepService.KIND_NAESIN);
    model.addAttribute("canonical", "https://feel2real.co.kr/naesintest");
    model.addAttribute("schools", schoolRows(exams));
    return "naesintest/schools";
  }

  @GetMapping("/naesintest/school/{slug}")
  public String school(@PathVariable String slug, Model model) {
    School school = SCHOOLS.stream().filter(s -> s.slug().equals(slug)).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    List<ExamListItem> mine = testPrepService.listExams(TestPrepService.KIND_NAESIN).stream()
        .filter(e -> e.examCode() != null && e.examCode().startsWith(school.codePrefix()))
        .sorted(java.util.Comparator.comparing(ExamListItem::examCode)).toList();
    model.addAttribute("canonical", "https://feel2real.co.kr/naesintest/school/" + slug);
    model.addAttribute("school", school);
    model.addAttribute("roundCount", mine.size());
    model.addAttribute("groups", group(mine));
    return "naesintest/home";
  }

  @GetMapping("/naesintest/run")
  public String run() {
    return "naesintest/run";
  }

  @GetMapping("/naesintest/review")
  public String review() {
    return "naesintest/review";
  }

  static List<SchoolRow> schoolRows(List<ExamListItem> exams) {
    return SCHOOLS.stream().map(s -> new SchoolRow(s, (int) exams.stream()
        .filter(e -> e.examCode() != null && e.examCode().startsWith(s.codePrefix())).count())).toList();
  }

  public static Row toRow(ExamListItem e) {
    String round = "";
    String setName = e.title();
    Matcher m = ROUND.matcher(e.title() == null ? "" : e.title());
    if (m.find()) {
      round = m.group(1);
      setName = m.group(2);
    }
    if (round.isEmpty()) {
      Matcher trailing = TRAILING_ROUND.matcher(setName == null ? "" : setName);
      if (trailing.find()) round = trailing.group(1);
    }
    String group = SET_SUFFIX.matcher(setName).replaceFirst("");
    String scope = e.subtitle() == null ? "" : e.subtitle().split(" · ")[0];
    Matcher p = POINTS.matcher(e.subtitle() == null ? "" : e.subtitle());
    Integer maxPoints = p.find() ? Integer.valueOf(p.group(1)) : null;
    int minutes = (int) (e.questionCount() * SEC_PER_QUESTION / 60);
    return new Row(e.examCode(), round, setName, group, scope, e.questionCount(), minutes, maxPoints,
        NaesinAccessService.isOpenRound(e.examCode(), e.title()));
  }

  public static List<Group> group(List<ExamListItem> exams) {
    Map<String, List<Row>> byGroup = new LinkedHashMap<>();
    for (ExamListItem e : exams) {
      Row r = toRow(e);
      byGroup.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);
    }
    return byGroup.entrySet().stream().map(en -> new Group(en.getKey(), en.getValue())).toList();
  }
}
