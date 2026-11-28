package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.CodiChatEngine.PublicExchange;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class CodiGeneralConversation {
  private final UnifiedAssistantService permissions;
  private final AcademyAssistantAdapter academy;
  private final AssistantModelBudget budget;
  private final CodiChatEngine engine;
  private static final Duration LIFETIME = Duration.ofMinutes(30);
  private static final int MAX_CONTEXTS = 256;
  private static final int MAX_TURNS = 3;
  private static final Pattern IDENTIFIER = Pattern.compile(
      "(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)"
          + "|(?:이름|학생명|학부모명|주민번호|계좌번호|비밀번호|학생ID)\\s*[:=]\\s*\\S+"
          + "|(?:학생|학부모)\\s*(?:ID|아이디|#)\\s*[:=]?\\s*\\d+"
          + "|(?:학생|학부모)\\s+[가-힣]{2,4}\\s*(?:등록|등원|하원|결석|지각|\\d{1,3}점)",
      Pattern.CASE_INSENSITIVE);
  private record Key(Long user, Long academy, UUID token) {}
  private record Context(List<PublicExchange> turns, Instant expires) {}
  private final Map<Key,Context> contexts = new LinkedHashMap<>();

  public AssistantConversationService.Turn send(AuthenticatedPrincipal p, Long academyId, String message,
                                                UUID previous, boolean webSearch) {
    permissions.actor(p);
    // The public app uses a principal-only context. Legacy academy threads retain their owner check.
    if (academyId != null) academy.owner(p, academyId);
    if (message == null || message.isBlank() || message.length() > 2000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "일반 대화는 1~2000자로 입력해 주세요.");
    }
    if (AssistantIntentAnalyzer.containsContact(message) || IDENTIFIER.matcher(message).find()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연락처와 개인 식별 정보를 제외하고 공개 질문을 입력해 주세요.");
    }
    var history = previous == null ? List.<PublicExchange>of() : history(new Key(p.userId(), academyId, previous));
    engine.requireAvailable();
    if (!budget.tryAcquire(p.userId())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "일반 대화 사용 한도에 도달했습니다.");
    var answer = engine.reply(history, message, academyId != null && webSearch);
    UUID token = UUID.randomUUID();
    var updated = new ArrayList<>(history);
    updated.add(new PublicExchange(message, answer.text()));
    remember(new Key(p.userId(), academyId, token), updated);
    return new AssistantConversationService.Turn(answer.text(), "코디 일반 대화", null, List.of(), List.of(),
        null, Map.of("conversationId", token.toString(), "sources", answer.sources()));
  }

  private synchronized List<PublicExchange> history(Key key) {
    Context context = contexts.get(key);
    if (context == null || !context.expires().isAfter(Instant.now())) {
      contexts.remove(key);
      throw new ResponseStatusException(HttpStatus.GONE, "일반 대화 연결이 만료됐습니다. 새 대화를 시작해 주세요.");
    }
    return context.turns();
  }

  private synchronized void remember(Key key, List<PublicExchange> turns) {
    if (contexts.size() >= MAX_CONTEXTS) contexts.remove(contexts.keySet().iterator().next());
    contexts.put(key, new Context(List.copyOf(turns.subList(Math.max(0, turns.size() - MAX_TURNS), turns.size())),
        Instant.now().plus(LIFETIME)));
  }

  @Scheduled(fixedDelay = 60000)
  synchronized void expireConversations() {
    Instant now = Instant.now();
    contexts.values().removeIf(context -> !context.expires().isAfter(now));
  }
}
