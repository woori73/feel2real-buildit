package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.VersionInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
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
 * 임계 구간의 모든 읽기를 잠금 읽기(SELECT ... FOR UPDATE)로 통일한다.
 * REPEATABLE READ에서 일반 SELECT는 트랜잭션 시작 시점의 스냅샷을 사용하므로,
 * 잠금 획득 전에 일반 SELECT를 하면 동시 요청이 같은 version_no를 계산한다.
 * 잠금 읽기는 항상 최신 커밋 데이터를 보므로 이 문제를 해결한다.
 *
 * <p>처리 순서:
 * 1. findForUpdateByTutorUserId() — Academy 행 잠금 + 최신 데이터 조회
 * 2. findTopForUpdateByAcademyId() — 최신 version_no 조회 (잠금 읽기)
 * 3. 저장 → 오래된 버전 정리 → 커밋
 *
 * <p>잠금은 Academy 행 단위이므로 다른 학원의 스냅샷 작업은 차단되지 않는다.
 * REQUIRES_NEW 재시도 방식은 사용하지 않는다.
 * 격리 수준 전역 변경 없이 REPEATABLE READ를 유지한다.
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
   * <p>D3: 임계 구간의 모든 읽기를 잠금 읽기로 통일한다.
   * 1. Academy 행 잠금 (findForUpdateByTutorUserId) — 학원별 직렬화
   * 2. 최신 version_no 조회 (findTopForUpdateByAcademyId) — 잠금 읽기
   * 3. 저장 → 정리 → 커밋을 단일 트랜잭션에서 처리
   *
   * <p>호출자의 트랜잭션에 참여한다 (REQUIRED).
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
    // D3: 잠금 읽기로 Academy 조회 — 일반 SELECT를 먼저 하면 REPEATABLE READ
    // 스냅샷이 확정되어 동시 요청이 같은 version_no를 계산한다.
    // findForUpdateByTutorUserId는 잠금 읽기이므로 최신 데이터를 보고,
    // 동시에 Academy 행 잠금을 획득해 학원별 작업을 직렬화한다.
    Academy academy = academyRepository
        .findForUpdateByTutorUserId(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("Academy가 없습니다."));
    Long academyId = academy.getAcademyId();

    // D3: version_no 조회도 잠금 읽기로 — 최신 커밋 데이터를 보장한다.
    Integer nextNo = versionRepository
        .findTopForUpdateByAcademyId(academyId)
        .map(v -> v.getVersionNo() + 1)
        .orElse(1);

    AcademyPageVersion version = new AcademyPageVersion(
        academyId,
        nextNo,
        toJson(builderDraft),
        toJson(pageSettings),
        intro,
        String.valueOf(principal.userId()),
        note);
    versionRepository.save(version);

    // F3: 20개 초과 시 오래된 버전 삭제 (최초 버전 v1은 보존)
    // 같은 트랜잭션·같은 잠금 안에서 처리하므로 상한이 정확히 유지된다.
    cleanupOldVersions(academyId);

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
