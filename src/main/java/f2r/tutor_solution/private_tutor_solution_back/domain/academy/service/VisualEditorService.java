package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorPublishedRepository;
import jakarta.persistence.OptimisticLockException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 시각 편집기 초안·게시본 서비스.
 *
 * <p>기존 P0/P1 데이터와 완전히 분리된 전용 테이블을 사용한다.
 * P1 스냅샷/D3 버전 번호와 연결하지 않는다.
 *
 * <p>동시 수정 제어: JPA {@code @Version} 낙관적 잠금.
 * DB 레벨에서 원자적으로 revision을 검증하므로 TOCTOU 문제가 없다.
 * 충돌 시 {@link DraftConflictException}을 던지며, 컨트롤러에서 HTTP 409로 변환한다.
 *
 * <p>크기 제한은 UTF-8 바이트 기준으로 검사한다.
 * DB 컬럼 용량(TEXT 65,535바이트, MEDIUMTEXT 16,777,215바이트)을 초과하지 않도록
 * 경계값을 포함하여 검사하므로, 정확히 제한 크기 입력 시에도
 * 500이 아니라 400이 반환된다.
 */
@Service
@RequiredArgsConstructor
public class VisualEditorService {

  // 크기 제한 (UTF-8 바이트). DB 컬럼 용량에 맞춰 경계값 포함 검사한다.
  public static final int MAX_HTML_SIZE = 16 * 1024 * 1024;    // MEDIUMTEXT 16,777,215
  public static final int MAX_CSS_SIZE = 64 * 1024;             // TEXT 65,535
  public static final int MAX_PROJECT_SIZE = 16 * 1024 * 1024; // MEDIUMTEXT 16,777,215
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
   * <p>원자성 보장:
   * <ul>
   *   <li>신규 생성은 {@code saveAndFlush()}로 즉시 INSERT하여
   *       UNIQUE 충돌을 트랜잭션 안에서 감지한다.</li>
   *   <li>수정은 JPA {@code @Version}이 UPDATE 시 원자적으로 revision을 검증하며,
   *       {@code flush()}로 충돌을 즉시 감지한다.</li>
   * </ul>
   *
   * @param principal 인증된 사용자
   * @param html 정제된 HTML
   * @param css 정제된 CSS
   * @param projectData 검증된 projectData JSON
   * @param expectedRevision 호출자가 읽은 시점의 revision (신규는 null 또는 0)
   * @return 저장된 초안
   * @throws DraftConflictException revision 불일치 또는 동시 생성 충돌 시
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
      // 선행 검증: 조회 시점 revision 불일치는 즉시 409
      if (expectedRevision != null && !draft.getRevision().equals(expectedRevision)) {
        throw new DraftConflictException(
            "다른 세션에서 수정되었습니다. 새로고침 후 다시 시도하세요. "
                + "(현재 revision=" + draft.getRevision()
                + ", 요청 revision=" + expectedRevision + ")");
      }
      try {
        draft.update(html, css, projectData);
        // flush를 강제하여 OptimisticLockException을 즉시 발생시킴
        draftRepository.flush();
      } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
        throw new DraftConflictException(
            "다른 세션에서 수정되었습니다. 새로고침 후 다시 시도하세요.");
      } catch (DataIntegrityViolationException e) {
        // 컬럼 용량 초과 등 DB 제약 위반 → 400
        throw new IllegalArgumentException(
            "저장할 데이터가 제한을 초과했습니다.");
      }
      return draft;
    }

    // 신규 초안: saveAndFlush로 원자적 저장 보장
    try {
      VisualEditorDraft draft = new VisualEditorDraft(
          academyId, html, css, projectData, String.valueOf(principal.userId()));
      return draftRepository.saveAndFlush(draft);
    } catch (DataIntegrityViolationException e) {
      // 동시 생성 충돌 (UNIQUE 제약) → 409,
      // 그 외 DB 제약 위반(용량 초과 등) → 400
      String message = e.getMessage();
      if (message != null && message.contains("uq_visual_draft_academy")) {
        throw new DraftConflictException(
            "다른 세션에서 초안이 생성되었습니다. 새로고침 후 다시 시도하세요.");
      }
      throw new IllegalArgumentException(
          "저장할 데이터가 제한을 초과했습니다.");
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

  /**
   * UTF-8 바이트 기준으로 크기를 검사한다.
   *
   * <p>DB 컬럼 용량을 초과하는 입력이 DB 오류(500)가 아니라
   * 400으로 처리되도록 경계값을 포함하여 검사한다.
   * 예: CSS는 TEXT(65,535바이트) 컬럼이므로 65,536바이트부터 거부.
   */
  private void validateSize(String data, int maxSize, String name) {
    if (data != null) {
      int byteSize = data.getBytes(StandardCharsets.UTF_8).length;
      if (byteSize >= maxSize) {
        throw new IllegalArgumentException(
            name + " 크기가 제한(" + (maxSize / 1024) + "KB)을 초과했습니다. "
                + "(현재 " + byteSize + "바이트)");
      }
    }
  }
}
