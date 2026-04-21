package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.memory.ConversationContext;
import com.lightflare.server.agent.skill.SkillContext;
import com.lightflare.server.harness.core.execution.PlanDag;
import com.lightflare.server.harness.core.event.HarnessExecutionListener;
import com.lightflare.server.harness.core.run.HarnessRunContext;
import com.lightflare.server.skill.Skill;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
class AgentExecutionState {

    private String checkpointId;
    private AgentRunCheckpoint checkpoint;
    private HarnessRunContext runContext;
    private ConversationContext conversationContext;
    private SkillContext skillContext;
    private PlanDag planDag;
    private Skill selectedSkill;
    private List<String> executionLog = new ArrayList<>();
    private int waveNumber;
    private int replanCount;
    private HarnessExecutionListener listener = HarnessExecutionListener.NOOP;

}
