package f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 랜딩 페이지에서 관리할 수 있는 고정 영상 슬롯.
 *
 * <p>enum 선언 순서는 랜딩 및 관리자 화면의 표시 순서다. 슬롯은 런타임에 추가하거나 삭제하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum LandingVideoSlotKey {

  HERO_BACKGROUND(
      "히어로 배경",
      "랜딩 첫 화면의 자동 재생 배경 영상",
      "/video/landing/hero-background.mp4"),
  ACADEMY_SHORTS_DEMO(
      "학원 홍보 쇼츠",
      "학원 홍보 쇼츠 제작 예시 영상",
      "/video/landing/academy-shorts-demo.mp4"),
  PARENT_REPORT_DEMO(
      "학부모 리포트",
      "학부모 리포트 작성 예시 영상",
      "/video/landing/parent-report-demo.mp4");

  private final String label;
  private final String description;
  private final String bundledPath;

  public static List<LandingVideoSlotKey> displayOrder() {
    return Arrays.asList(values());
  }
}
