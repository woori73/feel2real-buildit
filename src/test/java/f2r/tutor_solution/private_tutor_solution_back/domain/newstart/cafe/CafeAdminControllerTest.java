package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CafeAdminControllerTest {
  @Test
  void givenAdminPage_whenRendering_thenFormsKeepOriginAndCallbackKeepsCodePrivate() {
    var service = mock(CafeService.class);
    var controller = new CafeAdminController(service, new CafeProperties());
    var p = new AuthenticatedPrincipal(1L, "operator", "ADMIN");
    var pageResponse = mock(jakarta.servlet.http.HttpServletResponse.class);
    assertEquals("admin/newstart_cafe", controller.page(p, new org.springframework.ui.ExtendedModelMap(), pageResponse));
    verify(pageResponse).setHeader("Referrer-Policy", "same-origin");
    verify(pageResponse).setHeader("Cache-Control", "no-store");

    var callbackResponse = mock(jakarta.servlet.http.HttpServletResponse.class);
    assertEquals("redirect:/admin/newstart/cafe?connected=1", controller.callback(p, "state", "code", null, callbackResponse));
    verify(callbackResponse).setHeader("Referrer-Policy", "no-referrer");
  }

  @Test
  void givenCrossOrigin_whenConnectingOrSaving_thenNoStateChanges() {
    var service = mock(CafeService.class);
    var controller = new CafeAdminController(service, new CafeProperties());
    var request = mock(HttpServletRequest.class);
    when(request.getHeader("Origin")).thenReturn("https://example.invalid");
    var p = new AuthenticatedPrincipal(1L, "operator", "ADMIN");
    assertThrows(ResponseStatusException.class, () -> controller.connect(p, request));
    assertThrows(ResponseStatusException.class, () -> controller.boards(p, java.util.Map.of(), request));
    assertThrows(ResponseStatusException.class, () -> controller.disconnect(p, request));
    verifyNoInteractions(service);
  }

  @Test
  void givenNonAdmin_whenReadingOrPublishing_thenDeniedBeforeStorage() {
    var p = new AuthenticatedPrincipal(1L, "member", "TUTOR");
    var config = new CafeProperties();
    var service = new CafeService(null, null, config, null, null, mock(org.springframework.transaction.PlatformTransactionManager.class));
    assertThrows(ResponseStatusException.class, () -> service.status(p));
    assertThrows(ResponseStatusException.class, () -> service.history(p));
    assertThrows(ResponseStatusException.class, () -> service.publish(p, "grant", null));
  }
}
