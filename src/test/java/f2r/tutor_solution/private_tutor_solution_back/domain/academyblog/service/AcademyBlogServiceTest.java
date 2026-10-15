package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.config.AcademyBlogProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPostLike;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.repository.AcademyBlogPostLikeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.repository.AcademyBlogPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.repository.AcademyBlogSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademyBlogServiceTest {

  @Mock AcademyRepository academyRepository;
  @Mock AcademyBlogPostRepository postRepository;
  @Mock AcademyBlogPostLikeRepository likeRepository;
  @Mock AcademyBlogSourceRepository sourceRepository;
  @Mock UserRepository userRepository;
  @Mock AuthenticatedUserResolver authResolver;
  @Mock FileStorage fileStorage;
  @Mock ImageSanitizer imageSanitizer;
  @Mock AcademyBlogSanitizer sanitizer;

  @Test
  void delete_removesSourcesBeforePost_thenDeletesFiles() {
    // given: 이미지(source) 2개가 붙은 내 글
    AcademyBlogService service = service();

    JwtTokenProvider.AuthenticatedPrincipal principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(principal.userId()).thenReturn(1L);

    Academy academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(10L);
    when(academyRepository.findByTutor_UserId(1L)).thenReturn(Optional.of(academy));

    AcademyBlogPost post = mock(AcademyBlogPost.class);
    when(post.getAcademy()).thenReturn(academy);
    when(postRepository.findById(100L)).thenReturn(Optional.of(post));

    AcademyBlogSource s1 = mock(AcademyBlogSource.class);
    AcademyBlogSource s2 = mock(AcademyBlogSource.class);
    when(s1.getStorageKey()).thenReturn("key-1");
    when(s2.getStorageKey()).thenReturn("key-2");
    List<AcademyBlogSource> sources = List.of(s1, s2);
    when(sourceRepository.findByPost_PostIdOrderBySortOrderAsc(100L)).thenReturn(sources);

    // when
    service.delete(principal, 100L);

    // then: source를 먼저 삭제한 뒤 post를 삭제해야 transient 참조 예외가 안 난다
    InOrder order = inOrder(sourceRepository, postRepository);
    order.verify(sourceRepository).deleteAll(sources);
    order.verify(postRepository).delete(post);
    // 파일은 DB 정리 후 각각 삭제
    verify(fileStorage).delete("key-1", "IMAGE");
    verify(fileStorage).delete("key-2", "IMAGE");
  }

  @Test
  void toggleLike_whenNotLikedAndWithinWindow_savesLike() {
    // given: 공개 글이 좋아요 가능 기간 안에 있고 현재 사용자는 아직 추천하지 않음
    AcademyBlogService service = service();
    JwtTokenProvider.AuthenticatedPrincipal principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(principal.userId()).thenReturn(7L);

    AcademyBlogPost post = publicPost();
    when(post.getPostId()).thenReturn(100L);
    when(post.getPublishedAt()).thenReturn(LocalDateTime.now().minusDays(3));
    when(postRepository.findById(100L)).thenReturn(Optional.of(post));
    when(likeRepository.findByPost_PostIdAndUser_UserId(100L, 7L)).thenReturn(Optional.empty());
    User user = mock(User.class);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(sourceRepository.findByPost_PostIdOrderBySortOrderAsc(100L)).thenReturn(List.of());

    // when
    service.toggleLike(principal, 100L);

    // then: 사용자별 좋아요 row를 새로 저장한다
    verify(likeRepository).save(any(AcademyBlogPostLike.class));
    verify(likeRepository, never()).delete(any());
  }

  @Test
  void toggleLike_whenAlreadyLiked_deletesLike() {
    // given: 공개 글에 이미 좋아요가 존재함
    AcademyBlogService service = service();
    JwtTokenProvider.AuthenticatedPrincipal principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(principal.userId()).thenReturn(7L);

    AcademyBlogPost post = publicPost();
    when(post.getPostId()).thenReturn(100L);
    when(post.getPublishedAt()).thenReturn(LocalDateTime.now().minusDays(3));
    when(postRepository.findById(100L)).thenReturn(Optional.of(post));
    AcademyBlogPostLike existing = mock(AcademyBlogPostLike.class);
    when(likeRepository.findByPost_PostIdAndUser_UserId(100L, 7L)).thenReturn(Optional.of(existing));
    when(sourceRepository.findByPost_PostIdOrderBySortOrderAsc(100L)).thenReturn(List.of());

    // when
    service.toggleLike(principal, 100L);

    // then: 토글 정책에 따라 기존 좋아요를 삭제한다
    verify(likeRepository).delete(existing);
    verify(likeRepository, never()).save(any());
  }

  private AcademyBlogService service() {
    return new AcademyBlogService(
        academyRepository, postRepository, likeRepository, sourceRepository, userRepository, authResolver,
        fileStorage, imageSanitizer, sanitizer, new AcademyBlogProperties(30, 7));
  }

  private AcademyBlogPost publicPost() {
    Academy academy = mock(Academy.class);
    when(academy.getStatus()).thenReturn(AcademyStatus.PUBLISHED);
    when(academy.isVisible()).thenReturn(true);
    AcademyBlogPost post = mock(AcademyBlogPost.class);
    when(post.isPublicVisible()).thenReturn(true);
    when(post.getAcademy()).thenReturn(academy);
    return post;
  }
}
