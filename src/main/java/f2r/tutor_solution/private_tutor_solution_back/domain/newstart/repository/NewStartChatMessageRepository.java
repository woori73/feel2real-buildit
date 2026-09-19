package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartChatMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * NewStart 회원 채팅 메시지 repository. 히스토리 페이징은 "최근 순으로 N개 → 화면에서
 * 오래된 것부터(오름차순) 렌더" 방식이다. 삭제는 soft delete이므로 조회 쿼리는 항상
 * {@code deleted=false}만 본다 — 이미 내려간 화면의 메시지 id 순서는 변하지 않는다.
 */
public interface NewStartChatMessageRepository extends JpaRepository<NewStartChatMessage, Long> {

  /** 위로 스크롤 pagination: {@code before}보다 오래된(작은 id) 메시지를 최근 순으로 N개. */
  Page<NewStartChatMessage> findByMessageIdLessThanAndDeletedFalseOrderByMessageIdDesc(
      Long before, Pageable pageable);

  /** 초기 로딩: 최근 메시지 N개 (삭제 제외). */
  Page<NewStartChatMessage> findByDeletedFalseOrderByMessageIdDesc(Pageable pageable);

  /**
   * 재접속 구간 복구: {@code after}보다 새로운(큰 id) 메시지를 오래된 순으로 최대 50개.
   * WS가 끊긴 동안 놓친 메시지를 이어받는 용도다.
   */
  List<NewStartChatMessage> findTop50ByMessageIdGreaterThanAndDeletedFalseOrderByMessageIdAsc(
      Long after);
}
