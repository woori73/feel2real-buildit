package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 셸이 있는 AI 세션(Claude Code 등)이 큰 제작물을 MCP base64 인자 없이 올리기 위한 1회용 업로드 티켓.
 *
 * <p>{@code begin_studio_upload} 도구가 업로드 정보(작업 ID·버전·파일 이름 등)와 연결(grant)을 묶어 티켓을 발급하고,
 * 클라이언트는 {@code POST /mcp/studio/uploads}에 원본 bytes를 그대로 보낸다. 티켓은 10분 안에 한 번만 쓸 수 있고, 서버에는
 * 티켓 원문이 아니라 SHA-256만 남는다. 단일 EC2 인스턴스 기준이라 메모리에 보관하며, 재시작하면 미사용 티켓은 사라진다(다시
 * 발급하면 된다).
 */
@Component
public class McpStudioUploadTickets {
  static final Duration TTL = Duration.ofMinutes(10);
  /** 연결(grant)마다 동시에 쓸 수 있는 미사용 티켓 수. */
  static final int MAX_PENDING_PER_GRANT = 8;

  /** 티켓을 발급할 때 정한 업로드 정보. 파일 내용만 나중에 받는다. */
  public record Args(
      String projectId,
      String workId,
      String expectedHeadRevisionId,
      String versionLabel,
      String filename,
      String title,
      String kind,
      Integer episode,
      Integer cutNumber,
      String changeNote,
      String taskId,
      String modelReported) {}

  public record Pending(McpOAuth.Identity identity, String clientName, Args args, Instant expiresAt) {}

  private final Map<String, Pending> pending = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final Clock clock;

  @Autowired
  public McpStudioUploadTickets() {
    this(Clock.systemUTC());
  }

  McpStudioUploadTickets(Clock clock) {
    this.clock = clock;
  }

  /** 새 티켓 원문을 돌려준다. 원문은 이 응답에만 있고 서버에는 해시만 남는다. */
  public String issue(McpOAuth.Identity identity, String clientName, Args args) {
    purgeExpired();
    long mine = pending.values().stream().filter(p -> p.identity().grantId().equals(identity.grantId())).count();
    if (mine >= MAX_PENDING_PER_GRANT) throw McpOAuth.error(429, "too_many_pending_upload_tickets");
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    pending.put(hash(ticket), new Pending(identity, clientName, args, clock.instant().plus(TTL)));
    return ticket;
  }

  /** 티켓을 한 번만 꺼낸다. 없거나 만료됐으면 null. */
  public Pending redeem(String ticket) {
    if (ticket == null || ticket.isBlank() || ticket.length() > 100) return null;
    Pending p = pending.remove(hash(ticket.trim()));
    if (p == null || !clock.instant().isBefore(p.expiresAt())) return null;
    return p;
  }

  int size() {
    return pending.size();
  }

  private void purgeExpired() {
    Instant now = clock.instant();
    pending.values().removeIf(p -> !now.isBefore(p.expiresAt()));
  }

  static String hash(String ticket) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
