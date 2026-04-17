import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { fetchCurrentUser, logout, type AuthUser, updatePassword } from '@/lib/api'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'
import { Skeleton } from '@/components/ui/skeleton'
import { User, RefreshCw, LogOut, ShieldAlert, KeyRound, Mail, Fingerprint, Shield, ShieldCheck, AlertCircle } from 'lucide-react'
function formatValue(value: string | null | undefined) {
  return value && value.trim() ? value : 'Not set'
}

type UserInfoPageProps = {
  currentUser: AuthUser | null
  onUserChange: (user: AuthUser | null) => void
}

export default function UserInfoPage({ currentUser, onUserChange }: UserInfoPageProps) {
  const [user, setUser] = useState<AuthUser | null>(currentUser)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isUpdatingPassword, setIsUpdatingPassword] = useState(false)

  useEffect(() => {
    void loadUser()
  }, [onUserChange])

  async function loadUser() {
    setIsLoading(true)

    try {
      const nextUser = await fetchCurrentUser()
      setUser(nextUser)
      onUserChange(nextUser)
    } catch (loadError) {
      setUser(null)
      onUserChange(null)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load user information.')
    } finally {
      setIsLoading(false)
    }
  }

  async function handleLogout() {
    setIsLoggingOut(true)
    try {
      await logout()
      onUserChange(null)
      window.location.href = '/login'
    } finally {
      setIsLoggingOut(false)
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!newPassword.trim()) {
      toast.error('New password is required.')
      return
    }
    if (newPassword !== confirmPassword) {
      toast.error('Passwords do not match.')
      return
    }

    setIsUpdatingPassword(true)
    try {
      const nextUser = await updatePassword(newPassword)
      setUser(nextUser)
      onUserChange(nextUser)
      setNewPassword('')
      setConfirmPassword('')
      toast.success('Password updated.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to update password.')
    } finally {
      setIsUpdatingPassword(false)
    }
  }

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">User Profile</h2>
        </div>
      </header>

      <section className="w-full flex flex-col gap-6">
        <div className="bg-card text-card-foreground shadow-sm rounded-xl border border-border/40 p-6 sm:p-8 space-y-8 flex-1">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-border/40 pb-5">
            <div className="flex items-center gap-4">
              <div className="flex flex-col">
                <p className="text-xs font-semibold tracking-wider uppercase text-muted-foreground mb-1">Account Info</p>
                <h3 className="text-2xl font-bold tracking-tight">{user?.displayName || user?.username || 'Current User'}</h3>
              </div>
            </div>

            <div className="flex gap-2">
              <Button variant="outline" onClick={() => void loadUser()} disabled={isLoading} className="shadow-sm gap-2">
                <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
                {isLoading ? 'Refreshing...' : 'Refresh'}
              </Button>
              <Button variant="outline" onClick={() => void handleLogout()} disabled={isLoggingOut} className="shadow-sm gap-2 text-destructive hover:text-destructive hover:bg-destructive/5">
                <LogOut className="h-4 w-4" />
                {isLoggingOut ? 'Signing out...' : 'Sign out'}
              </Button>
            </div>
          </div>

          {isLoading ? (
            <div className="space-y-6">
              <Skeleton className="h-20 w-full rounded-xl" />
              <Skeleton className="h-40 w-full rounded-xl" />
            </div>
          ) : null}

          {!isLoading && !user ? (
             <div className="py-12 flex flex-col items-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
               <p className="text-muted-foreground text-sm">No authenticated user is available right now.</p>
               <p className="text-muted-foreground text-sm mt-1">This page shows the response from `/api/v1/auth/me` for the current session.</p>
             </div>
          ) : null}

          {user ? (
            <div className="space-y-8 animate-in fade-in duration-300">
              <section className={`rounded-xl border shadow-sm overflow-hidden flex flex-col ${user.mustChangePassword ? 'border-destructive/20 bg-destructive/5' : 'border-border/40 bg-card'}`}>
                <div className={`px-5 py-4 flex items-center justify-between border-b ${user.mustChangePassword ? 'bg-destructive/10 text-destructive border-destructive/10' : 'bg-muted/30 border-border/40'}`}>
                  <span className="text-sm font-bold flex items-center gap-2">
                    {user.mustChangePassword ? (
                      <>
                        <ShieldAlert className="h-4 w-4" /> Action Required: Update Password
                      </>
                    ) : (
                      <>
                        <KeyRound className="h-4 w-4" /> Change Password
                      </>
                    )}
                  </span>
                </div>
                <div className="p-5">
                  <p className="text-sm text-foreground mb-4">
                    {user.mustChangePassword
                      ? 'This account was created from the bootstrap superadmin login. Set a new password before using the rest of the workspace.'
                      : 'Update the password for your signed-in account.'}
                  </p>
                  <form className="space-y-4" onSubmit={handlePasswordSubmit}>
                    <div className="space-y-2">
                      <label className="text-sm font-semibold flex items-center gap-2">New Password</label>
                      <Input
                        className="h-10 rounded-md bg-background w-full sm:max-w-md"
                        type="password"
                        value={newPassword}
                        onChange={(event) => setNewPassword(event.target.value)}
                        autoComplete="new-password"
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <label className="text-sm font-semibold flex items-center gap-2">Confirm Password</label>
                      <Input
                        className="h-10 rounded-md bg-background w-full sm:max-w-md"
                        type="password"
                        value={confirmPassword}
                        onChange={(event) => setConfirmPassword(event.target.value)}
                        autoComplete="new-password"
                        required
                      />
                    </div>
                    <div className="pt-2">
                      <Button type="submit" disabled={isUpdatingPassword} className="shadow-sm min-w-32 gap-2">
                        <KeyRound className="h-4 w-4" />
                        {isUpdatingPassword ? 'Updating...' : 'Update password'}
                      </Button>
                    </div>
                  </form>
                </div>
              </section>

              <section className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><User className="h-3 w-3" /> Display Name</span>
                  <p className="text-sm font-medium">{formatValue(user.displayName)}</p>
                </article>
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><Fingerprint className="h-3 w-3" /> Username</span>
                  <p className="text-sm font-medium">{formatValue(user.username)}</p>
                </article>
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><Mail className="h-3 w-3" /> Email</span>
                  <p className="text-sm font-medium break-all">{formatValue(user.email)}</p>
                </article>
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><ShieldCheck className="h-3 w-3" /> Status</span>
                  <p className="text-sm font-medium">{formatValue(user.status)}</p>
                </article>
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><Shield className="h-3 w-3" /> Role</span>
                  <p className="text-sm font-medium">{formatValue(user.role)}</p>
                </article>
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><KeyRound className="h-3 w-3" /> Must Change Password</span>
                  <p className="text-sm font-medium">{user.mustChangePassword ? 'Yes' : 'No'}</p>
                </article>
                <article className="p-4 rounded-xl bg-muted/30 border shadow-sm transition-shadow sm:col-span-2">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><AlertCircle className="h-3 w-3" /> User ID</span>
                  <p className="text-sm font-mono break-all font-semibold text-primary">{user.id}</p>
                </article>
              </section>
            </div>
          ) : null}
        </div>
      </section>

      <Toaster />
    </div>
  )
}
