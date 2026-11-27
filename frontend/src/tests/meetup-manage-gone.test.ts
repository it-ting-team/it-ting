/**
 * 지운 모임의 신청 관리(예: 삭제된 모임의 "새 참여 신청" 알림 → /meetups/me?manage=<id>)가
 * "다시 시도" 오류 대신 "삭제된 모임이에요" 안내 화면으로 가는지 (09 알림 – 팝업 6, 00 공통 21)
 */
import { describe, expect, it } from 'vitest'
import { ApiError, isNotFound } from '../api'

describe('지운 모임의 신청 관리', () => {
  it('404 MEETUP_NOT_FOUND 는 "없는 대상"으로 봄', () => {
    expect(isNotFound(new ApiError(404, 'MEETUP_NOT_FOUND', '없는 모임입니다.'))).toBe(true)
    expect(isNotFound(new ApiError(500, 'INTERNAL_ERROR', '서버 오류'))).toBe(false)
  })

  it('신청 관리 팝업이 없는 모임이면 /gone/meetup 으로 바꿔 감 (뒤로 가면 알림으로)', async () => {
    const FS = 'node:fs'
    const fs = (await import(/* @vite-ignore */ FS)) as { readFileSync(p: string, e: string): string }
    const src = fs.readFileSync('src/screens/meetups/parts.tsx', 'utf8')
    expect(src).toMatch(/const gone = !!id && q\.isError && isNotFound\(q\.error\)/)
    expect(src).toMatch(/if \(gone\) nav\('\/gone\/meetup', \{ replace: true \}\)/)
    expect(src).toMatch(/if \(!id \|\| gone\) return null/)
  })
})
