package f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster;

import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Uses the existing Spring proxy facilities; no new orchestration library or server. */
@Configuration(proxyBeanMethods = false)
public class ClusterScheduledGate {
  private static final Map<String, String> METHODS = Map.of(
      "WemadeCollectionScheduler.collectAfterMarketClose", "wemade-close",
      "WemadeCollectionScheduler.collectIntradayAnomalyMetrics", "wemade-intraday",
      "EasyCollectionSchedulerService.runScheduledCollections", "easy-collection",
      "NewStartOfficialUpdateDiscoveryService.scheduledDiscoveryMorning", "discovery-morning",
      "NewStartOfficialUpdateDiscoveryService.scheduledDiscoveryEvening", "discovery-evening",
      "AlbAccessStatisticsWorker.poll", "alb-statistics");

  @Bean
  static BeanPostProcessor clusterScheduleInterceptor(ObjectProvider<ClusterScheduleLeader> leader) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        String type = AopUtils.getTargetClass(bean).getSimpleName();
        if (METHODS.keySet().stream().noneMatch(key -> key.startsWith(type + "."))) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
          String purpose = METHODS.get(type + "." + invocation.getMethod().getName());
          if (purpose == null) return invocation.proceed();
          return leader.getObject().execute(invocation::proceed);
        });
        return proxy.getProxy();
      }
    };
  }
}
