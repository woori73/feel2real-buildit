package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.repository;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiMedia;
import java.util.List;import java.util.Optional;import org.springframework.data.jpa.repository.JpaRepository;
public interface ChoonggiMediaRepository extends JpaRepository<ChoonggiMedia,Long>{
  List<ChoonggiMedia> findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long postId);
  Optional<ChoonggiMedia> findByIdAndDeletedAtIsNull(Long id);
}
