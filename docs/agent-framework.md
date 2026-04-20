# Agent Framework

This framework is intentionally isolated from the current watcher business code.
It is designed as a reusable autonomous agent runtime that can be integrated later.

## Included pieces

- `AgentKernel`: shared entry point that owns tools, memory, and sessions.
- `AgentSessionController`: a self-running session with lifecycle, event stream, and stop conditions.
- `AutonomousAgentRuntime`: a higher-level closed-loop runtime that wires perception, memory, cognition, execution, feedback, learning, and communication together.
- `AgentBrain`: pluggable decision engine.
- `JsonProtocolAgentBrain`: LLM-ready adapter that expects strict JSON actions.
- `AgentToolRegistry`: shared tool registration and execution.
- `InMemoryAgentMemoryStore`: default session memory implementation.
- `AgentFrameworkService`: external-facing facade for agent registration, invocation, run tracking, and cross-run evolution.
- `AgentProfileStore`: persistent profile abstraction for agent identity, config, and evolution state.
- `AgentProfileEvolutionStrategy`: pluggable strategy that turns completed runs into long-term profile and knowledge updates.

## What makes it a real agent runtime

- A session can run for multiple steps without business code orchestrating every turn.
- The agent can choose between continue, tool calls, waiting, or finishing.
- Tool execution is captured into history and fed back into the next decision round.
- Working and episodic memory writes are persisted across steps inside the session.
- Runtime budget, idle budget, and consecutive failure limits are enforced by the framework.
- State changes are exposed through `StateFlow` and `SharedFlow`.
- The framework can now register agents as durable runtime identities and invoke them as independent agents from external callers.
- Agent profiles and knowledge can evolve across multiple runs instead of being discarded after one request.

## Layer mapping

The codebase now separates the framework into two levels:

- Low-level runtime primitives:
  - `core/`
  - `memory/`
  - `tools/`
  - `runtime/`
- Closed-loop autonomous runtime:
  - `autonomy/`
- Multi-agent collaboration runtime:
  - `multiagent/`
- External invocation and lifecycle facade:
  - `service/`

The `autonomy/` package maps directly to the expected agent layers:

- Base support layer:
  - `AutonomousAgentRuntime`
  - lifecycle state and runtime budget handling
- Perception and input layer:
  - `SignalAdapter`
  - `PerceptionPipeline`
  - `CommunicationHub`
- Memory and knowledge layer:
  - `StructuredMemoryManager`
  - `StructuredMemorySnapshot`
- Cognition and decision layer:
  - `GoalParser`
  - `TaskPlanner`
  - `ReasoningEngine`
  - `DecisionSelector`
  - `RuleConstraintEngine`
- Tool and execution layer:
  - `ExecutionCoordinator`
  - `AgentToolRegistry`
- Feedback and validation layer:
  - `ResultValidator`
  - `FeedbackProcessor`
- Learning and iteration layer:
  - `LearningEngine`
  - `EvaluationEngine`
- Communication and collaboration layer:
  - `CommunicationHub`

The `multiagent/` package adds the missing team-level collaboration pieces:

- Team foundation:
  - `TeamDefinition`
  - `TeamAgentSpec`
  - `TeamTask`
  - `TeamSnapshot`
- Shared collaboration infrastructure:
  - `SharedBlackboard`
  - `TeamMessageBus`
  - `TeamAgentRegistry`
- Team cognition and coordination:
  - `TeamTaskPlanner`
  - `TeamTaskAssignmentStrategy`
  - `ConsensusStrategy`
  - `MultiAgentCoordinator`
- Agent runtime adapter:
  - `CollaborativeAgentFactory`
  - `AutonomousCollaborativeAgentFactory`

## Multi-agent collaboration

The team runtime is designed around a strict separation of concerns:

- Single-agent autonomy remains inside `autonomy/`.
- Team coordination remains inside `multiagent/`.
- Collaboration state is externalized into the blackboard and message bus.
- Consensus is computed at the team layer, not hidden inside one agent.

This lets you build:

- planner / executor / reviewer teams
- specialist ensembles
- leader / follower hierarchies
- blackboard-driven cooperative systems
- consensus-based decision teams

### Core collaboration flow

1. Define a `TeamDefinition` with members and roles.
2. Create a `MultiAgentCoordinator`.
3. The coordinator initializes initial tasks through `TeamTaskPlanner`.
4. Tasks are assigned through `TeamTaskAssignmentStrategy`.
5. Each agent runs independently through a `CollaborativeAgentHandle`.
6. Outputs are published into the shared blackboard and broadcast as team messages.
7. Follow-up tasks can be added dynamically.
8. Final proposals are merged by `ConsensusStrategy`.

### Team runtime usage

```kotlin
val coordinator = MultiAgentCoordinator(
    team = TeamDefinition(
        teamId = "incident_team",
        name = "Incident Team",
        rootGoal = "Diagnose and resolve the incident",
        members = members
    ),
    agentFactory = AutonomousCollaborativeAgentFactory(
        modulesFactory = { spec, context ->
            defaultAutonomousModules(
                brain = JsonProtocolAgentBrain(myGateway),
                toolRegistry = AgentToolRegistry()
            )
        }
    ),
    parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
)

coordinator.start()
val finalTeamState = coordinator.awaitCompletion()
```

## Basic usage

```kotlin
val kernel = AgentKernel()
kernel.registerTool(MyTool())

val session = kernel.createSession(
    definition = AgentDefinition(
        agentId = "ops_agent",
        name = "Ops Agent",
        systemInstruction = "Solve the task using tools when needed.",
        goal = "Inspect a target and return a final answer."
    ),
    brain = JsonProtocolAgentBrain(myGateway)
)

session.start()
val finalSnapshot = session.awaitCompletion()
```

For the full closed-loop runtime:

```kotlin
val modules = defaultAutonomousModules(
    brain = JsonProtocolAgentBrain(myGateway),
    toolRegistry = AgentToolRegistry()
)

val runtime = AutonomousAgentRuntime(
    definition = AgentDefinition(
        agentId = "ops_agent",
        name = "Ops Agent",
        systemInstruction = "Observe, decide, act, validate, and iterate.",
        goal = "Drive the task to completion."
    ),
    config = AutonomousAgentConfig(),
    modules = modules,
    parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
)

runtime.start()
runtime.submitSignal(AgentSignal(channel = SignalChannel.User, content = "Start the task"))
val finalState = runtime.awaitCompletion()
```

## Independent agent invocation

For business code or external adapters that want to treat the framework as a standalone agent service, use `AgentFrameworkService`:

```kotlin
val service = AgentFrameworkService()

service.registerAgent(
    AgentRegistration(
        definition = AgentDefinition(
            agentId = "ops_agent",
            name = "Ops Agent",
            systemInstruction = "Solve tasks autonomously and learn from runs.",
            goal = "Handle operational requests."
        ),
        brain = JsonProtocolAgentBrain(myGateway)
    )
)

val result = service.invoke(
    AgentInvocationRequest(
        agentId = "ops_agent",
        inputs = listOf(
            AgentInvocationInput(
                role = AgentMessageRole.User,
                content = "Inspect the latest incident and summarize the root cause."
            )
        )
    )
)
```

This service layer provides:

- explicit agent registration and profile lookup
- synchronous or asynchronous invocation
- invocation status and final snapshot query
- cross-run profile evolution and knowledge accumulation
- a stable boundary for future HTTP / gateway adapters

## Memory and knowledge interfaces

The framework now exposes memory and knowledge through two coordinated paths:

- external service APIs on `AgentFrameworkService`
- built-in runtime tools automatically registered into `AgentToolRegistry`

External callers can:

- preload invocation memory through `AgentInvocationRequest.preloadMemory`
- preload agent knowledge through `AgentInvocationRequest.preloadKnowledge`
- read and append invocation memory through `readInvocationMemory(...)` and `writeInvocationMemory(...)`
- read, query, and append long-term knowledge through `readAgentKnowledge(...)`, `queryAgentKnowledge(...)`, and `writeAgentKnowledge(...)`

Agents can use the same stores at runtime through default tools:

- `read_memory`
- `write_memory`
- `read_knowledge`
- `query_knowledge`
- `write_knowledge`

This means the caller can inject context before a run, the agent can decide what to load while executing, and the agent can persist what it wants to keep after the run.

## JSON protocol

The default LLM adapter expects this shape:

```json
{
  "thinking": "short internal reasoning",
  "reply": "optional reply",
  "memory": [
    { "scope": "working", "content": "important detail" }
  ],
  "action": {
    "type": "continue",
    "reason": "why",
    "success": true,
    "resumeAfterMillis": 0,
    "calls": []
  }
}
```

Supported `action.type` values:

- `continue`
- `tool_calls`
- `wait`
- `finish`
