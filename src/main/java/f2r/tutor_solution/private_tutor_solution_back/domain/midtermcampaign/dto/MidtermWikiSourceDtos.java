package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto;

import java.util.List;

/**
 * TUTOR Wiki(Python 앱, 127.0.0.1:8000) HTML 자료를 Midterm LIVE 가 read-only visual 로만 쓰기
 * 위한 payload. <b>데이터 연결만</b> — 원본을 feel2real-buildit DB 로 복제하지 않는다.
 *
 * <p>노출 정책(작업지시서 확정): 발행된 캠페인의 발행된 {@code MidtermSchoolCollection} 이 가진
 * (school, grade) + 캠페인 (examYear, semester, examType) 컨텍스트와 <b>정확히 일치</b>하는
 * Wiki 자료만 allowlist 에 든다. grade/exam 라벨 정규화 규칙을 임의로 만들지 않는다 —
 * 값을 저장된 그대로 비교하고, 일치하지 않으면 공개하지 않는다(fallback·전체 공개 없음).
 *
 * <p>보안: storageKey / 원본 파일 URL / download endpoint 를 절대 payload 에 싣지 않는다.
 * {@code sourceId} 는 (자료 종류 + Python id) 를 인코딩한 불투명 문자열이며 키/URL 이 아니다.
 */
public final class MidtermWikiSourceDtos {

  private MidtermWikiSourceDtos() {}

  /** 공개 목록 1건 — LIVE 가 iframe preview 를 걸기 위해 필요한 최소 정보만. */
  public record WikiSourceView(
      String sourceId,
      String kind,
      String kindLabel,
      String title,
      String school,
      String grade,
      Integer year,
      String semester,
      String exam,
      String createdAt,
      String previewUrl) {}

  /** Python list API 1건(내부). 저장된 값을 그대로 담는다. */
  public record WikiSourceRow(
      String kind,
      long id,
      String title,
      String school,
      String grade,
      int year,
      String semester,
      String exam,
      String createdAt) {}

  /** 발행된 school collection 에서 파생한 조회 컨텍스트(내부). */
  public record WikiExamContext(
      String school, String grade, int year, String semester, String exam) {}

  /** 목록 결과 + 진단(매칭 0건인 컨텍스트). 진단은 보고용이며 공개 payload 에 넣지 않는다. */
  public record WikiSourceListResult(
      List<WikiSourceView> views, List<WikiExamContext> unmatchedContexts) {}
}
