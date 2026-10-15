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

  /**
   * 공개 피드(홈 최신 3건·/newstart/updates 목록) 전용 정렬.
   * 원문 발표일(publishedAt)이 아닌 사이트 게시 시점(reviewedAt) 기준 내림차순이다.
   * 과거 원문을 backfill로 등록해도(V1046 등) 사이트에 새로 올라온 순서대로 노출되기 위함이다.
   * 표시되는 날짜는 publishedAt(원문 발표일) 그대로 유지한다.
   */
  List<NewStartOfficialUpdate> findByReviewStatusOrderByFeaturedDescReviewedAtDescPublishedAtDescUpdateIdDesc(
      ReviewStatus reviewStatus);

  List<NewStartOfficialUpdate>
      findByReviewStatusAndSourceTypeOrderByFeaturedDescReviewedAtDescPublishedAtDescUpdateIdDesc(
      ReviewStatus reviewStatus, SourceType sourceType);

  List<NewStartOfficialUpdate> findAllByOrderByUpdatedAtDescUpdateIdDesc();

  List<NewStartOfficialUpdate> findBySourceTypeOrderByUpdatedAtDescUpdateIdDesc(SourceType sourceType);

  Optional<NewStartOfficialUpdate> findByDedupKey(String dedupKey);

  boolean existsByDedupKeyAndUpdateIdNot(String dedupKey, Long updateId);
}

