package f2r.tutor_solution.private_tutor_solution_back.domain.community.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.config.CommunityProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.AnswerRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.CommunityPostRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.Answer;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostLike;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.AnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostLikeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("CommunityService — Q&A 3계층 권한/404·403/XOR/transitive 가시성")
class CommunityServiceTest {

  private CommunityPostRepository postRepository;
  private AnswerRepository answerRepository;
  private CommunityPostLikeRepository likeRepository;
  private UserRepository userRepository;
  private SubjectRepository subjectRepository;
  private AuthenticatedUserResolver authResolver;
  private AcademyBlogSanitizer sanitizer;
  private CommunityPostAttachmentService attachmentService;
  private CommunityService service;

  private User author, other, admin;
  private JwtTokenProvider.AuthenticatedPrincipal authorP, otherP, adminP;

  @BeforeEach
  void setUp() {
    postRepository = mock(CommunityPostRepository.class);
    answerRepository = mock(AnswerRepository.class);
    likeRepository = mock(CommunityPostLikeRepository.class);
    userRepository = mock(UserRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    sanitizer = mock(AcademyBlogSanitizer.class);
    attachmentService = mock(CommunityPostAttachmentService.class);
    service = new CommunityService(postRepository, answerRepository, likeRepository,
        userRepository, subjectRepository, authResolver, new CommunityProperties(30, 7), sanitizer,
        attachmentService);
    lenient().when(attachmentService.listForPost(anyLong())).thenReturn(List.of());

    author = user(10L, "author", Role.STUDENT);
    other = user(20L, "other", Role.STUDENT);
    admin = user(30L, "admin", Role.ADMIN);
    authorP = new JwtTokenProvider.AuthenticatedPrincipal(10L, "author", Role.STUDENT.name());
    otherP = new JwtTokenProvider.AuthenticatedPrincipal(20L, "other", Role.STUDENT.name());
    adminP = new JwtTokenProvider.AuthenticatedPrincipal(30L, "admin", Role.ADMIN.name());

    lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(author));
    lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(other));
    lenient().when(userRepository.findById(30L)).thenReturn(Optional.of(admin));
    lenient().when(authResolver.currentPrincipal()).thenReturn(Optional.empty());
    // sanitize: html 그대로 반환(비-blank 통과)
    lenient().when(sanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    // detail() 의존 stub
    lenient().when(answerRepository.findVisibleByQuestionId(anyLong())).thenReturn(List.of());
  }

  private User user(Long id, String login, Role role) {
    User u = User.builder().loginId(login).password("pw").name(login).role(role).build();
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }

  private CommunityPost post(Long id, User a, LocalDateTime createdAt) {
    CommunityPost p = CommunityPost.builder().author(a).boardType(BoardType.QNA)
        .title("t").contentJson("{}").contentHtml("<p>q</p>").build();
    ReflectionTestUtils.setField(p, "postId", id);
    ReflectionTestUtils.setField(p, "createdAt", createdAt);
    return p;
  }

  private Answer answer(Long id, CommunityPost q, User a) {
    Answer ans = Answer.builder().question(q).author(a).contentJson("{}").contentHtml("<p>a</p>").build();
    ReflectionTestUtils.setField(ans, "answerId", id);
    ReflectionTestUtils.setField(ans, "createdAt", LocalDateTime.now());
    return ans;
  }

  private CommunityPostRequest postReq() { return new CommunityPostRequest("QNA", null, "제목", "{}", "<p>본문</p>"); }
  private AnswerRequest ansReq() { return new AnswerRequest("{}", "<p>답</p>"); }

  /* ── 질문 작성 ── */

  @Test @DisplayName("비로그인 질문 작성 401")
  void createPost_anon_401() {
    assertThatThrownBy(() -> service.createPost(null, postReq()))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
    verify(postRepository, never()).save(any());
  }

  @Test @DisplayName("로그인 질문 작성 → 저장 + sanitize 호출")
  void createPost_login_saves() {
    CommunityPost saved = post(1L, author, LocalDateTime.now());
    when(postRepository.save(any())).thenReturn(saved);
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.of(saved));
    when(likeRepository.countByPost_PostId(1L)).thenReturn(0L);
    service.createPost(authorP, postReq());
    verify(postRepository).save(any(CommunityPost.class));
    verify(sanitizer).sanitize("<p>본문</p>");
  }

  @Test @DisplayName("유효하지 않은 게시판 작성 → 400(IllegalArgument)")
  void createPost_badBoard() {
    assertThatThrownBy(() -> service.createPost(authorP, new CommunityPostRequest("NOPE", null, "t", "{}", "<p>x</p>")))
        .isInstanceOf(IllegalArgumentException.class);
    verify(postRepository, never()).save(any());
  }

  @Test @DisplayName("SUBJECT 게시판인데 subjectId 없음 → 400(IllegalArgument)")
  void createPost_subjectWithoutId() {
    assertThatThrownBy(() -> service.createPost(authorP, new CommunityPostRequest("SUBJECT", null, "t", "{}", "<p>x</p>")))
        .isInstanceOf(IllegalArgumentException.class);
    verify(postRepository, never()).save(any());
  }

  @Test @DisplayName("SUBJECT 게시판인데 없는 과목 → 400(IllegalArgument)")
  void createPost_subjectNotFound() {
    when(subjectRepository.findById(99)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createPost(authorP, new CommunityPostRequest("SUBJECT", 99, "t", "{}", "<p>x</p>")))
        .isInstanceOf(IllegalArgumentException.class);
    verify(postRepository, never()).save(any());
  }

  @Test @DisplayName("SUBJECT 게시판 + 유효한 과목 → subject 세팅해 저장")
  void createPost_subjectValid() {
    Subject subject = mock(Subject.class);
    when(subjectRepository.findById(1)).thenReturn(Optional.of(subject));
    CommunityPost saved = post(1L, author, LocalDateTime.now());
    when(postRepository.save(any())).thenReturn(saved);
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.of(saved));
    when(likeRepository.countByPost_PostId(1L)).thenReturn(0L);
    service.createPost(authorP, new CommunityPostRequest("SUBJECT", 1, "t", "{}", "<p>x</p>"));
    org.mockito.ArgumentCaptor<CommunityPost> captor = org.mockito.ArgumentCaptor.forClass(CommunityPost.class);
    verify(postRepository).save(captor.capture());
    assertThat(captor.getValue().getBoardType()).isEqualTo(BoardType.SUBJECT);
    assertThat(captor.getValue().getSubject()).isSameAs(subject);
  }

  @Test @DisplayName("질문 수정: 타인 403")
  void updatePost_notAuthor_403() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(post(1L, author, LocalDateTime.now())));
    assertThatThrownBy(() -> service.updatePost(otherP, 1L, postReq())).isInstanceOf(AccessDeniedException.class);
  }

  @Test @DisplayName("질문 수정: 숨김 404")
  void updatePost_hidden_404() {
    CommunityPost p = post(1L, author, LocalDateTime.now()); p.hide(author);
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    assertThatThrownBy(() -> service.updatePost(authorP, 1L, postReq()))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
  }

  @Test @DisplayName("질문 삭제: 작성자 soft-hide")
  void deletePost_author() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    service.deletePost(authorP, 1L);
    assertThat(p.isVisible()).isFalse();
  }

  @Test @DisplayName("질문 삭제: Admin 타인글 soft-hide")
  void deletePost_admin() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    service.deletePost(adminP, 1L);
    assertThat(p.isVisible()).isFalse();
  }

  @Test @DisplayName("질문 삭제: 타인 403")
  void deletePost_other_403() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    assertThatThrownBy(() -> service.deletePost(otherP, 1L)).isInstanceOf(AccessDeniedException.class);
    assertThat(p.isVisible()).isTrue();
  }

  /* ── 답변 ── */

  @Test @DisplayName("답변 작성: 로그인 + visible 질문 → 저장")
  void createAnswer_ok() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.of(p));
    when(likeRepository.countByPost_PostId(1L)).thenReturn(0L);
    service.createAnswer(otherP, 1L, ansReq());
    verify(answerRepository).save(any(Answer.class));
  }

  @Test @DisplayName("답변 작성: 숨김 질문 404")
  void createAnswer_hiddenQuestion_404() {
    CommunityPost p = post(1L, author, LocalDateTime.now()); p.hide(author);
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    assertThatThrownBy(() -> service.createAnswer(otherP, 1L, ansReq()))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    verify(answerRepository, never()).save(any());
  }

  @Test @DisplayName("답변 수정: 타인 403")
  void updateAnswer_notAuthor_403() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(answerRepository.findById(5L)).thenReturn(Optional.of(answer(5L, p, author)));
    assertThatThrownBy(() -> service.updateAnswer(otherP, 5L, ansReq())).isInstanceOf(AccessDeniedException.class);
  }

  @Test @DisplayName("답변 삭제: Admin soft-hide")
  void deleteAnswer_admin() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    Answer a = answer(5L, p, other);
    when(answerRepository.findById(5L)).thenReturn(Optional.of(a));
    service.deleteAnswer(adminP, 5L);
    assertThat(a.isVisible()).isFalse();
  }

  @Test @DisplayName("답변 수정: 숨김 답변 404")
  void updateAnswer_hidden_404() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    Answer a = answer(5L, p, author); a.hide(author);
    when(answerRepository.findById(5L)).thenReturn(Optional.of(a));
    assertThatThrownBy(() -> service.updateAnswer(authorP, 5L, ansReq()))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
  }

  @Test @DisplayName("답변 작업: 질문이 숨김이면 404(transitive)")
  void updateAnswer_questionHidden_404() {
    CommunityPost p = post(1L, author, LocalDateTime.now()); p.hide(author);
    Answer a = answer(5L, p, author);  // 답변 자체는 visible, 부모 질문 숨김
    when(answerRepository.findById(5L)).thenReturn(Optional.of(a));
    assertThatThrownBy(() -> service.updateAnswer(authorP, 5L, ansReq()))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
  }

  /* ── like ── */

  @Test @DisplayName("like 신규 생성")
  void like_new() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    when(likeRepository.findByPost_PostIdAndUser_UserId(1L, 20L)).thenReturn(Optional.empty());
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.of(p));
    when(likeRepository.countByPost_PostId(1L)).thenReturn(1L);
    service.toggleLike(otherP, 1L);
    verify(likeRepository).save(any(CommunityPostLike.class));
  }

  @Test @DisplayName("like 취소")
  void like_cancel() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    CommunityPostLike l = new CommunityPostLike(p, other);
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    when(likeRepository.findByPost_PostIdAndUser_UserId(1L, 20L)).thenReturn(Optional.of(l));
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.of(p));
    when(likeRepository.countByPost_PostId(1L)).thenReturn(0L);
    service.toggleLike(otherP, 1L);
    verify(likeRepository).delete(l);
    verify(likeRepository, never()).save(any());
  }

  @Test @DisplayName("like 기간(30일) 경과 → 400")
  void like_expired_400() {
    CommunityPost old = post(1L, author, LocalDateTime.now().minusDays(40));
    when(postRepository.findById(1L)).thenReturn(Optional.of(old));
    assertThatThrownBy(() -> service.toggleLike(otherP, 1L))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    verify(likeRepository, never()).save(any());
    verify(likeRepository, never()).delete(any());
  }

  @Test @DisplayName("like 비로그인 401")
  void like_anon_401() {
    assertThatThrownBy(() -> service.toggleLike(null, 1L))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
  }

  /* ── 목록/상세 ── */

  @Test @DisplayName("상세: 숨김/미존재 404")
  void detail_hidden_404() {
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.detail(1L))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
  }

  @Test @DisplayName("상세: 첨부 서비스에 위임하고 응답에 attachments를 담는다")
  void detail_includesAttachments() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.findVisibleDetailById(1L)).thenReturn(Optional.of(p));
    when(likeRepository.countByPost_PostId(1L)).thenReturn(0L);
    when(attachmentService.listForPost(1L)).thenReturn(List.of());

    var result = service.detail(1L);

    assertThat(result.attachments()).isEmpty();
    verify(attachmentService).listForPost(1L);
  }

  @Test @DisplayName("목록: sort=recommended는 추천 쿼리, boardType enum 전달")
  void list_recommended_board() {
    when(postRepository.searchRecommended(any(), eq(BoardType.QNA), any(), any(), any())).thenReturn(List.of());
    service.list(null, "QNA", null, null, "recommended");
    verify(postRepository).searchRecommended(any(), eq(BoardType.QNA), any(), any(), any());
    verify(postRepository, never()).searchLatest(any(), any(), any(), any());
  }

  @Test @DisplayName("목록: 잘못된 게시판 필터 400")
  void list_badBoard_400() {
    assertThatThrownBy(() -> service.list(null, "NOPE", null, null, "latest"))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
  }

  @Test @DisplayName("목록: 비로그인 likedByMe=false")
  void list_anon_likedByMeFalse() {
    CommunityPost p = post(1L, author, LocalDateTime.now());
    when(postRepository.searchLatest(any(), any(), any(), any())).thenReturn(List.of(p));
    when(likeRepository.countTotalByPostIds(any())).thenReturn(List.of());
    when(likeRepository.countRecentByPostIds(any(), any())).thenReturn(List.of());
    when(answerRepository.countVisibleByQuestionIds(any())).thenReturn(List.of());
    var result = service.list(null, null, null, null, "latest");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).likedByMe()).isFalse();
  }

  @Test @DisplayName("목록 scope: 기본(community)은 F2R 게시판만, wiki는 wiki 게시판만 범위로 전달")
  void list_scope_splitsBoards() {
    // given
    when(postRepository.searchLatest(any(), any(), any(), any())).thenReturn(List.of());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Collection<BoardType>> scope = ArgumentCaptor.forClass(java.util.Collection.class);

    // when
    service.list(null, null, null, null, "latest");
    service.list("wiki", null, null, null, "latest");

    // then
    verify(postRepository, times(2)).searchLatest(scope.capture(), any(), any(), any());
    assertThat(scope.getAllValues().get(0))
        .containsExactlyInAnyOrder(BoardType.FREE, BoardType.QNA, BoardType.SUBJECT);
    assertThat(scope.getAllValues().get(1))
        .containsExactlyInAnyOrder(BoardType.EXAM_REVIEW, BoardType.VARIANT, BoardType.MATERIAL);
  }

  @Test @DisplayName("목록 scope: wiki 공간에 F2R 게시판(QNA) 필터를 요청하면 400")
  void list_scope_boardOutsideScope_400() {
    assertThatThrownBy(() -> service.list("wiki", "QNA", null, null, "latest"))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
  }

  @Test @DisplayName("목록 scope: 알 수 없는 scope 400")
  void list_scope_unknown_400() {
    assertThatThrownBy(() -> service.list("nope", null, null, null, "latest"))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
  }
}
