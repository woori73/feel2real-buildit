package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @RequiredArgsConstructor
class CodiCallRepository {
  private final EntityManager db;
  private final JdbcTemplate jdbc;

  // Commit the unique request BEFORE the external side effect, including across JVM restarts.
  @Transactional
  public void claim(CodiCallAttempt attempt) {
    jdbc.update("insert into codi_call_attempts (id,owner_id,academy_id,fingerprint,status) values (?,?,?,?,?)",
        attempt.getId(), attempt.getOwnerId(), attempt.getAcademyId(), attempt.getFingerprint(), attempt.getStatus());
  }

  @Transactional(readOnly = true)
  public Optional<CodiCallAttempt> find(String id, Long owner, Long academy) {
    return db.createQuery("from CodiCallAttempt where id=:id and ownerId=:owner and academyId=:academy", CodiCallAttempt.class)
        .setParameter("id", id).setParameter("owner", owner).setParameter("academy", academy)
        .getResultList().stream().findFirst();
  }

  @Transactional
  public void acknowledge(String id, String conversation, String status) {
    db.createQuery("update CodiCallAttempt set conversationId=:conversation, status=:status where id=:id")
        .setParameter("conversation", conversation).setParameter("status", status).setParameter("id", id).executeUpdate();
  }
}
