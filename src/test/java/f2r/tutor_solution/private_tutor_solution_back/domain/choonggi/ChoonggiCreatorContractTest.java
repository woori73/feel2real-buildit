package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi;

import static org.assertj.core.api.Assertions.assertThat;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiPost;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChoonggiCreatorContractTest {
  @Test void draftPublishUnpublishLifecycleIsExplicit() {
    var post=ChoonggiPost.draft("EP03","Hello Mumbai","hello-mumbai-real","First note");
    assertThat(post.getStatus()).isEqualTo(ChoonggiPost.Status.DRAFT);
    post.publish();assertThat(post.getStatus()).isEqualTo(ChoonggiPost.Status.PUBLISHED);assertThat(post.getPublishedAt()).isNotNull();
    post.unpublish();assertThat(post.getStatus()).isEqualTo(ChoonggiPost.Status.DRAFT);assertThat(post.getPublishedAt()).isNull();
  }
  @Test void migrationStoresMetadataNotBinary() throws Exception {
    try(var in=getClass().getResourceAsStream("/db/migration/V991__add_choonggi_creator.sql")){
      assertThat(in).isNotNull();String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8).toLowerCase();
      assertThat(sql).contains("choonggi_posts","choonggi_media","storage_key","size_bytes","deleted_at");
      assertThat(sql).doesNotContain(" longblob"," mediumblob"," blob ");
    }
  }
}
