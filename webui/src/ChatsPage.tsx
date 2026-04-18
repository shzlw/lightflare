import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { NavLink, useSearchParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemFooter,
  ItemGroup,
  ItemHeader,
  ItemTitle,
} from '@/components/ui/item'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  listWorkflowTriggers,
  listWorkflows,
  request,
  streamRequest,
  type Workflow,
  type WorkflowTrigger,
} from '@/lib/api'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'
import { Archive, MoreHorizontal, Trash2, Search, Plus, ChevronLeft, ChevronRight, Send, Eye, EyeOff, Brain, ListTodo, Play, CheckCircle2, Zap, AlertCircle, Terminal, FileText, Loader2, Info, RefreshCw, Workflow as WorkflowIcon } from 'lucide-react'

type ChatSession = {
  id: string
  title: string | null
  userId: string | null
  totalTokens: number | null
  totalInputTokens: number | null
  totalOutputTokens: number | null
  status: string | null
  createdAt: string
  updatedAt: string
}

type ChatSessionPageResponse = {
  items: ChatSession[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

type ChatMessage = {
  id: string
  sessionId: string
  source: string | null
  content: string
  createdAt: string
}

type ChatMessagePageResponse = {
  items: ChatMessage[]
  nextBefore: string | null
  hasMore: boolean
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
  executionId: string
  payload: T
}

type ChatStreamMessageStartEvent = {
  messageId: string
  executionId: string
  source: string
}

type ChatStreamMessageCompleteEvent = {
  messageId: string
  executionId: string
  source: string
  content: string
  createdAt: string | null
}

type ChatStreamMessageErrorEvent = {
  executionId: string
  message: string
}

type ChatStreamPlanCreatedEvent = {
  executionId: string
  thoughtProcess: string | null
  selectedSkill: string | null
  steps: Array<{ id: string; content: string; status: string | null }>
}

type ChatStreamStepEvent = {
  executionId: string
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
  | {
      id: string
      type: 'step_progress'
      stepId: string | null
      progressType: string
      message: string | null
    }
  | { id: string; type: 'step_completed'; stepId: string; status: string; terminalResponse: string | null }
  | { id: string; type: 'final_response'; content: string | null }
  | { id: string; type: 'message_complete'; messageId: string }
  | { id: string; type: 'message_error'; message: string }

const pageSize = 20
const messagePageSize = 10
const sessionPageSizeOptions = [10, 20, 50] as const

type WorkflowDefinition = {
  steps?: Array<Record<string, unknown>>
  triggers?: Array<Record<string, unknown>>
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

function formatTokenCount(value: number | null) {
  if (value === null || Number.isNaN(value)) {
    return '0'
  }
  if (value < 1_000) {
    return String(value)
  }
  if (value < 1_000_000) {
    return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}k`
  }
  return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`
}

function formatTokenSummary(total: number | null, input: number | null, output: number | null) {
  return `${formatTokenCount(total)} total · ${formatTokenCount(input)} in · ${formatTokenCount(output)} out`
}

function deriveSessionTitle(content: string) {
  const compact = content.trim().replace(/\s+/g, ' ')
  if (!compact) {
    return 'New chat'
  }
  return compact.length > 48 ? `${compact.slice(0, 48)}...` : compact
}

function optimisticMessage(sessionId: string, source: string, content: string): ChatMessage {
  return {
    id: `temp-${crypto.randomUUID()}`,
    sessionId,
    source,
    content,
    createdAt: new Date().toISOString(),
  }
}

function scrollMessageListToBottom(container: HTMLDivElement | null) {
  if (!container) {
    return
  }
  container.scrollTop = container.scrollHeight
}

function parseWorkflowDefinition(workflow: Workflow | null): WorkflowDefinition {
  const raw = workflow?.schemaDefinition || workflow?.definitionJson
  if (!raw) return { steps: [], triggers: [] }
  try {
    const parsed = JSON.parse(raw) as WorkflowDefinition
    return {
      steps: Array.isArray(parsed.steps) ? parsed.steps : [],
      triggers: Array.isArray(parsed.triggers) ? parsed.triggers : [],
    }
  } catch {
    return { steps: [], triggers: [] }
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

function workflowStepId(step: Record<string, unknown>, index: number) {
  return String(step.id || step.stepId || `step_${index + 1}`)
}

function isWorkflowCreationText(value: string | null | undefined) {
  const normalized = value?.toLowerCase() ?? ''
  return /\b(create|new|build|make|draft)\b/.test(normalized) && /\bworkflow|workflows\b/.test(normalized)
}

function isWorkflowPlan(steps: Array<{ content: string }>) {
  return steps.some((step) => isWorkflowCreationText(step.content))
}

export default function ChatsPage() {
  const [searchParams] = useSearchParams()
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [nextBefore, setNextBefore] = useState<string | null>(null)
  const [hasMoreMessages, setHasMoreMessages] = useState(false)
  const [draft, setDraft] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [sessionPageSize, setSessionPageSize] = useState(pageSize)
  const [totalItems, setTotalItems] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isSessionListLoading, setIsSessionListLoading] = useState(true)
  const [isMessagesLoading, setIsMessagesLoading] = useState(false)
  const [isLoadingOlderMessages, setIsLoadingOlderMessages] = useState(false)
  const [isSending, setIsSending] = useState(false)
  const [activeSessionActionId, setActiveSessionActionId] = useState<string | null>(null)
  const [isArchivingSession, setIsArchivingSession] = useState(false)
  const [isDeletingSession, setIsDeletingSession] = useState(false)
  const [streamEvents, setStreamEvents] = useState<StreamTimelineEntry[]>([])
  const [retainedStreamEvents, setRetainedStreamEvents] = useState<StreamTimelineEntry[]>([])
  const [isStreamDetailsExpanded, setIsStreamDetailsExpanded] = useState(false)
  const [isWorkflowPanelOpen, setIsWorkflowPanelOpen] = useState(false)
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string | null>(null)
  const [workflowTriggers, setWorkflowTriggers] = useState<WorkflowTrigger[]>([])
  const [isWorkflowPanelLoading, setIsWorkflowPanelLoading] = useState(false)
  const messageListRef = useRef<HTMLDivElement | null>(null)
  const shouldRefreshWorkflowPanelRef = useRef(false)

  useEffect(() => {
    void loadSessions(page, query, sessionPageSize)
  }, [page, query, sessionPageSize])

  useEffect(() => {
    if (searchParams.get('workflowMode') === '1') {
      setIsWorkflowPanelOpen(true)
    }
    const workflowId = searchParams.get('workflowId')
    if (workflowId) {
      setSelectedWorkflowId(workflowId)
    }
  }, [searchParams])

  useEffect(() => {
    if (isWorkflowPanelOpen) {
      void loadWorkflowPanel()
    }
  }, [isWorkflowPanelOpen])

  useEffect(() => {
    if (isWorkflowPanelOpen && selectedWorkflowId) {
      void loadWorkflowTriggers(selectedWorkflowId)
    }
  }, [isWorkflowPanelOpen, selectedWorkflowId])

  async function loadWorkflowPanel(options: { selectLatest?: boolean } = {}) {
    setIsWorkflowPanelLoading(true)
    let nextSelectedWorkflowId: string | null = null
    try {
      const data = await listWorkflows()
      setWorkflows(data)
      setSelectedWorkflowId((current) => {
        if (!options.selectLatest && current && data.some((workflow) => workflow.id === current)) {
          nextSelectedWorkflowId = current
          return current
        }
        nextSelectedWorkflowId = data[0]?.id ?? null
        return nextSelectedWorkflowId
      })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load workflows.')
    } finally {
      setIsWorkflowPanelLoading(false)
    }
    return nextSelectedWorkflowId
  }

  async function loadWorkflowTriggers(workflowId: string) {
    try {
      setWorkflowTriggers(await listWorkflowTriggers(workflowId))
    } catch {
      setWorkflowTriggers([])
    }
  }

  async function refreshWorkflowPanel() {
    const workflowId = await loadWorkflowPanel()
    if (workflowId) {
      await loadWorkflowTriggers(workflowId)
    }
  }

  async function loadSessions(nextPage: number, nextQuery: string, nextPageSize: number) {
    setIsSessionListLoading(true)


    try {
      const params = new URLSearchParams({
        page: String(nextPage),
        size: String(nextPageSize),
      })
      if (nextQuery.trim()) {
        params.set('q', nextQuery.trim())
      }
      const data = await request<ChatSessionPageResponse>(
        `/internal-api/v1/chat-sessions?${params.toString()}`,
        { method: 'GET' },
      )

      setSessions(data.items)
      setPage(data.page)
      setSessionPageSize(data.size)
      setTotalItems(data.totalItems)
      setTotalPages(data.totalPages)

      if (data.items.length === 0) {
        setSelectedSessionId(null)
        setMessages([])
        setNextBefore(null)
        setHasMoreMessages(false)
        return
      }

      const selectedStillExists = data.items.some((session) => session.id === selectedSessionId)
      if (!selectedSessionId || !selectedStillExists) {
        await selectSession(data.items[0].id)
      }
    } catch (loadError) {
      setSessions([])
      setMessages([])
      setNextBefore(null)
      setHasMoreMessages(false)
      setSelectedSessionId(null)
      setTotalItems(0)
      setTotalPages(0)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load chat sessions.')
    } finally {
      setIsSessionListLoading(false)
    }
  }

  async function selectSession(sessionId: string, clearStream = true) {
    setSelectedSessionId(sessionId)
    setIsMessagesLoading(true)
    if (clearStream) {
      setStreamEvents([])
      setRetainedStreamEvents([])
      setIsStreamDetailsExpanded(false)
    }

    try {
      const data = await request<ChatMessagePageResponse>(
        `/internal-api/v1/chat-sessions/${sessionId}/messages?limit=${messagePageSize}`,
        { method: 'GET' },
      )
      setMessages(data.items)
      setNextBefore(data.nextBefore)
      setHasMoreMessages(data.hasMore)
      requestAnimationFrame(() => {
        scrollMessageListToBottom(messageListRef.current)
      })
    } catch (loadError) {
      setMessages([])
      setNextBefore(null)
      setHasMoreMessages(false)
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load chat messages.')
    } finally {
      setIsMessagesLoading(false)
    }
  }

  async function handleDeleteSession(sessionId: string) {
    setIsDeletingSession(true)

    try {
      await request<void>(`/internal-api/v1/chat-sessions/${sessionId}`, {
        method: 'DELETE',
      })

      setSessions((current) => current.filter((session) => session.id !== sessionId))
      setTotalItems((current) => Math.max(0, current - 1))
      setActiveSessionActionId(null)

      if (selectedSessionId === sessionId) {
        const remainingSessions = sessions.filter((session) => session.id !== sessionId)
        if (remainingSessions.length > 0) {
          await selectSession(remainingSessions[0].id)
        } else {
          startNewDraft()
          await loadSessions(0, query, sessionPageSize)
        }
      } else {
        await loadSessions(page, query, sessionPageSize)
      }

      toast.success('Chat session deleted.')
    } catch (deleteError) {
      toast.error(deleteError instanceof Error ? deleteError.message : 'Failed to delete chat session.')
    } finally {
      setIsDeletingSession(false)
    }
  }

  async function handleArchiveSession(sessionId: string) {
    setIsArchivingSession(true)

    try {
      await request<void>(`/internal-api/v1/chat-sessions/${sessionId}/archive`, {
        method: 'POST',
      })

      setSessions((current) => current.filter((session) => session.id !== sessionId))
      setTotalItems((current) => Math.max(0, current - 1))
      setActiveSessionActionId(null)

      if (selectedSessionId === sessionId) {
        const remainingSessions = sessions.filter((session) => session.id !== sessionId)
        if (remainingSessions.length > 0) {
          await selectSession(remainingSessions[0].id)
        } else {
          startNewDraft()
          await loadSessions(0, query, sessionPageSize)
        }
      } else {
        await loadSessions(page, query, sessionPageSize)
      }

      toast.success('Chat session archived.')
    } catch (archiveError) {
      toast.error(archiveError instanceof Error ? archiveError.message : 'Failed to archive chat session.')
    } finally {
      setIsArchivingSession(false)
    }
  }

  async function loadOlderMessages() {
    if (!selectedSessionId || !nextBefore || !hasMoreMessages || isMessagesLoading || isLoadingOlderMessages) {
      return
    }

    const container = messageListRef.current
    const previousScrollHeight = container?.scrollHeight ?? 0
    const previousScrollTop = container?.scrollTop ?? 0

    setIsLoadingOlderMessages(true)
    try {
      const data = await request<ChatMessagePageResponse>(
        `/internal-api/v1/chat-sessions/${selectedSessionId}/messages?limit=${messagePageSize}&before=${encodeURIComponent(nextBefore)}`,
        { method: 'GET' },
      )
      setMessages((current) => [...data.items, ...current])
      setNextBefore(data.nextBefore)
      setHasMoreMessages(data.hasMore)
      requestAnimationFrame(() => {
        const nextContainer = messageListRef.current
        if (!nextContainer) {
          return
        }
        nextContainer.scrollTop = nextContainer.scrollHeight - previousScrollHeight + previousScrollTop
      })
    } catch (loadError) {
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load older chat messages.')
    } finally {
      setIsLoadingOlderMessages(false)
    }
  }

  async function createSession(title: string) {
    return request<ChatSession>('/internal-api/v1/chat-sessions', {
      method: 'POST',
      body: JSON.stringify({ title }),
    })
  }

  function startNewDraft() {
    setSelectedSessionId(null)
    setMessages([])
    setNextBefore(null)
    setHasMoreMessages(false)
    setStreamEvents([])
    setRetainedStreamEvents([])
    setIsStreamDetailsExpanded(false)
    setIsMessagesLoading(false)
  }

  function handleMessageListScroll() {
    const container = messageListRef.current
    if (!container || container.scrollTop > 32) {
      return
    }

    void loadOlderMessages()
  }

  async function handleSend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const content = draft.trim()
    if (!content) {
      return
    }

    setIsSending(true)
    setStreamEvents([])
    setRetainedStreamEvents([])
    setIsStreamDetailsExpanded(true)
    shouldRefreshWorkflowPanelRef.current = isWorkflowCreationText(content)
    if (shouldRefreshWorkflowPanelRef.current) {
      setIsWorkflowPanelOpen(true)
    }
    let sessionIdForReload: string | null = selectedSessionId

    try {
      let sessionId = selectedSessionId
      if (!sessionId) {
        const session = await createSession(deriveSessionTitle(content))
        sessionId = session.id
        setSessions((current) => {
          if (current.some((item) => item.id === session.id)) {
            return current
          }
          return [session, ...current]
        })
        setTotalItems((current) => current + 1)
        setTotalPages((current) => Math.max(1, current))
        setSelectedSessionId(session.id)
      }
      sessionIdForReload = sessionId

      const userMessage = optimisticMessage(sessionId, 'user', content)
      setMessages((current) => [...current, userMessage])
      requestAnimationFrame(() => {
        scrollMessageListToBottom(messageListRef.current)
      })

      setDraft('')

      await streamRequest<ChatStreamEvent>(
        `/internal-api/v1/chat-sessions/${sessionId}/responses/stream`,
        {
          method: 'POST',
          body: JSON.stringify({
            source: 'user',
            content,
          }),
        },
        ({ data }) => {
          switch (data.type) {
            case 'MESSAGE_START': {
              const payload = data.payload as ChatStreamMessageStartEvent
              setStreamEvents((current) => [
                ...current,
                { id: crypto.randomUUID(), type: 'message_start', messageId: payload.messageId, source: payload.source },
              ])
              requestAnimationFrame(() => {
                scrollMessageListToBottom(messageListRef.current)
              })
              break
            }
            case 'PLAN_CREATED': {
              const payload = data.payload as ChatStreamPlanCreatedEvent
              if (isWorkflowPlan(payload.steps)) {
                shouldRefreshWorkflowPanelRef.current = true
                setIsWorkflowPanelOpen(true)
              }
              setStreamEvents((current) => [
                ...current,
                {
                  id: crypto.randomUUID(),
                  type: 'plan_created',
                  thoughtProcess: payload.thoughtProcess,
                  selectedSkill: payload.selectedSkill,
                  steps: payload.steps,
                },
              ])
              requestAnimationFrame(() => {
                scrollMessageListToBottom(messageListRef.current)
              })
              break
            }
            case 'STEP_STARTED': {
              const payload = data.payload as ChatStreamStepEvent
              const step = payload.step
              if (step) {
                setStreamEvents((current) => [
                  ...current,
                  { id: crypto.randomUUID(), type: 'step_started', stepId: step.id, content: step.content },
                ])
                requestAnimationFrame(() => {
                  scrollMessageListToBottom(messageListRef.current)
                })
              }
              break
            }
            case 'STEP_PROGRESS': {
              const payload = data.payload as ChatStreamStepProgressEvent
              setStreamEvents((current) => [
                ...current,
                {
                  id: crypto.randomUUID(),
                  type: 'step_progress',
                  stepId: payload.step?.id ?? null,
                  progressType: payload.progressType,
                  message: payload.message,
                },
              ])
              requestAnimationFrame(() => {
                scrollMessageListToBottom(messageListRef.current)
              })
              break
            }
            case 'STEP_COMPLETED': {
              const payload = data.payload as ChatStreamStepCompletedEvent
              const step = payload.step
              if (step) {
                setStreamEvents((current) => [
                  ...current,
                  {
                    id: crypto.randomUUID(),
                    type: 'step_completed',
                    stepId: step.id,
                    status: payload.status,
                    terminalResponse: payload.terminalResponse,
                  },
                ])
                requestAnimationFrame(() => {
                  scrollMessageListToBottom(messageListRef.current)
                })
              }
              break
            }
            case 'FINAL_RESPONSE': {
              const payload = data.payload as { executionId: string; content: string | null }
              setStreamEvents((current) => [
                ...current,
                { id: crypto.randomUUID(), type: 'final_response', content: payload.content },
              ])
              requestAnimationFrame(() => {
                scrollMessageListToBottom(messageListRef.current)
              })
              break
            }
            case 'MESSAGE_COMPLETE': {
              const payload = data.payload as ChatStreamMessageCompleteEvent
              setStreamEvents((current) => {
                const nextEvents = [
                  ...current,
                  { id: crypto.randomUUID(), type: 'message_complete', messageId: payload.messageId } as StreamTimelineEntry,
                ]
                setRetainedStreamEvents(nextEvents)
                return nextEvents
              })
              requestAnimationFrame(() => {
                scrollMessageListToBottom(messageListRef.current)
              })
              if (isWorkflowPanelOpen || shouldRefreshWorkflowPanelRef.current) {
                void loadWorkflowPanel({ selectLatest: shouldRefreshWorkflowPanelRef.current }).then((workflowId) => {
                  if (workflowId) {
                    void loadWorkflowTriggers(workflowId)
                  }
                })
              }
              shouldRefreshWorkflowPanelRef.current = false
              break
            }
            case 'MESSAGE_ERROR': {
              const payload = data.payload as ChatStreamMessageErrorEvent
              setStreamEvents((current) => {
                const nextEvents = [
                  ...current,
                  { id: crypto.randomUUID(), type: 'message_error', message: payload.message } as StreamTimelineEntry,
                ]
                setRetainedStreamEvents(nextEvents)
                return nextEvents
              })
              throw new Error(payload.message)
            }
          }
        },
      )

      await loadSessions(page, query, sessionPageSize)
      await selectSession(sessionId, false)
      setIsStreamDetailsExpanded(false)
    } catch (sendError) {
      if (sessionIdForReload) {
        await selectSession(sessionIdForReload, false)
      }
      toast.error(sendError instanceof Error ? sendError.message : 'Failed to send message.')
    } finally {
      setIsSending(false)
    }
  }

  const selectedSession = sessions.find((session) => session.id === selectedSessionId) ?? null
  const hasActiveConversation = selectedSessionId !== null || messages.length > 0
  const visibleStreamEvents = streamEvents.length > 0 ? streamEvents : retainedStreamEvents
  const hasCompletedStream = visibleStreamEvents.some(
    (entry) => entry.type === 'message_complete' || entry.type === 'message_error',
  )
  const selectedWorkflow = workflows.find((workflow) => workflow.id === selectedWorkflowId) ?? null
  const selectedWorkflowDefinition = parseWorkflowDefinition(selectedWorkflow)

  function renderStreamEvent(entry: StreamTimelineEntry) {
    const iconProps = { className: 'h-3.5 w-3.5' }

    switch (entry.type) {
      case 'message_start':
        return (
          <div className="flex items-center gap-2 py-1 px-2 rounded-md bg-muted/30 border border-border/20">
            <Info {...iconProps} className="h-3.5 w-3.5 text-blue-500" />
            <span className="text-xs font-medium text-muted-foreground italic">
              Assistant strategy initialized.
            </span>
          </div>
        )
      case 'plan_created':
        return (
          <div className="space-y-2 p-3 rounded-lg border border-border/40 bg-card/50 shadow-sm">
            <div className="flex items-center gap-2">
              <div className="p-1 rounded-md bg-primary/10 text-primary">
                <Brain className="h-3.5 w-3.5" />
              </div>
              <h4 className="text-sm font-semibold tracking-tight">Execution Strategy</h4>
            </div>
            
            {entry.thoughtProcess && (
              <div className="text-xs leading-snug text-muted-foreground bg-muted/40 p-2 rounded-md border border-border/10">
                <p className="line-clamp-3 hover:line-clamp-none transition-all cursor-help">
                  {entry.thoughtProcess}
                </p>
              </div>
            )}

            <div className="space-y-1.5">
              <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground/70 px-1">
                <ListTodo className="h-3 w-3" />
                <span>Planned Steps ({entry.steps.length})</span>
                {entry.selectedSkill && (
                  <>
                    <span className="mx-1 opacity-40">/</span>
                    <span className="text-primary/80">{entry.selectedSkill}</span>
                  </>
                )}
              </div>
              <div className="grid gap-1">
                {entry.steps.map((step) => (
                  <div key={step.id} className="flex items-start gap-2 p-1.5 rounded-md hover:bg-muted/30 transition-colors group">
                    <span className="shrink-0 min-w-8 h-5 px-1 flex items-center justify-center rounded bg-muted text-[10px] font-mono font-bold group-hover:bg-primary/10 group-hover:text-primary transition-colors">
                      {step.id}
                    </span>
                    <span className="text-xs leading-snug">{step.content}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )
      case 'step_started':
        return (
          <div className="flex items-center gap-2 py-0.5 px-1">
            <div className="h-3.5 w-3.5 flex items-center justify-center">
              <Play className="h-3 w-3 text-primary animate-pulse" fill="currentColor" />
            </div>
            <p className="text-xs font-semibold">
              <span className="text-muted-foreground mr-1">Step {entry.stepId}:</span> {entry.content}
            </p>
          </div>
        )
      case 'step_progress':
        return (
          <div className="flex items-start gap-2 py-0.5 px-1 ml-0.5 border-l-2 border-border/20 pl-3">
            <div className="h-3.5 w-3.5 flex items-center justify-center shrink-0">
               {entry.progressType === 'executing' ? (
                 <Loader2 className="h-3 w-3 text-muted-foreground animate-spin" />
               ) : (
                 <Zap className="h-3 w-3 text-amber-500" />
               )}
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed italic">
              {entry.progressType !== 'executing' && <span className="font-bold mr-1 uppercase text-[9px] not-italic">{entry.progressType}</span>}
              {entry.message?.trim() || 'Processing...'}
            </p>
          </div>
        )
      case 'step_completed':
        return (
          <div className="ml-0.5 border-l-2 border-border/20 pl-3 space-y-1.5">
            <div className="flex items-center gap-2 px-2 py-1 rounded-md bg-green-500/5 border border-green-500/10 w-fit">
              <CheckCircle2 className="h-3 w-3 text-green-500" />
              <span className="text-[11px] font-bold text-green-600/80 uppercase tracking-tight">Step Result: {entry.status}</span>
            </div>
            {entry.terminalResponse ? (
               <div className="relative group">
                <div className="absolute left-[-13px] top-3 h-px w-2 bg-border/40" />
                <div className="rounded-lg border border-border/40 bg-zinc-950 p-0 overflow-hidden shadow-sm">
                  <div className="flex items-center justify-between px-2 py-1 bg-zinc-900 border-b border-zinc-800">
                    <div className="flex items-center gap-2">
                      <Terminal className="h-3 w-3 text-zinc-400" />
                      <span className="text-[10px] font-mono text-zinc-400 font-bold uppercase tracking-wider">Output</span>
                    </div>
                  </div>
                  <pre className="p-2 font-mono text-[11px] leading-snug text-zinc-300 break-all whitespace-pre-wrap max-h-40 overflow-y-auto [scrollbar-width:thin] scrollbar-color-zinc-800">
                    {entry.terminalResponse}
                  </pre>
                </div>
              </div>
            ) : null}
          </div>
        )
      case 'final_response':
        return (
          <div className="space-y-2 p-3 rounded-lg border-2 border-primary/20 bg-primary/5 shadow-md mt-2">
            <div className="flex items-center gap-2">
              <div className="p-1 rounded-md bg-primary text-primary-foreground">
                <FileText className="h-3.5 w-3.5" />
              </div>
              <h4 className="text-sm font-bold tracking-tight">Finalized Intelligence</h4>
            </div>
            {entry.content && (
              <div className="text-sm leading-snug whitespace-pre-wrap font-medium">
                {entry.content}
              </div>
            )}
          </div>
        )
      case 'message_complete':
        return (
          <div className="flex items-center gap-2 py-1 px-2 rounded-md bg-primary/5 border border-primary/10 mt-1">
            <CheckCircle2 {...iconProps} className="h-3.5 w-3.5 text-primary" />
            <span className="text-[11px] font-bold text-primary uppercase tracking-wider">
              Message archived successfully
            </span>
          </div>
        )
      case 'message_error':
        return (
          <div className="flex items-start gap-2 p-3 rounded-lg border border-destructive/20 bg-destructive/5 text-destructive mt-1">
            <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
            <div className="space-y-0.5">
              <p className="text-xs font-bold uppercase tracking-wider">Execution Error</p>
              <p className="text-sm font-medium leading-snug">{entry.message}</p>
            </div>
          </div>
        )
    }
  }

  return (
    <div className="w-full h-screen max-h-screen flex flex-col animate-in fade-in duration-500 overflow-hidden">
      <header className="shrink-0 px-6 md:px-8 pt-6 md:pt-8 pb-4">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Chats</h2>
        </div>
      </header>

      <section className="flex-1 min-h-0 px-6 md:px-8 pb-6 md:pb-8 grid gap-4 overflow-hidden grid-cols-1">
        {/* Main: sessions sidebar + chat thread */}
        <div className={`grid gap-4 min-h-0 overflow-hidden ${
          isWorkflowPanelOpen
            ? 'grid-cols-[minmax(220px,280px)_minmax(0,1fr)_minmax(460px,520px)]'
            : 'grid-cols-[minmax(260px,340px)_minmax(0,1fr)]'
        }`}>

          {/* Sessions sidebar */}
          <aside className="flex flex-col min-h-0 rounded-xl border border-border/40 bg-card">
            <div className="shrink-0 p-4 border-b border-border/40 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold tracking-tight text-muted-foreground uppercase">Chat Sessions</h3>
                <Badge variant="secondary" className="font-mono text-[10px]">{totalItems}</Badge>
              </div>
              
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    className="h-10 pl-9 rounded-lg text-sm bg-muted/30 border-none shadow-none focus-visible:ring-1"
                    value={query}
                    onChange={(event) => {
                      setPage(0)
                      setQuery(event.target.value)
                    }}
                    placeholder="Search conversations..."
                  />
                </div>
                <Button 
                  size="icon" 
                  className="shrink-0 h-10 w-10 rounded-lg shadow-sm"
                  onClick={startNewDraft}
                  title="New chat draft"
                >
                  <Plus className="h-5 w-5" />
                </Button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-3">
              {isSessionListLoading ? <p className="text-sm text-muted-foreground p-2">Loading sessions...</p> : null}
              {!isSessionListLoading && sessions.length === 0 ? <p className="text-sm text-muted-foreground p-2">No chat sessions found.</p> : null}

              <ItemGroup aria-label="Chat session list">
                {sessions.map((session) => (
                  <Item
                    key={session.id}
                    variant="outline"
                    className={`cursor-pointer ${session.id === selectedSessionId ? 'bg-muted/60' : ''}`}
                    size="sm"
                    role="button"
                    tabIndex={0}
                    onClick={() => void selectSession(session.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault()
                        void selectSession(session.id)
                      }
                    }}
                  >
                    <ItemContent>
                      <ItemHeader className="gap-2">
                        <ItemTitle>
                          {session.title?.trim() || 'Untitled session'}
                        </ItemTitle>
                        <Popover
                          open={activeSessionActionId === session.id}
                          onOpenChange={(open) => setActiveSessionActionId(open ? session.id : null)}
                        >
                          <PopoverTrigger asChild>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8 shrink-0"
                              aria-label={`Open actions for ${session.title?.trim() || 'Untitled session'}`}
                              onPointerDown={(event) => {
                                event.stopPropagation()
                              }}
                              onClick={(event) => {
                                event.stopPropagation()
                              }}
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </PopoverTrigger>
                          <PopoverContent
                            align="end"
                            className="w-56"
                            onClick={(event) => event.stopPropagation()}
                          >
                            <div className="space-y-2">
                              <Button
                                type="button"
                                variant="outline"
                                className="w-full justify-start"
                                disabled={isArchivingSession || isDeletingSession}
                                onClick={(event) => {
                                  event.preventDefault()
                                  event.stopPropagation()
                                  void handleArchiveSession(session.id)
                                }}
                              >
                                <Archive className="h-4 w-4" />
                                {isArchivingSession && activeSessionActionId === session.id ? 'Archiving...' : 'Archive'}
                              </Button>
                              <Button
                                type="button"
                                variant="destructive"
                                className="w-full justify-start"
                                disabled={isDeletingSession}
                                onClick={(event) => {
                                  event.preventDefault()
                                  event.stopPropagation()
                                  void handleDeleteSession(session.id)
                                }}
                              >
                                <Trash2 className="h-4 w-4" />
                                {isDeletingSession && activeSessionActionId === session.id ? 'Deleting...' : 'Delete'}
                              </Button>
                            </div>
                          </PopoverContent>
                        </Popover>
                      </ItemHeader>
                      <ItemDescription>
                        Updated {formatDate(session.updatedAt)}
                      </ItemDescription>
                      <ItemFooter>
                        <ItemDescription>
                          <span title={`${session.totalTokens ?? 0} total tokens`}>
                            {formatTokenSummary(
                              session.totalTokens,
                              session.totalInputTokens,
                              session.totalOutputTokens,
                            )}
                          </span>
                        </ItemDescription>
                      </ItemFooter>
                    </ItemContent>
                  </Item>
                ))}
              </ItemGroup>
            </div>

            <div className="shrink-0 p-2 border-t border-border/40 flex items-center justify-between gap-1">
              <div className="flex items-center bg-muted/30 rounded-lg p-0.5">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 rounded-md"
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                  disabled={page === 0 || isSessionListLoading}
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                </Button>
                <span className="text-[10px] font-mono font-bold text-muted-foreground px-2 min-w-[50px] text-center">
                  {totalPages === 0 ? 0 : page + 1}/{Math.max(totalPages, 1)}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 rounded-md"
                  onClick={() => setPage((current) => current + 1)}
                  disabled={isSessionListLoading || totalPages === 0 || page >= totalPages - 1}
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </Button>
              </div>

              <Select
                value={String(sessionPageSize)}
                onValueChange={(value) => {
                  setPage(0)
                  setSessionPageSize(Number(value))
                }}
              >
                <SelectTrigger className="h-8 w-auto border-none bg-transparent hover:bg-muted/50 text-[10px] font-bold uppercase tracking-wider shadow-none focus:ring-0 px-2 rounded-lg">
                  <span className="text-muted-foreground mr-1 font-semibold">Size:</span>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {sessionPageSizeOptions.map((option) => (
                    <SelectItem key={option} value={String(option)} className="text-xs">
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </aside>

          {/* Chat thread */}
          <section className="flex flex-col min-h-0 rounded-xl border border-border/40 bg-card overflow-hidden">
            <div className="shrink-0 p-4 border-b border-border/40 flex items-center justify-between gap-4">
              <div>
                <p className="text-xs font-semibold tracking-wider uppercase text-muted-foreground">Conversation</p>
                <h3 className="text-lg font-bold tracking-tight">{selectedSession?.title?.trim() || 'New chat draft'}</h3>
              </div>
              <Button
                type="button"
                variant={isWorkflowPanelOpen ? 'default' : 'outline'}
                size="sm"
                className="gap-2"
                onClick={() => setIsWorkflowPanelOpen((current) => !current)}
              >
                <WorkflowIcon className="h-4 w-4" />
                Workflow
              </Button>
            </div>


            <div className="shrink-0 pt-3 flex items-center justify-between text-xs text-muted-foreground w-full max-w-5xl mx-auto px-4">
              <span>
                {selectedSession
                  ? `Updated ${formatDate(selectedSession.updatedAt)} · ${formatTokenSummary(
                      selectedSession.totalTokens,
                      selectedSession.totalInputTokens,
                      selectedSession.totalOutputTokens,
                    )}`
                  : 'Compose the first message to create a session.'}
              </span>
              {hasActiveConversation ? <span>{messages.length} messages loaded</span> : null}
            </div>

            {/* Messages area */}
            <div
              ref={messageListRef}
              className="flex-1 overflow-y-auto min-h-0 p-4"
              aria-label="Chat messages"
              onScroll={handleMessageListScroll}
            >
              <div className="w-full max-w-5xl mx-auto flex flex-col gap-3">
                {isMessagesLoading ? <p className="text-sm text-muted-foreground">Loading messages...</p> : null}
                {!isMessagesLoading && isLoadingOlderMessages ? <p className="text-sm text-muted-foreground">Loading older messages...</p> : null}
                {!isMessagesLoading && hasActiveConversation && messages.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No messages in this session yet.</p>
                ) : null}
                {!hasActiveConversation ? <p className="text-sm text-muted-foreground">Start typing below to create a new chat session.</p> : null}

                {!isMessagesLoading && hasActiveConversation
                  ? messages.map((message) => (
                      <article key={message.id} className="rounded-lg border border-border/40 p-4 bg-card shadow-sm">
                        <div className="flex items-center justify-between gap-3 mb-2">
                          <Badge variant={message.source === 'user' ? 'default' : 'secondary'}>{formatValue(message.source)}</Badge>
                          <span className="text-xs text-muted-foreground">{formatDate(message.createdAt)}</span>
                        </div>
                        <p className="text-sm leading-relaxed whitespace-pre-wrap break-words">{message.content}</p>
                      </article>
                    ))
                  : null}

                {visibleStreamEvents.length > 0 ? (
                  <article className="rounded-lg border border-border/40 p-3 bg-muted/20">
                    <div className="flex items-center justify-between gap-3 mb-1.5">
                      <Badge variant="outline">stream</Badge>
                      <span className="text-xs text-muted-foreground">
                        {isSending ? 'Live execution' : 'Execution details'}
                      </span>
                    </div>
                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                          {isSending ? (
                            <div className="h-1.5 w-1.5 rounded-full bg-primary animate-pulse shadow-[0_0_8px_var(--color-primary)] opacity-80" />
                          ) : (
                            <div className="h-1.5 w-1.5 rounded-full bg-muted-foreground/40" />
                          )}
                          <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider">
                            {hasCompletedStream
                              ? `${visibleStreamEvents.length} events traced`
                              : `Streaming execution trace (${visibleStreamEvents.length})`}
                          </span>
                        </div>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-8 gap-2 rounded-lg text-xs hover:bg-muted/50"
                          onClick={() => setIsStreamDetailsExpanded((current) => !current)}
                        >
                          {isStreamDetailsExpanded ? (
                            <>
                              <EyeOff className="h-3.5 w-3.5" />
                              Hide log
                            </>
                          ) : (
                            <>
                              <Eye className="h-3.5 w-3.5" />
                              View log
                            </>
                          )}
                        </Button>
                      </div>
                      {isStreamDetailsExpanded ? (
                        <div className="relative mt-2 pt-2 border-t border-border/20">
                          <div className="absolute left-[3px] top-4 bottom-0 w-px bg-gradient-to-b from-border/60 via-border/20 to-transparent" />
                          <div className="space-y-2 text-sm text-foreground/90">
                            {visibleStreamEvents.map((entry) => (
                              <div key={entry.id} className="relative pl-4">
                                <div className="absolute left-[-1px] top-[10px] h-2 w-2 rounded-full border-2 border-background bg-border/60" />
                                <div className="max-w-full overflow-hidden">{renderStreamEvent(entry)}</div>
                              </div>
                            ))}
                          </div>
                        </div>
                      ) : null}
                    </div>
                  </article>
                ) : null}
              </div>
            </div>

            {/* Composer */}
            <form className="shrink-0 p-4 border-t border-border/40 sticky bottom-0 bg-card/95 backdrop-blur supports-[backdrop-filter]:bg-card/60 z-10" onSubmit={handleSend}>
              <div className="w-full max-w-4xl mx-auto flex items-center gap-3">
                <Button 
                  type="button" 
                  variant="ghost" 
                  size="icon" 
                  className="h-10 w-10 shrink-0 rounded-full text-muted-foreground hover:bg-muted"
                >
                  <Plus className="h-5 w-5" />
                </Button>
                
                <div className="flex-1 relative">
                  <textarea
                    className="w-full min-h-[44px] max-h-[200px] p-3 text-sm leading-relaxed rounded-2xl border border-input shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 transition-all resize-none bg-background [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
                    value={draft}
                    onChange={(event) => {
                      setDraft(event.target.value)
                      // Auto-resize logic (simple)
                      event.target.style.height = 'auto'
                      event.target.style.height = `${event.target.scrollHeight}px`
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' && !event.shiftKey) {
                        event.preventDefault()
                        void handleSend(event as any)
                      }
                    }}
                    placeholder="Message Lightflare..."
                    rows={1}
                  />
                </div>

                <Button 
                  type="submit" 
                  size="icon" 
                  className="h-10 w-10 shrink-0 rounded-full shadow-md hover:shadow-lg transition-all hover:scale-105 active:scale-95" 
                  disabled={isSending || !draft.trim()}
                >
                  {isSending ? (
                    <div className="w-4 h-4 border-2 border-primary-foreground/20 border-t-primary-foreground rounded-full animate-spin" />
                  ) : (
                    <Send className="h-5 w-5" />
                  )}
                </Button>
              </div>
            </form>
          </section>

          {isWorkflowPanelOpen ? (
            <aside className="flex flex-col min-h-0 rounded-xl border border-border/40 bg-card overflow-hidden">
              <div className="shrink-0 p-4 border-b border-border/40 space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold tracking-wider uppercase text-muted-foreground">Workflow</p>
                    <h3 className="text-lg font-bold tracking-tight">Design Reference</h3>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="gap-2"
                      disabled={isWorkflowPanelLoading}
                      onClick={() => void refreshWorkflowPanel()}
                    >
                      <RefreshCw className={`h-3.5 w-3.5 ${isWorkflowPanelLoading ? 'animate-spin' : ''}`} />
                      Refresh
                    </Button>
                    <Button type="button" variant="ghost" size="sm" onClick={() => setIsWorkflowPanelOpen(false)}>
                      Hide
                    </Button>
                  </div>
                </div>
                {selectedWorkflow ? (
                  <div className="rounded-lg border border-border/40 bg-muted/30 p-3">
                    <p className="text-sm font-semibold">{selectedWorkflow.name || 'Untitled workflow'}</p>
                    <p className="mt-1 truncate font-mono text-[11px] text-muted-foreground">{selectedWorkflow.id}</p>
                  </div>
                ) : null}
              </div>

              <div className="flex-1 min-h-0 overflow-y-auto p-4 space-y-5">
                {isWorkflowPanelLoading ? <p className="text-sm text-muted-foreground">Loading workflows...</p> : null}
                {!isWorkflowPanelLoading && workflows.length === 0 ? (
                  <div className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
                    Ask chat to create a workflow.
                  </div>
                ) : null}

                {selectedWorkflow ? (
                  <>
                    <section className="space-y-2">
                      <div className="flex items-center justify-between gap-3">
                        <h4 className="text-sm font-semibold">Steps</h4>
                        <Badge variant="secondary">{selectedWorkflowDefinition.steps?.length ?? 0}</Badge>
                      </div>
                      <div className="space-y-2">
                        {selectedWorkflowDefinition.steps?.length ? selectedWorkflowDefinition.steps.map((step, index) => (
                          <div key={workflowStepId(step, index)} className="rounded-lg border border-border/40 p-3">
                            <div className="flex items-start justify-between gap-2">
                              <div>
                                <p className="text-sm font-semibold">{String(step.name || workflowStepId(step, index))}</p>
                                <p className="text-xs text-muted-foreground mt-1 line-clamp-2">
                                  {String(step.prompt || step.toolName || step.actionIdentifier || 'No action configured')}
                                </p>
                              </div>
                              <Badge variant="outline">{String(step.type || 'step')}</Badge>
                            </div>
                          </div>
                        )) : (
                          <p className="text-sm text-muted-foreground">No steps defined yet.</p>
                        )}
                      </div>
                    </section>

                    <section className="space-y-2">
                      <div className="flex items-center justify-between gap-3">
                        <h4 className="text-sm font-semibold">Triggers</h4>
                        <Badge variant="secondary">{workflowTriggers.length}</Badge>
                      </div>
                      <div className="space-y-2">
                        {workflowTriggers.length ? workflowTriggers.map((trigger) => (
                          <div key={trigger.id} className="rounded-lg border border-border/40 p-3">
                            <div className="flex items-center justify-between gap-2">
                              <div>
                                <p className="text-sm font-semibold">{trigger.name || trigger.triggerType}</p>
                                <p className="text-xs text-muted-foreground">{trigger.triggerType}</p>
                              </div>
                              <Badge variant="outline">{trigger.enabled ? 'enabled' : 'disabled'}</Badge>
                            </div>
                            <pre className="mt-2 max-h-36 overflow-auto rounded-md bg-muted/40 p-2 text-[11px] leading-relaxed">
                              {formatJson(trigger.configJson)}
                            </pre>
                          </div>
                        )) : (
                          <p className="text-sm text-muted-foreground">No triggers defined yet.</p>
                        )}
                      </div>
                    </section>

                    <Button asChild variant="outline" className="w-full gap-2">
                      <NavLink to={`/workspace/workflows/${selectedWorkflow.id}`}>
                        <WorkflowIcon className="h-4 w-4" />
                        Open Workflow
                      </NavLink>
                    </Button>
                  </>
                ) : null}
              </div>
            </aside>
          ) : null}
        </div>
      </section>

      <Toaster />
    </div>
  )
}
