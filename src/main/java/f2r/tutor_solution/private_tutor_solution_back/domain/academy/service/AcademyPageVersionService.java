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
 */
@Service
@RequiredArgsConstructor
public class AcademyPageVersionService {

  private static final int MAX_VERSIONS = 20;

  private final AcademyPageVersionRepository versionRepository;
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
    AcademyResponse academy = getMyAcademy(principal);
    Integer nextNo = versionRepository
        .findTopByAcademyIdOrderByVersionNoDesc(academy.id())
        .map(v -> v.getVersionNo() + 1)
        .orElse(1);

    AcademyPageVersion version = new AcademyPageVersion(
        academy.id(),
        nextNo,
        toJson(builderDraft),
        toJson(pageSettings),
        principal.userId(),
        note);
    versionRepository.save(version);

    // 20개 초과 시 오래된 버전 삭제
    cleanupOldVersions(academy.id());

    return version.getId();
  }

  /** 버전 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<VersionInfo> list(JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = getMyAcademy(principal);
    return versionRepository.findByAcademyIdOrderByVersionNoDesc(academy.id()).stream()
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
    if (!version.getAcademyId().equals(academy.id())) {
      throw new SecurityException("다른 학원의 버전에 접근할 수 없습니다.");
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
