package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 시각 편집기 Agent 토큰 발급·인증·폐기.
 *
 * <p>평문 토큰은 발급 응답에서 한 번만 보이며 DB에는 SHA-256 해시만 저장한다. 토큰은 학원 1개에 바인딩되고
 * scope(읽기/제안)가 제한된다. 인증 실패 사유(없음/폐기/만료)는 호출자에게 구분해 알리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AgentTokenService {

  public static final String PREFIX = "vea_";
  public static final int MAX_ACTIVE_PER_ACADEMY = 5;
  public static final int TTL_DAYS = 90;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AgentTokenRepository repository;

  /** 인증된 Agent의 신원. */
  public record AgentPrincipal(Long tokenId, Long academyId, String scopes) {
    public boolean hasScope(String scope) {
      for (String s : scopes.split(",")) {
        if (s.trim().equals(scope)) {
          return true;
        }
      }
      return false;
    }
  }

  /** 발급 결과. {@code token}(평문)은 이 응답에서만 노출된다. */
  public record IssuedToken(Long tokenId, String token, String label, String scopes,
      LocalDateTime expiresAt) {
  }

  /** 목록용(비밀 없음). */
  public record TokenView(Long tokenId, String label, String scopes, LocalDateTime expiresAt,
      LocalDateTime lastUsedAt, LocalDateTime createdAt, boolean active) {
  }

  @Transactional
  public IssuedToken issue(Long academyId, Long userId, String label) {
    String cleanLabel = label == null ? "" : label.trim();
    if (cleanLabel.isEmpty() || cleanLabel.length() > 100) {
      throw new IllegalArgumentException("label은 1~100자여야 합니다.");
    }
    LocalDateTime now = LocalDateTime.now();
    if (repository.countByAcademyIdAndRevokedAtIsNullAndExpiresAtAfter(academyId, now)
        >= MAX_ACTIVE_PER_ACADEMY) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "사용 중인 Agent 토큰은 최대 " + MAX_ACTIVE_PER_ACADEMY + "개입니다. 불필요한 토큰을 폐기하세요.");
    }
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    String scopes = AgentToken.SCOPE_READ + "," + AgentToken.SCOPE_PROPOSE;
    AgentToken saved = repository.save(new AgentToken(
        academyId, sha256(token), cleanLabel, scopes, userId, now.plusDays(TTL_DAYS)));
    return new IssuedToken(saved.getTokenId(), token, cleanLabel, scopes, saved.getExpiresAt());
  }

  /** 유효한 토큰이면 신원을 돌려준다. 그 외는 모두 empty(fail-closed). */
  @Transactional
  public Optional<AgentPrincipal> authenticate(String presented) {
    if (presented == null || !presented.startsWith(PREFIX) || presented.length() > 200) {
      return Optional.empty();
    }
    LocalDateTime now = LocalDateTime.now();
    return repository.findByTokenHash(sha256(presented))
        .filter(t -> t.isActive(now))
        .map(t -> {
          // 요청마다 쓰기를 만들지 않도록 1분 단위로만 갱신한다.
          if (t.getLastUsedAt() == null || t.getLastUsedAt().isBefore(now.minusMinutes(1))) {
            t.touch(now);
          }
          return new AgentPrincipal(t.getTokenId(), t.getAcademyId(), t.getScopes());
        });
  }

  @Transactional(readOnly = true)
  public List<TokenView> list(Long academyId) {
    LocalDateTime now = LocalDateTime.now();
    return repository.findByAcademyIdOrderByCreatedAtDesc(academyId).stream()
        .map(t -> new TokenView(t.getTokenId(), t.getLabel(), t.getScopes(), t.getExpiresAt(),
            t.getLastUsedAt(), t.getCreatedAt(), t.isActive(now)))
        .toList();
  }

  /** 다른 학원의 토큰이면 없는 것과 같은 404. */
  @Transactional
  public void revoke(Long academyId, Long tokenId) {
    AgentToken token = repository.findByTokenIdAndAcademyId(tokenId, academyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "토큰을 찾을 수 없습니다."));
    token.revoke(LocalDateTime.now());
  }

  static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
