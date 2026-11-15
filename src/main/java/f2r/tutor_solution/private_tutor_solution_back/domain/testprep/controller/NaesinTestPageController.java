package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ExamListItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 내신 실전 연습(/naesintest) — TestPrep(New iBT / Digital SAT)과 분리된 화면.
 * 문제·응시·채점 데이터는 TESTPREP 테이블과 /api/testprep API(kind=NAESIN)를 그대로 쓴다.
 * 첫 화면(회차 목록)은 비회원도 볼 수 있고, 응시·결과 화면은 로그인 회원 전용(SecurityConfig).
 */
@Controller
@RequiredArgsConstructor
public class NaesinTestPageController {

  /** 화면 시험 시간: 문항당 30초(40문항 20분). naesin_run.js의 SEC_PER_QUESTION과 같아야 한다. */
  static final int SEC_PER_QUESTION = 30;

  private static final Pattern ROUND = Pattern.compile("기말 (\\d+회) · (.+)$");
  private static final Pattern SET_SUFFIX = Pattern.compile("\\s*예상문제\\s*\\d+회$");
  private static final Pattern POINTS = Pattern.compile("(\\d+)점$");

  private final TestPrepService testPrepService;

  /** 목록 한 줄. group은 같은 범위끼리 묶는 이름(Lesson 7, Lesson 7·8 종합, Final). */
  public record Row(String examCode, String round, String setName, String group, String scope,
      long questionCount, int minutes, Integer maxPoints) {}

  public record Group(String name, List<Row> rows) {}

  @GetMapping("/naesintest")
  public String home(Model model) {
    model.addAttribute("canonical", "https://feel2real.co.kr/naesintest");
    model.addAttribute("groups", group(testPrepService.listExams(TestPrepService.KIND_NAESIN)));
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

  static Row toRow(ExamListItem e) {
    String round = "";
    String setName = e.title();
    Matcher m = ROUND.matcher(e.title() == null ? "" : e.title());
    if (m.find()) {
      round = m.group(1);
      setName = m.group(2);
    }
    String group = SET_SUFFIX.matcher(setName).replaceFirst("");
    String scope = e.subtitle() == null ? "" : e.subtitle().split(" · ")[0];
    Matcher p = POINTS.matcher(e.subtitle() == null ? "" : e.subtitle());
    Integer maxPoints = p.find() ? Integer.valueOf(p.group(1)) : null;
    int minutes = (int) (e.questionCount() * SEC_PER_QUESTION / 60);
    return new Row(e.examCode(), round, setName, group, scope, e.questionCount(), minutes, maxPoints);
  }

  static List<Group> group(List<ExamListItem> exams) {
    Map<String, List<Row>> byGroup = new LinkedHashMap<>();
    for (ExamListItem e : exams) {
      Row r = toRow(e);
      byGroup.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);
    }
    return byGroup.entrySet().stream().map(en -> new Group(en.getKey(), en.getValue())).toList();
  }
}
