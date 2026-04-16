import { useEffect, useRef, useState } from 'react'
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
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter as AlertDialogFooterUI,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { request } from '@/lib/api'
import { Plus, Search, Archive, Trash2, ChevronLeft, ChevronRight, Save, FileText, Clock, User, MessageSquare, ArrowDown, ArrowUp } from 'lucide-react'

type Memory = {
  id: string
  ownerUserId: string | null
  sessionId: string | null
  scope: string
  kind: string
  source: string
  retentionPolicy: string
  status: string
  statusReason: string | null
  statusChangedAt: string | null
  statusChangedBy: string | null
  document: {
    id: string
    memoryId: string
    fileName: string | null
    filePath: string | null
    fileSize: number | null
    fileContentType: string | null
    createdAt: string
    updatedAt: string
  } | null
  content: string
  createdAt: string
  updatedAt: string
}

type MemoryPageResponse = {
  items: Memory[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

type MemoryForm = {
  scope: string
  kind: string
  retentionPolicy: string
  content: string
}

type CreatedAtSort = 'asc' | 'desc'

const DEFAULT_PAGE_SIZE = 20
const PAGE_SIZE_OPTIONS = [20, 50, 100] as const
const MEMORY_KIND_OPTIONS = [
  { value: 'knowledge_note', label: 'Knowledge note' },
  { value: 'fact', label: 'Fact' },
  { value: 'summary', label: 'Summary' },
  { value: 'document', label: 'Document' },
] as const
const MEMORY_RETENTION_OPTIONS = [
  { value: 'preserve_raw', label: 'Preserve raw' },
  { value: 'compactable', label: 'Compactable' },
] as const

const emptyForm: MemoryForm = {
  scope: 'user',
  kind: 'knowledge_note',
  retentionPolicy: 'preserve_raw',
  content: '',
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Not set'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatValue(value: string | null) {
  return value && value.trim() ? value : 'Not set'
}

function formatFileSize(value: number | null) {
  if (value === null || Number.isNaN(value)) {
    return 'Not set'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function memoryMeta(memory: Memory) {
  return `Updated ${formatDate(memory.updatedAt)} · ${memory.scope} · ${memory.kind} · ${memory.status}`
}

export default function MemoriesPage() {
  const [memories, setMemories] = useState<Memory[]>([])
  const [selectedMemoryId, setSelectedMemoryId] = useState<string | null>(null)
  const [selectedMemory, setSelectedMemory] = useState<Memory | null>(null)
  const [sheetMode, setSheetMode] = useState<'detail' | 'create'>('detail')
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [form, setForm] = useState<MemoryForm>(emptyForm)
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [query, setQuery] = useState('')
  const [queryDraft, setQueryDraft] = useState('')
  const [createdAtSort, setCreatedAtSort] = useState<CreatedAtSort>('desc')
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isListLoading, setIsListLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const uploadInputRef = useRef<HTMLInputElement | null>(null)
  useEffect(() => {
    void loadPage(page, query, pageSize, createdAtSort)
  }, [page, query, pageSize, createdAtSort])

  async function loadPage(nextPage: number, nextQuery: string, nextPageSize: number, nextCreatedAtSort: CreatedAtSort) {
    setIsListLoading(true)

    try {
      const params = new URLSearchParams({
        page: String(nextPage),
        size: String(nextPageSize),
        createdAtSort: nextCreatedAtSort,
      })
      if (nextQuery.trim()) {
        params.set('q', nextQuery.trim())
      }

      const data = await request<MemoryPageResponse>(`/internal-api/v1/memories?${params.toString()}`, {
        method: 'GET',
      })

      setMemories(data.items)
      setPage(data.page)
      setPageSize(data.size)
      setTotalItems(data.totalItems)
      setTotalPages(data.totalPages)

      if (data.items.length === 0) {
        setSelectedMemoryId(null)
        setSelectedMemory(null)
      }
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load memories.')
      setMemories([])
      setSelectedMemoryId(null)
      setSelectedMemory(null)
      setTotalItems(0)
      setTotalPages(0)
    } finally {
      setIsListLoading(false)
    }
  }

  async function selectMemory(memoryId: string) {
    setSelectedMemoryId(memoryId)
    setIsDetailLoading(true)

    try {
      const memory = await request<Memory>(`/internal-api/v1/memories/${memoryId}`, { method: 'GET' })
      setSelectedMemory(memory)
      setSheetMode('detail')
      setIsSheetOpen(true)
    } catch (loadError) {
      setSelectedMemory(null)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load memory details.')
    } finally {
      setIsDetailLoading(false)
    }
  }

  function updateField<Key extends keyof MemoryForm>(key: Key, value: MemoryForm[Key]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function openCreateSheet() {
    setSheetMode('create')
    setForm(emptyForm)
    setUploadFile(null)
    if (uploadInputRef.current) {
      uploadInputRef.current.value = ''
    }
    setIsSheetOpen(true)
  }

  function clearUploadFile() {
    setUploadFile(null)
    if (uploadInputRef.current) {
      uploadInputRef.current.value = ''
    }
  }

  function applySearch() {
    setPage(0)
    setQuery(queryDraft.trim())
  }

  async function handleCreateMemory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)

    try {
      const formData = new FormData()
      formData.set('scope', form.scope)
      formData.set('kind', form.kind)
      formData.set('retentionPolicy', form.retentionPolicy)
      if (form.content.trim()) {
        formData.set('content', form.content)
      }
      if (uploadFile) {
        formData.set('file', uploadFile)
      }

      const createdMemory = await request<Memory>('/internal-api/v1/memories', {
        method: 'POST',
        body: formData,
      })

      setForm(emptyForm)
      setUploadFile(null)
      if (uploadInputRef.current) {
        uploadInputRef.current.value = ''
      }
      toast.success('Memory created successfully')
      await loadPage(page, query, pageSize, createdAtSort)
      await selectMemory(createdMemory.id)
    } catch (submitError) {
      toast.error(submitError instanceof Error ? submitError.message : 'Failed to create memory.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleArchive() {
    if (!selectedMemoryId) {
      return
    }

    setIsArchiving(true)

    try {
      const memory = await request<Memory>(`/internal-api/v1/memories/${selectedMemoryId}/archive`, {
        method: 'POST',
      })
      toast.success('Memory archived successfully')
      setSelectedMemory(memory)
      await loadPage(page, query, pageSize, createdAtSort)
    } catch (archiveError) {
      toast.error(archiveError instanceof Error ? archiveError.message : 'Failed to archive memory.')
    } finally {
      setIsArchiving(false)
    }
  }

  async function handleDelete() {
    if (!selectedMemoryId) {
      return
    }

    setIsDeleting(true)

    try {
      await request<void>(`/internal-api/v1/memories/${selectedMemoryId}`, { method: 'DELETE' })
      toast.success('Memory deleted successfully')
      const shouldMoveToPreviousPage = page > 0 && memories.length === 1
      if (shouldMoveToPreviousPage) {
        setPage((current) => current - 1)
      } else {
        await loadPage(page, query, pageSize, createdAtSort)
      }
      setSelectedMemory(null)
      setSelectedMemoryId(null)
      setIsSheetOpen(false)
    } catch (deleteError) {
      toast.error(deleteError instanceof Error ? deleteError.message : 'Failed to delete memory.')
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Memories</h2>
        </div>
      </header>

      <section className="space-y-6">
        <div className="flex flex-col md:flex-row justify-between md:items-center gap-4">
          <div className="flex items-center gap-4">
            <h3 className="text-xl font-semibold">Memory Hub</h3>
            <span className="bg-primary/10 text-primary text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">{totalItems} Results</span>
          </div>
          <Button onClick={openCreateSheet} className="w-full md:w-auto shadow-sm hover:shadow-md transition-shadow gap-2">
            <Plus className="h-4 w-4" /> New Memory
          </Button>
        </div>

        <div className="flex flex-col md:flex-row gap-4 items-center justify-between bg-muted/30 p-2 rounded-lg">
          <div className="w-full md:w-2/3 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              className="h-10 pl-10 rounded-md w-full bg-background border-border/60 shadow-sm focus-visible:ring-primary/30"
              value={queryDraft}
              onChange={(event) => setQueryDraft(event.target.value)}
              placeholder="Search indexed knowledge..."
            />
          </div>
          <Button
            type="button"
            variant="outline"
            className="h-10 w-full md:w-auto gap-2 bg-background border-border/60 shadow-sm rounded-md"
            onClick={applySearch}
            disabled={isListLoading && queryDraft.trim() === query}
          >
            <Search className="h-4 w-4" />
            Search
          </Button>

          <div className="flex items-center gap-3 text-sm px-2">
            {query.trim().length === 0 ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  setPage(0)
                  setCreatedAtSort((current) => current === 'desc' ? 'asc' : 'desc')
                }}
                className="h-9 gap-2 bg-background border-border/60 shadow-sm rounded-md"
                aria-label={`Sort by created time ${createdAtSort === 'desc' ? 'ascending' : 'descending'}`}
              >
                {createdAtSort === 'desc' ? <ArrowDown className="h-4 w-4" /> : <ArrowUp className="h-4 w-4" />}
                Created {createdAtSort === 'desc' ? 'newest' : 'oldest'}
              </Button>
            ) : null}
            <label className="flex items-center gap-2">
              <span className="text-muted-foreground/80 font-medium whitespace-nowrap">Show:</span>
              <Select
                value={String(pageSize)}
                onValueChange={(value) => {
                  setPage(0)
                  setPageSize(Number(value))
                }}
              >
                <SelectTrigger className="h-9 w-[120px] bg-background border-border/60 shadow-sm focus:ring-2 focus:ring-primary/20">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAGE_SIZE_OPTIONS.map((option) => (
                    <SelectItem key={option} value={String(option)}>
                      {option} / page
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          </div>
        </div>

        {isListLoading ? (
          <div className="py-24 flex flex-col items-center justify-center space-y-4">
            <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
            <p className="text-muted-foreground text-sm font-medium animate-pulse">Loading memories...</p>
          </div>
        ) : !isListLoading && memories.length === 0 ? (
          <div className="py-16 flex flex-col items-center justify-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
            <div className="w-16 h-16 bg-muted/50 rounded-full flex items-center justify-center mb-4">
              <span className="text-2xl opacity-50">💭</span>
            </div>
            <h4 className="text-lg font-semibold mb-2">No memories found</h4>
            <p className="text-muted-foreground text-sm mb-6 max-w-md">Try adjusting your search query or creating a new memory altogether.</p>
            <Button variant="outline" onClick={openCreateSheet} className="shadow-sm gap-2">
              <Plus className="h-4 w-4" /> Create your first memory
            </Button>
          </div>
        ) : (
          <ItemGroup aria-label="Memory list">
            {memories.map((memory) => (
              <Item
                key={memory.id}
                variant="outline"
                className="cursor-pointer"
                role="button"
                tabIndex={0}
                onClick={() => void selectMemory(memory.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    void selectMemory(memory.id)
                  }
                }}
              >
                <ItemContent>
                  <ItemHeader>
                    <ItemTitle>
                      {memory.document?.fileName?.trim() || memory.kind.replaceAll('_', ' ').replace(/\b\w/g, l => l.toUpperCase())}
                    </ItemTitle>
                    <div className="flex gap-2 shrink-0">
                      <Badge variant="secondary">{memory.scope}</Badge>
                      <Badge variant={memory.status === 'active' ? 'default' : 'outline'} className={memory.status === 'active' ? 'bg-green-500/10 text-green-600 border-green-500/20' : 'bg-yellow-500/10 text-yellow-600 border-yellow-500/20'}>{memory.status}</Badge>
                    </div>
                  </ItemHeader>
                  <ItemDescription>
                    {memory.content.slice(0, 200) || 'No textual content available.'}
                  </ItemDescription>
                  <ItemDescription className="mt-2 text-[10px] flex items-center gap-1.5 uppercase font-bold tracking-tight">
                    <Clock className="h-3 w-3" />
                    {memoryMeta(memory)}
                  </ItemDescription>
                </ItemContent>
              </Item>
            ))}
          </ItemGroup>
        )}

        <div className="flex items-center justify-between text-sm pt-4">
          <span className="text-muted-foreground font-medium">
            {totalItems === 0
              ? 'Showing 0 memories'
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
              <p className="text-[10px] uppercase tracking-widest text-primary font-bold mb-2">{sheetMode === 'create' ? 'Create Node' : 'Memory Details'}</p>
              <SheetTitle className="text-2xl mt-1 tracking-tight">
                {sheetMode === 'create'
                  ? 'Store New Memory'
                  : selectedMemory?.document?.fileName || selectedMemory?.kind.replaceAll('_', ' ').replace(/\b\w/g, l => l.toUpperCase()) || 'Unnamed Memory'}
              </SheetTitle>
              <SheetDescription className="text-sm text-foreground/60 leading-relaxed mt-2 max-w-lg">
                {sheetMode === 'create'
                  ? 'Inject a memory systematically from raw text, or attach a structured file. Contents naturally embed into vector space.'
                  : 'Deeply inspect the selected memory artifact. Monitor references, check lifecycle state, and manage source files.'}
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
              selectedMemory ? (
                <div className="space-y-8 animate-in fade-in duration-300">
                  <section className="bg-muted/20 p-4 rounded-xl border border-border/40 inline-flex flex-wrap items-center gap-2">
                    <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500"></span> {selectedMemory.scope}</Badge>
                    <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-purple-500"></span> {selectedMemory.kind}</Badge>
                    <Badge variant="outline" className={`shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5 ${selectedMemory.status === 'active' ? 'bg-green-500/10 text-green-700 border-green-500/20' : 'bg-yellow-500/10 text-yellow-700 border-yellow-500/20'}`}><span className={`w-2 h-2 rounded-full ${selectedMemory.status === 'active' ? 'bg-green-500' : 'bg-yellow-500'}`}></span> {selectedMemory.status}</Badge>
                  </section>

                  <section className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><User className="h-3 w-3" />Owner Context</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedMemory.ownerUserId)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2"><MessageSquare className="h-3 w-3" />Session Link</span>
                      <p className="font-mono text-sm break-all text-primary/80">{formatValue(selectedMemory.sessionId)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Source Origin</span>
                      <p className="text-sm font-medium">{formatValue(selectedMemory.source)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Retention Rules</span>
                      <p className="text-sm font-medium">{formatValue(selectedMemory.retentionPolicy)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Created Vector Time</span>
                      <p className="text-sm font-medium">{formatDate(selectedMemory.createdAt)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Last Sync Time</span>
                      <p className="text-sm font-medium">{formatDate(selectedMemory.updatedAt)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow col-span-1 sm:col-span-2 bg-gradient-to-br from-card to-muted/20">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Status Metadata</span>
                      <p className="text-sm font-medium italic">{formatValue(selectedMemory.statusReason)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Blob Size</span>
                      <p className="font-mono text-sm">{formatFileSize(selectedMemory.document?.fileSize ?? null)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">MIME Type</span>
                      <p className="font-mono text-sm">{formatValue(selectedMemory.document?.fileContentType ?? null)}</p>
                    </article>
                    <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow col-span-1 sm:col-span-2">
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Physical File URI</span>
                      <p className="font-mono text-xs break-all text-primary bg-primary/5 p-2 rounded block">{formatValue(selectedMemory.document?.filePath ?? null)}</p>
                    </article>
                  </section>

                  <section className="rounded-xl border shadow-sm overflow-hidden flex flex-col bg-card">
                    <div className="bg-muted/40 px-5 py-4 flex items-center justify-between border-b">
                      <span className="text-sm font-bold flex items-center gap-2"><FileText className="h-4 w-4" /> Embedded Material</span>
                      <span className="text-[10px] font-bold tracking-wider uppercase bg-primary/10 text-primary px-2 py-0.5 rounded-sm">
                        {selectedMemory.content.length} characters
                      </span>
                    </div>
                    <div className="p-5 overflow-x-auto">
                      <pre className="text-[13px] leading-relaxed font-mono text-foreground/80 whitespace-pre-wrap break-words">
                        {selectedMemory.content}
                      </pre>
                    </div>
                  </section>
                </div>
              ) : (
                <div className="text-muted-foreground text-center py-12 border-2 border-dashed rounded-xl">Select a memory context object to inspect payload details.</div>
              )
            ) : (
              <form className="space-y-6 animate-in fade-in duration-300" onSubmit={handleCreateMemory}>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="4" /><line x1="21.17" y1="8" x2="12" y2="8" /><line x1="3.95" y1="6.06" x2="8.54" y2="14" /><line x1="10.88" y1="21.94" x2="15.46" y2="14" /></svg>Scope Strategy</label>
                    <Select
                      value={form.scope}
                      onValueChange={(value) => updateField('scope', value)}
                    >
                      <SelectTrigger className="w-full bg-background">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="user">User Space</SelectItem>
                        <SelectItem value="public">Global Namespace</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-semibold flex items-center gap-2"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>Knowledge Kind</label>
                    <Select
                      value={form.kind}
                      onValueChange={(value) => updateField('kind', value)}
                    >
                      <SelectTrigger className="w-full bg-background">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {MEMORY_KIND_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-2 sm:col-span-2">
                    <label className="text-sm font-semibold flex items-center gap-2"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" /></svg>Retention Policy</label>
                    <Select
                      value={form.retentionPolicy}
                      onValueChange={(value) => updateField('retentionPolicy', value)}
                    >
                      <SelectTrigger className="w-full bg-background">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {MEMORY_RETENTION_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label className="text-sm font-semibold">Raw Memory Content</label>
                    <span className="text-xs text-muted-foreground italic">
                      Optional when a file is attached
                    </span>
                  </div>
                  <textarea
                    className="w-full min-h-[220px] p-4 text-sm font-mono leading-relaxed rounded-xl border border-input shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 transition-all resize-y bg-background disabled:cursor-not-allowed disabled:opacity-60"
                    value={form.content}
                    onChange={(event) => updateField('content', event.target.value)}
                    disabled={isSubmitting}
                    placeholder="Enter facts, insights, or rules to be persisted in memory..."
                    rows={10}
                  />
                </div>

                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <label className="text-sm font-semibold flex items-center gap-2"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><polyline points="13 2 13 9 20 9" /></svg>File Attachment</label>
                    <span className="text-xs text-muted-foreground italic">
                      Optional when content is entered
                    </span>
                  </div>
                  <div className="relative border-2 border-dashed border-border/80 rounded-lg p-6 transition-colors hover:bg-muted/30">
                    <input
                      ref={uploadInputRef}
                      className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
                      type="file"
                      accept=".txt,.md,.csv,.json,.xml,.yaml,.yml,.log,.pdf,text/*,application/pdf"
                      disabled={isSubmitting}
                      onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
                    />
                    {uploadFile ? (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="absolute right-3 top-3 z-10"
                        onClick={clearUploadFile}
                        disabled={isSubmitting}
                      >
                        Remove
                      </Button>
                    ) : null}
                    <div className="flex flex-col items-center justify-center text-center gap-2 pointer-events-none">
                      <div className="p-3 bg-background rounded-full shadow-sm mb-1">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-primary"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" /></svg>
                      </div>
                      <span className="text-sm font-semibold">{uploadFile ? uploadFile.name : "Click or drag file here"}</span>
                      <span className="text-xs text-muted-foreground">TXT, MD, CSV, JSON, LOG, PDF</span>
                    </div>
                  </div>
                </div>

                <div className="pt-6 border-t flex justify-end">
                  <Button type="submit" disabled={isSubmitting} className="w-full sm:w-auto shadow-md hover:shadow-lg transition-transform hover:-translate-y-0.5 gap-2" size="lg">
                    {isSubmitting ? (
                      'Embedding Memory...'
                    ) : (
                      <>
                        <Save className="h-4 w-4" /> Initialize Memory Context
                      </>
                    )}
                  </Button>
                </div>
              </form>
            )}
          </div>

          {sheetMode === 'detail' && selectedMemory ? (
            <SheetFooter className="p-6 border-t bg-muted/10 shrink-0 flex-col sm:flex-row gap-3 shadow-[0_-10px_20px_rgba(0,0,0,0.02)]">
              <Button
                variant="outline"
                className="shadow-sm bg-background flex-1 sm:flex-none gap-2"
                onClick={() => void handleArchive()}
                disabled={isArchiving || selectedMemory.status !== 'active'}
              >
                <Archive className="h-4 w-4" />
                {isArchiving ? 'Archiving Node...' : 'Archive State'}
              </Button>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive" className="shadow-sm flex-1 sm:flex-none gap-2" disabled={isDeleting}>
                    <Trash2 className="h-4 w-4" />
                    {isDeleting ? 'Erasing Artifact...' : 'Delete Memory Blob'}
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This action cannot be undone. This will permanently delete the memory record.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooterUI>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={() => void handleDelete()} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">Delete Memory</AlertDialogAction>
                  </AlertDialogFooterUI>
                </AlertDialogContent>
              </AlertDialog>
            </SheetFooter>
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  )
}
