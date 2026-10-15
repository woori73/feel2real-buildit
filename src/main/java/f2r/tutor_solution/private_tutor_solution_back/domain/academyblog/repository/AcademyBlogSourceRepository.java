package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademyBlogSourceRepository extends JpaRepository<AcademyBlogSource, Long> {
  List<AcademyBlogSource> findByPost_PostIdOrderBySortOrderAsc(Long postId);
  List<AcademyBlogSource> findBySourceIdIn(List<Long> sourceIds);
  Optional<AcademyBlogSource> findBySourceIdAndAcademy_Slug(Long sourceId, String academySlug);
}
