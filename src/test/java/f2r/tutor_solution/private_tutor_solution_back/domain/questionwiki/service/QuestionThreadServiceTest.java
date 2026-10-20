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
  @DisplayName("given 2026년 9월 문항에 형식까지 갖춘 AI 생성 저장 when 작성 then 403 — AI 문제제작 비제공 회차")
  void generatedPracticeBlockedForSeptember2026() {
    // fixture 문항은 2026년 "9월 전국연합학력평가"다.
    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        QuestionThreadCategory.VARIANT, QuestionThreadDataKind.GENERATED_PRACTICE,
        "AI 변형문제", "AI 생성", null,
        new QuestionThreadPostRequest.Variant("지문", "similar", "②", "해설", "", "21번", ""),
        null);

    assertThatThrownBy(() -> service.createPost(studentP, 100L, req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("AI 문제제작을 제공하지 않습니다");
    verify(postRepository, never()).save(any());
  }

  @Test
  @DisplayName("given 2026년 10월 문항에 AI 생성 저장 when 작성 then 허용된다")
  void generatedPracticeAllowedForOctober2026() {
    QuestionWiki october = QuestionWiki.builder().subject("ENGLISH").grade(2).examYear(2026)
        .examLabel("10월 모의고사").questionNumber(21).visible(true).build();
    ReflectionTestUtils.setField(october, "questionWikiId", 101L);
    when(questionWikiService.loadVisible(101L)).thenReturn(october);
    when(questionWikiService.getContext(anyLong(), any())).thenReturn(emptyContext());

    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        QuestionThreadCategory.VARIANT, QuestionThreadDataKind.GENERATED_PRACTICE,
        "AI 변형문제", "AI 생성", null,
        new QuestionThreadPostRequest.Variant("지문", "similar", "②", "해설", "", "21번", ""),
        null);

    service.createPost(studentP, 101L, req);

    verify(postRepository).save(any());
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

  @Test
  @DisplayName("given 분류 미지정(단순화 UI) when 작성 then category=ETC 로 저장(사용자에게 분류를 떠넘기지 않음)")
  void unclassifiedPostDefaultsToEtc() {
    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        null, null, null, "여기서 where를 쓰는 이유가 무엇인가요?", null, null, null);

    service.createPost(studentP, 100L, req);

    verify(postRepository).save(argThat(p ->
        p.getCategory() == QuestionThreadCategory.ETC
            && p.getDataKind() == QuestionThreadDataKind.DISCUSSION));
  }

  @Test
  @DisplayName("given GENERATED_PRACTICE 글을 내용만으로 수정 when updatePost then 변형문제 상세 없이도 통과(원문 보존)")
  void updateGeneratedPracticeWithContentOnly() {
    QuestionThreadPost variantPost = QuestionThreadPost.builder()
        .questionWiki(question).parentPost(null).author(student)
        .category(QuestionThreadCategory.VARIANT).dataKind(QuestionThreadDataKind.GENERATED_PRACTICE)
        .title("변형문제").content("원래 내용").build();
    ReflectionTestUtils.setField(variantPost, "postId", 5L);
    when(postRepository.findDetailById(5L)).thenReturn(Optional.of(variantPost));

    QuestionThreadPostRequest req = new QuestionThreadPostRequest(
        null, null, null, "내용만 고침", null, null, null);

    service.updatePost(studentP, 5L, req);

    assertThat(variantPost.getContent()).isEqualTo("내용만 고침");
    verify(variantRepository, never()).save(any());
  }
}
