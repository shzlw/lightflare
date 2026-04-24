import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { login, type AuthUser } from '@/lib/api'

type LoginPageProps = {
  currentUser: AuthUser | null
  onLogin: (user: AuthUser) => void
}

export default function LoginPage({ currentUser, onLogin }: LoginPageProps) {
  const location = useLocation()
  const [loginValue, setLoginValue] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (currentUser) {
    return <Navigate to={currentUser.mustChangePassword ? '/workspace/account' : '/workspace/projects'} replace />
  }

  const nextPath = (location.state as { from?: string } | null)?.from || '/workspace/projects'
  const targetPath = nextPath === '/login' ? '/workspace/projects' : nextPath

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)

    try {
      const user = await login(loginValue.trim(), password)
      onLogin(user)
      window.location.replace(user.mustChangePassword ? '/workspace/account' : targetPath)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Login failed.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-shell">
      <section className="login-card animate-in fade-in zoom-in duration-500">
        <div className="login-card__header text-center">
          <h1 className="text-4xl font-extrabold tracking-tight text-foreground">Lightflare</h1>
        </div>

        <form className="login-form mt-8" onSubmit={handleSubmit}>
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                Identity
              </label>
              <Input
                className="h-12 rounded-none border-black bg-background px-4 transition-all"
                value={loginValue}
                onChange={(event) => setLoginValue(event.target.value)}
                placeholder="Username or email"
                autoComplete="username"
                required
              />
            </div>

            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                Password
              </label>
              <Input
                className="h-12 rounded-none border-black bg-background px-4 transition-all"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Password"
                autoComplete="current-password"
                required
              />
            </div>
          </div>

          {error ? (
            <div className="mt-4 border border-destructive p-3 text-xs font-medium text-destructive animate-in fade-in slide-in-from-top-1">
              {error}
            </div>
          ) : null}

          <Button 
            type="submit" 
            disabled={isSubmitting} 
            className="mt-8 h-12 w-full gap-2 rounded-none border border-black text-sm font-bold"
          >
            {isSubmitting ? (
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground/20 border-t-primary-foreground" />
            ) : (
              'Sign in'
            )}
          </Button>
        </form>
      </section>
    </main>
  )
}
