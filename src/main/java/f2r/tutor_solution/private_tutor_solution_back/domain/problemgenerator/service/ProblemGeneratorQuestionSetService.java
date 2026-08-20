package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemGeneratorQuestionSetService {
  private static final int ENGLISH_SUBJECT_ID = 1;
  private static final int TITLE_MAX = 180;

  private final ProblemGeneratorQuestionSetRepository setRepository;
  private final ProblemGeneratorSetQuestionRepository setQuestionRepository;
  private final ProblemGeneratorMaterialRepository materialRepository;
  private final QuestionRepository questionRepository;
  private final PassageRepository passageRepository;
  private final SubjectRepository subjectRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional
  public QuestionSetDetailResponse save(JwtTokenProvider.AuthenticatedPrincipal principal, QuestionSetSaveRequest request) {
    User owner = userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    Subject subject = subjectRepository.findById(ENGLISH_SUBJECT_ID)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "영어 subject가 없습니다."));
    ProblemGeneratorMaterial material = resolveMaterial(principal, request.materialId());
    String raw = requireRawJson(request.rawResponseJson());
    JsonNode root = parse(raw);
    JsonNode questions = root.path("questions");
    if (!questions.isArray() || questions.isEmpty()) {
      throw new IllegalArgumentException("저장할 questions 배열이 필요합니다.");
    }
    ProblemGeneratorQuestionSet set = setRepository.save(ProblemGeneratorQuestionSet.builder()
        .owner(owner)
        .material(material)
        .title(normalizeTitle(request.title(), root.path("summary").asText("Gemini 생성 문제")))
        .grammar(blankToNull(request.grammar()))
        .promptPassage(blankToNull(request.promptPassage()))
        .model(blankToNull(request.model()))
        .rawResponseJson(raw)
        .visibility(MaterialVisibility.PRIVATE)
        .build());

    Passage sharedPassage = createSharedPassage(subject, request.promptPassage(), set.getTitle());
    int order = 1;
    for (JsonNode node : questions) {
      Question question = questionRepository.save(Question.builder()
          .subject(subject)
          .passage(sharedPassage)
          .examId(null)
          .questionType(resolveType(node.path("type").asText(null)))
          .questionText(requireQuestionText(node))
          .choices(serializeChoices(node.path("choices")))
          .correctAnswer(firstText(node, "answer", "correct_answer", "correctAnswer"))
          .explanation(firstText(node, "explanation", "commentary"))
          .source(QuestionSource.GENERATED)
          .orderNum(order)
          .questionLabel(String.valueOf(order))
          .point(resolvePoint(node.path("score")))
          .build());
      setQuestionRepository.save(ProblemGeneratorSetQuestion.builder()
          .set(set)
          .question(question)
          .orderNum(order++)
          .build());
    }
    return detail(principal, set.getSetId());
  }

  public Page<QuestionSetSummaryResponse> list(JwtTokenProvider.AuthenticatedPrincipal principal, Pageable pageable) {
    return setRepository.findAccessible(principal.userId(), pageable).map(QuestionSetSummaryResponse::of);
  }

  public QuestionSetDetailResponse detail(JwtTokenProvider.AuthenticatedPrincipal principal, Long setId) {
    ProblemGeneratorQuestionSet set = loadAccessible(principal, setId);
    List<SavedQuestionResponse> questions = setQuestionRepository.findBySet_SetIdOrderByOrderNumAsc(setId).stream()
        .map(link -> SavedQuestionResponse.of(link.getQuestion(), link.getOrderNum()))
        .toList();
    return QuestionSetDetailResponse.of(set, questions);
  }

  @Transactional
  public QuestionSetSummaryResponse updateSet(JwtTokenProvider.AuthenticatedPrincipal principal, Long setId,
      QuestionSetUpdateRequest request) {
    ProblemGeneratorQuestionSet set = loadOwnedOrAdmin(principal, setId);
    MaterialVisibility visibility = request.visibility();
    if (visibility == MaterialVisibility.PUBLIC && !isAdmin(principal)) {
      throw new AccessDeniedException("공개 문제 등록은 관리자만 가능합니다.");
    }
    set.updateMetadata(normalizeTitle(request.title(), set.getTitle()), blankToNull(request.grammar()),
        blankToNull(request.promptPassage()), visibility != null ? visibility : set.getVisibility());
    return QuestionSetSummaryResponse.of(set);
  }

  @Transactional
  public SavedQuestionResponse updateQuestion(JwtTokenProvider.AuthenticatedPrincipal principal, Long setId,
      Long questionId, QuestionUpdateRequest request) {
    ProblemGeneratorQuestionSet set = loadOwnedOrAdmin(principal, setId);
    ProblemGeneratorSetQuestion link = setQuestionRepository.findBySet_SetIdOrderByOrderNumAsc(set.getSetId()).stream()
        .filter(candidate -> candidate.getQuestion().getQuestionId().equals(questionId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));
    Question question = link.getQuestion();
    Passage passage = question.getPassage();
    if (request.passageContent() != null && !request.passageContent().isBlank()) {
      if (passage == null) {
        Subject subject = subjectRepository.findById(ENGLISH_SUBJECT_ID)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "영어 subject가 없습니다."));
        passage = passageRepository.save(Passage.builder()
            .subject(subject)
            .title(deriveTitle(request.passageTitle(), request.passageContent()))
            .content(request.passageContent().trim())
            .sourceOrigin(set.getTitle())
            .build());
      } else {
        passage.updateContent(deriveTitle(request.passageTitle(), request.passageContent()), request.passageContent().trim());
      }
    }
    question.updatePassage(passage);
    question.updateProblemGeneratorFields(
        request.questionType(), requireText(request.questionText(), "문항 내용을 입력해 주세요."),
        serializeList(request.choices()), request.correctAnswer(), request.explanation(),
        request.questionLabel(), request.point());
    return SavedQuestionResponse.of(question, link.getOrderNum());
  }

  @Transactional
  public void delete(JwtTokenProvider.AuthenticatedPrincipal principal, Long setId) {
    ProblemGeneratorQuestionSet set = loadOwnedOrAdmin(principal, setId);
    List<ProblemGeneratorSetQuestion> links = setQuestionRepository.findBySet_SetIdOrderByOrderNumAsc(setId);
    Set<Passage> passages = new LinkedHashSet<>();
    List<Question> questions = new ArrayList<>();
    for (ProblemGeneratorSetQuestion link : links) {
      Question question = link.getQuestion();
      questions.add(question);
      if (question.getPassage() != null) passages.add(question.getPassage());
    }
    setQuestionRepository.deleteBySet(set);
    setRepository.delete(set);
    questionRepository.deleteAll(questions);
    passageRepository.deleteAll(passages);
  }

  private ProblemGeneratorQuestionSet loadAccessible(JwtTokenProvider.AuthenticatedPrincipal principal, Long setId) {
    ProblemGeneratorQuestionSet set = setRepository.findById(setId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문제 묶음을 찾을 수 없습니다."));
    if (set.isPublic() || set.isOwnedBy(principal.userId()) || isAdmin(principal)) return set;
    throw new AccessDeniedException("접근할 수 없는 문제 묶음입니다.");
  }

  private ProblemGeneratorQuestionSet loadOwnedOrAdmin(JwtTokenProvider.AuthenticatedPrincipal principal, Long setId) {
    ProblemGeneratorQuestionSet set = loadAccessible(principal, setId);
    if (set.isOwnedBy(principal.userId()) || isAdmin(principal)) return set;
    throw new AccessDeniedException("자기 문제만 수정/삭제할 수 있습니다.");
  }

  private ProblemGeneratorMaterial resolveMaterial(JwtTokenProvider.AuthenticatedPrincipal principal, Long materialId) {
    if (materialId == null) return null;
    ProblemGeneratorMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Digital Twin 자료를 찾을 수 없습니다."));
    if (material.isPublic() || material.isOwnedBy(principal.userId()) || isAdmin(principal)) return material;
    throw new AccessDeniedException("접근할 수 없는 Digital Twin 자료입니다.");
  }

  private Passage createSharedPassage(Subject subject, String content, String origin) {
    if (content == null || content.isBlank()) return null;
    return passageRepository.save(Passage.builder()
        .subject(subject)
        .title(deriveTitle(null, content))
        .content(content.trim())
        .sourceOrigin(origin)
        .build());
  }

  private JsonNode parse(String raw) {
    try { return objectMapper.readTree(raw); }
    catch (Exception e) { throw new IllegalArgumentException("Gemini 결과 JSON을 파싱할 수 없습니다."); }
  }
  private String requireRawJson(String raw) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Gemini 생성 결과가 필요합니다.");
    return raw.trim();
  }
  private String requireQuestionText(JsonNode node) {
    return requireText(firstText(node, "stem", "question_text", "questionText"), "문항 내용이 비어 있습니다.");
  }
  private String requireText(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
  private String firstText(JsonNode node, String... names) {
    for (String name : names) {
      String value = node.path(name).asText(null);
      if (value != null && !value.isBlank()) return value.trim();
    }
    return null;
  }
  private QuestionType resolveType(String type) {
    if (type != null && (type.toLowerCase().contains("choice") || type.contains("객관"))) return QuestionType.MULTIPLE_CHOICE;
    return QuestionType.SHORT_ANSWER;
  }
  private Integer resolvePoint(JsonNode score) {
    if (score == null || score.isMissingNode() || score.isNull()) return 0;
    if (score.isNumber()) return Math.max(0, score.asInt());
    String digits = score.asText("").replaceAll("[^0-9]", "");
    return digits.isBlank() ? 0 : Integer.parseInt(digits);
  }
  private String serializeChoices(JsonNode choices) {
    if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
    List<String> values = new ArrayList<>();
    choices.forEach(choice -> { if (!choice.asText("").isBlank()) values.add(choice.asText()); });
    return serializeList(values);
  }
  private String serializeList(List<String> choices) {
    if (choices == null || choices.isEmpty()) return null;
    try { return objectMapper.writeValueAsString(choices); }
    catch (Exception e) { throw new IllegalStateException("선택지 직렬화 실패", e); }
  }
  private String normalizeTitle(String title, String fallback) {
    String value = title == null || title.isBlank() ? fallback : title.trim();
    if (value == null || value.isBlank()) value = "Gemini 생성 문제";
    return value.length() <= TITLE_MAX ? value : value.substring(0, TITLE_MAX);
  }
  private String deriveTitle(String title, String content) {
    if (title != null && !title.isBlank()) return normalizeTitle(title, title);
    String trimmed = content == null ? "" : content.trim();
    if (trimmed.isEmpty()) return null;
    int newline = trimmed.indexOf('\n');
    String first = newline >= 0 ? trimmed.substring(0, newline).trim() : trimmed;
    return first.length() <= 60 ? first : first.substring(0, 60).trim() + "...";
  }
  private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }
}
