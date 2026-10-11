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

  // 홈페이지 렌더 모드 (builderDraft 키). 기본값 "template" (P0).
  public static final String RENDER_MODE_KEY = "homepage.renderMode";
  public static final String RENDER_MODE_TEMPLATE = "template";
  public static final String RENDER_MODE_CUSTOM = "custom";

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

  /**
   * 공개 홈페이지용 게시본 HTML/CSS를 반환한다. 인증 불필요.
   *
   * <p>renderMode가 "custom"이 아니면 empty를 반환하여 P0 폴백을 유도한다.
   * 출력 시 Sanitizer로 재검증한다 (defense in depth — 저장 시 정제되었더라도
   * 공개 출력 직전에 한 번 더 검증).
   *
   * @param handle 학원 handle (URL 테넌트 키)
   * @return 정제된 HTML/CSS. custom 모드가 아니거나 게시본이 없으면 empty
   */
  @Transactional(readOnly = true)
  public Optional<PublishedHomepage> getPublishedHomepage(String handle) {
    var academy = academyService.getPublishedEntityByHandle(handle);
    if (!RENDER_MODE_CUSTOM.equals(renderModeOf(academy.getPageSettings()))) {
      return Optional.empty();
    }
    return publishedRepository.findByAcademyId(academy.getAcademyId())
        .map(p -> new PublishedHomepage(
            VisualEditorSanitizer.sanitizeHtml(p.getHtml()),
            VisualEditorSanitizer.sanitizeCss(p.getCss())));
  }

  /**
   * 공개 홈페이지용 게시본.
   *
   * @param html 정제된 HTML
   * @param css 정제된 CSS
   */
  public record PublishedHomepage(String html, String css) {
  }

  /**
   * P0 빌더 DOM을 초안으로 가져온다.
   *
   * <p>클라이언트(브라우저)에서 P0 미리보기 DOM을 직렬화해 전송한다.
   * 서버는 academy_sections.js를 재구현하지 않는다.
   *
   * <p>기존 초안이 있으면 무단 덮어쓰기를 방지하기 위해 409를 던진다.
   * P0 원본 데이터(page_settings)는 건드리지 않는다.
   *
   * @param principal 인증된 사용자
   * @param html P0 DOM에서 직렬화한 HTML (정제 전)
   * @param css P0 브랜드 CSS (정제 전)
   * @return 생성된 초안
   * @throws DraftConflictException 기존 초안이 있을 때
   */
  @Transactional
  public VisualEditorDraft importFromP0(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String html,
      String css) {
    AcademyResponse academy = requireMyAcademy(principal);
    Long academyId = academy.academyId();

    if (draftRepository.existsByAcademyId(academyId)) {
      throw new DraftConflictException(
          "기존 초안이 있습니다. 가져오기로 덮어쓰려면 기존 초안을 먼저 삭제하세요.");
    }

    validateSize(html, MAX_HTML_SIZE, "HTML");
    validateSize(css, MAX_CSS_SIZE, "CSS");

    String cleanHtml = VisualEditorSanitizer.sanitizeHtml(html);
    String cleanCss = VisualEditorSanitizer.sanitizeCss(css);

    try {
      VisualEditorDraft draft = new VisualEditorDraft(
          academyId, cleanHtml, cleanCss, "{}", String.valueOf(principal.userId()));
      return draftRepository.saveAndFlush(draft);
    } catch (DataIntegrityViolationException e) {
      throw new DraftConflictException(
          "다른 세션에서 초안이 생성되었습니다. 새로고침 후 다시 시도하세요.");
    }
  }

  /**
   * 초안을 게시한다. draft → published 복사와 renderMode 변경을
   * 하나의 트랜잭션으로 처리하며, 중간 실패 시 전체 롤백된다.
   *
   * @param principal 인증된 사용자 (해당 학원 권한 필요)
   * @param expectedRevision 게시 시점의 초안 revision (낙관적 잠금)
   * @throws DraftConflictException 초안이 없거나 revision 불일치 시
   */
  @Transactional
  public void publish(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long expectedRevision) {
    AcademyResponse academy = requireMyAcademy(principal);
    Long academyId = academy.academyId();

    VisualEditorDraft draft = draftRepository.findByAcademyId(academyId)
        .orElseThrow(() -> new DraftConflictException(
            "게시할 초안이 없습니다. 먼저 초안을 저장하세요."));
    if (expectedRevision == null || !draft.getRevision().equals(expectedRevision)) {
      throw new DraftConflictException(
          "초안이 변경되었습니다. 새로고침 후 다시 시도하세요. "
              + "(현재 revision=" + draft.getRevision() + ")");
    }

    // 1. published 교체 (기존 게시본이 있으면 삭제 후 생성)
    // 동시 게시 시 UNIQUE 제약 충돌은 409로 변환 (데이터 정합성 유지)
    try {
      publishedRepository.findByAcademyId(academyId)
          .ifPresent(publishedRepository::delete);
      publishedRepository.flush();
      VisualEditorPublished published = new VisualEditorPublished(
          academyId, draft.getHtml(), draft.getCss(),
          String.valueOf(principal.userId()));
      publishedRepository.saveAndFlush(published);
    } catch (DataIntegrityViolationException e) {
      throw new DraftConflictException(
          "다른 세션에서 게시가 진행 중입니다. 새로고침 후 다시 시도하세요.");
    }

    // 2. renderMode를 custom으로 변경 (같은 트랜잭션)
    academyService.setRenderMode(principal, RENDER_MODE_CUSTOM);
  }

  /**
   * 게시를 취소한다. renderMode를 template으로 되돌린다.
   * published 행과 P0 데이터는 유지된다.
   *
   * @param principal 인증된 사용자 (해당 학원 권한 필요)
   */
  @Transactional
  public void unpublish(JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireMyAcademy(principal);
    academyService.setRenderMode(principal, RENDER_MODE_TEMPLATE);
  }

  /**
   * page_settings에서 renderMode를 읽는다. 없으면 "template" (기본값).
   */
  static String renderModeOf(
      f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings settings) {
    if (settings == null || settings.builderDraft() == null) {
      return RENDER_MODE_TEMPLATE;
    }
    String mode = settings.builderDraft().get(RENDER_MODE_KEY);
    return RENDER_MODE_CUSTOM.equals(mode) ? RENDER_MODE_CUSTOM : RENDER_MODE_TEMPLATE;
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
