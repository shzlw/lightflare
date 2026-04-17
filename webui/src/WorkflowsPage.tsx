import { useEffect, useMemo, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemHeader,
  ItemTitle,
} from '@/components/ui/item'
import { Input } from '@/components/ui/input'
import { executeWorkflow, listWorkflows, type Workflow } from '@/lib/api'
import { Plus, Clock, ExternalLink, Play, GitBranch, Activity, Search, Workflow as WorkflowIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'

export default function WorkflowsPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [runningWorkflowId, setRunningWorkflowId] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  useEffect(() => {
    void loadWorkflows()
  }, [])

  async function loadWorkflows() {
    setIsLoading(true)
    try {
      const data = await listWorkflows()
      setWorkflows(data)
    } catch (err) {
      toast.error('Failed to load workflows')
    } finally {
      setIsLoading(false)
    }
  }

  async function runWorkflow(workflowId: string) {
    setRunningWorkflowId(workflowId)
    try {
      const result = await executeWorkflow(workflowId)
      toast.success(`Workflow started: ${result.executionId}`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to start workflow')
    } finally {
      setRunningWorkflowId(null)
    }
  }

  const workflowStats = useMemo(() => {
    return workflows.reduce(
      (stats, workflow) => {
        try {
          const schema = JSON.parse(workflow.schemaDefinition || '{}')
          const steps = Array.isArray(schema.steps) ? schema.steps : []
          stats.steps += steps.length
          if (steps.some((step: any) => step?.type?.toUpperCase() === 'TRIGGER')) {
            stats.triggered += 1
          }
        } catch {
          stats.invalid += 1
        }
        return stats
      },
      { steps: 0, triggered: 0, invalid: 0 },
    )
  }, [workflows])

  const filteredWorkflows = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    if (!normalizedSearch) return workflows
    return workflows.filter((workflow) =>
      [workflow.name, workflow.description, workflow.id]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(normalizedSearch),
    )
  }, [search, workflows])

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Workflows</h2>
        </div>
      </header>

      <div className="flex flex-col md:flex-row justify-between md:items-center gap-4 mb-6">
        <div className="flex items-center gap-4">
          <h3 className="text-xl font-semibold">Automation Library</h3>
          <span className="bg-primary/10 text-primary text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">{workflows.length} total</span>
        </div>
        <Button asChild>
          <NavLink to="/workspace/workflows/new" className="gap-2">
            <Plus className="h-4 w-4" /> New Workflow
          </NavLink>
        </Button>
      </div>

      <section className="grid gap-4 md:grid-cols-3">
        <div className="p-4 rounded-2xl border bg-card/50 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Total Workflows</span>
            <span className="text-2xl font-bold">{workflows.length}</span>
          </div>
          <WorkflowIcon className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-primary/10 group-hover:text-primary/20 transition-colors" />
        </div>
        <div className="p-4 rounded-2xl border bg-emerald-500/5 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-widest">Steps</span>
            <span className="text-2xl font-bold text-emerald-700 dark:text-emerald-300">{workflowStats.steps}</span>
          </div>
          <GitBranch className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-emerald-500/10 group-hover:text-emerald-500/20 transition-colors" />
        </div>
        <div className="p-4 rounded-2xl border bg-blue-500/5 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-blue-600 dark:text-blue-400 uppercase tracking-widest">Triggered</span>
            <span className="text-2xl font-bold text-blue-700 dark:text-blue-300">{workflowStats.triggered}</span>
          </div>
          <Activity className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-blue-500/10 group-hover:text-blue-500/20 transition-colors" />
        </div>
      </section>

      <div className="flex flex-col gap-4 items-center justify-between bg-muted/30 p-2 rounded-lg">
        <div className="w-full relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            className="h-10 pl-10 rounded-md w-full bg-background border-border/60 shadow-sm focus-visible:ring-primary/30"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search workflows by name, description, or id..."
          />
        </div>
      </div>

      <ItemGroup aria-label="Workflow list">
        {isLoading ? (
          <div className="py-24 flex flex-col items-center justify-center space-y-4">
            <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
            <p className="text-muted-foreground text-sm font-medium animate-pulse">Loading workflow definitions...</p>
          </div>
        ) : filteredWorkflows.length === 0 ? (
          <div className="py-16 flex flex-col items-center justify-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
            <div className="w-16 h-16 bg-muted/50 rounded-full flex items-center justify-center mb-4">
              <WorkflowIcon className="h-7 w-7 text-muted-foreground" />
            </div>
            <h4 className="text-lg font-semibold mb-2">No workflows found</h4>
            <p className="text-muted-foreground text-sm max-w-md mb-4">Create a workflow from chat or adjust your search query.</p>
            <Button asChild variant="outline">
              <NavLink to="/workspace/chats">Go to Chat</NavLink>
            </Button>
          </div>
        ) : (
          filteredWorkflows.map((wf) => (
            <Item key={wf.id} variant="outline" className="group hover:shadow-sm transition-shadow">
              <ItemContent className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex-1">
                  <ItemHeader>
                    <ItemTitle className="flex items-center gap-2">
                      {wf.name}
                      <Badge variant="secondary" className="text-[10px] font-bold">V1</Badge>
                    </ItemTitle>
                  </ItemHeader>
                  <ItemDescription>{wf.description || 'No description provided.'}</ItemDescription>
                  <ItemDescription className="font-mono text-[10px] text-muted-foreground/70 mt-1 uppercase tracking-tight">{wf.id}</ItemDescription>
                  <div className="mt-3 flex items-center gap-4 text-[10px] font-bold text-muted-foreground uppercase tracking-tight">
                    <span className="flex items-center gap-1.5"><Clock className="h-3 w-3" /> Updated {new Date(wf.updatedAt).toLocaleDateString()}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="gap-2 md:opacity-0 md:group-hover:opacity-100 transition-opacity"
                    disabled={runningWorkflowId === wf.id}
                    onClick={() => void runWorkflow(wf.id)}
                  >
                    <Play className="h-3 w-3" /> {runningWorkflowId === wf.id ? 'Running' : 'Run'}
                  </Button>
                  <Button asChild variant="outline" size="sm" className="gap-2">
                    <NavLink to={`/workspace/workflows/${wf.id}`}>
                      <ExternalLink className="h-3 w-3" /> Open Designer
                    </NavLink>
                  </Button>
                </div>
              </ItemContent>
            </Item>
          ))
        )}
      </ItemGroup>
      <Toaster richColors position="top-right" />
    </div>
  )
}
