package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentAdminResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCardView;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCarousel;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentHighlightOption;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentModerationView;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaComment;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentHighlight;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.repository.BetaCommentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 랜딩 베타 의견 등록·모더레이션·캐러셀 렌더.
 *
 * <p>더미/실데이터 전환은 승인 건수에서 파생시키며 수동 표시모드 설정은 두지 않는다 — 설정값과 실제
 * 데이터가 어긋나는 상태를 만들지 않기 위함이다.
 *
 * <p>등록은 사전 승인 없이 바로 노출된다({@code BetaComment.INITIAL_STATUS}). 어드민은 사후에
 * 숨기는 역할이며, 사전 모더레이션이 필요해지면 그 상수만 되돌리면 승인 대기 흐름이 되살아난다.
 */
@Service
@RequiredArgsConstructor
public class BetaCommentService {

  /** 캐러셀 노출 상한. 최신순으로 자른다. */
  public static final int CAROUSEL_MAX_CARDS = 10;

  /**
   * 자동 스크롤을 켜는 최소 카드 수.
   *
   * <p>{@code .feedback-card}가 고정 310px이라 4장(1240px + gap)이어도 넓은 화면에서 트랙이
   * {@code .feedback-window}보다 좁아져 빈 구간을 스크롤한다. 미달 구간은 정적 가운데 정렬로 렌더한다.
   */
  public static final int ANIMATION_MIN_CARDS = 5;

  /**
   * 승인 의견이 0건일 때만 노출하는 자리표시 카드.
   *
   * <p>실제 참여자 발언이 아니므로 승인이 1건이라도 생기면 즉시 사라지고 더미와 실데이터를 섞지
   * 않는다. 5장인 것은 {@link #ANIMATION_MIN_CARDS}를 충족시켜 첫 화면에서도 캐러셀이 움직이게
   * 하기 위함이다.
   */
  private static final List<BetaCommentCardView> DUMMY_CARDS = List.of(
      BetaCommentCardView.dummy(
          "김원장", "김○○ 원장", "중등 영어학원", "반영 중",
          "“홈페이지를 직접 만들기 어려웠는데, 학원 정보만 입력해도 기본 화면이 완성되어 편했습니다.”"),
      BetaCommentCardView.dummy(
          "박강사", "박○○ 강사", "1인 교습소", "신규",
          "“수업 영상 몇 개만 올리면 홍보용 쇼츠가 만들어지는 기능을 가장 기대하고 있습니다.”"),
      BetaCommentCardView.dummy(
          "이원장", "이○○ 원장", "초·중등 보습학원", "반영 완료",
          "“학부모에게 매주 보내는 내용을 리포트로 자동 정리할 수 있으면 상담 시간이 크게 줄 것 같습니다.”"),
      BetaCommentCardView.dummy(
          "최원장", "최○○ 원장", "고등 전문학원", "검토 중",
          "“복잡한 기능보다 지금처럼 꼭 필요한 기능부터 하나씩 제공하는 방식이 좋습니다.”"),
      BetaCommentCardView.dummy(
          "정강사", "정○○ 강사", "영어 공부방", "신규",
          "“휴대폰에서도 학원 소개와 상담 신청 화면이 깔끔하게 보여서 활용하기 좋았습니다.”"));

  private final BetaCommentRepository repository;

  /** 랜딩 SSR용. 더미 여부·애니메이션 여부·복제본 생성을 여기서 끝낸다. */
  @Transactional(readOnly = true)
  public BetaCommentCarousel renderCarousel() {
    List<BetaComment> approved =
        repository.findTop10ByStatusOrderByCreatedAtDesc(BetaCommentStatus.APPROVED);
    List<BetaCommentCardView> base =
        approved.isEmpty()
            ? DUMMY_CARDS
            : approved.stream().map(BetaCommentCardView::from).toList();
    return BetaCommentCarousel.of(base, base.size() >= ANIMATION_MIN_CARDS);
  }

  @Transactional
  public void register(Long userId, BetaCommentCreateRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("요청 본문이 비어 있습니다.");
    }
    repository.save(
        BetaComment.register(
            userId, request.nickname(), request.participantType(), request.comment()));
  }

  @Transactional(readOnly = true)
  public BetaCommentModerationView loadModerationView() {
    return new BetaCommentModerationView(
        listByStatus(BetaCommentStatus.PENDING),
        listByStatus(BetaCommentStatus.APPROVED),
        listByStatus(BetaCommentStatus.HIDDEN),
        repository.countByStatus(BetaCommentStatus.APPROVED),
        CAROUSEL_MAX_CARDS,
        ANIMATION_MIN_CARDS,
        BetaCommentHighlightOption.all());
  }

  @Transactional
  public BetaCommentAdminResponse updateStatus(Long id, BetaCommentStatus status) {
    BetaComment comment = find(id);
    comment.updateStatus(status);
    return BetaCommentAdminResponse.from(comment);
  }

  /** {@code highlightLabel}이 null이면 뱃지를 해제한다. */
  @Transactional
  public BetaCommentAdminResponse updateHighlight(Long id, BetaCommentHighlight highlightLabel) {
    BetaComment comment = find(id);
    comment.updateHighlightLabel(highlightLabel);
    return BetaCommentAdminResponse.from(comment);
  }

  private List<BetaCommentAdminResponse> listByStatus(BetaCommentStatus status) {
    return repository.findAllByStatusOrderByCreatedAtDesc(status).stream()
        .map(BetaCommentAdminResponse::from)
        .toList();
  }

  private BetaComment find(Long id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "베타 의견을 찾을 수 없습니다."));
  }
}
