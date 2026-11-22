package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.NaesinTestPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart6Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 콘텐츠 모음(/learn)에 보여 줄 카드 목록. 새 기능·새 표 없이 이미 있는 화면으로 가는 링크만 모은다.
 * 분량(회차·세트 수)은 글로 적어 두지 않고 지금 올라와 있는 자료에서 센다.
 */
@Service
@Transactional(readOnly = true)
public class LearnHubCatalog {

  public static final String OPEN = "누구나";
  public static final String LIST_OPEN = "목록은 누구나 · 풀이는 로그인";
  public static final String INTRO_OPEN = "첫 화면은 누구나 · 자료는 로그인";
  public static final String LOGIN = "로그인 필요";
  public static final String CODE = "접속 코드 필요";

  private final TpExamRepository exams;
  private final PreHighEnglishStore preHigh;
  private final PreHighPart2Store part2;
  private final PreHighPart6Store part6;
  private final PreHighGrammarBank grammar;

  public LearnHubCatalog(TpExamRepository exams, PreHighEnglishStore preHigh, PreHighPart2Store part2,
      PreHighPart6Store part6, PreHighGrammarBank grammar) {
    this.exams = exams;
    this.preHigh = preHigh;
    this.part2 = part2;
    this.part6 = part6;
    this.grammar = grammar;
  }

  /** 카드 한 장. audiences = 학년 거르기 값, school = 학교 거르기 값(없으면 null), access = 로그인 안내 문장. */
  public record Card(String title, String href, String description, List<String> audiences, String school,
      String access, String volume) {
    public boolean loginNeeded() {
      return !OPEN.equals(access);
    }

    public String audienceAttr() {
      return String.join(",", audiences);
    }
  }

  public record Group(String key, String title, String lead, List<Card> cards) {}

  public static final List<String> AUDIENCES = List.of("중1", "중2", "예비고1", "고2", "고3·수능", "iBT·SAT·토익");

  public List<Group> groups() {
    return List.of(naesin(), csat(), preHigh(), testPrep(), admissions());
  }

  /** 학교 거르기에 쓰는 학교 이름(내신 실전 연습에 올라온 학교). */
  public List<String> schools() {
    return NaesinTestPageController.SCHOOLS.stream().map(NaesinTestPageController.School::name).distinct().toList();
  }

  private Group naesin() {
    List<TpExam> all = published(TestPrepService.KIND_NAESIN);
    List<Card> cards = new ArrayList<>();
    cards.add(new Card("내신 실전 연습 · 학교 고르기", "/naesintest", "학교별 디지털 영어 기말고사 예상문제. 학교를 고르면 회차 목록이 나옵니다.",
        List.of("중1", "중2"), null, LIST_OPEN, count(all.size(), "회차")));
    for (NaesinTestPageController.School s : NaesinTestPageController.SCHOOLS) {
      long mine = all.stream().filter(e -> e.getExamCode() != null && e.getExamCode().startsWith(s.codePrefix())).count();
      cards.add(new Card(s.title() + " 예상문제", "/naesintest/school/" + s.slug(), "시험 범위 " + s.scope() + ". 1회는 로그인한 회원 누구나, 2회부터는 재원생.",
          List.of(gradeTag(s.grade())), s.name(), LIST_OPEN, count((int) mine, "회차")));
    }
    cards.add(new Card("재원생 앱 · 디지털 모의고사", "/naesintest/app", "원장님이 발급한 접속 코드로 들어와 배정된 시험을 풉니다.",
        List.of("중1", "중2"), null, CODE, null));
    cards.add(new Card("내신 문제 생성기", "/problem-generator", "학교 자료로 내신 문제를 만들고 오답을 모읍니다. 소개 화면은 누구나 봅니다.",
        List.of("중1", "중2"), null, INTRO_OPEN, null));
    return new Group("naesin", "내신 대비", "학교 시험 범위에 맞춘 예상문제와 내신 자료", cards);
  }

  private Group csat() {
    return new Group("csat", "수능·모의고사", "수능 해설과 모의고사 자료", List.of(
        new Card("2027 수능 영어 해설", "/csat/2027-english", "홀수형 문항을 문제 → 근거 → 정답 → 해설 순서로 보는 모션 해설. 필기본 PDF는 로그인 후 받습니다.",
            List.of("고3·수능"), null, OPEN, "45문항"),
        new Card("모의고사 위키", "/wiki", "고1·고2·고3 영어 모의고사 자료. 첫 화면은 누구나, 자료 열람은 로그인.",
            List.of("예비고1", "고2", "고3·수능"), null, INTRO_OPEN, null)));
  }

  private Group preHigh() {
    int chapters = grammar.list().size();
    String grade1 = join(count(preHigh.list().size(), "1부 기출 %d회"), count(part2.list().size(), "2부 실전 독해 %d회"),
        "3부 어법 " + chapters + "단원 · 마무리 " + grammar.finalRounds().size() + "회",
        "4부 어휘 " + PreHighVocabularyBank.ROUND_COUNT + "회");
    String grade2 = join(count(preHigh.listGrade2().size(), "5부 기출 %d회"), count(part6.list().size(), "6부 실전 독해 %d회"),
        "7부 어휘 " + PreHighVocabularyBank.GRADE_TWO_ROUND_COUNT + "회");
    return new Group("prehigh", "예비고1", "고등학교 영어를 미리 익히는 디지털 모의고사", List.of(
        new Card("예비고1 디지털 영어 모의고사 (1~4부)", "/naesintest/pre-high", "고1 기출 독해·어휘, 실전 독해, 내신 대비 어법, 수능 대비 어휘.",
            List.of("예비고1"), null, LIST_OPEN, grade1),
        new Card("고2 자료로 한 단계 더 (5~7부)", "/naesintest/pre-high", "고2 기출 독해·어휘, 고2 실전 독해, 고2 어휘. 같은 화면 아래쪽에 있습니다.",
            List.of("고2"), null, LIST_OPEN, grade2)));
  }

  private Group testPrep() {
    List<String> tag = List.of("iBT·SAT·토익");
    return new Group("testprep", "iBT·SAT·토익", "TestPrep 연습 세트", List.of(
        new Card("TestPrep 홈", "/testprep", "TOEFL iBT·디지털 SAT·토익 연습 안내.", tag, null, OPEN, null),
        new Card("iBT 모의고사", "/testprep/practice", "새 형식 TOEFL iBT 연습 세트.", tag, null, LOGIN,
            count(published("PRACTICE").size(), "세트")),
        new Card("디지털 SAT", "/testprep/sat", "디지털 SAT 연습 세트.", tag, null, LOGIN,
            count(published(TestPrepService.KIND_SAT).size(), "세트")),
        new Card("토익", "/testprep/toeic", "토익 연습 세트.", tag, null, LOGIN,
            count(published(TestPrepService.KIND_TOEIC).size(), "세트")),
        new Card("어휘 퀴즈", "/testprep/vocab", "난이도별 어휘 퀴즈.", tag, null, LOGIN, count(published("VOCAB").size(), "세트")),
        new Card("영역별 가이드", "/testprep/guide", "읽기·듣기·말하기·쓰기 영역별 안내.", tag, null, LOGIN, null)));
  }

  private Group admissions() {
    return new Group("admissions", "입시 정보", "바뀌는 입시 제도 해설과 입시상담", List.of(
        new Card("「바뀌는 입시」 글", "/admissions/articles", "2028학년도부터 바뀌는 입시를 풀어 쓴 글.",
            List.of("예비고1", "고2", "고3·수능"), null, OPEN, null),
        new Card("입시상담 AI", "/admissions/guide", "대학·전형 자료를 근거로 답하는 입시상담 안내.",
            List.of("예비고1", "고2", "고3·수능"), null, OPEN, null)));
  }

  private List<TpExam> published(String kind) {
    return exams.findByKindAndStatusOrderByExamIdAsc(kind, "PUBLISHED");
  }

  /** '2학년' → '중2'. 내신 실전 연습에 올라온 학교는 모두 중학교다. */
  static String gradeTag(String grade) {
    return "중" + (grade == null ? "" : grade.replace("학년", ""));
  }

  /** 0이면 분량을 적지 않는다(자료가 아직 없을 때 틀린 숫자를 보이지 않기 위해). */
  static String count(int n, String unitOrFormat) {
    if (n <= 0) return null;
    return unitOrFormat.contains("%d") ? unitOrFormat.formatted(n) : n + unitOrFormat;
  }

  static String join(String... parts) {
    List<String> shown = new ArrayList<>();
    for (String p : parts) if (p != null && !p.isBlank()) shown.add(p);
    return shown.isEmpty() ? null : String.join(" · ", shown);
  }
}
