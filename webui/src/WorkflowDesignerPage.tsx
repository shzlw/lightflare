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
import { Activity, ChevronLeft, GitBranch, Play, Save, Settings2, Workflow as WorkflowIcon } from 'lucide-react'

type WorkflowStep = {
  stepId: string
  type?: string
  actionIdentifier?: string
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

  const transitionCount = useMemo(
    () => steps.reduce((count, step) => count + (step.transitions?.length || 0), 0),
    [steps],
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
      <div className="w-full max-w-7xl mx-auto p-6 md:p-8">
        <div className="py-24 flex flex-col items-center justify-center space-y-4">
          <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-muted-foreground text-sm font-medium animate-pulse">Loading workflow designer...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-5">
        <Button asChild variant="ghost" size="sm" className="w-fit gap-2 -ml-2">
          <NavLink to="/workspace/workflows">
            <ChevronLeft className="h-4 w-4" /> Workflows
          </NavLink>
        </Button>

        <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
          <div className="space-y-2">
            <div className="flex items-center gap-3">
              <h2 className="text-3xl font-bold tracking-tight text-foreground">{workflow?.name || 'Untitled Workflow'}</h2>
              <Badge variant="secondary" className="text-[10px] font-bold">Design</Badge>
            </div>
            <p className="text-muted-foreground max-w-3xl">{workflow?.description || 'No description provided.'}</p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" className="h-9 gap-2 shadow-sm" disabled title="Manual graph editing is not wired yet.">
              <Save className="h-3.5 w-3.5" /> Save
            </Button>
            <Button size="sm" className="h-9 gap-2 shadow-sm" disabled={!workflow || isRunning} onClick={() => void runWorkflow()}>
              <Play className="h-3.5 w-3.5" /> {isRunning ? 'Running' : 'Run'}
            </Button>
          </div>
        </div>
      </header>

      <section className="grid gap-4 md:grid-cols-3">
        <div className="p-4 rounded-2xl border bg-card/50 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Steps</span>
            <span className="text-2xl font-bold">{steps.length}</span>
          </div>
          <WorkflowIcon className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-primary/10 group-hover:text-primary/20 transition-colors" />
        </div>
        <div className="p-4 rounded-2xl border bg-emerald-500/5 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-widest">Transitions</span>
            <span className="text-2xl font-bold text-emerald-700 dark:text-emerald-300">{transitionCount}</span>
          </div>
          <GitBranch className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-emerald-500/10 group-hover:text-emerald-500/20 transition-colors" />
        </div>
        <div className="p-4 rounded-2xl border bg-blue-500/5 shadow-sm relative overflow-hidden group hover:shadow-md transition-all">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-blue-600 dark:text-blue-400 uppercase tracking-widest">Last Run</span>
            <span className="text-2xl font-bold text-blue-700 dark:text-blue-300">{execution?.status || 'Ready'}</span>
          </div>
          <Activity className="absolute right-4 top-1/2 -translate-y-1/2 h-8 w-8 text-blue-500/10 group-hover:text-blue-500/20 transition-colors" />
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b bg-muted/30 flex items-center justify-between gap-4">
            <div>
              <h3 className="text-sm font-bold">Workflow Graph</h3>
              <p className="text-xs text-muted-foreground mt-1">Select a step to inspect its configuration.</p>
            </div>
            <Badge variant="outline" className="bg-background shadow-sm">{steps.length} steps</Badge>
          </div>
          <div className="h-[620px]">
            <WorkflowGraph
              steps={steps}
              activeStepId={selectedStepId || undefined}
              onStepClick={setSelectedStepId}
            />
          </div>
        </div>

        <div className="space-y-6">
          <section className="rounded-xl border bg-card shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b bg-muted/30 flex items-center justify-between">
              <h3 className="text-sm font-bold">Step Details</h3>
              {selectedStepId ? (
                <Button variant="ghost" size="sm" className="h-8" onClick={() => setSelectedStepId(null)}>Clear</Button>
              ) : null}
            </div>
            <div className="p-5">
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
                  <div className="p-4 rounded-xl bg-muted/20 border border-border/40 text-center">
                    <Settings2 className="h-7 w-7 text-muted-foreground mx-auto mb-3" />
                    <p className="text-xs text-muted-foreground leading-relaxed">Manual editing is not wired yet. Use chat to update step logic.</p>
                  </div>
                </div>
              ) : (
                <div className="py-12 text-center border-2 border-dashed rounded-xl bg-muted/10">
                  <WorkflowIcon className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
                  <p className="text-sm font-medium">No step selected</p>
                  <p className="text-xs text-muted-foreground mt-1">Choose a node from the graph.</p>
                </div>
              )}
            </div>
          </section>

          <section className="rounded-xl border bg-card shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b bg-muted/30 flex items-center justify-between">
              <h3 className="text-sm font-bold">Execution</h3>
              {execution ? <Badge variant="outline" className={statusClass(execution.status)}>{execution.status}</Badge> : null}
            </div>
            <div className="p-5 max-h-[520px] overflow-auto space-y-4">
              {!execution ? (
                <div className="py-12 text-center border-2 border-dashed rounded-xl bg-muted/10">
                  <Activity className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
                  <p className="text-sm font-medium">Ready to run</p>
                  <p className="text-xs text-muted-foreground mt-1">Run the workflow to inspect step inputs, outputs, and failures.</p>
                </div>
              ) : (
                <>
                  <div className="rounded-xl border bg-muted/20 p-4">
                    <p className="text-[10px] uppercase font-bold text-muted-foreground mb-1">Execution ID</p>
                    <p className="text-xs font-mono break-all">{execution.id}</p>
                  </div>

                  {executionSteps.length === 0 ? (
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
                        {step.inputData && (
                          <details className="text-xs text-muted-foreground">
                            <summary className="cursor-pointer font-medium">Input</summary>
                            <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-lg bg-muted p-3 text-foreground/80">{formatJson(step.inputData)}</pre>
                          </details>
                        )}
                        {step.outputData && (
                          <details className="text-xs text-muted-foreground">
                            <summary className="cursor-pointer font-medium">Output</summary>
                            <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-lg bg-muted p-3 text-foreground/80">{formatJson(step.outputData)}</pre>
                          </details>
                        )}
                      </div>
                    ))
                  )}
                </>
              )}
            </div>
          </section>
        </div>
      </section>

      <Toaster richColors position="top-right" />
    </div>
  )
}
