# cloud-itonami-isco-3143

Open Occupation Blueprint for **ISCO-08 3143**: Forestry Technicians.

This repository designs a forkable OSS forestry technical support system: a technician proposes field assessments, test results, risk flags, and site visits for a registered forest stand, under a governor-gated actor that ensures all technical actions remain proposals (never direct actuation) and escalates critical risks.

## Technical Premise

All cloud-itonami verticals are designed on the premise that a **human expert or robot performs
the domain work**. Here a forestry technician proposes site assessments, test protocols, risk flags, and scheduled visits for a registered forest stand under management, under an actor that proposes technical actions and an independent **Technical Governor** that gates them. The governor never executes actions directly; critical actions (such as pest/disease/wildfire risk escalations or scheduled site visits) require human sign-off from the technician/forester.

## Core Contract

```text
technician registration + forest registration + technical history
        |
        v
Forestry Technician -> Technical Governor -> log assessment, run test, flag risk, or human sign-off
        |
        v
technician decision + technical records + audit ledger
```

No automated recommendation can suppress a record or disclose sensitive data without governor approval and
audit evidence.

## Capability Layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3143`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

## Reference Implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real [`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Technician and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :propose -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/forestry_technician/store.kotoba` — `Store` protocol + `MemStore`:
  registered technicians, forest stands, technical records, an append-only audit ledger.
- `src/forestry_technician/technician.kotoba` — `Technician` protocol;
  `mock-technician` (deterministic, default) proposes a technical action from a
  request; `llm-technician` wraps a `langchain.model/ChatModel` — either
  way the technician only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `:confidence 0.0` (forces escalation, never fabricated confidence).
- `src/forestry_technician/governor.kotoba` — `TechnicianGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered technician, unregistered forest, a proposal whose `:effect`
  isn't `:propose`) always route to `:hold`. Escalation invariants
  (`:flag-pest-disease-risk`, `:schedule-site-visit`, or low technician
  confidence) always route to `:request-approval` — an
  `interrupt-before` node that the graph checkpoints and only resumes
  on explicit human approval (`actor/approve!`), matching the premise
  that pest/disease/wildfire risks and scheduled site visits
  always require human sign-off.
- `src/forestry_technician/actor.kotoba` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

## Proposal Operations

- `:log-site-assessment` — forest stand/site assessment data logging. `:propose` only.
- `:run-test-protocol` — execute a registered soil/tree-health test protocol. `:propose` only.
- `:flag-pest-disease-risk` — surface a pest/disease/wildfire risk finding. ALWAYS escalates. Human sign-off required.
- `:schedule-site-visit` — site-visit scheduling proposal. ALWAYS escalates. Human sign-off required.

## Testing

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
