package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 랜딩 '/'에서 로그인 사용자가 남긴 베타 서비스 의견.
 *
 * <p>등록하면 {@link #INITIAL_STATUS}로 시작한다. 현재는 즉시 노출이며 어드민은 사후에 숨긴다.
 * 등록 횟수 제한도 두지 않으므로 지금은 노출을 막는 사전 게이트가 없다.
 */
@Entity
@Table(name = "beta_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BetaComment extends BaseEntity {

  /**
   * 등록 직후 상태.
   *
   * <p>사전 승인 없이 바로 노출하기로 한 운영 결정이다. 스팸이나 부적절한 의견이 문제가 되어
   * 사전 모더레이션이 필요해지면 이 한 줄만 {@link BetaCommentStatus#PENDING}으로 되돌리면
   * 된다 — 어드민의 승인 대기 큐와 승인 액션은 그대로 살아 있다.
   */
  private static final BetaCommentStatus INITIAL_STATUS = BetaCommentStatus.APPROVED;

  /** 랜딩 카드 아바타에 넣는 별명 글자수. 38px 원형 안에 11px 글씨가 넘치지 않는 상한. */
  private static final int AVATAR_LENGTH = 3;

  public static final int NICKNAME_MAX_LENGTH = 12;
  public static final int COMMENT_MAX_LENGTH = 80;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /**
   * 작성 시점의 별명 스냅샷.
   *
   * <p>폼이 별명을 따로 받으므로 {@code users.nickname}과 별개다. 이후 회원정보 변경에 연동하지
   * 않는다 — 승인 시점에 어드민이 본 문구가 그대로 노출되어야 한다.
   */
  @Column(name = "nickname", nullable = false, length = NICKNAME_MAX_LENGTH)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "participant_type", nullable = false, length = 20)
  private BetaParticipantType participantType;

  @Column(name = "comment", nullable = false, length = COMMENT_MAX_LENGTH)
  private String comment;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BetaCommentStatus status = INITIAL_STATUS;

  /** null이면 카드에 뱃지를 렌더하지 않는다. */
  @Enumerated(EnumType.STRING)
  @Column(name = "highlight_label", length = 20)
  private BetaCommentHighlight highlightLabel;

  private BetaComment(
      Long userId, String nickname, BetaParticipantType participantType, String comment) {
    this.userId = userId;
    this.nickname = nickname;
    this.participantType = participantType;
    this.comment = comment;
    this.status = INITIAL_STATUS;
  }

  public static BetaComment register(
      Long userId, String nickname, BetaParticipantType participantType, String comment) {
    if (userId == null) {
      throw new IllegalArgumentException("userId는 필수입니다.");
    }
    return new BetaComment(
        userId, requireNickname(nickname), requireParticipantType(participantType),
        requireComment(comment));
  }

  public void updateStatus(BetaCommentStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("status는 필수입니다.");
    }
    if (status == BetaCommentStatus.PENDING) {
      throw new IllegalArgumentException("승인 대기 상태로는 되돌릴 수 없습니다.");
    }
    this.status = status;
  }

  /** null을 넘기면 뱃지를 해제한다. */
  public void updateHighlightLabel(BetaCommentHighlight highlightLabel) {
    this.highlightLabel = highlightLabel;
  }

  /** 랜딩 카드 아바타 문구. 별명 앞 3자까지만 쓴다. */
  public String avatarText() {
    return nickname.length() <= AVATAR_LENGTH ? nickname : nickname.substring(0, AVATAR_LENGTH);
  }

  private static String requireNickname(String nickname) {
    String trimmed = nickname == null ? "" : nickname.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("이름 또는 별명을 입력해 주세요.");
    }
    if (trimmed.length() > NICKNAME_MAX_LENGTH) {
      throw new IllegalArgumentException(
          "이름 또는 별명은 " + NICKNAME_MAX_LENGTH + "자 이하로 입력해 주세요.");
    }
    return trimmed;
  }

  private static String requireComment(String comment) {
    String trimmed = comment == null ? "" : comment.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("의견을 입력해 주세요.");
    }
    if (trimmed.length() > COMMENT_MAX_LENGTH) {
      throw new IllegalArgumentException("의견은 " + COMMENT_MAX_LENGTH + "자 이하로 입력해 주세요.");
    }
    return trimmed;
  }

  private static BetaParticipantType requireParticipantType(BetaParticipantType participantType) {
    if (participantType == null) {
      throw new IllegalArgumentException("참여자 유형을 선택해 주세요.");
    }
    return participantType;
  }
}
