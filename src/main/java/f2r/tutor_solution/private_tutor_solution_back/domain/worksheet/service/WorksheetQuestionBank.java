package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
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
 *   <li>IBT·SAT·TOEIC — TestPrep 세트(tp_exams kind=PRACTICE·VOCAB / SAT / TOEIC, PUBLISHED)의 종이에 풀 수 있는 객관식·단답
 *       문항만(듣기·말하기·쓰기·문장 만들기는 뺀다). 문항 주소 IBT|SAT|TOEIC:{question_id} (2026-11-23)</li>
 *   <li>CSAT — 내신위키에 올라와 있는 수능·평가원 모의평가 영어 문항(로그인한 원장의 위키 작업 공간). 문항 주소
 *       CSAT:{위키 시험 id}-{문항 번호} (2026-11-23)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class WorksheetQuestionBank {

  public static final String NAESIN = "NAESIN";
  public static final String GRAMMAR = "GRAMMAR";
  public static final String IBT = "IBT";
  public static final String SAT = "SAT";
  public static final String TOEIC = "TOEIC";
  public static final String CSAT = "CSAT";
  /** TestPrep 출처 → tp_exams.kind. */
  static final Map<String, List<String>> TP_KINDS = Map.of(IBT, List.of("PRACTICE", "VOCAB"), SAT, List.of("SAT"),
      TOEIC, List.of("TOEIC"));
  /** 학습지에 넣을 수 있는 TestPrep 문항 유형과 화면 이름(종이에 풀 수 있는 것만). */
  static final Map<String, String> TP_TYPES = Map.ofEntries(Map.entry("READ_DAILY_LIFE", "일상 글 읽기"),
      Map.entry("READ_PASSAGE", "학술 지문 읽기"), Map.entry("COMPLETE_THE_WORDS", "단어 완성"),
      Map.entry("VOCAB_E2S", "어휘 · 뜻 고르기"), Map.entry("VOCAB_S2E", "어휘 · 단어 고르기"), Map.entry("VOCAB_CLOZE", "어휘 · 빈칸"),
      Map.entry("SAT_RW_MC", "읽기·쓰기"), Map.entry("SAT_MATH_MC", "수학 객관식"), Map.entry("SAT_MATH_SPR", "수학 주관식"),
      Map.entry("TOEIC_P5", "Part 5 단문 빈칸"), Map.entry("TOEIC_P6", "Part 6 장문 빈칸"), Map.entry("TOEIC_P7", "Part 7 독해"));
  private static final Map<String, String> CSAT_EXAM_LABEL = Map.of("CSAT", "대학수학능력시험", "JUNE_MOCK", "6월 모의평가",
      "SEPTEMBER_MOCK", "9월 모의평가");
  private static final Pattern CSAT_REF = Pattern.compile("^CSAT:(\\d{1,9})-(\\d{1,3})$");
  private static final Pattern TAG = Pattern.compile("^\\[([^\\]]{1,40})\\]");
  private static final String GRAMMAR_GROUP = "예비고1 어법";
  private static final String GRAMMAR_TYPE = "어법";
  private static final String[] CIRC = {"①", "②", "③", "④", "⑤"};
  private static final Pattern GROUP = Pattern.compile("^(.+?학년)");
  private static final Pattern GRAMMAR_REF = Pattern.compile("^(.+):(\\d+)$");

  private final TpExamRepository exams;
  private final TpQuestionRepository questions;
  private final PreHighGrammarBank grammar;
  private final ObjectMapper json;
  private final WikiCsatGateway wikiCsat;

  public WorksheetQuestionBank(TpExamRepository exams, TpQuestionRepository questions, PreHighGrammarBank grammar,
      ObjectMapper json, WikiCsatGateway wikiCsat) {
    this.exams = exams;
    this.questions = questions;
    this.grammar = grammar;
    this.json = json;
    this.wikiCsat = wikiCsat;
  }

  /** 범위 고르기에 쓰는 묶음 한 줄(내신 = 회차, 어법 = 단원 1·2부·마무리 회차). */
  public record SetInfo(String setKey, String source, String group, String title, int questionCount) {}

  /** 문항 한 개. 목록에는 preview까지만 쓰고, 상세·인쇄에는 stem·body·choices·answer를 쓴다. */
  public record Question(String ref, String source, String setKey, String setTitle, int no, String type,
      String topic, String level, String preview, String stem, String body, List<String> choices, String answer,
      String explanation) {}

  /** 고를 수 있는 묶음 전체(위키 수능 문항 빼고). */
  public List<SetInfo> sets() {
    return sets(null);
  }

  /** 고를 수 있는 묶음 전체. 내신은 학교·학년별, 어법은 한 묶음, TestPrep은 세트별, 수능은 위키 시험별(p가 있을 때). */
  public List<SetInfo> sets(AuthenticatedPrincipal p) {
    List<SetInfo> out = new ArrayList<>();
    for (TpExam e : naesinExams()) {
      out.add(new SetInfo(NAESIN + ":" + e.getExamCode(), NAESIN, groupOf(e.getTitle()), e.getTitle(),
          (int) questions.countByExamId(e.getExamId())));
    }
    for (String key : grammarKeys()) {
      PreHighGrammarBank.Exam exam = grammar.get(key);
      out.add(new SetInfo(GRAMMAR + ":" + key, GRAMMAR, GRAMMAR_GROUP, exam.title(), exam.questions().size()));
    }
    for (String source : List.of(IBT, SAT, TOEIC)) {
      for (TpExam e : tpExams(source)) {
        long n = questions.findByExamIdOrderByOrderNoAsc(e.getExamId()).stream().filter(WorksheetQuestionBank::paperFriendly).count();
        if (n > 0) out.add(new SetInfo(source + ":" + e.getExamCode(), source, tpGroup(source, e), e.getTitle(), (int) n));
      }
    }
    for (WikiCsatGateway.Exam e : p == null ? List.<WikiCsatGateway.Exam>of() : wikiCsat.exams(p)) {
      if (e.questionCount() > 0) {
        out.add(new SetInfo(CSAT + ":" + e.id(), CSAT, e.examYear() + "학년도", csatTitle(e), e.questionCount()));
      }
    }
    return out;
  }

  /**
   * 고른 묶음의 문항을 조건에 맞는 것만 돌려준다. types·levels가 비어 있으면 그 조건은 보지 않는다.
   * 난이도가 없는 문항(어법)은 난이도 조건을 걸면 빠진다.
   */
  public List<Question> search(Collection<String> setKeys, Collection<String> types, Collection<String> levels) {
    return search(null, setKeys, types, levels);
  }

  public List<Question> search(AuthenticatedPrincipal p, Collection<String> setKeys, Collection<String> types,
      Collection<String> levels) {
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
    for (String source : List.of(IBT, SAT, TOEIC)) {
      for (TpExam e : tpExams(source)) {
        if (!wanted.contains(source + ":" + e.getExamCode())) continue;
        for (TpQuestion q : questions.findByExamIdOrderByOrderNoAsc(e.getExamId())) {
          if (paperFriendly(q)) all.add(testprep(source, q, e));
        }
      }
    }
    if (p != null && setKeys.stream().anyMatch(k -> k.startsWith(CSAT + ":"))) {
      for (WikiCsatGateway.Exam e : wikiCsat.exams(p)) {
        if (!wanted.contains(CSAT + ":" + e.id())) continue;
        for (WikiCsatGateway.Item item : wikiCsat.questions(p, e.id())) all.add(csat(e, item));
      }
    }
    return all.stream()
        .filter(q -> types == null || types.isEmpty() || types.contains(q.type()))
        .filter(q -> levels == null || levels.isEmpty() || (q.level() != null && levels.contains(q.level())))
        .toList();
  }

  /** 문항 주소로 문항을 찾는다. 주어진 순서를 지키고, 없어진 문항은 결과에서 빠진다. */
  public List<Question> resolve(List<String> refs) {
    return resolve(null, refs);
  }

  /** 문항 주소로 문항을 찾는다(p가 있으면 위키 수능 문항도). 주어진 순서를 지키고, 없어진 문항은 결과에서 빠진다. */
  public List<Question> resolve(AuthenticatedPrincipal p, List<String> refs) {
    if (refs == null || refs.isEmpty()) return List.of();
    Map<String, List<Long>> idsBySource = new HashMap<>();
    for (String ref : refs) {
      for (String source : List.of(NAESIN, IBT, SAT, TOEIC)) {
        if (ref != null && ref.startsWith(source + ":")) {
          try {
            idsBySource.computeIfAbsent(source, k -> new ArrayList<>()).add(Long.parseLong(ref.substring(source.length() + 1)));
          } catch (NumberFormatException ignored) {
            // 잘못된 주소는 없는 문항으로 본다.
          }
        }
      }
    }
    Map<String, Question> found = new HashMap<>();
    if (idsBySource.containsKey(NAESIN)) {
      Map<Long, TpExam> byId = naesinExams().stream().collect(Collectors.toMap(TpExam::getExamId, e -> e));
      for (TpQuestion q : questions.findAllById(idsBySource.get(NAESIN))) {
        TpExam exam = byId.get(q.getExamId());
        if (exam != null) found.put(NAESIN + ":" + q.getQuestionId(), naesin(q, exam));
      }
    }
    for (String source : List.of(IBT, SAT, TOEIC)) {
      if (!idsBySource.containsKey(source)) continue;
      Map<Long, TpExam> byId = tpExams(source).stream().collect(Collectors.toMap(TpExam::getExamId, e -> e));
      for (TpQuestion q : questions.findAllById(idsBySource.get(source))) {
        TpExam exam = byId.get(q.getExamId());
        if (exam != null && paperFriendly(q)) found.put(source + ":" + q.getQuestionId(), testprep(source, q, exam));
      }
    }
    if (p != null && refs.stream().anyMatch(r -> r != null && r.startsWith(CSAT + ":"))) {
      Map<Long, WikiCsatGateway.Exam> wikiExams = wikiCsat.exams(p).stream()
          .collect(Collectors.toMap(WikiCsatGateway.Exam::id, e -> e, (a, b) -> a));
      Set<Long> needed = new java.util.LinkedHashSet<>();
      for (String ref : refs) {
        Matcher m = ref == null ? null : CSAT_REF.matcher(ref);
        if (m != null && m.matches()) needed.add(Long.parseLong(m.group(1)));
      }
      for (Long examId : needed) {
        WikiCsatGateway.Exam exam = wikiExams.get(examId);
        if (exam == null) continue;
        for (WikiCsatGateway.Item item : wikiCsat.questions(p, examId)) {
          Question q = csat(exam, item);
          found.put(q.ref(), q);
        }
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

  /** 종이 학습지에 넣을 수 있는 TestPrep 문항: 정해 둔 유형이고 정답이 있으며, 듣기 음성(TTS)이 아니다. */
  static boolean paperFriendly(TpQuestion q) {
    return TP_TYPES.containsKey(q.getTaskType()) && q.getAnswerKey() != null && !q.getAnswerKey().isBlank()
        && !"TTS".equals(q.getAudioMode());
  }

  static String tpGroup(String source, TpExam e) {
    if (IBT.equals(source)) return "VOCAB".equals(e.getKind()) ? "iBT 어휘 퀴즈" : "iBT 모의고사";
    if (SAT.equals(source)) return "Digital SAT";
    return e.getExamCode() != null && e.getExamCode().contains("-RC-") ? "TOEIC 독해 연습" : "TOEIC 실전";
  }

  static String csatTitle(WikiCsatGateway.Exam e) {
    String label = e.label() == null || e.label().isBlank() ? CSAT_EXAM_LABEL.getOrDefault(e.examType(), e.examType()) : e.label();
    return e.examYear() + "학년도 " + label + " 영어";
  }

  /** 위키 정답 '3'·'③'·'3번' → '③'. 알 수 없는 모양이면 그대로. */
  static String csatAnswer(String raw) {
    String v = raw == null ? "" : raw.strip();
    for (int i = 0; i < CIRC.length; i++) {
      if (v.equals(CIRC[i]) || v.equals(String.valueOf(i + 1)) || v.equals((i + 1) + "번")) return CIRC[i];
    }
    return v;
  }

  private Question testprep(String source, TpQuestion q, TpExam exam) {
    String explanation = q.getExplanation() == null ? "" : q.getExplanation();
    Matcher tag = TAG.matcher(explanation.strip());
    String prompt = q.getPrompt() == null ? "" : q.getPrompt();
    return new Question(source + ":" + q.getQuestionId(), source, source + ":" + exam.getExamCode(), exam.getTitle(),
        q.getOrderNo(), TP_TYPES.get(q.getTaskType()), tag.find() ? tag.group(1) : TP_TYPES.get(q.getTaskType()), null,
        prompt.length() > 80 ? prompt.substring(0, 80) + "…" : prompt, prompt, q.getBodyText(), choices(q.getChoicesJson()),
        circled(q.getAnswerKey()), explanation);
  }

  private static Question csat(WikiCsatGateway.Exam exam, WikiCsatGateway.Item item) {
    String prompt = item.prompt() == null ? "" : item.prompt();
    return new Question(CSAT + ":" + exam.id() + "-" + item.no(), CSAT, CSAT + ":" + exam.id(), csatTitle(exam), item.no(),
        item.typeName() == null || item.typeName().isBlank() ? "수능 영어" : item.typeName(), item.groupLabel(), null,
        prompt.length() > 80 ? prompt.substring(0, 80) + "…" : prompt, prompt, item.passage(), item.choices(),
        csatAnswer(item.answer()), item.solution());
  }

  private List<TpExam> tpExams(String source) {
    List<TpExam> out = new ArrayList<>();
    for (String kind : TP_KINDS.get(source)) out.addAll(exams.findByKindAndStatusOrderByExamIdAsc(kind, "PUBLISHED"));
    return out;
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
