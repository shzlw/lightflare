export type AuthUser = {
  id: string
  username: string
  email: string | null
  displayName: string | null
  status: string | null
  role: string | null
  mustChangePassword: boolean
}

export interface Project {
  id: string
  title: string | null
  description: string | null
  userId: string | null
  status: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectPageResponse {
  items: Project[]
  page: number
  size: number
  totalItems: number
  totalPages: number
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

export async function listProjects(params?: { page?: number; size?: number; q?: string }): Promise<ProjectPageResponse> {
  const searchParams = new URLSearchParams()
  searchParams.set('page', String(params?.page ?? 0))
  searchParams.set('size', String(params?.size ?? 20))
  if (params?.q?.trim()) {
    searchParams.set('q', params.q.trim())
  }
  return request<ProjectPageResponse>(`/internal-api/v1/projects?${searchParams.toString()}`, { method: 'GET' })
}

export async function createProject(project: { title?: string | null; description?: string | null }): Promise<Project> {
  return request<Project>('/internal-api/v1/projects', {
    method: 'POST',
    body: JSON.stringify(project),
  })
}

export async function updateProject(
  id: string,
  project: { title?: string | null; description?: string | null; status?: string | null },
): Promise<Project> {
  return request<Project>(`/internal-api/v1/projects/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(project),
  })
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
