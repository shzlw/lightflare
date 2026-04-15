import { useEffect, useState } from 'react'

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

export type Tool = {
  name: string
  description: string | null
  category: string | null
  integrationId: string | null
  sourceType: string | null
  sourceName: string | null
  parameters: unknown[]
}

type ToolCatalogPanelProps = {
  className?: string
  tools?: Tool[]
  onToolsChange?: (tools: Tool[]) => void
}

function formatValue(value: string | null | undefined) {
  return value && value.trim() ? value : 'Not set'
}

export default function ToolCatalogPanel({ className, tools: providedTools, onToolsChange }: ToolCatalogPanelProps) {
  const [tools, setTools] = useState<Tool[]>([])
  const [isLoading, setIsLoading] = useState(true)

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
        <h3 className="text-lg font-semibold text-foreground tracking-tight">Active Interfaces</h3>
        <Button variant="outline" onClick={() => void loadTools(true)} disabled={isLoading} className="shadow-sm hover:shadow-md transition-all h-9 gap-2">
          {isLoading ? (
            <div className="w-4 h-4 border-2 border-primary/20 border-t-primary rounded-full animate-spin" />
          ) : (
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" /><path d="M3 3v5h5" /></svg>
          )}
          Refresh
        </Button>
      </div>

      {isLoading && (providedTools ?? tools).length === 0 ? (
        <div className="py-20 flex flex-col items-center justify-center space-y-4 rounded-xl border border-border/40 bg-muted/10 animate-in fade-in duration-300">
          <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
          <p className="text-muted-foreground text-sm font-medium animate-pulse">Synchronizing tool schemas...</p>
        </div>
      ) : (providedTools ?? tools).length === 0 ? (
        <div className="py-12 flex flex-col items-center justify-center text-center border-2 border-dashed border-border/60 rounded-xl bg-muted/10">
          <div className="w-16 h-16 bg-muted/50 rounded-full flex items-center justify-center mb-4">
            <span className="text-2xl opacity-50">🛠️</span>
          </div>
          <h4 className="text-lg font-semibold mb-2">No tools connected</h4>
          <p className="text-muted-foreground text-sm max-w-md">The system could not locate any active tools matching your criteria or currently registered to the context.</p>
        </div>
      ) : (
        <ItemGroup>
            {(providedTools ?? tools).map((tool) => (
              <Item key={tool.name} variant="outline">
                <ItemContent>
                  <ItemHeader>
                    <ItemTitle>
                      <span className="font-mono">{tool.name}</span>
                    </ItemTitle>
                    <div className="flex flex-wrap justify-end gap-2 shrink-0">
                      <Badge variant="secondary">{tool.sourceType}</Badge>
                      <Badge variant="outline">{tool.sourceName}</Badge>
                    </div>
                  </ItemHeader>
                  <ItemDescription>
                    {tool.description?.trim() || 'No functional description provided for this internal endpoint.'}
                  </ItemDescription>
                  <ItemDescription className="mt-2 text-xs flex items-center gap-1.5">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /><polyline points="3.27 6.96 12 12.01 20.73 6.96" /><line x1="12" y1="22.08" x2="12" y2="12" /></svg>
                    Category: {formatValue(tool.category)}
                    <span className="mx-1">•</span>
                    Schemas: {tool.parameters?.length || 0}
                  </ItemDescription>
                </ItemContent>
              </Item>
            ))}
          </ItemGroup>
      )}
    </section>
  )
}
