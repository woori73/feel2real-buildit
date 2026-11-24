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
  public static final String SAT_DISCLAIMER =
      "F2R internal score estimate — not an official College Board SAT score.";
  public static final String KIND_SAT = "SAT";
  public static final String KIND_NAESIN = "NAESIN";
  public static final String KIND_TOEIC = "TOEIC";
  public static final String TOEIC_DISCLAIMER =
      "F2R internal score estimate — not an official ETS TOEIC score.";
  public static final String NAESIN_DISCLAIMER =
      "F2R 예상문제 연습 점수입니다. 실제 학교 시험 점수가 아닙니다.";
  public static final String FIRST_SET_ONLY_MESSAGE =
      "첫 세트는 로그인한 회원 누구나 풀 수 있습니다. 다른 세트는 압구정에듀홀릭 재원생과 관리자만 풀 수 있습니다.";
  /** 일반 회원에게 첫 세트만 여는 종류. 내신은 NaesinAccessService 규칙(1회·타과목)을 따른다. */
  public static final List<String> FIRST_SET_KINDS = List.of("PRACTICE", "VOCAB", KIND_SAT, KIND_TOEIC);
  public static final String TASK_SAT_SPR = "SAT_MATH_SPR";
  /** 내신 "~을 모두 고르면?" 문항. answerKey는 정답 기호를 쉼표로 잇는다(예: "B,D"). */
  public static final String TASK_NAESIN_MULTI = "NAESIN_MC5_MULTI";

  private static final List<String> PRACTICE_SECTION_ORDER =
      List.of("READING", "LISTENING", "WRITING", "SPEAKING", "RW", "MATH", "EN");
  private static final List<String> TOEIC_SECTION_ORDER = List.of("LISTENING", "READING");
  private static final int MAX_TEXT_ANSWER_LEN = 20_000;
  private static final int MAX_RECORDED_ANSWER_LEN = 4_000_000; // base64 data-URL 상한
  private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TpExamRepository examRepository;
  private final TpQuestionRepository questionRepository;
  private final TpAttemptRepository attemptRepository;
  private final TpAttemptAnswerRepository answerRepository;
  private final ObjectMapper objectMapper;
  private final NaesinAccessService naesinAccessService;

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
    if (KIND_NAESIN.equals(exam.getKind())) naesinAccessService.requireCanTake(principal, exam.getExamCode(), exam.getTitle());
    else requireFirstSetOrFullAccess(principal, exam);
    List<TpQuestion> questions;
    String questionIdsJson = null;
    int timeLimitSec = exam.getTimeLimitSec();
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
    } else if (req.hasFocus()) {
      // TOEIC 파트·유형만 골라 풀기. 부분 응시로 기록되어 추정 점수 없이 정답 수만 남는다.
      if (!KIND_TOEIC.equals(exam.getKind())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "part selection is only for TOEIC sets");
      }
      List<TpQuestion> all = questionRepository.findByExamIdOrderByOrderNoAsc(exam.getExamId());
      questions = all.stream().filter(q -> matchesFocus(q, req.taskTypes(), req.focusTag())
          && ToeicFocus.inRange(q.getOrderNo(), req.fromNo(), req.toNo())).toList();
      if (questions.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no questions for this part in this exam");
      }
      timeLimitSec = toeicPartialTimeLimitSec(questions, exam.getTimeLimitSec(), all.size());
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
    attempt.setDeadlineAt(now.plusSeconds(timeLimitSec));
    attempt.setOvertime(false);
    attempt = attemptRepository.save(attempt);

    return new AttemptView(attempt.getAttemptId(), exam.getExamCode(), exam.getKind(), exam.getTitle(),
        attempt.getStatus(), ISO.format(attempt.getDeadlineAt()),
        Duration.between(now, attempt.getDeadlineAt()).getSeconds(),
        questions.stream().map(this::toRunQuestion).toList(), Map.of(), disclaimerFor(exam.getKind()));
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
        questions.stream().map(this::toRunQuestion).toList(), saved, disclaimerFor(exam.getKind()));
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
    Map<String, int[]> bySection = new LinkedHashMap<>(); // [autoTotal, autoCorrect, totalCount, earnedPoints, maxPoints]
    for (TpQuestion q : questions) {
      int[] agg = bySection.computeIfAbsent(q.getSection(), k -> new int[5]);
      agg[2]++;
      int pts = q.getPoints() == null ? 0 : q.getPoints();
      agg[4] += pts;
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
          autoTotal++; agg[0]++; agg[1]++; autoCorrect++; agg[3] += pts;
        } else {
          autoTotal++; agg[0]++;
        }
      }
    }

    String kind = examOf(attempt).getKind();
    boolean sat = KIND_SAT.equals(kind);
    boolean naesin = KIND_NAESIN.equals(kind);
    boolean toeic = KIND_TOEIC.equals(kind);
    List<SectionScore> sections = new ArrayList<>();
    for (TpQuestion q : questions) {
      if (!sections.stream().anyMatch(s -> s.section().equals(q.getSection()))) {
        int[] agg = bySection.get(q.getSection());
        Double accuracy = agg[0] > 0 ? round1(100.0 * agg[1] / agg[0]) : null;
        // SAT은 TOEFL 밴드(1~6)를 쓰지 않고 영역 추정 점수(200~800)만 기록한다.
        // 내신 실전 연습은 맞힌 문항의 배점 합(만점 = 배점 합계)으로 채점한다.
        // TOEIC도 밴드를 쓰지 않고 영역 추정 점수(5~495)만 기록한다.
        Double band = agg[0] > 0 && !sat && !naesin && !toeic ? bandFor(accuracy) : null;
        // 삼항식에 int와 null을 섞으면 언박싱 NPE가 나므로 Integer로 나눠 정한다.
        Integer scaled = null;
        Integer scaleMax = null;
        // 오답만 다시 풀기(부분 응시)는 몇 문항만 푼 것이라 SAT·TOEIC 추정 점수를 내지 않고 정답 수만 남긴다.
        boolean partial = attempt.getQuestionIdsJson() != null;
        if (sat) {
          scaled = satSectionScaled(partial, agg[1], agg[0]);
          scaleMax = partial ? null : 800;
        } else if (toeic) {
          scaled = toeicSectionScaled(partial, agg[1], agg[0]);
          scaleMax = partial ? null : 495;
        } else if (naesin) {
          scaled = agg[3];
          scaleMax = agg[4];
        }
        sections.add(new SectionScore(q.getSection(), agg[2], agg[0], agg[0], agg[1], accuracy, band, scaled, scaleMax));
      }
    }
    sections.sort(Comparator.comparingInt(s -> sectionRank(kind, s.section())));

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
          q.getExplanation(), q.getOrderNo(), q.getKoHelpJson(), q.getPoints());
    }).toList();
    return new ReviewView(attempt.getAttemptId(), exam.getTitle(), exam.getKind(),
        attempt.getSubmittedAt() == null ? null : ISO.format(attempt.getSubmittedAt()),
        Boolean.TRUE.equals(attempt.getOvertime()),
        attempt.getOverallBand() == null ? null : attempt.getOverallBand().doubleValue(),
        attempt.getAutoAccuracy() == null ? null : attempt.getAutoAccuracy().doubleValue(),
        readSectionScores(attempt), items, disclaimerFor(exam.getKind()), totalScaled(readSectionScores(attempt)),
        totalMax(readSectionScores(attempt)));
  }

  @Transactional(readOnly = true)
  public List<AttemptSummary> history(AuthenticatedPrincipal principal) {
    return attemptRepository.findByUserIdOrderByStartedAtDesc(principal.userId()).stream().map(a -> {
      TpExam exam = examRepository.findById(a.getExamId()).orElse(null);
      int count = a.getQuestionIdsJson() == null && exam != null
          ? (int) questionRepository.countByExamId(a.getExamId())
          : loadAttemptQuestionIds(a).size();
      List<SectionScore> sections = readSectionScores(a);
      return new AttemptSummary(a.getAttemptId(),
          exam == null ? null : exam.getExamCode(),
          exam == null ? null : exam.getKind(),
          exam == null ? "(deleted exam)" : exam.getTitle(),
          a.getStatus(), ISO.format(a.getStartedAt()),
          a.getSubmittedAt() == null ? null : ISO.format(a.getSubmittedAt()),
          a.getOverallBand() == null ? null : a.getOverallBand().doubleValue(),
          a.getAutoAccuracy() == null ? null : a.getAutoAccuracy().doubleValue(),
          count, totalScaled(sections), totalMax(sections), correctCount(sections), autoCount(sections));
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
          prompt.length() > 90 ? prompt.substring(0, 90) + "…" : prompt, ISO.format(w.getAnsweredAt()),
          q.getOrderNo()));
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
    if (TASK_SAT_SPR.equals(q.getTaskType())) {
      return isSatSprCorrect(answer, q.getAnswerKey());
    }
    if (TASK_NAESIN_MULTI.equals(q.getTaskType())) {
      return isMultiChoiceCorrect(answer, q.getAnswerKey());
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
        q.getAudioMode(), parseChoices(q.getChoicesJson()), q.getOrderNo(), Boolean.TRUE.equals(q.getAutoScore()),
        q.getKoHelpJson());
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
        readSectionScores(attempt), disclaimerFor(exam.getKind()), exam.getKind(),
        totalScaled(readSectionScores(attempt)), totalMax(readSectionScores(attempt)));
  }

  private Set<Long> wrongQuestionIds(Long userId) {
    return answerRepository.findWrongAnswers(userId).stream()
        .map(TpAttemptAnswer::getQuestionId).collect(Collectors.toSet());
  }

  /**
   * iBT·어휘·SAT·TOEIC 이용 범위(대표 결정 2026-11-22): 로그인한 일반 회원은 종류(kind)마다 첫 세트만 풀고,
   * 관리자와 재원생은 모든 세트를 푼다. 첫 세트 = 그 종류의 게시된 세트 가운데 exam_id가 가장 작은 것.
   * 이미 시작한 응시의 이어 풀기·결과 보기는 막지 않는다(시작만 막는다).
   */
  private void requireFirstSetOrFullAccess(AuthenticatedPrincipal principal, TpExam exam) {
    if (TpExam.isNumberedFirstSet(exam.getExamCode()) || exam.getExamCode().equals(firstSetCode(exam.getKind())) || naesinAccessService.hasFullAccess(principal)) return;
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, FIRST_SET_ONLY_MESSAGE);
  }

  /** 그 종류에서 일반 회원에게 열린 첫 세트의 시험 코드. 게시된 세트가 없으면 null. */
  @Transactional(readOnly = true)
  public String firstSetCode(String kind) {
    return examRepository.findByKindAndStatusOrderByExamIdAsc(kind, "PUBLISHED").stream()
        .findFirst().map(TpExam::getExamCode).orElse(null);
  }

  /** 고른 파트(task_type)와 유형(해설 꼬리표)에 맞는 문항인지. 둘 다 주면 둘 다 맞아야 한다. */
  static boolean matchesFocus(TpQuestion q, List<String> taskTypes, String focusTag) {
    if (taskTypes != null && !taskTypes.isEmpty() && !taskTypes.contains(q.getTaskType())) return false;
    if (focusTag != null && !focusTag.isBlank() && !focusTag.trim().equals(ToeicFocus.label(q.getExplanation()))) return false;
    return true;
  }

  /**
   * TOEIC 파트·유형만 골라 풀 때의 시간. 문항마다 시간을 더해 분 단위로 올린다(최소 1분).
   * <ul>
   *   <li>읽기: 실제 시험의 Reading 75분 ÷ 100문항 = 문항당 45초({@link ToeicFocus#READING_SEC_PER_QUESTION}).</li>
   *   <li>듣기: 실제 시험은 45분 ÷ 100문항 = 27초지만, 이 화면은 대본을 문항마다 브라우저 음성으로 다시 읽어 주므로
   *       27초로는 모자란다. 듣기는 그 세트의 문항당 시간(세트 시간 ÷ 세트 문항 수)과 27초 가운데 긴 쪽을 쓴다.</li>
   * </ul>
   * 예) Part 5 30문항 → 22.5분 → 23분, Part 5·6 46문항 → 34.5분 → 35분, Part 7 끝 25문항 → 18.75분 → 19분.
   */
  static int toeicPartialTimeLimitSec(List<TpQuestion> selected, int examLimitSec, int examTotal) {
    double listeningEach = Math.max(ToeicFocus.LISTENING_SEC_PER_QUESTION, examTotal > 0 ? (double) examLimitSec / examTotal : 0);
    double sec = 0;
    for (TpQuestion q : selected) {
      sec += "LISTENING".equals(q.getSection()) ? listeningEach : ToeicFocus.READING_SEC_PER_QUESTION;
    }
    return (int) Math.max(60, (long) Math.ceil(sec / 60.0) * 60);
  }

  static String disclaimerFor(String kind) {
    if (KIND_NAESIN.equals(kind)) return NAESIN_DISCLAIMER;
    if (KIND_TOEIC.equals(kind)) return TOEIC_DISCLAIMER;
    return KIND_SAT.equals(kind) ? SAT_DISCLAIMER : BAND_DISCLAIMER;
  }

  /** 점수 만점 합계(SAT 1600, TOEIC은 영역당 495, 내신은 배점 합계). 만점이 기록되지 않은 섹션이 있으면 null. */
  static Integer totalMax(List<SectionScore> sections) {
    if (sections.isEmpty() || sections.stream().anyMatch(s -> s.scaleMax() == null)) return null;
    return sections.stream().mapToInt(SectionScore::scaleMax).sum();
  }

  /** SAT 영역 추정 점수: 200 + round(정답 비율 × 60) × 10 → 200~800 (10점 단위). 공식 환산표가 아니다. */
  static Integer satScaledFor(int correct, int total) {
    if (total <= 0) return null;
    double ratio = Math.max(0, Math.min(1, (double) correct / total));
    return 200 + (int) Math.round(ratio * 60) * 10;
  }

  /** SAT 응시의 영역 추정 점수. 부분 응시(오답만 다시 풀기)는 점수를 내지 않는다(null). */
  static Integer satSectionScaled(boolean partial, int correct, int total) {
    return partial ? null : satScaledFor(correct, total);
  }

  /**
   * TOEIC 영역(듣기·읽기) 추정 점수: 5 + round(정답 비율 × 98) × 5 → 5~495 (5점 단위).
   * ETS 환산표가 아니라 정답 비율을 점수 범위에 그대로 편 F2R 내부 추정치다.
   */
  static Integer toeicScaledFor(int correct, int total) {
    if (total <= 0) return null;
    double ratio = Math.max(0, Math.min(1, (double) correct / total));
    return 5 + (int) Math.round(ratio * 98) * 5;
  }

  /** TOEIC 응시의 영역 추정 점수. 부분 응시(오답만 다시 풀기)는 점수를 내지 않는다(null). */
  static Integer toeicSectionScaled(boolean partial, int correct, int total) {
    return partial ? null : toeicScaledFor(correct, total);
  }

  /** 영역별 맞힌 문항 수 합계. 채점 기록이 없으면(제출 전) null. 내 점수 화면이 부분 응시의 "정답 수 / 문항 수"에 쓴다. */
  static Integer correctCount(List<SectionScore> sections) {
    return sections.isEmpty() ? null : Integer.valueOf(sections.stream().mapToInt(SectionScore::correct).sum());
  }

  /** 영역별 자동 채점 문항 수 합계. 채점 기록이 없으면(제출 전) null. */
  static Integer autoCount(List<SectionScore> sections) {
    return sections.isEmpty() ? null : Integer.valueOf(sections.stream().mapToInt(SectionScore::autoCount).sum());
  }

  /** SAT 합계(400~1600). 영역 점수가 하나도 없으면 null. */
  static Integer totalScaled(List<SectionScore> sections) {
    List<Integer> scaled = sections.stream().map(SectionScore::scaled).filter(Objects::nonNull).toList();
    if (scaled.isEmpty()) return null;
    return scaled.stream().mapToInt(Integer::intValue).sum();
  }

  /**
   * 내신 "모두 고르면" 채점. 고른 기호 묶음과 정답 기호 묶음이 순서와 상관없이 똑같아야 정답이다
   * (하나라도 빠지거나 더 고르면 오답, 실제 학교 시험과 같다).
   */
  static boolean isMultiChoiceCorrect(String answer, String answerKey) {
    if (answer == null || answerKey == null) return false;
    Set<String> given = letterSet(answer);
    return !given.isEmpty() && given.equals(letterSet(answerKey));
  }

  private static Set<String> letterSet(String s) {
    return Arrays.stream(s.split(",")).map(x -> x.trim().toUpperCase()).filter(x -> !x.isEmpty())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * SAT 주관식 채점. answerKey는 허용 답을 '|'로 나열한다(예: "21/2|10.5").
   * 공백을 뺀 문자열이 같거나, 분수·소수를 수로 바꿔 같은 값이면 정답이다.
   */
  static boolean isSatSprCorrect(String answer, String answerKey) {
    if (answer == null || answerKey == null) return false;
    String given = answer.replaceAll("\\s+", "");
    if (given.isEmpty() || given.length() > 6) return false;
    Double givenValue = parseSatNumber(given);
    for (String raw : answerKey.split("\\|")) {
      String accepted = raw.replaceAll("\\s+", "");
      if (accepted.isEmpty()) continue;
      if (accepted.equals(given)) return true;
      Double acceptedValue = parseSatNumber(accepted);
      if (givenValue != null && acceptedValue != null && Math.abs(givenValue - acceptedValue) < 1e-9) return true;
      if (givenValue != null && acceptedValue != null && fillsSprWithCutOrRoundedDecimal(given, givenValue, acceptedValue)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 디지털 SAT 입력 규칙: 너무 긴 소수는 입력칸(양수 5자, 음수 6자)을 다 채워 자르거나 반올림해 넣는다.
   * 예) 2/3 → .6666, .6667, 0.666, 0.667 정답 / 0.66, .67 오답(칸을 다 채우지 않음).
   */
  static boolean fillsSprWithCutOrRoundedDecimal(String given, double givenValue, double acceptedValue) {
    int dot = given.indexOf('.');
    if (dot < 0 || given.indexOf('/') >= 0) return false;
    int maxLen = given.startsWith("-") ? 6 : 5;
    if (given.length() < maxLen) return false;
    int decimals = given.length() - dot - 1;
    if (decimals <= 0) return false;
    double scale = Math.pow(10, decimals);
    double cut = (acceptedValue < 0 ? Math.ceil(acceptedValue * scale) : Math.floor(acceptedValue * scale)) / scale;
    double rounded = Math.round(acceptedValue * scale) / scale;
    return Math.abs(givenValue - cut) < 1e-9 || Math.abs(givenValue - rounded) < 1e-9;
  }

  /** "-3", ".75", "7/2", "-1/4" 형태만 수로 바꾼다. 그 밖의 문자열은 null. */
  static Double parseSatNumber(String s) {
    if (s == null || !s.matches("-?(\\d+(\\.\\d*)?|\\.\\d+)(/\\d+(\\.\\d*)?)?")) return null;
    int slash = s.indexOf('/');
    try {
      if (slash < 0) return Double.parseDouble(s);
      double num = Double.parseDouble(s.substring(0, slash));
      double den = Double.parseDouble(s.substring(slash + 1));
      if (den == 0) return null;
      return num / den;
    } catch (NumberFormatException e) {
      return null;
    }
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

  /** 점수표의 영역 순서. TOEIC은 실제 시험 순서대로 듣기 → 읽기, 그 밖은 기존 순서. */
  static int sectionRank(String kind, String section) {
    if (KIND_TOEIC.equals(kind)) {
      int i = TOEIC_SECTION_ORDER.indexOf(section);
      return i < 0 ? 99 : i;
    }
    return sectionRank(section);
  }

  private static int sectionRank(String section) {
    int i = PRACTICE_SECTION_ORDER.indexOf(section);
    return i < 0 ? 99 : i;
  }
}
