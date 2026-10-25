package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;

class StudioCatalogTest {
  protected JdbcTemplate db;
  protected StudioService studio;
  protected StudioCatalog catalog;
  private DataSource source;
  protected final AuthenticatedPrincipal director = new AuthenticatedPrincipal(1L,"director","ADMIN");

  protected DataSource database() {
    return new DriverManagerDataSource("jdbc:h2:mem:catalog_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  }
  protected String temporary() { return ""; }

  @BeforeEach
  void givenCatalogueWithApprovedCandidateAndUnlinkedOriginals() {
    source=database(); db=new JdbcTemplate(source);studio=mock(StudioService.class);
    when(studio.projectId()).thenReturn("p");catalog=new StudioCatalog(db,studio);
    String create="CREATE "+temporary()+"TABLE ";
    db.execute(create+"studio_files(id VARCHAR(36),project_id VARCHAR(64),filename VARCHAR(255),sha256 VARCHAR(64),extension VARCHAR(8),size_bytes BIGINT,storage_key VARCHAR(100),created_at TIMESTAMP)");
    db.execute(create+"studio_items(project_id VARCHAR(64),item_key VARCHAR(100),head_id VARCHAR(36),approved_id VARCHAR(36))");
    db.execute(create+"studio_revisions(id VARCHAR(36),project_id VARCHAR(64),item_key VARCHAR(100),label VARCHAR(40),kind VARCHAR(16),episode INT,cut_number INT,origin VARCHAR(16),title VARCHAR(150),content TEXT,dialogue TEXT,direction TEXT,work_status VARCHAR(20),file_id VARCHAR(36),created_at TIMESTAMP)");
    db.execute(create+"studio_events(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(64),revision_id VARCHAR(36),action VARCHAR(20))");
    file("f0","v9.mp4","mp4","p");file("f1","v10.mp4","mp4","p");file("f2","AI_AGENT_EP01_S01-S05_v11.mp4","mp4","p");
    file("raw","recent-unlinked.mp4","mp4","p");file("doc","QA_recent.md","md","p");file("image","face.png","png","p");
    file("secret","private.mp4","mp4","other");
    db.update("INSERT INTO studio_items VALUES ('p','film','r2','r1'),('p','HOLO-SUNI-WEB-FINAL','suni',NULL),('p','rejected','reject',NULL),('other','secret','private',NULL)");
    revision("r0","film","v9",1,"READY","f0","p");
    revision("r1","film","v10",1,"REVIEW","f1","p");
    revision("r2","film","claude-v11",1,"REVIEW","f2","p");
    revision("suni","HOLO-SUNI-WEB-FINAL","final-r1",null,"REVIEW",null,"p");
    revision("reject","rejected","r1",1,"REVIEW",null,"p");
    revision("private","secret","r1",2,"REVIEW","secret","other");
    db.update("INSERT INTO studio_events(project_id,revision_id,action) VALUES ('p','r1','APPROVE'),('p','reject','REJECT')");
  }
  @AfterEach void close() { if(source instanceof SingleConnectionDataSource single)single.destroy(); cleanup(); }
  protected void cleanup() { }

  private void file(String id,String name,String ext,String project) {
    db.update("INSERT INTO studio_files VALUES (?,?,?,?,?,100,'private-storage-key','2026-10-25 10:00:00')",id,project,name,"a".repeat(64),ext);
  }
  private void revision(String id,String key,String label,Integer episode,String work,String file,String project) {
    db.update("INSERT INTO studio_revisions VALUES (?,?,?,?, 'VIDEO',?,NULL,'AI_PROPOSAL',?,'body','line','direction',?,?,'2026-10-25 11:00:00')",id,project,key,label,episode,"제작 영상 "+label,work,file);
  }
  protected List<Map<String,Object>> list(String section,String episode,String media,String status,String versions,String query,int limit,int offset) {
    return catalog.list(director,section,episode,"",media,status,versions,query,limit,offset);
  }
  private List<String> ids(List<Map<String,Object>> rows) {return rows.stream().map(r->(String)r.get("id")).toList();}

  @Test void givenLinkedAndUnlinkedUploads_whenAll_thenBothVisibleWithoutDuplicateFileRows() {
    var rows=list("materials","","","","latest","",100,0);
    assertTrue(ids(rows).containsAll(List.of("r1","r2","raw","doc","image","suni")));
    assertFalse(ids(rows).contains("f2"));assertFalse(ids(rows).contains("r0"));assertFalse(ids(rows).contains("private"));assertFalse(ids(rows).contains("secret"));
    assertFalse(rows.getFirst().containsKey("storage_key"));assertFalse(rows.getFirst().containsKey("content"));
  }
  @Test void givenCandidateAfterApproval_whenLatest_thenApprovedPointerRemainsVisible() {
    var latest=list("review","1","VIDEO","","latest","film",100,0);
    assertEquals(Set.of("r1","r2"),new HashSet<>(ids(latest)));
    assertEquals("r1",latest.stream().filter(r->r.get("id").equals("r2")).findFirst().orElseThrow().get("approved_id"));
    assertTrue(ids(list("materials","1","","","all","film",100,0)).contains("r0"));
  }
  @Test void givenKeysFilenamesAndVersionLabels_whenSearch_thenOriginalMetadataIsSearchable() {
    assertEquals(List.of("suni"),ids(list("materials","","","","latest","holo-suni-web-final",100,0)));
    assertEquals(List.of("r2"),ids(list("materials","","","","latest","S01-S05_v11.mp4",100,0)));
    assertEquals(List.of("r2"),ids(list("materials","","","","latest","claude-v11",100,0)));
    assertTrue(list("materials","","","","latest","%' OR 1=1 --",100,0).isEmpty());
  }
  @Test void givenCommonAndEpisodeRows_whenFilter_thenUnassignedIsNotMislabelledAsEpisodeOne() {
    var common=ids(list("materials","common","","","latest","",100,0));
    assertTrue(common.containsAll(List.of("suni","raw","doc","image")));assertFalse(common.contains("r2"));
    var episode=ids(list("materials","1","","","latest","",100,0));assertTrue(episode.contains("r2"));assertFalse(episode.contains("raw"));
  }
  @Test void givenReviewAndRejection_whenStatusFilter_thenNoFalseApprovalOrPending() {
    assertEquals(List.of("r1"),ids(list("review","","","APPROVED","latest","",100,0)));
    var pending=ids(list("review","","","PENDING","latest","",100,0));assertTrue(pending.containsAll(List.of("suni","r2")));assertFalse(pending.contains("reject"));assertFalse(pending.contains("r1"));
    assertEquals(List.of("reject"),ids(list("review","","","REJECTED","latest","",100,0)));
  }
  @Test void givenUnlinkedDocumentsAndMedia_whenTypeFilter_thenCorrectGroupsAndNoImplicitApproval() {
    var documents=ids(list("materials","","DOCUMENT","","latest","",100,0));
    assertTrue(documents.contains("doc"));assertFalse(documents.contains("image"));assertFalse(documents.contains("r2"));
    assertEquals(List.of("image"),ids(list("materials","","IMAGE","","latest","",100,0)));
    assertEquals(List.of("raw"),ids(list("materials","","VIDEO","UNASSIGNED","latest","",100,0)));
    assertTrue(ids(list("review","","DOCUMENT","","latest","",100,0)).contains("doc"));
  }
  @Test void givenSameTimestamp_whenPaging_thenDeterministicWithoutDuplicatesOrMissingRecords() {
    var all=ids(list("materials","","","","all","",100,0));
    List<String> pages=new ArrayList<>();for(int offset=0;offset<all.size();offset+=2)pages.addAll(ids(list("materials","","","","all","",2,offset)));
    assertEquals(all,pages);assertEquals(all.size(),new HashSet<>(pages).size());
  }
  @Test void givenReadOnlyBrowsing_whenFiltersAndHistoryRead_thenEveryRecordAndApprovalUnchanged() {
    var before=snapshot();list("materials","","","","all","",100,0);list("review","","","PENDING","latest","",100,0);list("review","","","APPROVED","all","",100,0);assertEquals(before,snapshot());
  }
  private List<List<Map<String,Object>>> snapshot() {
    return List.of(db.queryForList("SELECT * FROM studio_files ORDER BY id"),db.queryForList("SELECT * FROM studio_items ORDER BY project_id,item_key"),db.queryForList("SELECT * FROM studio_revisions ORDER BY id"),db.queryForList("SELECT * FROM studio_events ORDER BY id"));
  }
  @Test void givenUnauthorisedUser_whenBrowse_thenExistingAccessGuardRunsBeforeReading() {
    doThrow(StudioService.error(403,"제작소 접근 불가")).when(studio).require(director,false,false);
    assertEquals(403,assertThrows(ResponseStatusException.class,()->list("materials","","","","latest","",100,0)).getStatusCode().value());
  }
  @Test void givenInvalidFiltersOrPage_whenBrowse_then400WithoutMutatingData() {
    var before=snapshot();
    assertThrows(ResponseStatusException.class,()->list("materials","15","","","latest","",100,0));
    assertThrows(ResponseStatusException.class,()->list("review","","","APPROVER","latest","",100,0));
    assertThrows(ResponseStatusException.class,()->list("materials","","","","latest","",101,0));
    assertThrows(ResponseStatusException.class,()->list("materials","","","","latest","a".repeat(201),100,0));
    assertEquals(before,snapshot());
  }
}
