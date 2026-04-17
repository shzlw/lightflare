import React, { useMemo } from 'react';
import { Zap, Box, Brain, MoreHorizontal } from 'lucide-react';

interface WorkflowStep {
  stepId: string;
  type?: string;
  actionIdentifier?: string;
  transitions?: Array<{ conditionExpression: string; targetStepId: string }>;
}

interface WorkflowGraphProps {
  steps: WorkflowStep[];
  activeStepId?: string;
  onStepClick?: (stepId: string) => void;
}

const NODE_WIDTH = 256;
const NODE_HEIGHT = 92;
const GAP_X = 120;
const GAP_Y = 130;
const MARGIN = 64;

export const WorkflowGraph: React.FC<WorkflowGraphProps> = ({ steps, activeStepId, onStepClick }) => {
  const layout = useMemo(() => buildLayout(steps), [steps]);

  const getIcon = (type?: string) => {
    switch ((type || 'TOOL').toUpperCase()) {
      case 'TRIGGER': return <Zap className="w-4 h-4 text-blue-500" />;
      case 'TOOL': return <Box className="w-4 h-4 text-emerald-400" />;
      case 'LLM': return <Brain className="w-4 h-4 text-amber-400" />;
      default: return <MoreHorizontal className="w-4 h-4 text-muted-foreground" />;
    }
  };

  if (steps.length === 0) {
    return (
      <div className="w-full h-full bg-muted/10 flex items-center justify-center text-sm text-muted-foreground">
        No steps defined.
      </div>
    );
  }

  return (
    <div className="relative w-full h-full bg-muted/10 overflow-auto select-none">
      <div
        className="relative"
        style={{ width: layout.width, height: layout.height }}
      >
        <svg className="absolute inset-0 pointer-events-none" width={layout.width} height={layout.height}>
          <defs>
            <marker
              id="workflow-arrowhead"
              markerWidth="10"
              markerHeight="7"
              refX="9"
              refY="3.5"
              orient="auto"
            >
              <polygon points="0 0, 10 3.5, 0 7" fill="var(--muted-foreground)" />
            </marker>
          </defs>
          {layout.edges.map((edge) => (
            <g key={`${edge.from}-${edge.to}-${edge.label}`}>
              <path
                d={edge.path}
                fill="none"
                stroke="var(--border)"
                strokeWidth="2"
                markerEnd="url(#workflow-arrowhead)"
              />
              <foreignObject x={edge.labelX - 80} y={edge.labelY - 12} width="160" height="28">
                <div className="mx-auto max-w-[150px] truncate rounded bg-background px-2 py-1 text-center text-[10px] font-medium text-muted-foreground ring-1 ring-border shadow-sm">
                  {edge.label}
                </div>
              </foreignObject>
            </g>
          ))}
        </svg>

        {layout.nodes.map(({ step, x, y }) => (
          <button
            key={step.stepId}
            type="button"
            onClick={() => onStepClick?.(step.stepId)}
            className={`
              absolute z-10 text-left p-4 rounded-lg border transition-colors cursor-pointer
              ${activeStepId === step.stepId
                ? 'bg-background border-primary shadow-md'
                : 'bg-background/95 border-border hover:border-primary/50 hover:shadow-sm'}
            `}
            style={{ left: x, top: y, width: NODE_WIDTH, height: NODE_HEIGHT }}
          >
            <div className="flex items-center gap-3 mb-2">
              <div className="p-2 rounded bg-muted shadow-inner">
                {getIcon(step.type)}
              </div>
              <span className="text-sm font-medium text-foreground truncate">{step.stepId}</span>
            </div>
            <div className="text-[10px] uppercase text-muted-foreground font-bold ml-1 truncate">
              {step.actionIdentifier || step.type || 'step'}
            </div>

            {activeStepId === step.stepId && (
              <div className="absolute -right-1.5 -top-1.5">
                <div className="w-3 h-3 bg-primary rounded-full" />
              </div>
            )}
          </button>
        ))}
      </div>
    </div>
  );
};

function buildLayout(steps: WorkflowStep[]) {
  const byId = new Map(steps.map((step) => [step.stepId, step]));
  const start = steps.find((step) => step.type?.toUpperCase() === 'TRIGGER') || steps[0];
  const levels = new Map<string, number>();
  const queue: string[] = [];

  if (start) {
    levels.set(start.stepId, 0);
    queue.push(start.stepId);
  }

  while (queue.length > 0) {
    const stepId = queue.shift()!;
    const step = byId.get(stepId);
    const level = levels.get(stepId) || 0;
    for (const transition of step?.transitions || []) {
      const target = transition.targetStepId;
      if (!target || target.toLowerCase() === 'end' || !byId.has(target)) {
        continue;
      }
      const nextLevel = level + 1;
      if (!levels.has(target) || nextLevel > (levels.get(target) || 0)) {
        levels.set(target, nextLevel);
        queue.push(target);
      }
    }
  }

  for (const step of steps) {
    if (!levels.has(step.stepId)) {
      levels.set(step.stepId, Math.max(0, ...levels.values()) + 1);
    }
  }

  const groups = new Map<number, WorkflowStep[]>();
  for (const step of steps) {
    const level = levels.get(step.stepId) || 0;
    groups.set(level, [...(groups.get(level) || []), step]);
  }

  const maxColumns = Math.max(1, ...Array.from(groups.values()).map((group) => group.length));
  const width = MARGIN * 2 + maxColumns * NODE_WIDTH + (maxColumns - 1) * GAP_X;
  const maxLevel = Math.max(0, ...levels.values());
  const height = MARGIN * 2 + (maxLevel + 1) * NODE_HEIGHT + maxLevel * GAP_Y;
  const positioned = new Map<string, { step: WorkflowStep; x: number; y: number }>();

  for (const [level, group] of groups) {
    const rowWidth = group.length * NODE_WIDTH + Math.max(0, group.length - 1) * GAP_X;
    const startX = Math.max(MARGIN, (width - rowWidth) / 2);
    group.forEach((step, index) => {
      positioned.set(step.stepId, {
        step,
        x: startX + index * (NODE_WIDTH + GAP_X),
        y: MARGIN + level * (NODE_HEIGHT + GAP_Y),
      });
    });
  }

  const edges = steps.flatMap((step) => {
    const from = positioned.get(step.stepId);
    if (!from) return [];
    return (step.transitions || []).flatMap((transition) => {
      const to = positioned.get(transition.targetStepId);
      if (!to) return [];

      const startX = from.x + NODE_WIDTH / 2;
      const startY = from.y + NODE_HEIGHT;
      const endX = to.x + NODE_WIDTH / 2;
      const endY = to.y;
      const midY = startY + (endY - startY) / 2;
      const path = `M ${startX} ${startY} C ${startX} ${midY}, ${endX} ${midY}, ${endX} ${endY}`;

      return [{
        from: step.stepId,
        to: transition.targetStepId,
        label: summarizeCondition(transition.conditionExpression),
        labelX: (startX + endX) / 2,
        labelY: midY,
        path,
      }];
    });
  });

  return {
    nodes: Array.from(positioned.values()),
    edges,
    width,
    height,
  };
}

function summarizeCondition(condition: string) {
  if (!condition) return 'condition';
  if (condition.length <= 34) return condition;
  return `${condition.slice(0, 31)}...`;
}

export default WorkflowGraph;
