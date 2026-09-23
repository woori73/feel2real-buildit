package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiMedia;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.repository.ChoonggiMediaRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.repository.ChoonggiPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service.ChoonggiCreatorService;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ChoonggiCreatorContractTest {
  @Test void draftPublishUnpublishLifecycleIsExplicit() {
    var post=ChoonggiPost.draft("EP03","Hello Mumbai","hello-mumbai-real","First note");
    assertThat(post.getStatus()).isEqualTo(ChoonggiPost.Status.DRAFT);
    post.publish();assertThat(post.getStatus()).isEqualTo(ChoonggiPost.Status.PUBLISHED);assertThat(post.getPublishedAt()).isNotNull();
    post.unpublish();assertThat(post.getStatus()).isEqualTo(ChoonggiPost.Status.DRAFT);assertThat(post.getPublishedAt()).isNull();
  }
  @Test void migrationStoresMetadataNotBinary() throws Exception {
    try(var in=getClass().getResourceAsStream("/db/migration/V992__add_choonggi_creator.sql")){
      assertThat(in).isNotNull();String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8).toLowerCase();
      assertThat(sql).contains("choonggi_posts","choonggi_media","storage_key","size_bytes","deleted_at");
      assertThat(sql).doesNotContain(" longblob"," mediumblob"," blob ");
    }
  }

  private ChoonggiCreatorService newService(ChoonggiPostRepository posts,ChoonggiMediaRepository media){
    return new ChoonggiCreatorService(posts,media,mock(ImageSanitizer.class),mock(FileStorage.class));
  }

  /** Test-only: entity ids are DB-generated and have no public setter. */
  private static void setId(Object entity,Long id){
    try{Field f=entity.getClass().getDeclaredField("id");f.setAccessible(true);f.set(entity,id);}
    catch(ReflectiveOperationException e){throw new RuntimeException(e);}
  }

  @Test void anonymousMayReadMediaOnPublishedPost() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note");post.publish();
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThat(service.requireMedia(1L,null)).isSameAs(media);
  }
  @Test void anonymousIsBlockedFromDraftPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note"); // never published: DRAFT
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThatThrownBy(()->service.requireMedia(1L,null)).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
  @Test void anonymousIsBlockedFromUnpublishedPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note");
    post.publish();post.unpublish(); // was published, now back to DRAFT
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThatThrownBy(()->service.requireMedia(1L,null)).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
  @Test void anonymousIsBlockedFromDeletedPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note");
    post.publish();post.softDelete(); // soft-deleted post: status forced back to DRAFT + deletedAt set
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThatThrownBy(()->service.requireMedia(1L,null)).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
  @Test void anonymousIsBlockedFromDeletedMedia() {
    // findByIdAndDeletedAtIsNull already filters soft-deleted rows out at the query level.
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThatThrownBy(()->service.requireMedia(1L,null)).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
  @Test void adminMayPreviewDraftPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note"); // DRAFT
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThat(service.requireMedia(1L,"ADMIN")).isSameAs(media);
  }
  /** CREATOR is the scoped CHOONGGI role (not global ADMIN) — it must get the same draft-preview access as ADMIN. */
  @Test void creatorMayPreviewDraftPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note"); // DRAFT
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThat(service.requireMedia(1L,"CREATOR")).isSameAs(media);
  }
  @Test void studentIsBlockedFromDraftPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note"); // DRAFT
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThatThrownBy(()->service.requireMedia(1L,"STUDENT")).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
  @Test void tutorIsBlockedFromDraftPostMedia() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note"); // DRAFT
    var media=ChoonggiMedia.of(post,"k",ChoonggiMedia.Type.PHOTO,"image/jpeg","p.jpg",10L,100,100,0);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(media));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    assertThatThrownBy(()->service.requireMedia(1L,"TUTOR")).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test void moveUpSwapsSortOrderWithPreviousItem() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note");
    var m0=ChoonggiMedia.of(post,"k0",ChoonggiMedia.Type.PHOTO,"image/jpeg","0.jpg",1L,10,10,0);setId(m0,10L);
    var m1=ChoonggiMedia.of(post,"k1",ChoonggiMedia.Type.PHOTO,"image/jpeg","1.jpg",1L,10,10,1);setId(m1,11L);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(m0,m1));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    service.moveUp(1L,11L);
    assertThat(m1.getSortOrder()).isEqualTo(0);assertThat(m0.getSortOrder()).isEqualTo(1);
  }
  @Test void moveUpAtTopIsANoOp() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note");
    var m0=ChoonggiMedia.of(post,"k0",ChoonggiMedia.Type.PHOTO,"image/jpeg","0.jpg",1L,10,10,0);setId(m0,10L);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByPost_IdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(m0));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    service.moveUp(1L,10L);
    assertThat(m0.getSortOrder()).isEqualTo(0);
  }
  @Test void deleteMediaSoftDeletesAndClearsCover() {
    var post=ChoonggiPost.draft("EP01","Goodbye Korea","goodbye-korea-real","note");setId(post,1L);
    var m0=ChoonggiMedia.of(post,"k0",ChoonggiMedia.Type.PHOTO,"image/jpeg","0.jpg",1L,10,10,0);setId(m0,10L);m0.setCover(true);
    var mediaRepo=mock(ChoonggiMediaRepository.class);
    when(mediaRepo.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(m0));
    var service=newService(mock(ChoonggiPostRepository.class),mediaRepo);
    service.deleteMedia(1L,10L);
    assertThat(m0.getDeletedAt()).isNotNull();assertThat(m0.isCover()).isFalse();
  }
}
