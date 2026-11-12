package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** begin_studio_upload 1회용 티켓: 한 번만·10분·연결별 상한·원문 미보관. */
class McpStudioUploadTicketsTest {
  private static final McpOAuth.Identity CLAUDE =
      new McpOAuth.Identity("grant-1", new AuthenticatedPrincipal(4L, "modu", "TUTOR"), Set.of("studio:read", "studio:propose"));
  private static final McpStudioUploadTickets.Args ARGS =
      new McpStudioUploadTickets.Args(
          "ai-agent-part1", "EP01_TEST", null, "r1", "a.mp4", "시험", "VIDEO", 1, null, "메모", null, null);

  /** 시각을 바꿀 수 있는 시계. */
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-12T00:00:00Z");

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void givenIssuedTicket_whenRedeemedTwice_thenOnlyFirstSucceeds() {
    // given
    var tickets = new McpStudioUploadTickets(new MutableClock());
    String ticket = tickets.issue(CLAUDE, "Claude", ARGS);
    // when
    var first = tickets.redeem(ticket);
    var second = tickets.redeem(ticket);
    // then
    assertNotNull(first);
    assertEquals("EP01_TEST", first.args().workId());
    assertEquals("grant-1", first.identity().grantId());
    assertEquals("Claude", first.clientName());
    assertNull(second);
  }

  @Test
  void givenTicketOlderThanTenMinutes_whenRedeemed_thenRejected() {
    // given
    var clock = new MutableClock();
    var tickets = new McpStudioUploadTickets(clock);
    String ticket = tickets.issue(CLAUDE, "Claude", ARGS);
    // when
    clock.now = clock.now.plus(McpStudioUploadTickets.TTL).plus(Duration.ofSeconds(1));
    // then
    assertNull(tickets.redeem(ticket));
  }

  @Test
  void givenStoredHashOnly_whenRedeemWithHashOrGarbage_thenRejected() {
    // given
    var tickets = new McpStudioUploadTickets(new MutableClock());
    String ticket = tickets.issue(CLAUDE, "Claude", ARGS);
    // when / then
    assertNull(tickets.redeem(McpStudioUploadTickets.hash(ticket)));
    assertNull(tickets.redeem(""));
    assertNull(tickets.redeem(null));
    assertNull(tickets.redeem("x".repeat(500)));
    assertNotNull(tickets.redeem(ticket));
  }

  @Test
  void givenMaxPendingTickets_whenIssueAnother_thenTooManyRequests() {
    // given
    var tickets = new McpStudioUploadTickets(new MutableClock());
    for (int i = 0; i < McpStudioUploadTickets.MAX_PENDING_PER_GRANT; i++) tickets.issue(CLAUDE, "Claude", ARGS);
    // when
    var e = assertThrows(ResponseStatusException.class, () -> tickets.issue(CLAUDE, "Claude", ARGS));
    // then
    assertEquals(429, e.getStatusCode().value());
  }

  @Test
  void givenExpiredTickets_whenIssueAgain_thenExpiredAreDroppedAndIssueSucceeds() {
    // given
    var clock = new MutableClock();
    var tickets = new McpStudioUploadTickets(clock);
    for (int i = 0; i < McpStudioUploadTickets.MAX_PENDING_PER_GRANT; i++) tickets.issue(CLAUDE, "Claude", ARGS);
    clock.now = clock.now.plus(Duration.ofMinutes(11));
    // when
    String fresh = tickets.issue(CLAUDE, "Claude", ARGS);
    // then
    assertEquals(1, tickets.size());
    assertNotNull(tickets.redeem(fresh));
  }
}
