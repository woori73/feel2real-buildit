package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.repository;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiPost;
import java.util.List;import java.util.Optional;import org.springframework.data.jpa.repository.EntityGraph;import org.springframework.data.jpa.repository.JpaRepository;
public interface ChoonggiPostRepository extends JpaRepository<ChoonggiPost,Long>{
  @EntityGraph(attributePaths="media")
  Optional<ChoonggiPost> findByIdAndDeletedAtIsNull(Long id);
  @EntityGraph(attributePaths="media")
  Optional<ChoonggiPost> findBySlugAndStatusAndDeletedAtIsNull(String slug,ChoonggiPost.Status status);
  @EntityGraph(attributePaths="media")
  List<ChoonggiPost> findByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(ChoonggiPost.Status status);
  @EntityGraph(attributePaths="media")
  List<ChoonggiPost> findByDeletedAtIsNullOrderByUpdatedAtDesc();
  boolean existsBySlugAndIdNot(String slug,Long id);
}
