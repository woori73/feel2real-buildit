package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service.ChoonggiCreatorService;
import java.net.URI;import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.stereotype.Controller;import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;

@Controller @RequiredArgsConstructor
public class ChoonggiCreatorController {
  private final ChoonggiCreatorService service;
  @GetMapping({"/choonggi/admin","/choonggi/admin/posts"}) String list(Model model,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){model.addAttribute("posts",service.adminList(principal.loginId(),principal.role()).stream().map(PostView::of).toList());return "choonggi/admin";}
  @GetMapping("/choonggi/admin/posts/new") String create(Model model){model.addAttribute("post",null);return "choonggi/editor";}
  @GetMapping("/choonggi/admin/posts/{id}/edit") String edit(@PathVariable Long id,Model model,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){model.addAttribute("post",PostView.of(service.requireOwned(id,principal.loginId(),principal.role())));return "choonggi/editor";}
  @GetMapping("/choonggi/admin/posts/{id}/preview") String preview(@PathVariable Long id,Model model,jakarta.servlet.http.HttpServletResponse response){var post=service.require(id);response.setHeader(HttpHeaders.CACHE_CONTROL,"private, no-store");model.addAttribute("page","post");model.addAttribute("pageTitle",post.getTitle()+" — PREVIEW");model.addAttribute("description","CHOONGGI draft preview");model.addAttribute("canonical","https://feel2real.co.kr/choonggi/vlog/"+post.getSlug());model.addAttribute("post",post);return "choonggi/page";}

  @RestController @RequestMapping("/api/admin/choonggi") @RequiredArgsConstructor
  public static class Api {
    private final ChoonggiCreatorService service;
    @GetMapping("/posts") List<PostView> list(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){return service.adminList(principal.loginId(),principal.role()).stream().map(PostView::of).toList();}
    @PostMapping("/posts") PostView create(@RequestBody SaveRequest r,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){return PostView.of(service.save(null,r.title(),r.body(),r.youtubeUrl(),principal.loginId(),principal.role()));}
    @PutMapping("/posts/{id}") PostView update(@PathVariable Long id,@RequestBody SaveRequest r,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){return PostView.of(service.save(id,r.title(),r.body(),r.youtubeUrl(),principal.loginId(),principal.role()));}
    @PostMapping("/posts/{id}/media") MediaView upload(@PathVariable Long id,@RequestParam("file") MultipartFile file,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){return MediaView.of(service.upload(id,file,principal.loginId(),principal.role()));}
    @PostMapping("/posts/{id}/cover/{mediaId}") void cover(@PathVariable Long id,@PathVariable Long mediaId,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){service.cover(id,mediaId,principal.loginId(),principal.role());}
    @PostMapping("/posts/{id}/media/{mediaId}/move-up") void moveUp(@PathVariable Long id,@PathVariable Long mediaId,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){service.moveUp(id,mediaId,principal.loginId(),principal.role());}
    @PostMapping("/posts/{id}/media/{mediaId}/move-down") void moveDown(@PathVariable Long id,@PathVariable Long mediaId,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){service.moveDown(id,mediaId,principal.loginId(),principal.role());}
    @DeleteMapping("/posts/{id}/media/{mediaId}") void deleteMedia(@PathVariable Long id,@PathVariable Long mediaId,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){service.deleteMedia(id,mediaId,principal.loginId(),principal.role());}
    @PostMapping("/posts/{id}/publish") PostView publish(@PathVariable Long id,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){return PostView.of(service.publish(id,principal.loginId(),principal.role()));}
    @PostMapping("/posts/{id}/unpublish") void unpublish(@PathVariable Long id,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){service.unpublish(id,principal.loginId(),principal.role());}
    @DeleteMapping("/posts/{id}") void delete(@PathVariable Long id,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){service.delete(id,principal.loginId(),principal.role());}
  }

  @RestController @RequiredArgsConstructor
  public static class MediaApi {
    private final ChoonggiCreatorService service;
    @GetMapping("/api/choonggi/media/{id}") ResponseEntity<byte[]> media(@PathVariable Long id,@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal){
      ChoonggiMedia m=service.requireMedia(id,principal==null?null:principal.role());
      boolean publiclyCacheable=m.getPost().getDeletedAt()==null&&m.getPost().getStatus()==ChoonggiPost.Status.PUBLISHED;
      CacheControl cache=publiclyCacheable?CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic():CacheControl.noStore();
      return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.getContentType())).cacheControl(cache).contentLength(m.getSizeBytes()).body(service.bytes(m));
    }
  }
  public record SaveRequest(String title,String body,String youtubeUrl){}
  public record PostView(Long id,String title,String body,String youtubeVideoId,String status,String slug,java.time.LocalDateTime publishedAt,List<MediaView> media){static PostView of(ChoonggiPost p){return new PostView(p.getId(),p.getTitle(),p.getBody(),p.getYoutubeVideoId(),p.getStatus().name(),p.getSlug(),p.getPublishedAt(),p.getMedia().stream().filter(m->m.getDeletedAt()==null).map(MediaView::of).toList());}}
  public record MediaView(Long id,String type,String contentType,long sizeBytes,int sortOrder,boolean cover,String url){static MediaView of(ChoonggiMedia m){return new MediaView(m.getId(),m.getType().name(),m.getContentType(),m.getSizeBytes(),m.getSortOrder(),m.isCover(),"/api/choonggi/media/"+m.getId());}}
}
