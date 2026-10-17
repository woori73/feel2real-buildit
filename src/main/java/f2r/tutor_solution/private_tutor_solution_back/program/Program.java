package f2r.tutor_solution.private_tutor_solution_back.program;

import java.util.List;
import java.util.Set;

/**
 * 비회원에게 공개 첫 화면을 보여주고, 실제 기능을 시작할 때 네이버 로그인을 요청하는 프로그램 목록.
 *
 * <p>프로그램을 추가하려면 여기에 한 줄을 더하고 해당 Controller가 {@link ProgramEntry}를 쓰게 하면 된다.
 * 권한({@code roles})은 기존 SecurityConfig 정책을 그대로 옮긴 값이며, 이 목록이 권한을 완화하지 않는다.
 */
public enum Program {
  DESIGNCRAFT(
      "DesignCraft",
      "출판 편집기",
      "원고를 불러와 페이지를 편집하고, 서식을 검수한 뒤 PDF 등으로 내보내는 출판 편집기입니다.",
      List.of(
          "원고 업로드와 페이지 미리보기",
          "편집 명령과 마크다운 스타일 적용",
          "AI 편집 계획·수정 요청",
          "서식 검수(프리플라이트)",
          "내보내기 산출물 내려받기"),
      "/designcraft",
      "/designcraft/editor",
      Set.of("TUTOR", "ADMIN"),
      "DesignCraft는 강사(TUTOR) 또는 관리자(ADMIN) 계정 전용입니다."),

  BUILDER(
      "BuildIt 홈페이지 제작",
      "AI 학원 홈페이지 제작기",
      "한 문장으로 학원 홈페이지를 만들고, 편집기에서 문구·이미지·디자인을 다듬어 게시하는 제작기입니다.",
      List.of(
          "한 문장 제작 요청으로 홈페이지 초안 생성",
          "시각 편집기로 문구·이미지·디자인·섹션 수정",
          "미리보기로 확인한 뒤 게시",
          "학원 공개 주소(…feel2real.co.kr) 발급",
          "샘플 홈페이지 갤러리 둘러보기"),
      "/builder",
      "/builder",
      Set.of("TUTOR", "ADMIN"),
      "홈페이지 제작은 강사(TUTOR) 또는 관리자(ADMIN) 계정 전용입니다. 일반 회원은 공개된 학원 홈페이지와 샘플을 둘러볼 수 있습니다."),

  PROBLEM_GENERATOR(
      "내신위키",
      "BuildIt 내신위키",
      "학교 프린트·학생 오답·수업 인사이트를 모으고, 문제를 모아 내신 영어 문제지를 만들고, 오답 노트로 복습하는 내신위키입니다.",
      List.of(
          "학교·교재별 자료 선택",
          "문제 모음에서 문항 고르기",
          "내신 영어 문제지(시험) 제작",
          "오답 노트로 복습"),
      "/problem-generator",
      "/problem-generator",
      Set.of("STUDENT", "TUTOR", "ADMIN"),
      "문제지 제작기는 로그인한 학생·강사·관리자 계정이 이용할 수 있습니다. 현재 계정의 권한으로는 이용할 수 없습니다."),

  EXAM_ANALYSIS(
      "내신영어 기출분석 + 출제방향 제작",
      "BuildIt 기출분석",
      "최근 학교 기출문제와 이번 시험범위 자료를 올리면 AI가 기출문제를 분석하고 다음 시험의 출제방향을 정리해 드립니다.",
      List.of(
          "기출문제 유형·비중·난이도 분석",
          "시험범위 자료와 기출의 연결 정리",
          "출제방향 문서(.md) 다운로드",
          "분석 자료로 예상문제 제작 의뢰"),
      "/exam-analysis",
      "/exam-analysis",
      Set.of("STUDENT", "TUTOR", "ADMIN"),
      "기출분석은 로그인한 학생·강사·관리자 계정이 이용할 수 있습니다. 현재 계정의 권한으로는 이용할 수 없습니다."),

  LEDGER(
      "이지장부",
      "학원 전용 AI 회계",
      "학원 수입·지출 내역을 CSV로 입력하면 AI가 회계 계정을 추천하고, 확인·승인한 내용을 복식분개, 총계정원장, 시산표로 정리합니다.",
      List.of(
          "거래 내역 CSV 입력",
          "AI 회계 계정 추천",
          "사용자가 확인·승인한 뒤에만 장부 반영",
          "복식분개·총계정원장·시산표 정리"),
      "/ledger",
      "/ledger",
      Set.of("TUTOR", "ADMIN"),
      "이지장부는 원장(강사, TUTOR) 또는 관리자(ADMIN) 계정 전용입니다."),

  EASY(
      "Easy 회계·학생원장",
      "관리자 전용 운영 도구",
      "학원 회계와 학생 원장·수납을 관리자가 한곳에서 확인하고 검토하는 관리자 전용 운영 도구입니다.",
      List.of(
          "대시보드와 거래 내역 관리",
          "학생 원장과 수납 관리",
          "카드 거래·증빙 검토",
          "회계(분개·원장) 확인"),
      "/easy",
      "/easy",
      Set.of("ADMIN"),
      "Easy 회계·학생원장은 관리자(ADMIN) 계정 전용입니다. 원장(강사) 계정은 이지장부(/ledger)를 이용해 주세요.");

  private final String title;
  private final String subtitle;
  private final String summary;
  private final List<String> features;
  private final String introPath;
  private final String startPath;
  private final Set<String> roles;
  private final String roleNotice;

  Program(
      String title,
      String subtitle,
      String summary,
      List<String> features,
      String introPath,
      String startPath,
      Set<String> roles,
      String roleNotice) {
    this.title = title;
    this.subtitle = subtitle;
    this.summary = summary;
    this.features = features;
    this.introPath = introPath;
    this.startPath = startPath;
    this.roles = roles;
    this.roleNotice = roleNotice;
  }

  public String getTitle() { return title; }
  public String getSubtitle() { return subtitle; }
  public String getSummary() { return summary; }
  public List<String> getFeatures() { return features; }
  public String getIntroPath() { return introPath; }
  public String getStartPath() { return startPath; }
  public Set<String> getRoles() { return roles; }
  public String getRoleNotice() { return roleNotice; }

  /** 로그인 후 돌아올 URL이 이 프로그램 영역({@code introPath} 이하)인지. 쿼리스트링은 무시하고 경로만 본다. */
  public boolean ownsReturnUrl(String url) {
    if (url == null) return false;
    int q = url.indexOf('?');
    String path = q < 0 ? url : url.substring(0, q);
    return path.equals(introPath) || path.startsWith(introPath + "/");
  }

  /** 정확히 소개 경로({@code introPath})와 같은 요청 경로의 프로그램. 하위 경로는 해당하지 않는다. */
  public static java.util.Optional<Program> forIntroPath(String path) {
    for (Program p : values()) {
      if (p.introPath.equals(path)) return java.util.Optional.of(p);
    }
    return java.util.Optional.empty();
  }

  /** 하위 경로까지 포함해 URL이 속한 프로그램. */
  public static java.util.Optional<Program> forPath(String path) {
    for (Program p : values()) {
      if (p.ownsReturnUrl(path)) return java.util.Optional.of(p);
    }
    return java.util.Optional.empty();
  }

  /** 등록된 어떤 프로그램 영역의 URL이면 true. 로그인 시작 엔드포인트가 임의 경로를 복귀 대상으로 받지 않게 한다. */
  public static boolean isRegisteredReturnUrl(String url) {
    for (Program p : values()) {
      if (p.ownsReturnUrl(url)) return true;
    }
    return false;
  }
}
