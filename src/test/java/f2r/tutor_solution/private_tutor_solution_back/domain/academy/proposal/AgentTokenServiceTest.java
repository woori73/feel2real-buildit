package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("AgentTokenService — 발급·인증·폐기")
class AgentTokenServiceTest {

  private AgentTokenRepository repo;
  private AgentTokenService service;

  @BeforeEach
  void setUp() {
    repo = mock(AgentTokenRepository.class);
    service = new AgentTokenService(repo);
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("given 발급 when 저장 then DB에는 SHA-256 해시만 저장되고 평문은 응답에만 있다")
  void issue_storesOnlyHash() {
    when(repo.countByAcademyIdAndRevokedAtIsNullAndExpiresAtAfter(anyLong(), any())).thenReturn(0L);

    AgentTokenService.IssuedToken issued = service.issue(7L, 1L, "my agent");

    ArgumentCaptor<AgentToken> saved = ArgumentCaptor.forClass(AgentToken.class);
    Mockito.verify(repo).save(saved.capture());
    assertThat(issued.token()).startsWith("vea_").hasSizeGreaterThan(40);
    assertThat(saved.getValue().getTokenHash())
        .isEqualTo(AgentTokenService.sha256(issued.token())).hasSize(64)
        .doesNotContain(issued.token());
    assertThat(saved.getValue().getAcademyId()).isEqualTo(7L);
    assertThat(saved.getValue().hasScope("ve:propose")).isTrue();
    assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusDays(80));
  }

  @Test
  @DisplayName("given 활성 토큰 5개 when 발급 then 409")
  void issue_limit() {
    when(repo.countByAcademyIdAndRevokedAtIsNullAndExpiresAtAfter(anyLong(), any())).thenReturn(5L);

    assertThatThrownBy(() -> service.issue(7L, 1L, "x")).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("given 빈/긴 label when 발급 then 거부")
  void issue_label() {
    assertThatThrownBy(() -> service.issue(7L, 1L, " ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issue(7L, 1L, "x".repeat(101)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private AgentToken token(String plain, LocalDateTime expires, boolean revoked) {
    AgentToken t = new AgentToken(7L, AgentTokenService.sha256(plain), "l", "ve:read", 1L, expires);
    if (revoked) {
      t.revoke(LocalDateTime.now());
    }
    return t;
  }

  @Test
  @DisplayName("given 유효 토큰 when 인증 then 학원에 바인딩된 신원")
  void authenticate_ok() {
    String plain = "vea_abc";
    when(repo.findByTokenHash(AgentTokenService.sha256(plain)))
        .thenReturn(Optional.of(token(plain, LocalDateTime.now().plusDays(1), false)));

    var p = service.authenticate(plain);

    assertThat(p).isPresent();
    assertThat(p.get().academyId()).isEqualTo(7L);
    assertThat(p.get().hasScope("ve:read")).isTrue();
    assertThat(p.get().hasScope("ve:propose")).isFalse();
  }

  @Test
  @DisplayName("given 없음·폐기·만료·형식 오류·null when 인증 then 모두 empty")
  void authenticate_failClosed() {
    String revoked = "vea_revoked";
    String expired = "vea_expired";
    when(repo.findByTokenHash(AgentTokenService.sha256(revoked)))
        .thenReturn(Optional.of(token(revoked, LocalDateTime.now().plusDays(1), true)));
    when(repo.findByTokenHash(AgentTokenService.sha256(expired)))
        .thenReturn(Optional.of(token(expired, LocalDateTime.now().minusSeconds(1), false)));
    when(repo.findByTokenHash(Mockito.argThat(h -> !h.equals(AgentTokenService.sha256(revoked))
        && !h.equals(AgentTokenService.sha256(expired))))).thenReturn(Optional.empty());

    assertThat(service.authenticate(revoked)).isEmpty();
    assertThat(service.authenticate(expired)).isEmpty();
    assertThat(service.authenticate("vea_unknown")).isEmpty();
    assertThat(service.authenticate("Bearer vea_x")).isEmpty();
    assertThat(service.authenticate("other_token")).isEmpty();
    assertThat(service.authenticate(null)).isEmpty();
    assertThat(service.authenticate("")).isEmpty();
    assertThat(service.authenticate("vea_" + "a".repeat(300))).isEmpty();
  }

  @Test
  @DisplayName("given 다른 학원 토큰 id when 폐기 then 없는 것과 같은 404")
  void revoke_otherAcademy() {
    when(repo.findByTokenIdAndAcademyId(5L, 8L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revoke(8L, 5L)).isInstanceOf(ResponseStatusException.class);
  }
}
