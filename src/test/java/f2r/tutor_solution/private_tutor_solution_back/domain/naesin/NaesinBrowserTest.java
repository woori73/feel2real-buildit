package f2r.tutor_solution.private_tutor_solution_back.domain.naesin;

import static org.junit.jupiter.api.Assertions.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import jakarta.servlet.MultipartConfigElement;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.context.annotation.*;
import org.springframework.core.MethodParameter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.context.request.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.*;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/** Loopback-only real MVC/JDBC/disk browser fixture. Production authorization is tested separately. */
@EnabledIfEnvironmentVariable(named="NAESIN_BROWSER_TEST",matches="true")
class NaesinBrowserTest {
  @TempDir Path directory;
  static NaesinProductionTest fixture;
  @org.springframework.boot.test.context.TestConfiguration @EnableWebMvc @EnableTransactionManagement
  static class Web implements WebMvcConfigurer {
    @Bean NaesinProductionService service(){return fixture.service;}
    @Bean PlatformTransactionManager transactionManager(){return new DataSourceTransactionManager(fixture.db.getDataSource());}
    @Bean NaesinProductionController controller(NaesinProductionService service){var c=new NaesinProductionController(service);ReflectionTestUtils.setField(c,"publicBase",base);return c;}
    @Bean StandardServletMultipartResolver multipartResolver(){return new StandardServletMultipartResolver();}
    @Override public void addResourceHandlers(ResourceHandlerRegistry r){r.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");r.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");}
    @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers){resolvers.add(new HandlerMethodArgumentResolver(){
      public boolean supportsParameter(MethodParameter p){return p.getParameterType()==AuthenticatedPrincipal.class;}
      public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,org.springframework.web.bind.support.WebDataBinderFactory f){return fixture.admin;}
    });}
    @Bean ViewResolver views(){return (name,locale)->new AbstractView(){protected void renderMergedOutputModel(Map<String,Object> m,jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res)throws Exception{
      res.setContentType("text/html;charset=UTF-8");try(var in=getClass().getResourceAsStream("/templates/"+name+".html")){res.getOutputStream().write(Objects.requireNonNull(in).readAllBytes());}
    }};}
  }
  static String base;
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings={"MIDDLE","HIGH"})
  void givenSchoolDivision_whenUploadRevisionReviewPublishAndMobileNavigation_thenRealPipelineWorks(String division) throws Exception {
    fixture=new NaesinProductionTest();fixture.dir=directory.resolve("files");Files.createDirectories(fixture.dir);fixture.setup();
    String project=fixture.project;
    String grade=division.equals("HIGH")?"고1":"중2", candidateTitle=grade+" 완성본 후보";
    if(division.equals("HIGH"))project=fixture.json.valueToTree(fixture.call(()->fixture.service.create(fixture.admin,new NaesinProductionService.Project("고1 시험 제작","시험고","고1","학력평가","HIGH")))).path("id").asText();
    var tomcat=new Tomcat();tomcat.setBaseDir(directory.resolve("tomcat").toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
    var ctx=tomcat.addContext("",directory.toString());var web=new AnnotationConfigWebApplicationContext();web.register(Web.class);
    var wrapper=Tomcat.addServlet(ctx,"mvc",new DispatcherServlet(web));wrapper.setLoadOnStartup(1);
    wrapper.setAsyncSupported(true);
    wrapper.setMultipartConfigElement(new MultipartConfigElement(directory.toString(),20971520,22020096,0));ctx.addServletMappingDecoded("/","mvc");
    ChromeDriver driver=null;
    try {
      tomcat.start();base="http://127.0.0.1:"+tomcat.getConnector().getLocalPort();
      ReflectionTestUtils.setField(web.getBean(NaesinProductionController.class),"publicBase",base);
      // Controller initializes on first request, after the ephemeral port is known.
      var opts=new ChromeOptions();opts.addArguments("--headless=new","--no-sandbox","--disable-dev-shm-usage","--window-size=1440,1000");
      if(System.getenv("NAESIN_CHROME_BINARY")!=null)opts.setBinary(System.getenv("NAESIN_CHROME_BINARY"));
      driver=System.getenv("NAESIN_CHROMEDRIVER")==null?new ChromeDriver(opts):new ChromeDriver(new ChromeDriverService.Builder().usingDriverExecutable(new java.io.File(System.getenv("NAESIN_CHROMEDRIVER"))).build(),opts);
      final WebDriver browser=driver;
      var wait=new WebDriverWait(browser,Duration.ofSeconds(20));
      driver.get(base+"/naesin-production-studio?division="+division+"&projectId="+project);
      wait.until(d->d.findElement(By.id("project-title")).getText().contains(division.equals("HIGH")?"고1":"신구중"));
      if(division.equals("MIDDLE")){
        driver.findElement(By.id("division-high")).click();wait.until(d->new Select(d.findElement(By.name("grade"))).getFirstSelectedOption().getAttribute("value").equals("고1"));
        assertTrue(driver.findElement(By.id("project-form")).findElement(By.name("grade")).isEnabled());
        driver.findElement(By.id("division-middle")).click();wait.until(d->d.findElement(By.id("project-title")).getText().contains("신구중") && d.findElement(By.id("workspace")).isDisplayed());
      }
      Path original=directory.resolve(grade+"_시험.hwp");Files.write(original,NaesinTextExtractorTest.fixture(grade,true,false,false));
      driver.findElement(By.cssSelector("#upload-form input[type=file]")).sendKeys(original.toString());
      driver.findElement(By.cssSelector("#upload-form [name=source]")).sendKeys("중등부 격리 검증 표본");driver.findElement(By.cssSelector("#upload-form [name=rights]")).sendKeys("테스트용 직접 작성");
      driver.findElement(By.cssSelector("#upload-form button")).click();wait.until(d->d.findElement(By.id("message")).getText().contains("재조회"));
      assertTrue(driver.findElement(By.id("files")).getText().contains(grade+"_시험.hwp"));
      driver.findElement(By.cssSelector("#files button")).click();wait.until(d->d.findElement(By.id("detail-content")).getText().contains("정답"));
      ((JavascriptExecutor)driver).executeScript("document.querySelector('#work-form').closest('details').open=true");
      var form=driver.findElement(By.id("work-form"));new Select(form.findElement(By.name("category"))).selectByValue("RESULT");
      for(var e:Map.of("title",candidateTitle,"goal","문항 검증","prompt","원본·정답 확인","model","test-no-llm","tools","HWP5 parser","result","문항·정답 대조 완료","nextWork","최신 승인본 확인").entrySet())form.findElement(By.name(e.getKey())).sendKeys(e.getValue());
      new Select(form.findElement(By.name("sourceFileIds"))).selectByIndex(0);new Select(form.findElement(By.name("fileId"))).selectByIndex(1);new Select(form.findElement(By.name("status"))).selectByValue("REVIEW");
      form.findElement(By.cssSelector("button")).click();wait.until(d->d.findElement(By.id("detail-title")).getText().equals(candidateTitle));
      Map<String,String> checkLabels=Map.of("QUESTION","문항·정답","EXPLANATION","해설","FORMAT","서식","RIGHTS","이용 권한");
      for(String dimension:NaesinProductionService.CHECKS){
        var check=driver.findElement(By.id("check-form"));new Select(check.findElement(By.name("dimension"))).selectByValue(dimension);
        var note=check.findElement(By.name("note"));note.clear();note.sendKeys("격리 표본 검수 완료");clickWithoutPendingScroll(driver,check.findElement(By.tagName("button")));
        // An enabled button also describes a missed click or a rejected request.
        // Wait for the persisted result rendered by GET revision, not merely finally{}.
        wait.withMessage(()->"Check "+dimension+" was not saved. "+browserState(browser))
          .until(d->d.findElement(By.id("check-status")).getText().contains(checkLabels.get(dimension)+": 통과")
            && d.findElement(By.cssSelector("#check-form button")).isEnabled());
      }
      var decision=driver.findElement(By.id("decision-form"));new Select(decision.findElement(By.name("action"))).selectByValue("APPROVE");decision.findElement(By.name("note")).sendKeys("최종 승인");clickWithoutPendingScroll(driver,decision.findElement(By.tagName("button")));
      wait.withMessage(()->"Final review was not saved. "+browserState(browser))
        .until(d->d.findElement(By.id("message")).getText().contains("최종 판단") && d.findElement(By.cssSelector("#decision-form button")).isEnabled());
      clickWithoutPendingScroll(driver,driver.findElement(By.id("publish")));
      wait.withMessage(()->"Publication was not completed. "+browserState(browser))
        .until(d->d.findElement(By.id("message")).getText().contains("게시 승인 완료"));
      driver.findElement(By.id("query")).sendKeys("문항 검증");driver.findElement(By.id("search")).click();
      wait.until(d->d.findElements(By.cssSelector("#files article")).isEmpty());
      driver.findElement(By.cssSelector("#revisions button")).click();wait.until(d->d.findElement(By.id("detail")).isDisplayed());
      driver.findElement(By.id("revise")).click();wait.until(d->d.findElement(By.id("message")).getText().contains("새 리비전"));
      assertEquals(1,new Select(driver.findElement(By.id("source-files"))).getAllSelectedOptions().size());
      for(int width:new int[]{1440,390}){driver.manage().window().setSize(new Dimension(width,900));assertEquals(0L,((Number)((JavascriptExecutor)driver).executeScript("return Math.max(0,document.documentElement.scrollWidth-innerWidth)")).longValue());
        ((JavascriptExecutor)driver).executeScript("scrollTo(0,0)");
        if(System.getenv("NAESIN_BROWSER_EVIDENCE")!=null){Path evidence=Path.of(System.getenv("NAESIN_BROWSER_EVIDENCE"));Files.createDirectories(evidence);Files.copy(driver.getScreenshotAs(OutputType.FILE).toPath(),evidence.resolve(division+"-workspace-"+width+".png"),StandardCopyOption.REPLACE_EXISTING);}
      }
      driver.get(base+"/naesin-wiki/published?division="+division);wait.until(d->d.findElements(By.cssSelector("#publications article")).size()==1);
      assertTrue(driver.findElement(By.id("publications")).getText().contains(candidateTitle));
      var link=driver.findElement(By.cssSelector("#publications a")).getAttribute("href");
      var download=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(link)).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200,download.statusCode());assertArrayEquals(Files.readAllBytes(original),download.body());
      System.out.println("NAESIN_BROWSER_QA: "+division+" HWP upload/extraction, work/revision, four checks, approval/publication, fixed download, 1440/390 overflow=0 PASS");
    } catch(Exception | AssertionError failure) {
      if(driver!=null) {
        try {
          Path evidence=Path.of("build/reports/naesin-browser-failure",division);Files.createDirectories(evidence);
          Files.writeString(evidence.resolve("state.txt"),browserState(driver));
          Files.writeString(evidence.resolve("page.html"),driver.getPageSource());
          Files.copy(driver.getScreenshotAs(OutputType.FILE).toPath(),evidence.resolve("screen.png"),StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception evidenceFailure) { failure.addSuppressed(evidenceFailure); }
      }
      throw failure;
    } finally {if(driver!=null)driver.quit();tomcat.stop();tomcat.destroy();fixture.clear();}
  }

  private static void clickWithoutPendingScroll(WebDriver driver, WebElement target) {
    // open() starts smooth scrolling after each check. Stop that motion before the
    // next native pointer click so mouse-down/up cannot land on different controls.
    ((JavascriptExecutor)driver).executeScript("arguments[0].scrollIntoView({behavior:'instant',block:'center'})",target);
    target.click(); // Native WebDriver click, exactly once; no JS click or request retry.
  }

  private static String browserState(WebDriver driver) {
    return "message="+driver.findElement(By.id("message")).getText()
      +"; checks="+driver.findElement(By.id("check-status")).getText()
      +"; detail="+driver.findElement(By.id("detail-meta")).getText();
  }

}
