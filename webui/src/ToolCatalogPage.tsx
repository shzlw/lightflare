import { useMemo, useState } from 'react'

import { Input } from '@/components/ui/input'
import ToolCatalogPanel, { type Tool } from './ToolCatalogPanel'
import { Search, Wrench, Boxes } from 'lucide-react'

export default function ToolCatalogPage() {
  const [tools, setTools] = useState<Tool[]>([])
  const [search, setSearch] = useState('')

  const filteredTools = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    if (!normalizedSearch) {
      return tools
    }

    return tools.filter((tool) => {
      const haystack = [
        tool.name,
        tool.description,
        tool.integrationId,
        tool.sourceName,
        tool.sourceType,
        tool.category,
      ]
        .filter((value): value is string => Boolean(value))
        .join(' ')
        .toLowerCase()

      return haystack.includes(normalizedSearch)
    })
  }, [search, tools])


  return (
    <div className="w-full max-w-7xl mx-auto space-y-8 p-6 md:p-8 animate-in fade-in duration-500">
      <header className="flex flex-col gap-2 mb-8">
        <div className="flex items-center gap-3">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">Tool Catalog</h2>
        </div>
      </header>

      <section className="space-y-6">
        <div className="grid gap-3 md:grid-cols-3">
          <div className="relative overflow-hidden border border-black bg-card/50 p-3">
            <div className="flex flex-col gap-0.5">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Total Tools</span>
              <span className="text-xl font-bold leading-tight">{tools.length}</span>
            </div>
            <Wrench className="absolute right-3 top-1/2 -translate-y-1/2 h-6 w-6 text-primary/10 group-hover:text-primary/20 transition-colors" />
          </div>
          <div className="relative overflow-hidden border border-black bg-emerald-500/5 p-3">
            <div className="flex flex-col gap-0.5">
              <span className="text-[10px] font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-widest">Local Definitions</span>
              <span className="text-xl font-bold leading-tight text-emerald-700 dark:text-emerald-300">{tools.filter(t => t.sourceType === 'LOCAL').length}</span>
            </div>
            <Boxes className="absolute right-3 top-1/2 -translate-y-1/2 h-6 w-6 text-emerald-500/10 group-hover:text-emerald-500/20 transition-colors" />
          </div>
          <div className="relative overflow-hidden border border-black bg-blue-500/5 p-3">
            <div className="flex flex-col gap-0.5">
              <span className="text-[10px] font-bold text-blue-600 dark:text-blue-400 uppercase tracking-widest">MCP Integrations</span>
              <span className="text-xl font-bold leading-tight text-blue-700 dark:text-blue-300">{tools.filter(t => t.sourceType === 'MCP').length}</span>
            </div>
            <Boxes className="absolute right-3 top-1/2 -translate-y-1/2 h-6 w-6 text-blue-500/10 group-hover:text-blue-500/20 transition-colors" />
          </div>
        </div>

        <div className="flex flex-col items-center justify-between gap-4 border border-black bg-muted/30 p-2">
          <div className="w-full relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              className="h-10 w-full border-black bg-background pl-10 rounded-none"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search tool gallery by name, source, or category..."
            />
          </div>
        </div>

        <ToolCatalogPanel tools={filteredTools} onToolsChange={setTools} className="mt-4" />
      </section>
    </div>
  )
}
