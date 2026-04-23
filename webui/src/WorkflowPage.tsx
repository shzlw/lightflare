import { useEffect, useMemo, useState } from 'react'
import { NavLink, useNavigate, useParams } from 'react-router-dom'
import {
  deleteWorkflow,
  executeWorkflowStream,
  executeWorkflowTriggerStream,
  getExecutionSteps,
  getWorkflow,
  listWorkflowRuns,
  listWorkflows,
  listWorkflowTriggers,
  setWorkflowEnabled,
  updateWorkflowTrigger,
  type Workflow,
  type WorkflowExecution,
  type WorkflowStepExecution,
  type WorkflowStreamEvent,
  type WorkflowTrigger,
} from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemHeader,
  ItemTitle,
} from '@/components/ui/item'
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  MessageSquare,
  Play,
  Search,
  Trash2,
  Workflow as WorkflowIcon,
} from 'lucide-react'

type WorkflowStep = {
  id?: string
  stepId?: string
  name?: string
  type?: string
  toolName?: string
  actionIdentifier?: string
  prompt?: string
  input?: Record<string, unknown>
  inputMapping?: Record<string, unknown>
  output?: Record<string, unknown>
  outputMapping?: Record<string, unknown>
  onError?: string
}

type WorkflowDefinition = {
  version?: number
  inputs?: Array<Record<string, unknown>>
  steps?: WorkflowStep[]
  triggers?: Array<Record<string, unknown>>
}

type ManualInputField = {
  name?: string
  label?: string
  type?: string
  required?: boolean
  default?: unknown
  description?: string
}

type WorkflowInputField = ManualInputField

function titleFor(workflow: Workflow | null) {
  return workflow?.name?.trim() || 'Untitled workflow'
}

function definitionJson(workflow: Workflow | null) {
  return workflow?.schemaDefinition || workflow?.definitionJson || ''
}

function parseDefinition(workflow: Workflow | null): WorkflowDefinition {
  const raw = definitionJson(workflow)
  if (!raw) return { version: 1, inputs: [], steps: [], triggers: [] }
  try {
    const parsed = JSON.parse(raw) as WorkflowDefinition
    return {
      version: parsed.version ?? 1,
      inputs: Array.isArray(parsed.inputs) ? parsed.inputs : [],
      steps: Array.isArray(parsed.steps) ? parsed.steps : [],
      triggers: Array.isArray(parsed.triggers) ? parsed.triggers : [],
    }
  } catch {
    return { version: 1, inputs: [], steps: [], triggers: [] }
  }
}

function formatJson(value: unknown) {
  if (value === undefined || value === null || value === '') return 'Not set'
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  return JSON.stringify(value, null, 2)
}

function statusClass(status?: string | null) {
  const normalized = status?.toLowerCase()
  if (normalized === 'failed' || normalized === 'disabled') return 'bg-destructive/10 text-destructive border-destructive/20'
  if (normalized === 'completed' || normalized === 'active' || normalized === 'success') {
    return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/20'
  }
  return 'bg-primary/10 text-primary border-primary/20'
}

function isWorkflowDisabled(workflow: Workflow | null) {
  return workflow?.status?.toLowerCase() === 'disabled'
}

function stepId(step: WorkflowStep, index: number) {
  return step.id || step.stepId || `step_${index + 1}`
}

function runOutput(run: WorkflowExecution) {
  return run.outputJson ?? run.outputData ?? null
}

function runInput(run: WorkflowExecution) {
  return run.inputJson ?? run.inputData ?? null
}

function stepOutput(step: WorkflowStepExecution) {
  return step.outputJson ?? step.outputData ?? null
}

function stepInput(step: WorkflowStepExecution) {
  return step.inputJson ?? step.inputData ?? null
}

function liveEventLabel(event: WorkflowStreamEvent) {
  switch (event.type) {
    case 'RUN_STARTED':
      return 'Run started'
    case 'STEP_STARTED':
      return 'Step started'
    case 'STEP_COMPLETED':
      return 'Step completed'
    case 'STEP_FAILED':
      return 'Step failed'
    case 'RUN_COMPLETED':
      return 'Run completed'
    case 'RUN_FAILED':
      return 'Run failed'
    default:
      return event.type
  }
}

function parseTriggerConfig(trigger: WorkflowTrigger | null): Record<string, unknown> {
  if (!trigger?.configJson) return {}
  try {
    const parsed = JSON.parse(trigger.configJson)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

function manualInputFields(trigger: WorkflowTrigger | null): ManualInputField[] {
  const config = parseTriggerConfig(trigger)
  const fields = config.inputFields
  return Array.isArray(fields) ? fields.filter((field): field is ManualInputField => !!field && typeof field === 'object') : []
}

function defaultInputsFromDefinition(definition: WorkflowDefinition): Record<string, unknown> {
  const inputs: Record<string, unknown> = {}
  for (const field of definition.inputs ?? []) {
    const name = typeof field.name === 'string' ? field.name.trim() : ''
    if (name && Object.prototype.hasOwnProperty.call(field, 'default')) {
      inputs[name] = field.default
    }
  }
  return inputs
}

function workflowInputFields(definition: WorkflowDefinition): WorkflowInputField[] {
  return (definition.inputs ?? []).filter((field): field is WorkflowInputField => !!field && typeof field === 'object')
}

function defaultInputsFromTrigger(trigger: WorkflowTrigger | null): Record<string, unknown> {
  const inputs: Record<string, unknown> = {}
  for (const field of manualInputFields(trigger)) {
    const name = field.name?.trim()
    if (name && Object.prototype.hasOwnProperty.call(field, 'default')) {
      inputs[name] = field.default
    }
  }
  return inputs
}

export default function WorkflowPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [workflow, setWorkflow] = useState<Workflow | null>(null)
  const [triggers, setTriggers] = useState<WorkflowTrigger[]>([])
  const [runs, setRuns] = useState<WorkflowExecution[]>([])
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null)
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null)
  const [runSteps, setRunSteps] = useState<WorkflowStepExecution[]>([])
  const [query, setQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isRunning, setIsRunning] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [updatingTriggerId, setUpdatingTriggerId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState('steps')
  const [liveEvents, setLiveEvents] = useState<WorkflowStreamEvent[]>([])
  const [workflowInputs, setWorkflowInputs] = useState<Record<string, unknown>>({})
  const [manualInputsByTriggerId, setManualInputsByTriggerId] = useState<Record<string, Record<string, unknown>>>({})

  const definition = useMemo(() => parseDefinition(workflow), [workflow])
  const selectedStep = useMemo(() => {
    return selectedStepId
      ? definition.steps?.find((step, index) => stepId(step, index) === selectedStepId) ?? null
      : null
  }, [definition.steps, selectedStepId])
  const selectedRun = selectedRunId ? runs.find((run) => run.id === selectedRunId) ?? null : null
  const workflowDisabled = isWorkflowDisabled(workflow)

  useEffect(() => {
    void loadWorkflows()
  }, [])

  useEffect(() => {
    if (id && id !== 'new') {
      void selectWorkflow(id, true)
    }
  }, [id])

  useEffect(() => {
    if (!selectedRun?.id) {
      setRunSteps([])
      return
    }
    void loadRunSteps(selectedRun.id)
  }, [selectedRun?.id])

  useEffect(() => {
    setWorkflowInputs(defaultInputsFromDefinition(definition))
  }, [workflow?.id, workflow?.schemaDefinition, workflow?.definitionJson])

  async function loadWorkflows() {
    setIsLoading(true)
    try {
      const data = await listWorkflows()
      setWorkflows(data)
      if (!id && data.length > 0) {
        navigate(`/workspace/workflows/${data[0].id}`, { replace: true })
      }
    } catch {
      setWorkflows([])
    } finally {
      setIsLoading(false)
    }
  }

  async function selectWorkflow(workflowId: string, resetTab = false) {
    setIsLoading(true)
    try {
      const [nextWorkflow, nextTriggers, nextRuns] = await Promise.all([
        getWorkflow(workflowId),
        listWorkflowTriggers(workflowId),
        listWorkflowRuns(workflowId, 20),
      ])
      setWorkflow(nextWorkflow)
      setTriggers(nextTriggers)
      setRuns(nextRuns)
      setSelectedRunId((current) => (resetTab ? null : nextRuns.some((run) => run.id === current) ? current : null))
      setSelectedStepId((current) => {
        if (resetTab) return null
        const nextDefinition = parseDefinition(nextWorkflow)
        return nextDefinition.steps?.some((step, index) => stepId(step, index) === current) ? current : null
      })
      if (resetTab) {
        setLiveEvents([])
        setActiveTab('steps')
      }
    } catch {
      setWorkflow(null)
      setTriggers([])
      setRuns([])
      setRunSteps([])
      setSelectedRunId(null)
      setSelectedStepId(null)
    } finally {
      setIsLoading(false)
    }
  }

  async function refreshSelectedWorkflow() {
    if (!workflow?.id) return
    await selectWorkflow(workflow.id)
  }

  async function loadRunSteps(runId: string) {
    try {
      setRunSteps(await getExecutionSteps(runId))
    } catch (err) {
      setRunSteps([])
    }
  }

  function handleWorkflowStreamEvent(event: WorkflowStreamEvent) {
    setLiveEvents((current) => [...current, event])
    if (event.executionId) {
      setSelectedRunId(event.executionId)
    }
  }

  async function runWorkflow() {
    if (!workflow?.id) return
    setIsRunning(true)
    setActiveTab('history')
    setLiveEvents([])
    setSelectedRunId(null)
    try {
      await executeWorkflowStream(workflow.id, {
        ...defaultInputsFromDefinition(definition),
        ...workflowInputs,
      }, undefined, handleWorkflowStreamEvent)
      await refreshSelectedWorkflow()
      setActiveTab('history')
    } catch {
      await refreshSelectedWorkflow()
      setActiveTab('history')
    } finally {
      setIsRunning(false)
    }
  }

  async function runWorkflowFromSelectedStep() {
    if (!workflow?.id || !selectedStep) return
    const startStepId = stepId(selectedStep, definition.steps?.indexOf(selectedStep) ?? 0)
    setIsRunning(true)
    setActiveTab('history')
    setLiveEvents([])
    setSelectedRunId(null)
    try {
      await executeWorkflowStream(workflow.id, {
        ...defaultInputsFromDefinition(definition),
        ...workflowInputs,
      }, startStepId, handleWorkflowStreamEvent)
      await refreshSelectedWorkflow()
      setActiveTab('history')
    } catch {
      await refreshSelectedWorkflow()
      setActiveTab('history')
    } finally {
      setIsRunning(false)
    }
  }

  async function runManualTrigger(trigger: WorkflowTrigger) {
    if (!workflow?.id) return
    setIsRunning(true)
    setActiveTab('history')
    setLiveEvents([])
    setSelectedRunId(null)
    try {
      await executeWorkflowTriggerStream(
        workflow.id,
        trigger.id,
        {
          ...defaultInputsFromDefinition(definition),
          ...workflowInputs,
          ...defaultInputsFromTrigger(trigger),
          ...(manualInputsByTriggerId[trigger.id] ?? {}),
        },
        undefined,
        handleWorkflowStreamEvent,
      )
      await refreshSelectedWorkflow()
      setActiveTab('history')
    } catch {
      await refreshSelectedWorkflow()
      setActiveTab('history')
    } finally {
      setIsRunning(false)
    }
  }

  function updateManualInput(triggerId: string, fieldName: string, value: unknown) {
    setManualInputsByTriggerId((current) => ({
      ...current,
      [triggerId]: {
        ...(current[triggerId] ?? {}),
        [fieldName]: value,
      },
    }))
  }

  function updateWorkflowInput(fieldName: string, value: unknown) {
    setWorkflowInputs((current) => ({
      ...current,
      [fieldName]: value,
    }))
  }

  async function toggleEnabled() {
    if (!workflow?.id) return
    const nextEnabled = workflow.status !== 'active'
    try {
      const updated = await setWorkflowEnabled(workflow.id, nextEnabled)
      setWorkflow(updated)
      setWorkflows((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    } catch {
      await refreshSelectedWorkflow()
    }
  }

  async function toggleTriggerEnabled(trigger: WorkflowTrigger) {
    if (!workflow?.id || updatingTriggerId) return
    const nextEnabled = !trigger.enabled
    setUpdatingTriggerId(trigger.id)
    try {
      if (nextEnabled && trigger.triggerType === 'scheduler' && workflow.status !== 'active') {
        const updatedWorkflow = await setWorkflowEnabled(workflow.id, true)
        setWorkflow(updatedWorkflow)
        setWorkflows((current) => current.map((item) => (item.id === updatedWorkflow.id ? updatedWorkflow : item)))
      }
      const updatedTrigger = await updateWorkflowTrigger(workflow.id, trigger.id, {
        triggerType: trigger.triggerType,
        name: trigger.name,
        enabled: nextEnabled,
        configJson: trigger.configJson ?? undefined,
      })
      setTriggers((current) => current.map((item) => (item.id === updatedTrigger.id ? updatedTrigger : item)))
    } catch {
      await refreshSelectedWorkflow()
    } finally {
      setUpdatingTriggerId(null)
    }
  }

  async function removeWorkflow() {
    if (!workflow?.id || isDeleting) return
    setIsDeleting(true)
    try {
      await deleteWorkflow(workflow.id)
      const remaining = workflows.filter((item) => item.id !== workflow.id)
      setWorkflows(remaining)
      setWorkflow(null)
      setTriggers([])
      setRuns([])
      setRunSteps([])
      setSelectedRunId(null)
      setSelectedStepId(null)
      setWorkflowInputs({})
      setManualInputsByTriggerId({})
      navigate(remaining[0] ? `/workspace/workflows/${remaining[0].id}` : '/workspace/workflows', { replace: true })
    } catch {
      await refreshSelectedWorkflow()
    } finally {
      setIsDeleting(false)
    }
  }

  const filteredWorkflows = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return workflows
    return workflows.filter((item) =>
      [item.name, item.description, item.id].filter(Boolean).join(' ').toLowerCase().includes(normalized),
    )
  }, [query, workflows])

  return (
    <div className="h-screen max-h-screen overflow-hidden flex flex-col">
      <header className="shrink-0 px-6 md:px-8 py-5 border-b border-border/40 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-xs font-semibold tracking-wider uppercase text-muted-foreground">Workflow operations</p>
          <h2 className="text-2xl font-bold tracking-tight">{titleFor(workflow)}</h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button asChild variant="outline" className="gap-2">
            <NavLink to="/workspace/projects">
              <MessageSquare className="h-4 w-4" />
              Modify in Chat
            </NavLink>
          </Button>
          <Button variant="outline" disabled={!workflow} onClick={() => void toggleEnabled()}>
            {workflow?.status === 'active' ? 'Disable Workflow' : 'Enable Workflow'}
          </Button>
          <Button className="gap-2" disabled={!workflow || workflowDisabled || isRunning} onClick={() => void runWorkflow()}>
            <Play className="h-4 w-4" />
            {workflowDisabled ? 'Workflow Disabled' : isRunning ? 'Running' : 'Run Full Workflow'}
          </Button>
          <Button variant="destructive" size="icon" disabled={!workflow || isDeleting} onClick={() => void removeWorkflow()}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </header>

      <main className="flex-1 min-h-0 grid grid-cols-1 xl:grid-cols-[280px_minmax(0,1fr)] overflow-hidden">
        <aside className="min-h-0 border-r border-border/40 flex flex-col">
          <div className="shrink-0 p-4 border-b border-border/40 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold tracking-tight">Workflows</h3>
              <Badge variant="secondary">{workflows.length}</Badge>
            </div>
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input className="pl-9" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search workflows..." />
            </div>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto p-3">
            {isLoading && workflows.length === 0 ? <p className="text-sm text-muted-foreground p-2">Loading workflows...</p> : null}
            {!isLoading && filteredWorkflows.length === 0 ? (
              <div className="p-4 text-sm text-muted-foreground border border-dashed rounded-lg">
                Create workflows from chat.
              </div>
            ) : null}
            <ItemGroup>
              {filteredWorkflows.map((item) => (
                <Item
                  key={item.id}
                  variant="outline"
                  size="sm"
                  className={`cursor-pointer ${item.id === workflow?.id ? 'bg-muted/60' : ''}`}
                  onClick={() => navigate(`/workspace/workflows/${item.id}`)}
                >
                  <ItemContent>
                    <ItemHeader>
                      <ItemTitle>{item.name || 'Untitled workflow'}</ItemTitle>
                    </ItemHeader>
                    <ItemDescription>{item.description || item.id}</ItemDescription>
                    <Badge variant="outline" className={`mt-2 w-fit ${statusClass(item.status)}`}>{item.status || 'draft'}</Badge>
                  </ItemContent>
                </Item>
              ))}
            </ItemGroup>
          </div>
        </aside>

        <section className="min-h-0 overflow-hidden">
          {!workflow ? (
            <div className="h-full flex items-center justify-center text-center text-muted-foreground">
              <div>
                <WorkflowIcon className="h-10 w-10 mx-auto mb-3" />
                <p className="text-sm">Select a workflow.</p>
              </div>
            </div>
          ) : (
            <Tabs value={activeTab} onValueChange={setActiveTab} className="h-full min-h-0 flex flex-col">
              <div className="shrink-0 border-b border-border/40 px-4 py-3">
                <TabsList className="grid w-full max-w-3xl grid-cols-4">
                  <TabsTrigger value="triggers">Triggers</TabsTrigger>
                  <TabsTrigger value="steps">Steps</TabsTrigger>
                  <TabsTrigger value="input">Run Input</TabsTrigger>
                  <TabsTrigger value="history">Run History</TabsTrigger>
                </TabsList>
              </div>

              <TabsContent value="triggers" className="flex-1 min-h-0 overflow-y-auto p-4 m-0">
                <div className="max-w-4xl space-y-3">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-lg font-semibold">Triggers</h3>
                    <Badge variant="secondary">{triggers.length}</Badge>
                  </div>
                  <div className="grid gap-3">
                    {triggers.length ? triggers.map((trigger) => (
                      <div key={trigger.id} className="rounded-lg border border-border/40 p-4">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-semibold">{trigger.name || trigger.triggerType}</p>
                            <p className="text-xs text-muted-foreground font-mono mt-1">{trigger.id}</p>
                          </div>
                          <Badge variant="outline" className={trigger.enabled ? statusClass('active') : statusClass('disabled')}>
                            {trigger.enabled ? 'enabled' : 'disabled'}
                          </Badge>
                        </div>
                        <pre className="mt-3 text-xs overflow-auto rounded-md bg-muted/40 p-3 max-h-48">{formatJson(trigger.configJson)}</pre>
                        <Button
                          variant="outline"
                          className="mt-3"
                          disabled={updatingTriggerId === trigger.id}
                          onClick={() => void toggleTriggerEnabled(trigger)}
                        >
                          {updatingTriggerId === trigger.id
                            ? 'Updating'
                            : trigger.triggerType === 'scheduler'
                              ? trigger.enabled ? 'Disable Schedule' : 'Enable Schedule'
                              : trigger.enabled ? 'Disable Trigger' : 'Enable Trigger'}
                        </Button>
                        {trigger.triggerType === 'manual' ? (
                          <div className="mt-4 space-y-3 border-t border-border/40 pt-4">
                            <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Manual Run</p>
                            <div className="grid gap-3 md:grid-cols-2">
                              {manualInputFields(trigger).map((field) => {
                                const name = field.name?.trim()
                                if (!name) return null
                                const id = `manual-${trigger.id}-${name}`
                                const value = manualInputsByTriggerId[trigger.id]?.[name] ?? field.default ?? ''
                                const type = field.type === 'number' ? 'number' : field.type === 'boolean' ? 'checkbox' : 'text'
                                return (
                                  <label key={name} className="block space-y-1" htmlFor={id}>
                                    <span className="text-xs font-medium">
                                      {field.label || name}{field.required ? ' *' : ''}
                                    </span>
                                    {type === 'checkbox' ? (
                                      <input
                                        id={id}
                                        type="checkbox"
                                        checked={Boolean(value)}
                                        onChange={(event) => updateManualInput(trigger.id, name, event.target.checked)}
                                      />
                                    ) : (
                                      <Input
                                        id={id}
                                        type={type}
                                        value={String(value)}
                                        onChange={(event) => updateManualInput(
                                          trigger.id,
                                          name,
                                          type === 'number' ? Number(event.target.value) : event.target.value,
                                        )}
                                      />
                                    )}
                                    {field.description ? <span className="block text-xs text-muted-foreground">{field.description}</span> : null}
                                  </label>
                                )
                              })}
                            </div>
                            {manualInputFields(trigger).length === 0 ? (
                              <p className="text-xs text-muted-foreground">This trigger does not require input fields.</p>
                            ) : null}
                            <Button className="gap-2" disabled={workflowDisabled || isRunning || !trigger.enabled} onClick={() => void runManualTrigger(trigger)}>
                              <Play className="h-4 w-4" />
                              {workflowDisabled ? 'Workflow Disabled' : isRunning ? 'Running' : 'Run Manual Trigger'}
                            </Button>
                          </div>
                        ) : null}
                      </div>
                    )) : (
                      <div className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
                        No triggers defined.
                      </div>
                    )}
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="steps" className="flex-1 min-h-0 m-0 overflow-hidden">
                <div className="h-full min-h-0 grid grid-cols-1 lg:grid-cols-[320px_minmax(0,1fr)]">
                  <div className="min-h-0 overflow-y-auto border-r border-border/40 p-4 space-y-3">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-lg font-semibold">Steps</h3>
                      <Badge variant="secondary">{definition.steps?.length ?? 0}</Badge>
                    </div>
                    <div className="grid gap-2">
                      {definition.steps?.length ? definition.steps.map((step, index) => {
                        const id = stepId(step, index)
                        return (
                          <button
                            key={id}
                            type="button"
                            className={`text-left rounded-lg border p-3 transition-colors ${selectedStepId === id ? 'border-primary bg-primary/5' : 'border-border/40 hover:bg-muted/40'}`}
                            onClick={() => setSelectedStepId(id)}
                          >
                            <div className="flex items-start justify-between gap-3">
                              <div className="min-w-0">
                                <p className="font-semibold text-sm truncate">{step.name || id}</p>
                                <p className="text-xs text-muted-foreground mt-1 line-clamp-3">{step.prompt || step.toolName || step.actionIdentifier || 'No action configured'}</p>
                              </div>
                              <Badge variant="outline" className="shrink-0">{step.type || 'step'}</Badge>
                            </div>
                          </button>
                        )
                      }) : (
                        <div className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
                          No steps defined. Use chat to design this workflow.
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="min-h-0 overflow-y-auto p-4">
                    {selectedStep ? (
                      <div className="max-w-4xl space-y-4">
                        <div className="flex items-center justify-between gap-3 rounded-lg border border-border/40 p-4">
                          <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground">Step Detail</p>
                            <p className="mt-1 font-semibold">{selectedStep.name || selectedStep.id || selectedStep.stepId}</p>
                            <p className="text-xs text-muted-foreground mt-1">
                              {stepId(selectedStep, definition.steps?.indexOf(selectedStep) ?? 0)}
                            </p>
                          </div>
                          <Badge variant="outline">{selectedStep.type || 'step'}</Badge>
                        </div>
                        <Button
                          variant="outline"
                          className="gap-2"
                          disabled={!workflow || workflowDisabled || isRunning}
                          onClick={() => void runWorkflowFromSelectedStep()}
                        >
                          <Play className="h-4 w-4" />
                          {workflowDisabled ? 'Workflow Disabled' : isRunning ? 'Running' : 'Run From This Step'}
                        </Button>
                        {selectedStep.prompt ? (
                          <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Prompt</p>
                            <pre className="text-xs whitespace-pre-wrap overflow-auto rounded-md bg-muted/40 p-3 max-h-56">{selectedStep.prompt}</pre>
                          </div>
                        ) : null}
                        {selectedStep.toolName || selectedStep.actionIdentifier ? (
                          <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Tool</p>
                            <pre className="text-xs overflow-auto rounded-md bg-muted/40 p-3">{selectedStep.toolName || selectedStep.actionIdentifier}</pre>
                          </div>
                        ) : null}
                        <div>
                          <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Raw Step JSON</p>
                          <pre className="text-xs overflow-auto rounded-md bg-muted/40 p-3 max-h-96">{formatJson(selectedStep)}</pre>
                        </div>
                      </div>
                    ) : (
                      <div className="h-full min-h-[240px] flex items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
                        Select a step to view details.
                      </div>
                    )}
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="input" className="flex-1 min-h-0 overflow-y-auto p-4 m-0">
                <div className="max-w-4xl space-y-4">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-lg font-semibold">Run Input</h3>
                    <Button className="gap-2" disabled={!workflow || workflowDisabled || isRunning} onClick={() => void runWorkflow()}>
                      <Play className="h-4 w-4" />
                      {workflowDisabled ? 'Workflow Disabled' : isRunning ? 'Running' : 'Run Full Workflow'}
                    </Button>
                  </div>
                  {workflowInputFields(definition).length ? (
                    <div className="grid gap-3 md:grid-cols-2">
                      {workflowInputFields(definition).map((field) => {
                        const name = field.name?.trim()
                        if (!name) return null
                        const id = `workflow-input-${name}`
                        const value = workflowInputs[name] ?? field.default ?? ''
                        const type = field.type === 'number' ? 'number' : field.type === 'boolean' ? 'checkbox' : 'text'
                        return (
                          <label key={name} className="block space-y-1" htmlFor={id}>
                            <span className="text-xs font-medium">
                              {field.label || name}{field.required ? ' *' : ''}
                            </span>
                            {type === 'checkbox' ? (
                              <input
                                id={id}
                                type="checkbox"
                                checked={Boolean(value)}
                                onChange={(event) => updateWorkflowInput(name, event.target.checked)}
                              />
                            ) : (
                              <Input
                                id={id}
                                type={type}
                                value={String(value)}
                                onChange={(event) => updateWorkflowInput(
                                  name,
                                  type === 'number' ? Number(event.target.value) : event.target.value,
                                )}
                              />
                            )}
                            {field.description ? <span className="block text-xs text-muted-foreground">{field.description}</span> : null}
                          </label>
                        )
                      })}
                    </div>
                  ) : (
                    <div className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
                      This workflow does not define run input fields.
                    </div>
                  )}
                  <div>
                    <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Input Preview</p>
                    <pre className="text-xs overflow-auto rounded-md bg-muted/40 p-3 max-h-80">
                      {formatJson({ ...defaultInputsFromDefinition(definition), ...workflowInputs })}
                    </pre>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="history" className="flex-1 min-h-0 m-0 overflow-hidden">
                <div className="h-full min-h-0 flex flex-col">
                  {liveEvents.length ? (
                    <div className="shrink-0 border-b border-border/40 bg-muted/20 p-3">
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                          <div className={`h-2 w-2 rounded-full ${isRunning ? 'animate-pulse bg-primary' : 'bg-muted-foreground/50'}`} />
                          <p className="text-sm font-semibold">{isRunning ? 'Live Run' : 'Latest Run Trace'}</p>
                        </div>
                        <Badge variant="secondary">{liveEvents.length} events</Badge>
                      </div>
                      <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
                        {liveEvents.map((event, index) => (
                          <div key={`${event.type}-${event.stepRunId || event.executionId || index}`} className="min-w-[220px] rounded-lg border border-border/40 bg-background p-2">
                            <div className="flex items-center justify-between gap-2">
                              <span className="text-xs font-semibold">{liveEventLabel(event)}</span>
                              <Badge variant="outline" className={statusClass(event.status)}>{event.status || 'event'}</Badge>
                            </div>
                            <p className="mt-1 truncate text-xs text-muted-foreground">
                              {event.stepName || event.stepId || event.executionId || 'Workflow run'}
                            </p>
                            {event.errorMessage ? (
                              <p className="mt-1 line-clamp-2 text-xs text-destructive">{event.errorMessage}</p>
                            ) : null}
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}
                  <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-[360px_minmax(0,1fr)]">
                  <div className="min-h-0 overflow-y-auto border-r border-border/40 p-4 space-y-3">
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-lg font-semibold">Run History</h3>
                      <Button variant="ghost" size="sm" disabled={!workflow} onClick={() => void refreshSelectedWorkflow()}>
                        Refresh
                      </Button>
                    </div>
                    {runs.length ? (
                      <div className="space-y-2">
                        {runs.map((run) => (
                          <button
                            key={run.id}
                            type="button"
                            className={`w-full text-left rounded-lg border p-3 ${selectedRun?.id === run.id ? 'border-primary bg-primary/5' : 'border-border/40 hover:bg-muted/40'}`}
                            onClick={() => setSelectedRunId(run.id)}
                          >
                            <div className="flex items-center justify-between gap-2">
                              <span className="text-xs font-mono truncate">{run.id}</span>
                              <Badge variant="outline" className={statusClass(run.status)}>{run.status}</Badge>
                            </div>
                            <p className="text-xs text-muted-foreground mt-2">
                              {run.triggerType || 'manual'} · {new Date(run.startedAt).toLocaleString()}
                            </p>
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p className="text-sm text-muted-foreground">No runs yet.</p>
                    )}
                  </div>
                  <div className="min-h-0 overflow-y-auto p-4">
                    {selectedRun ? (
                      <div className="max-w-4xl space-y-5">
                        <section className="rounded-lg border border-border/40 p-4 space-y-3">
                          <div className="flex items-center gap-2">
                            {selectedRun.status?.toLowerCase() === 'failed'
                              ? <AlertCircle className="h-4 w-4 text-destructive" />
                              : <CheckCircle2 className="h-4 w-4 text-emerald-500" />}
                            <Badge variant="outline" className={statusClass(selectedRun.status)}>{selectedRun.status}</Badge>
                            <span className="text-xs text-muted-foreground">
                              {selectedRun.triggerType || 'manual'} · {new Date(selectedRun.startedAt).toLocaleString()}
                            </span>
                          </div>
                          <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Input</p>
                            <pre className="text-xs overflow-auto rounded-md bg-muted/40 p-3 max-h-40">{formatJson(runInput(selectedRun))}</pre>
                          </div>
                          <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Output</p>
                            <pre className="text-xs overflow-auto rounded-md bg-muted/40 p-3 max-h-40">{formatJson(runOutput(selectedRun))}</pre>
                          </div>
                          {selectedRun.errorMessage ? (
                            <div>
                              <p className="text-xs font-semibold uppercase text-destructive mb-1">Error</p>
                              <pre className="text-xs overflow-auto rounded-md bg-destructive/10 text-destructive p-3 max-h-40">{selectedRun.errorMessage}</pre>
                            </div>
                          ) : null}
                        </section>

                        <section className="space-y-2">
                          <div className="flex items-center justify-between gap-3">
                            <div className="flex items-center gap-2 text-sm font-semibold">
                              <Activity className="h-4 w-4" />
                              Step Timeline
                            </div>
                            <Badge variant="secondary">{runSteps.length}</Badge>
                          </div>
                          {runSteps.length ? runSteps.map((step) => (
                            <div key={step.id} className="rounded-lg border border-border/40 p-3">
                              <div className="flex items-center justify-between gap-2">
                                <span className="text-sm font-semibold">{step.stepName || step.stepId}</span>
                                <Badge variant="outline" className={statusClass(step.status)}>{step.status}</Badge>
                              </div>
                              {step.errorMessage ? (
                                <div className="mt-2">
                                  <p className="text-xs font-semibold uppercase text-destructive mb-1">Error</p>
                                  <pre className="text-xs overflow-auto rounded-md bg-destructive/10 text-destructive p-3 max-h-40">{step.errorMessage}</pre>
                                </div>
                              ) : (
                                <div className="mt-2">
                                  <p className="text-xs font-semibold uppercase text-muted-foreground mb-1">Output</p>
                                  <pre className="text-xs overflow-auto rounded-md bg-muted/40 p-3 max-h-40">{formatJson(stepOutput(step) || stepInput(step))}</pre>
                                </div>
                              )}
                            </div>
                          )) : <p className="text-sm text-muted-foreground">No step logs yet.</p>}
                        </section>
                      </div>
                    ) : (
                      <div className="h-full min-h-[240px] flex items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
                        Select a run to view details.
                      </div>
                    )}
                  </div>
                  </div>
                </div>
              </TabsContent>
            </Tabs>
          )}
        </section>
      </main>
    </div>
  )
}
