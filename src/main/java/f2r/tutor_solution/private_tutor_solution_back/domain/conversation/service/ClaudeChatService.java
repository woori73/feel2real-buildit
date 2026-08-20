package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.MessageRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.VisionMediaType;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class ClaudeChatService {

  private static final String SYSTEM_PROMPT = """
      당신은 학생의 학습을 돕는 영어 과외 AI 튜터입니다.
      학생이 업로드한 자료(시험지, 교재, 학습 노트 등)를 바탕으로 친절하고 명확하게 질문에 답해주세요.
      - 시험지가 있으면 문제 유형, 오답 원인, 개선 방법을 구체적으로 안내합니다.
      - 어려운 개념은 예시와 함께 쉽게 설명합니다.
      - 한국어로 답변하되, 영어 표현은 영어로 그대로 씁니다.
      """;

  private static final String FILE_CONTEXT_PROMPT = "위 자료를 바탕으로 질문에 답해드릴게요.";
  private static final String FILE_ACK = "자료를 확인했습니다! 궁금한 점이 있으면 편하게 물어보세요.";

  /** Claude 비활성(anthropic.enabled=false) 환경에서 채팅 요청 시 반환하는 안내 문구. */
  private static final String DEMO_NOTICE =
      "데모 버전에서는 AI 채팅 기능이 비활성화되어 있습니다. (Claude API 미연결)";

  private final AnthropicClient client;
  private final boolean anthropicEnabled;
  private final ConversationSourceRepository conversationSourceRepository;
  private final SourceRepository sourceRepository;
  private final ConversationMessageRepository conversationMessageRepository;
  private final ObjectMapper objectMapper;
  private final FileStorage fileStorage;

  @Value("${anthropic.model}")
  private String model;

  @Autowired
  public ClaudeChatService(
      @Value("${anthropic.enabled:true}") boolean anthropicEnabled,
      ConversationSourceRepository conversationSourceRepository,
      SourceRepository sourceRepository,
      ConversationMessageRepository conversationMessageRepository,
      ObjectMapper objectMapper,
      FileStorage fileStorage) {
    this.anthropicEnabled = anthropicEnabled;
    // 데모(anthropic.enabled=false)에서는 클라이언트를 만들지 않는다 → ANTHROPIC_API_KEY 없이도 부팅.
    this.client = anthropicEnabled ? AnthropicOkHttpClient.fromEnv() : null;
    this.conversationSourceRepository = conversationSourceRepository;
    this.sourceRepository = sourceRepository;
    this.conversationMessageRepository = conversationMessageRepository;
    this.objectMapper = objectMapper;
    this.fileStorage = fileStorage;
  }

  /** 테스트용 생성자 */
  ClaudeChatService(
      AnthropicClient client,
      ConversationSourceRepository conversationSourceRepository,
      SourceRepository sourceRepository,
      ConversationMessageRepository conversationMessageRepository,
      ObjectMapper objectMapper,
      FileStorage fileStorage,
      String model) {
    this.client = client;
    this.anthropicEnabled = true;
    this.conversationSourceRepository = conversationSourceRepository;
    this.sourceRepository = sourceRepository;
    this.conversationMessageRepository = conversationMessageRepository;
    this.objectMapper = objectMapper;
    this.fileStorage = fileStorage;
    this.model = model;
  }

  /** history: 새 userInput 이전까지의 대화 히스토리 (중복 방지용 분리) */
  public String chat(Conversation conversation, List<ConversationMessage> history, String userInput) {
    if (!anthropicEnabled) {
      return DEMO_NOTICE;
    }
    List<Source> sources = loadSources(conversation.getConversationId());
    List<MessageParam> messages = buildMessages(sources, history, userInput);
    String systemText = buildSystemText(conversation);

    Message response = client.messages().create(
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .systemOfTextBlockParams(List.of(
                TextBlockParam.builder()
                    .text(systemText)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()))
            .messages(messages)
            .build());

    return response.content().stream()
        .flatMap(block -> block.text().stream())
        .map(TextBlock::text)
        .findFirst()
        .orElse("");
  }

  /**
   * 시스템 프롬프트 + 학생 정보 스냅샷 + (Phase 7) 시험 결과 요약을 하나의 캐시 블록으로 결합.
   * 시험 결과는 metadata.sources 중 is_exam=true인 항목의 examScore를 모아 나열한다.
   */
  String buildSystemText(Conversation conversation) {
    ConversationMetadata metadata =
        ConversationMetadata.fromJson(conversation.getMetadata(), objectMapper);
    ConversationMetadata.StudentSnapshot student = metadata.student();
    if (student == null) {
      return SYSTEM_PROMPT;
    }
    StringBuilder sb = new StringBuilder(SYSTEM_PROMPT)
        .append("\n[학생 정보]\n");
    if (student.name() != null) sb.append("- 이름: ").append(student.name()).append('\n');
    if (student.schoolName() != null) sb.append("- 학교: ").append(student.schoolName()).append('\n');
    if (student.grade() != null) sb.append("- 학년: ").append(student.grade()).append("학년\n");
    if (student.parentName() != null) sb.append("- 학부모: ").append(student.parentName()).append("님\n");

    appendExamResults(sb, metadata.sources());
    return sb.toString();
  }

  /**
   * sources 중 is_exam=true인 항목을 모아 [시험 결과] 섹션으로 append.
   * 시험지가 없거나 모두 미확정이면 섹션 자체를 추가하지 않는다.
   */
  private void appendExamResults(StringBuilder sb, List<ConversationMetadata.SourceSummary> sources) {
    if (sources == null || sources.isEmpty()) return;
    boolean headerAdded = false;
    for (ConversationMetadata.SourceSummary s : sources) {
      if (!Boolean.TRUE.equals(s.isExam())) continue;
      if (!headerAdded) {
        sb.append("\n[시험 결과]\n");
        headerAdded = true;
      }
      sb.append("- ");
      if (s.fileName() != null) sb.append(s.fileName()).append(": ");
      if (s.examScore() != null) sb.append(s.examScore());
      sb.append('\n');
    }
  }

  private List<MessageParam> buildMessages(
      List<Source> sources, List<ConversationMessage> history, String userInput) {

    List<MessageParam> messages = new ArrayList<>();

    if (!sources.isEmpty()) {
      List<ContentBlockParam> fileBlocks = new ArrayList<>();
      for (Source src : sources) {
        try {
          fileBlocks.addAll(toContentBlocks(src));
        } catch (Exception e) {
          log.warn("파일 로드 실패: sourceId={}, reason={}", src.getSourceId(), e.getMessage());
        }
      }
      if (!fileBlocks.isEmpty()) {
        // cache_control은 첨부 묶음의 마지막 블록(FILE_CONTEXT_PROMPT) 1개만 붙인다.
        // Anthropic은 cache_control breakpoint를 최대 4개까지만 허용하므로
        // 페이지마다 붙이면 PDF 5쪽 이상에서 400 (max 4 blocks with cache_control) 발생.
        // breakpoint 1개로도 그 이전 prefix(시스템 프롬프트 + 모든 이미지) 전체가 캐싱된다.
        fileBlocks.add(ContentBlockParam.ofText(
            TextBlockParam.builder()
                .text(FILE_CONTEXT_PROMPT)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build()));

        messages.add(MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(MessageParam.Content.ofBlockParams(fileBlocks))
            .build());
        messages.add(MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .content(FILE_ACK)
            .build());
      }
    }

    for (ConversationMessage msg : history) {
      messages.add(MessageParam.builder()
          .role(msg.getRole() == MessageRole.USER
              ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
          .content(msg.getContent())
          .build());
    }

    messages.add(MessageParam.builder()
        .role(MessageParam.Role.USER)
        .content(userInput)
        .build());

    return messages;
  }

  /**
   * PDF는 page_01.jpg ... page_N.jpg(항상 JPEG)를, IMAGE는 단일 파일을 페이지 단위로 받아 LLM 블록으로.
   * 실제 저장 위치(로컬 디스크 / S3)는 {@link FileStorage}가 추상화.
   */
  private List<ContentBlockParam> toContentBlocks(Source src) throws IOException {
    List<byte[]> pages = fileStorage.readAllPages(src.getStorageKey(), src.getFileType());
    Base64ImageSource.MediaType mediaType = "PDF".equals(src.getFileType())
        ? Base64ImageSource.MediaType.IMAGE_JPEG
        : VisionMediaType.forStorageKey(src.getStorageKey());

    List<ContentBlockParam> blocks = new ArrayList<>(pages.size());
    for (byte[] page : pages) {
      String base64 = Base64.getEncoder().encodeToString(page);
      blocks.add(ContentBlockParam.ofImage(
          ImageBlockParam.builder()
              .source(Base64ImageSource.builder()
                  .mediaType(mediaType)
                  .data(base64)
                  .build())
              .build()));
    }
    return blocks;
  }


  /**
   * 대화의 LLM 컨텍스트에 포함할 source 목록을 로드한다.
   * Phase 8 — {@link ProcessingStatus#FAILED} source는 LLM 컨텍스트에서 제외 (분석 결과가 없으므로 컨텍스트로 무의미).
   * COMPLETED 자료만 전달되어, 일부 자료 실패에도 나머지로 채팅을 이어갈 수 있다.
   */
  private List<Source> loadSources(Long conversationId) {
    return conversationSourceRepository.findByConversationId(conversationId).stream()
        .map(ConversationSource::getSourceId)
        .map(sourceRepository::findById)
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .filter(s -> s.getProcessingStatus() != ProcessingStatus.FAILED)
        .toList();
  }
}
