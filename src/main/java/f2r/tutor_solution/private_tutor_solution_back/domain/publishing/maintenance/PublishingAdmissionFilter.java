package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Before multipart/service/transaction side effects; scoped ONLY to publishing API. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PublishingAdmissionFilter extends OncePerRequestFilter {
  private static final String HOLD = PublishingAdmissionFilter.class.getName();
  private final PublishingMaintenanceGate gate;
  public PublishingAdmissionFilter(PublishingMaintenanceGate gate) { this.gate = gate; }
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !(path.equals("/api/publishing") || path.startsWith("/api/publishing/"));
  }
  @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    var previous = (PublishingMaintenanceGate.Hold) request.getAttribute(HOLD);
    PublishingMaintenanceGate.Scope scope;
    try { scope = previous == null ? gate.enter() : previous.bind(); }
    catch (org.springframework.web.server.ResponseStatusException e) {
      response.setStatus(503); response.setHeader("Retry-After", "30");
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"message\":\"DesignCraft 유지보수 중입니다. 잠시 후 다시 시도하세요.\"}"); return;
    }
    try (scope) {
      if (previous != null) { chain.doFilter(request, response); return; }
      var held = gate.holdCurrent(); request.setAttribute(HOLD, held);
      ThreadLocal<PublishingMaintenanceGate.Scope> callableScope = new ThreadLocal<>();
      WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(HOLD, new CallableProcessingInterceptor() {
        @Override public <T> void preProcess(NativeWebRequest r, Callable<T> task) { callableScope.set(held.bind()); }
        @Override public <T> void postProcess(NativeWebRequest r, Callable<T> task, Object result) {
          var bound = callableScope.get(); if (bound != null) bound.close(); callableScope.remove();
        }
      });
      HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
        private AsyncContext tracked(AsyncContext delegate) {
          return (AsyncContext) java.lang.reflect.Proxy.newProxyInstance(AsyncContext.class.getClassLoader(),
              new Class<?>[]{AsyncContext.class}, (proxy, method, args) -> {
                if (method.getName().equals("start") && args != null && args.length == 1) {
                  try (var bound = held.bind()) {
                    gate.submit(delegate::start, () -> { ((Runnable) args[0]).run(); return null; });
                  }
                  return null;
                }
                try { return method.invoke(delegate, args); }
                catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
              });
        }
        @Override public AsyncContext startAsync() { return tracked(super.startAsync(this, response)); }
        @Override public AsyncContext startAsync(ServletRequest r, ServletResponse s) { return tracked(super.startAsync(r,s)); }
        @Override public AsyncContext getAsyncContext() { return tracked(super.getAsyncContext()); }
      };
      try { chain.doFilter(wrapped, response); }
      finally {
        if (!request.isAsyncStarted()) held.close();
        else {
          AsyncListener listener = new AsyncListener() {
            public void onComplete(AsyncEvent event) { held.close(); }
            public void onTimeout(AsyncEvent event) { /* Complete/task finally releases, not timeout signal. */ }
            public void onError(AsyncEvent event) { /* A running task must remain counted. */ }
            public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
          };
          try { request.getAsyncContext().addListener(listener); }
          catch (IllegalStateException finished) { held.close(); }
        }
      }
    }
  }
}
