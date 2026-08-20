package f2r.tutor_solution.private_tutor_solution_back.domain.community.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {
  Optional<CommunityPostLike> findByPost_PostIdAndUser_UserId(Long postId, Long userId);
  boolean existsByPost_PostIdAndUser_UserId(Long postId, Long userId);
  long countByPost_PostId(Long postId);

  @Query("""
      select l.post.postId, count(l.likeId)
      from CommunityPostLike l
      where l.post.postId in :postIds
      group by l.post.postId
      """)
  List<Object[]> countTotalByPostIds(@Param("postIds") Collection<Long> postIds);

  @Query("""
      select l.post.postId, count(l.likeId)
      from CommunityPostLike l
      where l.post.postId in :postIds and l.createdAt >= :since
      group by l.post.postId
      """)
  List<Object[]> countRecentByPostIds(@Param("postIds") Collection<Long> postIds, @Param("since") LocalDateTime since);
}
