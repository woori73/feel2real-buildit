package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service;

import com.anthropic.client.AnthropicClient;
import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClaudeChatServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ClaudeChatService service;

  @BeforeEach
  void setUp() {
    service = new ClaudeChatService(
        mock(AnthropicClient.class),
        mock(ConversationSourceRepository.class),
        mock(SourceRepository.class),
        mock(ConversationMessageRepository.class),
        objectMapper,
        mock(FileStorage.class),
        "claude-sonnet-4-6");
  }

  @Test
  @DisplayName("buildSystemText — 학생 스냅샷이 있으면 [학생 정보] 블록이 포함된다")
  void includes_student_block_when_snapshot_present() {
    // given
    Conversation conv = Conversation.builder()
        .student(sampleStudent())
        .title("새 대화")
        .metadata(ConversationMetadata.initial(sampleStudent(), 1).toJson(objectMapper))
        .build();

    // when
    String systemText = service.buildSystemText(conv);

    // then
    assertThat(systemText)
        .contains("[학생 정보]")
        .contains("이름: 홍길동")
        .contains("학교: 00중학교")
        .contains("학년: 3학년")
        .contains("학부모: 홍부모님");
  }

  @Test
  @DisplayName("buildSystemText — is_exam=true인 source가 있으면 [시험 결과] 섹션이 추가된다")
  void includes_exam_results_when_confirmed_exam_present() {
    // given — exam.pdf는 확정된 시험지(8/10), notes.pdf는 일반 자료
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(7L, "exam.pdf"))
        .withSourceAppended(new ConversationMetadata.SourceSummary(8L, "notes.pdf"))
        .withSourceUpdated(7L, 42L, "8/10");
    Conversation conv = Conversation.builder()
        .student(sampleStudent())
        .title("새 대화")
        .metadata(metadata.toJson(objectMapper))
        .build();

    // when
    String systemText = service.buildSystemText(conv);

    // then — 확정된 시험만 노출, 일반 자료는 [시험 결과]에 포함되지 않음
    assertThat(systemText)
        .contains("[시험 결과]")
        .contains("exam.pdf: 8/10");
    assertThat(systemText).doesNotContain("notes.pdf");
  }

  @Test
  @DisplayName("buildSystemText — 확정된 시험지가 없으면 [시험 결과] 섹션이 없다")
  void omits_exam_section_when_no_confirmed_exam() {
    // given — 일반 자료만 첨부
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(8L, "notes.pdf"));
    Conversation conv = Conversation.builder()
        .student(sampleStudent())
        .title("새 대화")
        .metadata(metadata.toJson(objectMapper))
        .build();

    // when
    String systemText = service.buildSystemText(conv);

    // then
    assertThat(systemText).doesNotContain("[시험 결과]");
  }

  @Test
  @DisplayName("buildSystemText — 확정된 시험지가 여러 개면 모두 나열된다")
  void lists_all_confirmed_exams() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(7L, "mid.pdf"))
        .withSourceAppended(new ConversationMetadata.SourceSummary(8L, "final.pdf"))
        .withSourceUpdated(7L, 42L, "8/10")
        .withSourceUpdated(8L, 43L, "5/10");
    Conversation conv = Conversation.builder()
        .student(sampleStudent())
        .title("새 대화")
        .metadata(metadata.toJson(objectMapper))
        .build();

    // when
    String systemText = service.buildSystemText(conv);

    // then
    assertThat(systemText)
        .contains("[시험 결과]")
        .contains("mid.pdf: 8/10")
        .contains("final.pdf: 5/10");
  }

  @Test
  @DisplayName("buildSystemText — metadata가 비어있으면 학생 정보 블록 없이 기본 시스템 프롬프트만 반환")
  void falls_back_to_default_prompt_when_metadata_blank() {
    // given
    Conversation conv = Conversation.builder()
        .student(sampleStudent())
        .title("새 대화")
        .metadata(null)
        .build();

    // when
    String systemText = service.buildSystemText(conv);

    // then
    assertThat(systemText).doesNotContain("[학생 정보]");
    assertThat(systemText).contains("영어 과외 AI 튜터");
  }

  @Test
  @DisplayName("chat — 데모(anthropic.enabled=false)면 Claude 호출 없이 데모 안내를 반환한다")
  void chat_returns_demo_notice_when_anthropic_disabled() {
    // given — anthropicEnabled=false로 생성 (fromEnv 미호출, client=null)
    ClaudeChatService demoService = new ClaudeChatService(
        false,
        mock(ConversationSourceRepository.class),
        mock(SourceRepository.class),
        mock(ConversationMessageRepository.class),
        objectMapper,
        mock(FileStorage.class));
    Conversation conv = Conversation.builder().student(sampleStudent()).title("t").build();

    // when
    String result = demoService.chat(conv, List.of(), "안녕");

    // then — 데모 안내 문구, Claude 미호출(client=null이어도 NPE 없이 반환)
    assertThat(result).contains("데모 버전").contains("비활성화");
  }

  private Student sampleStudent() {
    User user = User.builder()
        .loginId("std1").password("pw").name("홍길동")
        .phone("010").role(Role.STUDENT).build();
    return Student.builder()
        .user(user)
        .schoolName("00중학교")
        .grade((byte) 3)
        .parentName("홍부모")
        .parentPhone("010-1111-2222")
        .build();
  }
}
