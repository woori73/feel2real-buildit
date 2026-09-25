package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.ReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NewStartOfficialUpdateRepository extends JpaRepository<NewStartOfficialUpdate, Long>,
    JpaSpecificationExecutor<NewStartOfficialUpdate> {

  List<NewStartOfficialUpdate> findByReviewStatusOrderByFeaturedDescPublishedAtDescUpdateIdDesc(
      ReviewStatus reviewStatus);

  List<NewStartOfficialUpdate> findByReviewStatusAndSourceTypeOrderByFeaturedDescPublishedAtDescUpdateIdDesc(
      ReviewStatus reviewStatus, SourceType sourceType);

  List<NewStartOfficialUpdate> findAllByOrderByUpdatedAtDescUpdateIdDesc();

  List<NewStartOfficialUpdate> findBySourceTypeOrderByUpdatedAtDescUpdateIdDesc(SourceType sourceType);

  Optional<NewStartOfficialUpdate> findByDedupKey(String dedupKey);

  boolean existsByDedupKeyAndUpdateIdNot(String dedupKey, Long updateId);
}
