package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcademyBlogPostRepository extends JpaRepository<AcademyBlogPost, Long> {
  List<AcademyBlogPost> findByAcademy_AcademyIdOrderByUpdatedAtDesc(Long academyId);
  List<AcademyBlogPost> findByAcademy_SlugAndStatusAndVisibleTrueOrderByPublishedAtDesc(String slug, AcademyBlogPostStatus status);
  Optional<AcademyBlogPost> findByAcademy_SlugAndSlugAndStatusAndVisibleTrue(String academySlug, String slug, AcademyBlogPostStatus status);
  boolean existsByAcademy_AcademyIdAndSlug(Long academyId, String slug);
  boolean existsByAcademy_AcademyIdAndSlugAndPostIdNot(Long academyId, String slug, Long postId);

  @Query("""
      select p
      from AcademyBlogPost p
      join fetch p.academy a
      where p.status = :status
        and p.visible = true
        and a.status = f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus.PUBLISHED
        and a.visible = true
        and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%'))
          or lower(coalesce(p.summary, '')) like lower(concat('%', :keyword, '%'))
          or lower(a.name) like lower(concat('%', :keyword, '%')))
      order by p.publishedAt desc, p.postId desc
      """)
  List<AcademyBlogPost> searchPublicLatest(@Param("status") AcademyBlogPostStatus status, @Param("keyword") String keyword);

  @Query("""
      select p
      from AcademyBlogPost p
      join fetch p.academy a
      left join AcademyBlogPostLike l on l.post = p and l.createdAt >= :windowStart
      where p.status = :status
        and p.visible = true
        and a.status = f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus.PUBLISHED
        and a.visible = true
        and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%'))
          or lower(coalesce(p.summary, '')) like lower(concat('%', :keyword, '%'))
          or lower(a.name) like lower(concat('%', :keyword, '%')))
      group by p
      order by count(l.likeId) desc, p.publishedAt desc, p.postId desc
      """)
  List<AcademyBlogPost> searchPublicRecommended(
      @Param("status") AcademyBlogPostStatus status,
      @Param("keyword") String keyword,
      @Param("windowStart") java.time.LocalDateTime windowStart);
}
