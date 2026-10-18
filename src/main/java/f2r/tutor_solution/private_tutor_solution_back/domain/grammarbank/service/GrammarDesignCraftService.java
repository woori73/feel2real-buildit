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
          .append(profile.getSchoolLevel().labelKo()).append(" · ")
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
      md.append("- ").append(tocNumber++).append(". ").append(profile.getSchoolLevel().labelKo())
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
