package f2r.tutor_solution.private_tutor_solution_back.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostAttachment;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostAttachmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("CommunityPostAttachmentService — 게시글 문서 첨부 권한/개수/삭제")
class CommunityPostAttachmentServiceTest {

  private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};

  private CommunityPostAttachmentRepository attachmentRepository;
  private CommunityPostRepository postRepository;
  private UserRepository userRepository;
  private FileStorage fileStorage;
  private CommunityPostAttachmentService service;

  private User author;
  private User other;
  private JwtTokenProvider.AuthenticatedPrincipal authorP;
  private JwtTokenProvider.AuthenticatedPrincipal otherP;
  private JwtTokenProvider.AuthenticatedPrincipal adminP;

  @BeforeEach
  void setUp() {
    attachmentRepository = mock(CommunityPostAttachmentRepository.class);
    postRepository = mock(CommunityPostRepository.class);
    userRepository = mock(UserRepository.class);
    fileStorage = mock(FileStorage.class);
    service = new CommunityPostAttachmentService(attachmentRepository, postRepository, userRepository,
        fileStorage, new DocumentUploadValidator(new UploadSizeGuard(20L * 1024 * 1024)));
    ReflectionTestUtils.setField(service, "maxDocumentBytes", 20L * 1024 * 1024);

    author = user(10L, "author", Role.STUDENT);
    other = user(20L, "other", Role.STUDENT);
    authorP = new JwtTokenProvider.AuthenticatedPrincipal(10L, "author", Role.STUDENT.name());
    otherP = new JwtTokenProvider.AuthenticatedPrincipal(20L, "other", Role.STUDENT.name());
    adminP = new JwtTokenProvider.AuthenticatedPrincipal(30L, "admin", Role.ADMIN.name());
    lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(author));
    lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(other));
    lenient().when(userRepository.findById(30L)).thenReturn(Optional.of(user(30L, "admin", Role.ADMIN)));
    lenient().when(attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(anyLong()))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName("given 변형문제 글·작성자 when 첨부 then storage 저장 + content-type 추론")
  void addAttachments_authorVariant_ok() throws Exception {
    CommunityPost post = post(1L, BoardType.VARIANT, author);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));
    when(attachmentRepository.countByPost_PostIdAndVisibleTrue(1L)).thenReturn(0L);
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), anyLong())).thenReturn("k1");

    service.addAttachments(authorP, 1L, List.of(file("문제.pdf", PDF)));

    verify(fileStorage).saveRaw(anyLong(), anyString(), any(), anyLong());
    verify(attachmentRepository).save(any(CommunityPostAttachment.class));
  }

  @Test
  @DisplayName("given 시험 후기 게시판 when 첨부 then 400 (텍스트 전용)")
  void addAttachments_examReviewBoard_rejected() throws Exception {
    CommunityPost post = post(1L, BoardType.EXAM_REVIEW, author);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.addAttachments(authorP, 1L, List.of(file("a.pdf", PDF))))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("첨부할 수 없");
    verify(fileStorage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 비작성자 when 첨부 then 403")
  void addAttachments_nonAuthor_forbidden() {
    CommunityPost post = post(1L, BoardType.VARIANT, author);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.addAttachments(otherP, 1L, List.of(file("a.pdf", PDF))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 기존 8개 + 신규 3개 when 첨부 then 400 (최대 10)")
  void addAttachments_over10_rejected() {
    CommunityPost post = post(1L, BoardType.MATERIAL, author);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));
    when(attachmentRepository.countByPost_PostIdAndVisibleTrue(1L)).thenReturn(8L);

    assertThatThrownBy(() -> service.addAttachments(authorP, 1L,
        List.of(file("a.pdf", PDF), file("b.pdf", PDF), file("c.pdf", PDF))))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("10");
  }

  @Test
  @DisplayName("given 첨부·작성자 when 삭제 then row + storage 삭제")
  void deleteAttachment_author_removesStorage() {
    CommunityPost post = post(1L, BoardType.VARIANT, author);
    CommunityPostAttachment att = attachment(3L, post, author, "k3");
    when(attachmentRepository.findWithPostById(3L)).thenReturn(Optional.of(att));

    service.deleteAttachment(authorP, 3L);

    verify(attachmentRepository).delete(att);
    verify(fileStorage).delete("k3", "UNKNOWN");
  }

  @Test
  @DisplayName("given 첨부 when ADMIN 삭제 then 허용")
  void deleteAttachment_admin_allowed() {
    CommunityPost post = post(1L, BoardType.VARIANT, author);
    CommunityPostAttachment att = attachment(3L, post, author, "k3");
    when(attachmentRepository.findWithPostById(3L)).thenReturn(Optional.of(att));

    service.deleteAttachment(adminP, 3L);

    verify(attachmentRepository).delete(att);
  }

  @Test
  @DisplayName("given 첨부 when 제3자 삭제 then 403")
  void deleteAttachment_stranger_forbidden() {
    CommunityPost post = post(1L, BoardType.VARIANT, author);
    CommunityPostAttachment att = attachment(3L, post, author, "k3");
    when(attachmentRepository.findWithPostById(3L)).thenReturn(Optional.of(att));

    assertThatThrownBy(() -> service.deleteAttachment(otherP, 3L)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 숨김 글의 첨부 when serve then 404")
  void getAttachmentFile_hiddenPost_notFound() {
    CommunityPost post = post(1L, BoardType.VARIANT, author);
    post.hide(author);
    CommunityPostAttachment att = attachment(3L, post, author, "k3");
    when(attachmentRepository.findVisibleById(3L)).thenReturn(Optional.of(att));

    assertThatThrownBy(() -> service.getAttachmentFile(3L))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("찾을 수 없");
  }

  private MultipartFile file(String name, byte[] bytes) {
    return new org.springframework.mock.web.MockMultipartFile("files", name, "application/octet-stream", bytes);
  }

  private CommunityPost post(Long id, BoardType board, User author) {
    CommunityPost p = CommunityPost.builder()
        .author(author).boardType(board).subject(null).title("t").contentJson("{}").contentHtml("<p>x</p>").build();
    ReflectionTestUtils.setField(p, "postId", id);
    return p;
  }

  private CommunityPostAttachment attachment(Long id, CommunityPost post, User uploader, String key) {
    CommunityPostAttachment a = CommunityPostAttachment.builder()
        .post(post).uploader(uploader).originalFileName("f.pdf").contentType("application/pdf")
        .fileSize(4L).storageKey(key).build();
    ReflectionTestUtils.setField(a, "attachmentId", id);
    return a;
  }

  private User user(Long id, String loginId, Role role) {
    User u = User.builder().loginId(loginId).password("x").name(loginId).nickname(loginId).role(role).build();
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }
}
