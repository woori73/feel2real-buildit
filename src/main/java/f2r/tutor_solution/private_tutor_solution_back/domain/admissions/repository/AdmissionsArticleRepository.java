package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsArticle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsArticleRepository extends JpaRepository<AdmissionsArticle, Long> {
    /** 공개 목록: 최신 게시일 먼저, 같은 날은 정렬 순서. */
    List<AdmissionsArticle> findAllByVisibleTrueOrderByPublishedOnDescSortOrderAscArticleIdAsc();

    /** 관리자 목록: 숨긴 글 포함. */
    List<AdmissionsArticle> findAllByOrderByPublishedOnDescSortOrderAscArticleIdAsc();

    Optional<AdmissionsArticle> findByArticleIdAndVisibleTrue(Long articleId);

    boolean existsBySeriesAndEpisodeAndArticleIdNot(String series, String episode, Long articleId);

    boolean existsBySeriesAndEpisode(String series, String episode);
}
