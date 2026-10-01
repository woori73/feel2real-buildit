package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.DmMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartDirectMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartMemberPresence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository.NewStartDirectMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository.NewStartMemberPresenceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 사이트 내부 1:1 쪽지. 네이버 카페 쪽지와 무관하며, RAG·AI 학습에
 * 사용하지 않는다(채팅과 동일 취급).
 *
 * <p>발신자·수신자는 서버 principal과 존재하는 회원({@code users.user_id})으로만
 * 결정한다. 수신 거부({@code dm_blocked}) 상대에게는 전송하지 않는다.
 * 쪽지 삭제·수정 endpoint는 두지 않는다(create + 읽음 표시만).
 */
@Service
public class NewStartDirectMessageService {

  /** 쪽지 본문 상한. 컬럼 길이(VARCHAR(1000))와 동일하게 서버에서 검증한다. */
  static final int MAX_MESSAGE_CHARS = 1000;
  static final int DEFAULT_INBOX_LIMIT = 20;
  static final int MAX_INBOX_LIMIT = 50;

  private final NewStartDirectMessageRepository messageRepository;
  private final NewStartMemberPresenceRepository presenceRepository;
  private final UserRepository userRepository;
  private final NewStartMemberProfileService memberProfileService;
  private final NewStartDmRateLimiter rateLimiter;

  public NewStartDirectMessageService(
      NewStartDirectMessageRepository messageRepository,
      NewStartMemberPresenceRepository presenceRepository,
      UserRepository userRepository,
      NewStartMemberProfileService memberProfileService,
      NewStartDmRateLimiter rateLimiter) {
    this.messageRepository = messageRepository;
    this.presenceRepository = presenceRepository;
    this.userRepository = userRepository;
    this.memberProfileService = memberProfileService;
    this.rateLimiter = rateLimiter;
  }

  /** 쪽지 전송. 수신 거부·본인 전송·도배는 거부한다. */
  @Transactional
  public DmMessageView send(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long receiverUserId, String rawMessage) {
    String message = rawMessage == null ? "" : rawMessage.strip();
    if (message.isEmpty()) {
      throw new IllegalArgumentException("쪽지 내용을 입력해 주세요.");
    }
    if (message.length() > MAX_MESSAGE_CHARS) {
      throw new IllegalArgumentException(
          "쪽지가 너무 깁니다. " + MAX_MESSAGE_CHARS + "자 이내로 입력해 주세요.");
    }
    if (receiverUserId == null || receiverUserId.equals(principal.userId())) {
      throw new IllegalArgumentException("받는 회원을 확인해 주세요.");
    }
    User receiver = userRepository.findById(receiverUserId)
        .orElseThrow(() -> new IllegalArgumentException("받는 회원을 찾을 수 없습니다."));
    boolean blocked = presenceRepository.findById(receiverUserId)
        .map(row -> Boolean.TRUE.equals(row.getDmBlocked()))
        .orElse(false);
    if (blocked) {
      throw new IllegalArgumentException("상대방이 쪽지 수신을 거부했습니다.");
    }
    if (!rateLimiter.tryAcquire(principal.userId())) {
      throw new IllegalStateException("너무 빠르게 연속 전송하고 있습니다. 잠시 후 다시 시도해 주세요.");
    }
    User sender = userRepository.findById(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다. 다시 로그인해 주세요."));
    NewStartDirectMessage saved = messageRepository.save(NewStartDirectMessage.builder()
        .senderUserId(principal.userId())
        .receiverUserId(receiverUserId)
        .message(message)
        .build());
    Map<Long, String> nicknames =
        memberProfileService.resolveDisplayNicknames(List.of(sender, receiver));
    return toView(saved,
        nicknames.getOrDefault(sender.getUserId(), sender.getDisplayName()),
        nicknames.getOrDefault(receiver.getUserId(), receiver.getDisplayName()));
  }

  /** 받은 쪽지함(최신순). {@code before}가 있으면 그보다 오래된 쪽지부터. */
  @Transactional(readOnly = true)
  public List<DmMessageView> inbox(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long before, Integer limit) {
    int size = limit == null ? DEFAULT_INBOX_LIMIT : Math.min(Math.max(limit, 1), MAX_INBOX_LIMIT);
    Page<NewStartDirectMessage> page = before == null
        ? messageRepository.findByReceiverUserIdOrderByMessageIdDesc(
            principal.userId(), PageRequest.of(0, size))
        : messageRepository.findByReceiverUserIdAndMessageIdLessThanOrderByMessageIdDesc(
            principal.userId(), before, PageRequest.of(0, size));
    List<NewStartDirectMessage> rows = page.getContent();
    if (rows.isEmpty()) {
      return List.of();
    }
    List<Long> userIds = new ArrayList<>();
    rows.forEach(row -> {
      userIds.add(row.getSenderUserId());
      userIds.add(row.getReceiverUserId());
    });
    List<User> users = userRepository.findAllById(userIds.stream().distinct().toList());
    Map<Long, String> nicknames = memberProfileService.resolveDisplayNicknames(users);
    Map<Long, User> usersById = users.stream()
        .collect(java.util.stream.Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));
    return rows.stream()
        .map(row -> toView(row,
            displayOf(usersById.get(row.getSenderUserId()), nicknames),
            displayOf(usersById.get(row.getReceiverUserId()), nicknames)))
        .toList();
  }

  /** 읽지 않은 받은 쪽지 수. */
  @Transactional(readOnly = true)
  public long unreadCount(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return messageRepository.countByReceiverUserIdAndIsReadFalse(principal.userId());
  }

  /** 읽음 표시. 수신자 본인만 가능하며 반복 호출해도 된다. */
  @Transactional
  public void markRead(JwtTokenProvider.AuthenticatedPrincipal principal, Long messageId) {
    NewStartDirectMessage row = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("쪽지를 찾을 수 없습니다."));
    if (!row.getReceiverUserId().equals(principal.userId())) {
      throw new IllegalArgumentException("본인이 받은 쪽지가 아닙니다.");
    }
    if (!Boolean.TRUE.equals(row.getIsRead())) {
      row.setIsRead(true);
      messageRepository.save(row);
    }
  }

  private String displayOf(User user, Map<Long, String> nicknames) {
    if (user == null) {
      return "알 수 없는 회원";
    }
    return nicknames.getOrDefault(user.getUserId(), user.getDisplayName());
  }

  private DmMessageView toView(
      NewStartDirectMessage row, String senderNickname, String receiverNickname) {
    return new DmMessageView(
        row.getMessageId(),
        row.getSenderUserId(),
        senderNickname,
        row.getReceiverUserId(),
        receiverNickname,
        row.getMessage(),
        Boolean.TRUE.equals(row.getIsRead()),
        row.getCreatedAt());
  }
}
