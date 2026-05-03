# XMAX External Import Adapter Protocol

## Purpose

This document defines the stable middle-layer contract between XMAX-like source projects and Watcher-like target apps.

The target app should not depend on XMAX internals, and the source app should not write directly into Watcher private databases. The source app exports one JSON payload. The target app receives it, stores imported sections as staged state, applies the sections it owns, and reports a structured result.

Watcher is the first implementation of this protocol, not the protocol itself.

## Design Goals

1. Keep Watcher source changes small and localized.
2. Let other apps support the same import flow without copying Watcher UI code.
3. Allow partial success: Provider may apply immediately while Agent, audience, or council sections wait for review.
4. Preserve stable resource identity so repeated imports update existing local resources instead of creating duplicates.
5. Make status and next-step messages readable by both users and AI coding agents.

## Transport

### Android Intent

Targets should support this action:

```text
com.xmax.watcher.action.OPEN_WALLET
```

For generic adapters, targets should also accept this payload extra:

```text
com.xmax.external.extra.IMPORT_PAYLOAD
```

Watcher also keeps legacy extras for compatibility:

```text
com.xmax.watcher.extra.IMPORT_PAYLOAD
com.example.watcher.extra.IMPORT_PAYLOAD
```

### Result Extras

Targets should return a short human message and a structured result:

```text
com.xmax.external.extra.RESULT_MESSAGE
com.xmax.external.extra.RESULT_PAYLOAD
```

Watcher also mirrors the message to legacy result keys:

```text
com.xmax.watcher.extra.RESULT_MESSAGE
com.example.watcher.extra.RESULT_MESSAGE
```

## Payload Versioning

The current Watcher implementation accepts payloads without `schemaVersion`. New exporters should include it.

```json
{
  "schemaVersion": "xmax.external_import.v1"
}
```

Targets must treat a missing `schemaVersion` as `xmax.external_import.v1` for compatibility. Unknown future versions should be rejected with a clear result message instead of silently importing.

## Payload Shape

Provider fields are the minimum supported import. Other sections are optional.

```json
{
  "schemaVersion": "xmax.external_import.v1",
  "providerId": "xmax_main_chat",
  "providerName": "XMAX Main",
  "endpoint": "https://api.example.com/v1",
  "apiKey": "sk-xxx",
  "modelName": "gpt-5.5",
  "enabled": true,
  "makeDefault": true,
  "allowInsecureTls": false,
  "sourceSiteName": "XMAX",
  "sourceModelMode": "chat",
  "agentConfig": {
    "enabled": true,
    "agentId": "watcher_agent",
    "agentName": "Watcher Agent",
    "systemPrompt": "Run the Watcher-side agent workflow.",
    "entryPoint": "watcher://agent/main"
  },
  "audienceConfig": {
    "enabled": true,
    "roomName": "AI Audience",
    "focusPrompt": "Watch for risk signals and useful opportunities.",
    "responseStyle": "short, direct"
  },
  "expertCouncilConfig": {
    "enabled": true,
    "topic": "Launch Review",
    "memberRoles": ["product", "android", "qa"],
    "workflow": "discuss risks first, then produce a decision"
  }
}
```

## Field Rules

### Provider

Required:

- `providerId`: stable provider key. Must not be random per export.
- `providerName`: display name.
- `endpoint`: OpenAI-compatible base URL. Watcher currently requires HTTPS.
- `apiKey`: secret used by the target app.
- `modelName`: default model for this provider.

Optional:

- `enabled`: defaults to `true`.
- `makeDefault`: defaults to `false`.
- `allowInsecureTls`: currently ignored by Watcher; HTTPS is still required.
- `sourceSiteName`: source product/site label.
- `sourceModelMode`: source model mode label, for example `chat`, `vision`, or `analysis`.

### Agent

Optional object: `agentConfig`.

- `agentId`: stable key for the source agent.
- `agentName`: display name.
- `systemPrompt`: prompt or behavior description.
- `entryPoint`: source-side route or logical entry.
- `enabled`: whether the source suggests enabling it after import.

Targets may apply Agent automatically when they have a compatible runtime. Otherwise they should stage it and mark it as manual action.

### AI Audience

Optional object: `audienceConfig`.

- `roomName`: stable audience group key and display name.
- `focusPrompt`: what the audience watches for.
- `responseStyle`: output style guidance.
- `enabled`: source suggestion only. If the target has no Provider, it should save as disabled draft.

### Expert Council

Optional object: `expertCouncilConfig`.

- `topic`: stable council template key and display name.
- `memberRoles`: role names.
- `workflow`: collaboration flow.
- `enabled`: source suggestion only.

Targets should usually stage this first, then allow the user to apply it into local templates.

## Import States

Targets should expose each resource with one of these states:

- `APPLIED`: already written into the target app and usable.
- `RECEIVED`: payload accepted, not yet applied.
- `NEEDS_MANUAL_ACTION`: accepted but requires user review before local application.
- `FAILED`: attempted application failed.

Watcher mapping:

- Provider: `APPLIED` after wallet save.
- Agent: `APPLIED` only when runtime registration succeeds.
- AI audience: `NEEDS_MANUAL_ACTION` until applied in hidden workbench.
- Expert council: `NEEDS_MANUAL_ACTION` until applied in hidden workbench.

## Target-Side Boundaries

Watcher should keep import logic inside these bounded areas:

- `WatcherExternalImportContract`: parse payload, validate fields, build import status and result messages.
- `WatcherImportActivity`: Android entry point, payload persistence, Provider save, Agent registration.
- `ImportWorkbenchRepository` and `ImportWorkbenchContract`: generic workbench batch and status cards.
- `HiddenWorkbenchImportRepository`: read and clear staged audience/council import state.
- Hidden-workbench apply service: convert staged sections into Watcher entities and clear applied drafts.

UI files should only display state and call application methods. They should not choose Provider fallbacks, decide draft enablement, or mutate import-state storage directly.

## Source-Side Export Checklist

Source projects should implement:

1. A stable payload builder.
2. A launch path to send the payload to the target app.
3. Result handling for `RESULT_MESSAGE` and `RESULT_PAYLOAD`.
4. Documentation that lists which sections are exported and which sections the target applies automatically.

## Prompt For Other Project Authors

```text
Please add support for the XMAX External Import Adapter Protocol.

Goal:
- Export one JSON payload that a Watcher-like target app can import.
- Do not write to Watcher private storage or depend on Watcher database tables.
- Use stable IDs so repeated imports update the same resources.

Implement:
1. Build a payload with schemaVersion "xmax.external_import.v1".
2. Include Provider fields: providerId, providerName, endpoint, apiKey, modelName, enabled, makeDefault, sourceSiteName, sourceModelMode.
3. If your project has agents, map them to agentConfig.
4. If your project has AI audience / persona groups, map them to audienceConfig.
5. If your project has experts, councils, workflows, or review panels, map them to expertCouncilConfig.
6. Send the payload through the Android extra com.xmax.external.extra.IMPORT_PAYLOAD where available.
7. Read and display com.xmax.external.extra.RESULT_MESSAGE and com.xmax.external.extra.RESULT_PAYLOAD.

Compatibility:
- endpoint must be HTTPS by default.
- Missing schemaVersion should still export v1-compatible fields.
- Sections that cannot be auto-applied should still be included so the target app can stage them for user review.

Please also add a short document in your repo explaining:
- which resources are exported
- which IDs are stable
- which sections apply automatically
- which sections remain staged for manual confirmation
```

## Current Watcher Gaps

Watcher already implements the v1 flow, but these items remain future work:

1. Parse and validate `schemaVersion` explicitly.
2. Generalize staged sections beyond one audience and one expert council.
3. Return per-resource IDs in `RESULT_PAYLOAD`.
4. Add a user-facing import history list instead of only the latest batch.
