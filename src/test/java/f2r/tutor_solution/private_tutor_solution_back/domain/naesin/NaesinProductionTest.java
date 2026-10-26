package f2r.tutor_solution.private_tutor_solution_back.domain.naesin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightTextExtractor;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Real JDBC, unchanged Studio engine and disk bytes; no model, network or production DB. */
class NaesinProductionTest {
  @TempDir Path dir;
  JdbcTemplate db; TransactionTemplate tx; NaesinProductionService service; StudioService studio;
  ObjectMapper json = JsonMapper.builder().build();
  AuthenticatedPrincipal admin = new AuthenticatedPrincipal(1L,"admin","ADMIN","isolated");
  AuthenticatedPrincipal editor = new AuthenticatedPrincipal(2L,"modu","TUTOR","isolated");
  AuthenticatedPrincipal student = new AuthenticatedPrincipal(3L,"student","STUDENT","isolated");
  String project;
  @BeforeEach void setup() throws Exception {
    var ds = new DriverManagerDataSource("jdbc:h2:mem:naesin-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    db = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    db.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY)");
    db.execute("INSERT INTO users VALUES (1),(2),(3)");
    String ddl = Files.readString(Path.of("src/main/resources/db/migration/V1095__studio_and_mcp.sql"));
    for (String sql : ddl.substring(0,ddl.indexOf("CREATE TABLE mcp_grants")).split(";")) if(!sql.isBlank())db.execute(sql);
    var userRepo = mock(UserRepository.class); var modu=User.builder().loginId("modu").role(Role.TUTOR).build();
    ReflectionTestUtils.setField(modu,"userId",2L);when(userRepo.findByLoginId("modu")).thenReturn(Optional.of(modu));
    var guard=new StudioAccessGuard(userRepo);ReflectionTestUtils.setField(guard,"operatorLoginId","modu");
    var storage=new LocalFileStorage();ReflectionTestUtils.setField(storage,"basePath",dir.toString());
    var validator=new DocumentUploadValidator(new UploadSizeGuard(20*1024*1024));
    studio=new StudioService(db,storage);ReflectionTestUtils.setField(studio,"access",guard);
    var projects=new StudioProjectService(db,studio);
    service=new NaesinProductionService(studio,projects,guard,db,storage,validator,new NaesinTextExtractor(new ExamInsightTextExtractor(validator)),json);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    project=json.valueToTree(call(()->service.create(admin,new NaesinProductionService.Project("신구중 시험 제작","신구중","중2","2026 1학기 기말")))).path("id").asText();
  }
  @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();}
  void division(String value){((MockHttpServletRequest)((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest()).setParameter("division",value);}
  <T> T call(Supplier<T> action){return tx.execute(s->action.get());}
  Object upload(String text) {return call(()->{try{return service.upload(editor,new MockMultipartFile("file","중2_학습지.txt","text/plain",text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"강사 작성 격리 표본","내부 검증용, 게시 권한 미확인");}catch(Exception e){throw new RuntimeException(e);}});}
  String file(Object upload){return json.valueToTree(upload).path("file").path("id").asText();}
  NaesinProductionService.Work work(String parent,String version,String file,String status){return new NaesinProductionService.Work("exam-main",parent,version,"RESULT","중2 문제지","ChatGPT Work","중2 내신 문항 수정","원재료의 문항·정답을 대조한다","test-no-llm","격리 테스트","문항 1. Choose the correct answer.","FAILED".equals(status)?"의도된 실패: 정답 검수 필요":"","Claude는 최신 승인본과 실패 기록 확인",status,file,List.of(file),List.of());}
  String save(String parent,String version,String file,String status){return json.valueToTree(call(()->service.save(editor,work(parent,version,file,status)))).path("id").asText();}
  void checks(String id){for(String dimension:NaesinProductionService.CHECKS)call(()->service.check(admin,id,new NaesinProductionService.Check(dimension,true,"격리 표본 독립 대조 완료")));}
  void approve(String id){call(()->service.review(admin,id,new NaesinProductionService.Decision("APPROVE","격리 검증 승인")));}

  @Test void givenOriginal_whenRetryAndNewProvenance_thenSameBytesAndNoDuplicateRetryHistory() {
    // given / when
    var one=upload("Original middle-school text."); var two=upload("Original middle-school text.");
    // then
    assertEquals(file(one),file(two));assertTrue(json.valueToTree(two).path("duplicate").asBoolean());
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM studio_files",Integer.class));
    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM studio_revisions",Integer.class));
    assertFalse(json.valueToTree(one).path("file").has("storage_key"));
  }
  @Test void givenUnreviewedResult_whenApprovingOrPublishing_thenBlockedAndSourceNeverPublished() {
    String f=file(upload("Source passage.")),id=save(null,"v1",f,"REVIEW");
    assertThrows(ResponseStatusException.class,()->approve(id));
    assertThrows(ResponseStatusException.class,()->call(()->service.publish(admin,id,new NaesinProductionService.Publication(id))));
    checks(id);approve(id);
    call(()->service.check(admin,id,new NaesinProductionService.Check("FORMAT",false,"빈칸 위치 재수정 필요")));
    assertThrows(ResponseStatusException.class,()->call(()->service.publish(admin,id,new NaesinProductionService.Publication(id))));
    assertEquals(0,json.valueToTree(service.published(student,0)).size());
  }
  @Test void givenPublishedV1_whenV2FailedAndV3Approved_thenPublishedSnapshotAndOriginalRemainFixed() throws Exception {
    String f=file(upload("Approved original bytes.")),v1=save(null,"v1",f,"REVIEW");checks(v1);approve(v1);
    var publication=json.valueToTree(call(()->service.publish(admin,v1,new NaesinProductionService.Publication(v1))));
    String v2=save(v1,"v2",f,"FAILED"),v3=save(v2,"v3",f,"REVIEW");checks(v3);approve(v3);
    var list=json.valueToTree(service.published(student,0));assertEquals(1,list.size());assertEquals(v1,list.get(0).path("revision_id").asText());
    assertEquals("Approved original bytes.",new String(service.fileBytes(service.publishedFile(student,publication.path("publication_id").asLong()))));
    assertEquals("FAILED",studio.revision(admin,v2).get("work_status"));
    assertThrows(ResponseStatusException.class,()->save(v1,"v4",f,"READY"));
    assertThrows(ResponseStatusException.class,()->call(()->service.publish(editor,v3,new NaesinProductionService.Publication(v3))));
    assertEquals(v3,studio.revision(admin,v3).get("approved_id"));
  }
  @Test void givenApprovedVersion_whenNewRevision_thenChecksNotInheritedAndOldApprovalLeaseRejected() {
    String f=file(upload("Source text")),v1=save(null,"v1",f,"REVIEW");checks(v1);approve(v1);
    String v2=save(v1,"v2",f,"REVIEW");
    assertTrue(service.checks(v2).isEmpty());assertThrows(ResponseStatusException.class,()->approve(v2));
    checks(v2);approve(v2);
    assertThrows(ResponseStatusException.class,()->call(()->service.publish(admin,v1,new NaesinProductionService.Publication(v1))));
  }
  @Test void givenUnknownProjectFileOrSchoolLevel_whenRequested_thenDenied() {
    assertThrows(ResponseStatusException.class,()->service.select(admin,"ai-agent-part1"));
    assertThrows(ResponseStatusException.class,()->service.select(student,project));
    assertThrows(ResponseStatusException.class,()->call(()->service.create(admin,new NaesinProductionService.Project("고등","고등학교","고1","범위","MIDDLE"))));
    String f=file(upload("Secret source"));
    String other=json.valueToTree(call(()->service.create(admin,new NaesinProductionService.Project("다른 중등 프로젝트","신사중","중3","기말")))).path("id").asText();
    assertNotEquals(project,other);assertThrows(ResponseStatusException.class,()->save(null,"v1",f,"REVIEW"));
    assertThrows(ResponseStatusException.class,()->service.publishedFile(student,999));
  }
  @Test void givenValidExtraction_whenSecondAiReads_thenCachedRevisionReused() throws Exception {
    String f=file(upload("A middle-school source passage."));
    var first=json.valueToTree(call(()->{try{return service.extract(editor,f,false);}catch(Exception e){throw new RuntimeException(e);}}));
    var second=json.valueToTree(call(()->{try{return service.extract(editor,f,false);}catch(Exception e){throw new RuntimeException(e);}}));
    assertEquals(first.path("id").asText(),second.path("id").asText());assertEquals("READY",first.path("work_status").asText());
    assertTrue(json.readTree(first.path("content").asText()).path("text").asText().contains("middle-school"));
  }
  @Test void givenTransactionFailureAfterUpload_whenRolledBack_thenNoFileOrRevisionOrBlobRemains() throws Exception {
    long before=Files.walk(dir).filter(Files::isRegularFile).count();
    assertThrows(IllegalStateException.class,()->call(()->{upload("Rollback original");throw new IllegalStateException("injected failure");}));
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM studio_files",Integer.class));
    assertEquals(before,Files.walk(dir).filter(Files::isRegularFile).count());
  }
  @Test void givenArchive_whenWriting_thenRejectedWithAllHistoryPreserved() {
    String f=file(upload("Archived source"));call(()->service.projectChange(admin,"ARCHIVE",null));
    assertThrows(ResponseStatusException.class,()->save(null,"v1",f,"REVIEW"));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM studio_files",Integer.class));
    call(()->service.projectChange(admin,"REOPEN",null));assertDoesNotThrow(()->save(null,"v1",f,"REVIEW"));
  }
  @Test void givenLibraryMiddleSchoolOriginal_whenRegistered_thenShaDownloadAndProvenanceMatch() throws Exception {
    String path=System.getenv("NAESIN_MIDDLE_ORIGINAL");
    org.junit.jupiter.api.Assumptions.assumeTrue(path!=null,"Private Library original is supplied only for local evidence");
    byte[] original=Files.readAllBytes(Path.of(path));
    var upload=call(()->{try{return service.upload(editor,new MockMultipartFile("file","신구중2_2026_1학기_기말_01.jpg","image/jpeg",original),
        "Library /내신Wiki/기출원본/신구중2/2026_1학기_기말/01.jpg","격리 검증만, 게시 권한 미확인");}catch(Exception e){throw new RuntimeException(e);}});
    var result=json.valueToTree(upload);String file=file(upload);
    assertArrayEquals(original,service.fileBytes(service.file(editor,file)));
    assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original)),result.path("file").path("sha256").asText());
    assertTrue(result.path("revision").path("content").asText().contains("격리 검증만"));
    assertEquals(0,json.valueToTree(service.published(student,0)).size());
  }
  @Test void givenHighAndMiddle_whenRegisterReviewPublish_thenSectionsAndExistingApprovalRemainSeparate() throws Exception {
    String middle=project, mf=file(upload("Middle original source")),mr=save(null,"v1",mf,"REVIEW");checks(mr);approve(mr);
    var mp=json.valueToTree(call(()->service.publish(admin,mr,new NaesinProductionService.Publication(mr))));
    String middleSha=(String)studio.file(admin,mf).get("sha256");
    division("HIGH");
    String high=json.valueToTree(call(()->service.create(admin,new NaesinProductionService.Project("고1 모의고사","시험고","고1","9월","HIGH")))).path("id").asText();
    String hf=file(call(()->{try{return service.upload(editor,new MockMultipartFile("file","고1_원본.txt","text/plain","High original source".getBytes()),"고1 강사 자료","미확인, 내부 검수 대기");}catch(Exception e){throw new RuntimeException(e);}}));
    assertThrows(ResponseStatusException.class,()->save(null,"v1",mf,"REVIEW"));
    String hr=save(null,"v1",hf,"REVIEW");checks(hr);approve(hr);
    var hp=json.valueToTree(call(()->service.publish(admin,hr,new NaesinProductionService.Publication(hr))));
    assertEquals(1,json.valueToTree(service.list(admin)).size());
    assertEquals(high,json.valueToTree(service.list(admin)).get(0).path("id").asText());
    assertEquals(hr,json.valueToTree(service.published(student,0)).get(0).path("revision_id").asText());
    assertThrows(ResponseStatusException.class,()->service.select(admin,middle));
    division("MIDDLE");service.select(admin,middle);
    assertEquals(middle,json.valueToTree(service.list(admin)).get(0).path("id").asText());
    assertEquals(mr,studio.revision(admin,mr).get("approved_id"));assertEquals(middleSha,studio.file(admin,mf).get("sha256"));
    assertEquals(mr,json.valueToTree(service.published(student,0)).get(0).path("revision_id").asText());
    assertThrows(ResponseStatusException.class,()->service.publishedFile(student,hp.path("publication_id").asLong()));
    assertEquals("Middle original source",new String(service.fileBytes(service.publishedFile(student,mp.path("publication_id").asLong()))));
  }
  @Test void givenLegacyMiddleProfileWithoutDivision_whenRead_thenStillMiddleAndNotRewritten() {
    var original=(String)db.queryForMap("SELECT content FROM studio_revisions WHERE project_id=? AND item_key='project-profile'",project).get("content");
    var legacy=json.readTree(original).deepCopy();((tools.jackson.databind.node.ObjectNode)legacy).remove("division");
    String text=json.writeValueAsString(legacy);db.update("UPDATE studio_revisions SET content=? WHERE project_id=? AND item_key='project-profile'",text,project);
    division("MIDDLE");service.select(admin,project);assertEquals(1,json.valueToTree(service.list(admin)).size());
    division("HIGH");assertEquals(0,json.valueToTree(service.list(admin)).size());
    assertEquals(text,db.queryForObject("SELECT content FROM studio_revisions WHERE project_id=? AND item_key='project-profile'",String.class,project));
  }
  @Test void givenImportKey_whenRetryingProject_thenSameProjectWithoutResettingSources() {
    var p=new NaesinProductionService.Project("Library 고등공통","","고등공통","수능","HIGH","library-high-csat");
    var first=json.valueToTree(call(()->service.create(admin,p)));String f=file(upload("Import source"));
    var second=json.valueToTree(call(()->service.create(admin,p)));assertEquals(first.path("id").asText(),second.path("id").asText());
    assertTrue(second.path("duplicate").asBoolean());assertNotNull(studio.file(admin,f));
    assertThrows(ResponseStatusException.class,()->call(()->service.create(admin,new NaesinProductionService.Project("충돌","","고3","다름","HIGH","library-high-csat"))));
  }
  @Test void givenClaudeHandoff_whenWorkReadsAndContinues_thenLatestRevisionAndSourceRemainConnected() {
    String f=file(upload("Shared original"));
    var task=new NaesinProductionService.Work("handoff-roundtrip",null,"v1","HANDOFF","AI 인계","Claude","원본 검토","문항·해설 확인","test-no-llm","격리 테스트","추출 완료","","Work가 문항 검수 이어서 진행","READY",null,List.of(f),List.of());
    String one=json.valueToTree(call(()->service.save(editor,task))).path("id").asText();
    var read=json.valueToTree(service.detail(editor,one));assertEquals("Claude",json.readTree(read.path("revision").path("content").asText()).path("ai").asText());
    var next=new NaesinProductionService.Work("handoff-roundtrip",one,"v2","HANDOFF","AI 인계","ChatGPT Work","검수 이어받기","기존 결과 재사용","test-no-llm","격리 테스트","검수 완료","","Claude는 최신 결과 확인","READY",null,List.of(f),List.of(one));
    String two=json.valueToTree(call(()->service.save(editor,next))).path("id").asText();
    assertEquals(two,studio.revision(admin,two).get("head_id"));assertEquals(one,studio.revision(admin,two).get("parent_id"));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM studio_files",Integer.class));
  }
  @Test void givenLargeOriginalStream_whenRegistered_thenNoWholeFileMemoryReadAndStreamingShaMatches()throws Exception{
    var path=dir.resolve("large-source.pdf");
    try(var out=Files.newOutputStream(path)){out.write("%PDF-1.7\n".getBytes());byte[] block=new byte[1024*1024];for(int i=0;i<21;i++)out.write(block);}
    long size=Files.size(path);
    Object uploaded=call(()->{try(var in=Files.newInputStream(path)){return service.uploadStream(editor,"고등_대용량.pdf",in,size,"격리 대용량 표본","권한 검수 대기");}catch(Exception e){throw new RuntimeException(e);}});
    String id=file(uploaded);var digest=java.security.MessageDigest.getInstance("SHA-256");
    try(var in=new java.security.DigestInputStream(service.fileStream(service.file(editor,id)),digest)){assertEquals(size,in.transferTo(java.io.OutputStream.nullOutputStream()));}
    assertEquals(java.util.HexFormat.of().formatHex(digest.digest()),json.valueToTree(uploaded).path("file").path("sha256").asText());
    assertTrue(json.valueToTree(uploaded).path("revision").path("content").asText().contains("PENDING_REVIEW"));
    assertThrows(ResponseStatusException.class,()->call(()->{try{return service.uploadStream(editor,"too-big.pdf",java.io.InputStream.nullInputStream(),NaesinProductionService.MAX_SOURCE_BYTES+1,"test","unknown");}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new RuntimeException(e);}}));
  }
  @Test void givenRealHighHwp_whenRegisteredExtractedAndHandedOff_thenOriginalAndCachedExtractionRemainConnected()throws Exception{
    String cli=System.getenv("NAESIN_KORDOC_CLI"),source=System.getenv("NAESIN_KORDOC_REAL_HWP");
    org.junit.jupiter.api.Assumptions.assumeTrue(cli!=null && source!=null,"Private original and pinned CLI are optional local evidence");
    division("HIGH");call(()->service.create(admin,new NaesinProductionService.Project("Library 실제 HWP 검증","","고등공통","수능","HIGH")));
    var path=Path.of(source);byte[] original=Files.readAllBytes(path);
    var extractor=(NaesinTextExtractor)ReflectionTestUtils.getField(service,"extractor");ReflectionTestUtils.setField(extractor,"kordocCli",cli);
    String f=file(call(()->{try(var in=Files.newInputStream(path)){return service.uploadStream(editor,path.getFileName().toString(),in,original.length,"Library 실제 고등 원본 · 격리 검증","권한 미확인");}catch(Exception e){throw new RuntimeException(e);}}));
    var extracted=json.valueToTree(call(()->{try{return service.extract(editor,f,false);}catch(Exception e){throw new RuntimeException(e);}}));
    assertEquals("READY",extracted.path("work_status").asText());
    assertTrue(json.readTree(extracted.path("content").asText()).path("text").asText().length()>100);
    String extraction=extracted.path("id").asText();
    String handoff=json.valueToTree(call(()->service.save(editor,new NaesinProductionService.Work("real-library-handoff",null,"v1","HANDOFF","Work 인계","ChatGPT Work","추출 결과 재사용","기존 Kordoc 추출과 원본 SHA 확인","test-no-llm","격리 등록·재열람","기존 추출 완료","","Claude에서 같은 원본과 추출 결과를 이어 확인","REVIEW",null,List.of(f),List.of(extraction))))).path("id").asText();
    assertTrue(json.valueToTree(service.detail(editor,handoff)).path("revision").path("content").asText().contains(extraction));
    var reused=json.valueToTree(call(()->{try{return service.extract(editor,f,false);}catch(Exception e){throw new RuntimeException(e);}}));
    assertEquals(extraction,reused.path("id").asText());assertArrayEquals(original,service.fileBytes(service.file(editor,f)));assertArrayEquals(original,Files.readAllBytes(path));
    assertEquals(0,json.valueToTree(service.published(student,0)).size());
  }

}
