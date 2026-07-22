import { createFileRoute } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export const Route = createFileRoute('/login')({
  component: LoginPage,
})

function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    // TODO: 로그인 API 연동 (Orval useMutation)
    console.log('login', { email, password })
  }

  return (
    <div className="mx-auto flex min-h-svh w-full max-w-[420px] flex-col px-6">
      <div className="flex flex-1 flex-col justify-center pb-32">
        {/* 로고 + 슬로건 */}
        <div className="mb-10 text-center">
          <h1 className="text-[2rem] font-bold tracking-tight text-foreground">
            UpBid
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            입찰은 빠르게, 낙찰은 정확하게
          </p>
        </div>

        {/* 로그인 폼 */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="이메일 주소 입력 (아이디로 사용)"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="password">비밀번호</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="비밀번호 입력"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <Button type="submit" className="mt-1 h-12 w-full text-base font-bold">
            로그인
          </Button>

          <div className="flex items-center justify-between text-sm">
            <button
              type="button"
              className="text-muted-foreground transition-colors hover:text-foreground"
            >
              비밀번호를 잊으셨나요?
            </button>
            <button
              type="button"
              className="font-bold text-foreground hover:underline"
            >
              회원가입
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
