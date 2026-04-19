import { useEffect, useMemo, useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemHeader,
  ItemTitle,
} from '@/components/ui/item'
import { toast } from 'sonner'
import { request } from '@/lib/api'
import { Badge } from '@/components/ui/badge'

type ToolParameter = {
  name: string
  type: string | null
  description: string | null
  required: boolean
  parameters: ToolParameter[]
}

export type Tool = {
  name: string
  description: string | null
  category: string | null
  integrationId: string | null
  sourceType: string | null
  sourceName: string | null
  parameters: ToolParameter[]
}

type ToolCatalogPanelProps = {
  className?: string
  tools?: Tool[]
  onToolsChange?: (tools: Tool[]) => void
}

type ToolGroup = {
  key: string
  label: string
  sourceType: string
  integrationId: string | null
  tools: Tool[]
}

function formatValue(value: string | null | undefined) {
  return value && value.trim() ? value : 'Not set'
}

function integrationLabel(tool: Tool) {
  const sourceName = formatValue(tool.sourceName)
  if (tool.sourceType === 'MCP') {
    return `MCP: ${sourceName}`
  }
  if (tool.sourceType === 'INTERNAL') {
    return 'Internal tools'
  }
  if (tool.sourceType === 'LOCAL') {
    return 'Local tools'
  }
  return sourceName
}

function groupTools(tools: Tool[]) {
  const groupsByKey = new Map<string, ToolGroup>()

  for (const tool of tools) {
    const sourceType = formatValue(tool.sourceType)
    const sourceName = formatValue(tool.sourceName)
    const key = `${sourceType}:${sourceName}:${tool.integrationId ?? ''}`
    const existingGroup = groupsByKey.get(key)

    if (existingGroup) {
      existingGroup.tools.push(tool)
    } else {
      groupsByKey.set(key, {
        key,
        label: integrationLabel(tool),
        sourceType,
        integrationId: tool.integrationId,
        tools: [tool],
      })
    }
  }

  return [...groupsByKey.values()]
    .map((group) => ({
      ...group,
      tools: [...group.tools].sort((left, right) => left.name.localeCompare(right.name)),
    }))
    .sort((left, right) => left.label.localeCompare(right.label))
}

function renderParameters(parameters: ToolParameter[], depth = 0) {
  if (!parameters.length) {
    return <p className="text-xs text-muted-foreground">No inputs required.</p>
  }

  return (
    <ul className={depth === 0 ? 'space-y-2' : 'mt-2 space-y-2 pl-4 border-l border-border/60'}>
      {parameters.map((parameter) => (
        <li key={`${depth}-${parameter.name}`} className="text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono font-medium">{parameter.name}</span>
            <Badge variant="outline">{formatValue(parameter.type)}</Badge>
            {parameter.required ? <Badge variant="secondary">required</Badge> : null}
          </div>
          {parameter.description?.trim() ? (
            <p className="mt-1 text-xs text-muted-foreground">{parameter.description}</p>
          ) : null}
          {parameter.parameters?.length ? renderParameters(parameter.parameters, depth + 1) : null}
        </li>
      ))}
    </ul>
  )
}

export default function ToolCatalogPanel({ className, tools: providedTools, onToolsChange }: ToolCatalogPanelProps) {
  const [tools, setTools] = useState<Tool[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [expandedToolName, setExpandedToolName] = useState<string | null>(null)
  const visibleTools = providedTools ?? tools
  const toolGroups = useMemo(() => groupTools(visibleTools), [visibleTools])

  useEffect(() => {
    void loadTools()
  }, [])

  async function loadTools(isManualRefresh = false) {
    setIsLoading(true)

    try {
      const data = await request<Tool[]>('/internal-api/v1/tools', { method: 'GET' })
      const sortedTools = [...data].sort((left, right) => left.name.localeCompare(right.name))
      setTools(sortedTools)
      onToolsChange?.(sortedTools)

      if (isManualRefresh) {
        toast.success('Tool catalog synchronized successfully.')
      }
    } catch (loadError) {
      setTools([])
      toast.error(loadError instanceof Error ? loadError.message : 'Failed to load tools.')
      onToolsChange?.([])
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <section className={className}>
      <div className="flex items-center justify-between gap-4 mb-4">
        <h3 className="text-lg font-semibold text-foreground tracking-tight">Active</h3>
        <Button variant="outline" onClick={() => void loadTools(true)} disabled={isLoading} className="shadow-sm hover:shadow-md transition-all h-9 gap-2">
          {isLoading ? (
            <div className="w-4 h-4 border-2 border-primary/20 border-t-primary rounded-full animate-spin" />
          ) : (
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" /><path d="M3 3v5h5" /></svg>
          )}
          Refresh
        </Button>
      </div>

      {isLoading && visibleTools.length === 0 ? (
        <div className="py-20 flex flex-col items-center justify-center space-y-4 rounded-xl border border-border/40 bg-muted/10 animate-in fade-in duration-300">
          <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-muted-foreground text-sm font-medium animate-pulse">Synchronizing tool schemas...</p>
        </div>
      ) : visibleTools.length === 0 ? (
        <div className="py-12 flex flex-col items-center justify-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
          <div className="w-16 h-16 bg-muted/50 rounded-full flex items-center justify-center mb-4">
            <span className="text-2xl opacity-50">🛠️</span>
          </div>
          <h4 className="text-lg font-semibold mb-2">No tools connected</h4>
          <p className="text-muted-foreground text-sm max-w-md">The system could not locate any active tools matching your criteria or currently registered to the context.</p>
        </div>
      ) : (
        <div className="space-y-6">
          {toolGroups.map((group) => (
            <section key={group.key} className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/50 pb-2">
                <div className="flex flex-wrap items-center gap-2">
                  <h4 className="text-sm font-semibold">{group.label}</h4>
                  <Badge variant="secondary">{group.sourceType}</Badge>
                  {group.integrationId ? <Badge variant="outline">{group.integrationId}</Badge> : null}
                </div>
                <span className="text-xs text-muted-foreground">{group.tools.length} tools</span>
              </div>

              <ItemGroup>
                {group.tools.map((tool) => {
                  const isExpanded = expandedToolName === tool.name

                  return (
                    <Item
                      key={tool.name}
                      variant="outline"
                      role="button"
                      tabIndex={0}
                      aria-expanded={isExpanded}
                      className="cursor-pointer hover:bg-muted/30"
                      onClick={() => setExpandedToolName((currentName) => currentName === tool.name ? null : tool.name)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          setExpandedToolName((currentName) => currentName === tool.name ? null : tool.name)
                        }
                      }}
                    >
                      <ItemContent>
                        <ItemHeader>
                          <ItemTitle>
                            <span className="font-mono">{tool.name}</span>
                          </ItemTitle>
                          <div className="flex flex-wrap justify-end gap-2 shrink-0">
                            <Badge variant="outline">{isExpanded ? 'Hide inputs' : 'Show inputs'}</Badge>
                          </div>
                        </ItemHeader>
                        <ItemDescription>
                          {tool.description?.trim() || 'No functional description provided for this internal endpoint.'}
                        </ItemDescription>
                        <ItemDescription className="mt-2 text-xs flex items-center gap-1.5">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /><polyline points="3.27 6.96 12 12.01 20.73 6.96" /><line x1="12" y1="22.08" x2="12" y2="12" /></svg>
                          Category: {formatValue(tool.category)}
                          <span className="mx-1">•</span>
                          Inputs: {tool.parameters?.length || 0}
                        </ItemDescription>
                        {isExpanded ? (
                          <div className="mt-4 basis-full rounded-md border border-border/60 bg-muted/20 p-4">
                            <div className="mb-3 flex items-center justify-between gap-3">
                              <h4 className="text-sm font-semibold">Inputs</h4>
                              <span className="text-xs text-muted-foreground">{tool.parameters?.length || 0} total</span>
                            </div>
                            {renderParameters(tool.parameters ?? [])}
                          </div>
                        ) : null}
                      </ItemContent>
                    </Item>
                  )
                })}
              </ItemGroup>
            </section>
          ))}
        </div>
      )}
    </section>
  )
}
