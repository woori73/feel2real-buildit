package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessageStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiLiveMessageRepository extends JpaRepository<WikiLiveMessage, Long> {

  /** 폴링: 마지막으로 본 id 이후의 주어진 상태(VISIBLE) 메시지(오름차순). */
  @Query("""
      select m from WikiLiveMessage m
      where m.status = :status and m.wikiLiveMessageId > :sinceId
      order by m.wikiLiveMessageId asc
      """)
  List<WikiLiveMessage> findAfterId(
      @Param("status") WikiLiveMessageStatus status,
      @Param("sinceId") long sinceId,
      Pageable pageable);

  /** 초기 로딩: 최신 VISIBLE 메시지(내림차순, 서비스에서 뒤집어 오름차순으로 반환). */
  List<WikiLiveMessage> findByStatusOrderByWikiLiveMessageIdDesc(
      WikiLiveMessageStatus status, Pageable pageable);

  /** 도배 방지: 해당 사용자의 가장 최근 작성 시각. */
  @Query("select max(m.createdAt) from WikiLiveMessage m where m.userId = :userId")
  Optional<LocalDateTime> findLastCreatedAtByUserId(@Param("userId") Long userId);
}
