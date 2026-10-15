package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPostType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonCommunityPostRepository extends JpaRepository<LessonCommunityPost, Long> {
  @Query("""
      select p from LessonCommunityPost p
      join fetch p.lesson l
      join fetch l.academy a
      join fetch p.author author
      left join fetch p.category c
      where p.lesson.lessonId = :lessonId
        and p.visible = true
        and (:postType is null or p.postType = :postType)
        and (:categoryId is null or c.categoryId = :categoryId)
      order by p.createdAt desc, p.postId desc
      """)
  List<LessonCommunityPost> findVisibleList(@Param("lessonId") Long lessonId,
      @Param("postType") LessonCommunityPostType postType,
      @Param("categoryId") Long categoryId);

  @Query("""
      select p from LessonCommunityPost p
      join fetch p.lesson l
      join fetch l.academy a
      join fetch p.author author
      left join fetch p.category c
      where p.postId = :postId
      """)
  Optional<LessonCommunityPost> findDetailById(@Param("postId") Long postId);

  long countByLesson_LessonIdAndVisibleTrue(Long lessonId);

  Optional<LessonCommunityPost> findFirstByLesson_LessonIdAndVisibleTrueOrderByCreatedAtDescPostIdDesc(Long lessonId);


  Optional<LessonCommunityPost> findFirstByLesson_LessonIdAndPostTypeAndVisibleTrueOrderByCreatedAtDescPostIdDesc(
      Long lessonId, LessonCommunityPostType postType);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update LessonCommunityPost p
      set p.category = null
      where p.lesson.lessonId = :lessonId
        and p.category.categoryId = :categoryId
      """)
  int moveCategoryPostsToGeneral(@Param("lessonId") Long lessonId, @Param("categoryId") Long categoryId);

}
