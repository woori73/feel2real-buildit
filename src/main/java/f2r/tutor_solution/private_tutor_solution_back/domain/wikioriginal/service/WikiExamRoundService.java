package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamRoundResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamRound;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamRoundRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 모의고사 위키 회차(연도·월) 관리.
 *
 * <p>공개: 공개 회차 목록 + 현재 메인 회차(is_current 지정이 없으면 가장 최근 공개 회차로 폴백).
 * 관리(ADMIN): 회차 생성·수정·메인 지정·공개 전환·삭제. 메인 지정은 항상 하나의 회차만 유지한다.
 */
@Service
public class WikiExamRoundService {

  private final WikiExamRoundRepository repository;

  public WikiExamRoundService(WikiExamRoundRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<WikiExamRoundResponse> listPublic() {
    return repository.findPublicRounds().stream().map(WikiExamRoundResponse::of).toList();
  }

  /** 현재 메인 회차. is_current 지정이 없으면 최신 공개 회차, 공개 회차가 없으면 404. */
  @Transactional(readOnly = true)
  public WikiExamRoundResponse currentPublic() {
    return WikiExamRoundResponse.of(resolveCurrent());
  }

  @Transactional(readOnly = true)
  public WikiExamRound resolveCurrent() {
    return repository.findCurrentPublic()
        .orElseGet(() -> repository.findPublicRounds().stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개된 모의고사 회차가 없습니다.")));
  }

  @Transactional(readOnly = true)
  public List<WikiExamRoundResponse> listAdmin() {
    return repository.findAdminList().stream().map(WikiExamRoundResponse::of).toList();
  }

  @Transactional
  public WikiExamRoundResponse create(Integer examYear, Integer examMonth, String title, String subtitle,
      String bannerPath, boolean published, boolean current) {
    validYearMonth(examYear, examMonth);
    repository.findByExamYearAndExamMonthAndDeletedFalse(examYear, examMonth).ifPresent(existing -> {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "이미 존재하는 회차입니다: " + examYear + "년 " + examMonth + "월");
    });
    WikiExamRound round = WikiExamRound.builder()
        .examYear(examYear)
        .examMonth(examMonth)
        .title(required(title, "회차 제목"))
        .subtitle(blankToNull(subtitle))
        .bannerPath(blankToNull(bannerPath))
        .current(false)
        .published(published)
        .build();
    WikiExamRound saved = repository.save(round);
    if (current) {
      markCurrent(saved);
    }
    return WikiExamRoundResponse.of(saved);
  }

  @Transactional
  public WikiExamRoundResponse update(Long id, Integer examYear, Integer examMonth, String title,
      String subtitle, String bannerPath, boolean published) {
    validYearMonth(examYear, examMonth);
    WikiExamRound round = getAdminEntity(id);
    repository.findByExamYearAndExamMonthAndDeletedFalse(examYear, examMonth)
        .filter(other -> !other.getWikiExamRoundId().equals(id))
        .ifPresent(other -> {
          throw new ResponseStatusException(HttpStatus.CONFLICT,
              "이미 존재하는 회차입니다: " + examYear + "년 " + examMonth + "월");
        });
    round.updateMetadata(examYear, examMonth, required(title, "회차 제목"),
        blankToNull(subtitle), blankToNull(bannerPath), published);
    return WikiExamRoundResponse.of(round);
  }

  /** 메인 회차 지정 — 기존 메인 지정은 모두 해제하고 이 회차만 메인으로 둔다. */
  @Transactional
  public WikiExamRoundResponse setCurrent(Long id) {
    WikiExamRound round = getAdminEntity(id);
    markCurrent(round);
    return WikiExamRoundResponse.of(round);
  }

  private void markCurrent(WikiExamRound target) {
    repository.findAllCurrent().forEach(other -> {
      if (!other.getWikiExamRoundId().equals(target.getWikiExamRoundId())) {
        other.setCurrent(false);
      }
    });
    target.setCurrent(true);
    target.setPublished(true);
  }

  @Transactional
  public WikiExamRoundResponse updatePublished(Long id, boolean published) {
    WikiExamRound round = getAdminEntity(id);
    round.setPublished(published);
    return WikiExamRoundResponse.of(round);
  }

  @Transactional
  public void softDelete(Long id) {
    getAdminEntity(id).softDelete();
  }

  private WikiExamRound getAdminEntity(Long id) {
    return repository.findAdminById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다."));
  }

  private void validYearMonth(Integer examYear, Integer examMonth) {
    if (examYear == null || examYear < 2000 || examYear > 2100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험 연도는 2000~2100 사이로 입력해주세요.");
    }
    if (examMonth == null || examMonth < 1 || examMonth > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험 시행 월은 1~12 사이로 입력해주세요.");
    }
  }

  private String required(String value, String label) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을(를) 입력해주세요.");
    }
    return trimmed;
  }

  private String blankToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
