package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
// AcademyPathRedirectInterceptor가 요구하는 PublicSiteProperties를 명시 등록:
// @WebMvcTest 슬라이스가 이 설정만 import해도 컨텍스트가 뜬다(중복 등록은 무해).
@EnableConfigurationProperties(PublicSiteProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleModelInterceptor roleModelInterceptor;
    private final AcademyPathRedirectInterceptor academyPathRedirectInterceptor;

    public WebMvcConfig(RoleModelInterceptor roleModelInterceptor,
                        AcademyPathRedirectInterceptor academyPathRedirectInterceptor) {
        this.roleModelInterceptor = roleModelInterceptor;
        this.academyPathRedirectInterceptor = academyPathRedirectInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleModelInterceptor);
        // 경로형 /academies/{slug} → handle 있으면 서브도메인 301. 단일 segment만.
        registry.addInterceptor(academyPathRedirectInterceptor).addPathPatterns("/academies/*");
        // NewStart: 홈·로그인만 공개, 나머지 /newstart/** 화면은 회원 전용(비회원은 자체 로그인으로).
        registry.addInterceptor(new NewStartMembersOnlyInterceptor())
                .addPathPatterns("/newstart/**")
                .excludePathPatterns("/newstart", "/newstart/", "/newstart/login");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        view(registry, "/home", "public/home");
        // 구 /eduholic → /home 영구 이동 (북마크/외부 링크 보존)
        registry.addRedirectViewController("/eduholic", "/home")
                .setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        registry.addRedirectViewController("/login", "/home")
                .setStatusCode(HttpStatus.FOUND);
        registry.addRedirectViewController("/login.html", "/home")
                .setStatusCode(HttpStatus.FOUND);
        view(registry, "/register", "auth/register");
        view(registry, "/signin/reset/password", "auth/password_reset_request");
        view(registry, "/signin/reset/password/confirm", "auth/password_reset_confirm");
        view(registry, "/tutor/academy", "tutor/tutor_academy");
        view(registry, "/tutor/academy/setup", "tutor/tutor_academy_setup");
        view(registry, "/tutor/academy/lessons/editor", "tutor/lesson_product_editor");
        view(registry, "/tutor/academy/videos", "tutor/tutor_academy_videos");
        view(registry, "/tutor/lesson/{lessonId}", "tutor/tutor_lesson_detail");
        view(registry, "/tutor/academy/posts", "tutor/tutor_academy_posts");
        view(registry, "/tutor/academy/posts/new", "tutor/tutor_academy_post_editor");
        view(registry, "/tutor/academy/posts/{postId}", "tutor/tutor_academy_post_editor");
        view(registry, "/builder", "tutor/academy_builder");
        view(registry, "/academies", "public/academies");
        view(registry, "/academy-posts", "public/academy_posts");
        view(registry, "/community", "public/community");
        view(registry, "/community/posts/new", "public/community_new");
        view(registry, "/community/posts/{postId}", "public/community_post");
        view(registry, "/academies/{slug}", "public/academy_public");
        view(registry, "/analyzes", "public/analyzes");
        view(registry, "/analyzes/{id}", "public/analyze_detail");
        // /wiki, /wiki/originals, /wiki/analysis 는 Cache-Control: no-store 때문에 WikiPageController 가 담당.
        // (업로드 버튼/모달은 role 무관하게 항상 렌더 — 권한은 wiki_admin_upload.js + /api/admin/wiki/** 서버 게이트)
        view(registry, "/wiki/learning", "public/wiki_learning");
        view(registry, "/wiki/learning/{id}", "public/wiki_learning_detail");
        view(registry, "/wiki/board/{boardKey}", "public/wiki_board");
        view(registry, "/wiki/board/{boardKey}/new", "public/wiki_post_new");
        view(registry, "/wiki/posts/{postId}", "public/wiki_post");
        view(registry, "/academies/{slug}/posts", "public/academy_blog_posts");
        view(registry, "/academies/{slug}/posts/{postSlug}", "public/academy_blog_post");
        view(registry, "/lessons", "public/lesson_search");
        view(registry, "/my/profile", "shared/my_profile");

        view(registry, "/stdt_dashboard", "student/stdt_dashboard");
        view(registry, "/tutor_dashboard", "tutor/tutor_dashboard");

        // /conversation/materials, /exam-materials, /study-materials 는 ConversationMaterialsPageController가
        // activeMenu/filter/title 모델을 채워서 같은 conversation_materials 템플릿을 렌더한다.
        view(registry, "/exam_review", "student/exam_review");

        view(registry, "/admin/login", "admin/login");
        view(registry, "/admin/dashboard", "admin/dashboard");
        view(registry, "/admin/students", "admin/students");
        view(registry, "/admin/members", "admin/members");
        view(registry, "/admin/analytics", "admin/analytics");
        view(registry, "/admin/academies", "admin/academies");
        view(registry, "/admin/analyzes", "admin/analyzes");
        view(registry, "/admin/wiki-originals", "admin/wiki_originals");
        view(registry, "/admin/wiki-analysis", "admin/wiki_analysis");
        view(registry, "/admin/newstart/wiki", "admin/newstart_wiki");
        view(registry, "/admin/newstart/jipijigi", "admin/newstart_jipijigi");
        view(registry, "/admin/lesson-communities", "admin/lesson_communities");
        view(registry, "/admin/landing", "admin/landing");

        // Easy 회계·학생원장 — ADMIN 전용(SecurityConfig hasRole). 학생 상세는
        // /easy/students?id= 쿼리로 같은 템플릿에서 처리한다.
        view(registry, "/easy", "easy/dashboard");
        view(registry, "/easy/transactions", "easy/transactions");
        view(registry, "/easy/students", "easy/students");
        view(registry, "/easy/payments", "easy/payments");
        view(registry, "/easy/card-review", "easy/card_review");
        view(registry, "/easy/reviews", "easy/reviews");
        view(registry, "/easy/evidence", "easy/reviews");
        view(registry, "/easy/accounting", "easy/accounting");

        // 원장용 Easy 대화 장부 — 데이터/변경은 /api/tutor/easy/** 권한 검증을 거친다.
        view(registry, "/tutor/easy", "easy/tutor_agent");

        view(registry, "/tutor/lessons/{lessonId}/students", "tutor/lesson_students");
    }

    private void view(ViewControllerRegistry registry, String path, String viewName) {
        registry.addViewController(path).setViewName(viewName);
    }
}
