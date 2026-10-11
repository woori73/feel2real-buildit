package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.VersionInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyPageVersionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 */
@Service
@RequiredArgsConstructor
public class AcademyPageVersionService {

  private static final int MAX_VERSIONS = 20;

  private final AcademyPageVersionRepository versionRepository;
  private final AcademyService academyService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** version_no 충돌 재시도용 자기 참조 (B4). */
  @Lazy
  @Autowired
  private AcademyPageVersionService self;

  /**
   * 현재 상태를 스냅샷으로 저장한다.
   *
   * @param principal 인증된 사용자 (TUTOR)
   * @param builderDraft 현재 builderDraft
   * @param pageSettings 현재 페이지 설정
   * @param note 버전 설명
   * @return 생성된 버전 ID
   */
  /**
   * 현재 상태를 스냅샷으로 저장한다 (B4).
   *
   * <p>동시에 두 스냅샷이 같은 version_no를 잡으면 UNIQUE 제약에 걸린다.
   * 이 경우 최대 1회 재시도한다. 재시도를 위해 내부 트랜잭션은 REQUIRES_NEW로
   * 분리하므로, 실패한 시도가 바깥 트랜잭션을 오염시키지 않는다.
   */
  /**
   * 버전 스냅샷을 저장한다.
   *
   * <p>호출자의 트랜잭션에 참여한다 (REQUIRED). version_no 동시성 충돌 시
   * 새 트랜잭션에서 1회 재시도한다.
   */
  @Transactional
  public Long snapshot(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, String> builderDraft,
      Map<String, Object> pageSettings,
      String note) {
    try {
      return doSnapshotInternal(principal, builderDraft, pageSettings, note);
    } catch (DataIntegrityViolationException e) {
      // version_no UNIQUE 충돌 → 현재 TX는 rollback-only이므로 새 TX에서 재시도
      return self.snapshotRetry(principal, builderDraft, pageSettings, note);
    }
  }

  /**
   * 스냅샷 재시도용 (REQUIRES_NEW).
   *
   * <p>동시성 충돌로 기존 트랜잭션이 broken된 경우에만 호출된다.
   * 독립 트랜잭션에서 version_no를 다시 계산하여 저장한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long snapshotRetry(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, String> builderDraft,
      Map<String, Object> pageSettings,
      String note) {
    return doSnapshotInternal(principal, builderDraft, pageSettings, note);
  }

  private Long doSnapshotInternal(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, String> builderDraft,
      Map<String, Object> pageSettings,
      String note) {
    AcademyResponse academy = getMyAcademy(principal);
    Integer nextNo = versionRepository
        .findTopByAcademyIdOrderByVersionNoDesc(academy.academyId())
        .map(v -> v.getVersionNo() + 1)
        .orElse(1);

    AcademyPageVersion version = new AcademyPageVersion(
        academy.academyId(),
        nextNo,
        toJson(builderDraft),
        toJson(pageSettings),
        String.valueOf(principal.userId()),
        note);
    versionRepository.save(version);

    // 20개 초과 시 오래된 버전 삭제
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

  private void cleanupOldVersions(Long academyId) {
    long count = versionRepository.countByAcademyId(academyId);
    if (count > MAX_VERSIONS) {
      List<AcademyPageVersion> versions =
          versionRepository.findByAcademyIdOrderByVersionNoDesc(academyId);
      // 20개를 초과하는 오래된 버전 삭제
      for (int i = MAX_VERSIONS; i < versions.size(); i++) {
        versionRepository.delete(versions.get(i));
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
