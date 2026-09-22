package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service.ChoonggiCreatorService;
import java.net.URI;import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;import org.springframework.stereotype.Controller;import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;

@Controller @RequiredArgsConstructor
public class ChoonggiCreatorController {
  private final ChoonggiCreatorService service;
  @GetMapping({"/choonggi/admin","/choonggi/admin/posts"}) String list(Model model){model.addAttribute("posts",service.adminList());return "choonggi/admin";}
  @GetMapping("/choonggi/admin/posts/new") String create(Model model){model.addAttribute("post",null);return "choonggi/editor";}
  @GetMapping("/choonggi/admin/posts/{id}/edit") String edit(@PathVariable Long id,Model model){model.addAttribute("post",service.require(id));return "choonggi/editor";}
  @GetMapping("/choonggi/admin/posts/{id}/preview") String preview(@PathVariable Long id,Model model,jakarta.servlet.http.HttpServletResponse response){var post=service.require(id);response.setHeader(HttpHeaders.CACHE_CONTROL,"private, no-store");model.addAttribute("page","post");model.addAttribute("pageTitle",post.getTitle()+" — PREVIEW");model.addAttribute("description","CHOONGGI draft preview");model.addAttribute("canonical","https://feel2real.co.kr/choonggi/vlog/"+post.getSlug());model.addAttribute("post",post);return "choonggi/page";}

  @RestController @RequestMapping("/api/admin/choonggi") @RequiredArgsConstructor
  public static class Api {
    private final ChoonggiCreatorService service;
    @GetMapping("/posts") List<PostView> list(){return service.adminList().stream().map(PostView::of).toList();}
    @PostMapping("/posts") PostView create(@RequestBody SaveRequest r){return PostView.of(service.save(null,r.episode(),r.title(),r.slug(),r.body()));}
    @PutMapping("/posts/{id}") PostView update(@PathVariable Long id,@RequestBody SaveRequest r){return PostView.of(service.save(id,r.episode(),r.title(),r.slug(),r.body()));}
    @PostMapping("/posts/{id}/media") MediaView upload(@PathVariable Long id,@RequestParam("file") MultipartFile file){return MediaView.of(service.upload(id,file));}
    @PostMapping("/posts/{id}/cover/{mediaId}") void cover(@PathVariable Long id,@PathVariable Long mediaId){service.cover(id,mediaId);}
    @PostMapping("/posts/{id}/publish") PostView publish(@PathVariable Long id){return PostView.of(service.publish(id));}
    @PostMapping("/posts/{id}/unpublish") void unpublish(@PathVariable Long id){service.unpublish(id);}
    @DeleteMapping("/posts/{id}") void delete(@PathVariable Long id){service.delete(id);}
  }

  @RestController @RequiredArgsConstructor
  public static class MediaApi {
    private final ChoonggiCreatorService service;
    @GetMapping("/api/choonggi/media/{id}") ResponseEntity<byte[]> media(@PathVariable Long id){ChoonggiMedia m=service.requireMedia(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.getContentType())).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic()).contentLength(m.getSizeBytes()).body(service.bytes(m));}
  }
  public record SaveRequest(String episode,String title,String slug,String body){}
  public record PostView(Long id,String episode,String title,String slug,String body,String status,java.time.LocalDateTime publishedAt,List<MediaView> media){static PostView of(ChoonggiPost p){return new PostView(p.getId(),p.getEpisodeCode(),p.getTitle(),p.getSlug(),p.getBody(),p.getStatus().name(),p.getPublishedAt(),p.getMedia().stream().filter(m->m.getDeletedAt()==null).map(MediaView::of).toList());}}
  public record MediaView(Long id,String type,String contentType,long sizeBytes,int sortOrder,boolean cover,String url){static MediaView of(ChoonggiMedia m){return new MediaView(m.getId(),m.getType().name(),m.getContentType(),m.getSizeBytes(),m.getSortOrder(),m.isCover(),"/api/choonggi/media/"+m.getId());}}
}
