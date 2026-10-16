package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Infrastructure advisor is OUTSIDE transaction commit, including direct internal service calls. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class PublishingOperationProxy {
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public Advisor publishingOperationAdvisor(PublishingMaintenanceGate gate) {
    var pointcut = new StaticMethodMatcherPointcut() {
      @Override public boolean matches(Method method, Class<?> type) {
        return type.getPackageName().equals("f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service")
            && method.getDeclaringClass() != Object.class;
      }
    };
    MethodInterceptor interceptor = invocation -> {
      PublishingMaintenanceGate.Scope scope;
      try { scope = gate.enter(); }
      catch (org.springframework.web.server.ResponseStatusException e) {
        if (invocation.getThis() instanceof f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingWorker
            && invocation.getMethod().getName().equals("poll")) return null;
        throw e;
      }
      try (scope) {
        Object result = invocation.proceed();
        if (result instanceof CompletionStage<?> stage) {
          AutoCloseable retained = gate.retainCurrent();
          try { stage.whenComplete((value, failure) -> close(retained)); }
          catch (Throwable e) { close(retained); throw e; }
        }
        return result;
      }
    };
    var advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
    advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return advisor;
  }
  private static void close(AutoCloseable held) {
    try { held.close(); } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
