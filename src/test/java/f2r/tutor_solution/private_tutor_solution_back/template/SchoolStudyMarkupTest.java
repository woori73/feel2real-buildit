package f2r.tutor_solution.private_tutor_solution_back.template;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.study.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class SchoolStudyMarkupTest {
    private final SpringTemplateEngine engine = engine();
    private static SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver); return engine;
    }
    private String render(String view, Map<String,Object> model) {
        return engine.process(view, new Context(Locale.KOREAN,model));
    }
    private void preview(String filename,String html) throws Exception {
        String dir = System.getenv("F2R_REVIEW_HTML");
        if (dir != null) {
            Files.createDirectories(Path.of(dir));
            Files.writeString(Path.of(dir,filename),html);
        }
    }
    @Test void loginModalShowsWorkingGoogleLinkOnlyWhenEnabled() {
        for (boolean enabled : List.of(false,true)) {
            var context = new Context(Locale.KOREAN,Map.of("googleSignup",enabled));
            var modal = Jsoup.parse(engine.process("fragments/_account_menu",Set.of("loginModal"),context));
            assertThat(modal.select(".login-modal-social a.login-modal-google[href=/auth/google]")).hasSize(enabled ? 1 : 0);
            assertThat(modal.select(".login-modal-social button.login-modal-google[disabled]")).hasSize(enabled ? 0 : 1);
            assertThat(modal.select("a[href=/auth/naver/start]")).hasSize(1);
        }
    }

    @Test void rendersCanonicalGuidesAndMetadataAndPreservesArchive() throws Exception {
        var catalog = new StudyCatalog(JsonMapper.builder().build());
        var hub = new StudyHubController(catalog);
        var index = new ExtendedModelMap();
        String hubHtml=render(hub.index(index),index);
        var home=Jsoup.parse(hubHtml);
        assertThat(home.select("section li a")).hasSize(50);
        assertThat(home.select("[itemtype=https://schema.org/Organization] [itemprop=name]").text()).isEqualTo("Feel to Real");
        var archive=new ExtendedModelMap();
        assertThat(Jsoup.parse(render(hub.archive(archive),archive)).select("li a")).hasSize(300);
        for(var a:home.select("section li a")) {
            var path=a.attr("href");
            var model=new ExtendedModelMap();
            String html=render(hub.topic(path.split("/")[2],model),model);
            var doc=Jsoup.parse(html);
            assertThat(catalog.canonicalDates()).containsKey(path);
            assertThat(doc.select("link[rel=canonical]").attr("href")).endsWith(path);
            assertThat(doc.select("[itemtype=https://schema.org/LearningResource] [itemprop=name]").first().text()).isEqualTo(a.text());
            assertThat(doc.select("section h3")).hasSize(2);
            assertThat(doc.select("[itemprop=datePublished]").attr("datetime")).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(LocalDate.parse(doc.select("[itemprop=dateModified]").attr("datetime")))
                .isAfterOrEqualTo(LocalDate.parse(doc.select("[itemprop=datePublished]").attr("datetime")));
            if(path.equals("/study/apgujeong-m2/")) preview("school.html",html);
        }
        var math=new ExtendedModelMap();
        var legacy=Jsoup.parse(render(hub.topic("csat-math-derivative",math),math));
        assertThat(legacy.select("main").text()).contains("F2R 자체 문항","11");
        preview("study.html",hubHtml);
        preview("home.html",LandingEdunovaTemplateContractTest.render());
        String guardian = render("auth/google-guardian",Map.of());
        assertThat(Jsoup.parse(guardian).select("input, form")).isEmpty();
        preview("guardian.html",guardian);
        String enrolled=render("auth/google-enrolled",Map.of("nonce","test"));
        assertThat(Jsoup.parse(enrolled).select("form input[name=code]")).hasSize(1);
        preview("enrolled.html",enrolled);
        var sampleGroup=new f2r.tutor_solution.private_tutor_solution_back.domain.user.google.EnrolledSignupApproval.Business(1,"가상 학원");
        var sampleStudent=new f2r.tutor_solution.private_tutor_solution_back.domain.user.google.EnrolledSignupApproval.Student(1,"가상 학생");
        String director=render("auth/director-approvals",Map.of("nonce","test","businessId",1L,
            "businesses",List.of(sampleGroup),"students",List.of(sampleStudent)));
        assertThat(Jsoup.parse(director).select("select[name=method] option[value=paper], select[name=method] option[value=phone]")).hasSize(2);
        preview("director.html",director);
        preview("signup.html",render("auth/google-entry",Map.of("nonce","sample","unlinked",false,"failed",false)));
    }
}
