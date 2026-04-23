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
                className="h-12 rounded-xl bg-background border-border/60 focus-visible:ring-primary/20 transition-all px-4"
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
                className="h-12 rounded-xl bg-background border-border/60 focus-visible:ring-primary/20 transition-all px-4"
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
            <div className="mt-4 p-3 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive text-xs font-medium animate-in fade-in slide-in-from-top-1">
              {error}
            </div>
          ) : null}

          <Button 
            type="submit" 
            disabled={isSubmitting} 
            className="h-12 w-full mt-8 rounded-xl gap-2 font-bold shadow-xl shadow-primary/10 transition-all hover:shadow-2xl hover:shadow-primary/20 hover:-translate-y-0.5 active:translate-y-0 text-sm"
          >
            {isSubmitting ? (
              <div className="w-4 h-4 border-2 border-primary-foreground/20 border-t-primary-foreground rounded-full animate-spin" />
            ) : (
              'Sign in'
            )}
          </Button>
        </form>
      </section>
    </main>
  )
}
