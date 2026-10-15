package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpAttemptAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpAttemptAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpAttemptRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestPrepService {

  public static final String BAND_DISCLAIMER =
      "TestPrep internal band estimate — not an official ETS TOEFL iBT score.";

  private static final List<String> PRACTICE_SECTION_ORDER = List.of("READING", "LISTENING", "WRITING", "SPEAKING");
  private static final int MAX_TEXT_ANSWER_LEN = 20_000;
  private static final int MAX_RECORDED_ANSWER_LEN = 4_000_000; // base64 data-URL 상한
  private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TpExamRepository examRepository;
  private final TpQuestionRepository questionRepository;
  private final TpAttemptRepository attemptRepository;
  private final TpAttemptAnswerRepository answerRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<ExamListItem> listExams(String kind) {
    List<TpExam> exams = (kind == null || kind.isBlank())
        ? List.of()
        : examRepository.findByKindAndStatusOrderByExamIdAsc(kind, "PUBLISHED");
    return exams.stream()
        .map(e -> new ExamListItem(e.getExamId(), e.getExamCode(), e.getKind(), e.getTitle(),
            e.getSubtitle(), e.getDescription(), e.getDifficulty(), e.getTimeLimitSec(),
            questionRepository.countByExamId(e.getExamId())))
        .toList();
  }

  @Transactional
  public AttemptView start(AuthenticatedPrincipal principal, StartAttemptRequest req) {
    TpExam exam = examRepository.findByExamCodeAndStatus(req.examCode(), "PUBLISHED")
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found"));
    List<TpQuestion> questions;
    String questionIdsJson = null;
    if (req.questionIds() != null && !req.questionIds().isEmpty()) {
      Set<Long> wrong = wrongQuestionIds(principal.userId());
      List<Long> ids = req.questionIds().stream().filter(wrong::contains).distinct().toList();
      questions = questionRepository.findByExamIdAndQuestionIdInOrderByOrderNoAsc(exam.getExamId(), ids);
      if (questions.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no retryable wrong questions in this exam");
      }
      try {
        questionIdsJson = objectMapper.writeValueAsString(questions.stream().map(TpQuestion::getQuestionId).toList());
      } catch (Exception ignored) {}
    } else {
      questions = questionRepository.findByExamIdOrderByOrderNoAsc(exam.getExamId());
      if (questions.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "exam has no questions");
      }
    }

    LocalDateTime now = LocalDateTime.now();
    TpAttempt attempt = new TpAttempt();
    attempt.setUserId(principal.userId());
    attempt.setExamId(exam.getExamId());
    attempt.setStatus("IN_PROGRESS");
    attempt.setQuestionIdsJson(questionIdsJson);
    attempt.setStartedAt(now);
    attempt.setDeadlineAt(now.plusSeconds(exam.getTimeLimitSec()));
    attempt.setOvertime(false);
    attempt = attemptRepository.save(attempt);

    return new AttemptView(attempt.getAttemptId(), exam.getExamCode(), exam.getKind(), exam.getTitle(),
        attempt.getStatus(), ISO.format(attempt.getDeadlineAt()),
        Duration.between(now, attempt.getDeadlineAt()).getSeconds(),
        questions.stream().map(this::toRunQuestion).toList(), Map.of(), BAND_DISCLAIMER);
  }

  @Transactional(readOnly = true)
  public AttemptView resume(AuthenticatedPrincipal principal, Long attemptId) {
    TpAttempt attempt = ownedAttempt(principal, attemptId);
    TpExam exam = examRepository.findById(attempt.getExamId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found"));
    List<TpQuestion> questions = loadAttemptQuestions(attempt);
    Map<Long, String> saved = answerRepository.findByAttemptId(attemptId).stream()
        .filter(a -> a.getAnswerText() != null)
        .collect(Collectors.toMap(TpAttemptAnswer::getQuestionId, TpAttemptAnswer::getAnswerText, (x, y) -> y));
    long remaining = Math.max(0, Duration.between(LocalDateTime.now(), attempt.getDeadlineAt()).getSeconds());
    return new AttemptView(attempt.getAttemptId(), exam.getExamCode(), exam.getKind(), exam.getTitle(),
        attempt.getStatus(), ISO.format(attempt.getDeadlineAt()), remaining,
        questions.stream().map(this::toRunQuestion).toList(), saved, BAND_DISCLAIMER);
  }

  @Transactional
  public void saveAnswer(AuthenticatedPrincipal principal, Long attemptId, SaveAnswerRequest req) {
    TpAttempt attempt = ownedAttempt(principal, attemptId);
    requireInProgress(attempt);
    requireQuestionInAttempt(attempt, req.questionId());
    validateAnswerSize(req.answerText());
    upsertAnswer(attemptId, req.questionId(), req.answerText(), null);
  }

  @Transactional
  public Scorecard submit(AuthenticatedPrincipal principal, Long attemptId, SubmitRequest req) {
    TpAttempt attempt = ownedAttempt(principal, attemptId);
    if ("SUBMITTED".equals(attempt.getStatus())) {
      return toScorecard(attempt, examOf(attempt));
    }
    requireInProgress(attempt);
    LocalDateTime now = LocalDateTime.now();

    if (req != null && req.answers() != null) {
      for (SaveAnswerRequest a : req.answers()) {
        if (a.questionId() == null) continue;
        requireQuestionInAttempt(attempt, a.questionId());
        validateAnswerSize(a.answerText());
        upsertAnswer(attemptId, a.questionId(), a.answerText(), null);
      }
    }

    List<TpQuestion> questions = loadAttemptQuestions(attempt);
    Map<Long, TpAttemptAnswer> answers = answerRepository.findByAttemptId(attemptId).stream()
        .collect(Collectors.toMap(TpAttemptAnswer::getQuestionId, x -> x, (a, b) -> b));

    int autoTotal = 0, autoCorrect = 0;
    Map<String, int[]> bySection = new LinkedHashMap<>(); // [autoTotal, autoCorrect, totalCount]
    for (TpQuestion q : questions) {
      int[] agg = bySection.computeIfAbsent(q.getSection(), k -> new int[3]);
      agg[2]++;
      TpAttemptAnswer ans = answers.get(q.getQuestionId());
      if (Boolean.TRUE.equals(q.getAutoScore())) {
        boolean correct = ans != null && isCorrect(q, ans.getAnswerText());
        if (ans == null) {
          upsertAnswer(attemptId, q.getQuestionId(), null, false);
        } else {
          ans.setIsCorrect(correct);
          ans.setUpdatedAt(now);
          answerRepository.save(ans);
        }
        if (correct) {
          autoTotal++; agg[0]++; agg[1]++; autoCorrect++;
        } else {
          autoTotal++; agg[0]++;
        }
      }
    }

    List<SectionScore> sections = new ArrayList<>();
    for (TpQuestion q : questions) {
      if (!sections.stream().anyMatch(s -> s.section().equals(q.getSection()))) {
        int[] agg = bySection.get(q.getSection());
        Double accuracy = agg[0] > 0 ? round1(100.0 * agg[1] / agg[0]) : null;
        Double band = agg[0] > 0 ? bandFor(accuracy) : null;
        sections.add(new SectionScore(q.getSection(), agg[2], agg[0], agg[0], agg[1], accuracy, band));
      }
    }
    sections.sort(Comparator.comparingInt(s -> sectionRank(s.section())));

    Double accuracy = autoTotal > 0 ? round1(100.0 * autoCorrect / autoTotal) : null;
    Double overall = overallBand(sections);

    attempt.setStatus("SUBMITTED");
    attempt.setSubmittedAt(now);
    attempt.setOvertime(now.isAfter(attempt.getDeadlineAt()));
    attempt.setAutoAccuracy(accuracy == null ? null : BigDecimal.valueOf(accuracy).setScale(2, RoundingMode.HALF_UP));
    attempt.setOverallBand(overall == null ? null : BigDecimal.valueOf(overall).setScale(1, RoundingMode.HALF_UP));
    try {
      attempt.setSectionScoresJson(objectMapper.writeValueAsString(sections));
    } catch (Exception ignored) {}
    attempt = attemptRepository.save(attempt);
    return toScorecard(attempt, examOf(attempt));
  }

  @Transactional(readOnly = true)
  public ReviewView review(AuthenticatedPrincipal principal, Long attemptId) {
    TpAttempt attempt = ownedAttempt(principal, attemptId);
    if (!"SUBMITTED".equals(attempt.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attempt is not submitted yet");
    }
    TpExam exam = examOf(attempt);
    List<TpQuestion> questions = loadAttemptQuestions(attempt);
    Map<Long, TpAttemptAnswer> answers = answerRepository.findByAttemptId(attemptId).stream()
        .collect(Collectors.toMap(TpAttemptAnswer::getQuestionId, x -> x, (a, b) -> b));
    List<ReviewItem> items = questions.stream().map(q -> {
      TpAttemptAnswer ans = answers.get(q.getQuestionId());
      String your = ans == null ? null : displayableAnswer(q, ans.getAnswerText());
      return new ReviewItem(q.getQuestionId(), q.getSection(), q.getTaskType(), q.getPrompt(), q.getBodyText(),
          q.getAudioMode(), parseChoices(q.getChoicesJson()), your,
          Boolean.TRUE.equals(q.getAutoScore()) ? q.getAnswerKey() : null,
          ans == null ? null : ans.getIsCorrect(), Boolean.TRUE.equals(q.getAutoScore()),
          q.getExplanation(), q.getOrderNo());
    }).toList();
    return new ReviewView(attempt.getAttemptId(), exam.getTitle(), exam.getKind(),
        attempt.getSubmittedAt() == null ? null : ISO.format(attempt.getSubmittedAt()),
        Boolean.TRUE.equals(attempt.getOvertime()),
        attempt.getOverallBand() == null ? null : attempt.getOverallBand().doubleValue(),
        attempt.getAutoAccuracy() == null ? null : attempt.getAutoAccuracy().doubleValue(),
        readSectionScores(attempt), items, BAND_DISCLAIMER);
  }

  @Transactional(readOnly = true)
  public List<AttemptSummary> history(AuthenticatedPrincipal principal) {
    return attemptRepository.findByUserIdOrderByStartedAtDesc(principal.userId()).stream().map(a -> {
      TpExam exam = examRepository.findById(a.getExamId()).orElse(null);
      int count = a.getQuestionIdsJson() == null && exam != null
          ? (int) questionRepository.countByExamId(a.getExamId())
          : loadAttemptQuestionIds(a).size();
      return new AttemptSummary(a.getAttemptId(),
          exam == null ? null : exam.getExamCode(),
          exam == null ? null : exam.getKind(),
          exam == null ? "(deleted exam)" : exam.getTitle(),
          a.getStatus(), ISO.format(a.getStartedAt()),
          a.getSubmittedAt() == null ? null : ISO.format(a.getSubmittedAt()),
          a.getOverallBand() == null ? null : a.getOverallBand().doubleValue(),
          a.getAutoAccuracy() == null ? null : a.getAutoAccuracy().doubleValue(),
          count);
    }).toList();
  }

  @Transactional(readOnly = true)
  public LearningView learning(AuthenticatedPrincipal principal) {
    List<TpAttempt> attempts = attemptRepository.findByUserIdOrderByStartedAtDesc(principal.userId());
    long submitted = attempts.stream().filter(a -> "SUBMITTED".equals(a.getStatus())).count();
    long practice = attempts.stream().filter(a -> isKind(a, "PRACTICE")).count();
    long vocab = attempts.stream().filter(a -> isKind(a, "VOCAB")).count();
    Double best = attempts.stream()
        .filter(a -> "SUBMITTED".equals(a.getStatus()) && a.getOverallBand() != null)
        .map(a -> a.getOverallBand().doubleValue())
        .max(Double::compare).orElse(null);

    List<SectionScore> latestSections = new ArrayList<>();
    Map<String, LocalDateTime> seenAt = new HashMap<>();
    for (TpAttempt a : attempts) {
      if (!"SUBMITTED".equals(a.getStatus()) || !isKind(a, "PRACTICE")) continue;
      for (SectionScore s : readSectionScores(a)) {
        if (s.band() == null) continue;
        LocalDateTime at = a.getSubmittedAt() == null ? a.getStartedAt() : a.getSubmittedAt();
        if (!seenAt.containsKey(s.section()) || seenAt.get(s.section()).isBefore(at)) {
          seenAt.put(s.section(), at);
          boolean replaced = false;
          for (int i = 0; i < latestSections.size(); i++) {
            if (latestSections.get(i).section().equals(s.section())) { latestSections.set(i, s); replaced = true; break; }
          }
          if (!replaced) latestSections.add(s);
        }
      }
    }
    latestSections.sort(Comparator.comparingInt(s -> sectionRank(s.section())));

    List<TpAttemptAnswer> wrongs = answerRepository.findWrongAnswers(principal.userId());
    Map<Long, TpAttemptAnswer> latestWrongByQuestion = new LinkedHashMap<>();
    for (TpAttemptAnswer w : wrongs) { // wrongs는 answeredAt 내림차순
      latestWrongByQuestion.putIfAbsent(w.getQuestionId(), w);
    }
    List<WrongItem> wrongItems = new ArrayList<>();
    for (TpAttemptAnswer w : latestWrongByQuestion.values()) {
      TpQuestion q = questionRepository.findById(w.getQuestionId()).orElse(null);
      if (q == null) continue;
      TpExam exam = examRepository.findById(q.getExamId()).orElse(null);
      if (exam == null) continue;
      String prompt = q.getPrompt() == null ? "" : q.getPrompt();
      wrongItems.add(new WrongItem(q.getQuestionId(), exam.getExamId(), exam.getExamCode(), exam.getTitle(),
          exam.getKind(), q.getSection(), q.getTaskType(),
          prompt.length() > 90 ? prompt.substring(0, 90) + "…" : prompt, ISO.format(w.getAnsweredAt())));
    }
    wrongItems.sort(Comparator.comparing(WrongItem::lastWrongAt, Comparator.nullsLast(Comparator.reverseOrder())));

    return new LearningView(attempts.size(), submitted, practice, vocab, best, latestSections,
        wrongItems.size(), wrongItems, BAND_DISCLAIMER);
  }

  // ===== 내부 =====

  private boolean isKind(TpAttempt a, String kind) {
    return examRepository.findById(a.getExamId()).map(e -> kind.equals(e.getKind())).orElse(false);
  }

  private TpAttempt ownedAttempt(AuthenticatedPrincipal principal, Long attemptId) {
    TpAttempt attempt = attemptRepository.findById(attemptId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "attempt not found"));
    boolean owner = attempt.getUserId().equals(principal.userId());
    boolean admin = "ADMIN".equals(principal.role());
    if (!owner && !admin) {
      // 타인의 attempt는 존재 자체를 노출하지 않는다.
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "attempt not found");
    }
    return attempt;
  }

  private void requireInProgress(TpAttempt attempt) {
    if (!"IN_PROGRESS".equals(attempt.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attempt already finished");
    }
  }

  private TpExam examOf(TpAttempt attempt) {
    return examRepository.findById(attempt.getExamId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found"));
  }

  private List<Long> loadAttemptQuestionIds(TpAttempt attempt) {
    if (attempt.getQuestionIdsJson() == null) {
      return questionRepository.findByExamIdOrderByOrderNoAsc(attempt.getExamId())
          .stream().map(TpQuestion::getQuestionId).toList();
    }
    try {
      return objectMapper.readValue(attempt.getQuestionIdsJson(), new TypeReference<List<Long>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private List<TpQuestion> loadAttemptQuestions(TpAttempt attempt) {
    List<TpQuestion> all = questionRepository.findByExamIdOrderByOrderNoAsc(attempt.getExamId());
    if (attempt.getQuestionIdsJson() == null) return all;
    Set<Long> ids = new HashSet<>(loadAttemptQuestionIds(attempt));
    return all.stream().filter(q -> ids.contains(q.getQuestionId())).toList();
  }

  private void requireQuestionInAttempt(TpAttempt attempt, Long questionId) {
    if (questionId == null || !loadAttemptQuestionIds(attempt).contains(questionId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is not part of this attempt");
    }
  }

  private void validateAnswerSize(String answer) {
    if (answer != null && answer.length() > MAX_RECORDED_ANSWER_LEN) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "answer too large");
    }
    if (answer != null && !answer.startsWith("data:audio/") && answer.length() > MAX_TEXT_ANSWER_LEN) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text answer too long");
    }
  }

  private void upsertAnswer(Long attemptId, Long questionId, String answerText, Boolean isCorrect) {
    LocalDateTime now = LocalDateTime.now();
    TpAttemptAnswer ans = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)
        .orElseGet(() -> {
          TpAttemptAnswer n = new TpAttemptAnswer();
          n.setAttemptId(attemptId);
          n.setQuestionId(questionId);
          n.setAnsweredAt(now);
          return n;
        });
    ans.setAnswerText(answerText);
    if (isCorrect != null || ans.getIsCorrect() == null) ans.setIsCorrect(isCorrect);
    ans.setUpdatedAt(now);
    answerRepository.save(ans);
  }

  private boolean isCorrect(TpQuestion q, String answer) {
    if (answer == null || answer.isBlank() || q.getAnswerKey() == null) return false;
    if ("BUILD_SENTENCE".equals(q.getTaskType())) {
      return normalizeWords(answer).equals(normalizeWords(q.getAnswerKey()));
    }
    return answer.trim().equalsIgnoreCase(q.getAnswerKey().trim());
  }

  private String normalizeWords(String s) {
    return Arrays.stream(s.trim().toLowerCase().split("\\s+")).collect(Collectors.joining(" "));
  }

  private String displayableAnswer(TpQuestion q, String raw) {
    if (raw == null) return null;
    if (raw.startsWith("data:audio/")) return "[녹음 응답 저장됨 — 길이 " + (raw.length() / 1024) + "KB]";
    return raw;
  }

  private RunQuestion toRunQuestion(TpQuestion q) {
    return new RunQuestion(q.getQuestionId(), q.getSection(), q.getTaskType(), q.getPrompt(), q.getBodyText(),
        q.getAudioMode(), parseChoices(q.getChoicesJson()), q.getOrderNo(), Boolean.TRUE.equals(q.getAutoScore()));
  }

  private List<String> parseChoices(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private List<SectionScore> readSectionScores(TpAttempt attempt) {
    if (attempt.getSectionScoresJson() == null) return List.of();
    try {
      return objectMapper.readValue(attempt.getSectionScoresJson(), new TypeReference<List<SectionScore>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private Scorecard toScorecard(TpAttempt attempt, TpExam exam) {
    return new Scorecard(attempt.getAttemptId(), exam.getTitle(),
        attempt.getSubmittedAt() == null ? null : ISO.format(attempt.getSubmittedAt()),
        Boolean.TRUE.equals(attempt.getOvertime()),
        attempt.getOverallBand() == null ? null : attempt.getOverallBand().doubleValue(),
        attempt.getAutoAccuracy() == null ? null : attempt.getAutoAccuracy().doubleValue(),
        readSectionScores(attempt), BAND_DISCLAIMER);
  }

  private Set<Long> wrongQuestionIds(Long userId) {
    return answerRepository.findWrongAnswers(userId).stream()
        .map(TpAttemptAnswer::getQuestionId).collect(Collectors.toSet());
  }

  static Double bandFor(double accuracyPercent) {
    if (accuracyPercent >= 95) return 6.0;
    if (accuracyPercent >= 88) return 5.5;
    if (accuracyPercent >= 80) return 5.0;
    if (accuracyPercent >= 70) return 4.5;
    if (accuracyPercent >= 60) return 4.0;
    if (accuracyPercent >= 50) return 3.5;
    if (accuracyPercent >= 40) return 3.0;
    if (accuracyPercent >= 30) return 2.5;
    if (accuracyPercent >= 20) return 2.0;
    return 1.5;
  }

  static Double overallBand(List<SectionScore> sections) {
    List<Double> bands = sections.stream().map(SectionScore::band).filter(Objects::nonNull).toList();
    if (bands.isEmpty()) return null;
    double avg = bands.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    return Math.round(avg * 2.0) / 2.0; // ETS와 동일하게 0.5 단위 반올림
  }

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }

  private static int sectionRank(String section) {
    int i = PRACTICE_SECTION_ORDER.indexOf(section);
    return i < 0 ? 99 : i;
  }
}
