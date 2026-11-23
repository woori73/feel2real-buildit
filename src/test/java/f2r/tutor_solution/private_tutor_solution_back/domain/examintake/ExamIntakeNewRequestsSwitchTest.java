package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

/** 2026-11-23 대표 지시: 맞춤문제 새 신청만 막고 기존 신청자의 접수 확인은 그대로 둔다. */
class ExamIntakeNewRequestsSwitchTest {
  final AuthenticatedPrincipal owner = new AuthenticatedPrincipal(1L, "owner", "STUDENT", null);
  final Create input = new Create(Kind.APPLICATION, Map.of(), true, CONSENT, "switch-test-key-0001");

  MockHttpServletRequest sameSitePost() {
    var request = new MockHttpServletRequest("POST", "/api/exam-intake/requests");
    request.addHeader("X-F2R-Exam", "1");
    request.addHeader("Origin", "http://localhost");
    return request;
  }

  @Test
  @DisplayName("given 새 신청 스위치 꺼짐(기본) when 신청을 만들면 then 410과 서비스 도입 문의 안내를 돌려주고 접수는 만들지 않는다")
  void givenClosed_whenCreate_thenGoneAndNothingSaved() {
    // given
    var service = mock(ExamIntakeService.class);
    var controller = new ExamIntakeController(service);
    // when / then
    assertThatThrownBy(() -> controller.create(owner, input, sameSitePost()))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GONE);
          assertThat(e.getReason()).contains("서비스 도입 문의");
        });
    verify(service, never()).create(any(), any());
  }

  @Test
  @DisplayName("given 새 신청 스위치 꺼짐 when 신청 설정을 읽으면 then acceptingNew=false로 화면이 신청서를 숨길 수 있다")
  void givenClosed_whenConfig_thenNotAcceptingNew() {
    // given
    var controller = new ExamIntakeController(mock(ExamIntakeService.class));
    // when
    var config = controller.config();
    // then
    assertThat(config).containsEntry("acceptingNew", false).containsEntry("enabled", true);
  }

  @Test
  @DisplayName("given 새 신청 스위치 켜짐 when 신청을 만들면 then 기존처럼 서비스가 접수를 만든다")
  void givenOpen_whenCreate_thenDelegatesToService() {
    // given
    var service = mock(ExamIntakeService.class);
    var controller = new ExamIntakeController(service, true);
    // when
    controller.create(owner, input, sameSitePost());
    // then
    verify(service).create(owner, input);
    assertThat(controller.config()).containsEntry("acceptingNew", true);
  }

  @Test
  @DisplayName("given 새 신청 스위치 꺼짐 when 로그인한 사람이 신청 화면을 열면 then 화면은 열리고(기존 접수 확인) 모델에 acceptingNew=false가 담긴다")
  void givenClosed_whenOpenPage_thenPageShowsWithClosedFlag() {
    // given
    var page = new ExamIntakePageController(false);
    var model = new ExtendedModelMap();
    // when
    String view = page.page(owner, model, "");
    // then
    assertThat(view).isEqualTo("public/exam_service");
    assertThat(model).containsEntry("acceptingNew", false).containsEntry("adminView", false);
  }
}
