package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Import(CodiCallRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CodiCallRepositoryTest {
  @Autowired CodiCallRepository attempts;

  @Test void givenCommittedRequest_whenClaimedAgain_thenOriginalRemainsScopedAndQueryable() {
    String id = UUID.randomUUID().toString();
    attempts.claim(new CodiCallAttempt(id, 1L, 7L, "abc"));
    assertThrows(DataIntegrityViolationException.class,
        () -> attempts.claim(new CodiCallAttempt(id, 1L, 7L, "abc")));
    attempts.acknowledge(id, "conv_example", "initiated");
    assertEquals("conv_example", attempts.find(id, 1L, 7L).orElseThrow().getConversationId());
    assertTrue(attempts.find(id, 2L, 7L).isEmpty());
    assertTrue(attempts.find(id, 1L, 8L).isEmpty());
  }
}
