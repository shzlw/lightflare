export type AuthUser = {
  id: string
  username: string
  email: string | null
  displayName: string | null
  status: string | null
  role: string | null
  mustChangePassword: boolean
}

export interface Workflow {
  id: string;
  name: string;
  description: string;
  schemaDefinition: string; // JSON string
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowExecution {
  id: string;
  workflowId: string;
  version: number;
  status: string;
  startedAt: string;
  completedAt: string | null;
}

export interface WorkflowStepExecution {
  id: string;
  workflowExecutionId: string;
  stepId: string;
  version: number;
  status: string;
  inputData: string | null;
  outputData: string | null;
  errorMessage: string | null;
  startedAt: string;
  completedAt: string | null;
}

function readCookie(name: string) {
  const cookiePrefix = `${name}=`
  return document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(cookiePrefix))
    ?.slice(cookiePrefix.length)
}

function csrfToken() {
  return readCookie('XSRF-TOKEN')
}

function formatClientDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  const offsetMinutes = -date.getTimezoneOffset()
  const offsetSign = offsetMinutes >= 0 ? '+' : '-'
  const absoluteOffsetMinutes = Math.abs(offsetMinutes)
  const offsetHours = String(Math.floor(absoluteOffsetMinutes / 60)).padStart(2, '0')
  const offsetRemainderMinutes = String(absoluteOffsetMinutes % 60).padStart(2, '0')

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${offsetSign}${offsetHours}:${offsetRemainderMinutes}`
}

function applyRequestHeaders(headers: Headers, method: string, hasBody: boolean, body: RequestInit['body']) {
  const isFormDataBody = typeof FormData !== 'undefined' && body instanceof FormData

  if (!headers.has('x-client-datetime')) {
    headers.set('x-client-datetime', formatClientDateTime(new Date()))
  }

  if (hasBody && !isFormDataBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const token = csrfToken()
    if (token && !headers.has('X-CSRF-Token')) {
      headers.set('X-CSRF-Token', token)
    }
  }
}

export async function request<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  const method = (init?.method ?? 'GET').toUpperCase()
  const hasBody = init?.body !== undefined && init?.body !== null
  applyRequestHeaders(headers, method, hasBody, init?.body)

  const response = await fetch(input, {
    ...init,
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const json = JSON.parse(text)
      if (json.message) {
        message = json.message
      }
    } catch {
      // Not JSON, use raw text
    }
    throw new Error(message || `Request failed with status ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export async function streamRequest<T>(
  input: RequestInfo,
  init: RequestInit,
  onEvent: (event: { event: string; data: T }) => void,
) {
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  const hasBody = init.body !== undefined && init.body !== null
  applyRequestHeaders(headers, method, hasBody, init.body)

  const response = await fetch(input, {
    ...init,
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const json = JSON.parse(text)
      if (json.message) {
        message = json.message
      }
    } catch {
      // Not JSON, use raw text
    }
    throw new Error(message || `Request failed with status ${response.status}`)
  }

  if (!response.body) {
    throw new Error('Streaming response body is not available.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })

    const normalized = buffer.replace(/\r\n/g, '\n')
    const segments = normalized.split('\n\n')
    buffer = segments.pop() ?? ''

    for (const segment of segments) {
      const parsed = parseSseSegment<T>(segment)
      if (parsed) {
        onEvent(parsed)
      }
    }

    if (done) {
      const trailing = buffer.trim()
      if (trailing) {
        const parsed = parseSseSegment<T>(trailing)
        if (parsed) {
          onEvent(parsed)
        }
      }
      return
    }
  }
}

function parseSseSegment<T>(segment: string) {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of segment.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  return {
    event: eventName,
    data: JSON.parse(dataLines.join('\n')) as T,
  }
}

export async function fetchCurrentUser() {
  return request<AuthUser>('/api/v1/auth/me', { method: 'GET' })
}

export async function listWorkflows(): Promise<Workflow[]> {
  return request<Workflow[]>('/api/v1/workflows');
}

export async function getWorkflow(id: string): Promise<Workflow> {
  return request<Workflow>(`/api/v1/workflows/${id}`);
}

export async function upsertWorkflow(id: string, workflow: Partial<Workflow>): Promise<Workflow> {
  return request<Workflow>(`/api/v1/workflows/${id}`, {
    method: 'PUT',
    body: JSON.stringify(workflow)
  });
}

export async function executeWorkflow(
  id: string,
  initialData: Record<string, unknown> = {},
  startStepId?: string,
): Promise<{ executionId: string }> {
  return request<{ executionId: string }>(`/api/v1/workflows/${id}/executions`, {
    method: 'POST',
    body: JSON.stringify({ initialData, startStepId })
  });
}

export async function getExecution(executionId: string): Promise<WorkflowExecution> {
  return request<WorkflowExecution>(`/api/v1/executions/${executionId}`);
}

export async function getExecutionSteps(executionId: string): Promise<WorkflowStepExecution[]> {
  return request<WorkflowStepExecution[]>(`/api/v1/executions/${executionId}/steps`);
}

export async function testWorkflowStep(step: any, context: any): Promise<any> {
  return request<any>('/api/v1/workflows/test-step', {
    method: 'POST',
    body: JSON.stringify({ step, mockContext: context })
  });
}

export async function login(loginValue: string, password: string) {
  return request<AuthUser>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      login: loginValue,
      password,
    }),
  })
}

export async function logout() {
  return request<void>('/api/v1/auth/logout', {
    method: 'POST',
  })
}

export async function updatePassword(newPassword: string) {
  return request<AuthUser>('/api/v1/auth/password', {
    method: 'POST',
    body: JSON.stringify({
      newPassword,
    }),
  })
}
