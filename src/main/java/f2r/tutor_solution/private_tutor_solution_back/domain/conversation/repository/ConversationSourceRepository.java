package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSourceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationSourceRepository extends JpaRepository<ConversationSource, ConversationSourceId> {
  List<ConversationSource> findByConversationId(Long conversationId);

  /** 역방향 조회 — 시험지 확정 시 해당 source가 attach된 모든 conversation을 찾기 위함 (Phase 7). */
  List<ConversationSource> findBySourceId(Long sourceId);

  /** 상담자료 삭제 IDOR 가드(C1) — 이 conversation에 실제로 매핑된 source인지 확인. 타 conversation의 source 삭제 차단. */
  boolean existsByConversationIdAndSourceId(Long conversationId, Long sourceId);

  /** 자료 삭제 시 conversation 매핑 cascade 제거. */
  void deleteBySourceId(Long sourceId);
}
