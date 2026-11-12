# Efficient agent workflow

Read before choosing workers or delegating. Keep this policy identical across HyDiscordX, HyEssentialsX, and HyTeleportersX; keep project rules in each development guide. User instructions and host restrictions take precedence.

## Context budget

1. Identify the requested outcome, relevant package, and existing changes. Start with paths and symbols, not a full repository scan.
2. Use `rg --files <directory>` for discovery, `rg -n -m 20 <symbol> <directory>` for evidence, then read selected ranges. `-m` limits matches per file, so narrow directories too. Default tool output to roughly 1,500-3,000 tokens; expand only to resolve a specific uncertainty.
3. Never dump full staged imports, dependency trees, cached docs, generated assets, histories, or logs. For large Git states, summarize status counts and inspect selected paths. Search source before generated/decompiled copies. Do not read secrets or private runtime data into context.
4. Read each relevant guide once; retain paths and findings. Reuse worker findings instead of repeating exploration. Read adjacent callers/config/tests only when the change needs them. Follow all applicable instructions even when saving tokens.
5. Summarize large logs locally into failure lines and a small surrounding range. Keep concise progress updates and final reports. After compaction, resume from the handoff instead of restarting discovery.

## Model routing

Keep the primary model and reasoning selected by the user. Do not switch global settings or claim Markdown instructions automatically configure a client.

| Work | Preferred worker approach |
| --- | --- |
| Small edit, one lookup, tightly coupled steps | Primary agent; no delegation overhead |
| Narrow source search, documentation, mechanical edits, defined tests | Available economical model; lowest supported reasoning that reliably handles the task |
| Difficult bounded debugging, migration, cross-system reasoning | Available stronger model; raise reasoning only for the difficult part |
| Architecture, integration, sensitive permission/data decisions, final review | Primary agent retains responsibility |

CheddarLoader uses `gpt-6-luna` for routine work and `gpt-6.1-sol` for difficult work. Those are preferred examples **only when the actual spawning tool exposes them**. Start routine workers at `low`/`medium` reasoning when supported and sufficient; use `high`/`xhigh` only when the task warrants it. An available local model can handle narrow read-only discovery when its capabilities are known; do not assume it is reliable merely because it is local.

Check the actual tool schema/provider model list and supported reasoning efforts once. App model choices and thread-creation model lists do not establish subagent-tool availability. If a preferred pairing is unavailable, keep work in the primary agent or use an explicitly authorized available alternative; briefly disclose a material limitation. Do not repeatedly probe missing models, route work into new user-owned chats, or silently substitute an explicitly requested model.

For tools exposing `fork_turns`, use `"none"` with a compact self-contained brief when setting model/reasoning overrides. Full-history forks inherit parent settings in this environment. Other hosts may differ: follow the exposed schema, not copied parameter names. Do not invent configuration keys or create persistent model overrides just to enforce this policy.

## Delegate only when it pays

- This guide requests subagents for substantial independent tasks when supported and permitted. Parallelism can reduce elapsed time while increasing total tokens: use it only when useful isolation or avoided work outweighs setup and review.
- Default to one or two workers; never exceed the host limit. No recursive delegation by default. Reuse an existing worker for related follow-ups rather than spawning again.
- Favor read-only exploration or review first. Give editing workers disjoint files. Keep shared-file edits, dependent steps, migrations, deployment, and integration with the primary agent.
- Send only: goal/question, absolute checkout path, relevant symbols/files and constraints, write ownership, acceptance criteria, needed checks, and report budget. Workers read applicable project instructions themselves; do not paste the entire conversation or repository.
- Ask for a report of at most about 300 words: result, file/line evidence, edits, checks, assumptions, and unresolved issues. No raw logs or source dumps. Increase the budget only when the evidence requires it.
- Stop at the assigned boundary. No speculative refactoring, unrelated audits, redundant browsing, or repeated status polling. Continue independent work while a worker runs; use a bounded wait when needed.

## Review and validation

The primary checks worker evidence, reviews diffs, resolves conflicts, and runs the appropriate verification after integration. Worker success alone is insufficient. Use focused tests during iteration and the applicable suite once after final edits; repeat only for changes or unresolved failures. Agent-only/documentation-only work does not require Java compilation. State skipped checks, missing prerequisites, and live-validation gaps plainly.

Reference: [official OpenAI subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents). Capabilities and model availability depend on the current client and account; the exposed tool is authoritative for a specific session.
