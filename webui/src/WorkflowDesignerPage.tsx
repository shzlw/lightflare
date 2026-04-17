import { useEffect, useMemo, useRef, useState } from 'react'
import type React from 'react'
import { NavLink, useNavigate, useParams } from 'react-router-dom'
import {
  createWorkflow,
  executeWorkflow,
  getExecution,
  getExecutionSteps,
  getWorkflow,
  listWorkflows,
  request,
  streamRequest,
  type Workflow,
  type WorkflowExecution,
  type WorkflowStepExecution,
} from '@/lib/api'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'
import { Badge } from '@/components/ui/badge'
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
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import {
  Activity,
  AlertCircle,
  Brain,
  CheckCircle2,
  ChevronDown,
  FileText,
  Info,
  ListTodo,
  Loader2,
  MessageSquare,
  Plus,
  Play,
  Search,
  Send,
  Workflow as WorkflowIcon,
} from 'lucide-react'

type WorkflowInputDefinition = {
  name: string
  type?: string
  label?: string
  description?: string
  required?: boolean
  default?: unknown
  options?: unknown[]
}

type WorkflowStep = {
  stepId: string
  type?: string
  actionIdentifier?: string
  prompt?: string
  config?: Record<string, unknown>
  inputMapping?: Record<string, unknown>
  outputMapping?: Record<string, unknown>
  transitions?: Array<{ conditionExpression: string; targetStepId: string }>
  metadata?: Record<string, unknown>
}

type WorkflowSchema = {
  version?: number
  inputs?: WorkflowInputDefinition[]
  steps?: WorkflowStep[]
  metadata?: Record<string, unknown>
}

type ChatSession = {
  id: string
  title: string | null
}

type WorkflowChatMessage = {
  id: string
  source: 'user' | 'llm' | 'system'
  content: string
}

type ChatStreamEventType =
  | 'MESSAGE_START'
  | 'PLAN_CREATED'
  | 'STEP_STARTED'
  | 'STEP_PROGRESS'
  | 'STEP_COMPLETED'
  | 'FINAL_RESPONSE'
  | 'MESSAGE_COMPLETE'
  | 'MESSAGE_ERROR'

type ChatStreamEvent<T = unknown> = {
  type: ChatStreamEventType
  sessionId: string
  payload: T
}

type ChatStreamMessageCompleteEvent = {
  messageId: string
  content: string
}

type ChatStreamFinalResponseEvent = {
  content: string | null
}

type ChatStreamMessageErrorEvent = {
  message: string
}

type ChatStreamMessageStartEvent = {
  messageId: string
  source: string
}

type ChatStreamPlanCreatedEvent = {
  thoughtProcess: string | null
  selectedSkill: string | null
  steps: Array<{ id: string; content: string; status: string | null }>
}

type ChatStreamStepEvent = {
  step: { id: string; content: string; status: string | null } | null
}

type ChatStreamStepProgressEvent = ChatStreamStepEvent & {
  progressType: string
  message: string | null
}

type ChatStreamStepCompletedEvent = ChatStreamStepEvent & {
  status: string
  terminalResponse: string | null
}

type StreamTimelineEntry =
  | { id: string; type: 'message_start'; messageId: string; source: string }
  | {
      id: string
      type: 'plan_created'
      thoughtProcess: string | null
      selectedSkill: string | null
      steps: Array<{ id: string; content: string; status: string | null }>
    }
  | { id: string; type: 'step_started'; stepId: string; content: string }
  | { id: string; type: 'step_progress'; stepId: string | null; progressType: string; message: string | null }
  | { id: string; type: 'step_completed'; stepId: string; status: string; terminalResponse: string | null }
  | { id: string; type: 'final_response'; content: string | null }
  | { id: string; type: 'message_complete'; messageId: string }
  | { id: string; type: 'message_error'; message: string }

function workflowTitle(workflow: Workflow | null) {
  return workflow?.name?.trim() || 'Untitled Workflow'
}

function formatJson(value: string | null) {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function stringify(value: unknown) {
  if (value === undefined || value === null || value === '') return 'Not set'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function statusClass(status?: string) {
  if (status === 'FAILED') return 'bg-destructive/10 text-destructive border-destructive/20'
  if (status === 'COMPLETED' || status === 'SUCCESS') {
    return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/20'
  }
  return 'bg-primary/10 text-primary border-primary/20'
}

function parseSchema(workflow: Workflow | null): WorkflowSchema {
  if (!workflow?.schemaDefinition) return { version: 1, inputs: [], steps: [] }
  try {
    const parsed = JSON.parse(workflow.schemaDefinition) as WorkflowSchema
    return {
      version: parsed.version ?? 1,
      inputs: Array.isArray(parsed.inputs) ? parsed.inputs : [],
      steps: Array.isArray(parsed.steps) ? parsed.steps : [],
      metadata: parsed.metadata ?? {},
    }
  } catch {
    return { version: 1, inputs: [], steps: [] }
  }
}

function defaultInputs(inputs: WorkflowInputDefinition[]) {
  return inputs.reduce<Record<string, unknown>>((acc, input) => {
    if (input.name) acc[input.name] = input.default ?? ''
    return acc
  }, {})
}

function scrollToBottom(container: HTMLDivElement | null) {
  if (container) container.scrollTop = container.scrollHeight
}

export default function WorkflowDesignerPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNewWorkflow = id === 'new'
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [workflow, setWorkflow] = useState<Workflow | null>(null)
  const [isListLoading, setIsListLoading] = useState(true)
  const [isWorkflowLoading, setIsWorkflowLoading] = useState(false)
  const [workflowQuery, setWorkflowQuery] = useState('')
  const [expandedStepId, setExpandedStepId] = useState<string | null>(null)
  const [execution, setExecution] = useState<WorkflowExecution | null>(null)
  const [executionSteps, setExecutionSteps] = useState<WorkflowStepExecution[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const [runInputs, setRunInputs] = useState<Record<string, unknown>>({})
  const [chatDraft, setChatDraft] = useState('')
  const [chatSessionId, setChatSessionId] = useState<string | null>(null)
  const [chatMessages, setChatMessages] = useState<WorkflowChatMessage[]>([])
  const [isChatSending, setIsChatSending] = useState(false)
  const [streamEvents, setStreamEvents] = useState<StreamTimelineEntry[]>([])
  const [retainedStreamEvents, setRetainedStreamEvents] = useState<StreamTimelineEntry[]>([])
  const [isAssistantOpen, setIsAssistantOpen] = useState(false)
  const [newWorkflowName, setNewWorkflowName] = useState('')
  const [newWorkflowDescription, setNewWorkflowDescription] = useState('')
  const [isCreatingWorkflow, setIsCreatingWorkflow] = useState(false)
  const workflowChatListRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    void loadWorkflowList()
  }, [])

  useEffect(() => {
    if (id && id !== 'new') {
      void selectWorkflow(id)
      return
    }

    setWorkflow(null)
    setExpandedStepId(null)
    setExecution(null)
    setExecutionSteps([])
    resetAssistant()
    setIsWorkflowLoading(false)
  }, [id])

  const schema = useMemo(() => parseSchema(workflow), [workflow])
  const inputs = schema.inputs ?? []
  const steps = schema.steps ?? []
  const finalOutput = execution?.outputData ? formatJson(execution.outputData) : ''

  useEffect(() => {
    setRunInputs(defaultInputs(inputs))
  }, [workflow?.id, workflow?.schemaDefinition])

  async function loadWorkflowList() {
    setIsListLoading(true)
    try {
      const data = await listWorkflows()
      setWorkflows(data)
      if (!id && data.length > 0) {
        navigate(`/workspace/workflows/${data[0].id}`, { replace: true })
      }
    } catch (err) {
      toast.error('Failed to load workflows')
    } finally {
      setIsListLoading(false)
    }
  }

  async function selectWorkflow(workflowId: string) {
    setIsWorkflowLoading(true)
    setExpandedStepId(null)
    setExecution(null)
    setExecutionSteps([])
    resetAssistant()

    try {
      const data = await getWorkflow(workflowId)
      setWorkflow(data)
    } catch (err) {
      setWorkflow(null)
      toast.error('Failed to load workflow definition')
    } finally {
      setIsWorkflowLoading(false)
    }
  }

  function resetAssistant() {
    setChatSessionId(null)
    setChatMessages([])
    setChatDraft('')
    setStreamEvents([])
    setRetainedStreamEvents([])
  }

  async function reloadSelectedWorkflow() {
    if (!workflow?.id) return
    const data = await getWorkflow(workflow.id)
    setWorkflow(data)
    setWorkflows((current) => current.map((item) => (item.id === data.id ? data : item)))
  }

  async function handleCreateWorkflow() {
    const name = newWorkflowName.trim()
    if (!name || isCreatingWorkflow) return

    setIsCreatingWorkflow(true)
    try {
      const created = await createWorkflow({
        name,
        description: newWorkflowDescription.trim(),
        schemaDefinition: '{"version":1,"inputs":[],"steps":[]}',
      })
      setWorkflows((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setNewWorkflowName('')
      setNewWorkflowDescription('')
      toast.success('Workflow created')
      navigate(`/workspace/workflows/${created.id}`, { replace: true })
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create workflow')
    } finally {
      setIsCreatingWorkflow(false)
    }
  }

  function cancelNewWorkflow() {
    setNewWorkflowName('')
    setNewWorkflowDescription('')
    if (workflows.length > 0) {
      navigate(`/workspace/workflows/${workflows[0].id}`, { replace: true })
      return
    }
    navigate('/workspace/workflows', { replace: true })
  }

  const filteredWorkflows = useMemo(() => {
    const normalizedSearch = workflowQuery.trim().toLowerCase()
    if (!normalizedSearch) return workflows
    return workflows.filter((item) =>
      [item.name, item.description, item.id].filter(Boolean).join(' ').toLowerCase().includes(normalizedSearch),
    )
  }, [workflowQuery, workflows])

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
      const result = await executeWorkflow(workflow.id, runInputs)
      const latestExecution = await refreshExecution(result.executionId)
      toast.success(`Workflow ${latestExecution.status.toLowerCase()}`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to run workflow')
    } finally {
      setIsRunning(false)
    }
  }

  function updateRunInput(input: WorkflowInputDefinition, value: string) {
    setRunInputs((current) => ({
      ...current,
      [input.name]: parseInputValue(input, value),
    }))
  }

  function parseInputValue(input: WorkflowInputDefinition, value: string) {
    const type = input.type?.toLowerCase()
    if (type === 'number') return value === '' ? '' : Number(value)
    if (type === 'boolean') return value === 'true'
    return value
  }

  async function createWorkflowChatSession() {
    const session = await request<ChatSession>('/internal-api/v1/chat-sessions', {
      method: 'POST',
      body: JSON.stringify({ title: `Workflow: ${workflowTitle(workflow)}` }),
    })
    setChatSessionId(session.id)
    return session.id
  }

  function buildWorkflowChatPrompt(content: string) {
    return [
      'You are helping refine a saved Lightflare workflow.',
      `Workflow id: ${workflow?.id || 'new'}`,
      `Workflow name: ${workflowTitle(workflow)}`,
      `Workflow description: ${workflow?.description || 'No description'}`,
      'Current workflow schema:',
      workflow?.schemaDefinition || '{"version":1,"inputs":[],"steps":[]}',
      'User request:',
      content,
      'When changing the workflow, use manage-workflow-definition. Preserve reusable workflow inputs when possible.',
    ].join('\n\n')
  }

  async function sendWorkflowChat() {
    const content = chatDraft.trim()
    if (!content || isChatSending || !workflow) return

    setIsChatSending(true)
    setStreamEvents([])
    setChatDraft('')
    setChatMessages((current) => [...current, { id: crypto.randomUUID(), source: 'user', content }])
    requestAnimationFrame(() => scrollToBottom(workflowChatListRef.current))

    let finalResponse: string | null = null
    try {
      const sessionId = chatSessionId || await createWorkflowChatSession()
      await streamRequest<ChatStreamEvent>(
        `/internal-api/v1/chat-sessions/${sessionId}/responses/stream`,
        {
          method: 'POST',
          body: JSON.stringify({ source: 'user', content: buildWorkflowChatPrompt(content) }),
        },
        ({ data }) => handleAssistantStreamEvent(data, (value) => { finalResponse = value }),
      )

      setChatMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), source: 'llm', content: finalResponse || 'Done.' },
      ])
      requestAnimationFrame(() => scrollToBottom(workflowChatListRef.current))
      await reloadSelectedWorkflow()
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Workflow chat failed.'
      setChatMessages((current) => [...current, { id: crypto.randomUUID(), source: 'system', content: message }])
      toast.error(message)
    } finally {
      setIsChatSending(false)
    }
  }

  function handleAssistantStreamEvent(data: ChatStreamEvent, setFinalResponse: (content: string | null) => void) {
    switch (data.type) {
      case 'MESSAGE_START': {
        const payload = data.payload as ChatStreamMessageStartEvent
        appendStream({ id: crypto.randomUUID(), type: 'message_start', messageId: payload.messageId, source: payload.source })
        break
      }
      case 'PLAN_CREATED': {
        const payload = data.payload as ChatStreamPlanCreatedEvent
        appendStream({
          id: crypto.randomUUID(),
          type: 'plan_created',
          thoughtProcess: payload.thoughtProcess,
          selectedSkill: payload.selectedSkill,
          steps: payload.steps,
        })
        break
      }
      case 'STEP_STARTED': {
        const payload = data.payload as ChatStreamStepEvent
        if (payload.step) {
          appendStream({ id: crypto.randomUUID(), type: 'step_started', stepId: payload.step.id, content: payload.step.content })
        }
        break
      }
      case 'STEP_PROGRESS': {
        const payload = data.payload as ChatStreamStepProgressEvent
        appendStream({
          id: crypto.randomUUID(),
          type: 'step_progress',
          stepId: payload.step?.id ?? null,
          progressType: payload.progressType,
          message: payload.message,
        })
        break
      }
      case 'STEP_COMPLETED': {
        const payload = data.payload as ChatStreamStepCompletedEvent
        if (payload.step) {
          appendStream({
            id: crypto.randomUUID(),
            type: 'step_completed',
            stepId: payload.step.id,
            status: payload.status,
            terminalResponse: payload.terminalResponse,
          })
        }
        break
      }
      case 'FINAL_RESPONSE': {
        const payload = data.payload as ChatStreamFinalResponseEvent
        setFinalResponse(payload.content)
        appendStream({ id: crypto.randomUUID(), type: 'final_response', content: payload.content })
        break
      }
      case 'MESSAGE_COMPLETE': {
        const payload = data.payload as ChatStreamMessageCompleteEvent
        setFinalResponse(payload.content)
        appendStream({ id: crypto.randomUUID(), type: 'message_complete', messageId: payload.messageId }, true)
        break
      }
      case 'MESSAGE_ERROR': {
        const payload = data.payload as ChatStreamMessageErrorEvent
        appendStream({ id: crypto.randomUUID(), type: 'message_error', message: payload.message }, true)
        throw new Error(payload.message)
      }
    }
  }

  function appendStream(entry: StreamTimelineEntry, retain = false) {
    setStreamEvents((current) => {
      const next = [...current, entry]
      if (retain) setRetainedStreamEvents(next)
      return next
    })
    requestAnimationFrame(() => scrollToBottom(workflowChatListRef.current))
  }

  const visibleStreamEvents = streamEvents.length > 0 ? streamEvents : retainedStreamEvents

  return (
    <div className="w-full h-screen max-h-screen flex flex-col animate-in fade-in duration-500 overflow-hidden">
      <header className="shrink-0 px-6 md:px-8 pt-6 md:pt-8 pb-4">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 className="text-3xl font-bold tracking-tight text-foreground">Workflows</h2>
            <p className="text-sm text-muted-foreground mt-1">
              {isNewWorkflow ? 'Create a workflow first, then define its inputs and steps.' : workflowTitle(workflow)}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button asChild variant="outline" size="sm" className="gap-2">
              <NavLink to="/workspace/workflows/new">
                <Plus className="h-4 w-4" /> New
              </NavLink>
            </Button>
            <AssistantSheet
              open={isAssistantOpen}
              onOpenChange={setIsAssistantOpen}
              workflow={workflow}
              chatMessages={chatMessages}
              chatDraft={chatDraft}
              setChatDraft={setChatDraft}
              isChatSending={isChatSending}
              sendWorkflowChat={sendWorkflowChat}
              visibleStreamEvents={visibleStreamEvents}
              renderStreamEvent={renderStreamEvent}
              workflowChatListRef={workflowChatListRef}
            />
          </div>
        </div>
      </header>

      <section className="flex-1 min-h-0 px-6 md:px-8 pb-6 md:pb-8 overflow-hidden">
        <div className="grid h-full min-h-0 gap-4 overflow-hidden grid-cols-[minmax(240px,300px)_minmax(420px,1fr)_minmax(320px,420px)]">
          {renderWorkflowList()}
          {renderMainRunner()}
          {renderExecutionLogs()}
        </div>
      </section>

      <Toaster richColors position="top-right" />
    </div>
  )

  function renderWorkflowList() {
    return (
      <aside className="flex flex-col min-h-0 rounded-xl border border-border/40 bg-card">
        <div className="shrink-0 p-4 border-b border-border/40 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold tracking-tight text-muted-foreground uppercase">Workflows</h3>
            <Badge variant="secondary" className="font-mono text-[10px]">{workflows.length}</Badge>
          </div>
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              className="h-10 pl-9 rounded-lg text-sm bg-muted/30 border-none shadow-none focus-visible:ring-1"
              value={workflowQuery}
              onChange={(event) => setWorkflowQuery(event.target.value)}
              placeholder="Search workflows..."
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-3">
          {isListLoading ? <p className="text-sm text-muted-foreground p-2">Loading workflows...</p> : null}
          {!isListLoading && filteredWorkflows.length === 0 ? <p className="text-sm text-muted-foreground p-2">No workflows found.</p> : null}
          <ItemGroup aria-label="Workflow list">
            {filteredWorkflows.map((item) => (
              <Item
                key={item.id}
                variant="outline"
                size="sm"
                className={`cursor-pointer ${item.id === workflow?.id ? 'bg-muted/60' : ''}`}
                role="button"
                tabIndex={0}
                onClick={() => navigate(`/workspace/workflows/${item.id}`)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    navigate(`/workspace/workflows/${item.id}`)
                  }
                }}
              >
                <ItemContent>
                  <ItemHeader><ItemTitle>{item.name || 'Untitled workflow'}</ItemTitle></ItemHeader>
                  <ItemDescription>{item.description || 'No description provided.'}</ItemDescription>
                </ItemContent>
              </Item>
            ))}
          </ItemGroup>
        </div>
      </aside>
    )
  }

  function renderMainRunner() {
    return (
      <main className="flex flex-col min-h-0 rounded-xl border border-border/40 bg-card overflow-hidden">
        <div className="shrink-0 p-4 border-b border-border/40 flex items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-semibold tracking-wider uppercase text-muted-foreground">Workflow Runner</p>
            <h3 className="text-xl font-bold tracking-tight truncate">{workflowTitle(workflow)}</h3>
          </div>
          <Button className="gap-2" disabled={!workflow || isRunning || isNewWorkflow} onClick={() => void runWorkflow()}>
            <Play className="h-4 w-4" /> {isRunning ? 'Running' : 'Run'}
          </Button>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-5">
          {isWorkflowLoading ? (
            <div className="py-16 text-center text-sm text-muted-foreground">Loading workflow...</div>
          ) : isNewWorkflow ? (
            renderNewWorkflowForm()
          ) : workflow ? (
            <>
              <section className="space-y-3">
                <div>
                  <h4 className="text-sm font-bold tracking-tight">Inputs</h4>
                  <p className="text-xs text-muted-foreground mt-1">Enter the values for this run.</p>
                </div>
                {inputs.length === 0 ? (
                  <div className="rounded-lg border border-dashed bg-muted/10 p-4 text-sm text-muted-foreground">
                    This workflow does not require manual inputs.
                  </div>
                ) : (
                  <div className="grid gap-3 md:grid-cols-2">
                    {inputs.map((input) => renderRunInput(input))}
                  </div>
                )}
              </section>

              {finalOutput ? (
                <section className="space-y-3">
                  <h4 className="text-sm font-bold tracking-tight">Latest Result</h4>
                  <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-lg border border-border/40 bg-muted/30 p-4 text-xs leading-relaxed">
                    {finalOutput}
                  </pre>
                </section>
              ) : null}

              <section className="space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-bold tracking-tight">Steps</h4>
                    <p className="text-xs text-muted-foreground mt-1">Saved procedure executed from top to bottom.</p>
                  </div>
                  <Badge variant="outline">{steps.length} steps</Badge>
                </div>
                {steps.length === 0 ? (
                  <div className="rounded-lg border border-dashed bg-muted/10 p-8 text-center">
                    <WorkflowIcon className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
                    <p className="text-sm font-medium">No steps yet</p>
                    <p className="text-xs text-muted-foreground mt-1">Use the assistant drawer to define this workflow.</p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {steps.map((step, index) => renderProcedureStep(step, index))}
                  </div>
                )}
              </section>
            </>
          ) : (
            <div className="h-full flex items-center justify-center text-center p-6">
              <div>
                <WorkflowIcon className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
                <p className="text-sm font-medium">No workflow selected</p>
                <p className="text-xs text-muted-foreground mt-1">Select a workflow from the list.</p>
              </div>
            </div>
          )}
        </div>
      </main>
    )
  }

  function renderNewWorkflowForm() {
    return (
      <form
        className="max-w-2xl space-y-4"
        onSubmit={(event) => {
          event.preventDefault()
          void handleCreateWorkflow()
        }}
      >
        <div>
          <h4 className="text-lg font-bold tracking-tight">New workflow</h4>
          <p className="text-sm text-muted-foreground mt-1">Name the saved procedure before adding inputs and steps.</p>
        </div>
        <div className="space-y-2">
          <label className="text-xs font-semibold tracking-wider uppercase text-muted-foreground" htmlFor="workflow-name">Name</label>
          <Input id="workflow-name" value={newWorkflowName} onChange={(event) => setNewWorkflowName(event.target.value)} autoFocus />
        </div>
        <div className="space-y-2">
          <label className="text-xs font-semibold tracking-wider uppercase text-muted-foreground" htmlFor="workflow-description">Description</label>
          <textarea
            id="workflow-description"
            className="min-h-28 w-full resize-none rounded-md border bg-background px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
            value={newWorkflowDescription}
            onChange={(event) => setNewWorkflowDescription(event.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          <Button type="submit" disabled={!newWorkflowName.trim() || isCreatingWorkflow}>{isCreatingWorkflow ? 'Creating...' : 'Create Workflow'}</Button>
          <Button type="button" variant="outline" onClick={cancelNewWorkflow} disabled={isCreatingWorkflow}>Cancel</Button>
        </div>
      </form>
    )
  }

  function renderRunInput(input: WorkflowInputDefinition) {
    const id = `workflow-input-${input.name}`
    const value = runInputs[input.name]
    const type = input.type?.toLowerCase()
    return (
      <div key={input.name} className="space-y-2 rounded-lg border border-border/40 p-4">
        <label className="text-xs font-semibold tracking-wider uppercase text-muted-foreground" htmlFor={id}>
          {input.label || input.name}{input.required ? ' *' : ''}
        </label>
        {input.options && input.options.length > 0 ? (
          <select
            id={id}
            className="h-10 w-full rounded-md border bg-background px-3 text-sm shadow-sm outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
            value={String(value ?? '')}
            onChange={(event) => updateRunInput(input, event.target.value)}
          >
            <option value="">Select...</option>
            {input.options.map((option) => (
              <option key={String(option)} value={String(option)}>{String(option)}</option>
            ))}
          </select>
        ) : type === 'boolean' ? (
          <select
            id={id}
            className="h-10 w-full rounded-md border bg-background px-3 text-sm shadow-sm outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
            value={String(Boolean(value))}
            onChange={(event) => updateRunInput(input, event.target.value)}
          >
            <option value="true">True</option>
            <option value="false">False</option>
          </select>
        ) : (
          <Input
            id={id}
            type={type === 'number' ? 'number' : 'text'}
            value={String(value ?? '')}
            onChange={(event) => updateRunInput(input, event.target.value)}
          />
        )}
        {input.description ? <p className="text-xs text-muted-foreground">{input.description}</p> : null}
      </div>
    )
  }

  function renderProcedureStep(step: WorkflowStep, index: number) {
    const isExpanded = expandedStepId === step.stepId
    const stepRun = executionSteps.find((item) => item.stepId === step.stepId)
    return (
      <article key={step.stepId} className="rounded-lg border border-border/40 bg-background overflow-hidden">
        <button
          type="button"
          className="w-full p-4 text-left flex items-center gap-3 hover:bg-muted/30"
          onClick={() => setExpandedStepId(isExpanded ? null : step.stepId)}
        >
          <span className="h-7 w-7 shrink-0 rounded-md bg-muted flex items-center justify-center text-xs font-mono font-bold">{index + 1}</span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <p className="text-sm font-semibold truncate">{step.stepId}</p>
              <Badge variant="secondary" className="text-[10px]">{step.type || 'TOOL'}</Badge>
              {stepRun ? <Badge variant="outline" className={statusClass(stepRun.status)}>{stepRun.status}</Badge> : null}
            </div>
            <p className="text-xs text-muted-foreground truncate">{step.actionIdentifier || step.prompt || 'No action configured'}</p>
          </div>
          <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${isExpanded ? 'rotate-180' : ''}`} />
        </button>
        {isExpanded ? (
          <div className="border-t border-border/40 p-4 grid gap-3 md:grid-cols-2">
            <DetailBlock label="Action" value={step.actionIdentifier || 'none'} />
            <DetailBlock label="Routes" value={String(step.transitions?.length || 0)} />
            {step.prompt ? <DetailBlock className="md:col-span-2" label="Prompt" value={step.prompt} /> : null}
            <JsonBlock label="Input Mapping" value={step.inputMapping} />
            <JsonBlock label="Output Mapping" value={step.outputMapping} />
            <JsonBlock label="Transitions" value={step.transitions} />
            {stepRun?.outputData ? <JsonBlock label="Latest Output" value={formatJson(stepRun.outputData)} /> : null}
          </div>
        ) : null}
      </article>
    )
  }

  function renderExecutionLogs() {
    return (
      <section className="flex flex-col min-h-0 rounded-xl border border-border/40 bg-card overflow-hidden">
        <div className="shrink-0 p-4 border-b border-border/40 flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Activity className="h-4 w-4 text-muted-foreground" />
            <div>
              <p className="text-xs font-semibold tracking-wider uppercase text-muted-foreground">Execution Logs</p>
              <h3 className="text-sm font-bold tracking-tight">Latest run</h3>
            </div>
          </div>
          {execution ? <Badge variant="outline" className={statusClass(execution.status)}>{execution.status}</Badge> : null}
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto p-4">
          {!execution ? (
            <div className="h-full flex items-center justify-center text-center border-2 border-dashed rounded-lg bg-muted/10">
              <div>
                <Activity className="h-8 w-8 mx-auto text-muted-foreground mb-3" />
                <p className="text-sm font-medium">Ready to run</p>
                <p className="text-xs text-muted-foreground mt-1">Run the workflow to inspect inputs, outputs, and failures.</p>
              </div>
            </div>
          ) : executionSteps.length === 0 ? (
            <p className="text-xs text-muted-foreground italic">No step logs recorded.</p>
          ) : (
            <div className="space-y-3">
              {executionSteps.map((step) => (
                <div key={step.id} className="rounded-lg border border-border/40 bg-card p-4 space-y-3 shadow-sm">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-mono truncate">{step.stepId}</p>
                      <p className="text-[10px] text-muted-foreground uppercase">{step.stepType || 'step'}</p>
                    </div>
                    <Badge variant="outline" className={statusClass(step.status)}>{step.status}</Badge>
                  </div>
                  {step.errorMessage ? <p className="text-xs text-destructive leading-relaxed">{step.errorMessage}</p> : null}
                  {step.inputData ? <LogDetails label="Input" value={step.inputData} /> : null}
                  {step.outputData ? <LogDetails label="Output" value={step.outputData} /> : null}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    )
  }

  function renderStreamEvent(entry: StreamTimelineEntry) {
    switch (entry.type) {
      case 'message_start':
        return <StreamLine icon={<Info className="h-3.5 w-3.5 text-blue-500" />} text="Assistant strategy initialized." />
      case 'plan_created':
        return (
          <div className="space-y-3 p-4 rounded-xl border border-border/40 bg-card/50 shadow-sm">
            <div className="flex items-center gap-2">
              <Brain className="h-4 w-4 text-primary" />
              <h4 className="text-sm font-semibold tracking-tight">Execution Strategy</h4>
            </div>
            {entry.thoughtProcess ? <p className="text-xs leading-relaxed text-muted-foreground">{entry.thoughtProcess}</p> : null}
            <div className="space-y-1.5">
              <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground/70">
                <ListTodo className="h-3 w-3" /> Planned Steps ({entry.steps.length})
              </div>
              {entry.steps.map((step) => <p key={step.id} className="text-xs">{step.id}: {step.content}</p>)}
            </div>
          </div>
        )
      case 'step_started':
        return <StreamLine icon={<Play className="h-3.5 w-3.5 text-primary" />} text={`Step ${entry.stepId}: ${entry.content}`} />
      case 'step_progress':
        return <StreamLine icon={<Loader2 className="h-3.5 w-3.5 text-muted-foreground animate-spin" />} text={entry.message || 'Processing...'} />
      case 'step_completed':
        return (
          <div className="space-y-2">
            <StreamLine icon={<CheckCircle2 className="h-3.5 w-3.5 text-green-500" />} text={`Step ${entry.stepId}: ${entry.status}`} />
            {entry.terminalResponse ? (
              <pre className="rounded-lg bg-zinc-950 p-3 text-[11px] text-zinc-300 whitespace-pre-wrap max-h-40 overflow-auto">{entry.terminalResponse}</pre>
            ) : null}
          </div>
        )
      case 'final_response':
        return <StreamLine icon={<FileText className="h-3.5 w-3.5 text-primary" />} text={entry.content || 'Final response ready.'} />
      case 'message_complete':
        return <StreamLine icon={<CheckCircle2 className="h-3.5 w-3.5 text-primary" />} text="Message archived successfully." />
      case 'message_error':
        return <StreamLine icon={<AlertCircle className="h-3.5 w-3.5 text-destructive" />} text={entry.message} />
    }
  }
}

function DetailBlock({ label, value, className = '' }: { label: string; value: string; className?: string }) {
  return (
    <div className={`rounded-lg border border-border/40 p-3 ${className}`}>
      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest block mb-1">{label}</span>
      <p className="text-xs whitespace-pre-wrap break-words">{value}</p>
    </div>
  )
}

function JsonBlock({ label, value }: { label: string; value: unknown }) {
  return (
    <div className="rounded-lg border border-border/40 p-3">
      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest block mb-1">{label}</span>
      <pre className="text-xs whitespace-pre-wrap break-words max-h-48 overflow-auto">{stringify(value)}</pre>
    </div>
  )
}

function LogDetails({ label, value }: { label: string; value: string }) {
  return (
    <details className="text-xs text-muted-foreground">
      <summary className="cursor-pointer font-medium">{label}</summary>
      <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded-lg bg-muted p-3 text-foreground/80">{formatJson(value)}</pre>
    </details>
  )
}

function StreamLine({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <div className="flex items-start gap-3 py-1.5 px-3 rounded-lg bg-muted/30 border border-border/20">
      <div className="mt-0.5">{icon}</div>
      <span className="text-xs leading-relaxed text-muted-foreground">{text}</span>
    </div>
  )
}

function AssistantSheet({
  open,
  onOpenChange,
  workflow,
  chatMessages,
  chatDraft,
  setChatDraft,
  isChatSending,
  sendWorkflowChat,
  visibleStreamEvents,
  renderStreamEvent,
  workflowChatListRef,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  workflow: Workflow | null
  chatMessages: WorkflowChatMessage[]
  chatDraft: string
  setChatDraft: (value: string) => void
  isChatSending: boolean
  sendWorkflowChat: () => Promise<void>
  visibleStreamEvents: StreamTimelineEntry[]
  renderStreamEvent: (entry: StreamTimelineEntry) => React.ReactNode
  workflowChatListRef: React.RefObject<HTMLDivElement | null>
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2" disabled={!workflow}>
          <MessageSquare className="h-4 w-4" /> Refine
        </Button>
      </SheetTrigger>
      <SheetContent className="w-[520px] sm:max-w-[520px] p-0 gap-0">
        <SheetHeader className="border-b border-border/40">
          <SheetTitle>Refine Workflow</SheetTitle>
          <SheetDescription>Ask the assistant to update inputs, steps, schedule, or behavior.</SheetDescription>
        </SheetHeader>

        <div ref={workflowChatListRef} className="flex-1 min-h-0 overflow-y-auto p-4">
          <div className="flex flex-col gap-3">
            {chatMessages.length === 0 ? <p className="text-sm text-muted-foreground">Describe the workflow change you want.</p> : null}
            {chatMessages.map((message) => (
              <article key={message.id} className="rounded-lg border border-border/40 p-4 bg-card shadow-sm">
                <Badge variant={message.source === 'user' ? 'default' : message.source === 'system' ? 'destructive' : 'secondary'}>
                  {message.source === 'llm' ? 'assistant' : message.source}
                </Badge>
                <p className="text-sm leading-relaxed whitespace-pre-wrap break-words mt-2">{message.content}</p>
              </article>
            ))}
            {visibleStreamEvents.length > 0 ? (
              <article className="rounded-lg border border-border/40 p-4 bg-muted/20 space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <Badge variant="outline">stream</Badge>
                  <span className="text-xs text-muted-foreground">{isChatSending ? 'Live execution' : 'Execution details'}</span>
                </div>
                {visibleStreamEvents.map((entry) => <div key={entry.id}>{renderStreamEvent(entry)}</div>)}
              </article>
            ) : null}
          </div>
        </div>

        <form
          className="shrink-0 p-4 border-t border-border/40 bg-card/95"
          onSubmit={(event) => {
            event.preventDefault()
            void sendWorkflowChat()
          }}
        >
          <div className="flex items-center gap-3">
            <textarea
              className="flex-1 min-h-[44px] max-h-[160px] p-3 text-sm leading-relaxed rounded-2xl border border-input shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 resize-none bg-background [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
              value={chatDraft}
              onChange={(event) => {
                setChatDraft(event.target.value)
                event.target.style.height = 'auto'
                event.target.style.height = `${event.target.scrollHeight}px`
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  void sendWorkflowChat()
                }
              }}
              placeholder="Message Lightflare..."
              disabled={isChatSending || !workflow}
              rows={1}
            />
            <Button type="submit" size="icon" className="h-10 w-10 shrink-0 rounded-full shadow-md" disabled={isChatSending || !chatDraft.trim() || !workflow}>
              {isChatSending ? <div className="w-4 h-4 border-2 border-primary-foreground/20 border-t-primary-foreground rounded-full animate-spin" /> : <Send className="h-5 w-5" />}
            </Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  )
}
