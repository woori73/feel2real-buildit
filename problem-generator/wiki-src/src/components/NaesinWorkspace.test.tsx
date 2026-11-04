import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import NaesinWorkspace from './NaesinWorkspace'
vi.mock('./NaesinWikiV1', () => ({ default: () => <input aria-label="자료 제목" /> }))
vi.mock('./GrammarBank', () => ({ default: () => <input aria-label="문법 검색" /> }))
afterEach(() => { cleanup(); window.history.replaceState({}, '', '/') })
describe('내신위키 통합 작업실', () => {
  it('given 작성 중인 자료 when 다른 기능을 사용하고 돌아오면 then 입력과 문법 검색을 보존한다', () => {
    render(<NaesinWorkspace onNavigate={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('자료 제목'), { target: { value: '기말 자료' } })
    fireEvent.click(screen.getByRole('button', { name: /문법문제은행/ }))
    fireEvent.change(screen.getByLabelText('문법 검색'), { target: { value: '관계대명사' } })
    fireEvent.click(screen.getByRole('button', { name: /내신제작소/ }))
    const frame = screen.getByTitle('내신제작소 작업 화면')
    expect(frame).toHaveAttribute('src', '/naesin-production-studio?embedded=1')
    fireEvent.click(screen.getByRole('button', { name: /문제·자료 업로드/ }))
    expect(screen.getByLabelText('자료 제목')).toHaveValue('기말 자료')
    fireEvent.click(screen.getByRole('button', { name: /문법문제은행/ }))
    expect(screen.getByLabelText('문법 검색')).toHaveValue('관계대명사')
    expect(screen.getByTitle('내신제작소 작업 화면')).toBe(frame)
  })
  it('given 제작소 진입 링크 when 페이지를 열면 then 제작소를 바로 표시한다', () => {
    window.history.replaceState({}, '', '/?section=production')
    render(<NaesinWorkspace onNavigate={vi.fn()} />)
    expect(screen.getByRole('button', { name: /내신제작소/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByLabelText('자료 제목')).not.toBeInTheDocument()
  })
})
