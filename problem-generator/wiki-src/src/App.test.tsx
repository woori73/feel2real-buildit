import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import App from './App'
import CsatEnglishWorkspace from './components/CsatEnglishWorkspace'

const scrollIntoViewMock = vi.fn()

beforeEach(() => {
  const storage=()=>{const values=new Map<string,string>();return{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key),clear:()=>values.clear(),key:(index:number)=>[...values.keys()][index]??null,get length(){return values.size}}}
  vi.stubGlobal('localStorage',storage());vi.stubGlobal('sessionStorage',storage())
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url=String(input)
    if(url.endsWith('/students')) return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    if(url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}',{status:200,headers:{'Content-Type':'application/json'}})
    return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
  }))
  scrollIntoViewMock.mockClear()
  window.HTMLElement.prototype.scrollIntoView = scrollIntoViewMock
})
afterEach(() => { cleanup(); vi.unstubAllGlobals();delete document.modelContext })

describe('내신 Wiki V1.1 시나리오 홈', () => {
  it('수능 Workspace는 문제 DB와 4개 Assessment 제작 탭을 분리한다', async()=>{
    render(<CsatEnglishWorkspace/>)
    const tabs = screen.getByRole('navigation',{name:'수능 Workspace'})
    for(const label of ['문제 DB','HALF','FULL','WRONG','DAILY']) expect(within(tabs).getByRole('button',{name:label})).toBeInTheDocument()
    expect(screen.getByRole('heading',{name:'고3 수능 영어 자료 등록'})).toBeInTheDocument()
    fireEvent.click(within(tabs).getByRole('button',{name:'DAILY'}))
    expect(await screen.findByRole('heading',{name:'DAILY 제작'})).toBeInTheDocument()
    expect(screen.getByLabelText('DAY 수')).toHaveValue(1)
    expect(screen.queryByRole('heading',{name:'고3 수능 영어 자료 등록'})).not.toBeInTheDocument()
  })

  it('JSON Batch는 정상 파일과 실패 파일 상태를 독립적으로 표시한다',async()=>{
    vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
      const url=String(input)
      if(url.includes('/csat/json-imports/preview'))return new Response(JSON.stringify({files:[
        {source_id:null,source_hash:'a'.repeat(64),parser:'csat-json-v1',raw_text:'{}',metadata:{},source_filename:'ready.json',original_stored:false,owner_scope:'shared',requires_review:false,status:'READY',duplicate_source_id:null,failures:[],questions:[]},
        {source_id:null,source_hash:'b'.repeat(64),parser:'csat-json-v1',raw_text:'',metadata:{},source_filename:'broken.txt',original_stored:false,owner_scope:'shared',requires_review:false,status:'FAILED',duplicate_source_id:null,failures:[{index:1,reason:'JSON 파일만 업로드할 수 있습니다.'}],questions:[]},
      ],file_count:2,question_count:0,review_count:0,failed_count:1,duplicate_count:0}),{status:201,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    }))
    render(<CsatEnglishWorkspace/>)
    const input=screen.getByLabelText('수능 문제 JSON 업로드')
    fireEvent.change(input,{target:{files:[new File(['{}'],'ready.json',{type:'application/json'}),new File(['bad'],'broken.txt',{type:'text/plain'})]}})
    const states=await screen.findByRole('region',{name:'JSON Batch 상태'})
    expect(within(states).getByText('READY')).toBeInTheDocument()
    expect(within(states).getByText('FAILED')).toBeInTheDocument()
    expect(within(states).getByText('JSON 파일만 업로드할 수 있습니다.')).toBeInTheDocument()
  })

  it('해설이 없는 정상 JSON 문항도 READY로 표시한다',async()=>{
    vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
      const url=String(input)
      if(url.includes('/csat/json-imports/preview'))return new Response(JSON.stringify({files:[{
        source_id:null,source_hash:'c'.repeat(64),parser:'csat-json-v1',raw_text:'{}',
        metadata:{source_kind:'half'},source_filename:'no-explanation.json',original_stored:false,owner_scope:'shared',
        requires_review:false,status:'READY',failures:[],questions:[{id:0,source_id:0,exam_kind:'half',source_kind:'half',
          source_filename:'no-explanation.json',source_question_no:1,question_no:23,csat_type_id:'topic',csat_type_name:'주제',
          prompt:'다음 글의 주제로 가장 적절한 것은?',passage:'Passage',choices:['① one','② two'],answer:'①',solution:'',
          footnote:'',group_label:'',source_reference:'',type_mismatch:false,mismatch_reason:'',created_at:'',
          classification_status:'classified',problem:{instruction:'다음 글의 주제로 가장 적절한 것은?',passage:'Passage',choices:['① one','② two']},import_status:'READY'}]
      }],file_count:1,question_count:1,review_count:0,failed_count:0,duplicate_count:0}),{status:201,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    }))
    render(<CsatEnglishWorkspace/>)
    fireEvent.change(screen.getByLabelText('수능 문제 JSON 업로드'),{target:{files:[new File(['{}'],'no-explanation.json',{type:'application/json'})]}})
    const preview=await screen.findByRole('heading',{name:/문항 Preview/})
    const table=within(preview.closest('section') as HTMLElement)
    expect(table.getAllByText('없음').length).toBeGreaterThan(0)
    expect(table.getByText('(선택)')).toBeInTheDocument()
    expect(table.getByText('READY')).toBeInTheDocument()
    expect(table.getByRole('button',{name:'⑤ READY 문항 저장'})).toBeEnabled()
  })

  it('WRONG 탭은 Question ID가 연결된 기존 오답만 Assessment 대상으로 선택한다', async()=>{
    vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
      const url=String(input)
      if(url.includes('/csat/questions')) return new Response('[{"id":7,"source_id":1,"exam_kind":"half","source_filename":"fixture.html","question_no":21,"csat_type_id":"implication","csat_type_name":"함축 의미","passage":"Original","prompt":"의미하는 것은?","choices":[],"answer":"1","solution":"Because","footnote":"","group_label":"","source_reference":"","type_mismatch":false,"mismatch_reason":"","created_at":""}]',{status:200,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/csat/wrong-answers')) return new Response('[{"id":3,"question_id":7,"student_name":"TEST_STUDENT_001","exam_name":"TEST_EXAM","question_no":21,"csat_type_id":"implication","csat_type_name":"함축 의미","wrong_answer":"2","correct_answer":"1","memo":"","source_filename":"","created_at":""}]',{status:200,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    }))
    render(<CsatEnglishWorkspace/>)
    fireEvent.click(within(screen.getByRole('navigation',{name:'수능 Workspace'})).getByRole('button',{name:'WRONG'}))
    expect((await screen.findAllByText(/QID 7/)).length).toBeGreaterThan(0)
    const linkedRow = screen.getAllByText(/QID 7/).find(element=>element.tagName==='P') as HTMLElement
    const select = within(linkedRow.closest('article') as HTMLElement).getByRole('checkbox')
    fireEvent.click(select)
    expect(screen.getByRole('button',{name:'선택 오답으로 WRONG 저장'})).not.toBeDisabled()
  })

  it('top-level Wiki가 DOM 클릭 없는 CSAT WebMCP tools를 기존 API에 등록한다',async()=>{
    const registered:Array<{name:string;annotations:{readOnlyHint:boolean};execute:(input:Record<string,unknown>)=>Promise<unknown>}>=[]
    Object.defineProperty(document,'modelContext',{configurable:true,value:{registerTool:vi.fn(async tool=>{registered.push(tool)})}})
    const fetchMock=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
      const url=String(input)
      if(url.includes('/csat/questions'))return new Response('[{"id":7}]',{status:200,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/csat/assessments')&&init?.method==='POST')return new Response('{"id":9,"assessment_type":"daily","question_ids":[1,2,3,4]}',{status:201,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/exam-draft'))return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}',{status:200,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    })
    vi.stubGlobal('fetch',fetchMock)
    render(<App/>)
    await waitFor(()=>expect(registered).toHaveLength(7))
    expect(registered.map(tool=>tool.name)).toEqual(['search_csat_questions','search_csat_wrong_answers','link_csat_wrong_question','get_csat_assessment','create_csat_assessment','create_csat_wrong_assessment','replace_csat_assessment_question'])
    expect(registered.find(tool=>tool.name==='search_csat_questions')?.annotations.readOnlyHint).toBe(true)
    expect(registered.find(tool=>tool.name==='create_csat_assessment')?.annotations.readOnlyHint).toBe(false)
    await registered.find(tool=>tool.name==='search_csat_questions')?.execute({exam_kind:'full',question_no:34,source_round:'6',has_explanation:true,exclude_used:true})
    await registered.find(tool=>tool.name==='search_csat_wrong_answers')?.execute({student:'TEST_STUDENT_001',exam:'TEST_EXAM',linked:false})
    await registered.find(tool=>tool.name==='link_csat_wrong_question')?.execute({wrong_answer_id:3,question_id:7})
    await registered.find(tool=>tool.name==='create_csat_assessment')?.execute({assessment_type:'daily',days:1,question_ids:[1,2,3,4],exclude_used:true})
    expect(fetchMock.mock.calls.some(([url])=>String(url).includes('csat/questions?exam_kind=full&question_no=34&source_round=6&has_explanation=true&exclude_used=true'))).toBe(true)
    expect(fetchMock.mock.calls.some(([url])=>String(url).includes('csat/wrong-answers?student=TEST_STUDENT_001&exam=TEST_EXAM&linked=false'))).toBe(true)
    expect(fetchMock.mock.calls.some(([url,init])=>String(url).endsWith('/csat/wrong-answers/3/question')&&init?.method==='PATCH')).toBe(true)
    const write=fetchMock.mock.calls.find(([url,init])=>String(url).endsWith('/csat/assessments')&&init?.method==='POST')
    expect(JSON.parse(String(write?.[1]?.body))).toMatchObject({assessment_type:'daily',days:1,question_ids:[1,2,3,4],exclude_used:true})
  })

  it('WRONG bulk 표는 저장 전 행별 preview와 정상 행 독립 저장을 제공한다',async()=>{
    const fetchMock=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
      const url=String(input)
      if(url.endsWith('/csat/wrong-answers')&&init?.method==='POST')return new Response('{"id":41}',{status:201,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    })
    vi.stubGlobal('fetch',fetchMock)
    render(<CsatEnglishWorkspace/>);fireEvent.click(within(screen.getByRole('navigation',{name:'수능 Workspace'})).getByRole('button',{name:'WRONG'}))
    fireEvent.change(screen.getByLabelText('오답 표 붙여넣기'),{target:{value:'학생명\t시험명\t문항번호\t학생답\t정답\nTEST_STUDENT_001\tTEST_EXAM\t21\t2\t1\nTEST_STUDENT_002\tTEST_EXAM\t99\t3\t1'}})
    fireEvent.click(screen.getByRole('button',{name:'저장 전 Preview'}))
    const region=screen.getByRole('region',{name:'수능 오답 bulk 입력'})
    expect(within(region).getByText('3행 · FAILED')).toBeInTheDocument()
    fireEvent.click(within(region).getByRole('button',{name:'READY 행 저장'}))
    await waitFor(()=>expect(within(region).getByText('2행 · IMPORTED')).toBeInTheDocument())
    expect(fetchMock.mock.calls.filter(([url,init])=>String(url).endsWith('/csat/wrong-answers')&&init?.method==='POST')).toHaveLength(1)
  })

  it('학교·학년·연도·학기·시험 Context와 STEP 1~4를 순서대로 표시한다', async()=>{
    render(<App/>)
    expect(await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})).toBeInTheDocument()
    expect(screen.getByLabelText('시험 Context')).toBeInTheDocument()
    for(const step of ['STEP 1','STEP 2','STEP 3','STEP 4']) {
      expect(screen.getByText(step)).toBeInTheDocument()
    }
    for(const name of ['학교 프린트 추가','학생 오답 추가','수업 인사이트 기록','문제 만들기','기존 문제 변형','크롬 문제 변형','저장된 문제','FINAL 준비','직보 준비']) {
      expect(screen.getByRole('button',{name:new RegExp(name)})).toBeInTheDocument()
    }
    expect(screen.queryByRole('button',{name:'Admin / Final 자료 보기'})).not.toBeInTheDocument()
  })

  it('STEP 3의 크롬 문제 변형은 현재 5개 Context를 유지해 Workbench로 이동한다',async()=>{
    const schools=[{school:'신사중',grades:[{grade:'3학년',exams:[{exam:'전체',twinId:null,version:null,updatedAt:'',hasDigitalTwin:false}]}]}]
    vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
      const url=String(input)
      if(url.endsWith('/schools'))return new Response(JSON.stringify(schools),{status:200,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/students'))return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/exam-draft'))return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}',{status:200,headers:{'Content-Type':'application/json'}})
      if(url.includes('/workspaces/final/context'))return new Response(JSON.stringify({exam_context:{school:'신사중',grade:'3학년',year:new Date().getFullYear(),semester:'1학기',exam:'전체'},digital_twin:null,school_prints:[],class_insights:[]}),{status:200,headers:{'Content-Type':'application/json'}})
      if(url.includes('/questions?'))return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    }))
    render(<App/>);await screen.findByText('신사중 · 3학년')
    fireEvent.click(screen.getByRole('button',{name:'크롬 문제 변형'}))
    expect(await screen.findByRole('heading',{name:'크롬 문제 변형 Workbench'})).toBeInTheDocument()
    expect(screen.getByText(new RegExp(`신사중 · 3학년 · ${new Date().getFullYear()}년 · 1학기 · 전체`))).toBeInTheDocument()
    expect(JSON.parse(sessionStorage.getItem('buildit_chrome_context')||'{}')).toMatchObject({school:'신사중',grade:'3학년',semester:'1학기',exam:'전체'})
  })


  it('Chrome Workbench는 최초 진입 DOM에 QID, Twin, Insight와 학교프린트 전체 내용을 노출한다',async()=>{
    const year=new Date().getFullYear()
    const schools=[{school:'신사중',grades:[{grade:'3학년',exams:[{exam:'전체',twinId:9,version:1,updatedAt:'now',hasDigitalTwin:true}]}]}]
    const canonical={schema_version:'wiki-question-v1',kind:'mc',question_type:'객관식',
      instruction:{text:'QID 82 발문 TOKEN',html:'<p>QID 82 발문 TOKEN</p>'},
      passage:{text:'QID 82 전체 지문 TOKEN',html:'<p>QID 82 전체 지문 TOKEN</p>',status:'complete'},
      choices:[{number:1,text:'정답',html:'정답'},{number:2,text:'오답',html:'오답'}],conditions:[],
      answer:{choice_numbers:[1],accepted_answers:[],sample_answer:''},explanation:{text:'해설',html:'해설'},
      scoring:{score:1,multi:false,answer_format:'',grading_rule:''},source:{school:'신사중',grade:'3학년',year,semester:'1학기',exam:'전체',source_question_number:'82'}}
    const question={id:82,twin_id:9,school:'신사중',grade:'3학년',exam:'전체',mode:'new',q_kind:'mc',question_type:'객관식',
      source_text:'QID 82 전체 지문 TOKEN',stem:'QID 82 발문 TOKEN',options:['정답','오답'],answer:[1],multi:0,score:1,conditions:[],
      answer_format:'',accepted_answers:[],sample_answer:'',grading_rule:'해설',exam_title:'시험',exam_group_id:'g82',favorite:0,created_at:'now',canonical}
    vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
      const url=String(input)
      if(url.endsWith('/schools'))return new Response(JSON.stringify(schools),{status:200,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/students'))return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
      if(url.endsWith('/exam-draft'))return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}',{status:200,headers:{'Content-Type':'application/json'}})
      if(url.includes('/workspaces/final/context'))return new Response(JSON.stringify({
        exam_context:{school:'신사중',grade:'3학년',year,semester:'1학기',exam:'전체'},
        digital_twin:{id:9,description:'TWIN INITIAL DOM TOKEN'},
        class_insights:[{id:21,content:'INSIGHT INITIAL DOM TOKEN',source:'school_teacher',created_at:'now'}],
        school_prints:[{id:31,title:'5과 학교프린트',html_text:'<p>SCHOOL PRINT INITIAL DOM TOKEN</p>',json_text:{reading:'PRINT JSON TOKEN'}}],
      }),{status:200,headers:{'Content-Type':'application/json'}})
      if(url.includes('/questions?'))return new Response(JSON.stringify([question]),{status:200,headers:{'Content-Type':'application/json'}})
      return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    }))
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:'크롬 문제 변형'}))
    expect(await screen.findByRole('heading',{name:'크롬 문제 변형 Workbench'})).toBeInTheDocument()
    expect((await screen.findAllByText(/QID 82/)).length).toBeGreaterThan(0)
    for(const token of ['TWIN INITIAL DOM TOKEN','INSIGHT INITIAL DOM TOKEN','SCHOOL PRINT INITIAL DOM TOKEN','PRINT JSON TOKEN','QID 82 전체 지문 TOKEN']){
      expect((await screen.findAllByText(new RegExp(token))).length).toBeGreaterThan(0)
    }
    expect(screen.getByRole('button',{name:/Digital Twin접기/})).toBeInTheDocument()
    expect(screen.getByRole('button',{name:/학교 선생님 Insight #21접기/})).toBeInTheDocument()
    expect(screen.getByRole('button',{name:/5과 학교프린트접기/})).toBeInTheDocument()
  })

  it('STEP 1의 학생 오답 추가는 학생 선택·오답 원본·JSON 필드만 열고 자료 제목·구형 메타 입력이 없다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학생 오답 추가/}))
    const heading = screen.getByRole('heading',{name:'학생 오답 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    expect(form.getByLabelText(/^학생/)).toBeInTheDocument()
    expect(form.getByLabelText('오답 원본 파일')).toBeInTheDocument()
    expect(form.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(form.getByLabelText('또는 JSON 붙여넣기')).toBeInTheDocument()
    expect(form.queryByLabelText('자료 제목')).not.toBeInTheDocument()
    expect(form.queryByLabelText(/문제 유형|문제번호|시험명/)).not.toBeInTheDocument()
    const saveButton = form.getByRole('button', { name: '저장' })
    expect(saveButton).toBeDisabled()
    fireEvent.change(form.getByLabelText('오답 원본 파일'), { target: { files: [new File(['x'], 'wrong.jpg', { type: 'image/jpeg' })] } })
    expect(saveButton).toBeDisabled()
  })

  it('STEP 1의 학교 프린트 추가는 자료 제목·HTML·JSON(선택) 필드만 열고 원본 업로드가 없으며 HTML만으로 저장할 수 있다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const heading = screen.getByRole('heading',{name:'학교 프린트 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    expect(form.getByLabelText('자료 제목')).toBeInTheDocument()
    expect(form.getByLabelText('HTML 자료 파일 선택')).toBeInTheDocument()
    expect(form.getByLabelText('HTML 붙여넣기')).toBeInTheDocument()
    expect(form.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(form.queryByLabelText('원본 파일 업로드')).not.toBeInTheDocument()
    expect(form.queryByLabelText(/^학생/)).not.toBeInTheDocument()
    const saveButton = form.getByRole('button', { name: '저장' })
    expect(saveButton).toBeDisabled()
    fireEvent.change(form.getByLabelText('HTML 붙여넣기'), { target: { value: '<p>5과 본문 HTML</p>' } })
    expect(saveButton).not.toBeDisabled()
  })

  it('학교 프린트 HTML 파일을 선택하면 브라우저에서 읽어 붙여넣기 textarea에 채운다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const heading = screen.getByRole('heading',{name:'학교 프린트 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    const file = new File(['<p>파일에서 읽은 HTML</p>'], 'print.html', { type: 'text/html' })
    fireEvent.change(form.getByLabelText('HTML 자료 파일 선택'), { target: { files: [file] } })
    await waitFor(() => expect((form.getByLabelText('HTML 붙여넣기') as HTMLTextAreaElement).value).toBe('<p>파일에서 읽은 HTML</p>'))
  })

  it('학교 프린트 추가 클릭 시 등록 폼이 열리고 즉시(behavior:auto) 그 위치까지 이동한다', async()=>{
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const form = screen.getByRole('heading',{name:'학교 프린트 추가'}).closest('form') as HTMLElement
    expect(form).toBeInTheDocument()
    expect(form.id).toBe('material-set-form')
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled())
    expect(scrollIntoViewMock.mock.instances[scrollIntoViewMock.mock.instances.length - 1]).toBe(form)
    expect(scrollIntoViewMock).toHaveBeenLastCalledWith({ behavior: 'auto', block: 'start' })
  })

  it('학생 오답 추가 클릭 시 등록 폼이 열리고 즉시(behavior:auto) 그 위치까지 이동한다', async()=>{
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:/학생 오답 추가/}))
    const form = screen.getByRole('heading',{name:'학생 오답 추가'}).closest('form') as HTMLElement
    expect(form).toBeInTheDocument()
    expect(form.id).toBe('material-set-form')
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled())
    expect(scrollIntoViewMock.mock.instances[scrollIntoViewMock.mock.instances.length - 1]).toBe(form)
    expect(scrollIntoViewMock).toHaveBeenLastCalledWith({ behavior: 'auto', block: 'start' })
  })

  it('수업 인사이트 기록 버튼의 기존 스크롤 동작은 회귀 없이 그대로 유지된다', async()=>{
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:/수업 인사이트 기록/}))
    const insightSection = document.getElementById('insight-entry')
    expect(insightSection).toBeInTheDocument()
    expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth' })
    expect(scrollIntoViewMock.mock.instances[scrollIntoViewMock.mock.instances.length - 1]).toBe(insightSection)
  })

  it('버튼에 저장 건수를 "추가 · 저장 N건" 형식으로 표시한다', async()=>{
    render(<App/>)
    const printButton = await screen.findByRole('button',{name:/학교 프린트 추가\s*·\s*저장\s*0\s*건/})
    expect(printButton.textContent).toBe('학교 프린트 추가 · 저장 0건')
    const wrongButton = screen.getByRole('button',{name:/학생 오답 추가\s*·\s*저장\s*0\s*건/})
    expect(wrongButton.textContent).toBe('학생 오답 추가 · 저장 0건')
  })

  it('STEP 1에는 구형 도구(OCR·재구성, 오답 사진 추출, 학생별 오답노트) 안내가 없다', async()=>{
    render(<App/>)
    await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})
    expect(screen.queryByText(/OCR·재구성 도구/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:/오답 사진 추출/})).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:/학생별 오답노트/})).not.toBeInTheDocument()
  })

  it('시험 Context는 전체/중간/기말만 선택할 수 있고 기본값은 전체다', async()=>{
    render(<App/>)
    await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})
    const examSelect = within(screen.getByLabelText('시험 Context')).getByLabelText('시험') as HTMLSelectElement
    const optionLabels = [...examSelect.options].map(o => o.textContent)
    expect(optionLabels).toEqual(['전체', '중간', '기말'])
    expect(examSelect.value).toBe('전체')
  })

  it('학교 목록 조회가 늦게 끝나도 그 사이 사용자가 이미 고른 시험 Context는 전체로 되돌아가지 않는다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    let resolveSchools: (value: Response) => void = () => {}
    const schoolsPromise = new Promise<Response>(resolve => { resolveSchools = resolve })
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/schools')) return schoolsPromise
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    // 학교 목록 응답이 아직 오지 않은 상태(운영 네트워크 지연 재현)에서 사용자가 먼저 시험을 바꾼다
    fireEvent.change(within(screen.getByLabelText('시험 Context')).getByLabelText('시험'), { target: { value: '중간고사' } })
    // 뒤늦게 학교 목록 조회가 끝난다
    resolveSchools(new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    await screen.findByText('신사중 · 3학년')
    const examSelect = within(screen.getByLabelText('시험 Context')).getByLabelText('시험') as HTMLSelectElement
    expect(examSelect.value).toBe('중간고사')
  })

  it('학교 프린트 저장 후 동일한 5개 시험 Context query로 재조회해 저장 자료를 표시한다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    let saved = false
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-materials') && init?.method === 'POST') {
        saved = true
        return new Response('{"id":7}', { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.includes('/exam-materials?')) return new Response(saved ? '[{"id":7,"core_type":"school_print","student_id":null,"student_name":null,"title":"5과 학교 프린트","original_name":"","created_at":"","has_html":1}]' : '[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.change(within(screen.getByLabelText('시험 Context')).getByLabelText('시험'), { target: { value: '중간고사' } })
    await screen.findByText(`${new Date().getFullYear()}년 · 1학기 · 중간`)
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    expect(within(formElement).getByText(/중간에 자동 연결됩니다/)).toBeInTheDocument()
    fireEvent.change(within(formElement).getByLabelText('HTML 붙여넣기'), { target: { value: '<p>5과 본문 HTML</p>' } })
    fireEvent.submit(formElement)
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, init]) => String(u).includes('exam-materials') && init?.method === 'POST')).toBe(true))
    const call = fetchMock.mock.calls.find(([u, init]) => String(u).includes('exam-materials') && init?.method === 'POST')
    const form = call?.[1]?.body as FormData
    expect(form.get('school')).toBe('신사중')
    expect(form.get('grade')).toBe('3학년')
    expect(form.get('exam')).toBe('중간고사')
    expect(form.get('html_text')).toBe('<p>5과 본문 HTML</p>')
    expect(form.get('original')).toBeNull()
    await screen.findByRole('button', { name: /학교 프린트 추가\s*·\s*저장\s*1\s*건/ })
    fireEvent.click(screen.getByRole('button', { name: 'FINAL 준비' }))
    await screen.findByText('5과 학교 프린트')
    const getCalls = fetchMock.mock.calls.filter(([u, init]) => String(u).includes('/exam-materials?') && !init?.method)
    const savedContextCall = getCalls[getCalls.length - 1]
    const query = new URL(String(savedContextCall[0]), 'https://feel2real.co.kr').searchParams
    expect(Object.fromEntries(query)).toEqual({ school: '신사중', grade: '3학년', year: String(new Date().getFullYear()), semester: '1학기', exam: '중간고사' })
  })

  it('HTML 붙여넣기와 JSON 파일 선택 후 저장 버튼 click이 POST를 만들고 동일 Context의 저장 자료를 표시한다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    let saved = false
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-materials') && init?.method === 'POST') {
        saved = true
        return new Response('{"id":8}', { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.includes('/exam-materials?')) return new Response(saved ? '[{"id":8,"core_type":"school_print","student_id":null,"student_name":null,"title":"JSON 파일 조합","original_name":"","created_at":"","has_html":1}]' : '[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.change(within(screen.getByLabelText('시험 Context')).getByLabelText('시험'), { target: { value: '중간고사' } })
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    const jsonFile = new File(['{"items":[{"id":1}]}'], 'print.json', { type: 'application/json' })
    fireEvent.change(within(formElement).getByLabelText('자료 제목'), { target: { value: 'JSON 파일 조합' } })
    fireEvent.change(within(formElement).getByLabelText('HTML 붙여넣기'), { target: { value: '<p>HTML + JSON file</p>' } })
    fireEvent.change(within(formElement).getByLabelText('JSON 파일 업로드'), { target: { files: [jsonFile] } })
    expect((within(formElement).getByLabelText('또는 JSON 붙여넣기') as HTMLTextAreaElement).value).toBe('')
    fireEvent.click(within(formElement).getByRole('button', { name: '저장' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, init]) => String(u).endsWith('/exam-materials') && init?.method === 'POST')).toBe(true))
    const postCall = fetchMock.mock.calls.find(([u, init]) => String(u).endsWith('/exam-materials') && init?.method === 'POST')
    const payload = postCall?.[1]?.body as FormData
    expect(payload.get('html_text')).toBe('<p>HTML + JSON file</p>')
    expect(payload.get('json_file')).toBe(jsonFile)
    expect(payload.get('json_text')).toBe('')
    expect(payload.get('school')).toBe('신사중')
    expect(payload.get('grade')).toBe('3학년')
    expect(payload.get('exam')).toBe('중간고사')
    expect(await screen.findByRole('status')).toHaveTextContent('HTML과 JSON을 한 세트로 저장했습니다.')
    expect(screen.getByRole('button', { name: /학교 프린트 추가\s*·\s*저장\s*1\s*건/ })).toBeInTheDocument()
    expect(within(screen.getByLabelText('시험 Context')).getByLabelText('시험')).toHaveValue('중간고사')
    fireEvent.click(screen.getByRole('button', { name: 'FINAL 준비' }))
    expect(await screen.findByText('JSON 파일 조합')).toBeInTheDocument()
  })

  it('HTML이 없는 제출 경로는 조용히 끝내지 않고 저장 불가 이유를 표시한다', async()=>{
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    fireEvent.submit(formElement)
    expect(await screen.findByRole('status')).toHaveTextContent('학교 프린트 HTML을 입력하거나 HTML 파일을 선택해 주세요.')
  })

  it('학교 프린트 저장이 422 detail 배열로 실패해도 문자열 오류를 표시하고 Wiki는 crash하지 않는다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-materials') && init?.method === 'POST') return new Response(JSON.stringify({ detail: [
        { type: 'missing', loc: ['query', 'school'], msg: 'Field required' },
        { type: 'missing', loc: ['query', 'grade'], msg: 'Field required' },
      ] }), { status: 422, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    fireEvent.change(within(formElement).getByLabelText('HTML 붙여넣기'), { target: { value: '<p>5과 본문 HTML</p>' } })
    fireEvent.submit(formElement)
    expect(await screen.findByRole('status')).toHaveTextContent('저장하지 못했습니다. (Field required)')
    expect(screen.getByRole('heading', { name: '시험 자료 한눈에 모으기' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '학교 프린트 추가' })).toBeInTheDocument()
  })

  it('STEP 4의 FINAL 준비는 출제자 Digital Twin을 포함한 5 CORE 선택형 워크스페이스를 연다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'FINAL 준비'}))
    const heading = await screen.findByRole('heading',{name:'현재 시험 자료'})
    const workspace = within(heading.closest('section') as HTMLElement)
    for(const title of ['출제자 Digital Twin','제작 문제','학교 프린트','학생 오답','수업 인사이트']) {
      expect(workspace.getByRole('heading',{name:new RegExp(title)})).toBeInTheDocument()
    }
  })

  it('FINAL Workspace에는 FINAL 완성본을 저장하는 영역이 있고 결과 파일 또는 HTML 중 하나만 있으면 저장할 수 있다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'FINAL 준비'}))
    const heading = await screen.findByRole('heading',{name:'현재 시험 자료'})
    const workspace = within(heading.closest('section') as HTMLElement)
    expect(workspace.getByText('아직 저장된 FINAL 완성본이 없습니다. 외부에서 만든 결과물을 아래에서 저장할 수 있습니다.')).toBeInTheDocument()
    const saveButton = workspace.getByRole('button',{name:'FINAL 완성본 저장'})
    expect(saveButton).toBeDisabled()
    fireEvent.change(workspace.getByLabelText('HTML 붙여넣기'),{target:{value:'<p>FINAL 완성본</p>'}})
    expect(saveButton).not.toBeDisabled()
  })

  it('STEP 4의 직보 준비는 FINAL 완성본이 없으면 대기 안내를 보여준다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'직보 준비'}))
    expect(await screen.findByText('아직 FINAL 자료가 없습니다. FINAL 제작 후 직보를 준비할 수 있습니다.')).toBeInTheDocument()
  })

  it('직보 준비는 FINAL 완성본이 있으면 FINAL 완성본 → 최신 수업 인사이트 → 학생 반복 오답 순으로 보여준다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.includes('/final-deliverables?')) return new Response(JSON.stringify([{ id: 1, kind: 'final', school: '신사중', grade: '3학년', year: new Date().getFullYear(), semester: '1학기', exam: '전체', title: '기말 FINAL', original_name: '', created_at: new Date().toISOString(), has_html: 1 }]), { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.click(screen.getByRole('button', { name: '직보 준비' }))
    const heading = await screen.findByRole('heading',{name:'직보 준비'})
    const workspace = within(heading.closest('section') as HTMLElement)
    expect(workspace.queryByText('아직 FINAL 자료가 없습니다. FINAL 제작 후 직보를 준비할 수 있습니다.')).not.toBeInTheDocument()
    const coreHeadings = workspace.getAllByRole('heading',{level:3}).map(el=>el.textContent)
    const order = ['FINAL 완성본','최신 수업 인사이트','학생 반복 오답'].map(label=>coreHeadings.findIndex(text=>text?.includes(label)))
    expect(order.every(index=>index>=0)).toBe(true)
    expect(order).toEqual([...order].sort((a,b)=>a-b))
    expect(workspace.getByText(/기말 FINAL/)).toBeInTheDocument()
  })

  it('FINAL Workspace의 AI Context 보기는 GET /api/workspaces/final/context 응답을 그대로 보여준다', async()=>{
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/workspaces/final/context')) return new Response(JSON.stringify({ exam_context: { school: '신사중', grade: '3학년', year: 2026, semester: '1학기', exam: '전체' }, digital_twin: null, school_prints: [], student_errors: [], class_insights: [], generated_questions: [], final_deliverables: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'FINAL 준비'}))
    const heading = await screen.findByRole('heading',{name:'현재 시험 자료'})
    const workspace = within(heading.closest('section') as HTMLElement)
    fireEvent.click(workspace.getByRole('button',{name:'AI Context 보기'}))
    const preview = await workspace.findByText(/"exam_context"/)
    expect(preview.textContent).toContain('"school": "신사중"')
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/workspaces/final/context'))).toBe(true)
  })

  it('직보 Workspace의 AI Context 보기는 GET /api/workspaces/jikbo/context 응답을 그대로 보여준다', async()=>{
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/workspaces/jikbo/context')) return new Response(JSON.stringify({ exam_context: { school: '신사중', grade: '3학년', year: 2026, semester: '1학기', exam: '전체' }, ready: false, latest_final: null, post_final_insights: [], student_weaknesses: [], jikbo_deliverables: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'직보 준비'}))
    const heading = await screen.findByRole('heading',{name:'직보 준비'})
    const workspace = within(heading.closest('section') as HTMLElement)
    fireEvent.click(workspace.getByRole('button',{name:'AI Context 보기'}))
    const preview = await workspace.findByText(/"ready": false/)
    expect(preview).toBeInTheDocument()
  })
})
