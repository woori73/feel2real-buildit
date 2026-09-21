package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberProfile;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberProfileRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 전용 표시 별명 resolve/변경.
 *
 * <p>표시 우선순위: (1) 사용자가 NewStart에서 직접 정한 별명({@code newstart_member_profiles})
 * → (2) 최초 NAVER 로그인 시점의 NAVER 프로필 nickname으로 1회 seed된 값(같은 테이블, 사용자가
 * 아직 안 바꿨다면 이 값 그대로) → (3) 그마저 없으면(row 자체가 없는 레거시 회원 등)
 * {@link User#getDisplayName()}(기존 nickname 우선 → loginId, ADMIN=관리자 — 실명은 여기서도
 * 절대 쓰지 않는다).
 *
 * <p>별명은 인증/식별 key가 아니다 — canonical identity는 항상 {@code users.user_id}다.
 * 그래서 별명 자체에는 unique 제약을 두지 않는다(기존 {@code users.nickname} 정책과 동일,
 * §3 조사 결과: 중복 닉네임 허용이 기존 서비스 정책).
 */
@Service
public class NewStartMemberProfileService {

  /** UserService.requireValidNickname과 동일한 정책(한글/영문/숫자/공백/_/- 2~20자) — 위험한
   * HTML/script 문자는 이 화이트리스트 자체에 없어 구조적으로 걸러진다. */
  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9 _-]{2,20}$");
  private static final int NICKNAME_MAX_LENGTH = 20;
  /** NAVER 프로필에 nickname이 전혀 없을 때 쓰는 안전한 자동 생성 별명(실명 사용 안 함, PII 없음). */
  private static final String SAFE_FALLBACK_PREFIX = "새출발러";

  private final NewStartMemberProfileRepository profileRepository;
  private final UserRepository userRepository;

  public NewStartMemberProfileService(
      NewStartMemberProfileRepository profileRepository, UserRepository userRepository) {
    this.profileRepository = profileRepository;
    this.userRepository = userRepository;
  }

  /** 채팅 등 화면에 보여줄 현재 표시 별명 하나. */
  @Transactional(readOnly = true)
  public String resolveDisplayNickname(User user) {
    return profileRepository.findById(user.getUserId())
        .map(NewStartMemberProfile::getDisplayNickname)
        .orElseGet(user::getDisplayName);
  }

  /** 채팅 히스토리처럼 여러 명을 한 번에 표시할 때 N+1을 피하기 위한 배치 조회. */
  @Transactional(readOnly = true)
  public Map<Long, String> resolveDisplayNicknames(List<User> users) {
    List<Long> userIds = users.stream().map(User::getUserId).distinct().toList();
    Map<Long, String> seeded = profileRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(
            NewStartMemberProfile::getUserId, NewStartMemberProfile::getDisplayNickname));
    return users.stream()
        .collect(Collectors.toMap(
            User::getUserId,
            u -> seeded.getOrDefault(u.getUserId(), u.getDisplayName()),
            (a, b) -> a));
  }

  /**
   * NAVER 로그인 성공마다 호출된다(신규/기존 회원 공통) — 이미 row가 있으면(사용자가 이미
   * 설정했거나 예전에 seed됐으면) 아무것도 하지 않는다. 최초 1회에 한해 그 순간의 NAVER
   * 프로필 nickname으로 기본값을 채운다(§8 흐름 — 로그인 자체를 막지 않는다).
   */
  @Transactional
  public void ensureSeeded(Long userId, String naverNickname) {
    if (profileRepository.existsById(userId)) {
      return;
    }
    String initial = normalizeForSeed(naverNickname, userId);
    profileRepository.save(
        NewStartMemberProfile.builder().userId(userId).displayNickname(initial).build());
  }

  /** 프로필 화면에서 사용자가 직접 별명을 바꾼다. 타인 userId 조작은 principal에서만 결정되므로
   * 호출자(controller)가 인증된 본인 userId만 넘긴다는 전제다. */
  @Transactional
  public String updateNickname(Long userId, String rawNickname) {
    String normalized = requireValidNickname(rawNickname);
    NewStartMemberProfile profile = profileRepository.findById(userId)
        .orElseGet(() -> NewStartMemberProfile.builder().userId(userId).displayNickname(normalized).build());
    profile.updateDisplayNickname(normalized);
    profileRepository.save(profile);
    return normalized;
  }

  private String normalizeForSeed(String naverNickname, Long userId) {
    if (naverNickname != null) {
      String trimmed = naverNickname.trim();
      if (!trimmed.isEmpty() && NICKNAME_PATTERN.matcher(trimmed).matches()) {
        return trimmed.length() > NICKNAME_MAX_LENGTH ? trimmed.substring(0, NICKNAME_MAX_LENGTH) : trimmed;
      }
    }
    return safeGeneratedNickname(userId);
  }

  /** users.user_id(순번일 뿐 실명/이메일 등 개인정보 아님) 기반 결정적 접미사로 서로 구별되게
   * 한다 — 그래도 별명 자체의 유일성을 강제하진 않는다(§3, unique 제약 없음). */
  private String safeGeneratedNickname(Long userId) {
    long suffix = userId == null ? 0 : Math.floorMod(userId, 10_000L);
    return SAFE_FALLBACK_PREFIX + String.format("%04d", suffix);
  }

  private String requireValidNickname(String nickname) {
    if (nickname == null) {
      throw new IllegalArgumentException("별명을 입력해 주세요.");
    }
    String normalized = nickname.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("별명을 입력해 주세요.");
    }
    if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("별명은 한글/영문/숫자/공백/_/- 조합 2~20자로 입력해 주세요.");
    }
    return normalized;
  }
}
