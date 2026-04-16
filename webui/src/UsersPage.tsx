import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Search, Plus, Trash2, Edit3, Save, X, Lock, Shield, ChevronLeft, ChevronRight, User as UserIcon } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { request, type AuthUser } from '@/lib/api'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Toaster } from '@/components/ui/sonner'
import { toast } from 'sonner'
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemHeader,
  ItemTitle,
} from '@/components/ui/item'

type User = {
  id: string
  username: string
  email: string | null
  displayName: string | null
  status: string | null
  role: string | null
  mustChangePassword: boolean
  createdAt: string
  updatedAt: string
}

type UserPageResponse = {
  items: User[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

type AppUserIdentity = {
  id: string
  appUserId: string
  provider: string
  externalUserId: string
  createdAt: string
  updatedAt: string
}

type UserFormState = {
  username: string
  email: string
  displayName: string
  status: string
  role: string
  password: string
}

type IdentityFormState = {
  provider: string
  externalUserId: string
}

const USER_STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'] as const
const USER_ROLE_OPTIONS = ['USER', 'ADMIN'] as const

const pageSize = 20

const emptyForm: UserFormState = {
  username: '',
  email: '',
  displayName: '',
  status: 'ACTIVE',
  role: 'USER',
  password: '',
}

const emptyIdentityForm: IdentityFormState = {
  provider: '',
  externalUserId: '',
}

function toFormState(user: User): UserFormState {
  return {
    username: user.username ?? '',
    email: user.email ?? '',
    displayName: user.displayName ?? '',
    status: user.status ?? 'ACTIVE',
    role: user.role ?? 'USER',
    password: '',
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatValue(value: string | null) {
  return value && value.trim() ? value : 'Not set'
}

export default function UsersPage({ currentUser }: { currentUser: AuthUser | null }) {
  const isSuperAdmin = currentUser?.role === 'SUPERADMIN'
  const [users, setUsers] = useState<User[]>([])
  const [identities, setIdentities] = useState<AppUserIdentity[]>([])
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [form, setForm] = useState<UserFormState>(emptyForm)
  const [identityForm, setIdentityForm] = useState<IdentityFormState>(emptyIdentityForm)
  const [sheetMode, setSheetMode] = useState<'create' | 'edit' | 'detail'>('create')
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isListLoading, setIsListLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [isIdentitiesLoading, setIsIdentitiesLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isIdentitySubmitting, setIsIdentitySubmitting] = useState(false)
  const [identityIdBeingDeleted, setIdentityIdBeingDeleted] = useState<string | null>(null)
  const [editingIdentityId, setEditingIdentityId] = useState<string | null>(null)
  const [passwordResetValue, setPasswordResetValue] = useState('')
  const [forcePasswordChange, setForcePasswordChange] = useState(true)
  const [isResettingPassword, setIsResettingPassword] = useState(false)
  const [query, setQuery] = useState('')

  useEffect(() => {
    void loadPage(page)
  }, [page])

  async function loadPage(nextPage: number) {
    setIsListLoading(true)

    try {
      const data = await request<UserPageResponse>(
        `/internal-api/v1/users?page=${nextPage}&size=${pageSize}`,
        { method: 'GET' },
      )

      setUsers(data.items)
      setTotalItems(data.totalItems)
      setTotalPages(data.totalPages)

      if (data.items.length === 0) {
        clearSelection()
        return
      }

      const selectedStillExists = data.items.some((user) => user.id === selectedUserId)
      if (!selectedUserId || !selectedStillExists) {
        await selectUser(data.items[0].id)
      }
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load users.')
      setUsers([])
      setTotalItems(0)
      setTotalPages(0)
      clearSelection()
    } finally {
      setIsListLoading(false)
    }
  }

  async function selectUser(userId: string) {
    setSelectedUserId(userId)
    setIsDetailLoading(true)

    try {
      const [user, userIdentities] = await Promise.all([
        request<User>(`/internal-api/v1/users/${userId}`, { method: 'GET' }),
        loadIdentities(userId),
      ])
      setSelectedUser(user)
      setIdentities(userIdentities)
      setForcePasswordChange(user.mustChangePassword)
      if (sheetMode === 'edit' && isSheetOpen) {
        setForm(toFormState(user))
      } else {
        setSheetMode('detail')
        setIsSheetOpen(true)
      }
    } catch (loadError) {
      setSelectedUser(null)
      setIdentities([])
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load user details.')
    } finally {
      setIsDetailLoading(false)
    }
  }

  async function loadIdentities(userId: string) {
    setIsIdentitiesLoading(true)
    try {
      return await request<AppUserIdentity[]>(`/internal-api/v1/users/${userId}/identities`, { method: 'GET' })
    } finally {
      setIsIdentitiesLoading(false)
    }
  }

  function clearSelection() {
    setSelectedUserId(null)
    setSelectedUser(null)
    setIdentities([])
    setForm(emptyForm)
    setIdentityForm(emptyIdentityForm)
    setSheetMode('create')
    setIsSheetOpen(false)
    setIsIdentitiesLoading(false)
    setPasswordResetValue('')
    setForcePasswordChange(true)
    setIsIdentitySubmitting(false)
    setIdentityIdBeingDeleted(null)
    setEditingIdentityId(null)
  }

  function openCreateSheet() {
    setSheetMode('create')
    setForm(emptyForm)
    setIsSheetOpen(true)
  }

  function openEditSheet() {
    if (!selectedUser) {
      return
    }

    setSheetMode('edit')
    setForm(toFormState(selectedUser))
    setIsSheetOpen(true)
  }

  function updateField<Key extends keyof UserFormState>(key: Key, value: UserFormState[Key]) {
    setForm((current) => ({
      ...current,
      [key]: value,
    }))
  }

  function updateIdentityField<Key extends keyof IdentityFormState>(key: Key, value: IdentityFormState[Key]) {
    setIdentityForm((current) => ({
      ...current,
      [key]: value,
    }))
  }

  function openCreateIdentityForm() {
    setEditingIdentityId(null)
    setIdentityForm(emptyIdentityForm)
  }

  function openEditIdentityForm(identity: AppUserIdentity) {
    setEditingIdentityId(identity.id)
    setIdentityForm({
      provider: identity.provider,
      externalUserId: identity.externalUserId,
    })
  }

  function cancelIdentityEdit() {
    setEditingIdentityId(null)
    setIdentityForm(emptyIdentityForm)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)

    const payload = {
      username: form.username.trim(),
      email: form.email.trim() || null,
      displayName: form.displayName.trim() || null,
      status: form.status.trim() || 'ACTIVE',
    }

    try {
      const user =
        sheetMode === 'create'
          ? await request<User>('/internal-api/v1/users', {
              method: 'POST',
              body: JSON.stringify({
                ...payload,
                role: form.role.trim() || 'USER',
                password: form.password,
              }),
            })
          : await request<User>(`/internal-api/v1/users/${selectedUserId}`, {
              method: 'PUT',
              body: JSON.stringify(payload),
            })

      await loadPage(page)
      await selectUser(user.id)
      setIsSheetOpen(false)
      toast.success(sheetMode === 'create' ? 'User created.' : 'User updated.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to save user.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleDelete() {
    if (!selectedUserId) {
      return
    }

    setIsDeleting(true)

    try {
      await request<void>(`/internal-api/v1/users/${selectedUserId}`, {
        method: 'DELETE',
      })

      const shouldMoveToPreviousPage = page > 0 && users.length === 1
      if (shouldMoveToPreviousPage) {
        setPage((current) => current - 1)
      } else {
        await loadPage(page)
      }
      toast.success('User deleted.')
    } catch (deleteError) {
      toast.error(deleteError instanceof Error ? deleteError.message : 'Failed to delete user.')
    } finally {
      setIsDeleting(false)
    }
  }

  async function handleIdentitySubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedUserId) {
      return
    }

    setIsIdentitySubmitting(true)

    try {
      const payload = {
        provider: identityForm.provider.trim(),
        externalUserId: identityForm.externalUserId.trim(),
      }

      const savedIdentity = editingIdentityId
        ? await request<AppUserIdentity>(`/internal-api/v1/users/${selectedUserId}/identities/${editingIdentityId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
          })
        : await request<AppUserIdentity>(`/internal-api/v1/users/${selectedUserId}/identities`, {
            method: 'POST',
            body: JSON.stringify(payload),
          })

      setIdentities((current) => {
        if (editingIdentityId) {
          return current
            .map((identity) => (identity.id === savedIdentity.id ? savedIdentity : identity))
            .sort((left, right) => left.provider.localeCompare(right.provider) || left.externalUserId.localeCompare(right.externalUserId))
        }
        return [...current, savedIdentity]
          .sort((left, right) => left.provider.localeCompare(right.provider) || left.externalUserId.localeCompare(right.externalUserId))
      })
      cancelIdentityEdit()
      toast.success(editingIdentityId ? 'Identity updated.' : 'Identity created.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to save identity.')
    } finally {
      setIsIdentitySubmitting(false)
    }
  }

  async function handleDeleteIdentity(identityId: string) {
    if (!selectedUserId) {
      return
    }

    setIdentityIdBeingDeleted(identityId)

    try {
      await request<void>(`/internal-api/v1/users/${selectedUserId}/identities/${identityId}`, {
        method: 'DELETE',
      })
      setIdentities((current) => current.filter((identity) => identity.id !== identityId))
      if (editingIdentityId === identityId) {
        cancelIdentityEdit()
      }
      toast.success('Identity deleted.')
    } catch (deleteError) {
      toast.error(deleteError instanceof Error ? deleteError.message : 'Failed to delete identity.')
    } finally {
      setIdentityIdBeingDeleted(null)
    }
  }

  async function handlePasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedUserId) {
      return
    }

    setIsResettingPassword(true)

    try {
      const updatedUser = await request<User>(`/internal-api/v1/users/${selectedUserId}/password`, {
        method: 'POST',
        body: JSON.stringify({
          newPassword: passwordResetValue,
          mustChangePassword: forcePasswordChange,
        }),
      })
      setSelectedUser(updatedUser)
      setPasswordResetValue('')
      setForcePasswordChange(updatedUser.mustChangePassword)
      await loadPage(page)
      toast.success('Password reset successfully.')
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to reset password.')
    } finally {
      setIsResettingPassword(false)
    }
  }

  const normalizedQuery = query.trim().toLowerCase()
  const filteredUsers = users.filter((user) => {
    if (!normalizedQuery) {
      return true
    }

    return [user.username, user.displayName, user.email, user.status, user.role]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLowerCase().includes(normalizedQuery))
  })

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">User Management</h2>
        </div>
      </header>

      <section className="space-y-6">
        <div className="flex flex-col md:flex-row justify-between md:items-center gap-4">
          <div className="flex items-center gap-4">
            <h3 className="text-xl font-semibold">User Directory</h3>
            <span className="bg-primary/10 text-primary text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">{totalItems} total</span>
          </div>

          <div className="flex gap-2 w-full md:w-auto">
            <Button className="w-full md:w-auto shadow-sm hover:shadow-md transition-shadow gap-2" onClick={openCreateSheet}>
              <Plus className="h-4 w-4" /> New user
            </Button>
          </div>
        </div>

        <div className="flex flex-col md:flex-row gap-4 items-center justify-between bg-muted/30 p-2 rounded-lg">
          <div className="w-full relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              className="h-10 pl-10 rounded-md w-full bg-background border-border/60 shadow-sm focus-visible:ring-primary/30"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search user directory..."
            />
          </div>
        </div>

        {isListLoading ? <p>Loading users...</p> : null}
        {!isListLoading && users.length === 0 ? <p>No managed users found.</p> : null}
        {!isListLoading && users.length > 0 && filteredUsers.length === 0 ? (
          <p>No users match the current search.</p>
        ) : null}

        <ItemGroup aria-label="User list">
            {filteredUsers.map((user) => (
              <Item
                key={user.id}
                variant="outline"
                className="cursor-pointer"
                role="button"
                tabIndex={0}
                onClick={() => void selectUser(user.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    void selectUser(user.id)
                  }
                }}
              >
                <ItemContent>
                  <ItemHeader>
                    <ItemTitle>{user.displayName?.trim() || user.username}</ItemTitle>
                    <div className="flex gap-2 shrink-0">
                      <Badge variant="secondary">{user.status}</Badge>
                      <Badge variant="outline">{user.role}</Badge>
                      <Badge variant="outline">{user.mustChangePassword ? 'Reset required' : 'Ready'}</Badge>
                    </div>
                  </ItemHeader>
                  <ItemDescription>{formatValue(user.email)}</ItemDescription>
                  <ItemDescription className="mt-2 text-[10px] flex items-center gap-1.5 uppercase font-bold tracking-tight text-muted-foreground/70">
                    <UserIcon className="h-3 w-3" />
                    @{user.username}
                  </ItemDescription>
                </ItemContent>
              </Item>
            ))}
          </ItemGroup>

        <div className="flex items-center justify-between text-sm pt-4">
          <span className="text-muted-foreground font-medium">
            {totalItems === 0
              ? 'Showing 0 users'
              : `Showing ${page * pageSize + 1} to ${Math.min((page + 1) * pageSize, totalItems)} of ${totalItems}`}
          </span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={page === 0 || isListLoading}
              className="h-8 w-8 p-0 rounded-md shadow-sm"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <div className="bg-muted px-3 py-1 rounded-md text-xs font-mono font-bold text-muted-foreground">
              {totalPages === 0 ? 0 : page + 1} / {Math.max(totalPages, 1)}
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((current) => current + 1)}
              disabled={isListLoading || totalPages === 0 || page >= totalPages - 1}
              className="h-8 w-8 p-0 rounded-md shadow-sm"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </section>

      <Sheet open={isSheetOpen} onOpenChange={setIsSheetOpen}>
        <SheetContent
          side="right"
          className="!w-[90vw] sm:!max-w-xl md:!max-w-2xl lg:!max-w-3xl xl:!max-w-4xl overflow-y-auto border-l border-border/40 shadow-2xl p-0 flex flex-col"
        >
          <div className="p-6 md:p-8 shrink-0 border-b bg-background/95 backdrop-blur sticky top-0 z-10 supports-[backdrop-filter]:bg-background/60">
            <SheetHeader>
              <div className="flex items-center gap-2 text-xs font-semibold tracking-wider uppercase text-muted-foreground mb-1">
                {sheetMode === 'create' ? 'Create' : sheetMode === 'edit' ? 'Update' : 'Details'}
              </div>
              <SheetTitle className="text-2xl font-bold">
                {sheetMode === 'create' 
                  ? 'New user account' 
                  : selectedUser?.displayName || selectedUser?.username || form.displayName || form.username || 'User Profile'
                }
              </SheetTitle>
              <SheetDescription className="text-sm">
                {sheetMode === 'detail'
                  ? 'Inspect the selected user and manage their profile and security settings.'
                  : sheetMode === 'create'
                    ? isSuperAdmin
                      ? 'Create a user or admin account. New accounts are forced to change their password on first sign-in.'
                      : 'Create a normal user account. New users are forced to change their password on first sign-in.'
                    : 'Update the selected user profile and account status.'}
              </SheetDescription>
            </SheetHeader>
          </div>

          <div className="p-6 md:p-8 flex-1">
            {sheetMode === 'detail' && isDetailLoading ? (
              <div className="space-y-6">
                <Skeleton className="h-20 w-full rounded-xl" />
                <Skeleton className="h-40 w-full rounded-xl" />
              </div>
            ) : null}

            {sheetMode === 'detail' ? (
              selectedUser ? (
                <div className="space-y-8 animate-in fade-in duration-300">
                  <section className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Display Name</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedUser.displayName)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Username</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedUser.username)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Email</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedUser.email)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Status</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedUser.status)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Role</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedUser.role)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Password Reset Required</span>
                      <p className="font-mono text-sm break-all text-primary/80">{selectedUser.mustChangePassword ? 'Yes' : 'No'}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Created</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatDate(selectedUser.createdAt)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Updated</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatDate(selectedUser.updatedAt)}</p>
                    </article>
                  </section>

                  <section className="rounded-xl border shadow-sm overflow-hidden flex flex-col bg-card">
                    <div className="bg-muted/40 px-5 py-4 flex items-center justify-between border-b">
                      <span className="text-sm font-bold flex items-center gap-2"><Shield className="h-4 w-4" /> Identity Mappings</span>
                      <Button type="button" variant="outline" size="sm" className="gap-2" onClick={openCreateIdentityForm}>
                        <Plus className="h-4 w-4" /> Add identity
                      </Button>
                    </div>
                    <div className="p-5 space-y-5">
                      {isIdentitiesLoading ? <p className="text-sm text-muted-foreground">Loading identities...</p> : null}

                      {!isIdentitiesLoading && identities.length === 0 ? (
                        <div className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
                          No external identities are linked to this user.
                        </div>
                      ) : null}

                      {identities.length > 0 ? (
                        <div className="space-y-3">
                          {identities.map((identity) => (
                            <article key={identity.id} className="rounded-lg border p-4 bg-background/80">
                              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                <div className="space-y-1">
                                  <div className="flex items-center gap-2">
                                    <Badge variant="secondary">{identity.provider}</Badge>
                                    <span className="font-mono text-sm text-primary/80 break-all">{identity.externalUserId}</span>
                                  </div>
                                  <p className="text-xs text-muted-foreground">
                                    Updated {formatDate(identity.updatedAt)}
                                  </p>
                                </div>
                                <div className="flex gap-2">
                                  <Button type="button" variant="outline" size="sm" className="gap-2" onClick={() => openEditIdentityForm(identity)}>
                                    <Edit3 className="h-4 w-4" /> Edit
                                  </Button>
                                  <AlertDialog>
                                    <AlertDialogTrigger asChild>
                                      <Button
                                        type="button"
                                        variant="destructive"
                                        size="sm"
                                        disabled={identityIdBeingDeleted === identity.id}
                                        className="gap-2"
                                      >
                                        <Trash2 className="h-4 w-4" />
                                        {identityIdBeingDeleted === identity.id ? 'Deleting...' : 'Delete'}
                                      </Button>
                                    </AlertDialogTrigger>
                                    <AlertDialogContent>
                                      <AlertDialogHeader>
                                        <AlertDialogTitle>Delete identity mapping?</AlertDialogTitle>
                                        <AlertDialogDescription>
                                          This removes the external identity link for provider "{identity.provider}".
                                        </AlertDialogDescription>
                                      </AlertDialogHeader>
                                      <AlertDialogFooter>
                                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                                        <AlertDialogAction onClick={() => void handleDeleteIdentity(identity.id)} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
                                          Delete
                                        </AlertDialogAction>
                                      </AlertDialogFooter>
                                    </AlertDialogContent>
                                  </AlertDialog>
                                </div>
                              </div>
                            </article>
                          ))}
                        </div>
                      ) : null}

                      <form className="space-y-4 rounded-lg border bg-muted/20 p-4" onSubmit={handleIdentitySubmit}>
                        <div className="flex items-center justify-between gap-3">
                          <h4 className="text-sm font-semibold">
                            {editingIdentityId ? 'Edit identity' : 'Add identity'}
                          </h4>
                          {editingIdentityId ? (
                            <Button type="button" variant="ghost" size="sm" onClick={cancelIdentityEdit}>
                              Cancel
                            </Button>
                          ) : null}
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                          <div className="space-y-2">
                            <label className="text-sm font-semibold">Provider</label>
                            <Input
                              className="h-10 rounded-md bg-background"
                              value={identityForm.provider}
                              onChange={(event) => updateIdentityField('provider', event.target.value)}
                              placeholder="slack"
                              required
                            />
                          </div>
                          <div className="space-y-2">
                            <label className="text-sm font-semibold">External User ID</label>
                            <Input
                              className="h-10 rounded-md bg-background"
                              value={identityForm.externalUserId}
                              onChange={(event) => updateIdentityField('externalUserId', event.target.value)}
                              placeholder="U0123456789"
                              required
                            />
                          </div>
                        </div>
                        <Button
                          type="submit"
                          disabled={isIdentitySubmitting || !identityForm.provider.trim() || !identityForm.externalUserId.trim()}
                          className="gap-2"
                        >
                          {isIdentitySubmitting ? 'Saving...' : editingIdentityId ? 'Update identity' : 'Create identity'}
                        </Button>
                      </form>
                    </div>
                  </section>

                  <section className="rounded-xl border shadow-sm overflow-hidden flex flex-col bg-card">
                    <div className="bg-muted/40 px-5 py-4 flex items-center justify-between border-b">
                      <span className="text-sm font-bold flex items-center gap-2"><Shield className="h-4 w-4" /> Security Controls</span>
                    </div>
                    <div className="p-5">
                      <form className="space-y-4" onSubmit={handlePasswordReset}>
                        <div className="space-y-2">
                          <label className="text-sm font-semibold flex items-center gap-2">Temporary Password</label>
                          <Input
                            className="h-10 rounded-md bg-background w-full sm:max-w-md"
                            type="password"
                            value={passwordResetValue}
                            onChange={(event) => setPasswordResetValue(event.target.value)}
                            autoComplete="new-password"
                            required
                          />
                        </div>

                        <label className="flex items-center gap-3 cursor-pointer">
                          <input
                            type="checkbox"
                            className="w-4 h-4 rounded border-gray-300 text-primary focus:ring-primary"
                            checked={forcePasswordChange}
                            onChange={(event) => setForcePasswordChange(event.target.checked)}
                          />
                          <span className="text-sm font-medium text-foreground">Require password update on next sign-in</span>
                        </label>

                        <div className="pt-2">
                          <Button type="submit" disabled={isResettingPassword || !passwordResetValue.trim()} className="shadow-sm min-w-32 gap-2">
                            {isResettingPassword ? (
                              'Resetting...'
                            ) : (
                              <>
                                <Lock className="h-4 w-4" /> Reset password
                              </>
                            )}
                          </Button>
                        </div>
                      </form>
                    </div>
                  </section>
                </div>
              ) : (
                <div className="py-12 flex flex-col items-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
                  <p className="text-muted-foreground text-sm">Select a user to inspect it.</p>
                </div>
              )
            ) : (
              <form className="space-y-6 animate-in fade-in duration-300" onSubmit={handleSubmit}>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 bg-muted/20 p-5 rounded-xl border border-border/50">
                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2">Username</label>
                    <Input
                      className="h-10 rounded-md bg-background"
                      value={form.username}
                      onChange={(event) => updateField('username', event.target.value)}
                      placeholder="operator"
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2">Display Name</label>
                    <Input
                      className="h-10 rounded-md bg-background"
                      value={form.displayName}
                      onChange={(event) => updateField('displayName', event.target.value)}
                      placeholder="Operations User"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2">Email</label>
                    <Input
                      className="h-10 rounded-md bg-background"
                      value={form.email}
                      onChange={(event) => updateField('email', event.target.value)}
                      placeholder="user@example.com"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2">Status</label>
                    <Select value={form.status} onValueChange={(value) => updateField('status', value)}>
                      <SelectTrigger className="h-10 w-full rounded-md bg-background">
                        <SelectValue placeholder="Select status" />
                      </SelectTrigger>
                      <SelectContent>
                        {USER_STATUS_OPTIONS.map((status) => (
                          <SelectItem key={status} value={status}>
                            {status}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  {sheetMode === 'create' && isSuperAdmin ? (
                    <div className="space-y-2">
                      <label className="text-sm font-semibold flex items-center gap-2">Role</label>
                      <Select value={form.role} onValueChange={(value) => updateField('role', value)}>
                        <SelectTrigger className="h-10 w-full rounded-md bg-background">
                          <SelectValue placeholder="Select role" />
                        </SelectTrigger>
                        <SelectContent>
                          {USER_ROLE_OPTIONS.map((role) => (
                            <SelectItem key={role} value={role}>
                              {role}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  ) : null}
                </div>

                {sheetMode === 'create' ? (
                  <div className="space-y-2 sm:col-span-2">
                    <label className="text-sm font-semibold flex items-center gap-2">Initial Password</label>
                    <Input
                      className="h-10 rounded-md bg-background max-w-md"
                      type="password"
                      value={form.password}
                      onChange={(event) => updateField('password', event.target.value)}
                      autoComplete="new-password"
                      required
                    />
                  </div>
                ) : null}

                <div className="flex gap-3 pt-6">
                  <Button type="submit" disabled={isSubmitting} className="min-w-[120px] gap-2 shadow-sm hover:shadow-md transition-shadow">
                    {isSubmitting ? (
                      'Saving...'
                    ) : (
                      <>
                        <Save className="h-4 w-4" />
                        {sheetMode === 'create' ? 'Create user' : 'Save changes'}
                      </>
                    )}
                  </Button>
                  <Button type="button" variant="outline" className="gap-2 shadow-sm" onClick={() => setIsSheetOpen(false)} disabled={isSubmitting}>
                    <X className="h-4 w-4" /> Cancel
                  </Button>
                </div>
              </form>
            )}
          </div>

          {sheetMode === 'detail' && selectedUser ? (
            <div className="p-6 md:p-8 shrink-0 border-t bg-muted/20 flex gap-3">
              <Button onClick={openEditSheet} className="shadow-sm gap-2">
                <Edit3 className="h-4 w-4" /> Edit User
              </Button>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive" disabled={isDeleting} className="shadow-sm gap-2">
                    <Trash2 className="h-4 w-4" />
                    {isDeleting ? 'Deleting...' : 'Delete User'}
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This action cannot be undone. This will permanently delete the user account and disable their access immediately.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={() => void handleDelete()} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">Delete permanently</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
          ) : null}
        </SheetContent>
      </Sheet>

      <Toaster />
    </div>
  )
}
