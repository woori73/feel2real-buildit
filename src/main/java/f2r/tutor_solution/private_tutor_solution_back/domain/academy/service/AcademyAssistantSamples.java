package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 시작 화면의 샘플 6종. 새 디자인을 만들지 않고 기존 프리셋 10종 중에서 고른다.
 * seed는 AI 생성기에 넘기는 요청 문장이며, 주소·전화번호·강사·수강료 같은 사실은 담지 않는다.
 */
public final class AcademyAssistantSamples {

  private AcademyAssistantSamples() {}

  public record Sample(
      String key, String title, String description, String preset,
      String subjectNameKo, Set<TargetGroup> targets, String seed) {}

  private static final List<Sample> ALL = List.of(
      new Sample("exam", "입시·내신 전문", "내신 대비와 입시 상담을 앞세운 신뢰형 홈페이지",
          "EXAM", "영어", Set.of(TargetGroup.MIDDLE, TargetGroup.HIGH),
          "중고등 내신 대비와 입시 전문 학원 홈페이지를 만들어 주세요. 상담 신청 버튼을 넣어 주세요."),
      new Sample("junior-english", "초중등 영어", "초등·중등 영어를 체계적으로 지도하는 따뜻한 학원",
          "ENGLISH", "영어", Set.of(TargetGroup.ELEMENTARY, TargetGroup.MIDDLE),
          "초등 중등 영어 전문 학원 홈페이지를 만들어 주세요. 상담 신청 버튼을 넣어 주세요."),
      new Sample("conversation", "영어회화", "말하기 중심 수업을 보여 주는 활동적인 홈페이지",
          "DYNAMIC", "영어", Set.of(TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.ADULT),
          "영어회화 학원 홈페이지를 만들어 주세요. 말하기 중심 수업이 돋보이게 해 주세요."),
      new Sample("general", "종합학원", "여러 과목을 한곳에서 안내하는 종합형 홈페이지",
          "STUDY", "영어", Set.of(TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.HIGH),
          "초중고 종합학원 홈페이지를 만들어 주세요. 과목별 수업을 안내하고 상담 신청 버튼을 넣어 주세요."),
      new Sample("premium", "프리미엄", "차분하고 고급스러운 분위기의 홈페이지",
          "DARK", "영어", Set.of(TargetGroup.MIDDLE, TargetGroup.HIGH),
          "프리미엄 소수정예 학원 홈페이지를 만들어 주세요. 고급스럽고 차분한 분위기로 해 주세요."),
      new Sample("minimal", "미니멀", "군더더기 없이 정보에 집중한 깔끔한 홈페이지",
          "MINIMAL", "영어", Set.of(TargetGroup.MIDDLE, TargetGroup.HIGH),
          "깔끔하고 심플한 학원 홈페이지를 만들어 주세요. 꼭 필요한 정보만 보여 주세요."));

  public static List<Sample> all() {
    return ALL;
  }

  public static Optional<Sample> find(String key) {
    if (key == null) return Optional.empty();
    return ALL.stream().filter(s -> s.key().equals(key)).findFirst();
  }
}
