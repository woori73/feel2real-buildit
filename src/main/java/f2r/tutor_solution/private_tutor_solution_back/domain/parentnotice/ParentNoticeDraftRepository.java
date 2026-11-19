package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ParentNoticeDraftRepository extends JpaRepository<ParentNoticeDraft, String> {
  List<ParentNoticeDraft> findTop30ByOwnerIdAndOwnerRoleOrderByCreatedAtDescIdDesc(
      Long ownerId, String ownerRole);

  Optional<ParentNoticeDraft> findByIdAndOwnerIdAndOwnerRole(
      String id, Long ownerId, String ownerRole);

  @Modifying
  @Query(value = """
      INSERT INTO parent_notice_drafts (id, owner_id, owner_role, kind, title, body, source, created_at)
      VALUES (:id, :ownerId, :ownerRole, :kind, :title, :body, :source, UTC_TIMESTAMP(6))
      ON DUPLICATE KEY UPDATE id = id
      """, nativeQuery = true)
  int insertDraft(@Param("id") String id, @Param("ownerId") Long ownerId,
      @Param("ownerRole") String ownerRole, @Param("kind") String kind,
      @Param("title") String title, @Param("body") String body, @Param("source") String source);
}
