import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemHeader,
  ItemTitle,
} from '@/components/ui/item'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Toaster } from '@/components/ui/sonner'
import { request } from '@/lib/api'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Plus, Search, Trash2, Edit3, Save, Zap, Clock, User, Globe, Database, FileText, ChevronLeft, ChevronRight } from 'lucide-react'
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

type Skill = {
  id: string
  name: string
  description: string | null
  visibility: string | null
  userId: string | null
  source: string | null
  content: string
  createdAt: string
  updatedAt: string
}

type SkillPageResponse = {
  items: Skill[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

type SkillFormState = {
  name: string
  description: string
  visibility: string
  content: string
}

const pageSize = 20

const emptyForm: SkillFormState = {
  name: '',
  description: '',
  visibility: 'PRIVATE',
  content: '',
}

function toFormState(skill: Skill): SkillFormState {
  return {
    name: skill.name ?? '',
    description: skill.description ?? '',
    visibility: skill.visibility ?? 'PRIVATE',
    content: skill.content ?? '',
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

export default function SkillsPage() {
  const [skills, setSkills] = useState<Skill[]>([])
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null)
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null)
  const [form, setForm] = useState<SkillFormState>(emptyForm)
  const [sheetMode, setSheetMode] = useState<'create' | 'edit' | 'detail'>('detail')
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isListLoading, setIsListLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [query, setQuery] = useState('')

  useEffect(() => {
    void loadPage(page)
  }, [page])

  async function loadPage(nextPage: number) {
    setIsListLoading(true)

    try {
      const data = await request<SkillPageResponse>(
        `/internal-api/v1/skills?page=${nextPage}&size=${pageSize}`,
        { method: 'GET' },
      )

      setSkills(data.items)
      setTotalItems(data.totalItems)
      setTotalPages(data.totalPages)

      if (data.items.length === 0) {
        setSelectedSkillId(null)
        setSelectedSkill(null)
      }
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load skills.')
      setSkills([])
      setTotalItems(0)
      setTotalPages(0)
      setSelectedSkillId(null)
      setSelectedSkill(null)
    } finally {
      setIsListLoading(false)
    }
  }

  async function selectSkill(skillId: string) {
    setSelectedSkillId(skillId)
    setIsDetailLoading(true)

    try {
      const skill = await request<Skill>(`/internal-api/v1/skills/${skillId}`, {
        method: 'GET',
      })
      setSelectedSkill(skill)
      setSheetMode('detail')
      setIsSheetOpen(true)
    } catch (loadError) {
      setSelectedSkill(null)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load skill details.')
    } finally {
      setIsDetailLoading(false)
    }
  }

  function openCreateSheet() {
    setSheetMode('create')
    setForm(emptyForm)
    setIsSheetOpen(true)
  }

  function openEditSheet() {
    if (!selectedSkill) {
      return
    }

    setSheetMode('edit')
    setForm(toFormState(selectedSkill))
    setIsSheetOpen(true)
  }

  function updateField<Key extends keyof SkillFormState>(key: Key, value: SkillFormState[Key]) {
    setForm((current) => ({
      ...current,
      [key]: value,
    }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)

    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      visibility: form.visibility.trim(),
      content: form.content,
    }

    try {
      const skill =
        sheetMode === 'create'
          ? await request<Skill>('/internal-api/v1/skills', {
              method: 'POST',
              body: JSON.stringify(payload),
            })
          : await request<Skill>(`/internal-api/v1/skills/${selectedSkillId}`, {
              method: 'PUT',
              body: JSON.stringify(payload),
            })

      await loadPage(page)
      await selectSkill(skill.id)
      toast.success(sheetMode === 'create' ? 'Skill created.' : 'Skill updated.')
    } catch (submitError) {
      const message = submitError instanceof Error ? submitError.message : 'Failed to save skill.'
      toast.error(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleDelete() {
    if (!selectedSkillId) {
      return
    }

    setIsDeleting(true)

    try {
      await request<void>(`/internal-api/v1/skills/${selectedSkillId}`, {
        method: 'DELETE',
      })

      const shouldMoveToPreviousPage = page > 0 && skills.length === 1
      if (shouldMoveToPreviousPage) {
        setPage((current) => current - 1)
      } else {
        await loadPage(page)
      }

      setSelectedSkill(null)
      setSelectedSkillId(null)
      setIsSheetOpen(false)
      toast.success('Skill deleted.')
    } catch (deleteError) {
      const message = deleteError instanceof Error ? deleteError.message : 'Failed to delete skill.'
      toast.error(message)
    } finally {
      setIsDeleting(false)
    }
  }

  const normalizedQuery = query.trim().toLowerCase()
  const filteredSkills = skills.filter((skill) => {
    if (!normalizedQuery) {
      return true
    }

    return [skill.name, skill.description, skill.visibility, skill.source, skill.userId]
      .filter((value): value is string => Boolean(value))
      .some((value) => value.toLowerCase().includes(normalizedQuery))
  })

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <Zap className="h-8 w-8 text-primary" />
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Skills</h2>
        </div>
      </header>

      <section className="space-y-6">
        <div className="flex flex-col md:flex-row justify-between md:items-center gap-4">
          <div className="flex items-center gap-4">
            <h3 className="text-xl font-semibold">Skills Library</h3>
            <span className="bg-primary/10 text-primary text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">{totalItems} total</span>
          </div>

          <div className="flex gap-2 w-full md:w-auto">
            <Button className="w-full md:w-auto shadow-sm hover:shadow-md transition-shadow gap-2" onClick={openCreateSheet}>
              <Plus className="h-4 w-4" /> New skill
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
              placeholder="Search skills library..."
            />
          </div>
        </div>

        {isListLoading ? <p>Loading skills...</p> : null}
        {!isListLoading && skills.length === 0 ? <p>No skills found.</p> : null}
        {!isListLoading && skills.length > 0 && filteredSkills.length === 0 ? (
          <p>No skills match the current search.</p>
        ) : null}

        <ItemGroup aria-label="Skill list">
            {filteredSkills.map((skill) => (
              <Item
                key={skill.id}
                variant="outline"
                className="cursor-pointer"
                role="button"
                tabIndex={0}
                onClick={() => void selectSkill(skill.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    void selectSkill(skill.id)
                  }
                }}
              >
                <ItemContent>
                  <ItemHeader>
                    <ItemTitle>{skill.name}</ItemTitle>
                    <div className="flex gap-2 shrink-0">
                      <Badge variant="secondary">{skill.visibility}</Badge>
                      <Badge variant="outline">{skill.source}</Badge>
                    </div>
                  </ItemHeader>
                  <ItemDescription>{skill.description?.trim() || 'No description provided.'}</ItemDescription>
                  <ItemDescription className="mt-2 text-[10px] flex items-center gap-1.5 uppercase font-bold tracking-tight">
                    <Clock className="h-3 w-3" />
                    Updated {formatDate(skill.updatedAt)}
                  </ItemDescription>
                </ItemContent>
              </Item>
            ))}
          </ItemGroup>

        <div className="flex items-center justify-between text-sm pt-4">
          <span className="text-muted-foreground font-medium">
            {totalItems === 0
              ? 'Showing 0 skills'
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
          className="!w-[90vw] sm:!max-w-2xl md:!max-w-3xl lg:!max-w-4xl xl:!max-w-5xl overflow-y-auto border-l border-border/40 shadow-2xl p-0 flex flex-col"
        >
          <div className="p-6 md:p-8 shrink-0 border-b bg-background/95 backdrop-blur sticky top-0 z-10 supports-[backdrop-filter]:bg-background/60">
            <SheetHeader>
              <p className="text-[10px] uppercase tracking-widest text-primary font-bold mb-2">
                {sheetMode === 'create' ? 'New Definition' : sheetMode === 'edit' ? 'Update Skill' : 'Skill Details'}
              </p>
              <SheetTitle className="text-2xl mt-1 tracking-tight">
                {sheetMode === 'create'
                  ? 'Define New Skill'
                  : selectedSkill?.name || form.name || 'Unnamed Skill'}
              </SheetTitle>
              <SheetDescription className="text-sm text-foreground/60 leading-relaxed mt-2 max-w-lg">
                {sheetMode === 'detail'
                  ? 'Deeply inspect the selected skill artifact. Monitor references, check visibility state, and manage core instructions.'
                  : sheetMode === 'create'
                    ? 'Inject a new functional capability into the system. High-level logic and instructions will be compiled into the skill library.'
                    : 'Refine the operational logic of the selected skill. Changes will propagate to all agents consuming this capability.'}
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
              selectedSkill ? (
                <div className="space-y-8 animate-in fade-in duration-300">
                  <section className="bg-muted/20 p-4 rounded-xl border border-border/40 inline-flex flex-wrap items-center gap-2">
                    <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500"></span> {selectedSkill.visibility}</Badge>
                    <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-purple-500"></span> {selectedSkill.source}</Badge>
                    <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><Clock className="h-3 w-3" /> Updated {formatDate(selectedSkill.updatedAt)}</Badge>
                  </section>

                  <section className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><User className="h-3 w-3" />Owner Context</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedSkill.userId)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><Globe className="h-3 w-3" />Visibility</span>
                      <p className="font-medium text-sm">{selectedSkill.visibility}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><Database className="h-3 w-3" />Source</span>
                      <p className="font-medium text-sm">{formatValue(selectedSkill.source)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block text-muted-foreground/80">Created At</span>
                      <p className="text-sm font-medium">{formatDate(selectedSkill.createdAt)}</p>
                    </article>
                  </section>

                  <section className="rounded-xl border shadow-sm overflow-hidden flex flex-col bg-card">
                    <div className="bg-muted/40 px-5 py-4 flex items-center justify-between border-b">
                      <span className="text-sm font-bold flex items-center gap-2"><FileText className="h-4 w-4" /> Operational Instructions</span>
                      <span className="text-[10px] font-bold tracking-wider uppercase bg-primary/10 text-primary px-2 py-0.5 rounded-sm">
                        {selectedSkill.content.length} characters
                      </span>
                    </div>
                    <div className="p-5 overflow-x-auto">
                      <pre className="text-[13px] leading-relaxed font-mono text-foreground/80 whitespace-pre-wrap break-words">
                        {selectedSkill.content}
                      </pre>
                    </div>
                  </section>
                </div>
              ) : (
                <div className="text-muted-foreground text-center py-12 border-2 border-dashed rounded-xl">Select a skill to inspect its operational logic.</div>
              )
            ) : (
              <form className="space-y-6 animate-in fade-in duration-300" onSubmit={handleSubmit}>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2">
                      <Edit3 className="h-3.5 w-3.5 text-primary" /> Skill Name
                    </label>
                    <Input
                      className="h-10 rounded-md bg-background border-border/60 focus-visible:ring-primary/20"
                      value={form.name}
                      onChange={(event) => updateField('name', event.target.value)}
                      placeholder="Enter a descriptive name (e.g., Tactical Planner)"
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2">
                      <Globe className="h-3.5 w-3.5 text-primary" /> Visibility
                    </label>
                    <Select value={form.visibility} onValueChange={(value) => updateField('visibility', value)}>
                      <SelectTrigger className="h-10 w-full rounded-md bg-background border-border/60">
                        <SelectValue placeholder="Select visibility" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="PRIVATE">Private (User only)</SelectItem>
                        <SelectItem value="PUBLIC">Public (Organization)</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-semibold flex items-center gap-2">
                    <FileText className="h-3.5 w-3.5 text-primary" /> Brief Description
                  </label>
                  <Input
                    className="h-10 rounded-md bg-background border-border/60 focus-visible:ring-primary/20"
                    value={form.description}
                    onChange={(event) => updateField('description', event.target.value)}
                    placeholder="Provide a brief overview of this skill's core function..."
                  />
                </div>

                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <label className="text-sm font-semibold flex items-center gap-2">
                      <Database className="h-3.5 w-3.5 text-primary" /> Core Instructions
                    </label>
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Supports Markdown</span>
                  </div>
                  <textarea
                    className="w-full min-h-[400px] p-4 text-sm font-mono leading-relaxed rounded-xl border border-input shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 transition-all resize-y bg-background"
                    value={form.content}
                    onChange={(event) => updateField('content', event.target.value)}
                    placeholder="Define the functional requirements, prompts, and execution logic..."
                    rows={16}
                    required
                  />
                </div>

                <div className="pt-6 border-t flex justify-end gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    className="shadow-sm border-border/60"
                    onClick={() => setIsSheetOpen(false)}
                    disabled={isSubmitting}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" disabled={isSubmitting} className="min-w-[140px] gap-2 shadow-md hover:shadow-lg transition-all hover:-translate-y-0.5">
                    {isSubmitting ? (
                      'Processing...'
                    ) : (
                      <>
                        <Save className="h-4 w-4" />
                        {sheetMode === 'create' ? 'Create Skill' : 'Save Changes'}
                      </>
                    )}
                  </Button>
                </div>
              </form>
            )}
          </div>

          {sheetMode === 'detail' && selectedSkill ? (
            <SheetFooter className="p-6 md:p-8 border-t bg-muted/10 shrink-0 flex flex-row items-center justify-between gap-3 shadow-[0_-10px_20px_rgba(0,0,0,0.02)]">
              <Button onClick={openEditSheet} variant="outline" className="shadow-sm gap-2 bg-background border-border/60 flex-1 sm:flex-none">
                <Edit3 className="h-4 w-4" /> Edit Skill
              </Button>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive" disabled={isDeleting} className="shadow-sm gap-2 flex-1 sm:flex-none">
                    <Trash2 className="h-4 w-4" />
                    {isDeleting ? 'Deleting...' : 'Delete Skill'}
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This action cannot be undone. This will permanently delete the selected skill from the library.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={() => void handleDelete()} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">Delete Skill</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </SheetFooter>
          ) : null}
        </SheetContent>
      </Sheet>

      <Toaster />
    </div>
  )
}
