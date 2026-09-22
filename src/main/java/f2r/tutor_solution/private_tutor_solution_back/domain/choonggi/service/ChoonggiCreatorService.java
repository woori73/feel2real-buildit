package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.*;import java.util.*;import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ChoonggiCreatorService {
  public static final long PHOTO_MAX=20L*1024*1024, VIDEO_MAX=50L*1024*1024;
  private static final Pattern SLUG=Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final Set<String> EPISODES=Set.of("EP00","EP01","EP02","EP03","EP04","EP05","EP06","EP07","EP08");
  private final ChoonggiPostRepository posts; private final ChoonggiMediaRepository media;
  private final ImageSanitizer sanitizer; private final FileStorage storage;

  public List<ChoonggiPost> adminList(){return posts.findByDeletedAtIsNullOrderByUpdatedAtDesc();}
  public List<ChoonggiPost> published(){return posts.findByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(ChoonggiPost.Status.PUBLISHED);}
  public Optional<ChoonggiPost> publicPost(String slug){return posts.findBySlugAndStatusAndDeletedAtIsNull(slug,ChoonggiPost.Status.PUBLISHED);}
  public ChoonggiPost require(Long id){return posts.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
  @Transactional public ChoonggiPost save(Long id,String episode,String title,String slug,String body){
    episode=clean(episode,30,"Episode"); if(!EPISODES.contains(episode)&&!episode.matches("EP\\d{2,3}"))throw bad("Episode 형식이 올바르지 않습니다.");
    title=clean(title,120,"제목"); slug=clean(slug,140,"slug").toLowerCase(); body=clean(body,5000,"오늘의 기록");
    if(!SLUG.matcher(slug).matches())throw bad("slug는 영문 소문자, 숫자, 하이픈만 가능합니다.");
    if(posts.existsBySlugAndIdNot(slug,id==null?-1L:id))throw bad("이미 사용 중인 slug입니다.");
    ChoonggiPost post=id==null?ChoonggiPost.draft(episode,title,slug,body):require(id); if(id!=null)post.update(episode,title,slug,body); return posts.save(post);
  }
  @Transactional public ChoonggiPost publish(Long id){ChoonggiPost p=require(id);if(media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(id).isEmpty())throw bad("사진 또는 영상을 먼저 추가해 주세요.");p.publish();return p;}
  @Transactional public void unpublish(Long id){require(id).unpublish();}
  @Transactional public void delete(Long id){require(id).softDelete();}
  @Transactional public ChoonggiMedia upload(Long postId,MultipartFile file){
    ChoonggiPost post=require(postId);if(file==null||file.isEmpty())throw bad("파일을 선택해 주세요.");
    String name=safeName(file.getOriginalFilename());String type=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase();
    try{
      List<ChoonggiMedia> current=media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(postId);if(current.size()>=10)throw bad("게시물당 미디어는 최대 10개입니다.");
      if(type.startsWith("image/")){if(file.getSize()>PHOTO_MAX)throw bad("사진은 20MB 이하여야 합니다.");var img=sanitizer.sanitize(file);var dim=sanitizer.readDimension(img.bytes());String key=storage.saveImage(0L,"choonggi_"+name,img.bytes(),img.extension());return media.save(ChoonggiMedia.of(post,key,ChoonggiMedia.Type.PHOTO,img.contentType(),name,img.bytes().length,dim==null?null:dim.width(),dim==null?null:dim.height(),current.size()));}
      if(type.equals("video/mp4")||type.equals("video/quicktime")){if(file.getSize()>VIDEO_MAX)throw bad("영상은 50MB 이하여야 합니다.");byte[] head=file.getInputStream().readNBytes(12);if(head.length<12||head[4]!='f'||head[5]!='t'||head[6]!='y'||head[7]!='p')throw bad("유효한 MP4/MOV 영상이 아닙니다.");String key=storage.saveRaw(0L,"choonggi_"+name,file.getInputStream(),file.getSize());return media.save(ChoonggiMedia.of(post,key,ChoonggiMedia.Type.VIDEO,type,name,file.getSize(),null,null,current.size()));}
      throw bad("사진(JPEG/PNG/WebP/GIF) 또는 MP4/MOV만 업로드할 수 있습니다.");
    }catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"미디어 저장에 실패했습니다.");}
  }
  @Transactional public void cover(Long postId,Long mediaId){var list=media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(postId);if(list.stream().noneMatch(m->m.getId().equals(mediaId)))throw bad("이 게시물의 미디어가 아닙니다.");list.forEach(m->m.setCover(m.getId().equals(mediaId)));}
  @Transactional public void deleteMedia(Long postId,Long mediaId){var m=media.findByIdAndDeletedAtIsNull(mediaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!m.getPost().getId().equals(postId))throw bad("이 게시물의 미디어가 아닙니다.");m.softDelete();}
  @Transactional public void moveUp(Long postId,Long mediaId){swap(postId,mediaId,-1);}
  @Transactional public void moveDown(Long postId,Long mediaId){swap(postId,mediaId,1);}
  private void swap(Long postId,Long mediaId,int dir){
    var list=media.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(postId);
    int idx=-1;for(int i=0;i<list.size();i++)if(list.get(i).getId().equals(mediaId)){idx=i;break;}
    if(idx<0)throw bad("이 게시물의 미디어가 아닙니다.");
    int swapIdx=idx+dir;if(swapIdx<0||swapIdx>=list.size())return;
    var a=list.get(idx);var b=list.get(swapIdx);int ao=a.getSortOrder(),bo=b.getSortOrder();a.move(bo);b.move(ao);
  }
  /** Anonymous readers may only fetch media on a published, non-deleted post; ADMIN may preview any non-deleted media. */
  public ChoonggiMedia requireMedia(Long id,String role){
    ChoonggiMedia m=media.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    if(!"ADMIN".equals(role)){
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
