package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import com.fasterxml.jackson.databind.JsonNode;
import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AcademyAssistantAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.UnifiedAssistantService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class ParentNoticeService {
  public enum Kind { NOTICE, PROMOTION }
  public enum Source { DASHBOARD, MCP }

  public record Request(String requestId, Kind kind, String title, String body) {}
  public record Draft(String id, String kind, String title, String body, String source,
      Instant createdAt) {}

  private final ParentNoticeDraftRepository drafts;
  private final UnifiedAssistantService assistant;
  private final AcademyAssistantAdapter academy;

  private final ParentNoticeStorage storage;
  private final Clock clock;

  @Autowired
  public ParentNoticeService(ParentNoticeDraftRepository drafts, UnifiedAssistantService assistant,
      AcademyAssistantAdapter academy, ParentNoticeStorage storage) {
    this(drafts, assistant, academy, storage, Clock.systemUTC());
  }

  ParentNoticeService(ParentNoticeDraftRepository drafts, UnifiedAssistantService assistant,
      AcademyAssistantAdapter academy, ParentNoticeStorage storage, Clock clock) {
    this.drafts = drafts;
    this.assistant = assistant;
    this.academy = academy;
    this.storage = storage;
    this.clock = clock;
  }

  private JsonNode ownerContext(AuthenticatedPrincipal principal) {
    assistant.actor(principal);
    // An administrator can manage personal drafts without representing a billable academy.
    if ("ADMIN".equals(principal.role())) return null;
    JsonNode context = academy.context(principal);
    if (!"ACADEMY_OWNER".equals(context.path("role").asText())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학원 원장만 이용할 수 있습니다.");
    }
    return context;
  }

  public void requireOwner(AuthenticatedPrincipal principal) {
    ownerContext(principal);
  }

  private long academyId(JsonNode context) {
    JsonNode id = context.path("academyId");
    if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "원장의 기본 학원을 확인할 수 없습니다.");
    }
    return id.longValue();
  }

  @Transactional(readOnly = true)
  public long monthlyConfirmed(AuthenticatedPrincipal principal) {
    JsonNode context = ownerContext(principal);
    if (context == null) return 0L;
    YearMonth month = YearMonth.now(clock.withZone(KstTime.ZONE));
    return storage.monthlyConfirmed(academyId(context), month);
  }

  @Transactional(readOnly = true)
  public List<Draft> list(AuthenticatedPrincipal principal) {
    requireOwner(principal);
    return drafts.findTop30ByOwnerIdAndOwnerRoleOrderByCreatedAtDescIdDesc(
        principal.userId(), principal.role()).stream().map(ParentNoticeService::view).toList();
  }

  @Transactional
  public Draft save(AuthenticatedPrincipal principal, Request request, Source source) {
    JsonNode context = ownerContext(principal);
    UUID requestId = requestId(request.requestId());
    if (request.kind() == null) throw bad("공지 또는 홍보 분류를 선택하세요.");
    String title = field(request.title(), 80, "제목");
    String body = field(request.body(), 200, "보낼 문구");
    if (request.kind() == Kind.PROMOTION && !body.startsWith("(광고)")) {
      throw bad("홍보 문구는 (광고)로 시작해야 합니다.");
    }
    String token = principal.userId() + ":" + principal.role() + ":" + requestId;
    String id = UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8)).toString();

    Instant savedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    boolean inserted = storage.insertDraft(id, principal, request.kind(), title, body, source, savedAt);
    ParentNoticeDraft row = drafts.findByIdAndOwnerIdAndOwnerRole(
        id, principal.userId(), principal.role()).orElseThrow();
    if (!row.getKind().equals(request.kind().name()) || !row.getTitle().equals(title)
        || !row.getBody().equals(body) || !row.getSource().equals(source.name())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 요청 ID에 다른 내용이 있습니다.");
    }
    if (inserted && context != null) {
      long academyId = academyId(context);
      storage.confirm(academyId, id, source, savedAt);
      if (context.path("academyOptions").size() > 1) {
        log.info("Parent notice attribution ambiguous=true academyId={} draftId={}", academyId, id);
      }
    }
    return view(row);
  }

  public static Kind kind(String value) {
    try {
      return Kind.valueOf(value == null ? "" : value);
    } catch (IllegalArgumentException e) {
      throw bad("공지 또는 홍보 분류를 선택하세요.");
    }
  }

  private static UUID requestId(String input) {
    try {
      String canonical = input == null ? "" : input.toLowerCase(Locale.ROOT);
      UUID id = UUID.fromString(canonical);
      if (id.toString().equals(canonical)) return id;
    } catch (IllegalArgumentException ignored) {
      // A new request must have a complete canonical UUID.
    }
    throw bad("새 초안의 요청 ID를 확인하세요.");
  }

  private static String field(String raw, int maximum, String label) {
    String value = raw == null ? "" : raw.strip();
    if (value.isEmpty() || value.length() > maximum) {
      throw bad(label + "을(를) " + maximum + "자 이내로 입력하세요.");
    }
    return value;
  }

  private static Draft view(ParentNoticeDraft row) {
    return new Draft(row.getId(), row.getKind(), row.getTitle(), row.getBody(), row.getSource(),
        row.getCreatedAt().toInstant(ZoneOffset.UTC));
  }

  private static ResponseStatusException bad(String reason) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
  }
}
