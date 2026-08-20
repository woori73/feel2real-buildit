package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemGeneratorAiFrontendController {

  @GetMapping(value = "/js/problem_generator.js", produces = "application/javascript;charset=UTF-8")
  public ResponseEntity<String> problemGeneratorJavascript() throws IOException {
    ClassPathResource resource = new ClassPathResource("static/js/problem_generator.js");
    String legacy = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/javascript;charset=UTF-8"))
        .body(legacy + "\n" + aiFrontendPatch());
  }

  private String aiFrontendPatch() {
    return """
      ;(function(){
        var mode='generate';
        var lastAiResult=null;
        function $(s){return document.querySelector(s)}
        function esc(v){return String(v==null?'':v).replace(/[&<>\"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c]})}
        function status(msg,bad){var el=$('#generationStatus');if(el){el.textContent=msg||'';el.style.color=bad?'#b42318':'#067647'}}
        function selectedTwin(){var s=$('#pgMaterialSelect');return s&&s.selectedIndex>=0?s.options[s.selectedIndex].text:''}
        function render(text,model){
          var out=$('#generationOutput');if(!out)return;var p=null;try{p=JSON.parse(text)}catch(e){}
          var title=(mode==='transform'?'Luna 변형 결과':'Gemini 생성 결과')+' · '+esc(model||'AI');
          if(p&&Array.isArray(p.questions)){
            out.innerHTML='<div class=\"result-toolbar\"><div><div class=\"result-title\">'+title+'</div></div><div class=\"result-actions\"><button class=\"btn-ghost\" type=\"button\" id=\"copyAiResult\">JSON 복사</button></div></div><div class=\"exam-sheet\"><div class=\"exam-meta-box\"><div class=\"meta-row\"><span>요약</span><b>'+esc(p.summary||'완료')+'</b></div></div><div class=\"question-list\">'+p.questions.map(function(q,i){return '<article class=\"q-card\"><div class=\"q-head\"><span class=\"q-num\">'+(i+1)+'.</span><b>'+esc(q.type||'문항')+'</b><span class=\"q-score\">'+esc(q.score||'')+'점</span></div><p class=\"q-stem\">'+esc(q.stem||'')+'</p>'+(Array.isArray(q.choices)?'<ol class=\"opt-list\">'+q.choices.map(function(c){return '<li>'+esc(c)+'</li>'}).join('')+'</ol>':'')+'<div class=\"answer-format\">정답: '+esc(q.answer||'')+'</div><div class=\"grading-rule\">'+esc(q.explanation||'')+'</div></article>'}).join('')+'</div><pre id=\"rawAiResult\" class=\"raw-result\">'+esc(text)+'</pre></div>';
          } else out.innerHTML='<div class=\"result-title\">'+title+'</div><pre id=\"rawAiResult\" class=\"raw-result\">'+esc(text)+'</pre>';
          var copy=$('#copyAiResult');if(copy)copy.onclick=function(){if(navigator.clipboard)navigator.clipboard.writeText(text)};
        }
        function configureUi(){
          var keyCard=document.querySelector('[data-testid=\"api-key-card\"]');if(keyCard)keyCard.style.display='none';
          var tabs=document.querySelectorAll('.mode-tabs .mode-tab');
          if(tabs[0]){tabs[0].removeAttribute('data-action');tabs[0].dataset.aiMode='generate';tabs[0].classList.add('active')}
          if(tabs[1]){tabs[1].removeAttribute('data-action');tabs[1].dataset.aiMode='transform'}
          var intro=document.querySelector('.mode-panel .panel-intro');if(intro)intro.innerHTML='<b>신규 문제</b>는 Gemini 3.5 Flash, <b>기존 문제 변형</b>은 GPT-5.6 Luna를 사용합니다. API 키는 서버에서 안전하게 관리됩니다.';
          var gen=$('#generateGeminiQuestions');if(gen)gen.textContent='🚀 Gemini 3.5 Flash로 문제 생성';
          var empty=document.querySelector('#generationOutput .empty-desc');if(empty)empty.innerHTML='왼쪽에서 학교를 선택하고 핵심 문법·새 지문을 입력한 뒤<br>생성 버튼을 누르면 여기에 시험지가 나타납니다.';
          checkAiStatus();
        }
        async function checkAiStatus(){try{var r=await fetch('/api/problem-generator/ai/status');if(!r.ok)return;var b=await r.json();var h=$('#pgMaterialHint');if(h)h.textContent=(h.textContent||'')+' · Gemini '+(b.geminiReady?'준비됨':'미설정')+' / Luna '+(b.lunaReady?'준비됨':'미설정')}catch(e){}}
        function setMode(next){
          mode=next;document.querySelectorAll('.mode-tabs .mode-tab').forEach(function(b){b.classList.toggle('active',b.dataset.aiMode===mode)});
          var grammar=$('#pgGrammar'),passage=$('#pgPassage'),button=$('#generateGeminiQuestions');
          if(mode==='transform'){
            if(grammar)grammar.placeholder='변형 지시 예: 어법형을 서술형으로, 함정 선택지 강화';
            if(passage)passage.placeholder='변형할 기존 문제 전체를 붙여넣으세요.';
            if(button)button.textContent='♻️ GPT-5.6 Luna로 기존 문제 변형';
          }else{
            if(grammar)grammar.placeholder='예: 분사구문, 관계대명사 what, 주요 회화 표현 등';
            if(passage)passage.placeholder='생성할 영어 지문, 대화문, 또는 테스트 문장을 붙여넣으세요.';
            if(button)button.textContent='🚀 Gemini 3.5 Flash로 문제 생성';
          }
          status('',false);
        }
        async function runAi(){
          var grammar=($('#pgGrammar')||{}).value||'', passage=($('#pgPassage')||{}).value||'', difficulty=($('#pgDifficulty')||{}).value||'학교 평균', count=Number(($('#pgCount')||{}).value||3), twin=selectedTwin(), button=$('#generateGeminiQuestions');
          if(mode==='generate'&&!grammar.trim()&&!passage.trim()){status('핵심 문법 또는 새 지문을 입력해 주세요.',true);return}
          if(mode==='transform'&&!passage.trim()){status('변형할 기존 문제를 새로운 지문/출제 조건 칸에 붙여넣어 주세요.',true);return}
          button.disabled=true;status(mode==='transform'?'GPT-5.6 Luna가 문제를 변형 중입니다...':'Gemini 3.5 Flash가 문제를 생성 중입니다...',false);
          try{
            var url,payload;
            if(mode==='transform'){url='/api/problem-generator/luna/transform';payload={sourceQuestion:passage,instructions:grammar,difficulty:difficulty,digitalTwinContext:twin}}
            else {url='/api/problem-generator/gemini/generate';payload={apiKey:'',grammar:grammar,passage:passage,count:count,difficulty:difficulty,materialTitle:twin}}
            var r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});var raw=await r.text(),body=null;try{body=raw?JSON.parse(raw):null}catch(e){}
            if(!r.ok)throw new Error(body&&body.detail?body.detail:(raw||'AI 호출 실패'));
            lastAiResult=body;render(body.text,body.model);status((mode==='transform'?'변형':'생성')+' 완료',false);
          }catch(e){status(e&&e.message?e.message:'AI 호출 실패',true)}finally{button.disabled=false}
        }
        document.addEventListener('click',function(e){
          var tab=e.target.closest('[data-ai-mode]');if(tab){e.preventDefault();e.stopImmediatePropagation();setMode(tab.dataset.aiMode);return}
          var gen=e.target.closest('#generateGeminiQuestions');if(gen){e.preventDefault();e.stopImmediatePropagation();runAi();return}
        },true);
        if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',configureUi);else configureUi();
      })();
      """;
  }
}
