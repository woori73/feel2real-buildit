package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonCommunityCommentRepository extends JpaRepository<LessonCommunityComment, Long> {
  @Query("""
      select c from LessonCommunityComment c
      join fetch c.author
      where c.post.postId = :postId and c.visible = true
      order by c.createdAt asc, c.commentId asc
      """)
  List<LessonCommunityComment> findVisibleByPostId(@Param("postId") Long postId);

  @Query("""
      select c from LessonCommunityComment c
      join fetch c.post p
      join fetch p.lesson l
      join fetch l.academy a
      join fetch c.author
      where c.commentId = :commentId
      """)
  Optional<LessonCommunityComment> findDetailById(@Param("commentId") Long commentId);

  long countByPost_PostIdAndVisibleTrue(Long postId);

  @Query("""
      select count(c) from LessonCommunityComment c
      where c.visible = true
        and c.post.visible = true
        and c.post.lesson.lessonId = :lessonId
      """)
  long countVisibleByLessonId(@Param("lessonId") Long lessonId);

}
