import { useEffect, useMemo, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Toaster } from '@/components/ui/sonner'
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
import { toast } from 'sonner'
import {
  ChevronLeft,
  ChevronRight,
  Clock3,
  Search,
  Trash2,
  Activity,
  AlertTriangle,
  Zap,
} from 'lucide-react'

type ScheduledTask = {
  id: string
  userId: string
  taskName: string
  taskType: string
  taskDetails: string | null
  enabled: boolean
  cronExpression: string
  nextRunAt: string | null
  lastStartedAt: string | null
  lastCompletedAt: string | null
  lastSuccessAt: string | null
  lastFailureAt: string | null
  lastError: string | null
  createdAt: string
  updatedAt: string
}

type ScheduledTaskPageResponse = {
  items: ScheduledTask[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

const pageSize = 12

function formatDate(value: string | null) {
  if (!value) {
    return 'Not scheduled'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}



export default function CronJobsPage() {
  const [jobs, setJobs] = useState<ScheduledTask[]>([])
  const [selectedJob, setSelectedJob] = useState<ScheduledTask | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [query, setQuery] = useState('')
  const [searchValue, setSearchValue] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [togglingId, setTogglingId] = useState<string | null>(null)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setPage(0)
      setQuery(searchValue.trim())
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [searchValue])

  useEffect(() => {
    void loadPage(page, query)
  }, [page, query])

  async function loadPage(nextPage: number, nextQuery: string) {
    setIsLoading(true)

    try {
      const params = new URLSearchParams({
        page: String(nextPage),
        size: String(pageSize),
      })
      if (nextQuery) {
        params.set('q', nextQuery)
      }

      const data = await request<ScheduledTaskPageResponse>(
        `/internal-api/v1/scheduled-tasks?${params.toString()}`,
        { method: 'GET' },
      )

      setJobs(data.items)
      setTotalItems(data.totalItems)
      setTotalPages(data.totalPages)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load scheduled tasks.')
      setJobs([])
      setTotalItems(0)
      setTotalPages(0)
    } finally {
      setIsLoading(false)
    }
  }

  async function selectJob(jobId: string) {
    setIsDetailLoading(true)
    setIsSheetOpen(true)

    try {
      const job = await request<ScheduledTask>(`/internal-api/v1/scheduled-tasks/${jobId}`, {
        method: 'GET',
      })
      setSelectedJob(job)
    } catch (loadError) {
      setSelectedJob(null)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load job details.')
    } finally {
      setIsDetailLoading(false)
    }
  }

  async function handleDelete(jobId: string) {
    setDeletingId(jobId)

    try {
      await request<void>(`/internal-api/v1/scheduled-tasks/${jobId}`, {
        method: 'DELETE',
      })

      const shouldMoveToPreviousPage = page > 0 && jobs.length === 1
      if (shouldMoveToPreviousPage) {
        setPage((current) => current - 1)
      } else {
        await loadPage(page, query)
      }

      setSelectedJob(null)
      setIsSheetOpen(false)
      toast.success('Scheduled job deleted.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete scheduled job.')
    } finally {
      setDeletingId(null)
    }
  }

  async function handleToggleEnabled(job: ScheduledTask) {
    setTogglingId(job.id)

    try {
      const updatedJob = await request<ScheduledTask>(`/internal-api/v1/scheduled-tasks/${job.id}/enabled`, {
        method: 'PATCH',
        body: JSON.stringify({
          enabled: !job.enabled,
        }),
      })

      setJobs((current) =>
        current.map((item) => (item.id === updatedJob.id ? updatedJob : item)),
      )
      setSelectedJob((current) => (current?.id === updatedJob.id ? updatedJob : current))
      toast.success(updatedJob.enabled ? 'Scheduled job enabled.' : 'Scheduled job disabled.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update scheduled job state.')
    } finally {
      setTogglingId(null)
    }
  }

  const metrics = useMemo(() => {
    const enabledCount = jobs.filter((job) => job.enabled).length
    const failingCount = jobs.filter((job) => Boolean(job.lastError?.trim())).length
    return {
      enabledCount,
      failingCount,
    }
  }, [jobs])

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Cron Jobs</h2>
        </div>
      </header>

      <div className="flex flex-col md:flex-row justify-between md:items-center gap-4 mb-6">
        <div className="flex items-center gap-4">
          <h3 className="text-xl font-semibold">Scheduled Operations</h3>
          <span className="bg-primary/10 text-primary text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">{totalItems} total</span>
        </div>
      </div>

      <section className="grid gap-4 md:grid-cols-3">
        <div className="p-4 rounded-2xl border bg-card/50 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Total Jobs</span>
            <span className="text-2xl font-bold">{totalItems}</span>
          </div>
          <Clock3 className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-primary/10 group-hover:text-primary/20 transition-colors" />
        </div>
        <div className="p-4 rounded-2xl border bg-emerald-500/5 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-widest">Enabled</span>
            <span className="text-2xl font-bold text-emerald-700 dark:text-emerald-300">{metrics.enabledCount}</span>
          </div>
          <Activity className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-emerald-500/10 group-hover:text-emerald-500/20 transition-colors" />
        </div>
        <div className="p-4 rounded-2xl border bg-amber-500/5 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-amber-600 dark:text-amber-400 uppercase tracking-widest">Failing</span>
            <span className="text-2xl font-bold text-amber-700 dark:text-amber-300">{metrics.failingCount}</span>
          </div>
          <AlertTriangle className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-amber-500/10 group-hover:text-amber-500/20 transition-colors" />
        </div>
      </section>

      <section className="space-y-6">
        <div className="flex flex-col md:flex-row gap-4 items-center justify-between bg-muted/30 p-2 rounded-lg">
          <div className="w-full relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              className="h-10 pl-10 rounded-md w-full bg-background border-border/60 shadow-sm focus-visible:ring-primary/30"
              value={searchValue}
              onChange={(event) => setSearchValue(event.target.value)}
              placeholder="Search scheduled tasks..."
            />
          </div>
        </div>

        {isLoading ? (
          <div className="py-24 flex flex-col items-center justify-center space-y-4">
             <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
             <p className="text-muted-foreground text-sm font-medium animate-pulse">Syncing calendar system...</p>
          </div>
        ) : null}

        {!isLoading && jobs.length === 0 ? (
          <div className="py-16 flex flex-col items-center justify-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
            <div className="w-16 h-16 bg-muted/50 rounded-full flex items-center justify-center mb-4">
              <span className="text-2xl opacity-50">⏰</span>
            </div>
            <h4 className="text-lg font-semibold mb-2">No scheduled jobs</h4>
            <p className="text-muted-foreground text-sm max-w-md">Try adjusting your search query or check if server-side scheduling is enabled.</p>
          </div>
        ) : null}

        {!isLoading && jobs.length > 0 ? (
          <ItemGroup aria-label="Cron job list">
            {jobs.map((job) => (
              <Item
                key={job.id}
                variant="outline"
                className="cursor-pointer"
                role="button"
                tabIndex={0}
                onClick={() => void selectJob(job.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    void selectJob(job.id)
                  }
                }}
              >
                <ItemContent>
                  <ItemHeader>
                    <ItemTitle>{job.taskName}</ItemTitle>
                    <div className="flex gap-2 shrink-0">
                      <Badge variant={job.enabled ? 'default' : 'secondary'} className={job.enabled ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20' : ''}>
                        {job.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                      <Badge variant="outline">{job.taskType}</Badge>
                    </div>
                  </ItemHeader>
                  <ItemDescription className="font-mono text-[10px] text-muted-foreground/70 mb-2 uppercase tracking-tight">{job.id}</ItemDescription>
                  <ItemDescription>
                    {job.cronExpression} · Next run: {formatDate(job.nextRunAt)}
                  </ItemDescription>
                  <ItemDescription className="mt-2 text-[10px] flex items-center gap-1.5 uppercase font-bold tracking-tight">
                    <Activity className={`h-3 w-3 ${job.lastError ? 'text-destructive' : 'text-emerald-500'}`} />
                    Last run: {formatDate(job.lastStartedAt)} {job.lastError ? '· FAILED' : ''}
                  </ItemDescription>
                </ItemContent>
              </Item>
            ))}
          </ItemGroup>
        ) : null}

        <div className="flex items-center justify-between text-sm pt-4">
          <span className="text-muted-foreground font-medium">
            {totalItems === 0
              ? 'Showing 0 jobs'
              : `Showing ${page * pageSize + 1} to ${Math.min((page + 1) * pageSize, totalItems)} of ${totalItems}`}
          </span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={page === 0 || isLoading}
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
              disabled={isLoading || totalPages === 0 || page >= totalPages - 1}
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
              <p className="text-[10px] uppercase tracking-widest text-primary font-bold mb-2 text-muted-foreground">Internal Scheduling</p>
              <SheetTitle className="text-2xl mt-1 tracking-tight">
                {selectedJob?.taskName || 'Job Details'}
              </SheetTitle>
              <SheetDescription className="text-sm text-foreground/60 leading-relaxed mt-2 max-w-lg">
                Deeply inspect the scheduled execution cycle. Monitor lifecycle states, verify cron expressions, and manage server-side artifacts.
              </SheetDescription>
            </SheetHeader>
          </div>

          <div className="p-6 md:p-8 flex-1">
            {isDetailLoading ? (
              <div className="space-y-6">
                <Skeleton className="h-20 w-full rounded-xl" />
                <Skeleton className="h-40 w-full rounded-xl" />
              </div>
            ) : null}

            {selectedJob && !isDetailLoading ? (
              <div className="space-y-8 animate-in fade-in duration-300">
                <section className="bg-muted/20 p-4 rounded-xl border border-border/40 inline-flex flex-wrap items-center gap-2">
                  <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><span className={`w-2 h-2 rounded-full ${selectedJob.enabled ? 'bg-emerald-500' : 'bg-muted-foreground'}`}></span> {selectedJob.enabled ? 'Enabled' : 'Disabled'}</Badge>
                  <Badge variant="outline" className="bg-background shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500"></span> {selectedJob.taskType}</Badge>
                  <Badge variant="outline" className={`shadow-sm px-3 py-1 text-xs font-semibold flex items-center gap-1.5 ${selectedJob.lastError ? 'bg-destructive/10 text-destructive border-destructive/20' : 'bg-emerald-500/10 text-emerald-700 border-emerald-500/20'}`}><span className={`w-2 h-2 rounded-full ${selectedJob.lastError ? 'bg-destructive' : 'bg-emerald-500'}`}></span> {selectedJob.lastError ? 'Failing' : 'Healthy'}</Badge>
                </section>

                <section className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 flex items-center gap-2">Owner Identity</span>
                    <p className="font-mono text-sm break-all text-primary/80">{selectedJob.userId}</p>
                  </article>
                  <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Cron Expression</span>
                    <p className="font-mono text-sm text-primary">{selectedJob.cronExpression}</p>
                  </article>
                  <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Next Scheduled Run</span>
                    <p className="text-sm font-medium">{formatDate(selectedJob.nextRunAt)}</p>
                  </article>
                  <article className="p-4 rounded-xl bg-card border shadow-sm hover:shadow-md transition-shadow">
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1.5 block">Last Completion</span>
                    <p className="text-sm font-medium">{formatDate(selectedJob.lastCompletedAt)}</p>
                  </article>
                  {selectedJob.lastError && (
                    <article className="p-4 rounded-xl border border-destructive/20 bg-destructive/5 shadow-sm col-span-1 sm:col-span-2">
                      <span className="text-[10px] font-bold text-destructive uppercase tracking-widest mb-1.5 flex items-center gap-2"><AlertTriangle className="h-3 w-3" /> System Error Artifact</span>
                      <p className="text-sm font-medium text-destructive/90 italic leading-relaxed">{selectedJob.lastError}</p>
                      <p className="mt-2 text-[10px] text-destructive/60 font-mono uppercase">Failed at {formatDate(selectedJob.lastFailureAt)}</p>
                    </article>
                  )}
                </section>

                <section className="rounded-xl border shadow-sm overflow-hidden flex flex-col bg-card">
                  <div className="bg-muted/40 px-5 py-4 flex items-center justify-between border-b">
                    <span className="text-sm font-bold flex items-center gap-2"><Zap className="h-4 w-4" /> Task Payload</span>
                  </div>
                  <div className="p-5 overflow-x-auto">
                    <pre className="text-[13px] leading-relaxed font-mono text-foreground/80 whitespace-pre-wrap break-words">
                      {selectedJob.taskDetails || 'No auxiliary payload defined for this schedule.'}
                    </pre>
                  </div>
                </section>
              </div>
            ) : !isDetailLoading && (
              <div className="text-muted-foreground text-center py-12 border-2 border-dashed rounded-xl">Select a scheduled job artifact to inspect execution payload.</div>
            )}
          </div>

          {selectedJob ? (
            <SheetFooter className="p-6 md:p-8 border-t bg-muted/10 shrink-0 flex flex-row items-center justify-end gap-3 shadow-[0_-10px_20px_rgba(0,0,0,0.02)]">
              <Button
                variant={selectedJob.enabled ? 'outline' : 'default'}
                disabled={togglingId === selectedJob.id}
                onClick={() => void handleToggleEnabled(selectedJob)}
                className="shadow-sm gap-2"
              >
                {togglingId === selectedJob.id
                  ? selectedJob.enabled
                    ? 'Disabling...'
                    : 'Enabling...'
                  : selectedJob.enabled
                    ? 'Disable Schedule'
                    : 'Enable Schedule'}
              </Button>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive" disabled={deletingId === selectedJob.id} className="shadow-sm gap-2">
                    <Trash2 className="h-4 w-4" />
                    {deletingId === selectedJob.id ? 'Deleting...' : 'Delete Schedule'}
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This action cannot be undone. This will permanently remove the scheduled task <span className="font-semibold text-foreground">{selectedJob.taskName}</span> from the system.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooterUI>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={() => void handleDelete(selectedJob.id)} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">Delete Job</AlertDialogAction>
                  </AlertDialogFooterUI>
                </AlertDialogContent>
              </AlertDialog>
            </SheetFooter>
          ) : null}
        </SheetContent>
      </Sheet>

      <Toaster richColors position="top-right" />
    </div>
  )
}
