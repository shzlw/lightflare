import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'

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
import { request, streamRequest } from '@/lib/api'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'
import { Archive, MoreHorizontal, Trash2, Search, Plus, ChevronLeft, ChevronRight, Send, Eye, EyeOff, MessageSquare } from 'lucide-react'

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
  sessionId: string
  payload: T
}

type ChatStreamMessageStartEvent = {
  messageId: string
  sessionId: string
  source: string
}

type ChatStreamMessageCompleteEvent = {
  messageId: string
  sessionId: string
  source: string
  content: string
  createdAt: string | null
}

type ChatStreamMessageErrorEvent = {
  sessionId: string
  message: string
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

export default function ChatsPage() {
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
  const messageListRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    void loadSessions(page, query, sessionPageSize)
  }, [page, query, sessionPageSize])

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
              const payload = data.payload as { content: string | null }
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

  function renderStreamEvent(entry: StreamTimelineEntry) {
    switch (entry.type) {
      case 'message_start':
        return <p>Assistant response started. Message id: {entry.messageId}</p>
      case 'plan_created':
        return (
          <>
            <p className="font-medium">
              Planned {entry.steps.length} step{entry.steps.length === 1 ? '' : 's'}
              {entry.selectedSkill ? ` using ${entry.selectedSkill}` : ''}.
            </p>
            {entry.thoughtProcess ? <p className="mt-1 opacity-90 break-words line-clamp-3 hover:line-clamp-none transition-all">{entry.thoughtProcess}</p> : null}
            {entry.steps.length > 0 ? (
              <ol className="mt-2 space-y-1 list-decimal list-inside opacity-80">
                {entry.steps.map((step) => (
                  <li key={step.id} className="break-words">
                    <span className="font-mono text-[10px] font-bold">{step.id}</span>: {step.content}
                  </li>
                ))}
              </ol>
            ) : null}
          </>
        )
      case 'step_started':
        return <p>Started step {entry.stepId}: {entry.content}</p>
      case 'step_progress':
        return (
          <p className="break-words">
            {entry.stepId ? `Step ${entry.stepId}` : 'Step'} {entry.progressType}: {entry.message?.trim() || 'In progress'}
          </p>
        )
      case 'step_completed':
        return (
          <>
            <p>Completed step {entry.stepId} with status {entry.status}.</p>
            {entry.terminalResponse ? <p className="mt-1 font-mono text-[10px] bg-background/40 p-1.5 rounded border border-border/20 break-all whitespace-pre-wrap max-h-32 overflow-y-auto">{entry.terminalResponse}</p> : null}
          </>
        )
      case 'final_response':
        return (
          <>
            <p className="font-bold">Final response</p>
            {entry.content ? <p className="mt-1 break-words whitespace-pre-wrap">{entry.content}</p> : null}
          </>
        )
      case 'message_complete':
        return <p>Assistant message persisted. Message id: {entry.messageId}</p>
      case 'message_error':
        return <p>{entry.message}</p>
    }
  }

  return (
    <div className="w-full h-screen max-h-screen flex flex-col animate-in fade-in duration-500 overflow-hidden">
      <header className="shrink-0 px-6 md:px-8 pt-6 md:pt-8 pb-4">
        <div className="flex items-center gap-3">
          <MessageSquare className="h-8 w-8 text-primary" />
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Chats</h2>
        </div>
      </header>

      <section className="flex-1 min-h-0 px-6 md:px-8 pb-6 md:pb-8 grid gap-4 overflow-hidden grid-cols-1">
        {/* Main: sessions sidebar + chat thread */}
        <div className="grid grid-cols-[minmax(260px,340px)_1fr] gap-4 min-h-0 overflow-hidden">

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
            </div>


            <div className="shrink-0 pt-3 flex items-center justify-between text-xs text-muted-foreground w-full max-w-3xl mx-auto px-4">
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
              <div className="w-full max-w-3xl mx-auto flex flex-col gap-3">
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
                  <article className="rounded-lg border border-border/40 p-4 bg-muted/20">
                    <div className="flex items-center justify-between gap-3 mb-2">
                      <Badge variant="outline">stream</Badge>
                      <span className="text-xs text-muted-foreground">
                        {isSending ? 'Live execution' : 'Execution details'}
                      </span>
                    </div>
                    <div className="space-y-2">
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-xs text-muted-foreground">
                          {hasCompletedStream
                            ? `Execution trace saved with ${visibleStreamEvents.length} events.`
                            : `Execution trace in progress with ${visibleStreamEvents.length} events.`}
                        </span>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="h-8 gap-2 rounded-lg"
                          onClick={() => setIsStreamDetailsExpanded((current) => !current)}
                        >
                          {isStreamDetailsExpanded ? (
                            <>
                              <EyeOff className="h-3.5 w-3.5" />
                              Hide details
                            </>
                          ) : (
                            <>
                              <Eye className="h-3.5 w-3.5" />
                              Show details
                            </>
                          )}
                        </Button>
                      </div>
                      {isStreamDetailsExpanded ? (
                        <div className="space-y-3 text-sm text-foreground/80 mt-4 pt-4 border-t border-border/20 overflow-hidden">
                          {visibleStreamEvents.map((entry) => (
                            <div key={entry.id} className="max-w-full overflow-hidden">{renderStreamEvent(entry)}</div>
                          ))}
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
        </div>
      </section>

      <Toaster />
    </div>
  )
}
