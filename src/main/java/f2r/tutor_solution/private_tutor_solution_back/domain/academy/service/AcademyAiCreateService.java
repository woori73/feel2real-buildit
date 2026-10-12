package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 홈페이지 최초 생성 서비스 (뮤즈 담당, 2026-10-12).
 *
 * <p>TUTOR가 대화로 학원 정보를 입력하면 5종 템플릿 중 적합한 디자인을 선택하고,
 * 실제 제공 정보에 맞는 문구를 생성한다.
 *
 * <p><b>중요 원칙</b>:
 * <ul>
 *   <li>없는 학원 정보·실적·연락처를 임의로 만들지 않는다
 *   <li>제공된 정보 밖의 사실을 새로 지어내지 않는다
 *   <li>클로드의 편집기 초안 데이터 형식(builderDraft 키)을 준수한다
 * </ul>
 *
 * <p>프롬프트는 이 서비스에서 직접 조립한다. 기존 {@code AcademyAiPromptBuilder}는
 * 섹션 빌더용 Task 전용이므로 건드리지 않는다.
 */
@Service
public class AcademyAiCreateService {

  private final AiProvider aiProvider;

  public AcademyAiCreateService(AiProvider aiProvider) {
    this.aiProvider = aiProvider;
  }

  /**
   * 학원 정보를 받아 홈페이지 초안을 생성한다.
   *
   * @param input 학원 정보 (이름, 종류, 위치, 과목 등)
   * @return builderDraft 형식의 초안 데이터 (키: homepage.*, sec-*)
   */
  public Map<String, String> createHomepage(AcademyCreateInput input) {
    // 1. 입력 검증 (필수 정보 확인)
    validateInput(input);

    // 2. 템플릿 선택 (5종 중 적합한 디자인)
    String template = selectTemplate(input);

    // 3. 초안 조립
    Map<String, String> draft = new HashMap<>();
    draft.put("homepage.template", template);
    draft.put("homepage.renderMode", "template");

    // 4. 히어로 섹션
    draft.put("sec-hero.title", generateHeroTitle(input));
    String subtitle = generateHeroSubtitle(input);
    if (!subtitle.isBlank()) {
      draft.put("sec-hero.subtitle", subtitle);
    }

    // 5. 소개 섹션 (제공된 정보만 사용)
    if (input.getIntroduction() != null && !input.getIntroduction().isBlank()) {
      draft.put("sec-intro.content", refineText(input.getIntroduction()));
    }

    // 6. 교육과정 섹션
    if (input.getCurriculum() != null && !input.getCurriculum().isEmpty()) {
      draft.put("sec-curriculum.items", toJsonItems(input.getCurriculum()));
    }

    // 7. 연락처 섹션 (제공된 것만 — 없으면 키 자체를 넣지 않는다)
    if (input.getPhone() != null && !input.getPhone().isBlank()) {
      draft.put("sec-contact.phone", input.getPhone());
    }
    if (input.getAddress() != null && !input.getAddress().isBlank()) {
      draft.put("sec-contact.address", input.getAddress());
    }

    // 8. CTA 섹션 (고정 문구)
    draft.put("sec-cta.title", "상담 신청하기");
    draft.put("sec-cta.buttonText", "무료 상담 신청");

    return draft;
  }

  private void validateInput(AcademyCreateInput input) {
    if (input.getName() == null || input.getName().isBlank()) {
      throw new IllegalArgumentException("학원 이름은 필수입니다");
    }
    if (input.getType() == null) {
      throw new IllegalArgumentException("학원 종류는 필수입니다");
    }
    // 실적·수상 등은 필수가 아님 (없으면 생성하지 않음)
  }

  /**
   * 학원 종류에 따라 적합한 템플릿을 선택한다.
   *
   * <p>5종 프리셋: EXAM(입시), ENGLISH(어학), ARTS(예체능), STUDY(보습), ONLINE(온라인)
   */
  private String selectTemplate(AcademyCreateInput input) {
    return switch (input.getType()) {
      case LANGUAGE -> "ENGLISH";
      case EXAM_PREP -> "EXAM";
      case ARTS_SPORTS -> "ARTS";
      case GENERAL -> "STUDY";
      case ONLINE -> "ONLINE";
    };
  }

  private String generateHeroTitle(AcademyCreateInput input) {
    String prompt =
        "학원 홈페이지 히어로 제목을 20자 이내로 만들어줘. 다른 설명 없이 최종 문구만 출력해.\n"
            + "학원 이름: " + input.getName() + "\n"
            + "학원 종류: " + input.getType() + "\n"
            + "제공된 소개: " + (input.getIntroduction() != null ? input.getIntroduction() : "없음") + "\n"
            + "제공된 정보 밖의 실적·수치·수상은 절대 지어내지 마.";
    AiCompletion completion = aiProvider.complete(prompt, new AiTaskLimits(100));
    if (!completion.ok()) {
      // AI 실패 시 기본값 (정보 날조 방지)
      return input.getName();
    }
    return completion.text().trim();
  }

  private String generateHeroSubtitle(AcademyCreateInput input) {
    List<String> strengths = input.getStrengths();
    String prompt =
        "학원 홈페이지 부제목을 50자 이내로 만들어줘. 다른 설명 없이 최종 문구만 출력해.\n"
            + "학원 이름: " + input.getName() + "\n"
            + "강점: " + (strengths == null || strengths.isEmpty()
                ? "없음" : String.join(", ", strengths)) + "\n"
            + "제공된 정보 밖의 사실은 절대 지어내지 마. 강점이 없으면 빈 문자열만 출력해.";
    AiCompletion completion = aiProvider.complete(prompt, new AiTaskLimits(200));
    if (!completion.ok()) {
      return "";
    }
    return completion.text().trim();
  }

  private String refineText(String rawText) {
    // AI로 다듬기 (WikiRouterAcademyAiProvider의 system prompt에 사실 날조 금지 내장)
    AiCompletion completion = aiProvider.complete(
        "다음 문장을 자연스럽고 신뢰가 가게 다듬어줘. 다른 설명 없이 최종 문구만 출력해. "
            + "원문에 없는 실적·수치는 절대 추가하지 마: " + rawText,
        new AiTaskLimits(500));
    if (!completion.ok()) {
      return rawText; // 실패 시 원문 유지
    }
    return completion.text().trim();
  }

  private String toJsonItems(List<String> items) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("{\"text\":\"")
          .append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
          .append("\"}");
    }
    sb.append("]");
    return sb.toString();
  }

  /** 학원 생성 입력 데이터. */
  public static class AcademyCreateInput {
    private String name;
    private AcademyType type;
    private String introduction;
    private List<String> curriculum = new ArrayList<>();
    private List<String> strengths = new ArrayList<>();
    private String phone;
    private String address;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AcademyType getType() { return type; }
    public void setType(AcademyType type) { this.type = type; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public List<String> getCurriculum() { return curriculum; }
    public void setCurriculum(List<String> curriculum) { this.curriculum = curriculum; }
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
  }

  public enum AcademyType {
    LANGUAGE,    // 어학원
    EXAM_PREP,   // 입시학원
    ARTS_SPORTS, // 예체능학원
    GENERAL,     // 보습학원
    ONLINE       // 온라인 교육
  }
}
