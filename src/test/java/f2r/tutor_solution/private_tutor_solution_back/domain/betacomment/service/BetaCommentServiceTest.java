package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCardView;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCarousel;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaComment;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentHighlight;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaParticipantType;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.repository.BetaCommentRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BetaCommentServiceTest {

  @Mock BetaCommentRepository repository;
  @InjectMocks BetaCommentService service;

  // ─── 캐러셀 렌더 분기 ─────────────────────────────────────────────────
  // 승인 건수 경계(0 / 1 / 4 / 5)마다 더미 여부·애니메이션 여부·복제본 수가 달라진다.

  @Test
  @DisplayName("given 승인 의견이 없을 때 when 캐러셀을 렌더하면 then 더미 5장을 복제해 애니메이션을 켠다")
  void carousel_falls_back_to_dummy_when_nothing_approved() {
    givenApproved(List.of());

    BetaCommentCarousel carousel = service.renderCarousel();

    assertThat(carousel.animated()).isTrue();
    assertThat(carousel.cards()).hasSize(10);
    assertThat(carousel.cards()).filteredOn(BetaCommentCardView::decorative).hasSize(5);
    // 더미는 실제 참여자 발언이 아니므로 마스킹된 자리표시 문구여야 한다.
    assertThat(carousel.cards().get(0).nickname()).isEqualTo("김○○ 원장");
  }

  @Test
  @DisplayName("given 승인 의견이 1건일 때 when 캐러셀을 렌더하면 then 더미를 섞지 않고 애니메이션을 끈다")
  void carousel_uses_real_data_without_animation_for_single_approval() {
    givenApproved(List.of(approved("민지", "첫 의견입니다.")));

    BetaCommentCarousel carousel = service.renderCarousel();

    assertThat(carousel.animated()).isFalse();
    assertThat(carousel.cards()).hasSize(1);
    assertThat(carousel.cards().get(0).nickname()).isEqualTo("민지");
    assertThat(carousel.cards()).noneMatch(BetaCommentCardView::decorative);
  }

  @Test
  @DisplayName("given 승인 의견이 하한 직전일 때 when 캐러셀을 렌더하면 then 복제 없이 정적으로 렌더한다")
  void carousel_stays_static_just_below_animation_threshold() {
    givenApproved(approvedList(BetaCommentService.ANIMATION_MIN_CARDS - 1));

    BetaCommentCarousel carousel = service.renderCarousel();

    assertThat(carousel.animated()).isFalse();
    assertThat(carousel.cards()).hasSize(BetaCommentService.ANIMATION_MIN_CARDS - 1);
    assertThat(carousel.cards()).noneMatch(BetaCommentCardView::decorative);
  }

  @Test
  @DisplayName("given 승인 의견이 하한에 닿을 때 when 캐러셀을 렌더하면 then 원본과 같은 수의 복제본을 붙인다")
  void carousel_duplicates_exactly_once_at_threshold() {
    int size = BetaCommentService.ANIMATION_MIN_CARDS;
    givenApproved(approvedList(size));

    BetaCommentCarousel carousel = service.renderCarousel();

    // translateX(-50%)가 성립하려면 트랙이 정확히 동일한 두 절반이어야 한다.
    assertThat(carousel.animated()).isTrue();
    assertThat(carousel.cards()).hasSize(size * 2);
    assertThat(carousel.cards().subList(0, size)).noneMatch(BetaCommentCardView::decorative);
    assertThat(carousel.cards().subList(size, size * 2))
        .allMatch(BetaCommentCardView::decorative);
  }

  @Test
  @DisplayName("given 승인 의견이 상한을 넘을 때 when 캐러셀을 렌더하면 then 조회 자체를 최신 10건으로 제한한다")
  void carousel_caps_cards_at_repository_limit() {
    // 상한은 findTop10...이 담당하므로 서비스는 받은 만큼만 복제한다.
    givenApproved(approvedList(BetaCommentService.CAROUSEL_MAX_CARDS));

    BetaCommentCarousel carousel = service.renderCarousel();

    assertThat(carousel.cards()).hasSize(BetaCommentService.CAROUSEL_MAX_CARDS * 2);
  }

  @Test
  @DisplayName("given 뱃지가 없는 승인 의견 when 카드로 변환하면 then highlightLabel이 null로 남아 뱃지를 렌더하지 않는다")
  void card_keeps_highlight_null_when_unassigned() {
    BetaComment comment = approved("민지", "뱃지 없는 의견");
    givenApproved(List.of(comment));

    assertThat(service.renderCarousel().cards().get(0).highlightLabel()).isNull();

    comment.updateHighlightLabel(BetaCommentHighlight.APPLYING);
    assertThat(service.renderCarousel().cards().get(0).highlightLabel()).isEqualTo("반영 중");
  }

  @Test
  @DisplayName("given 12자 별명 when 카드로 변환하면 then 아바타는 앞 3자만 쓴다")
  void card_truncates_avatar_text() {
    // 38px 원형 안에 11px 글씨가 넘치지 않는 상한이 3자다.
    givenApproved(List.of(approved("가나다라마바사아자차카타", "긴 별명 의견")));

    assertThat(service.renderCarousel().cards().get(0).avatarText()).isEqualTo("가나다");
  }

  // ─── 등록 검증 ────────────────────────────────────────────────────────

  @Test
  @DisplayName("given 신규 등록 when 저장하면 then 사전 승인 없이 APPROVED로 바로 노출된다")
  void register_starts_approved_for_immediate_exposure() {
    when(repository.save(any(BetaComment.class))).thenAnswer(call -> call.getArgument(0));

    service.register(7L, new BetaCommentCreateRequest("민지", BetaParticipantType.STUDY_ROOM, "좋아요"));

    // 사전 모더레이션을 켜려면 BetaComment.INITIAL_STATUS만 PENDING으로 되돌리면 되고,
    // 그 결정이 바뀌었는지를 이 단정이 드러낸다.
    ArgumentCaptor<BetaComment> captor = ArgumentCaptor.forClass(BetaComment.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(BetaCommentStatus.APPROVED);
    assertThat(captor.getValue().getUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("given 등록 직후 의견 when 캐러셀을 렌더하면 then 승인 절차 없이 더미를 밀어낸다")
  void register_immediately_replaces_dummy_cards() {
    BetaComment registered =
        BetaComment.register(1L, "민지", BetaParticipantType.STUDY_ROOM, "방금 남긴 의견");
    givenApproved(List.of(registered));

    BetaCommentCarousel carousel = service.renderCarousel();

    assertThat(carousel.cards()).hasSize(1);
    assertThat(carousel.cards().get(0).nickname()).isEqualTo("민지");
  }

  @Test
  @DisplayName("given 상한을 넘는 입력 when 등록하면 then 클라이언트 maxlength와 무관하게 서버가 거절한다")
  void register_rejects_over_length_input() {
    assertThatThrownBy(
            () ->
                service.register(
                    1L,
                    new BetaCommentCreateRequest(
                        "민지", BetaParticipantType.OTHER, "가".repeat(81))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("80자");

    assertThatThrownBy(
            () ->
                service.register(
                    1L,
                    new BetaCommentCreateRequest(
                        "가".repeat(13), BetaParticipantType.OTHER, "정상 의견")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("12자");
  }

  @Test
  @DisplayName("given 공백만 입력 when 등록하면 then 필수값 누락으로 거절한다")
  void register_rejects_blank_input() {
    assertThatThrownBy(
            () -> service.register(1L, new BetaCommentCreateRequest("  ", BetaParticipantType.OTHER, "의견")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () -> service.register(1L, new BetaCommentCreateRequest("민지", BetaParticipantType.OTHER, "   ")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> service.register(1L, new BetaCommentCreateRequest("민지", null, "의견")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ─── 상태 전이 ────────────────────────────────────────────────────────

  @Test
  @DisplayName("given 승인 대기 의견 when 상태를 되돌리려 하면 then PENDING 복귀를 막는다")
  void status_cannot_return_to_pending() {
    BetaComment comment = approved("민지", "의견");

    assertThatThrownBy(() -> comment.updateStatus(BetaCommentStatus.PENDING))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 빈 카드 목록 when 캐러셀을 만들면 then 빈 트랙 렌더를 막는다")
  void carousel_rejects_empty_cards() {
    assertThatThrownBy(() -> BetaCommentCarousel.of(List.of(), false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ─── fixtures ─────────────────────────────────────────────────────────

  private void givenApproved(List<BetaComment> approved) {
    when(repository.findTop10ByStatusOrderByCreatedAtDesc(BetaCommentStatus.APPROVED))
        .thenReturn(approved);
  }

  private List<BetaComment> approvedList(int size) {
    return IntStream.range(0, size)
        .mapToObj(index -> approved("작성자" + index, "의견 " + index))
        .toList();
  }

  private BetaComment approved(String nickname, String comment) {
    BetaComment created =
        BetaComment.register(1L, nickname, BetaParticipantType.ACADEMY_PRINCIPAL, comment);
    created.updateStatus(BetaCommentStatus.APPROVED);
    return created;
  }
}
