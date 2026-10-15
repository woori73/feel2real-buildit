package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto;

import java.util.List;

/**
 * 공개 페이지 상단 "중간고사 준비 중" Showcase 가 소비하는 경량 payload(Phase 4e).
 *
 * <p>학교/학년 identity + 실제 Wiki 자료 HTML 에서 <b>bounded pattern 으로 추출한 텍스트만</b>
 * 담는다. HTML 원문 / 파일 URL / storageKey / 회원 전용 자료는 절대 싣지 않는다. 추출이
 * 불확실한 필드는 채우지 않고 비운다(추측하지 않는다).
 */
public final class MidtermShowcaseDtos {

  private MidtermShowcaseDtos() {}

  public record ShowcaseScene(
      String school,
      int grade,
      String examLabel,
      String lesson,                 // 예 "Lesson 5" — 추출 실패 시 null
      List<String> grammarPoints,    // 예 ["관계대명사", "간접의문문"] — 없으면 빈 리스트
      List<String> sampleSentences,  // 자료 본문의 예문 — 없으면 빈 리스트
      String sourceId) {}            // wiki-sources 와 동일한 공개 opaque id
}
