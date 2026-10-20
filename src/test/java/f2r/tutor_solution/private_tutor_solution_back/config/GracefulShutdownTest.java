package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.tomcat.autoconfigure.WebSocketTomcatWebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 배포 중단 완화 0단계 — 종료 구간(graceful shutdown) 계약.
 *
 * <p>무엇을 보증하는가:
 * <ul>
 *   <li>실제 application.yaml(기본·prd 프로파일)이 graceful + 30초 대기를 명시한다(기본값에 기대지 않는다).
 *   <li>그 설정대로면 처리 중인 요청은 종료 신호 뒤에도 끝까지 응답하고, 새 연결은 거절된다.
 *   <li>열려 있는 WebSocket 연결은 graceful 종료를 막지 않는다(배포 지연 방지).
 * </ul>
 *
 * <p>무엇을 보증하지 않는가: 새 JVM이 뜨는 동안의 서비스 공백(기동 시간)은 이 설정과 무관하다.
 */
class GracefulShutdownTest {

  /** systemd 기본 TimeoutStopSec. graceful 대기는 이보다 충분히 짧아야 SIGKILL 전에 끝난다. */
  private static final Duration SYSTEMD_DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(90);

  private WebServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      try {
        server.stop();
      } catch (RuntimeException ignored) {
        // 이미 중지된 경우
      }
    }
  }

  @ParameterizedTest(name = "[{0}] 프로파일")
  @ValueSource(strings = {"local", "prd"})
  @DisplayName("given 실제 application.yaml when 설정을 바인딩하면 then graceful 이고 대기시간은 systemd 기본 종료 제한보다 짧다")
  void configPinsGracefulShutdownWithBoundedTimeout(String profile) {
    // given
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(EmptyConfig.class)
            .web(WebApplicationType.NONE)
            .properties("spring.profiles.active=" + profile, "spring.main.banner-mode=off")
            .run()) {
      Binder binder = Binder.get(context.getEnvironment());

      // when
      Shutdown mode = binder.bind("server.shutdown", Shutdown.class).orElse(null);
      Duration timeout =
          binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).orElse(null);

      // then
      assertThat(mode).isEqualTo(Shutdown.GRACEFUL);
      assertThat(timeout).isEqualTo(Duration.ofSeconds(30));
      assertThat(timeout).isLessThan(SYSTEMD_DEFAULT_STOP_TIMEOUT);
    }
  }

  @Test
  @DisplayName("given graceful when 처리 중 요청이 있는 상태로 종료를 시작하면 then 그 요청은 끝까지 응답하고 새 연결은 거절된다")
  void inFlightRequestCompletesAndNewConnectionsAreRefused() throws Exception {
    // given
    server = startServer(Shutdown.GRACEFUL, false);
    int port = server.getPort();
    HttpClient client = HttpClient.newHttpClient();
    CompletableFuture<HttpResponse<String>> inFlight =
        client.sendAsync(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/slow")).build(),
            HttpResponse.BodyHandlers.ofString());
    Thread.sleep(500); // 서버가 요청을 받아 처리 중인 상태

    // when
    CountDownLatch drained = new CountDownLatch(1);
    long start = System.nanoTime();
    server.shutDownGracefully(result -> drained.countDown());
    Thread.sleep(300);

    // then: 새 연결은 거절
    assertThatThrownBy(
            () ->
                HttpClient.newHttpClient()
                    .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/slow"))
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                        HttpResponse.BodyHandlers.ofString()))
        .isInstanceOf(IOException.class);
    // then: 진행 중이던 요청은 정상 응답
    HttpResponse<String> response = inFlight.get(10, TimeUnit.SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("done");
    // then: 요청이 끝난 뒤에 종료가 완료된다(= 요청이 끝날 때까지 기다렸다)
    assertThat(drained.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThan(Duration.ofMillis(1200));
  }

  @Test
  @DisplayName("given 유휴 서버 when graceful 종료하면 then 지연 없이 바로 끝난다")
  void idleServerShutsDownImmediately() throws Exception {
    // given
    server = startServer(Shutdown.GRACEFUL, false);
    CountDownLatch drained = new CountDownLatch(1);
    GracefulShutdownResult[] holder = new GracefulShutdownResult[1];

    // when
    long start = System.nanoTime();
    server.shutDownGracefully(
        result -> {
          holder[0] = result;
          drained.countDown();
        });

    // then
    assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(holder[0]).isEqualTo(GracefulShutdownResult.IDLE);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }

  @Test
  @DisplayName("given 열려 있는 WebSocket 연결 when graceful 종료하면 then 연결이 종료를 막지 않는다")
  void openWebSocketDoesNotBlockGracefulShutdown() throws Exception {
    // given
    server = startServer(Shutdown.GRACEFUL, true);
    int port = server.getPort();
    CountDownLatch opened = new CountDownLatch(1);
    CountDownLatch closed = new CountDownLatch(1);
    WebSocket.Listener listener =
        new WebSocket.Listener() {
          @Override
          public void onOpen(WebSocket webSocket) {
            opened.countDown();
            webSocket.request(1);
          }

          @Override
          public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.countDown();
            return null;
          }

          @Override
          public void onError(WebSocket webSocket, Throwable error) {
            closed.countDown();
          }
        };
    try {
      HttpClient.newHttpClient()
          .newWebSocketBuilder()
          .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/echo"), listener)
          .get(5, TimeUnit.SECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      if (e.getCause() instanceof java.net.http.WebSocketHandshakeException h) {
        throw new AssertionError("handshake status=" + h.getResponse().statusCode(), e);
      }
      throw e;
    }
    assertThat(opened.await(5, TimeUnit.SECONDS)).isTrue();

    // when
    CountDownLatch drained = new CountDownLatch(1);
    long start = System.nanoTime();
    server.shutDownGracefully(result -> drained.countDown());

    // then: 연결이 열려 있어도 graceful 종료는 즉시 끝난다
    assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    // then: 서버를 내리면 클라이언트는 연결 종료를 감지한다(재접속 필요)
    server.stop();
    assertThat(closed.await(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("given 종료된 서버 when 접속하면 then 연결 거절(ConnectException)")
  void stoppedServerRefusesConnections() throws Exception {
    // given
    server = startServer(Shutdown.GRACEFUL, false);
    int port = server.getPort();
    server.stop();

    // when / then
    assertThatThrownBy(
            () ->
                HttpClient.newHttpClient()
                    .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/slow")).build(),
                        HttpResponse.BodyHandlers.ofString()))
        .isInstanceOf(ConnectException.class);
  }

  private static WebServer startServer(Shutdown mode, boolean withWebSocket) {
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
    factory.setShutdown(mode);
    if (withWebSocket) {
      // Spring Boot가 운영에서 쓰는 것과 같은 WebSocket용 Tomcat 설정
      new WebSocketTomcatWebServerFactoryCustomizer().customize(factory);
    }
    WebServer web =
        factory.getWebServer(
            servletContext -> {
              var registration =
                  servletContext.addServlet(
                      "slow",
                      new HttpServlet() {
                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                            throws IOException {
                          try {
                            Thread.sleep(2000); // 처리 시간이 긴 요청
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                          resp.setStatus(200);
                          resp.getWriter().write("done");
                        }
                      });
              // "/"(기본 서블릿)도 매핑해야 WebSocket 필터 체인이 /ws/echo 요청에 적용된다
              registration.addMapping("/slow", "/");
            });
    web.start();
    if (withWebSocket) {
      registerEndpoint((TomcatWebServer) web);
    }
    return web;
  }

  private static void registerEndpoint(TomcatWebServer web) {
    try {
      Context context = (Context) web.getTomcat().getHost().findChildren()[0];
      ServerContainer container =
          (ServerContainer) context.getServletContext().getAttribute(ServerContainer.class.getName());
      container.addEndpoint(EchoEndpoint.class);
    } catch (DeploymentException e) {
      throw new IllegalStateException(e);
    }
  }

  @Configuration
  static class EmptyConfig {}

  /** 테스트용 WebSocket 엔드포인트: 연결만 열어 두고 아무 것도 보내지 않는다. */
  @ServerEndpoint("/ws/echo")
  public static class EchoEndpoint {
    @OnOpen
    public void onOpen(Session session) {
      // 연결 유지
    }
  }
}
