package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.ExternalVideoUrlValidator;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.*;import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ChoonggiCreatorService {
  public static final long PHOTO_MAX=20L*1024*1024, VIDEO_MAX=50L*1024*1024;
  private final ChoonggiPostRepository posts; private final ChoonggiMediaRepository media;
  private final ImageSanitizer sanitizer; private final FileStorage storage;
  private final ExternalVideoUrlValidator videoValidator;

  public List<ChoonggiPost> adminList(){return posts.findByDeletedAtIsNullOrderByUpdatedAtDesc();}
  /** CREATOR sees only their own records ("내 기록"); ADMIN sees everyone's. */
  public List<ChoonggiPost> adminList(String loginId,String role){
    List<ChoonggiPost> all=adminList();
    if("ADMIN".equals(role))return all;
    return all.stream().filter(p->Objects.equals(loginId,p.getCreatedBy())).toList();
  }
  public List<ChoonggiPost> published(){return posts.findByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(ChoonggiPost.Status.PUBLISHED);}
  public Optional<ChoonggiPost> publicPost(String slug){return posts.findBySlugAndStatusAndDeletedAtIsNull(slug,ChoonggiPost.Status.PUBLISHED);}
  public ChoonggiPost require(Long id){return posts.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
  public ChoonggiPost requireOwned(Long id,String loginId,String role){return requireOwned(require(id),loginId,role);}
  /**
   * One-screen record: title + optional body/photo/YouTube link. Episode code and slug are
   * internal bookkeeping the creator never sees or types (auto-generated on first save; slug
   * only needs to be unique, episode code is cosmetic only).
   */
  @Transactional public ChoonggiPost save(Long id,String title,String body,String youtubeUrl,String loginId,String role){
    title=clean(title,120,"제목");
    body=body==null?"":body.trim(); if(body.length()>5000)throw bad("오늘의 기록이 너무 깁니다.");
    String videoId=resolveYoutubeId(youtubeUrl);
    ChoonggiPost post;
    if(id==null){
      post=ChoonggiPost.draft(nextEpisodeCode(),title,nextSlug(),body);
    } else {
      post=requireOwned(require(id),loginId,role);
      post.update(post.getEpisodeCode(),title,post.getSlug(),body);
    }
    post.setYoutubeVideoId(videoId);
    return posts.save(post);
  }
  private String resolveYoutubeId(String youtubeUrl){
    if(youtubeUrl==null||youtubeUrl.isBlank())return null;
    ExternalVideoUrlValidator.Resolved resolved;
    try{resolved=videoValidator.validate(youtubeUrl);}
    catch(RuntimeException e){throw bad("올바른 YouTube 링크를 넣어주세요.");}
    if(resolved.provider()!=AcademyExternalVideoProvider.YOUTUBE||resolved.embedUrl()==null)
      throw bad("올바른 YouTube 링크를 넣어주세요.");
    String embed=resolved.embedUrl();
    return embed.substring(embed.lastIndexOf('/')+1);
  }
  private String nextEpisodeCode(){return "EP"+String.format("%02d",posts.count()+1);}
  private String nextSlug(){
    for(int attempt=0;attempt<5;attempt++){
      String candidate="log-"+System.currentTimeMillis()+(attempt==0?"":"-"+attempt);
      if(!posts.existsBySlugAndIdNot(candidate,-1L))return candidate;
    }
    return "log-"+UUID.randomUUID();
  }
  @Transactional public ChoonggiPost publish(Long id,String loginId,String role){ChoonggiPost p=requireOwned(require(id),loginId,role);p.publish();return p;}
  @Transactional public void unpublish(Long id,String loginId,String role){requireOwned(require(id),loginId,role).unpublish();}
  @Transactional public void delete(Long id,String loginId,String role){requireOwned(require(id),loginId,role).softDelete();}
  private ChoonggiPost requireOwned(ChoonggiPost post,String loginId,String role){
    if(!"ADMIN".equals(role)&&!Objects.equals(loginId,post.getCreatedBy()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"본인이 작성한 기록만 수정·삭제할 수 있습니다.");
    return post;
  }
  @Transactional public ChoonggiMedia upload(Long postId,MultipartFile file,String loginId,String role){
    ChoonggiPost post=requireOwned(require(postId),loginId,role);if(file==null||file.isEmpty())throw bad("파일을 선택해 주세요.");
    String name=safeName(file.getOriginalFilename());String type=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase();
    try{
      List<ChoonggiMedia> current=media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(postId);if(current.size()>=10)throw bad("게시물당 미디어는 최대 10개입니다.");
      if(type.startsWith("image/")){if(file.getSize()>PHOTO_MAX)throw bad("사진은 20MB 이하여야 합니다.");var img=sanitizer.sanitize(file);var dim=sanitizer.readDimension(img.bytes());String key=storage.saveImage(0L,"choonggi_"+name,img.bytes(),img.extension());return media.save(ChoonggiMedia.of(post,key,ChoonggiMedia.Type.PHOTO,img.contentType(),name,img.bytes().length,dim==null?null:dim.width(),dim==null?null:dim.height(),current.size()));}
      if(type.equals("video/mp4")||type.equals("video/quicktime")){if(file.getSize()>VIDEO_MAX)throw bad("영상은 50MB 이하여야 합니다.");byte[] head=file.getInputStream().readNBytes(12);if(head.length<12||head[4]!='f'||head[5]!='t'||head[6]!='y'||head[7]!='p')throw bad("유효한 MP4/MOV 영상이 아닙니다.");String key=storage.saveRaw(0L,"choonggi_"+name,file.getInputStream(),file.getSize());return media.save(ChoonggiMedia.of(post,key,ChoonggiMedia.Type.VIDEO,type,name,file.getSize(),null,null,current.size()));}
      throw bad("사진(JPEG/PNG/WebP/GIF) 또는 MP4/MOV만 업로드할 수 있습니다.");
    }catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"미디어 저장에 실패했습니다.");}
  }
  @Transactional public void cover(Long postId,Long mediaId,String loginId,String role){requireOwned(require(postId),loginId,role);var list=media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(postId);if(list.stream().noneMatch(m->m.getId().equals(mediaId)))throw bad("이 게시물의 미디어가 아닙니다.");list.forEach(m->m.setCover(m.getId().equals(mediaId)));}
  /** Media rows are never shared across posts (each has its own storageKey) -- a soft delete here can never orphan another post's file. */
  @Transactional public void deleteMedia(Long postId,Long mediaId,String loginId,String role){requireOwned(require(postId),loginId,role);var m=media.findByIdAndDeletedAtIsNull(mediaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!m.getPost().getId().equals(postId))throw bad("이 게시물의 미디어가 아닙니다.");m.softDelete();}
  @Transactional public void moveUp(Long postId,Long mediaId,String loginId,String role){requireOwned(require(postId),loginId,role);swap(postId,mediaId,-1);}
  @Transactional public void moveDown(Long postId,Long mediaId,String loginId,String role){requireOwned(require(postId),loginId,role);swap(postId,mediaId,1);}
  private void swap(Long postId,Long mediaId,int dir){
    var list=media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(postId);
    int idx=-1;for(int i=0;i<list.size();i++)if(list.get(i).getId().equals(mediaId)){idx=i;break;}
    if(idx<0)throw bad("이 게시물의 미디어가 아닙니다.");
    int swapIdx=idx+dir;if(swapIdx<0||swapIdx>=list.size())return;
    var a=list.get(idx);var b=list.get(swapIdx);int ao=a.getSortOrder(),bo=b.getSortOrder();a.move(bo);b.move(ao);
  }
  /** Anonymous/STUDENT/TUTOR readers may only fetch media on a published, non-deleted post; ADMIN or the scoped CHOONGGI CREATOR role may preview any non-deleted media. */
  public ChoonggiMedia requireMedia(Long id,String role){
    ChoonggiMedia m=media.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    if(!"ADMIN".equals(role)&&!"CREATOR".equals(role)){
      ChoonggiPost post=m.getPost();
      if(post.getDeletedAt()!=null||post.getStatus()!=ChoonggiPost.Status.PUBLISHED)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return m;
  }
  public byte[] bytes(ChoonggiMedia m){try{return storage.readSingleLimited(m.getStorageKey(),"RAW",m.getType()==ChoonggiMedia.Type.VIDEO?VIDEO_MAX:PHOTO_MAX);}catch(IOException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}}
  private String clean(String v,int max,String label){if(v==null||v.isBlank())throw bad(label+"을 입력해 주세요.");String x=v.trim();if(x.length()>max)throw bad(label+"이 너무 깁니다.");return x;}
  private String safeName(String n){String x=n==null?"media":n.replace('\\','/');x=x.substring(x.lastIndexOf('/')+1).replaceAll("[^a-zA-Z0-9._가-힣-]","_");return x.isBlank()?"media":x;}
  private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
