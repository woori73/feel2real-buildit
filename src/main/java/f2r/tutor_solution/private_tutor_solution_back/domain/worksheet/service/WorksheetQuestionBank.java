package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습지에 넣을 수 있는 문항 은행. 새 문항을 만들지 않고 F2R에 이미 있는 문항만 한 모양으로 읽어 준다.
 * <ul>
 *   <li>NAESIN — 내신 예상문제(tp_exams kind=NAESIN·PUBLISHED의 tp_questions). 문항 주소 NAESIN:{question_id}</li>
 *   <li>GRAMMAR — 예비고1 어법(PreHighGrammarBank의 단원 1·2부, 마무리 종합). 문항 주소 GRAMMAR:{시험 키}:{번호}</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class WorksheetQuestionBank {

  public static final String NAESIN = "NAESIN";
  public static final String GRAMMAR = "GRAMMAR";
  private static final String GRAMMAR_GROUP = "예비고1 어법";
  private static final String GRAMMAR_TYPE = "어법";
  private static final String[] CIRC = {"①", "②", "③", "④", "⑤"};
  private static final Pattern GROUP = Pattern.compile("^(.+?학년)");
  private static final Pattern GRAMMAR_REF = Pattern.compile("^(.+):(\\d+)$");

  private final TpExamRepository exams;
  private final TpQuestionRepository questions;
  private final PreHighGrammarBank grammar;
  private final ObjectMapper json;

  public WorksheetQuestionBank(TpExamRepository exams, TpQuestionRepository questions, PreHighGrammarBank grammar,
      ObjectMapper json) {
    this.exams = exams;
    this.questions = questions;
    this.grammar = grammar;
    this.json = json;
  }

  /** 범위 고르기에 쓰는 묶음 한 줄(내신 = 회차, 어법 = 단원 1·2부·마무리 회차). */
  public record SetInfo(String setKey, String source, String group, String title, int questionCount) {}

  /** 문항 한 개. 목록에는 preview까지만 쓰고, 상세·인쇄에는 stem·body·choices·answer를 쓴다. */
  public record Question(String ref, String source, String setKey, String setTitle, int no, String type,
      String topic, String level, String preview, String stem, String body, List<String> choices, String answer,
      String explanation) {}

  /** 고를 수 있는 묶음 전체. 내신은 학교·학년별, 어법은 한 묶음. */
  public List<SetInfo> sets() {
    List<SetInfo> out = new ArrayList<>();
    for (TpExam e : naesinExams()) {
      out.add(new SetInfo(NAESIN + ":" + e.getExamCode(), NAESIN, groupOf(e.getTitle()), e.getTitle(),
          (int) questions.countByExamId(e.getExamId())));
    }
    for (String key : grammarKeys()) {
      PreHighGrammarBank.Exam exam = grammar.get(key);
      out.add(new SetInfo(GRAMMAR + ":" + key, GRAMMAR, GRAMMAR_GROUP, exam.title(), exam.questions().size()));
    }
    return out;
  }

  /**
   * 고른 묶음의 문항을 조건에 맞는 것만 돌려준다. types·levels가 비어 있으면 그 조건은 보지 않는다.
   * 난이도가 없는 문항(어법)은 난이도 조건을 걸면 빠진다.
   */
  public List<Question> search(Collection<String> setKeys, Collection<String> types, Collection<String> levels) {
    if (setKeys == null || setKeys.isEmpty()) return List.of();
    Set<String> wanted = Set.copyOf(setKeys);
    List<Question> all = new ArrayList<>();
    List<TpExam> picked = naesinExams().stream().filter(e -> wanted.contains(NAESIN + ":" + e.getExamCode())).toList();
    if (!picked.isEmpty()) {
      Map<Long, TpExam> byId = picked.stream().collect(Collectors.toMap(TpExam::getExamId, e -> e));
      for (TpQuestion q : questions.findByExamIdInOrderByExamIdAscOrderNoAsc(byId.keySet())) {
        all.add(naesin(q, byId.get(q.getExamId())));
      }
    }
    for (String key : grammarKeys()) {
      if (!wanted.contains(GRAMMAR + ":" + key)) continue;
      PreHighGrammarBank.Exam exam = grammar.get(key);
      for (int i = 0; i < exam.questions().size(); i++) all.add(grammar(key, exam, i + 1));
    }
    return all.stream()
        .filter(q -> types == null || types.isEmpty() || types.contains(q.type()))
        .filter(q -> levels == null || levels.isEmpty() || (q.level() != null && levels.contains(q.level())))
        .toList();
  }

  /** 문항 주소로 문항을 찾는다. 주어진 순서를 지키고, 없어진 문항은 결과에서 빠진다. */
  public List<Question> resolve(List<String> refs) {
    if (refs == null || refs.isEmpty()) return List.of();
    List<Long> ids = new ArrayList<>();
    for (String ref : refs) {
      if (ref != null && ref.startsWith(NAESIN + ":")) {
        try {
          ids.add(Long.parseLong(ref.substring(NAESIN.length() + 1)));
        } catch (NumberFormatException ignored) {
          // 잘못된 주소는 없는 문항으로 본다.
        }
      }
    }
    Map<String, Question> found = new HashMap<>();
    if (!ids.isEmpty()) {
      Map<Long, TpExam> byId = naesinExams().stream().collect(Collectors.toMap(TpExam::getExamId, e -> e));
      for (TpQuestion q : questions.findAllById(ids)) {
        TpExam exam = byId.get(q.getExamId());
        if (exam != null) found.put(NAESIN + ":" + q.getQuestionId(), naesin(q, exam));
      }
    }
    List<Question> out = new ArrayList<>();
    for (String ref : refs) {
      Question q = ref != null && ref.startsWith(GRAMMAR + ":") ? grammarByRef(ref) : found.get(ref);
      if (q != null) out.add(q);
    }
    return out;
  }

  /** 문항 유형 이름과 문항 수(묶음을 고른 뒤 유형 단추에 쓴다). */
  public static Map<String, Long> countByType(List<Question> list) {
    return list.stream().collect(Collectors.groupingBy(Question::type, LinkedHashMap::new, Collectors.counting()));
  }

  static String groupOf(String title) {
    Matcher m = GROUP.matcher(title == null ? "" : title);
    return m.find() ? m.group(1) : "내신 예상문제";
  }

  /** 정답 글자(A, 'A,C')를 동그라미 번호로 바꾼다. */
  static String circled(String letters) {
    if (letters == null || letters.isBlank()) return "";
    StringBuilder sb = new StringBuilder();
    for (String l : letters.split(",")) {
      String one = l.strip();
      int k = one.length() == 1 ? "ABCDE".indexOf(one) : -1;
      if (sb.length() > 0) sb.append(", ");
      sb.append(k >= 0 ? CIRC[k] : one);
    }
    return sb.toString();
  }

  private List<TpExam> naesinExams() {
    return exams.findByKindAndStatusOrderByExamIdAsc(TestPrepService.KIND_NAESIN, "PUBLISHED");
  }

  private List<String> grammarKeys() {
    List<String> keys = new ArrayList<>();
    for (PreHighGrammarBank.ChapterInfo c : grammar.list()) {
      keys.add(c.number() + "-1");
      keys.add(c.number() + "-2");
    }
    for (Integer round : grammar.finalRounds()) keys.add("final-" + round);
    return keys;
  }

  private Question naesin(TpQuestion q, TpExam exam) {
    String stem = NaesinQuestionType.stem(q.getExplanation());
    return new Question(NAESIN + ":" + q.getQuestionId(), NAESIN, NAESIN + ":" + exam.getExamCode(), exam.getTitle(),
        q.getOrderNo(), NaesinQuestionType.classify(stem), stem, NaesinQuestionType.level(q.getPoints()),
        stem.isEmpty() ? "그림 문항" : stem, q.getPrompt(), q.getBodyText(), choices(q.getChoicesJson()),
        circled(q.getAnswerKey()), q.getExplanation());
  }

  private Question grammarByRef(String ref) {
    Matcher m = GRAMMAR_REF.matcher(ref.substring(GRAMMAR.length() + 1));
    if (!m.matches()) return null;
    String key = m.group(1);
    if (!grammarKeys().contains(key)) return null;
    PreHighGrammarBank.Exam exam = grammar.get(key);
    int no = Integer.parseInt(m.group(2));
    return no < 1 || no > exam.questions().size() ? null : grammar(key, exam, no);
  }

  private Question grammar(String key, PreHighGrammarBank.Exam exam, int no) {
    PreHighGrammarBank.Question q = exam.questions().get(no - 1);
    return new Question(GRAMMAR + ":" + key + ":" + no, GRAMMAR, GRAMMAR + ":" + key, exam.title(), no, GRAMMAR_TYPE,
        q.topic(), null, q.text(), q.instruction(), q.text(), q.choices(), CIRC[q.answer() - 1], q.principle());
  }

  private List<String> choices(String choicesJson) {
    if (choicesJson == null || choicesJson.isBlank()) return List.of();
    try {
      return json.readValue(choicesJson, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }
}
