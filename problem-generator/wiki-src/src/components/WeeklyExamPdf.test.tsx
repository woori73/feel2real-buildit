import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WeeklyExamHub from './WeeklyExamHub'
import { PAPER_ACCEPT } from '../lib/examArtifactFile'

const context = { school:'압구정중', grade:'2학년', year:2026, semester:'2학기', exam:'기말고사' }
const props = { context, pastExamCount:0, schoolPrintCount:0, wrongCount:0, aiWorkLogCount:0,
  hasDigitalTwin:false, onOpenContext:vi.fn(), onOpenWeeklyWorkspace:vi.fn(), onOpenWrongReview:vi.fn() }
const pdf = new Uint8Array([37,80,68,70,45,49,46,55,10,255,0,128,10])
const expected = 'data:application/pdf;base64,' + btoa(String.fromCharCode(...pdf))
let writes: {url:string;body:Record<string,unknown>}[]
let rows: unknown[]

beforeEach(()=>{
  writes=[];rows=[]
  vi.spyOn(Storage.prototype,'getItem').mockReturnValue(null)
  vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
    const url=String(input)
    if(init?.method==='POST'||init?.method==='PUT'){
      writes.push({url,body:JSON.parse(String(init.body))})
      return new Response('{"id":1}',{status:init.method==='POST'?201:200})
    }
    return new Response(JSON.stringify(url.includes('workspace/me')?{user_id:'teacher'}:rows),{status:200})
  }))
})
afterEach(()=>{cleanup();vi.unstubAllGlobals();vi.restoreAllMocks()})

it('given PDF papers when registered then send binary bytes and original filenames',async()=>{
  render(<WeeklyExamHub {...props}/> )
  fireEvent.click(screen.getByRole('button',{name:'시험 자료 등록'}))
  const paper=screen.getByLabelText('학생용 문제지 (HTML·PDF)')
  const answer=screen.getByLabelText('정답·해설 (HTML·PDF)')
  expect(paper).toHaveAttribute('accept',PAPER_ACCEPT)
  expect(answer).toHaveAttribute('accept',PAPER_ACCEPT)
  // PDF MIME can be absent on the user's device.
  fireEvent.change(paper,{target:{files:[new File([pdf],'문제지.PDF')]}})
  fireEvent.change(answer,{target:{files:[new File([pdf],'정답해설.pdf',{type:'application/pdf'})]}})
  fireEvent.change(screen.getByLabelText('제작·검수 MD (선택)'),{target:{files:[new File(['# 검수'],'검수.md')]}})
  const save=screen.getByRole('button',{name:'DRAFT 등록',exact:true})
  await waitFor(()=>expect(save).toBeEnabled())
  fireEvent.click(save)
  await screen.findByText('시험 자료를 DRAFT로 등록했습니다. FINAL 확정 전까지 제작·수정 중입니다.')
  expect(writes).toHaveLength(1)
  expect(writes[0].body).toMatchObject({studentPaper:expected,answerExplanation:expected,
    studentPaperName:'문제지.PDF',answerExplanationName:'정답해설.pdf',productionVerification:'# 검수'})
})

it('given an existing paper when replaced with PDF then keep the binary envelope',async()=>{
  rows=[{id:7,...context,round:1,set_type:'STD',question_count:40,status:'DRAFT',version:1,
    created_at:'2026-11-11T09:00:00',student_paper_name:'old.html'}]
  const {container}=render(<WeeklyExamHub {...props}/> )
  const table=await screen.findByRole('table',{name:'1회 자료'})
  const row=within(table).getByText('문제지').closest('[role=row]') as HTMLElement
  fireEvent.click(within(row).getByRole('button',{name:'교체'}))
  const input=container.querySelector('input[aria-hidden="true"]') as HTMLInputElement
  expect(input.accept).toBe(PAPER_ACCEPT)
  fireEvent.change(input,{target:{files:[new File([pdf],'new.pdf',{type:'application/pdf'})]}})
  await screen.findByText('문제지를 교체했습니다.')
  expect(writes[0].body).toEqual({studentPaper:expected,studentPaperName:'new.pdf'})
})

it('given unsupported paper when chosen then clear the old selection and block registration',async()=>{
  render(<WeeklyExamHub {...props}/> )
  fireEvent.click(screen.getByRole('button',{name:'시험 자료 등록'}))
  const chooser=screen.getByLabelText('학생용 문제지 (HTML·PDF)')
  fireEvent.change(chooser,{target:{files:[new File(['<h1>문제</h1>'],'old.html')]}})
  await screen.findByText('선택한 문제지: old.html')
  fireEvent.change(chooser,{target:{files:[new File(['zip'],'bad.zip')]}})
  await screen.findByText('문제지·해설지는 HTML 또는 PDF 파일을 선택해 주세요.')
  expect(screen.queryByText('선택한 문제지: old.html')).not.toBeInTheDocument()
  expect(screen.getByRole('button',{name:'DRAFT 등록',exact:true})).toBeDisabled()
})

it.each([false,true])('given optional MD (cancel selection: %s) when absent then register only papers',async(cancelMd)=>{
  render(<WeeklyExamHub {...props}/> )
  fireEvent.click(screen.getByRole('button',{name:'시험 자료 등록'}))
  fireEvent.change(screen.getByLabelText('학생용 문제지 (HTML·PDF)'),{target:{files:[new File([pdf],'paper.pdf')]}})
  fireEvent.change(screen.getByLabelText('정답·해설 (HTML·PDF)'),{target:{files:[new File([pdf],'answer.pdf')]}})
  if(cancelMd){
    fireEvent.change(screen.getByLabelText('제작·검수 MD (선택)'),{target:{files:[new File(['# 검수'],'검수.md')]}})
    await screen.findByText(/선택한 제작·검수: 검수.md/)
    fireEvent.click(screen.getByRole('button',{name:'선택 취소'}))
  }
  const save=screen.getByRole('button',{name:'DRAFT 등록',exact:true})
  await waitFor(()=>expect(save).toBeEnabled())
  fireEvent.click(save)
  await screen.findByText('시험 자료를 DRAFT로 등록했습니다. FINAL 확정 전까지 제작·수정 중입니다.')
  expect(writes).toHaveLength(1)
  expect(writes[0].body).toMatchObject({studentPaper:expected,answerExplanation:expected})
  expect(writes[0].body).not.toHaveProperty('productionVerification')
  expect(writes[0].body).not.toHaveProperty('productionVerificationName')
})
