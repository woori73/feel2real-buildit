package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialSourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 자료 1건의 <b>공개 범위 판정</b>과 파일 바이트 접근을 담당한다(작업지시서 §14·§37).
 *
 * <p>핵심 계약: 접근 판정은 프런트가 아니라 여기서 한다. {@link MidtermMaterialVisibility#PRIVATE}
 * 자료는 목록에서도 제외되고 {@code /file} 은 404 다. {@code MEMBER} 는 인증 principal 을 요구한다.
 *
 * <p>Phase 1 은 파일 원본 제공을 {@link MidtermMaterialSourceType#WIKI_ANALYSIS}(이미 공개
 * 다운로드가 있는 ADMIN 분석자료)로 한정한다. 다른 source 타입은 표현 메타데이터만 노출하고
 * 파일은 Phase 2 에서 연결한다. Wiki 원본을 이 도메인으로 복제하지 않는다(§15·§39).
 */
@Service
@RequiredArgsConstructor
public class MidtermMaterialAccessService {

  private final AuthenticatedUserResolver authResolver;
  private final WikiAnalysisMaterialRepository wikiAnalysisMaterialRepository;
  private final WikiAnalysisMaterialService wikiAnalysisMaterialService;

  /** 공개 목록/상세 payload 에 실을 접근 라벨. */
  public enum Access {
    /** 비로그인 포함 전체 파일 열람 가능. */
    PUBLIC,
    /** 로그인하면 전체 파일 열람 가능(현재 principal 있음). */
    MEMBER,
    /** 로그인이 필요함(현재 principal 없음). */
    MEMBER_LOCKED,
    /** 썸네일/일부만. 전체 파일은 제공하지 않음. */
    PREVIEW,
    /** 노출 불가(목록에서도 제외). */
    NONE
  }

  public Access resolveAccess(MidtermMaterial material, Optional<JwtTokenProvider.AuthenticatedPrincipal> principal) {
    if (!material.isPublished()) {
      return Access.NONE;
    }
    return switch (material.getVisibility()) {
      case PUBLIC -> Access.PUBLIC;
      case MEMBER -> principal.isPresent() ? Access.MEMBER : Access.MEMBER_LOCKED;
      case PREVIEW -> Access.PREVIEW;
      case PRIVATE -> Access.NONE;
    };
  }

  /** 목록에 노출할 수 있는가(PRIVATE/미발행 제외). */
  public boolean isListable(MidtermMaterial material) {
    return material.isPublished() && material.getVisibility() != MidtermMaterialVisibility.PRIVATE;
  }

  /** 전체 파일 URL 을 payload 에 실어도 되는가. */
  public boolean canExposeFileUrl(Access access) {
    return access == Access.PUBLIC || access == Access.MEMBER;
  }

  /** 운영자 편집 화면에 보여줄 원본 자료 라벨(없으면 null). */
  public String sourceLabel(MidtermMaterial material) {
    if (material.getSourceType() == MidtermMaterialSourceType.WIKI_ANALYSIS
        && material.getSourceMaterialId() != null) {
      return wikiAnalysisMaterialRepository.findAdminById(material.getSourceMaterialId())
          .map(WikiAnalysisMaterial::getTitle)
          .orElse("(삭제된 분석자료 #" + material.getSourceMaterialId() + ")");
    }
    if (material.getSourceType() != MidtermMaterialSourceType.NONE
        && material.getSourceMaterialId() != null) {
      return material.getSourceType() + " #" + material.getSourceMaterialId();
    }
    return null;
  }

  /**
   * 파일 바이트 반환. visibility 를 서버에서 강제한다.
   *
   * @throws ResponseStatusException 404(PRIVATE/PREVIEW/파일없음) 또는 401(MEMBER 인데 비로그인)
   */
  public WikiAnalysisMaterialService.DownloadFile readFile(
      MidtermMaterial material, Optional<JwtTokenProvider.AuthenticatedPrincipal> principal) {
    Access access = resolveAccess(material, principal);
    switch (access) {
      case NONE, PREVIEW ->
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공개된 자료 파일이 아닙니다.");
      case MEMBER_LOCKED ->
          throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 후 열람할 수 있는 자료입니다.");
      default -> { /* PUBLIC, MEMBER: 통과 */ }
    }
    if (material.getSourceType() != MidtermMaterialSourceType.WIKI_ANALYSIS
        || material.getSourceMaterialId() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "연결된 자료 파일이 없습니다.");
    }
    return wikiAnalysisMaterialService.getPublicDownload(material.getSourceMaterialId());
  }
}
