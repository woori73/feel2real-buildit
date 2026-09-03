package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionThreadPostRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiContextResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadDataKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWiki;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("QuestionThreadService — 문항 THREAD 쓰기 권한/답글/데이터 성격")
class QuestionThreadServiceTest {

  private QuestionWikiService questionWikiService;
  private QuestionThreadPostRepository postRepository;
  private QuestionThreadVariantDetailRepository variantRepository;
  private QuestionThreadExamEvidenceRepository examEvidenceRepository;
  private QuestionThreadAttachmentRepository attachmentRepository;
  private UserRepository userRepository;
  private FileStorage fileStorage;
  private QuestionThreadService service;

  private QuestionWiki question;
  private User student, otherStudent, admin;
  private JwtTokenProvider.AuthenticatedPrincipal studentP, otherP, adminP;

  @BeforeEach
  void setUp() {
    questionWikiService = mock(QuestionWikiService.class);
    postRepository = mock(QuestionThreadPostRepository.class);
    variantRepository = mock(QuestionThreadVariantDetailRepository.class);
    examEvidenceRepository = mock(QuestionThreadExamEvidenceRepository.class);
    attachmentRepository = mock(QuestionThreadAttachmentRepository.class);
    userRepository = mock(UserRepository.class);
    fileStorage = mock(FileStorage.class);
    DocumentUploadValidator validator = mock(DocumentUploadValidator.class);
    service = new QuestionThreadService(questionWikiService, postRepository, variantRepository,
        examEvidenceRepository, attachmentRepository, userRepository, fileStorage, validator);

    question = QuestionWiki.builder().subject("ENGLISH").grade(2).examYear(2026)
        .examLabel("9월 전국연합학력평가").questionNumber(21).visible(true).build();
    ReflectionTestUtils.setField(question, "questionWikiId", 100L);

    student = user(10L, "stu", Role.STUDENT);
    otherStudent = user(20L, "stu2", Role.STUDENT);
    admin = user(30L, "admin", Role.ADMIN);
    studentP = new JwtTokenProvider.AuthenticatedPrincipal(10L, "stu", Role.STUDENT.name());
    otherP = new JwtTokenProvider.AuthenticatedPrincipal(20L, "stu2", Role.STUDENT.name());
    adminP = new JwtTokenProvider.AuthenticatedPrincipal(30L, "admin", Role.ADMIN.name());

    lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(student));
    lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(otherStudent));
    lenient().when(userRepository.findById(30L)).thenReturn(Optional.of(admin));
    lenient().when(questionWikiService.loadVisible(100L)).thenReturn(question);
    lenient().when(questionWikiService.getContext(anyLong(), any()))
        .thenReturn(emptyContext());
    lenient().when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private User user(Long id, String login, Role role) {
    User u = User.builder().loginId(login).password("pw").name(login).role(role).build();
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }

  private QuestionThreadPost post(Long id, User author, QuestionThreadPost parent) {
    QuestionThreadPost p = QuestionThreadPost.builder()
        .questionWiki(question).parentPost(parent).author(author)
        .category(QuestionThreadCategory.QUESTION).dataKind(QuestionThreadDataKind.DISCUSSION)
        .title(null).content("본문").build();
    ReflectionTestUtils.setField(p, "postId", id);
    return p;
  }

  private QuestionWikiContextResponse emptyContext() {
    return new QuestionWikiContextResponse(
        new QuestionWikiContextResponse.Question(100L, "ENGLISH", 2, 2026, "9월 전국연합학력평가",
            21, null, java.util.List.of(), null, "고2 · 2026년 · 9월 전국연합학력평가 · 21번"),
        java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null,
        new QuestionWikiContextResponse.Counts(0, 0, 0, 0));
  }

  @Test
  @DisplayName("given 학생 when 질문 작성 then DISCUSSION 글이 저장된다")
  void studentCreatesDiscussionPost() {
    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        QuestionThreadCategory.QUESTION, QuestionThreadDataKind.DISCUSSION,
        null, "What절이 왜 명사절인가요?", null, null, null);

    service.createPost(studentP, 100L, req);

    verify(postRepository).save(argThat(p ->
        p.getDataKind() == QuestionThreadDataKind.DISCUSSION
            && p.getAuthorRole() == Role.STUDENT
            && p.getParentPost() == null));
  }

  @Test
  @DisplayName("given 변형문제인데 문제 지문 없음 when 작성 then 400")
  void generatedPracticeRequiresVariantText() {
    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        QuestionThreadCategory.VARIANT, QuestionThreadDataKind.GENERATED_PRACTICE,
        "변형문제", "설명", null, null, null);

    assertThatThrownBy(() -> service.createPost(studentP, 100L, req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("문제 지문");
  }

  @Test
  @DisplayName("given 답글의 답글 when 작성 then 400 (1 depth)")
  void replyToReplyRejected() {
    QuestionThreadPost root = post(1L, student, null);
    QuestionThreadPost reply = post(2L, student, root);
    when(postRepository.findDetailById(2L)).thenReturn(Optional.of(reply));

    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        null, null, null, "답글의 답글", 2L, null, null);

    assertThatThrownBy(() -> service.createPost(studentP, 100L, req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("답글");
  }

  @Test
  @DisplayName("given 작성자가 아닌 사용자 when 수정 then 403")
  void nonAuthorCannotUpdate() {
    QuestionThreadPost root = post(1L, student, null);
    when(postRepository.findDetailById(1L)).thenReturn(Optional.of(root));

    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        QuestionThreadCategory.QUESTION, QuestionThreadDataKind.DISCUSSION, null, "고침", null, null, null);

    assertThatThrownBy(() -> service.updatePost(otherP, 1L, req))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given ADMIN when 남의 글 숨김 then 허용된다")
  void adminCanHideOthersPost() {
    QuestionThreadPost root = post(1L, student, null);
    when(postRepository.findDetailById(1L)).thenReturn(Optional.of(root));

    service.hidePost(adminP, 1L);

    assertThat(root.isVisible()).isFalse();
  }

  @Test
  @DisplayName("given 제3자 when 남의 글 숨김 then 403")
  void strangerCannotHide() {
    QuestionThreadPost root = post(1L, student, null);
    when(postRepository.findDetailById(1L)).thenReturn(Optional.of(root));

    assertThatThrownBy(() -> service.hidePost(otherP, 1L))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(root.isVisible()).isTrue();
  }
}
