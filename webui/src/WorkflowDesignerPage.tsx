import { useEffect, useMemo, useState } from 'react'
import { NavLink, useParams } from 'react-router-dom'
import {
  executeWorkflow,
  getExecution,
  getExecutionSteps,
  getWorkflow,
  type Workflow,
  type WorkflowExecution,
  type WorkflowStepExecution,
} from '@/lib/api'
import WorkflowGraph from './components/WorkflowGraph'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Activity, ChevronLeft, MessageSquare, Play, Save, Settings2, Workflow as WorkflowIcon } from 'lucide-react'

type WorkflowStep = {
  stepId: string
  type?: string
  actionIdentifier?: string
  inputMapping?: Record<string, unknown>
  outputMapping?: Record<string, unknown>
  transitions?: Array<{ conditionExpression: string; targetStepId: string }>
}

function formatJson(value: string | null) {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function formatObject(value: unknown) {
  if (!value) return 'Not set'
  return JSON.stringify(value, null, 2)
}

function statusClass(status?: string) {
  if (status === 'FAILED') {
    return 'bg-destructive/10 text-destructive border-destructive/20'
  }
  if (status === 'COMPLETED' || status === 'SUCCESS') {
    return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/20'
  }
  return 'bg-primary/10 text-primary border-primary/20'
}

export default function WorkflowDesignerPage() {
  const { id } = useParams<{ id: string }>()
  const [workflow, setWorkflow] = useState<Workflow | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null)
  const [execution, setExecution] = useState<WorkflowExecution | null>(null)
  const [executionSteps, setExecutionSteps] = useState<WorkflowStepExecution[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const [chatDraft, setChatDraft] = useState('')

  useEffect(() => {
    if (id && id !== 'new') {
      void loadWorkflow(id)
    } else {
      setIsLoading(false)
    }
  }, [id])

  async function loadWorkflow(workflowId: string) {
    setIsLoading(true)
    try {
      const data = await getWorkflow(workflowId)
      setWorkflow(data)
    } catch (err) {
      toast.error('Failed to load workflow definition')
    } finally {
      setIsLoading(false)
    }
  }

  const steps = useMemo<WorkflowStep[]>(() => {
    if (!workflow?.schemaDefinition) return []
    try {
      const schema = JSON.parse(workflow.schemaDefinition)
      return Array.isArray(schema.steps) ? schema.steps : []
    } catch (e) {
      console.error('Failed to parse schema', e)
      return []
    }
  }, [workflow])

  const selectedStep = useMemo(
    () => steps.find((step) => step.stepId === selectedStepId),
    [selectedStepId, steps],
  )

  async function refreshExecution(executionId: string) {
    const [latestExecution, latestSteps] = await Promise.all([
      getExecution(executionId),
      getExecutionSteps(executionId),
    ])
    setExecution(latestExecution)
    setExecutionSteps(latestSteps)
    return latestExecution
  }

  async function runWorkflow() {
    if (!workflow || isRunning) return
    setIsRunning(true)
    setExecution(null)
    setExecutionSteps([])
    try {
      const result = await executeWorkflow(workflow.id)
      const latestExecution = await refreshExecution(result.executionId)
      toast.success(`Workflow ${latestExecution.status.toLowerCase()}`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to run workflow')
    } finally {
      setIsRunning(false)
    }
  }

  if (isLoading) {
    return (
      <div className="w-full p-6 md:p-8">
        <div className="py-24 flex flex-col items-center justify-center space-y-4">
          <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-muted-foreground text-sm font-medium animate-pulse">Loading workflow designer...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="w-full min-h-0 flex flex-col gap-4 p-4 md:p-6 animate-in fade-in duration-500">
      <header className="flex flex-col gap-4 shrink-0">
        <Button asChild variant="ghost" size="sm" className="w-fit gap-2 -ml-2">
          <NavLink to="/workspace/workflows">
            <ChevronLeft className="h-4 w-4" /> Workflows
          </NavLink>
        </Button>

        <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-3 min-w-0">
              <h2 className="text-2xl md:text-3xl font-bold tracking-tight text-foreground truncate">{workflow?.name || 'Untitled Workflow'}</h2>
              <Badge variant="secondary" className="text-[10px] font-bold shrink-0">Designer</Badge>
            </div>
            <p className="text-muted-foreground mt-2 max-w-4xl">{workflow?.description || 'No description provided.'}</p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <Button variant="outline" size="sm" className="h-9 gap-2 shadow-sm" disabled title="Manual graph editing is not wired yet.">
              <Save className="h-3.5 w-3.5" /> Save
            </Button>
            <Button size="sm" className="h-9 gap-2 shadow-sm" disabled={!workflow || isRunning} onClick={() => void runWorkflow()}>
              <Play className="h-3.5 w-3.5" /> {isRunning ? 'Running' : 'Run'}
            </Button>
          </div>
        </div>
      </header>

      <section className="grid min-h-[560px] flex-1 gap-4 xl:grid-cols-[minmax(0,1.35fr)_360px_400px] lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="rounded-xl border bg-card shadow-sm overflow-hidden min-h-[460px]">
          <div className="px-5 py-4 border-b bg-muted/30 flex items-center justify-between gap-4">
            <div>
              <h3 className="text-sm font-bold">Workflow Graph</h3>
              <p className="text-xs text-muted-foreground mt-1">Select a node to inspect the step.</p>
            </div>
            <Badge variant="outline" className="bg-background shadow-sm">{steps.length} steps</Badge>
          </div>
          <div className="h-[520px] xl:h-[620px]">
            <WorkflowGraph
              steps={steps}
              activeStepId={selectedStepId || undefined}
              onStepClick={setSelectedStepId}
            />
          </div>
        </div>

        <section className="rounded-xl border bg-card shadow-sm overflow-hidden min-h-[460px]">
          <div className="px-5 py-4 border-b bg-muted/30 flex items-center justify-between">
            <h3 className="text-sm font-bold">Step Details</h3>
            {selectedStepId ? (
              <Button variant="ghost" size="sm" className="h-8" onClick={() => setSelectedStepId(null)}>Clear</Button>
            ) : null}
          </div>
          <div className="p-5 h-[520px] xl:h-[620px] overflow-auto">
            {selectedStep ? (
              <div className="space-y-4 animate-in fade-in duration-300">
                <div className="p-4 rounded-xl bg-muted/20 border border-border/40">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest block mb-1">Step ID</span>
                  <p className="text-sm font-mono break-all">{selectedStep.stepId}</p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-4 rounded-xl bg-card border shadow-sm">
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest block mb-1">Type</span>
                    <p className="text-sm font-medium">{selectedStep.type || 'TOOL'}</p>
                  </div>
                  <div className="p-4 rounded-xl bg-card border shadow-sm">
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest block mb-1">Routes</span>
                    <p className="text-sm font-medium">{selectedStep.transitions?.length || 0}</p>
                  </div>
                </div>
                <div className="p-4 rounded-xl bg-card border shadow-sm">
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest block mb-1">Action</span>
                  <p className="text-sm font-mono break-all text-primary/80">{selectedStep.actionIdentifier || 'none'}</p>
                </div>
                <details className="rounded-xl border bg-card shadow-sm overflow-hidden">
                  <summary className="cursor-pointer px-4 py-3 text-sm font-medium bg-muted/30">Input Mapping</summary>
                  <pre className="p-4 text-xs overflow-auto whitespace-pre-wrap">{formatObject(selectedStep.inputMapping)}</pre>
                </details>
                <details className="rounded-xl border bg-card shadow-sm overflow-hidden">
                  <summary className="cursor-pointer px-4 py-3 text-sm font-medium bg-muted/30">Transitions</summary>
                  <pre className="p-4 text-xs overflow-auto whitespace-pre-wrap">{formatObject(selectedStep.transitions)}</pre>
                </details>
                <div className="p-4 rounded-xl bg-muted/20 border border-border/40 text-center">
                  <Settings2 className="h-7 w-7 text-muted-foreground mx-auto mb-3" />
                  <p className="text-xs text-muted-foreground leading-relaxed">Manual editing is not wired yet. Use chat to update step logic.</p>
                </div>
              </div>
            ) : (
              <div className="h-full flex items-center justify-center text-center border-2 border-dashed rounded-xl bg-muted/10">
                <div>
                  <WorkflowIcon className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
                  <p className="text-sm font-medium">No step selected</p>
                  <p className="text-xs text-muted-foreground mt-1">Choose a node from the graph.</p>
                </div>
              </div>
            )}
          </div>
        </section>

        <section className="rounded-xl border bg-card shadow-sm overflow-hidden min-h-[460px] lg:col-span-2 xl:col-span-1">
          <div className="px-5 py-4 border-b bg-muted/30 flex items-center gap-2">
            <MessageSquare className="h-4 w-4 text-muted-foreground" />
            <h3 className="text-sm font-bold">Workflow Chat</h3>
          </div>
          <div className="h-[520px] xl:h-[620px] flex flex-col">
            <div className="flex-1 overflow-auto p-5 space-y-4">
              <div className="rounded-xl border bg-muted/20 p-4">
                <p className="text-sm font-medium">Ask for workflow changes</p>
                <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                  Describe the graph, step, or execution behavior you want changed.
                </p>
              </div>
              <div className="rounded-xl border bg-card p-4 shadow-sm">
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Chat wiring is handled by the workspace assistant. This panel reserves the designer space for workflow-specific edits.
                </p>
              </div>
            </div>
            <div className="border-t bg-muted/20 p-4 space-y-3">
              <textarea
                className="min-h-24 w-full resize-none rounded-md border bg-background px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
                value={chatDraft}
                onChange={(event) => setChatDraft(event.target.value)}
                placeholder="Example: add a step to extract article text before summarizing..."
              />
              <Button className="w-full" disabled={!chatDraft.trim()}>
                Send
              </Button>
            </div>
          </div>
        </section>
      </section>

      <section className="rounded-xl border bg-card shadow-sm overflow-hidden shrink-0">
        <div className="px-5 py-4 border-b bg-muted/30 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Activity className="h-4 w-4 text-muted-foreground" />
            <h3 className="text-sm font-bold">Execution Logs</h3>
          </div>
          {execution ? <Badge variant="outline" className={statusClass(execution.status)}>{execution.status}</Badge> : null}
        </div>
        <div className="p-5 max-h-[320px] overflow-auto space-y-4">
          {!execution ? (
            <div className="py-10 text-center border-2 border-dashed rounded-xl bg-muted/10">
              <Activity className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
              <p className="text-sm font-medium">Ready to run</p>
              <p className="text-xs text-muted-foreground mt-1">Run the workflow to inspect step inputs, outputs, and failures.</p>
            </div>
          ) : executionSteps.length === 0 ? (
            <p className="text-xs text-muted-foreground italic">No step logs recorded.</p>
          ) : (
            executionSteps.map((step) => (
              <div key={step.id} className="rounded-xl border bg-card p-4 space-y-3 shadow-sm">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-mono truncate">{step.stepId}</p>
                  <Badge variant="outline" className={statusClass(step.status)}>{step.status}</Badge>
                </div>
                {step.errorMessage && (
                  <p className="text-xs text-destructive leading-relaxed">{step.errorMessage}</p>
                )}
                <div className="grid gap-3 lg:grid-cols-2">
                  {step.inputData && (
                    <details className="text-xs text-muted-foreground">
                      <summary className="cursor-pointer font-medium">Input</summary>
                      <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-muted p-3 text-foreground/80">{formatJson(step.inputData)}</pre>
                    </details>
                  )}
                  {step.outputData && (
                    <details className="text-xs text-muted-foreground">
                      <summary className="cursor-pointer font-medium">Output</summary>
                      <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-muted p-3 text-foreground/80">{formatJson(step.outputData)}</pre>
                    </details>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </section>

      <Toaster richColors position="top-right" />
    </div>
  )
}
