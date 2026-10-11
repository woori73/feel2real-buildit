package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.VersionInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyPageVersionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 홈페이지 버전 관리 서비스 (P1).
 *
 * <p>보안 원칙:
 * <ul>
 *   <li>academyId는 서버에서 인증된 고객 정보로 결정한다 (파라미터 조작 불가).
 *   <li>적용과 복원 모두 권한 검사 및 변경 이력을 남긴다.
 *   <li>최근 20개 버전만 유지한다.
 * </ul>
 *
 * <p>D3 동시성 제어:
 * 스냅샷 생성 전 Academy 행에 비관적 쓰기 잠금(PESSIMISTIC_WRITE, SELECT ... FOR UPDATE)을
 * 획득한다. 이로써 같은 학원의 스냅샷 생성 작업이 직렬화되며, version_no 결정·저장·오래된
 * 버전 정리가 하나의 트랜잭션 경계에서 원자적으로 처리된다.
 *
 * <p>잠금은 Academy 행 단위이므로 다른 학원의 스냅샷 작업은 차단되지 않는다.
 * REQUIRES_NEW 재시도 방식은 사용하지 않는다 — 잠금으로 충돌 자체가 발생하지 않으므로
 * 재시도가 불필요하고, "실패 응답인데 DB에 버전이 저장되는" 현상도 제거된다.
 */
@Service
@RequiredArgsConstructor
public class AcademyPageVersionService {

  private static final int MAX_VERSIONS = 20;

  private final AcademyPageVersionRepository versionRepository;
  private final AcademyRepository academyRepository;
  private final AcademyService academyService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 현재 상태를 스냅샷으로 저장한다.
   *
   * @param principal 인증된 사용자 (TUTOR)
   * @param builderDraft 현재 builderDraft
   * @param pageSettings 현재 페이지 설정
   * @param note 버전 설명
   * @return 생성된 버전 ID
   */
  @Transactional
  public Long snapshot(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, String> builderDraft,
      Map<String, Object> pageSettings,
      String note) {
    return snapshot(principal, builderDraft, pageSettings, null, note);
  }

  /**
   * 버전 스냅샷을 저장한다 (intro 포함).
   *
   * <p>D3: Academy 행 잠금 → version_no 결정 → 저장 → 정리를 단일 트랜잭션에서 처리한다.
   * 호출자의 트랜잭션에 참여한다 (REQUIRED).
   *
   * <p>F2: intro를 함께 저장한다 (AI가 변경한 top-level intro 복원용).
   */
  @Transactional
  public Long snapshot(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, String> builderDraft,
      Map<String, Object> pageSettings,
      String intro,
      String note) {
    AcademyResponse academy = getMyAcademy(principal);

    // D3: 학원별 스냅샷 작업 직렬화.
    // Academy 행에 PESSIMISTIC_WRITE 잠금을 걸어 동시 스냅샷 요청을 순서대로 처리한다.
    // 행 단위 잠금이므로 다른 학원의 작업은 영향을 받지 않는다.
    academyRepository
        .findForUpdateByAcademyId(academy.academyId())
        .orElseThrow(() -> new IllegalArgumentException("Academy가 없습니다."));

    Integer nextNo = versionRepository
        .findTopByAcademyIdOrderByVersionNoDesc(academy.academyId())
        .map(v -> v.getVersionNo() + 1)
        .orElse(1);

    AcademyPageVersion version = new AcademyPageVersion(
        academy.academyId(),
        nextNo,
        toJson(builderDraft),
        toJson(pageSettings),
        intro,
        String.valueOf(principal.userId()),
        note);
    versionRepository.save(version);

    // F3: 20개 초과 시 오래된 버전 삭제 (최초 버전 v1은 보존)
    // 같은 트랜잭션·같은 잠금 안에서 처리하므로 상한이 정확히 유지된다.
    cleanupOldVersions(academy.academyId());

    return version.getId();
  }

  /** 버전 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<VersionInfo> list(JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = getMyAcademy(principal);
    return versionRepository.findByAcademyIdOrderByVersionNoDesc(academy.academyId()).stream()
        .map(v -> new VersionInfo(
            v.getId(),
            v.getVersionNo(),
            v.getCreatedBy(),
            v.getCreatedAt().toString(),
            v.getNote()))
        .collect(Collectors.toList());
  }

  /**
   * 특정 버전을 조회한다 (복원용).
   *
   * @param principal 인증된 사용자
   * @param versionId 버전 ID
   * @return 버전 엔티티 (권한 검증됨)
   */
  @Transactional(readOnly = true)
  public AcademyPageVersion getForRestore(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long versionId) {
    AcademyResponse academy = getMyAcademy(principal);
    AcademyPageVersion version = versionRepository
        .findById(versionId)
        .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다: " + versionId));
    if (!version.getAcademyId().equals(academy.academyId())) {
      throw new AccessDeniedException("다른 학원의 버전에 접근할 수 없습니다.");
    }
    return version;
  }

  private AcademyResponse getMyAcademy(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService
        .getMine(principal)
        .orElseThrow(() -> new IllegalArgumentException("Academy가 없습니다."));
  }

  /**
   * F3: 20개 초과 시 오래된 버전을 삭제하되, 최초 버전(version_no=1)은 항상 보존한다.
   * 최초 원본은 복원의 기준점이므로 삭제하지 않는다.
   */
  private void cleanupOldVersions(Long academyId) {
    long count = versionRepository.countByAcademyId(academyId);
    if (count > MAX_VERSIONS) {
      List<AcademyPageVersion> versions =
          versionRepository.findByAcademyIdOrderByVersionNoDesc(academyId);
      // version_no=1 (최초 버전)은 제외하고 삭제 대상 선정
      List<AcademyPageVersion> deletable = versions.stream()
          .filter(v -> v.getVersionNo() != 1)
          .collect(Collectors.toList());
      // 전체가 20개를 초과하는 만큼만 삭제 (최초 버전 제외 후)
      int excess = (int) (count - MAX_VERSIONS);
      for (int i = 0; i < excess && i < deletable.size(); i++) {
        // deletable은 DESC 정렬이므로 뒤에서부터 (오래된 것부터) 삭제
        versionRepository.delete(deletable.get(deletable.size() - 1 - i));
      }
    }
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON 직렬화 실패", e);
    }
  }
}
