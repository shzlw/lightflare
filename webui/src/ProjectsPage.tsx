import { useEffect, useMemo, useRef, useState } from 'react'

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
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { createProject, listProjects, request, streamRequest, updateProject, type Project } from '@/lib/api'
import { AlertCircle, Archive, Brain, CheckCircle2, ChevronDown, ChevronRight, FileText, FolderOpen, Info, ListTodo, Loader2, MessageSquarePlus, MoreHorizontal, Pencil, Play, Plus, Search, Send, Terminal, Trash2, Zap } from 'lucide-react'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'

type ChatSession = {
  id: string
  projectId: string
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

type ChatStreamMessageCompleteEvent = {
  messageId: string
  executionId: string
  source: string
  content: string
  createdAt: string | null
  artifactIds?: string[] | null
}

type ChatStreamMessageErrorEvent = {
  executionId: string
  message: string
}

type ChatStreamMessageStartEvent = {
  messageId: string
  executionId: string
  source: string
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

type ChatArtifact = {
  id: string
  sessionId: string
  messageId: string | null
  artifactType: string
  title: string | null
  content: string
  metadata: string | null
  pinned: boolean
  displayOrder: number
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

type ArtifactDraft = {
  artifactType: string
  title: string
  content: string
  metadata: string
}

function formatProjectTitle(project: Project) {
  return project.title?.trim() ? project.title : 'Untitled project'
}

function formatSessionTitle(session: ChatSession, index: number) {
  return session.title?.trim() ? session.title : `Chat ${index + 1}`
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

function deriveSessionTitle(content: string) {
  const compact = content.trim().replace(/\s+/g, ' ')
  if (!compact) {
    return 'New chat'
  }
  return compact.length > 36 ? `${compact.slice(0, 36)}...` : compact
}

function extractFencedBlock(content: string, language: string) {
  const match = content.match(new RegExp(`\\\`\\\`\\\`${language}\\s*([\\s\\S]*?)\\\`\\\`\\\``, 'i'))
  return match?.[1]?.trim() ?? null
}

function inferArtifactDraft(content: string): ArtifactDraft {
  const normalized = content.trim()
  const jsonBlock = extractFencedBlock(normalized, 'json')
  if (jsonBlock) {
    return {
      artifactType: 'json',
      title: 'Generated JSON',
      content: jsonBlock,
      metadata: '{"renderer":"json"}',
    }
  }

  const diffBlock = extractFencedBlock(normalized, 'diff')
  if (diffBlock) {
    return {
      artifactType: 'diff',
      title: 'Generated Diff',
      content: diffBlock,
      metadata: '{"renderer":"diff"}',
    }
  }

  if ((normalized.startsWith('{') && normalized.endsWith('}')) || (normalized.startsWith('[') && normalized.endsWith(']'))) {
    return {
      artifactType: 'json',
      title: 'Generated JSON',
      content: normalized,
      metadata: '{"renderer":"json"}',
    }
  }

  if (normalized.includes('```diff') || normalized.includes('@@') || /\n[+-][^\n]+/.test(normalized)) {
    return {
      artifactType: 'diff',
      title: 'Generated Diff',
      content: diffBlock ?? normalized,
      metadata: '{"renderer":"diff"}',
    }
  }

  if (/^\s*(\d+\.|- |\* )/m.test(normalized) && normalized.split('\n').filter((line) => /^\s*(\d+\.|- |\* )/.test(line)).length >= 3) {
    return {
      artifactType: 'plan',
      title: 'Generated Plan',
      content: normalized,
      metadata: '{"renderer":"plan"}',
    }
  }

  return {
    artifactType: 'text',
    title: 'Generated Artifact',
    content: normalized,
    metadata: '{"renderer":"text"}',
  }
}

function formatMessageTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

function scrollToBottom(container: HTMLDivElement | null) {
  if (!container) {
    return
  }
  container.scrollTop = container.scrollHeight
}

export default function ProjectsPage() {
  const messagePageSize = 50
  const defaultQuickChatProjectTitle = 'Untitled project'
  const [projects, setProjects] = useState<Project[]>([])
  const [isLoadingProjects, setIsLoadingProjects] = useState(true)
  const [isCreatingProject, setIsCreatingProject] = useState(false)
  const [isCreateProjectOpen, setIsCreateProjectOpen] = useState(false)
  const [projectSearch, setProjectSearch] = useState('')
  const [newProjectTitle, setNewProjectTitle] = useState('')
  const [newProjectDescription, setNewProjectDescription] = useState('')
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [isEditProjectOpen, setIsEditProjectOpen] = useState(false)
  const [editingProjectTitle, setEditingProjectTitle] = useState('')
  const [editingProjectDescription, setEditingProjectDescription] = useState('')
  const [isUpdatingProject, setIsUpdatingProject] = useState(false)
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [isLoadingSessions, setIsLoadingSessions] = useState(false)
  const [isCreatingSession, setIsCreatingSession] = useState(false)
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [nextBefore, setNextBefore] = useState<string | null>(null)
  const [hasMoreMessages, setHasMoreMessages] = useState(false)
  const [isLoadingMessages, setIsLoadingMessages] = useState(false)
  const [isLoadingOlderMessages, setIsLoadingOlderMessages] = useState(false)
  const [isArchivingSession, setIsArchivingSession] = useState(false)
  const [isDeletingSession, setIsDeletingSession] = useState(false)
  const [activeSessionActionId, setActiveSessionActionId] = useState<string | null>(null)
  const [editingSessionTitle, setEditingSessionTitle] = useState('')
  const [isRenamingSession, setIsRenamingSession] = useState(false)
  const [streamEvents, setStreamEvents] = useState<StreamTimelineEntry[]>([])
  const [retainedStreamEvents, setRetainedStreamEvents] = useState<StreamTimelineEntry[]>([])
  const [isLastRunTraceExpanded, setIsLastRunTraceExpanded] = useState(false)
  const [artifacts, setArtifacts] = useState<ChatArtifact[]>([])
  const [activeArtifactId, setActiveArtifactId] = useState<string | null>(null)
  const [pendingArtifactIds, setPendingArtifactIds] = useState<string[]>([])
  const [isLoadingArtifacts, setIsLoadingArtifacts] = useState(false)
  const [isUpdatingArtifactId, setIsUpdatingArtifactId] = useState<string | null>(null)
  const [isCreatingArtifactForMessageId, setIsCreatingArtifactForMessageId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [isQuickStartingChat, setIsQuickStartingChat] = useState(false)
  const [shouldFocusComposer, setShouldFocusComposer] = useState(false)
  const messageListRef = useRef<HTMLDivElement | null>(null)
  const composerInputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    void loadProjects()
  }, [])

  useEffect(() => {
    if (!selectedProjectId) {
      setSessions([])
      setSelectedSessionId(null)
      setMessages([])
      setNextBefore(null)
      setHasMoreMessages(false)
      return
    }
    void loadProjectSessions(selectedProjectId)
  }, [selectedProjectId])

  useEffect(() => {
    if (!selectedSessionId) {
      setMessages([])
      setNextBefore(null)
      setHasMoreMessages(false)
      setArtifacts([])
      setActiveArtifactId(null)
      return
    }
    void loadLatestMessages(selectedSessionId)
    void loadArtifacts(selectedSessionId)
  }, [selectedSessionId])

  useEffect(() => {
    if (!shouldFocusComposer || !selectedSessionId) {
      return
    }
    requestAnimationFrame(() => {
      composerInputRef.current?.focus()
      setShouldFocusComposer(false)
    })
  }, [selectedSessionId, shouldFocusComposer])

  useEffect(() => {
    if (pendingArtifactIds.length === 0 || artifacts.length === 0) {
      return
    }
    const nextArtifact = artifacts.find((artifact) => pendingArtifactIds.includes(artifact.id))
    if (nextArtifact) {
      setActiveArtifactId(nextArtifact.id)
      setPendingArtifactIds([])
    }
  }, [artifacts, pendingArtifactIds])

  async function loadProjects() {
    setIsLoadingProjects(true)
    try {
      const data = await listProjects()
      setProjects(data.items)
      setSelectedProjectId((current) => current ?? data.items[0]?.id ?? null)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load projects.')
    } finally {
      setIsLoadingProjects(false)
    }
  }

  async function loadProjectSessions(projectId: string) {
    setIsLoadingSessions(true)
    try {
      const params = new URLSearchParams({
        page: '0',
        size: '50',
        projectId,
      })
      const data = await request<ChatSessionPageResponse>(`/internal-api/v1/chat-sessions?${params.toString()}`, {
        method: 'GET',
      })
      setSessions(data.items)
      setSelectedSessionId((current) => {
        if (current && data.items.some((session) => session.id === current)) {
          return current
        }
        return data.items[0]?.id ?? null
      })
      if (data.items.length === 0) {
        setMessages([])
        setNextBefore(null)
        setHasMoreMessages(false)
      }
    } catch (error) {
      setSessions([])
      setSelectedSessionId(null)
      setMessages([])
      toast.error(error instanceof Error ? error.message : 'Failed to load chat sessions.')
    } finally {
      setIsLoadingSessions(false)
    }
  }

  async function loadLatestMessages(sessionId: string) {
    setIsLoadingMessages(true)
    try {
      const data = await request<ChatMessagePageResponse>(
        `/internal-api/v1/chat-sessions/${sessionId}/messages?limit=${messagePageSize}`,
        { method: 'GET' },
      )
      setMessages(data.items)
      setNextBefore(data.nextBefore)
      setHasMoreMessages(data.hasMore)
      requestAnimationFrame(() => {
        scrollToBottom(messageListRef.current)
      })
    } catch (error) {
      setMessages([])
      setNextBefore(null)
      setHasMoreMessages(false)
      toast.error(error instanceof Error ? error.message : 'Failed to load chat messages.')
    } finally {
      setIsLoadingMessages(false)
    }
  }

  async function loadArtifacts(sessionId: string) {
    setIsLoadingArtifacts(true)
    try {
      const data = await request<ChatArtifact[]>(`/internal-api/v1/chat-sessions/${sessionId}/artifacts`, {
        method: 'GET',
      })
      setArtifacts(data)
      setActiveArtifactId((current) => {
        if (current && data.some((artifact) => artifact.id === current)) {
          return current
        }
        return data.find((artifact) => artifact.pinned)?.id ?? data[0]?.id ?? null
      })
    } catch (error) {
      setArtifacts([])
      setActiveArtifactId(null)
      toast.error(error instanceof Error ? error.message : 'Failed to load artifacts.')
    } finally {
      setIsLoadingArtifacts(false)
    }
  }

  async function loadOlderMessages() {
    if (!selectedSessionId || !nextBefore || !hasMoreMessages || isLoadingOlderMessages || isLoadingMessages) {
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
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load older chat messages.')
    } finally {
      setIsLoadingOlderMessages(false)
    }
  }

  async function handleCreateProject() {
    const title = newProjectTitle.trim()
    const description = newProjectDescription.trim()
    if (!title) {
      toast.error('Project title is required.')
      return
    }

    setIsCreatingProject(true)
    try {
      const created = await createProject({
        title,
        description: description || null,
      })
      setProjects((current) => [created, ...current])
      setSelectedProjectId(created.id)
      setNewProjectTitle('')
      setNewProjectDescription('')
      setIsCreateProjectOpen(false)
      toast.success('Project created.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create project.')
    } finally {
      setIsCreatingProject(false)
    }
  }

  async function createSessionForProject(projectId: string, title: string) {
    return request<ChatSession>('/internal-api/v1/chat-sessions', {
      method: 'POST',
      body: JSON.stringify({
        projectId,
        title,
      }),
    })
  }

  function resetActiveConversation(sessionId: string) {
    setSelectedSessionId(sessionId)
    setActiveSessionActionId(null)
    setMessages([])
    setStreamEvents([])
    setRetainedStreamEvents([])
    setIsLastRunTraceExpanded(false)
    setArtifacts([])
    setActiveArtifactId(null)
    setPendingArtifactIds([])
    setDraft('')
  }

  async function handleCreateSession() {
    if (!selectedProjectId) {
      toast.error('Select a project first.')
      return
    }

    setIsCreatingSession(true)
    try {
      const nextIndex = sessions.length + 1
      const session = await createSessionForProject(selectedProjectId, `Chat ${nextIndex}`)
      setSessions((current) => [session, ...current])
      resetActiveConversation(session.id)
      setShouldFocusComposer(true)
      toast.success('Chat tab created.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create chat session.')
    } finally {
      setIsCreatingSession(false)
    }
  }

  async function handleQuickStartChat() {
    setIsQuickStartingChat(true)
    try {
      const project = await createProject({
        title: defaultQuickChatProjectTitle,
        description: null,
      })
      const session = await createSessionForProject(project.id, 'Chat 1')
      setProjects((current) => [project, ...current])
      setSelectedProjectId(project.id)
      setSessions([session])
      resetActiveConversation(session.id)
      setShouldFocusComposer(true)
      toast.success('New chat ready.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to start a new chat.')
    } finally {
      setIsQuickStartingChat(false)
    }
  }

  async function handleSendMessage() {
    const content = draft.trim()
    if (!content || !selectedProjectId) {
      return
    }

    setIsSending(true)
    setStreamEvents([])
    setRetainedStreamEvents([])
    setIsLastRunTraceExpanded(false)
    let sessionId = selectedSessionId

    try {
      if (!sessionId) {
        const session = await request<ChatSession>('/internal-api/v1/chat-sessions', {
          method: 'POST',
          body: JSON.stringify({
            projectId: selectedProjectId,
            title: deriveSessionTitle(content),
          }),
        })
        setSessions((current) => [session, ...current])
        setSelectedSessionId(session.id)
        sessionId = session.id
      }
      const activeSessionId = sessionId

      const userMessage = optimisticMessage(activeSessionId, 'user', content)
      setMessages((current) => [...current, userMessage])
      setDraft('')
      requestAnimationFrame(() => {
        scrollToBottom(messageListRef.current)
      })

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
          if (data.type === 'MESSAGE_START') {
            const payload = data.payload as ChatStreamMessageStartEvent
            setStreamEvents((current) => [
              ...current,
              { id: crypto.randomUUID(), type: 'message_start', messageId: payload.messageId, source: payload.source },
            ])
          }

          if (data.type === 'PLAN_CREATED') {
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
          }

          if (data.type === 'STEP_STARTED') {
            const payload = data.payload as ChatStreamStepEvent
            if (payload.step) {
              const step = payload.step
              setStreamEvents((current) => [
                ...current,
                { id: crypto.randomUUID(), type: 'step_started', stepId: step.id, content: step.content },
              ])
            }
          }

          if (data.type === 'STEP_PROGRESS') {
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
          }

          if (data.type === 'STEP_COMPLETED') {
            const payload = data.payload as ChatStreamStepCompletedEvent
            if (payload.step) {
              const step = payload.step
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
            }
          }

          if (data.type === 'FINAL_RESPONSE') {
            const payload = data.payload as { executionId: string; content: string | null }
            setStreamEvents((current) => [
              ...current,
              { id: crypto.randomUUID(), type: 'final_response', content: payload.content },
            ])
          }

          if (data.type === 'MESSAGE_COMPLETE') {
            const payload = data.payload as ChatStreamMessageCompleteEvent
            const artifactIds = payload.artifactIds?.filter(Boolean) ?? []
            setStreamEvents((current) => {
              const nextEvents = [
                ...current,
                { id: crypto.randomUUID(), type: 'message_complete', messageId: payload.messageId } as StreamTimelineEntry,
              ]
              setRetainedStreamEvents(nextEvents)
              return nextEvents
            })
            setMessages((current) => [
              ...current,
              {
                id: payload.messageId,
                sessionId: activeSessionId,
                source: payload.source,
                content: payload.content,
                createdAt: payload.createdAt ?? new Date().toISOString(),
              },
            ])
            setPendingArtifactIds(artifactIds)
            requestAnimationFrame(() => {
              scrollToBottom(messageListRef.current)
            })
          }

          if (data.type === 'MESSAGE_ERROR') {
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
        },
      )

      await loadProjectSessions(selectedProjectId)
      await loadLatestMessages(activeSessionId)
      await loadArtifacts(activeSessionId)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to send message.')
      if (sessionId) {
        await loadLatestMessages(sessionId)
        await loadArtifacts(sessionId)
      }
    } finally {
      setIsSending(false)
    }
  }

  function handleOpenArtifact(artifactId: string) {
    setActiveArtifactId(artifactId)
  }

  async function handlePinArtifact(artifactId: string) {
    const artifact = artifacts.find((item) => item.id === artifactId)
    if (!artifact) {
      return
    }
    setIsUpdatingArtifactId(artifactId)
    try {
      const updated = await request<ChatArtifact>(`/internal-api/v1/chat-artifacts/${artifactId}`, {
        method: 'PATCH',
        body: JSON.stringify({ pinned: true }),
      })
      setArtifacts((current) => current.map((item) => (item.id === artifactId ? updated : item)))
      setActiveArtifactId(updated.id)
      toast.success('Pinned to view.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to pin artifact.')
    } finally {
      setIsUpdatingArtifactId(null)
    }
  }

  async function handleCreateArtifact(message: ChatMessage) {
    if (!selectedSessionId) {
      return
    }
    const draftArtifact = inferArtifactDraft(message.content)
    setIsCreatingArtifactForMessageId(message.id)
    try {
      const created = await request<ChatArtifact>(`/internal-api/v1/chat-sessions/${selectedSessionId}/artifacts`, {
        method: 'POST',
        body: JSON.stringify({
          messageId: message.id,
          artifactType: draftArtifact.artifactType,
          title: draftArtifact.title,
          content: draftArtifact.content,
          metadata: draftArtifact.metadata,
          pinned: false,
          displayOrder: 0,
        }),
      })
      setArtifacts((current) => {
        const next = [created, ...current]
        return next.sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime())
      })
      setActiveArtifactId(created.id)
      toast.success('Artifact created.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create artifact.')
    } finally {
      setIsCreatingArtifactForMessageId(null)
    }
  }

  async function handleArchiveSession(sessionId = selectedSessionId) {
    if (!sessionId || !selectedProjectId) {
      return
    }

    setIsArchivingSession(true)
    try {
      await request<void>(`/internal-api/v1/chat-sessions/${sessionId}/archive`, {
        method: 'POST',
      })
      await loadProjectSessions(selectedProjectId)
      toast.success('Chat session archived.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to archive chat session.')
    } finally {
      setIsArchivingSession(false)
    }
  }

  async function handleDeleteSession(sessionId = selectedSessionId) {
    if (!sessionId || !selectedProjectId) {
      return
    }

    setIsDeletingSession(true)
    try {
      await request<void>(`/internal-api/v1/chat-sessions/${sessionId}`, {
        method: 'DELETE',
      })
      setActiveSessionActionId(null)
      await loadProjectSessions(selectedProjectId)
      toast.success('Chat session deleted.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete chat session.')
    } finally {
      setIsDeletingSession(false)
    }
  }

  async function handleRenameSession(sessionId: string) {
    const title = editingSessionTitle.trim()
    setIsRenamingSession(true)
    try {
      const updated = await request<ChatSession>(`/internal-api/v1/chat-sessions/${sessionId}`, {
        method: 'PATCH',
        body: JSON.stringify({ title: title || null }),
      })
      setSessions((current) => current.map((session) => (session.id === sessionId ? updated : session)))
      if (selectedSessionId === sessionId) {
        setSelectedSessionId(updated.id)
      }
      setActiveSessionActionId(null)
      toast.success('Chat tab renamed.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to rename chat tab.')
    } finally {
      setIsRenamingSession(false)
    }
  }

  const filteredProjects = useMemo(() => {
    const normalizedSearch = projectSearch.trim().toLowerCase()
    if (!normalizedSearch) {
      return projects
    }
    return projects.filter((project) =>
      [project.title, project.description, project.id, project.userId]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(normalizedSearch),
    )
  }, [projects, projectSearch])

  const selectedProject = projects.find((project) => project.id === selectedProjectId) ?? null
  const visibleStreamEvents = isSending ? streamEvents : retainedStreamEvents
  const activeArtifact = artifacts.find((artifact) => artifact.id === activeArtifactId) ?? null
  const viewMode: 'artifact' | 'application-builder' = activeArtifact ? 'artifact' : 'application-builder'
  const artifactsByMessageId = useMemo(() => {
    const map = new Map<string, ChatArtifact[]>()
    for (const artifact of artifacts) {
      if (!artifact.messageId) {
        continue
      }
      const current = map.get(artifact.messageId) ?? []
      current.push(artifact)
      map.set(artifact.messageId, current)
    }
    return map
  }, [artifacts])
  const latestAssistantMessageId = useMemo(() => {
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      if (messages[index]?.source !== 'user') {
        return messages[index]?.id ?? null
      }
    }
    return null
  }, [messages])
  const latestFinalResponseContent = useMemo(() => {
    for (let index = visibleStreamEvents.length - 1; index >= 0; index -= 1) {
      const entry = visibleStreamEvents[index]
      if (entry?.type === 'final_response' && entry.content?.trim()) {
        return entry.content.trim()
      }
    }
    return null
  }, [visibleStreamEvents])

  useEffect(() => {
    if (!selectedProject || isEditProjectOpen) {
      return
    }
    setEditingProjectTitle(selectedProject.title?.trim() || '')
    setEditingProjectDescription(selectedProject.description?.trim() || '')
  }, [selectedProject, isEditProjectOpen])

  function renderStreamEvent(entry: StreamTimelineEntry) {
    switch (entry.type) {
      case 'message_start':
        return (
          <div className="flex items-center gap-2 py-1 px-2 rounded-none bg-muted/30 border border-black">
            <Info className="h-3.5 w-3.5 text-blue-500" />
            <span className="text-xs font-medium text-muted-foreground italic">Assistant strategy initialized.</span>
          </div>
        )
      case 'plan_created':
        return (
          <div className="space-y-2 p-3 rounded-none border border-black bg-card/50 shadow-sm">
            <div className="flex items-center gap-2">
              <div className="p-1 rounded-md bg-primary/10 text-primary">
                <Brain className="h-3.5 w-3.5" />
              </div>
              <h4 className="text-sm font-semibold tracking-tight">Execution Strategy</h4>
            </div>
            {entry.thoughtProcess ? (
              <div className="text-xs leading-snug text-muted-foreground bg-muted/40 p-2 rounded-none border border-black">
                {entry.thoughtProcess}
              </div>
            ) : null}
            <div className="space-y-1">
              <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground/70">
                <ListTodo className="h-3 w-3" />
                <span>Planned Steps ({entry.steps.length})</span>
              </div>
              {entry.steps.map((step) => (
                <div key={step.id} className="text-xs rounded-md bg-muted/20 px-2 py-1">
                  <span className="font-mono mr-2">{step.id}</span>
                  {step.content}
                </div>
              ))}
            </div>
          </div>
        )
      case 'step_started':
        return (
          <div className="flex items-center gap-2 py-0.5 px-1">
            <Play className="h-3 w-3 text-primary animate-pulse" fill="currentColor" />
            <p className="text-xs font-semibold">
              <span className="text-muted-foreground mr-1">Step {entry.stepId}:</span>{entry.content}
            </p>
          </div>
        )
      case 'step_progress':
        return (
          <div className="flex items-start gap-2 py-0.5 px-1 ml-0.5 border-l-2 border-black pl-3">
            {entry.progressType === 'executing' ? (
              <Loader2 className="h-3 w-3 text-muted-foreground animate-spin shrink-0 mt-0.5" />
            ) : (
              <Zap className="h-3 w-3 text-amber-500 shrink-0 mt-0.5" />
            )}
            <p className="text-[11px] text-muted-foreground leading-relaxed italic">{entry.message?.trim() || 'Processing...'}</p>
          </div>
        )
      case 'step_completed':
        return (
          <div className="ml-0.5 border-l-2 border-black pl-3 space-y-1.5">
            <div className="flex items-center gap-2 px-2 py-1 rounded-none bg-green-500/5 border border-black w-fit">
              <CheckCircle2 className="h-3 w-3 text-green-500" />
              <span className="text-[11px] font-bold text-green-600/80 uppercase tracking-tight">Step Result: {entry.status}</span>
            </div>
            {entry.terminalResponse ? (
              <div className="rounded-none border border-black bg-background p-2 overflow-hidden shadow-sm">
                <div className="flex items-center gap-2 mb-1">
                  <Terminal className="h-3 w-3 text-zinc-400" />
                  <span className="text-[10px] font-mono text-zinc-400 font-bold uppercase tracking-wider">Output</span>
                </div>
                <pre className="font-mono text-[11px] leading-snug text-zinc-300 break-all whitespace-pre-wrap max-h-40 overflow-y-auto">{entry.terminalResponse}</pre>
              </div>
            ) : null}
          </div>
        )
      case 'final_response':
        return (
          <div className="space-y-2 p-3 rounded-none border-2 border-black bg-primary/5 shadow-md">
            <div className="flex items-center gap-2">
              <div className="p-1 rounded-md bg-primary text-primary-foreground">
                <FileText className="h-3.5 w-3.5" />
              </div>
              <h4 className="text-sm font-bold tracking-tight">Finalized Intelligence</h4>
            </div>
            {entry.content ? <div className="text-sm leading-snug whitespace-pre-wrap font-medium">{entry.content}</div> : null}
          </div>
        )
      case 'message_complete':
        return (
          <div className="flex items-center gap-2 py-1 px-2 rounded-none bg-primary/5 border border-black">
            <CheckCircle2 className="h-3.5 w-3.5 text-primary" />
            <span className="text-[11px] font-bold text-primary uppercase tracking-wider">Message archived successfully</span>
          </div>
        )
      case 'message_error':
        return (
          <div className="flex items-start gap-2 p-3 rounded-none border border-black bg-destructive/5 text-destructive">
            <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
            <div className="space-y-0.5">
              <p className="text-xs font-bold uppercase tracking-wider">Execution Error</p>
              <p className="text-sm font-medium leading-snug">{entry.message}</p>
            </div>
          </div>
        )
    }
  }

  function renderTraceSection(forceExpanded: boolean) {
    if (visibleStreamEvents.length === 0) {
      return null
    }

    const expanded = forceExpanded || isLastRunTraceExpanded

    return (
      <div className="mb-3 space-y-2">
        <button
          type="button"
          className="flex w-full items-center justify-between border border-black px-3 py-2 text-left text-[11px] font-semibold uppercase tracking-wider"
          onClick={() => {
            if (!forceExpanded) {
              setIsLastRunTraceExpanded((current) => !current)
            }
          }}
        >
          <span>{isSending ? 'Run Trace' : 'Last Run Trace'}</span>
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>
        {expanded ? (
          <div className="space-y-2 max-h-56 overflow-y-auto">
            {visibleStreamEvents.map((entry) => (
              <div key={entry.id}>{renderStreamEvent(entry)}</div>
            ))}
          </div>
        ) : null}
      </div>
    )
  }

  async function handleUpdateProject() {
    if (!selectedProject) {
      return
    }

    const title = editingProjectTitle.trim()
    if (!title) {
      toast.error('Project title is required.')
      return
    }

    setIsUpdatingProject(true)
    try {
      const updated = await updateProject(selectedProject.id, {
        title,
        description: editingProjectDescription.trim() || null,
      })
      setProjects((current) => current.map((project) => (project.id === updated.id ? updated : project)))
      setIsEditProjectOpen(false)
      toast.success('Project updated.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update project.')
    } finally {
      setIsUpdatingProject(false)
    }
  }

  return (
    <div className="h-full min-h-0 p-4 md:p-6">
      <div className="grid h-full min-h-0 gap-4 lg:grid-cols-[340px_minmax(0,1fr)]">
        <aside className="min-h-0 rounded-none border border-black bg-card/70 shadow-sm overflow-hidden">
          <div className="border-b border-black p-4 space-y-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold">Projects</h2>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <Button size="sm" className="gap-2 rounded-none border border-black" onClick={() => void handleQuickStartChat()} disabled={isQuickStartingChat}>
                  {isQuickStartingChat ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageSquarePlus className="h-4 w-4" />}
                  New Chat
                </Button>
                <Popover open={isCreateProjectOpen} onOpenChange={setIsCreateProjectOpen}>
                  <PopoverTrigger asChild>
                    <Button size="sm" className="gap-2 rounded-none border border-black">
                      <Plus className="h-4 w-4" />
                      New
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent align="end" className="w-80 space-y-3 rounded-none border border-black">
                    <div>
                      <h3 className="text-sm font-semibold">Create Project</h3>
                      <p className="text-xs text-muted-foreground mt-1">Start a new project container for multiple chat tabs.</p>
                    </div>
                    <Input
                      className="rounded-none border-black"
                      value={newProjectTitle}
                      onChange={(event) => setNewProjectTitle(event.target.value)}
                      placeholder="Project title"
                    />
                    <Input
                      className="rounded-none border-black"
                      value={newProjectDescription}
                      onChange={(event) => setNewProjectDescription(event.target.value)}
                      placeholder="Description"
                    />
                    <Button className="w-full gap-2 rounded-none border border-black" onClick={() => void handleCreateProject()} disabled={isCreatingProject}>
                      {isCreatingProject ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                      Create Project
                    </Button>
                  </PopoverContent>
                </Popover>
              </div>
            </div>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                className="h-10 pl-10 rounded-none border-black"
                value={projectSearch}
                onChange={(event) => setProjectSearch(event.target.value)}
                placeholder="Search projects..."
              />
            </div>
          </div>

          <div className="min-h-0 overflow-y-auto p-4">
            {isLoadingProjects ? (
              <div className="py-12 flex items-center justify-center">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : filteredProjects.length === 0 ? (
              <div className="rounded-none border border-black p-6 text-center text-sm text-muted-foreground">
                No projects found.
              </div>
            ) : (
              <ItemGroup aria-label="Project list" className="gap-2">
                {filteredProjects.map((project) => {
                  const isActive = project.id === selectedProjectId
                  return (
                    <button
                      key={project.id}
                      type="button"
                      onClick={() => setSelectedProjectId(project.id)}
                      className="w-full text-left"
                    >
                      <Item
                        variant="outline"
                        size="sm"
                        className={isActive ? 'rounded-none border-black bg-primary/5 shadow-sm' : 'rounded-none border-black hover:bg-muted/30'}
                      >
                        <ItemContent>
                          <ItemHeader>
                            <ItemTitle>{formatProjectTitle(project)}</ItemTitle>
                            <Badge variant="secondary" className="capitalize">{project.status ?? 'active'}</Badge>
                          </ItemHeader>
                          <ItemDescription>{project.description?.trim() || 'No description provided.'}</ItemDescription>
                          <ItemDescription className="font-mono text-[10px] uppercase tracking-tight">
                            {project.id}
                          </ItemDescription>
                        </ItemContent>
                      </Item>
                    </button>
                  )
                })}
              </ItemGroup>
            )}
          </div>
        </aside>

        <main className="min-h-0 rounded-none border border-black bg-card/70 shadow-sm overflow-hidden">
          {!selectedProject ? (
            <div className="h-full flex items-center justify-center text-center p-8">
              <div>
                <FolderOpen className="h-10 w-10 mx-auto mb-4 text-muted-foreground" />
                <h3 className="text-lg font-semibold">Select a project</h3>
                <p className="text-sm text-muted-foreground mt-2">
                  Choose a project from the left to manage its chat session tabs.
                </p>
              </div>
            </div>
          ) : (
            <div className="h-full min-h-0 flex flex-col overflow-hidden">
              <div className="border-b border-black px-4 py-2.5">
                <div className="flex flex-col gap-1 md:flex-row md:items-center md:justify-between">
                  <div className="min-w-0 flex items-center gap-2">
                    <h3 className="text-lg font-semibold truncate">{formatProjectTitle(selectedProject)}</h3>
                    <Badge variant="secondary" className="capitalize rounded-none border border-black">{selectedProject.status ?? 'active'}</Badge>
                    <Popover open={isEditProjectOpen} onOpenChange={setIsEditProjectOpen}>
                      <PopoverTrigger asChild>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-8 gap-2 rounded-none border border-black px-2"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                          Edit
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent align="start" className="w-80 space-y-3 rounded-none border border-black">
                        <div>
                          <h3 className="text-sm font-semibold">Edit Project</h3>
                          <p className="mt-1 text-xs text-muted-foreground">Update the selected project title and description.</p>
                        </div>
                        <Input
                          className="rounded-none border-black"
                          value={editingProjectTitle}
                          onChange={(event) => setEditingProjectTitle(event.target.value)}
                          placeholder="Project title"
                        />
                        <Input
                          className="rounded-none border-black"
                          value={editingProjectDescription}
                          onChange={(event) => setEditingProjectDescription(event.target.value)}
                          placeholder="Description"
                        />
                        <Button
                          type="button"
                          className="w-full gap-2 rounded-none border border-black"
                          onClick={() => void handleUpdateProject()}
                          disabled={isUpdatingProject}
                        >
                          {isUpdatingProject ? <Loader2 className="h-4 w-4 animate-spin" /> : <Pencil className="h-4 w-4" />}
                          Save Changes
                        </Button>
                      </PopoverContent>
                    </Popover>
                  </div>
                  <p className="text-xs text-muted-foreground truncate max-w-[50%]">
                    {selectedProject.description?.trim() || 'No project description yet.'}
                  </p>
                </div>
              </div>

              <div className="border-b border-black px-4 py-3">
                {isLoadingSessions ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Loading chat tabs...
                  </div>
                ) : sessions.length === 0 ? (
                  <div className="flex items-center justify-between gap-3">
                    <div className="text-sm text-muted-foreground">
                      No chat sessions yet. Create a tab to start working inside this project.
                    </div>
                    <Button className="gap-2 rounded-none border border-black" onClick={() => void handleCreateSession()} disabled={isCreatingSession}>
                      {isCreatingSession ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageSquarePlus className="h-4 w-4" />}
                      New Chat Tab
                    </Button>
                  </div>
                ) : (
                  <div className="flex items-start gap-3">
                    <Button className="gap-2 rounded-none border border-black shrink-0" onClick={() => void handleCreateSession()} disabled={isCreatingSession}>
                      {isCreatingSession ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageSquarePlus className="h-4 w-4" />}
                      Chat
                    </Button>
                    <div className="min-w-0 flex-1 overflow-x-auto overflow-y-hidden pb-1">
                    <div className="flex min-w-max items-stretch">
                      {sessions.map((session, index) => (
                        <div
                          key={session.id}
                          className={`flex items-center border border-black rounded-none mr-2 ${
                            session.id === selectedSessionId ? 'bg-primary/10 text-primary shadow-[inset_0_0_0_2px_#7DFDFE]' : 'bg-background'
                          }`}
                        >
                          <button
                            type="button"
                            onClick={() => setSelectedSessionId(session.id)}
                            className={`min-w-[140px] flex-none px-3 py-2 text-left text-sm ${
                              session.id === selectedSessionId ? 'bg-primary/10 text-primary' : 'bg-background text-foreground'
                            }`}
                          >
                            {formatSessionTitle(session, index)}
                          </button>
                          <Popover
                            open={activeSessionActionId === session.id}
                            onOpenChange={(open) => {
                              setActiveSessionActionId(open ? session.id : null)
                              setEditingSessionTitle(session.title?.trim() || '')
                            }}
                          >
                            <PopoverTrigger asChild>
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon"
                                className={`h-8 w-8 shrink-0 rounded-none ${
                                  session.id === selectedSessionId ? 'text-primary' : ''
                                }`}
                                onClick={(event) => {
                                  event.stopPropagation()
                                  setSelectedSessionId(session.id)
                                }}
                              >
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </PopoverTrigger>
                            <PopoverContent align="end" className="w-48 rounded-none border border-black">
                              <div className="space-y-2">
                                <Input
                                  className="rounded-none border-black"
                                  value={editingSessionTitle}
                                  onChange={(event) => setEditingSessionTitle(event.target.value)}
                                  placeholder="Chat tab name"
                                />
                                <Button
                                  type="button"
                                  variant="outline"
                                  className="w-full justify-start rounded-none border-black"
                                  disabled={isRenamingSession}
                                  onClick={() => {
                                    setSelectedSessionId(session.id)
                                    void handleRenameSession(session.id)
                                  }}
                                >
                                  {isRenamingSession ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                                  Rename
                                </Button>
                                <Button
                                  type="button"
                                  variant="outline"
                                  className="w-full justify-start rounded-none border-black"
                                  disabled={isArchivingSession || isDeletingSession}
                                  onClick={() => {
                                    setSelectedSessionId(session.id)
                                    void handleArchiveSession(session.id)
                                  }}
                                >
                                  <Archive className="h-4 w-4" />
                                  Archive
                                </Button>
                                <Button
                                  type="button"
                                  variant="outline"
                                  className="w-full justify-start rounded-none border-black"
                                  disabled={isDeletingSession || isArchivingSession}
                                  onClick={() => {
                                    setSelectedSessionId(session.id)
                                    void handleDeleteSession(session.id)
                                  }}
                                >
                                  <Trash2 className="h-4 w-4" />
                                  Delete
                                </Button>
                              </div>
                            </PopoverContent>
                          </Popover>
                        </div>
                      ))}
                    </div>
                    </div>
                  </div>
                )}
              </div>

              <div className="flex-1 min-h-0 flex flex-col">
                {!selectedSessionId ? (
                  <div className="flex-1 flex items-center justify-center text-center p-8">
                    <div>
                      <h4 className="text-lg font-semibold">No chat tab selected</h4>
                      <p className="text-sm text-muted-foreground mt-2">
                        Create a tab to start a chat session inside this project.
                      </p>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="flex-1 min-h-0 grid grid-cols-1 overflow-hidden lg:grid-cols-[minmax(0,0.78fr)_minmax(420px,1.22fr)]">
                      <div className="min-h-0 flex h-full flex-col overflow-hidden border-r border-black max-w-[760px]">
                        <div ref={messageListRef} className="h-0 flex-1 overflow-y-auto p-4 md:p-5 space-y-3 bg-muted/10">
                          {hasMoreMessages ? (
                            <div className="flex justify-center">
                              <Button
                                variant="outline"
                                size="sm"
                                className="rounded-none border-black"
                                onClick={() => void loadOlderMessages()}
                                disabled={isLoadingOlderMessages}
                              >
                                {isLoadingOlderMessages ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : 'Load older messages'}
                              </Button>
                            </div>
                          ) : null}
                          {isLoadingMessages ? (
                            <div className="py-12 flex items-center justify-center">
                              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                            </div>
                          ) : messages.length === 0 ? (
                            <div className="h-full min-h-[240px] flex items-center justify-center text-center text-sm text-muted-foreground">
                              No messages yet. Send the first prompt in this chat tab.
                            </div>
                          ) : (
                            <>
                              {messages.map((message) => {
                                const isUser = message.source === 'user'
                                const attachTrace = !isUser
                                  && message.id === latestAssistantMessageId
                                  && !isSending
                                  && visibleStreamEvents.length > 0
                                const messageArtifacts = artifactsByMessageId.get(message.id) ?? []
                                return (
                                  <div key={message.id} className="flex justify-start">
                                    <div
                                      className={`w-full rounded-none border border-black px-4 py-3 shadow-sm ${
                                        isUser
                                          ? 'bg-primary/10 text-foreground'
                                          : 'bg-background text-foreground'
                                      }`}
                                    >
                                      <div className="mb-3 flex items-center justify-between gap-3 border-b border-black pb-2">
                                        <span className="text-[10px] font-bold uppercase tracking-[0.2em]">
                                          {isUser ? 'User' : 'LLM'}
                                        </span>
                                        <span className="text-[10px] text-muted-foreground">
                                          {formatMessageTime(message.createdAt)}
                                        </span>
                                      </div>
                                      {attachTrace ? renderTraceSection(false) : null}
                                      <div className="whitespace-pre-wrap text-sm leading-relaxed">{message.content}</div>
                                      {!isUser ? (
                                        <div className="mt-3 flex flex-wrap gap-2">
                                          {messageArtifacts.length === 0 ? (
                                            <Button
                                              type="button"
                                              variant="outline"
                                              size="sm"
                                              className="rounded-none border-black"
                                              disabled={isCreatingArtifactForMessageId === message.id}
                                              onClick={() => void handleCreateArtifact(message)}
                                            >
                                              {isCreatingArtifactForMessageId === message.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : 'Create Artifact'}
                                            </Button>
                                          ) : null}
                                          {messageArtifacts.map((artifact) => (
                                            <div key={artifact.id} className="flex items-center gap-2">
                                              <Button
                                                type="button"
                                                variant="outline"
                                                size="sm"
                                                className="rounded-none border-black"
                                                onClick={() => void handleOpenArtifact(artifact.id)}
                                              >
                                                Open in View
                                              </Button>
                                              {!artifact.pinned ? (
                                                <Button
                                                  type="button"
                                                  variant="outline"
                                                  size="sm"
                                                  className="rounded-none border-black"
                                                  disabled={isUpdatingArtifactId === artifact.id}
                                                  onClick={() => void handlePinArtifact(artifact.id)}
                                                >
                                                  {isUpdatingArtifactId === artifact.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : 'Pin'}
                                                </Button>
                                              ) : null}
                                            </div>
                                          ))}
                                        </div>
                                      ) : null}
                                    </div>
                                  </div>
                                )
                              })}
                              {isSending && visibleStreamEvents.length > 0 ? (
                                <div className="flex justify-start">
                                  <div className="w-full rounded-none border border-black bg-background px-4 py-3 text-foreground shadow-sm">
                                    <div className="mb-3 flex items-center justify-between gap-3 border-b border-black pb-2">
                                      <span className="text-[10px] font-bold uppercase tracking-[0.2em]">LLM</span>
                                      <span className="text-[10px] text-muted-foreground">Streaming</span>
                                    </div>
                                    {renderTraceSection(true)}
                                    {latestFinalResponseContent ? (
                                      <div className="whitespace-pre-wrap text-sm leading-relaxed">{latestFinalResponseContent}</div>
                                    ) : null}
                                  </div>
                                </div>
                              ) : null}
                            </>
                          )}
                        </div>

                        <div className="border-t border-black p-4">
                          <div className="flex gap-3">
                            <Input
                              ref={composerInputRef}
                              className="rounded-none border-black"
                              value={draft}
                              onChange={(event) => setDraft(event.target.value)}
                              placeholder="Message this chat tab..."
                              disabled={isSending}
                              onKeyDown={(event) => {
                                if (event.key === 'Enter' && !event.shiftKey) {
                                  event.preventDefault()
                                  void handleSendMessage()
                                }
                              }}
                            />
                            <Button className="gap-2 rounded-none border border-black" onClick={() => void handleSendMessage()} disabled={isSending || !draft.trim()}>
                              {isSending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                              Send
                            </Button>
                          </div>
                        </div>
                      </div>

                      <aside className="min-h-0 flex h-full flex-col overflow-hidden bg-background">
                        <div className="border-b border-black px-4 py-3">
                          <div className="flex items-center justify-between gap-3">
                            <div className="text-sm font-semibold uppercase tracking-wider">View Panel</div>
                            <div className="flex items-center gap-2">
                              <Badge variant={viewMode === 'artifact' ? 'default' : 'outline'} className="rounded-none border border-black">
                                Artifact
                              </Badge>
                              <Badge variant={viewMode === 'application-builder' ? 'default' : 'outline'} className="rounded-none border border-black">
                                Application Builder
                              </Badge>
                            </div>
                          </div>
                        </div>
                        <div className="h-0 flex-1 overflow-y-auto p-4">
                          {isLoadingArtifacts ? (
                            <div className="border border-black p-4 min-h-[280px] flex items-center justify-center text-center">
                              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                            </div>
                          ) : activeArtifact ? (
                            <div className="border border-black min-h-[280px] flex flex-col">
                              <div className="border-b border-black px-4 py-3">
                                <div className="flex items-center justify-between gap-3">
                                  <div>
                                    <div className="text-sm font-semibold">{activeArtifact.title?.trim() || 'Untitled artifact'}</div>
                                    <div className="mt-1 text-[10px] uppercase tracking-[0.2em] text-muted-foreground">
                                      {activeArtifact.artifactType}
                                      {activeArtifact.pinned ? ' · pinned' : ''}
                                    </div>
                                  </div>
                                </div>
                              </div>
                              <div className="flex-1 overflow-auto p-4">
                                {activeArtifact.artifactType === 'json' ? (
                                  <pre className="whitespace-pre-wrap break-all font-mono text-xs leading-relaxed">{activeArtifact.content}</pre>
                                ) : activeArtifact.artifactType === 'diff' ? (
                                  <pre className="whitespace-pre-wrap break-all font-mono text-xs leading-relaxed">{activeArtifact.content}</pre>
                                ) : activeArtifact.artifactType === 'plan' ? (
                                  <div className="whitespace-pre-wrap text-sm leading-relaxed">{activeArtifact.content}</div>
                                ) : (
                                  <div className="whitespace-pre-wrap text-sm leading-relaxed">{activeArtifact.content}</div>
                                )}
                              </div>
                            </div>
                          ) : (
                            <div className="border border-black min-h-[280px] h-full p-4 flex flex-col">
                              <div className="border-b border-black pb-3">
                                <div className="text-sm font-semibold">Application Builder</div>
                              </div>
                              <div className="flex-1 min-h-0" />
                            </div>
                          )}
                        </div>
                      </aside>
                    </div>
                  </>
                )}
              </div>
            </div>
          )}
        </main>
      </div>

      <Toaster richColors position="top-right" />
    </div>
  )
}
