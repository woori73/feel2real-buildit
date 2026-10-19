package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarCollectionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarDesignCraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollection;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollectionItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.GrammarCollectionItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.GrammarCollectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.QuestionGrammarTagRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.JobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingJobService;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문법 문제은행 선택함/문제집 → DesignCraft 편집기 연결.
 * 문항 순서·문항 번호 자동 부여·문제지/정답해설지 분리를 원고(markdown)로 조판해
 * 기존 /api/publishing.jobs 파이프라인(Worker·편집·PDF 출력)에 그대로 반입한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrammarDesignCraftService {

  private static final Map<String, String> ALLOWED_TEMPLATES = Map.of(
      "ko-textbook-a4", "A4",
      "ko-workbook-b5", "B5");

  private final GrammarCollectionService collectionService;
  private final GrammarCollectionRepository collectionRepository;
  private final GrammarCollectionItemRepository itemRepository;
  private final QuestionGrammarTagRepository questionTagRepository;
  private final GrammarBankService bankService;
  private final PublishingJobService publishingJobService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public record DesignCraftHandoff(JobResponse job, GrammarCollectionResponse collection,
      String editorUrl) {
  }

  /** 로컬 DesignCraft로 옮길 원고 산출물(파일명·본문·MIME). */
  public record ManuscriptExport(String filename, String content, String contentType) {
  }

  /** 선택함/문제집 원고를 로컬 DesignCraft용 Markdown으로 내려받는다. 웹 편집기 실행 없이 생성만 한다. */
  public ManuscriptExport exportMarkdown(AuthenticatedPrincipal principal, Long collectionId) {
    GrammarCollection collection = collectionService.loadOwned(principal, collectionId);
    String md = buildManuscript(collection, loadItems(collectionId), "ko-textbook-a4", true);
    return new ManuscriptExport(slug(collection.getTitle()) + ".md", md, "text/markdown");
  }

  /** 같은 원고를 HTML 문서로 내려받는다(로컬 브라우저·DesignCraft 입력용). */
  public ManuscriptExport exportHtml(AuthenticatedPrincipal principal, Long collectionId) {
    GrammarCollection collection = collectionService.loadOwned(principal, collectionId);
    String md = buildManuscript(collection, loadItems(collectionId), "ko-textbook-a4", true);
    return new ManuscriptExport(slug(collection.getTitle()) + ".html", toHtmlDocument(collection.getTitle(), md), "text/html");
  }

  /** 같은 원고를 구조화된 JSON(문항 배열)으로 내려받는다. */
  public ManuscriptExport exportJson(AuthenticatedPrincipal principal, Long collectionId) {
    GrammarCollection collection = collectionService.loadOwned(principal, collectionId);
    List<GrammarCollectionItem> items = loadItems(collectionId);
    java.util.List<Map<String, Object>> questions = new java.util.ArrayList<>();
    int number = 1;
    for (GrammarCollectionItem item : items) {
      Question q = item.getQuestion();
      GrammarQuestionProfile profile = bankService.requireProfile(q.getQuestionId());
      List<String> tags = questionTagRepository.findByQuestion_QuestionId(q.getQuestionId())
          .stream().map(gt -> gt.getTag().getNameKo()).toList();
      Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("number", number++);
      row.put("questionId", q.getQuestionId());
      row.put("schoolLevel", profile.getSchoolLevel().name());
      row.put("schoolLevelLabelKo", profile.getSchoolLevel().labelKo());
      row.put("recommendedGrade", GrammarReviewMetadata.recommendedGrade(profile.getAutoClassification()));
      row.put("difficulty", profile.getDifficulty());
      row.put("externalRef", profile.getExternalRef());
      row.put("reviewMetadata", GrammarReviewMetadata.snapshot(profile.getAutoClassification()));
      row.put("grammarTags", tags);
      row.put("passage", q.getPassage() != null ? q.getPassage().getContent() : null);
      row.put("questionText", q.getQuestionText());
      row.put("choices", bankService.parseChoices(q.getChoices()));
      row.put("correctAnswer", q.getCorrectAnswer());
      row.put("explanation", q.getExplanation());
      questions.add(row);
    }
    Map<String, Object> root = new java.util.LinkedHashMap<>();
    root.put("title", collection.getTitle());
    root.put("kind", collection.getKind() == null ? null : collection.getKind().name());
    root.put("questionCount", items.size());
    root.put("questions", questions);
    String json;
    try {
      json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 원고 생성 실패", e);
    }
    return new ManuscriptExport(slug(collection.getTitle()) + ".json", json, "application/json");
  }

  private List<GrammarCollectionItem> loadItems(Long collectionId) {
    List<GrammarCollectionItem> items =
        itemRepository.findByCollection_CollectionIdOrderByOrderNumAsc(collectionId);
    if (items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문제를 먼저 선택해 주세요.");
    }
    return items;
  }

  /** 마크다운 원고를 최소 형식의 HTML 문서로 변환한다(로컬 확인·입력용). */
  static String toHtmlDocument(String title, String markdown) {
    StringBuilder body = new StringBuilder();
    for (String raw : markdown.split("\n", -1)) {
      String line = raw.stripTrailing();
      String trimmed = line.strip();
      if (trimmed.isEmpty()) {
        body.append("\n");
        continue;
      }
      String text = escapeHtml(trimmed);
      if (trimmed.startsWith("# ")) {
        body.append("<h1>").append(text.substring(2).strip()).append("</h1>\n");
      } else if (trimmed.startsWith("## ")) {
        body.append("<h2>").append(text.substring(3).strip()).append("</h2>\n");
      } else if (trimmed.equals("---")) {
        body.append("<hr>\n");
      } else if (trimmed.startsWith("- ")) {
        body.append("<li>").append(text.substring(2).strip()).append("</li>\n");
      } else {
        body.append("<p>").append(text).append("</p>\n");
      }
    }
    return "<!DOCTYPE html>\n<html lang=\"ko\"><head><meta charset=\"utf-8\">"
        + "<title>" + escapeHtml(title) + "</title></head>\n<body>\n"
        + body + "</body></html>\n";
  }

  private static String escapeHtml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String slug(String title) {
    if (title == null || title.isBlank()) {
      return "grammar-items";
    }
    String cleaned = title.strip().replaceAll("[^\\p{L}\\p{N}_-]+", "_").replaceAll("^_+|_+$", "");
    return cleaned.isBlank() ? "grammar-items" : cleaned;
  }

  @Transactional
  public DesignCraftHandoff export(AuthenticatedPrincipal principal, Long collectionId,
      GrammarDesignCraftRequest request) {
    GrammarCollection collection = collectionService.loadOwned(principal, collectionId);
    String template = request != null && request.template() != null
        ? request.template() : "ko-textbook-a4";
    if (!ALLOWED_TEMPLATES.containsKey(template)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "템플릿은 ko-textbook-a4 | ko-workbook-b5 중 하나여야 합니다.");
    }
    List<GrammarCollectionItem> items =
        itemRepository.findByCollection_CollectionIdOrderByOrderNumAsc(collectionId);
    if (items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문제를 먼저 선택해 주세요.");
    }
    boolean splitAnswerSheet = request == null || request.splitAnswerSheet() == null
        || request.splitAnswerSheet();

    String manuscript = buildManuscript(collection, items, template, splitAnswerSheet);
    JobResponse job = publishingJobService.createJob(principal,
        new f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.CreateJobRequest(
            collection.getTitle(), template));
    publishingJobService.attachManuscript(principal, job.jobId(),
        new InMemoryMultipartFile("manuscript", "manuscript.md", "text/markdown",
            manuscript.getBytes(StandardCharsets.UTF_8)));

    String optionsJson;
    try {
      optionsJson = objectMapper.writeValueAsString(Map.of(
          "template", template,
          "splitAnswerSheet", splitAnswerSheet,
          "questionCount", items.size()));
    } catch (Exception e) {
      optionsJson = null;
    }
    collection.markDesignCraftJob(job.jobId(), optionsJson);
    collectionRepository.save(collection);
    return new DesignCraftHandoff(job, collectionService.detail(principal, collectionId),
        "/designcraft/editor?jobId=" + job.jobId());
  }

  String buildManuscript(GrammarCollection collection, List<GrammarCollectionItem> items,
      String template, boolean splitAnswerSheet) {
    Map<Long, List<String>> tagNamesByQuestion = new java.util.HashMap<>();
    for (GrammarCollectionItem item : items) {
      tagNamesByQuestion.put(item.getQuestion().getQuestionId(),
          questionTagRepository.findByQuestion_QuestionId(item.getQuestion().getQuestionId())
              .stream().map(gt -> gt.getTag().getNameKo()).toList());
    }
    StringBuilder md = new StringBuilder();
    md.append("# ").append(collection.getTitle()).append("\n\n");
    md.append("규격: ").append(ALLOWED_TEMPLATES.get(template)).append(
        " · 문항 수: ").append(items.size()).append("\n\n");

    int number = 1;
    for (GrammarCollectionItem item : items) {
      Question q = item.getQuestion();
      GrammarQuestionProfile profile = bankService.requireProfile(q.getQuestionId());
      md.append("## ").append(number++).append(". ")
          .append(classificationLabel(profile)).append(" · ")
          .append(String.join(", ", tagNamesByQuestion.get(q.getQuestionId()))).append("\n\n");
      if (q.getPassage() != null && q.getPassage().getContent() != null) {
        md.append(q.getPassage().getContent().strip()).append("\n\n");
      }
      md.append(q.getQuestionText().strip()).append("\n\n");
      List<String> choices = q.getChoices() == null || q.getChoices().isBlank()
          ? List.of() : bankService.parseChoices(q.getChoices());
      for (int c = 0; c < choices.size(); c++) {
        md.append("(").append((char) ('1' + c)).append(") ").append(choices.get(c)).append("\n");
      }
      if (!choices.isEmpty()) {
        md.append("\n");
      }
    }

    // 목차(문항 번호 → 유형)
    md.append("\n---\n\n## 목차\n\n");
    int tocNumber = 1;
    for (GrammarCollectionItem item : items) {
      GrammarQuestionProfile profile = bankService.requireProfile(item.getQuestion().getQuestionId());
      md.append("- ").append(tocNumber++).append(". ").append(classificationLabel(profile))
          .append(" ").append(String.join(", ", tagNamesByQuestion.get(item.getQuestion().getQuestionId())))
          .append("\n");
    }

    if (splitAnswerSheet) {
      md.append("\n---\n\n# 정답 및 해설\n\n");
      int answerNumber = 1;
      for (GrammarCollectionItem item : items) {
        Question q = item.getQuestion();
        md.append("## ").append(answerNumber++).append(".\n\n");
        md.append("정답: ").append(q.getCorrectAnswer() == null ? "-" : q.getCorrectAnswer())
            .append("\n\n");
        if (q.getExplanation() != null && !q.getExplanation().isBlank()) {
          md.append(q.getExplanation().strip()).append("\n\n");
        }
      }
    }
    return md.toString();
  }

  static String classificationLabel(GrammarQuestionProfile profile) {
    var grade = GrammarReviewMetadata.recommendedGrade(profile.getAutoClassification());
    String difficulty = profile.getDifficulty() == null ? "미분류" : switch (profile.getDifficulty()) {
      case EASY -> "기본";
      case MEDIUM -> "응용";
      case HARD -> "심화";
    };
    return "권장 " + (grade == null ? "미분류" : grade.labelKo()) + " · " + difficulty
        + " (기존 " + profile.getSchoolLevel().labelKo() + ")";
  }

  /** 메모리 원고를 PublishingJobService의 multipart 계약에 맞추는 어댑터. */
  static final class InMemoryMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
      this.name = name;
      this.originalFilename = originalFilename;
      this.contentType = contentType;
      this.content = content;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getOriginalFilename() {
      return originalFilename;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return content.length == 0;
    }

    @Override
    public long getSize() {
      return content.length;
    }

    @Override
    public byte[] getBytes() {
      return content;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File dest) throws java.io.IOException {
      java.nio.file.Files.write(dest.toPath(), content);
    }
  }
}
