import ReactECharts from 'echarts-for-react'

type ChartArtifact = {
  title: string | null
  content: string
  metadata: string | null
}

type GenericRecord = Record<string, unknown>

function isRecord(value: unknown): value is GenericRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseJson(value: string | null | undefined): unknown {
  if (!value?.trim()) {
    return null
  }
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function normalizePieData(data: unknown) {
  if (!Array.isArray(data)) {
    return []
  }
  return data.flatMap((entry) => {
    if (isRecord(entry) && typeof entry.name === 'string' && typeof entry.value === 'number') {
      return [{ name: entry.name, value: entry.value }]
    }
    if (Array.isArray(entry) && entry.length >= 2 && typeof entry[0] === 'string' && typeof entry[1] === 'number') {
      return [{ name: entry[0], value: entry[1] }]
    }
    return []
  })
}

function normalizeCartesianSeries(data: unknown, fallbackType: 'line' | 'bar') {
  if (Array.isArray(data)) {
    if (data.every((entry) => typeof entry === 'number')) {
      return [{ type: fallbackType, data }]
    }

    return data.flatMap((entry) => {
      if (!isRecord(entry)) {
        return []
      }
      const type = entry.type === 'bar' || entry.type === 'line' ? entry.type : fallbackType
      const seriesData = Array.isArray(entry.data) ? entry.data.filter((item) => typeof item === 'number') : []
      return seriesData.length > 0
        ? [{
            name: typeof entry.name === 'string' ? entry.name : undefined,
            type,
            data: seriesData,
          }]
        : []
    })
  }

  return []
}

function buildOption(contentObject: unknown, metadataObject: unknown, title: string | null) {
  if (isRecord(contentObject) && (contentObject.series || contentObject.xAxis || contentObject.yAxis)) {
    return contentObject
  }

  const contentRecord = isRecord(contentObject) ? contentObject : {}
  const metadataRecord = isRecord(metadataObject) ? metadataObject : {}
  const chartType = contentRecord.chartType === 'pie' || contentRecord.chartType === 'line' || contentRecord.chartType === 'bar'
    ? contentRecord.chartType
    : metadataRecord.chartType === 'pie' || metadataRecord.chartType === 'line' || metadataRecord.chartType === 'bar'
      ? metadataRecord.chartType
      : 'bar'

  const effectiveTitle = typeof contentRecord.title === 'string'
    ? contentRecord.title
    : typeof metadataRecord.title === 'string'
      ? metadataRecord.title
      : title ?? 'Chart'

  if (chartType === 'pie') {
    const pieData = normalizePieData(contentRecord.data)
    if (pieData.length === 0) {
      return null
    }
    return {
      backgroundColor: 'transparent',
      title: { text: effectiveTitle, left: 'center', textStyle: { color: '#E6EDF3', fontSize: 16, fontWeight: 600 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: '#E6EDF3' } },
      series: [
        {
          name: effectiveTitle,
          type: 'pie',
          radius: ['35%', '68%'],
          data: pieData,
          label: { color: '#E6EDF3' },
        },
      ],
    }
  }

  const xAxis = Array.isArray(contentRecord.xAxis)
    ? contentRecord.xAxis.filter((entry) => typeof entry === 'string')
    : []
  const series = normalizeCartesianSeries(contentRecord.series ?? contentRecord.data, chartType)
  if (xAxis.length === 0 || series.length === 0) {
    return null
  }

  return {
    backgroundColor: 'transparent',
    title: { text: effectiveTitle, left: 'center', textStyle: { color: '#E6EDF3', fontSize: 16, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    legend: { top: 28, textStyle: { color: '#E6EDF3' } },
    grid: { left: 48, right: 24, top: 72, bottom: 40 },
    xAxis: {
      type: 'category',
      data: xAxis,
      axisLabel: { color: '#E6EDF3' },
      axisLine: { lineStyle: { color: '#7DFDFE' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#E6EDF3' },
      splitLine: { lineStyle: { color: 'rgba(125, 253, 254, 0.18)' } },
      axisLine: { lineStyle: { color: '#7DFDFE' } },
    },
    series: series.map((entry) => ({
      ...entry,
      smooth: entry.type === 'line',
    })),
  }
}

export default function ChartRenderer({ artifact }: { artifact: ChartArtifact }) {
  const contentObject = parseJson(artifact.content)
  const metadataObject = parseJson(artifact.metadata)
  const option = buildOption(contentObject, metadataObject, artifact.title)

  if (!option) {
    return (
      <div className="space-y-3">
        <div className="text-sm font-semibold">Chart data is not in a supported shape.</div>
        <pre className="overflow-auto border border-black p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap break-all">
          {artifact.content}
        </pre>
      </div>
    )
  }

  return (
    <div className="h-full min-h-[360px]">
      <ReactECharts
        option={option}
        notMerge
        lazyUpdate
        style={{ height: '100%', minHeight: 360, width: '100%' }}
      />
    </div>
  )
}
