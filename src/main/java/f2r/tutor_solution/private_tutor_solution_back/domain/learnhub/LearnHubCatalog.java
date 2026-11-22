package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.NaesinTestPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ExamListItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.NaesinAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart6Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.ToeicFocus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생 풀이 첫 화면(/learn)의 탭·시험 목록. 새 표·새 풀이 엔진 없이 이미 게시된 시험(tp_exams, 예비고1 자료)만 읽는다.
 * 문항을 지어내지 않는다: 게시된 것이 없으면 「준비 중」으로 보이고, 분량은 글로 적지 않고 올라와 있는 자료에서 센다.
 */
@Service
@Transactional(readOnly = true)
public class LearnHubCatalog {

  public static final String OPEN = "누구나";
  public static final String LOGIN = "로그인 필요";
  public static final String SUBJECT_ACCESS = "목록은 누구나 · 풀이는 로그인 (회원 누구나 전 회차)";
  public static final String NAESIN_ACCESS = "목록은 누구나 · 풀이는 로그인 (1회는 회원 누구나, 2회부터 재원생)";
  public static final String PREHIGH_ACCESS = "목록은 누구나 · 풀이는 로그인";
  public static final String SUBJECT_EMPTY = "준비 중 — 제작 공장에서 게시하면 여기에 올라옵니다.";

  /** 풀기 단추가 여는 풀이 화면. NAESIN·TP·SAT는 응시를 만들고 곧바로 풀이 화면으로, LINK는 그 주소로 간다. */
  public static final String RUN_NAESIN = "NAESIN";
  public static final String RUN_TP = "TP";
  public static final String RUN_SAT = "SAT";
  public static final String RUN_LINK = "LINK";

  /**
   * 타과목 디지털 모의고사의 게시 계약: tp_exams.kind = NAESIN(내신과 같은 풀이·채점 틀)이고
   * exam_code가 아래 머리로 시작하면 그 과목 탭에 나온다. 새 열·새 kind를 만들지 않는다.
   */
  public record Subject(String tab, String label, String codePrefix) {}

  public static final List<Subject> SUBJECTS = List.of(
      new Subject("korean", "국어", NaesinAccessService.SUBJECT_CODE_PREFIXES.get(0)),
      new Subject("math", "수학", NaesinAccessService.SUBJECT_CODE_PREFIXES.get(1)),
      new Subject("social", "사회", NaesinAccessService.SUBJECT_CODE_PREFIXES.get(2)),
      new Subject("science", "과학", NaesinAccessService.SUBJECT_CODE_PREFIXES.get(3)));

  public static final List<String> TAB_KEYS =
      List.of("naesin", "csat", "ibt", "sat", "toeic", "korean", "math", "social", "science");

  /** 단추 한 개. parts·focusTag는 TOEIC 파트·유형만 골라 풀 때만 쓴다. */
  public record Action(String label, String run, String examCode, String href, String parts, String focusTag,
      Integer fromNo, Integer toNo) {
    public Action(String label, String run, String examCode, String href, String parts, String focusTag) {
      this(label, run, examCode, href, parts, focusTag, null, null);
    }

    static Action start(String label, String run, String examCode) {
      return new Action(label, run, examCode, null, null, null);
    }

    static Action link(String label, String href) {
      return new Action(label, RUN_LINK, null, href, null, null);
    }

    static Action toeic(String label, String examCode, String parts, String focusTag) {
      return new Action(label, RUN_TP, examCode, null, parts, focusTag);
    }

    static Action toeicRange(String label, String examCode, String parts, int fromNo, int toNo) {
      return new Action(label, RUN_TP, examCode, null, parts, null, fromNo, toNo);
    }

    public boolean link() { return RUN_LINK.equals(run); }
  }

  /** 목록 한 줄. more = 회차가 많은 자료의 회차 고르기(접어 둔다). */
  public record Row(String title, String sub, String volume, String badge, List<Action> actions, List<Action> more) {}

  /** 탭 안의 묶음. school·grade는 내신 탭의 거르기 값. focus = 강조 묶음. rows가 비면 emptyText를 보인다. */
  public record Section(String key, String title, String note, String school, String grade, boolean focus,
      List<Row> rows, String emptyText) {}

  public record Tab(String key, String label, String lead, String access, boolean loginNeeded, List<Section> sections) {
    public int rowCount() {
      return sections.stream().mapToInt(s -> s.rows().size()).sum();
    }
  }

  private final TpExamRepository exams;
  private final TpQuestionRepository questions;
  private final PreHighEnglishStore preHigh;
  private final PreHighPart2Store part2;
  private final PreHighPart6Store part6;
  private final PreHighGrammarBank grammar;

  public LearnHubCatalog(TpExamRepository exams, TpQuestionRepository questions, PreHighEnglishStore preHigh,
      PreHighPart2Store part2, PreHighPart6Store part6, PreHighGrammarBank grammar) {
    this.exams = exams;
    this.questions = questions;
    this.preHigh = preHigh;
    this.part2 = part2;
    this.part6 = part6;
    this.grammar = grammar;
  }

  public List<Tab> tabs() {
    List<ExamListItem> naesinKind = published(TestPrepService.KIND_NAESIN);
    List<Tab> tabs = new ArrayList<>();
    tabs.add(naesin(naesinKind));
    tabs.add(csat());
    tabs.add(ibt());
    tabs.add(sat());
    tabs.add(toeic());
    for (Subject s : SUBJECTS) tabs.add(subject(s, naesinKind));
    return tabs;
  }

  /** 주소의 ?tab= 값을 탭 이름으로. 모르는 값이면 첫 탭(내신). */
  public static String tabOrDefault(String tab) {
    return tab != null && TAB_KEYS.contains(tab) ? tab : TAB_KEYS.get(0);
  }

  /** 내신 탭의 학년 거르기 값(올라와 있는 학교의 학년). */
  public List<String> grades() {
    return NaesinTestPageController.SCHOOLS.stream().map(s -> gradeTag(s.grade())).distinct().sorted().toList();
  }

  /** 내신 탭의 학교 거르기 값. */
  public List<String> schools() {
    return NaesinTestPageController.SCHOOLS.stream().map(NaesinTestPageController.School::name).distinct().toList();
  }

  // ── 내신: 지금의 디지털 모의고사 그대로 ─────────────────────────────────────────────

  Tab naesin(List<ExamListItem> all) {
    List<Section> sections = new ArrayList<>();
    for (NaesinTestPageController.School s : NaesinTestPageController.SCHOOLS) {
      List<Row> rows = new ArrayList<>();
      for (ExamListItem e : all) {
        if (e.examCode() == null || !e.examCode().startsWith(s.codePrefix())) continue;
        NaesinTestPageController.Row r = NaesinTestPageController.toRow(e);
        rows.add(new Row(join(r.round(), r.setName()), r.scope(),
            join(r.questionCount() + "문항", r.minutes() + "분", r.maxPoints() == null ? null : r.maxPoints() + "점"),
            null, List.of(Action.start("풀기", RUN_NAESIN, e.examCode())), List.of()));
      }
      sections.add(new Section(s.slug(), s.title(), "시험 범위 " + s.scope(), s.name(), gradeTag(s.grade()), false, rows,
          "이 학교 예상문제는 준비 중입니다."));
    }
    sections.add(new Section("naesin-app", "재원생 앱", "원장님이 발급한 접속 코드로 들어와 배정된 시험을 풉니다.", null, null, false,
        List.of(new Row("재원생 디지털 모의고사", "접속 코드 필요", null, null,
            List.of(Action.link("앱 열기", "/naesintest/app")), List.of())), null));
    return new Tab("naesin", "내신", "학교 시험 범위에 맞춘 영어 예상문제를 디지털 모의고사로 풉니다. 문항당 30초, 제출하면 바로 채점됩니다.",
        NAESIN_ACCESS, true, sections);
  }

  // ── 수능: 해설 + 예비고1·고2 디지털 모의고사(회차를 고르면 바로 풀이) ─────────────────────────

  Tab csat() {
    List<Row> mock = new ArrayList<>();
    String base = "/naesintest/pre-high";
    addRounds(mock, "1부 · 고1 기출 독해·어휘", "예비고1", numbers(preHigh.list().stream().map(PreHighEnglishStore.RoundInfo::number).toList()),
        n -> base + "/" + n);
    addRounds(mock, "2부 · 실전 독해", "예비고1", numbers(part2.list().stream().map(PreHighPart2Store.Info::number).toList()),
        n -> base + "/part2/" + n);
    List<Action> grammarLinks = new ArrayList<>();
    for (PreHighGrammarBank.ChapterInfo c : grammar.list()) {
      grammarLinks.add(Action.link(c.number() + "단원 1부", base + "/grammar/" + c.number() + "-1"));
      grammarLinks.add(Action.link(c.number() + "단원 2부", base + "/grammar/" + c.number() + "-2"));
    }
    for (Integer r : grammar.finalRounds()) grammarLinks.add(Action.link("마무리 " + r + "회", base + "/grammar/final-" + r));
    if (!grammarLinks.isEmpty()) {
      mock.add(new Row("3부 · 내신 대비 어법", "예비고1",
          join(count(grammar.list().size(), "%d단원"), count(grammar.finalRounds().size(), "마무리 %d회")), null, List.of(), grammarLinks));
    }
    addRounds(mock, "4부 · 수능 대비 어휘", "예비고1", range(PreHighVocabularyBank.ROUND_COUNT), n -> base + "/part4/" + n);
    addRounds(mock, "5부 · 고2 기출 독해·어휘", "고2", numbers(preHigh.listGrade2().stream().map(PreHighEnglishStore.RoundInfo::number).toList()),
        n -> base + "/part5/" + n);
    addRounds(mock, "6부 · 고2 실전 독해", "고2", numbers(part6.list().stream().map(PreHighPart2Store.Info::number).toList()),
        n -> base + "/part6/" + n);
    addRounds(mock, "7부 · 고2 어휘", "고2", range(PreHighVocabularyBank.GRADE_TWO_ROUND_COUNT), n -> base + "/part7/" + n);

    List<Section> sections = List.of(
        new Section("csat-mock", "디지털 영어 모의고사", "회차를 고르면 바로 풀이 화면이 열립니다. " + PREHIGH_ACCESS + ".", null, null, false,
            mock, "모의고사 자료는 준비 중입니다."),
        new Section("csat-review", "수능 해설·자료", null, null, null, false, List.of(
            new Row("2027 수능 영어 해설", "홀수형 문항을 문제 → 근거 → 정답 → 해설 순서로 봅니다. " + OPEN + "(필기본 PDF는 로그인).", null, null,
                List.of(Action.link("해설 보기", "/csat/2027-english")), List.of()),
            new Row("모의고사 위키", "고1·고2·고3 영어 모의고사 자료. 첫 화면은 누구나, 자료는 로그인.", null, null,
                List.of(Action.link("열기", "/wiki")), List.of())), null));
    return new Tab("csat", "수능", "수능·모의고사 영어를 회차별로 풉니다.", PREHIGH_ACCESS, true, sections);
  }

  private interface RoundUrl {
    String of(int round);
  }

  private static void addRounds(List<Row> rows, String title, String sub, List<Integer> rounds, RoundUrl url) {
    if (rounds.isEmpty()) return; // 자료가 없는 곳(로컬 등)에서는 줄을 만들지 않는다
    rows.add(new Row(title, sub, rounds.size() + "회", null, List.of(),
        rounds.stream().map(n -> Action.link(n + "회", url.of(n))).toList()));
  }

  // ── iBT · SAT: 지금 풀이 화면 그대로, 목록에서 바로 시작 ────────────────────────────────

  Tab ibt() {
    List<Section> sections = List.of(
        new Section("ibt-practice", "iBT 모의고사", "시간을 재고 푸는 세트. 자동 채점 문항은 내부 밴드 추정치를 줍니다.", null, null, false,
            examRows(published("PRACTICE"), RUN_TP, true), "게시된 세트가 없습니다. 준비 중입니다."),
        new Section("ibt-vocab", "어휘 퀴즈", "학술 어휘를 뜻·문맥으로 익힙니다.", null, null, false,
            examRows(published("VOCAB"), RUN_TP, true), "게시된 퀴즈가 없습니다. 준비 중입니다."),
        new Section("ibt-more", "함께 보기", null, null, null, false, List.of(
            new Row("영역별 가이드", "읽기·듣기·말하기·쓰기 안내", null, null, List.of(Action.link("열기", "/testprep/guide")), List.of()),
            new Row("내 점수 · 오답 다시 풀기", "지난 응시 기록과 틀린 문항", null, null,
                List.of(Action.link("내 점수", "/testprep/scores"), Action.link("내 학습", "/testprep/learning")), List.of())), null));
    return new Tab("ibt", "iBT", "TOEFL iBT 형식 연습. 풀기를 누르면 바로 시험 화면이 열립니다.", LOGIN, true, sections);
  }

  Tab sat() {
    List<Section> sections = List.of(new Section("sat-sets", "디지털 SAT 세트", "모듈별로 시간이 흐르는 전용 시험 화면에서 풉니다.", null, null, false,
        examRows(published(TestPrepService.KIND_SAT), RUN_SAT, false), "게시된 세트가 없습니다. 준비 중입니다."));
    return new Tab("sat", "SAT", "디지털 SAT 형식 연습. 풀기를 누르면 바로 시험 화면이 열립니다.", LOGIN, true, sections);
  }

  private List<Row> examRows(List<ExamListItem> list, String run, boolean showMinutes) {
    return list.stream().map(e -> new Row(e.title(), e.subtitle(),
        join(e.questionCount() + "문항", showMinutes ? minutes(e.timeLimitSec()) : "모듈별 시간"), difficulty(e.difficulty()),
        List.of(Action.start("풀기", run, e.examCode())), List.of())).toList();
  }

  // ── TOEIC: 파트·유형별로 묶고 Part 5·6 어휘·어법을 맨 위에 ──────────────────────────────

  Tab toeic() {
    List<ExamListItem> sets = published(TestPrepService.KIND_TOEIC);
    // 세트 → 파트(task_type) → 유형(해설 꼬리표) → 문항 수
    Map<Long, Map<String, Map<String, Integer>>> bySet = new HashMap<>();
    Map<Long, Map<String, Integer>> lastPart = new HashMap<>(); // 세트 → Part 7 끝부분(176~200번) 유형별 문항 수
    if (!sets.isEmpty()) {
      for (Object[] r : questions.findFocusRows(sets.stream().map(ExamListItem::examId).toList())) {
        String label = ToeicFocus.label((String) r[2]);
        if ("TOEIC_P7".equals(r[1]) && r.length > 3 && r[3] != null
            && ToeicFocus.inRange(((Number) r[3]).intValue(), ToeicFocus.PART7_LAST_FROM, ToeicFocus.PART7_LAST_TO)) {
          lastPart.computeIfAbsent((Long) r[0], k -> new LinkedHashMap<>()).merge(label == null ? "" : label, 1, Integer::sum);
        }
        bySet.computeIfAbsent((Long) r[0], k -> new HashMap<>()).computeIfAbsent((String) r[1], k -> new LinkedHashMap<>())
            .merge(label == null ? "" : label, 1, Integer::sum);
      }
    }
    List<Section> sections = new ArrayList<>();
    sections.add(focusSets(sets, bySet));
    sections.add(part7Last(sets, lastPart));
    sections.add(focusTypes(sets, bySet, "TOEIC_P5", "Part 5 유형별로 골라 풀기", "틀리기 쉬운 유형만 모아 풉니다. 단추의 숫자는 그 세트에 든 문항 수입니다."));
    sections.add(focusTypes(sets, bySet, "TOEIC_P6", "Part 6 유형별로 골라 풀기", "장문 빈칸을 어법·어휘·문장 삽입·연결어로 나눠 풉니다."));
    for (ToeicFocus.Part p : ToeicFocus.PARTS) {
      if (p.number() == 5 || p.number() == 6) continue;
      sections.add(partSection(p, sets, bySet));
    }
    sections.add(new Section("toeic-full", "실전 세트 전체 풀기", "실제 번호·실제 길이로 시간을 재고 풉니다. 영역별 추정 점수(5~495)가 나옵니다.", null, null, false,
        examRows(sets, RUN_TP, true), "게시된 세트가 없습니다. 준비 중입니다."));
    return new Tab("toeic", "TOEIC", "많이 틀리는 Part 5·6 어휘·어법과 Part 7 끝부분(이중·삼중 지문)을 맨 위에 두고, 파트·유형별로 나눠 풉니다. 골라 풀면 추정 점수 없이 정답 수와 해설을 봅니다.",
        LOGIN, true, sections);
  }

  private Section focusSets(List<ExamListItem> sets, Map<Long, Map<String, Map<String, Integer>>> bySet) {
    List<Row> rows = new ArrayList<>();
    for (ExamListItem e : sets) {
      int p5 = partCount(bySet, e.examId(), "TOEIC_P5");
      int p6 = partCount(bySet, e.examId(), "TOEIC_P6");
      if (p5 + p6 == 0) continue;
      List<Action> actions = new ArrayList<>();
      if (p5 > 0 && p6 > 0) actions.add(Action.toeic("Part 5·6 · " + (p5 + p6) + "문항", e.examCode(), "TOEIC_P5,TOEIC_P6", null));
      if (p5 > 0) actions.add(Action.toeic("Part 5 · " + p5 + "문항", e.examCode(), "TOEIC_P5", null));
      if (p6 > 0) actions.add(Action.toeic("Part 6 · " + p6 + "문항", e.examCode(), "TOEIC_P6", null));
      rows.add(new Row(setName(e.title()), e.title(), (p5 + p6) + "문항", "집중", actions, List.of()));
    }
    return new Section("toeic-focus", "Part 5·6 어휘·어법 집중", "단문 빈칸(Part 5)과 장문 빈칸(Part 6)만 골라 풉니다. 시간은 실제 시험과 같이 문항당 45초입니다.",
        null, null, true, rows, "Part 5·6 문항은 준비 중입니다.");
  }

  /** 많이 틀리는 Part 7 끝부분: 실제 번호 176~200번(이중·삼중 지문). */
  private Section part7Last(List<ExamListItem> sets, Map<Long, Map<String, Integer>> lastPart) {
    List<Row> rows = new ArrayList<>();
    for (ExamListItem e : sets) {
      Map<String, Integer> byLabel = lastPart.get(e.examId());
      if (byLabel == null) continue;
      int n = byLabel.values().stream().mapToInt(Integer::intValue).sum();
      rows.add(new Row(setName(e.title()), typeSummary(byLabel), n + "문항", "집중", List.of(Action.toeicRange(
          ToeicFocus.PART7_LAST_FROM + "~" + ToeicFocus.PART7_LAST_TO + "번 · " + n + "문항", e.examCode(), "TOEIC_P7",
          ToeicFocus.PART7_LAST_FROM, ToeicFocus.PART7_LAST_TO)), List.of()));
    }
    return new Section("toeic-p7-last", "Part 7 끝부분 · 이중·삼중 지문", "독해 마지막 "
        + (ToeicFocus.PART7_LAST_TO - ToeicFocus.PART7_LAST_FROM + 1) + "문항(" + ToeicFocus.PART7_LAST_FROM + "~" + ToeicFocus.PART7_LAST_TO
        + "번). 지문 두세 개를 함께 읽고 푸는 묶음입니다. 문항당 45초.", null, null, true, rows, "Part 7 끝부분 문항은 준비 중입니다.");
  }

  private Section focusTypes(List<ExamListItem> sets, Map<Long, Map<String, Map<String, Integer>>> bySet, String taskType,
      String title, String note) {
    Map<String, Integer> totals = new LinkedHashMap<>();
    for (ExamListItem e : sets) {
      bySet.getOrDefault(e.examId(), Map.of()).getOrDefault(taskType, Map.of())
          .forEach((label, n) -> { if (!label.isEmpty()) totals.merge(label, n, Integer::sum); });
    }
    List<String> labels = new ArrayList<>(totals.keySet());
    labels.sort(Comparator.<String, String>comparing(ToeicFocus::area).thenComparing(l -> -totals.get(l)).thenComparing(l -> l));
    List<Row> rows = new ArrayList<>();
    for (String label : labels) {
      List<Action> actions = new ArrayList<>();
      for (ExamListItem e : sets) {
        int n = bySet.getOrDefault(e.examId(), Map.of()).getOrDefault(taskType, Map.of()).getOrDefault(label, 0);
        if (n > 0) actions.add(Action.toeic(setName(e.title()) + " · " + n + "문항", e.examCode(), taskType, label));
      }
      rows.add(new Row(ToeicFocus.display(label), null, totals.get(label) + "문항", ToeicFocus.area(label), actions, List.of()));
    }
    return new Section("toeic-types-" + taskType.substring(taskType.length() - 1), title, note, null, null, true, rows,
        "유형별 문항은 준비 중입니다.");
  }

  private Section partSection(ToeicFocus.Part p, List<ExamListItem> sets, Map<Long, Map<String, Map<String, Integer>>> bySet) {
    List<Row> rows = new ArrayList<>();
    for (ExamListItem e : sets) {
      int n = partCount(bySet, e.examId(), p.taskType());
      if (n == 0) continue;
      rows.add(new Row(setName(e.title()), typeSummary(bySet.get(e.examId()).get(p.taskType())), n + "문항", null,
          List.of(Action.toeic(p.label() + " · " + n + "문항", e.examCode(), p.taskType(), null)), List.of()));
    }
    String note = "듣기".equals(p.area()) ? "브라우저 음성이 대본을 한 목소리로 읽어 줍니다. 듣기 결과는 참고용입니다." : null;
    return new Section("toeic-p" + p.number(), p.label() + " · " + p.name(), note, null, null, false, rows,
        "준비 중 — 이 파트 문항은 아직 없습니다.");
  }

  private static int partCount(Map<Long, Map<String, Map<String, Integer>>> bySet, Long examId, String taskType) {
    return bySet.getOrDefault(examId, Map.of()).getOrDefault(taskType, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
  }

  /** 유형별 문항 수를 많은 순으로 "세부 사항 19 · 연계 10 …"처럼 적는다. 꼬리표가 없으면 null. */
  static String typeSummary(Map<String, Integer> byLabel) {
    if (byLabel == null) return null;
    String s = byLabel.entrySet().stream().filter(en -> !en.getKey().isEmpty())
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
        .map(en -> ToeicFocus.display(en.getKey()) + " " + en.getValue()).reduce((a, b) -> a + " · " + b).orElse("");
    return s.isEmpty() ? null : s;
  }

  private static final Pattern LAST_PAREN = Pattern.compile("\\(([^()]+)\\)\\s*$");

  /** 세트 제목 끝 괄호의 우리말 이름("… (기출 유사 1회)" → "기출 유사 1회"). 없으면 제목 그대로. */
  static String setName(String title) {
    if (title == null) return "";
    Matcher m = LAST_PAREN.matcher(title);
    return m.find() ? m.group(1) : title;
  }

  // ── 국어·수학·사회·과학: 내신과 같은 디지털 모의고사 틀. 게시된 시험이 있으면 그 과목 탭에 나온다 ──────────

  Tab subject(Subject s, List<ExamListItem> naesinKind) {
    List<Row> rows = naesinKind.stream().filter(e -> e.examCode() != null && e.examCode().startsWith(s.codePrefix()))
        .map(e -> new Row(e.title(), e.subtitle(), join(e.questionCount() + "문항", minutes(e.questionCount() * 30)), null,
            List.of(Action.start("풀기", RUN_NAESIN, e.examCode())), List.of())).toList();
    return new Tab(s.tab(), s.label(), s.label() + " 디지털 모의고사. 내신과 같은 화면에서 풀고 제출하면 바로 채점됩니다.", SUBJECT_ACCESS, true,
        List.of(new Section(s.tab() + "-sets", s.label() + " 디지털 모의고사", null, null, null, false, rows, SUBJECT_EMPTY)));
  }

  // ── 공통 ────────────────────────────────────────────────────────────────────

  /** 게시된 시험 목록(문항 수는 한 번의 질의로 센다). */
  List<ExamListItem> published(String kind) {
    List<TpExam> list = exams.findByKindAndStatusOrderByExamIdAsc(kind, "PUBLISHED");
    if (list.isEmpty()) return List.of();
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] r : questions.countByExamIds(list.stream().map(TpExam::getExamId).toList())) {
      counts.put((Long) r[0], ((Number) r[1]).longValue());
    }
    return list.stream().map(e -> new ExamListItem(e.getExamId(), e.getExamCode(), e.getKind(), e.getTitle(), e.getSubtitle(),
        e.getDescription(), e.getDifficulty(), e.getTimeLimitSec(), counts.getOrDefault(e.getExamId(), 0L))).toList();
  }

  /** '2학년' → '중2'. 내신 실전 연습에 올라온 학교는 모두 중학교다. */
  static String gradeTag(String grade) {
    return "중" + (grade == null ? "" : grade.replace("학년", ""));
  }

  static String minutes(long sec) {
    return Math.round(sec / 60.0) + "분";
  }

  private static String difficulty(String d) {
    return d == null || d.isBlank() ? null : d;
  }

  private static List<Integer> numbers(List<Integer> list) {
    return list.stream().sorted().toList();
  }

  private static List<Integer> range(int count) {
    return IntStream.rangeClosed(1, Math.max(0, count)).boxed().toList();
  }

  /** 0이면 적지 않는다(자료가 아직 없을 때 틀린 숫자를 보이지 않기 위해). */
  static String count(int n, String format) {
    return n <= 0 ? null : format.formatted(n);
  }

  static String join(String... parts) {
    List<String> shown = new ArrayList<>();
    for (String p : parts) if (p != null && !p.isBlank()) shown.add(p);
    return shown.isEmpty() ? null : String.join(" · ", shown);
  }
}
