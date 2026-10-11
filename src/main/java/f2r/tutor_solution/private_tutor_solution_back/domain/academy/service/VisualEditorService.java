package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorPublishedRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 시각 편집기 초안·게시본 서비스.
 *
 * <p>기존 P0/P1 데이터와 완전히 분리된 전용 테이블을 사용한다.
 * P1 스냅샷/D3 버전 번호와 연결하지 않는다.
 *
 * <p>동시 수정 제어: revision 낙관적 잠금.
 * 저장 시 revision 불일치하면 {@link DraftConflictException}을 던지며,
 * 컨트롤러에서 HTTP 409로 변환한다.
 */
@Service
@RequiredArgsConstructor
public class VisualEditorService {

  // 크기 제한 (바이트)
  public static final int MAX_HTML_SIZE = 16 * 1024 * 1024;   // 16MB
  public static final int MAX_CSS_SIZE = 64 * 1024;            // 64KB
  public static final int MAX_PROJECT_SIZE = 16 * 1024 * 1024; // 16MB
  public static final int MAX_REQUEST_SIZE = 33 * 1024 * 1024; // 33MB (전체)

  private final VisualEditorDraftRepository draftRepository;
  private final VisualEditorPublishedRepository publishedRepository;
  private final AcademyService academyService;

  /**
   * 동시 수정 충돌 예외. HTTP 409로 변환된다.
   */
  public static class DraftConflictException extends RuntimeException {
    public DraftConflictException(String message) {
      super(message);
    }
  }

  /**
   * 초안을 저장한다. 학원당 하나의 초안을 유지한다.
   *
   * @param principal 인증된 사용자
   * @param html 정제된 HTML
   * @param css 정제된 CSS
   * @param projectData 검증된 projectData JSON
   * @param expectedRevision 호출자가 읽은 시점의 revision (신규는 null)
   * @return 저장된 초안
   * @throws DraftConflictException revision 불일치 시
   */
  @Transactional
  public VisualEditorDraft saveDraft(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String html,
      String css,
      String projectData,
      Long expectedRevision) {
    AcademyResponse academy = requireMyAcademy(principal);
    Long academyId = academy.academyId();

    validateSize(html, MAX_HTML_SIZE, "HTML");
    validateSize(css, MAX_CSS_SIZE, "CSS");
    validateSize(projectData, MAX_PROJECT_SIZE, "프로젝트 데이터");

    Optional<VisualEditorDraft> existing = draftRepository.findByAcademyId(academyId);
    if (existing.isPresent()) {
      VisualEditorDraft draft = existing.get();
      try {
        draft.update(html, css, projectData, expectedRevision);
      } catch (IllegalStateException e) {
        throw new DraftConflictException(e.getMessage());
      }
      return draft;
    }

    // 신규 초안
    try {
      VisualEditorDraft draft = new VisualEditorDraft(
          academyId, html, css, projectData, String.valueOf(principal.userId()));
      return draftRepository.save(draft);
    } catch (DataIntegrityViolationException e) {
      // 동시 생성 충돌
      throw new DraftConflictException(
          "다른 세션에서 초안이 생성되었습니다. 새로고침 후 다시 시도하세요.");
    }
  }

  /**
   * 초안을 조회한다. 본인 학원의 초안만 반환한다.
   */
  @Transactional(readOnly = true)
  public Optional<VisualEditorDraft> getDraft(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);
    return draftRepository.findByAcademyId(academy.academyId());
  }

  /**
   * 게시본을 조회한다. 본인 학원의 게시본만 반환한다.
   */
  @Transactional(readOnly = true)
  public Optional<VisualEditorPublished> getPublished(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);
    return publishedRepository.findByAcademyId(academy.academyId());
  }

  private AcademyResponse requireMyAcademy(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.getMine(principal)
        .orElseThrow(() -> new AccessDeniedException("학원이 없습니다."));
  }

  private void validateSize(String data, int maxSize, String name) {
    if (data != null && data.length() > maxSize) {
      throw new IllegalArgumentException(
          name + " 크기가 제한(" + (maxSize / 1024) + "KB)을 초과했습니다.");
    }
  }
}
