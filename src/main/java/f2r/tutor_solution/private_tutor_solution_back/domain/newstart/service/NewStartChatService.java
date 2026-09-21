package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat.NewStartChatBroadcaster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat.NewStartChatMessageEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat.NewStartChatRateLimiter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartChatMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartChatMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 회원 채팅 service — 공용방 1개, 텍스트 전용, 회원 ↔ 회원.
 *
 * <p>NewStart AI({@code NewStartAiChatService})와 완전히 분리된 기능이다. 회원 대화는
 * Wiki AI Router로 전달되지 않고, AI 학습·RAG·embedding·요약에도 사용하지 않는다
 * (별도 정책/동의 결정 전까지 회원 대화 데이터로만 취급).
 *
 * <p>작성자 신원은 브라우저 요청값이 아니라 WS handshake에서 검증된
 * {@link JwtTokenProvider.AuthenticatedPrincipal}로만 결정한다 — userId/nickname을
 * 클라이언트가 보낼 통로 자체가 없다. 표시 이름은 {@link User#getDisplayName()}
 * (nickname 우선 → loginId, ADMIN=관리자)를 서버에서만 resolve해 내려준다.
 *
 * <p>흐름: 검증 → 도배 방지 → DB 저장 → 방 전체 broadcast. 저장이 먼저이고 broadcast
 * 실패가 저장을 되돌리지 않는다(재접속 시 히스토리로 복구된다).
 */
@Service
public class NewStartChatService {

  /** 메시지 본문 상한. 컬럼 길이(VARCHAR(500))와 동일하게 서버에서 검증한다. */
  static final int MAX_MESSAGE_CHARS = 500;
  /** 히스토리 기본/최대 조회 건수. 전체 기록을 한 번에 내려보내지 않는다. */
  static final int DEFAULT_HISTORY_LIMIT = 30;
  static final int MAX_HISTORY_LIMIT = 50;
  /** 재접속 구간 복구 최대 건수. */
  static final int MAX_AFTER_LIMIT = 50;
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private final NewStartChatMessageRepository messageRepository;
  private final UserRepository userRepository;
  private final NewStartChatRateLimiter rateLimiter;
  private final NewStartChatBroadcaster broadcaster;
  private final NewStartMemberProfileService memberProfileService;

  public NewStartChatService(
      NewStartChatMessageRepository messageRepository,
      UserRepository userRepository,
      NewStartChatRateLimiter rateLimiter,
      NewStartChatBroadcaster broadcaster,
      NewStartMemberProfileService memberProfileService) {
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
    this.rateLimiter = rateLimiter;
    this.broadcaster = broadcaster;
    this.memberProfileService = memberProfileService;
  }

  /** 메시지 하나를 저장하고 방 전체에 broadcast한다. 실패는 예외 없이 result-object로 보고한다. */
  @Transactional
  public NewStartChatSendResult send(
      JwtTokenProvider.AuthenticatedPrincipal principal, String rawMessage) {
    String message = rawMessage == null ? "" : rawMessage.strip();
    if (message.isEmpty()) {
      return NewStartChatSendResult.failed("메시지를 입력해 주세요.");
    }
    if (message.length() > MAX_MESSAGE_CHARS) {
      return NewStartChatSendResult.failed("메시지가 너무 깁니다. " + MAX_MESSAGE_CHARS + "자 이내로 입력해 주세요.");
    }
    if (!rateLimiter.tryAcquire(principal.userId())) {
      return NewStartChatSendResult.failed("너무 빠르게 연속 전송하고 있습니다. 잠시 후 다시 시도해 주세요.");
    }
    User user = userRepository.findById(principal.userId()).orElse(null);
    if (user == null) {
      return NewStartChatSendResult.failed("회원 정보를 찾을 수 없습니다. 다시 로그인해 주세요.");
    }
    NewStartChatMessage saved = messageRepository.save(
        NewStartChatMessage.builder().userId(principal.userId()).message(message).build());
    String displayName = memberProfileService.resolveDisplayNickname(user);
    NewStartChatMessageEvent event = NewStartChatMessageEvent.created(saved, displayName);
    broadcaster.broadcast(event);
    return NewStartChatSendResult.success(toView(saved, displayName));
  }

  /** 최근 메시지 조회. {@code before}가 있으면 그보다 오래된 메시지(위로 스크롤 pagination). */
  @Transactional(readOnly = true)
  public NewStartChatHistory history(Long before, Integer limit) {
    int size = limit == null ? DEFAULT_HISTORY_LIMIT : Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
    Page<NewStartChatMessage> page = before == null
        ? messageRepository.findByDeletedFalseOrderByMessageIdDesc(PageRequest.of(0, size))
        : messageRepository.findByMessageIdLessThanAndDeletedFalseOrderByMessageIdDesc(
            before, PageRequest.of(0, size));
    Map<Long, String> names = displayNames(page.getContent());
    List<NewStartChatMessageView> views = page.getContent().stream()
        .map(message -> toView(message, names.get(message.getUserId())))
        .toList();
    return new NewStartChatHistory(views, page.hasNext());
  }

  /** 재접속 복구: {@code after}보다 새로운 메시지를 오래된 순으로(최대 50건). */
  @Transactional(readOnly = true)
  public List<NewStartChatMessageView> after(Long after) {
    List<NewStartChatMessage> messages =
        messageRepository.findTop50ByMessageIdGreaterThanAndDeletedFalseOrderByMessageIdAsc(after);
    Map<Long, String> names = displayNames(messages);
    return messages.stream()
        .map(message -> toView(message, names.get(message.getUserId())))
        .toList();
  }

  /**
   * 메시지 삭제 — 본인만(ADMIN은 기존 권한으로 타인 메시지 삭제 허용). 서버가 작성자를 검증한다.
   * 삭제는 soft delete이며 결과를 방 전체에 broadcast한다.
   */
  @Transactional
  public NewStartChatDeleteResult delete(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long messageId) {
    if (messageId == null) {
      return NewStartChatDeleteResult.failed("삭제할 메시지가 올바르지 않습니다.", false);
    }
    NewStartChatMessage message = messageRepository.findById(messageId).orElse(null);
    if (message == null || message.isDeleted()) {
      return NewStartChatDeleteResult.failed("이미 삭제되었거나 존재하지 않는 메시지입니다.", false);
    }
    boolean admin = "ADMIN".equals(principal.role());
    if (!admin && !principal.userId().equals(message.getUserId())) {
      return NewStartChatDeleteResult.failed("본인이 작성한 메시지만 삭제할 수 있습니다.", true);
    }
    message.markDeleted();
    messageRepository.save(message);
    broadcaster.broadcast(NewStartChatMessageEvent.deleted(messageId));
    return NewStartChatDeleteResult.success();
  }

  /** 표시 이름을 한 번에 resolve한다(N+1 방지). 조회 시점 기준 최신 NewStart 별명을 쓴다 —
   * 메시지 row에는 이름을 저장하지 않으므로 과거 메시지도 항상 현재 별명으로 보인다. */
  private Map<Long, String> displayNames(List<NewStartChatMessage> messages) {
    List<Long> userIds = messages.stream().map(NewStartChatMessage::getUserId).distinct().toList();
    List<User> users = userRepository.findAllById(userIds);
    return memberProfileService.resolveDisplayNicknames(users);
  }

  private NewStartChatMessageView toView(NewStartChatMessage message, String displayName) {
    return new NewStartChatMessageView(
        message.getMessageId(),
        message.getUserId(),
        displayName,
        message.getMessage(),
        message.isDeleted(),
        iso(message.getCreatedAt()));
  }

  private static String iso(LocalDateTime time) {
    return time == null ? null : time.format(ISO);
  }

  /** 전송 결과. 성공 시 {@code view}, 실패 시 {@code errorMessage}만 의미 있다. */
  public record NewStartChatSendResult(
      boolean ok, String errorMessage, NewStartChatMessageView view) {
    public static NewStartChatSendResult success(NewStartChatMessageView view) {
      return new NewStartChatSendResult(true, null, view);
    }

    public static NewStartChatSendResult failed(String errorMessage) {
      return new NewStartChatSendResult(false, errorMessage, null);
    }
  }

  /** 삭제 결과. {@code forbidden}이면 controller가 403으로 응답한다. */
  public record NewStartChatDeleteResult(boolean ok, String errorMessage, boolean forbidden) {
    public static NewStartChatDeleteResult success() {
      return new NewStartChatDeleteResult(true, null, false);
    }

    public static NewStartChatDeleteResult failed(String errorMessage, boolean forbidden) {
      return new NewStartChatDeleteResult(false, errorMessage, forbidden);
    }
  }

  /** 히스토리 페이지. {@code messages}는 화면에서 오래된 것부터(오름차순) 렌더한다. */
  public record NewStartChatHistory(List<NewStartChatMessageView> messages, boolean hasMore) {}

  /**
   * 화면용 메시지. {@code displayName}은 서버가 결정한 공개 표기(nickname 우선 → loginId,
   * ADMIN=관리자)다. 개인정보(실명·이메일·전화)는 포함하지 않는다.
   */
  public record NewStartChatMessageView(
      Long id, Long senderUserId, String displayName, String message, boolean deleted, String createdAt) {
    public NewStartChatMessageView {
      Objects.requireNonNull(id, "id");
    }
  }
}
