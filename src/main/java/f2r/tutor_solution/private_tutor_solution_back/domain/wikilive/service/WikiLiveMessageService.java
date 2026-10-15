package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wiki.support.PublicDisplayName;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessageStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.repository.WikiLiveMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * WIKI LIVE 한 줄 메시지. 조회는 공개, 작성은 로그인 회원만(컨트롤러 + {@code /api/**} 인증).
 *
 * <p>도배 방지: 동일 사용자 연속 작성 {@link #MIN_INTERVAL} 제한(인메모리 최근시각 맵 + DB 최근시각 대조).
 * XSS: 본문은 저장 전에 HTML 특수문자를 escape 한다(저장값 자체가 안전 — 이후 어떤 렌더러도 안전).
 */
@Service
@Transactional(readOnly = true)
public class WikiLiveMessageService {

  public static final int MAX_LENGTH = 100;
  static final Duration MIN_INTERVAL = Duration.ofSeconds(5);
  private static final int INITIAL_LOAD_LIMIT = 30;
  private static final int POLL_LIMIT = 50;

  private final WikiLiveMessageRepository repository;
  private final UserRepository userRepository;
  private final Clock clock;
  /** userId -> 마지막 작성 시각. 인스턴스 재시작 시 DB 최근시각으로 보정된다. */
  private final Map<Long, LocalDateTime> lastPostAt = new ConcurrentHashMap<>();

  @Autowired
  public WikiLiveMessageService(WikiLiveMessageRepository repository, UserRepository userRepository) {
    this(repository, userRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  WikiLiveMessageService(
      WikiLiveMessageRepository repository, UserRepository userRepository, Clock clock) {
    this.repository = repository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /** 초기 로딩 — 최신 N개를 오름차순(오래된→최신)으로. */
  public List<WikiLiveMessageResponse> recent() {
    List<WikiLiveMessage> desc = repository.findByStatusOrderByWikiLiveMessageIdDesc(
        WikiLiveMessageStatus.VISIBLE, PageRequest.of(0, INITIAL_LOAD_LIMIT));
    return desc.stream()
        .sorted((a, b) -> Long.compare(a.getWikiLiveMessageId(), b.getWikiLiveMessageId()))
        .map(WikiLiveMessageResponse::of)
        .toList();
  }

  /** 폴링 — sinceId 이후의 새 메시지만 오름차순으로. */
  public List<WikiLiveMessageResponse> since(long sinceId) {
    return repository
        .findAfterId(WikiLiveMessageStatus.VISIBLE, sinceId, PageRequest.of(0, POLL_LIMIT)).stream()
        .map(WikiLiveMessageResponse::of)
        .toList();
  }

  @Transactional
  public WikiLiveMessageResponse create(Long userId, String rawMessage) {
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    String normalized = normalize(rawMessage);
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
    }
    if (normalized.codePointCount(0, normalized.length()) > MAX_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "한 줄 인사는 " + MAX_LENGTH + "자까지 입력할 수 있습니다.");
    }
    enforceRateLimit(userId);

    String realName = userRepository.findById(userId).map(u -> u.getName()).orElse(null);
    String maskedName = PublicDisplayName.mask(realName);
    String safeMessage = escapeHtml(normalized);

    WikiLiveMessage saved = repository.save(WikiLiveMessage.of(userId, maskedName, safeMessage));
    lastPostAt.put(userId, LocalDateTime.now(clock));
    return WikiLiveMessageResponse.of(saved);
  }

  /** 관리자 숨김. 존재하지 않으면 404. 이미 숨김이면 그대로 둔다(멱등). */
  @Transactional
  public void adminHide(Long messageId) {
    WikiLiveMessage message = repository.findById(messageId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."));
    if (message.getStatus() == WikiLiveMessageStatus.VISIBLE) {
      message.hide(LocalDateTime.now(clock));
    }
  }

  private void enforceRateLimit(Long userId) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime last = lastPostAt.get(userId);
    if (last == null) {
      last = repository.findLastCreatedAtByUserId(userId).orElse(null);
    }
    if (last != null && Duration.between(last, now).compareTo(MIN_INTERVAL) < 0) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
    }
  }

  /** 앞뒤 공백 제거 + 내부 연속 공백/제어문자 → 단일 공백. */
  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
  }

  private String escapeHtml(String value) {
    StringBuilder sb = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        case '\'' -> sb.append("&#39;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
