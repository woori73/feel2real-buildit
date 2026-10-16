package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class PublishingMaintenanceIntegrationTest {
  @TempDir Path dir;
  AnnotationConfigWebApplicationContext context;
  PublishingMaintenanceGate gate;
  MockMvc mvc;
  @BeforeEach void setup() throws Exception {
    Path cli=dir.resolve("fake-cli");Files.writeString(cli,"#!/bin/sh\nprintf '{\"stories\":[]}'\n");cli.toFile().setExecutable(true);
    context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
        "publishing.maintenance.state-dir="+dir.resolve("maintenance"),"probe.cli="+cli);
    context.register(Config.class);context.refresh();gate=context.getBean(PublishingMaintenanceGate.class);
    mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(context.getBean(PublishingAdmissionFilter.class)).build();
  }
  @AfterEach void close(){context.close();}
  @Test void onlyAdminCanReadOrControlAndCrossSitePostsAreDenied() throws Exception {
    String url="/api/admin/publishing/maintenance";
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).with(user("tutor").roles("TUTOR"))).andExpect(status().isForbidden());
    mvc.perform(post(url+"/resume").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    mvc.perform(post(url+"/resume").with(user("admin").roles("ADMIN")).header("X-DesignCraft-Maintenance","1").header("Origin","https://evil.example")).andExpect(status().isForbidden());
    mvc.perform(post(url+"/resume").with(user("admin").roles("ADMIN")).header("X-DesignCraft-Maintenance","1").header("Origin","http://localhost")).andExpect(status().isOk());
    mvc.perform(post(url+"/drain").with(user("tutor").roles("TUTOR")).header("X-DesignCraft-Maintenance","1")).andExpect(status().isForbidden());
  }
  @Test void securityRejectsUnauthorizedUsersBeforeMaintenanceResponse() throws Exception {
    assertTrue(PublishingAdmissionFilter.class.getAnnotation(org.springframework.core.annotation.Order.class).value()
        >= 0);
    String url="/api/publishing/protected";
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
    mvc.perform(get(url).with(user("tutor").roles("TUTOR"))).andExpect(status().isServiceUnavailable());
    assertEquals(0,context.getBean(ProbeController.class).writes);
    gate.resume();mvc.perform(get(url).with(user("tutor").roles("TUTOR"))).andExpect(status().isOk());
  }
  @Test void closedAdmissionStopsServiceBeforeSideEffectsAndOtherRoutesStayOpen() throws Exception {
    mvc.perform(post("/api/publishing/jobs")).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","30"));
    mvc.perform(post("/api/publishing;view=preview/jobs")).andExpect(status().isServiceUnavailable());
    assertEquals(0,context.getBean(ProbeController.class).writes);
    for(String path:List.of("/ledger","/builder","/testprep","/photocraft","/login","/api/auth/me","/actuator/health"))
      mvc.perform(get(path)).andExpect(status().isOk());
    gate.resume();mvc.perform(post("/api/publishing/jobs")).andExpect(status().isOk());
    assertEquals(1,context.getBean(ProbeController.class).writes);
  }
  @Test void workerSkipsBeforeRepositoryQueryOrAttemptChanges() {
    var repo=context.getBean(PublishingJobRepository.class);
    context.getBean(PublishingWorker.class).poll();verifyNoInteractions(repo);
    gate.resume();context.getBean(PublishingWorker.class).poll();verify(repo).findQueued(any());
    gate.drain();reset(repo);context.getBean(PublishingWorker.class).poll();verifyNoInteractions(repo);
  }
  @Test void realCliBeanCannotStartWhileClosedEvenFromInternalCall() {
    var cli=context.getBean(DesignCraftCliExecutor.class);
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->cli.inspect(dir.resolve("unused"),dir));
    assertEquals(0,gate.status().liveCliProcesses());
  }
  @Test void asyncCallableKeepsOriginalOperationAcrossDrainAndTwoCliCalls() throws Exception {
    gate.resume();ProbeController controller=context.getBean(ProbeController.class);
    var result=mvc.perform(get("/api/publishing/async")).andExpect(request().asyncStarted()).andReturn();
    assertTrue(controller.started.await(3,TimeUnit.SECONDS));
    gate.drain();assertEquals(1,gate.status().activeOperations());
    controller.finish.countDown();result.getAsyncResult(3000);
    var asyncContext=(org.springframework.mock.web.MockAsyncContext)result.getRequest().getAsyncContext();
    mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    asyncContext.complete();
    awaitClosed();
  }
  @Test void serviceAdvisorRemainsOutsideCommitAndStageCompletion() throws Exception {
    gate.resume();var service=context.getBean(MaintenanceProbeService.class);
    service.commitDuringDrain();assertTrue(context.getBean(ProbeTransactions.class).commitCounted);
    assertTrue(gate.status().singleProcessDrained());
    gate.resume();CompletableFuture<String> stage=service.stage();gate.drain();
    assertEquals(1,gate.status().activeOperations());stage.completeExceptionally(new IllegalStateException());
    awaitClosed();
  }
  @Test void asyncTimeoutDoesNotReleaseStillRunningOperation() throws Exception {
    gate.resume();ProbeController controller=context.getBean(ProbeController.class);controller.ignoreInterrupts=true;
    var result=mvc.perform(get("/api/publishing/async")).andExpect(request().asyncStarted()).andReturn();
    assertTrue(controller.started.await(3,TimeUnit.SECONDS));gate.drain();
    var asyncContext=(org.springframework.mock.web.MockAsyncContext)result.getRequest().getAsyncContext();
    for(var listener:asyncContext.getListeners())listener.onTimeout(new jakarta.servlet.AsyncEvent(asyncContext));
    asyncContext.complete();
    assertEquals(1,gate.status().activeOperations());
    controller.finish.countDown();awaitClosed();
  }
  @Test void servletAsyncStartPropagatesTheAdmittedOperation() throws Exception {
    gate.resume();mvc.perform(get("/api/publishing/raw-async")).andExpect(status().isOk());awaitClosed();
  }
  @Test void requestExceptionReleasesAdmission() throws Exception {
    gate.resume();assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(get("/api/publishing/error")));
    assertEquals(0,gate.status().activeOperations());assertTrue(gate.drain().singleProcessDrained());
  }
  void awaitClosed() throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
    while(!gate.status().singleProcessDrained()&&System.nanoTime()<end)Thread.sleep(10);
    assertTrue(gate.status().singleProcessDrained());
  }
  @Configuration @EnableWebMvc @EnableWebSecurity @EnableTransactionManagement
  @Import({PublishingMaintenanceGate.class,PublishingOperationProxy.class,PublishingMaintenanceController.class})
  static class Config {
    @Bean SecurityFilterChain security(HttpSecurity http)throws Exception {
      return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/publishing/protected").hasRole("TUTOR").anyRequest().permitAll()).httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
    }
    @Bean ProbeController probe(DesignCraftCliExecutor cli,PublishingMaintenanceGate gate){return new ProbeController(cli,gate);}
    @Bean PublishingProperties props(org.springframework.core.env.Environment env){var p=new PublishingProperties();p.setCliPath(env.getRequiredProperty("probe.cli"));return p;}
    @Bean DesignCraftCliExecutor cli(PublishingProperties p){return new DesignCraftCliExecutor(p,new ObjectMapper());}
    @Bean PublishingJobRepository repo(){return mock(PublishingJobRepository.class);}
    @Bean PublishingWorker worker(PublishingJobRepository r,PublishingProperties p){return new PublishingWorker(r,p,mock(TemplateService.class));}
    @Bean MaintenanceProbeService service(PublishingMaintenanceGate g){return new MaintenanceProbeService(g);}
    @Bean ProbeTransactions transactionManager(PublishingMaintenanceGate g){return new ProbeTransactions(g);}
  }
  @RestController static class ProbeController {
    final DesignCraftCliExecutor cli;final PublishingMaintenanceGate gate;int writes;volatile boolean ignoreInterrupts;
    final CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
    ProbeController(DesignCraftCliExecutor cli,PublishingMaintenanceGate gate){this.cli=cli;this.gate=gate;}
    @GetMapping("/api/publishing/raw-async") void raw(jakarta.servlet.http.HttpServletRequest request){
      var async=request.startAsync();
      async.start(()->{try{gate.drain();cli.inspect(Path.of("unused"),Path.of(cliWork()));}finally{async.complete();}});
    }
    @GetMapping("/api/publishing/error") String error(){throw new IllegalStateException("synthetic failure");}
    @GetMapping("/api/publishing/protected") String authorized(){writes++;return "ok";}
    @PostMapping("/api/publishing/jobs") String create(){writes++;return "ok";}
    @GetMapping({"/ledger","/builder","/testprep","/photocraft","/login","/api/auth/me","/actuator/health"}) String other(){return "ok";}
    @GetMapping("/api/publishing/async") Callable<String> async(){return ()->{
      cli.inspect(Path.of("unused"),Path.of(cliWork()));started.countDown();
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
      while(finish.getCount()>0){
        if(System.nanoTime()>deadline)throw new IllegalStateException("test task deadline");
        try{finish.await(50,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){if(!ignoreInterrupts)throw interrupted;}
      }
      cli.inspect(Path.of("unused"),Path.of(cliWork()));return "ok";
    };}
    String cliWork(){return System.getProperty("java.io.tmpdir");}
  }
  static class ProbeTransactions extends AbstractPlatformTransactionManager {
    final PublishingMaintenanceGate gate;boolean commitCounted;
    ProbeTransactions(PublishingMaintenanceGate gate){this.gate=gate;}
    protected Object doGetTransaction(){return new Object();}
    protected void doBegin(Object tx,TransactionDefinition d){}
    protected void doCommit(DefaultTransactionStatus s){commitCounted=gate.status().activeOperations()==1;}
    protected void doRollback(DefaultTransactionStatus s){}
  }
}
