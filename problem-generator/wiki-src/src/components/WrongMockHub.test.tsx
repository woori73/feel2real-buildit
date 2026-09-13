import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import WrongMockHub from './WrongMockHub'

const context = { school: 'BuildIt', grade: '고3', year: 2026, semester: '1학기', exam: '전체' }
const props = { context, onNavigate: () => undefined }

const collectingItem = (id:number) => ({
  id, school: 'BuildIt', grade: '고3', year: 2026, semester: '1학기', exam: '전체',
  round: 24, title: '오답 24회', question_count: 20, status: 'COLLECTING', status_label: '사진 등록 중',
  photo_count: 3, has_paper: false, has_explanation: false, paper_name: '', expl_name: '',
  edit_locked: false, created_by: 't', created_at: '2026-09-01T00:00:00', updated_at: '2026-09-01T00:00:00',
})

const completedItem = (id:number) => ({
  ...collectingItem(id), status: 'COMPLETED', status_label: '제작완료',
  has_paper: true, has_explanation: true, edit_locked: true,
})

const detailItem = (base:ReturnType<typeof collectingItem>) => ({
  ...base, photos: [{id: 7, round_id: base.id, title: '2021년 고3 3월 서울특별시교육청 학력평가 31번 빈칸',
    original_name: 'q31.jpg', original_type: 'image/jpeg', size: 12, content_hash: 'h',
    sort_order: 0, uploaded_by: 't', created_at: '2026-09-01T00:00:00',
    original_api_path: '/api/wrong-mock/photos/7/original'}],
})

const memoryStorage = () => { const v = new Map<string, string>(); return { getItem: (k: string) => v.get(k) ?? null, setItem: (k: string, x: string) => { v.set(k, String(x)) }, removeItem: (k: string) => { v.delete(k) }, clear: () => v.clear(), key: (i: number) => [...v.keys()][i] ?? null, get length() { return v.size } } }

const mockApi = (rounds:unknown[], calls:{url:string; init?:RequestInit}[], detail?:unknown) => {
  vi.stubGlobal('fetch', vi.fn(async (url:RequestInfo|URL, init?:RequestInit) => {
    const u = String(url)
    calls.push({url:u, init})
    if (u.includes('wrong-mock/rounds?')) return new Response(JSON.stringify(rounds), {status:200, headers:{'Content-Type':'application/json'}})
    if (/wrong-mock\/rounds\/\d+$/.test(u) && (!init?.method || init.method === 'GET')) return new Response(JSON.stringify(detail ?? rounds[0]), {status:200, headers:{'Content-Type':'application/json'}})
    if (u.includes('wrong-mock/rounds') && init?.method === 'POST') return new Response(JSON.stringify(detail ?? rounds[0]), {status:200, headers:{'Content-Type':'application/json'}})
    return new Response('{}', {status:200, headers:{'Content-Type':'application/json'}})
  }))
}

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage()); vi.stubGlobal('sessionStorage', memoryStorage())
  window.HTMLElement.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('WrongMockHub 회차 카드', () => {
  it('수집 중 회차에 사진 추가 진입을 노출한다', async () => {
    const calls:{url:string; init?:RequestInit}[] = []
    mockApi([collectingItem(1)], calls)
    render(<WrongMockHub {...props} />)
    await screen.findByText('오답 24회')
    expect(screen.getByText('사진 등록 중')).toBeInTheDocument()
    expect(screen.getByRole('button', {name:'사진 추가 · 등록 자료 보기'})).toBeInTheDocument()
    expect(screen.getByRole('button', {name:'편집 ON'})).toBeDisabled()
  })

  it('제작완료 회차에 문제지·해설 보기를 노출한다', async () => {
    const calls:{url:string; init?:RequestInit}[] = []
    mockApi([completedItem(2)], calls)
    render(<WrongMockHub {...props} />)
    await screen.findByText('오답 24회')
    expect(screen.getByText('제작완료')).toBeInTheDocument()
    expect(screen.getByRole('button', {name:'문제지 보기'})).toBeInTheDocument()
    expect(screen.getByRole('button', {name:'간략해설 보기'})).toBeInTheDocument()
  })

  it('회차가 없으면 새 회차 안내를 노출한다', async () => {
    const calls:{url:string; init?:RequestInit}[] = []
    mockApi([], calls)
    render(<WrongMockHub {...props} />)
    await screen.findByText('아직 오답 모의고사 회차가 없습니다.')
    expect(screen.getByRole('button', {name:'+ 새 회차'})).toBeInTheDocument()
  })
})

describe('WrongMockHub 회차 상세', () => {
  it('사진 제목 metadata와 사진 등록 완료 버튼을 노출한다', async () => {
    const calls:{url:string; init?:RequestInit}[] = []
    const base = collectingItem(3)
    mockApi([base], calls, detailItem(base))
    render(<WrongMockHub {...props} />)
    fireEvent.click(await screen.findByRole('button', {name:'사진 추가 · 등록 자료 보기'}))
    await screen.findByText('2021년 고3 3월 서울특별시교육청 학력평가 31번 빈칸')
    expect(screen.getByRole('button', {name:'사진 등록 완료 → 제작대기'})).toBeInTheDocument()
    expect(screen.getByRole('button', {name:'제목 수정'})).toBeInTheDocument()
  })

  it('새 회차 만들기는 다음 번호를 제안한다', async () => {
    const calls:{url:string; init?:RequestInit}[] = []
    mockApi([collectingItem(4)], calls)
    render(<WrongMockHub {...props} />)
    await screen.findByText('오답 24회')
    fireEvent.click(screen.getByRole('button', {name:'+ 새 회차'}))
    expect(screen.getByPlaceholderText('25')).toBeInTheDocument()
    const createButton = screen.getByRole('button', {name:'회차 만들기'})
    fireEvent.click(createButton)
    await waitFor(() => expect(calls.some(c => c.url.includes('wrong-mock/rounds') && c.init?.method === 'POST')).toBe(true))
    const post = calls.find(c => c.url.includes('wrong-mock/rounds') && c.init?.method === 'POST')!
    expect(JSON.parse(String(post.init!.body)).round).toBe(25)
  })
})
