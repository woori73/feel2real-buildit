package f2r.tutor_solution.private_tutor_solution_back.domain.community.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

  /** 상세: 숨김 아닌 질문 + 작성자/과목 fetch. */
  @Query("""
      select p from CommunityPost p
      join fetch p.author a
      left join fetch p.subject s
      where p.postId = :postId and p.visible = true
      """)
  Optional<CommunityPost> findVisibleDetailById(@Param("postId") Long postId);

  /**
   * 최신순. scopeBoards(해당 공간의 게시판 화이트리스트) 안에서 boardType(null=scope 전체) + subjectId(과목 탭)
   * 필터 + keyword는 **제목만**. content_*는 LOB(LONGTEXT)라 lower() 검색 시 Hibernate
   * FunctionArgumentException → title만 검색.
   */
  @Query("""
      select p from CommunityPost p
      join fetch p.author a
      left join fetch p.subject s
      where p.visible = true
        and p.boardType in :scopeBoards
        and (:boardType is null or p.boardType = :boardType)
        and (:subjectId is null or s.subjectId = :subjectId)
        and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')))
      order by p.createdAt desc, p.postId desc
      """)
  List<CommunityPost> searchLatest(@Param("scopeBoards") Collection<BoardType> scopeBoards,
      @Param("boardType") BoardType boardType,
      @Param("subjectId") Integer subjectId, @Param("keyword") String keyword);

  /** 추천순: 최근 windowStart 이후 좋아요 수 DESC, 동률 최신순. scope/boardType/subject 필터 + keyword=제목. */
  @Query("""
      select p from CommunityPost p
      join fetch p.author a
      left join fetch p.subject s
      left join CommunityPostLike l on l.post = p and l.createdAt >= :windowStart
      where p.visible = true
        and p.boardType in :scopeBoards
        and (:boardType is null or p.boardType = :boardType)
        and (:subjectId is null or s.subjectId = :subjectId)
        and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')))
      group by p, a, s
      order by count(l.likeId) desc, p.createdAt desc, p.postId desc
      """)
  List<CommunityPost> searchRecommended(@Param("scopeBoards") Collection<BoardType> scopeBoards,
      @Param("boardType") BoardType boardType,
      @Param("subjectId") Integer subjectId, @Param("keyword") String keyword,
      @Param("windowStart") LocalDateTime windowStart);
}
