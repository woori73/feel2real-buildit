package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** One conversation orchestrates existing academic and ledger draft services, never executes them. */
@Service @RequiredArgsConstructor
public class AssistantConversationService {
  private final UnifiedAssistantService service;
  private final AssistantIntentAnalyzer intents;
  private final AssistantMaterialAnalyzer materials;
  public record Turn(String message, String analysis, AssistantCommandParser.Suggestion suggestion,
                     List<AssistantDraft> drafts, List<String> issues, Object overview, Object data) {
    public Turn(String message, String analysis, AssistantCommandParser.Suggestion suggestion,
                List<AssistantDraft> drafts, List<String> issues, Object overview) { this(message,analysis,suggestion,drafts,issues,overview,null); }
  }
  private static final Map<String,List<String>> REQUIRED=Map.ofEntries(
      Map.entry("STUDENT_CREATE",List.of("name")),Map.entry("STUDENT_UPDATE",List.of("studentId","name")),
      Map.entry("CLASS_CREATE",List.of("name","teacherId")),Map.entry("CLASS_UPDATE",List.of("classId")),
      Map.entry("CLASS_ASSIGN",List.of("classId","studentId")),Map.entry("ATTENDANCE_RECORD",List.of("studentId","eventType")),
      Map.entry("CONSULT_CREATE",List.of("studentId","title","content")),Map.entry("CONSULT_MESSAGE",List.of("consultId","content")),
      Map.entry("LEDGER_ENTRY",List.of("occurredOn","direction","amount")));

  public Turn send(AuthenticatedPrincipal p, Long academyId, Long businessId, String message) {
    service.actor(p);
    var analysis=intents.analyze(p.userId(),message,null);var s=analysis.suggestion();
    if(s.operation().equals("OVERVIEW")) return new Turn("허용된 학원과 사업체의 현황을 재조회했습니다.",analysis.method(),s,List.of(),List.of(),service.overview(p,businessId,academyId));
    if(s.operation().equals("NOTICE_DRAFT")) return new Turn("공지 문안 초안입니다. 저장·발송하지 않았으니 필요하면 복사해 기존 공지 화면에서 사용하세요.",analysis.method(),s,List.of(),List.of(),null,Map.of("notice",noticeText(message,s)));
    if(s.operation().equals("ATTENDANCE_QUERY")) {
      Object id=s.input().get("studentId");
      if(id==null) return new Turn("조회할 학생 ID가 필요합니다. 예: 학생 ID 5 출결 조회해줘",analysis.method(),s,List.of(),List.of(),null);
      return new Turn("출결 내역을 조회만 했습니다. 기록을 만들지 않았습니다.",analysis.method(),s,List.of(),List.of(),null,service.attendanceQuery(p,academyId,((Number)id).longValue()));
    }
    if(!complete(s)) return new Turn(s.message()+" 빠진 값은 아래 초안 보완에서 입력하세요.",analysis.method(),s,List.of(),List.of(),null);
    var d=service.draft(p,academyId,businessId,s.operation(),AssistantJson.read(AssistantJson.write(s.input())));
    return new Turn("업무 초안을 만들었습니다. 대상과 내용을 확인하고 승인하면 실제 저장 후 다시 조회합니다.",analysis.method(),s,List.of(d),List.of(),null);
  }
  public Turn upload(AuthenticatedPrincipal p,Long academyId,Long businessId,String filename,byte[] bytes) throws IOException {
    service.actor(p);
    var a=materials.analyze(p.userId(),filename,bytes);
    if(a.kind().equals("LEDGER_IMPORT")) {
      var d=service.upload(p,businessId,filename,bytes);
      return new Turn("거래·증빙 자료를 분석해 장부 초안을 만들었습니다. 오류 행과 증빙을 확인한 뒤 승인하세요.","기존 이지장부 CSV 분석·분류",null,List.of(d),a.issues(),null);
    }
    var drafts=new ArrayList<AssistantDraft>();var issues=new ArrayList<>(a.issues());
    // Check every requested scope before creating any of a mixed material's drafts.
    for(var item:a.items()) if(complete(item.suggestion())) service.checkDraftScope(p,academyId,businessId,item.suggestion().operation());
    for(var item:a.items()) {
      if(!complete(item.suggestion())) {issues.add(item.row()+"행: 대상 ID 또는 필수 내용이 부족하거나 지원하지 않는 지시입니다. 초안을 만들지 않았습니다.");continue;}
      try {drafts.add(service.draft(p,academyId,businessId,item.suggestion().operation(),AssistantJson.read(AssistantJson.write(item.suggestion().input()))));}
      catch(ResponseStatusException e) {
        // Authority failures must not become a success-shaped response.
        if(e.getStatusCode().value()==401||e.getStatusCode().value()==403) throw e;
        issues.add(item.row()+"행: 초안 생성 실패. 권한·연결 상태 또는 대기 초안 수를 확인하세요.");
      } catch(IllegalArgumentException e) {issues.add(item.row()+"행: 초안 생성 실패. 입력 항목·날짜·ID를 확인하세요.");}
    }
    return new Turn(a.kind()+" "+a.rows()+"행 분석 · 초안 "+drafts.size()+"개 · 확인 사항 "+issues.size()+"개. 아직 실제 업무에 저장하지 않았습니다.","자료의 열·업무 지시 분석",null,drafts,issues,null);
  }
  /** Plain-text proposal only; nothing is stored or sent. */
  static String noticeText(String message,AssistantCommandParser.Suggestion s) {
    String title=String.valueOf(s.input().getOrDefault("title","")).strip();
    String body=String.valueOf(s.input().getOrDefault("content","")).strip();
    if(body.isEmpty()) body=message.replaceAll("공지|작성|해줘|해 줘|써줘|초안|만들어줘|부탁","").replaceAll("[,;]"," ").strip();
    if(title.isEmpty()) title="학원 안내";
    return "제목: "+title+"\n\n안녕하세요, 학부모님.\n"+(body.isEmpty()?"(안내할 내용을 입력해 주세요)":body)+"\n문의는 학원으로 연락 주세요.\n감사합니다.";
  }
  static boolean complete(AssistantCommandParser.Suggestion s) {
    return REQUIRED.containsKey(s.operation())&&REQUIRED.get(s.operation()).stream().allMatch(k->s.input().get(k)!=null&&!s.input().get(k).toString().isBlank());
  }
}
