# Third-Party XMAX Import Adapter Template

Use this template when adding "export to Watcher / XMAX external import" support to another project.

The target contract is defined in `docs/xmax-external-import-adapter-protocol.md`. This file is the practical implementation checklist.

## 1. Export Capability Statement

Copy this section into the third-party project documentation and fill in the values.

```markdown
# Watcher External Import Support

This project can export a `xmax.external_import.v1` payload for Watcher-compatible target apps.

## Exported Resources

- Provider: yes
- Agent: yes/no
- AI audience: yes/no
- Expert council: yes/no

## Stable IDs

- Provider ID: `<stable_provider_id>`
- Agent ID: `<stable_agent_id_or_empty>`
- Audience key: `<stable_room_or_audience_name_or_empty>`
- Council key: `<stable_topic_or_empty>`

## Application Behavior In Target App

- Provider applies automatically into the target API wallet.
- Agent applies automatically only if the target supports the runtime; otherwise it is staged.
- AI audience is staged first and must be applied from the target workbench.
- Expert council is staged first and must be applied from the target workbench.
```

## 2. Payload Builder Template

Use stable IDs. Do not generate random IDs on each export.

```kotlin
data class XmaxExternalImportPayload(
    val schemaVersion: String = "xmax.external_import.v1",
    val providerId: String,
    val providerName: String,
    val endpoint: String,
    val apiKey: String,
    val modelName: String,
    val enabled: Boolean = true,
    val makeDefault: Boolean = false,
    val allowInsecureTls: Boolean = false,
    val sourceSiteName: String = "",
    val sourceModelMode: String = "",
    val agentConfig: AgentConfig? = null,
    val audienceConfig: AudienceConfig? = null,
    val expertCouncilConfig: ExpertCouncilConfig? = null
)

data class AgentConfig(
    val enabled: Boolean,
    val agentId: String,
    val agentName: String,
    val systemPrompt: String,
    val entryPoint: String
)

data class AudienceConfig(
    val enabled: Boolean,
    val roomName: String,
    val focusPrompt: String,
    val responseStyle: String
)

data class ExpertCouncilConfig(
    val enabled: Boolean,
    val topic: String,
    val memberRoles: List<String>,
    val workflow: String
)
```

Example:

```json
{
  "schemaVersion": "xmax.external_import.v1",
  "providerId": "myapp_main_chat",
  "providerName": "MyApp Main Chat",
  "endpoint": "https://api.example.com/v1",
  "apiKey": "sk-example",
  "modelName": "gpt-5.5",
  "enabled": true,
  "makeDefault": true,
  "sourceSiteName": "MyApp",
  "sourceModelMode": "chat",
  "agentConfig": {
    "enabled": true,
    "agentId": "myapp_watcher_agent",
    "agentName": "MyApp Watcher Agent",
    "systemPrompt": "Observe the current task and produce concise advice.",
    "entryPoint": "myapp://agents/watcher"
  },
  "audienceConfig": {
    "enabled": true,
    "roomName": "MyApp Audience",
    "focusPrompt": "Watch for user intent, risks, and useful next actions.",
    "responseStyle": "short, direct"
  },
  "expertCouncilConfig": {
    "enabled": true,
    "topic": "MyApp Review Council",
    "memberRoles": ["product", "engineering", "qa"],
    "workflow": "list risks, compare options, then recommend one action"
  }
}
```

## 3. Android Intent Sender Template

```kotlin
fun openWatcherImport(context: Context, payloadJson: String) {
    val intent = Intent("com.xmax.watcher.action.OPEN_WALLET")
        .setPackage("com.xmax.watcher")
        .putExtra("com.xmax.external.extra.IMPORT_PAYLOAD", payloadJson)

    context.startActivity(intent)
}
```

If the target app returns a result through `startActivityForResult` or Activity Result APIs, read:

```kotlin
val message = data?.getStringExtra("com.xmax.external.extra.RESULT_MESSAGE")
val payload = data?.getStringExtra("com.xmax.external.extra.RESULT_PAYLOAD")
```

Display `message` to the user. Log or inspect `payload` for per-resource status.

## 4. Web Or Server Payload Builder Template

For non-Android projects, generate the same JSON and hand it to the Android app layer, QR bridge, clipboard, or local gateway.

```javascript
export function buildWatcherImportPayload(config) {
  return {
    schemaVersion: "xmax.external_import.v1",
    providerId: config.providerId,
    providerName: config.providerName,
    endpoint: config.endpoint,
    apiKey: config.apiKey,
    modelName: config.modelName,
    enabled: config.enabled ?? true,
    makeDefault: config.makeDefault ?? false,
    sourceSiteName: config.sourceSiteName ?? "",
    sourceModelMode: config.sourceModelMode ?? "",
    agentConfig: config.agentConfig ?? undefined,
    audienceConfig: config.audienceConfig ?? undefined,
    expertCouncilConfig: config.expertCouncilConfig ?? undefined
  };
}
```

## 5. Validation Checklist

Before sending payload:

- `schemaVersion` is exactly `xmax.external_import.v1`.
- `providerId`, `providerName`, `endpoint`, `apiKey`, and `modelName` are non-empty.
- `endpoint` starts with `https://`.
- `providerId` is stable across exports.
- Optional `agentConfig.agentId` is stable when present.
- Optional `audienceConfig.roomName` is stable when present.
- Optional `expertCouncilConfig.topic` is stable when present.
- The UI explains which sections apply automatically and which sections are staged.

## 6. Expected Target Behavior

Watcher-compatible targets should behave like this:

| Resource | Expected target behavior |
| --- | --- |
| Provider | Save into API wallet immediately. |
| Agent | Register automatically when compatible; otherwise show manual action. |
| AI audience | Stage, then let user apply in hidden/common workbench. |
| Expert council | Stage, then let user apply in hidden/common workbench. |

If a target has no matching Provider when applying an AI audience, it should save the audience as a disabled draft instead of rejecting the import.

## 7. Test Payloads

### Provider Only

```json
{
  "schemaVersion": "xmax.external_import.v1",
  "providerId": "provider_only_main",
  "providerName": "Provider Only Main",
  "endpoint": "https://api.example.com/v1",
  "apiKey": "sk-test",
  "modelName": "gpt-5.5",
  "enabled": true,
  "makeDefault": true,
  "sourceSiteName": "ProviderOnly",
  "sourceModelMode": "chat"
}
```

### Full Payload

```json
{
  "schemaVersion": "xmax.external_import.v1",
  "providerId": "full_demo_main",
  "providerName": "Full Demo Main",
  "endpoint": "https://api.example.com/v1",
  "apiKey": "sk-test",
  "modelName": "gpt-5.5",
  "enabled": true,
  "makeDefault": true,
  "sourceSiteName": "FullDemo",
  "sourceModelMode": "chat",
  "agentConfig": {
    "enabled": true,
    "agentId": "full_demo_agent",
    "agentName": "Full Demo Agent",
    "systemPrompt": "Help Watcher reason about imported context.",
    "entryPoint": "fulldemo://agent/main"
  },
  "audienceConfig": {
    "enabled": true,
    "roomName": "Full Demo Audience",
    "focusPrompt": "Watch for risks and useful improvements.",
    "responseStyle": "brief"
  },
  "expertCouncilConfig": {
    "enabled": true,
    "topic": "Full Demo Council",
    "memberRoles": ["pm", "android", "qa"],
    "workflow": "risks first, then final recommendation"
  }
}
```

### Invalid Future Version

Targets should reject this payload with a clear unsupported-version message:

```json
{
  "schemaVersion": "xmax.external_import.v2",
  "providerId": "future_version_main",
  "providerName": "Future Version Main",
  "endpoint": "https://api.example.com/v1",
  "apiKey": "sk-test",
  "modelName": "gpt-5.5"
}
```

## 8. Prompt For An AI Coding Agent

```text
Add support for exporting a Watcher-compatible XMAX External Import Adapter payload.

Use schemaVersion "xmax.external_import.v1".

Implement a payload builder with these required Provider fields:
- providerId
- providerName
- endpoint
- apiKey
- modelName
- enabled
- makeDefault
- sourceSiteName
- sourceModelMode

Map local agent/persona/workflow features into these optional sections when available:
- agentConfig: enabled, agentId, agentName, systemPrompt, entryPoint
- audienceConfig: enabled, roomName, focusPrompt, responseStyle
- expertCouncilConfig: enabled, topic, memberRoles, workflow

Add validation:
- endpoint must be HTTPS
- stable IDs must not be regenerated on each export
- unknown schema versions must not be emitted

Add docs explaining what is exported and what remains staged for user confirmation in the target app.
Do not depend on Watcher private database tables.
```
