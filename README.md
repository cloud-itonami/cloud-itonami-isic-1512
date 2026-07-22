# cloud-itonami-isic-1512

Open Business Blueprint for **ISIC Rev.5 1512**: manufacture of luggage,
handbags and the like, saddlery and harness -- cutting/stitching/assembly
lines that turn leather (or leather-substitute synthetic) material into
finished suitcases, bags, wallets, belts, saddles and harness goods.
Tanning and dressing of leather itself is a distinct upstream ISIC class
(1511) and is explicitly **out of scope** here -- this repository only
covers the downstream goods-manufacturing step.

## What the implemented actor is

**Luggage-Goods Operations Advisor ⊣ Luggage Governor** -- the
fleet-standard pattern: the advisor LLM drafts production-batch logging
(cutting/stitching/assembly batch, output-quality data), cutting/
stitching-line-equipment maintenance scheduling, safety/quality-concern
flags, and outbound finished-product shipment coordination; the
independent `:luggage-governor` (a keyword unique fleet-wide) gates every
action; physical-domain work (cutting, skiving, stitching, assembling,
finishing) is executed by plant engineers/robots under
`kotoba-lang/robotics` safety classes, never dispatched directly by the
LLM.

Operating states: `spec → design → produce → inspect → package → audit`.

> **Why an actor layer at all?** An LLM is great at drafting a batch-log
> summary, checking an evidence checklist, or noticing that a shipment's
> underlying batch is unverified -- but it has **no notion of which
> jurisdiction's leather-labeling/exotic-skin-sourcing requirements are
> official, no license to actually log a real production batch or
> coordinate a real shipment, and no way to know on its own whether the
> plant/batch it is acting on has actually been independently verified**.
> Letting it commit a batch record or coordinate a shipment directly
> invites fabricated spec-basis citations, an unverified plant/batch being
> silently recorded, and a genuine equipment-safety or labeling concern
> being quietly logged instead of escalated. This project seals the
> advisor into a single node and wraps it with an independent **Luggage
> Governor**, a human approval workflow, and an immutable audit ledger.

## Scope: plant OPERATIONS COORDINATION, not equipment control

This actor coordinates **logistics and compliance paperwork** around
luggage/handbag/saddlery-and-harness manufacturing plant operations. It
does **not**:

- Operate cutting equipment or stitching machines
- Make design decisions about patterns, cuts, or hide-matching
- Control production-line parameters (speed, tension, needle control,
  etc.)
- Approve raw-material or tanning quality (that is the upstream tannery's
  or material supplier's responsibility, not this plant's)
- Tan or dress leather (a distinct upstream ISIC class, 1511)

Those remain the exclusive authority of licensed plant production
engineers. The governor's `process-control-forbidden` check enforces this
with a keyword scan covering both generic cutting/stitching-line terms and
leather-goods-specific process terms (skiving an edge, positioning a piece
on a clicking press, riveting, punching a stitch hole).

### Closed proposal allowlist

This actor may **only ever propose** one of exactly four operations, all
`:effect :propose` -- the allowlist is a single canonical `def`
(`luggage.registry/allowed-ops`) that both the registry drafters and the
governor's `op-not-allowlisted-violations` check require, so the two can
never drift out of sync:

| Op | Purpose |
|---|---|
| `:proposal/log-production-batch` | Log a completed cutting/stitching/assembly batch and its output-quality data |
| `:proposal/schedule-maintenance` | Propose cutting/stitching-line-equipment maintenance scheduling |
| `:proposal/flag-safety-concern` | Surface an equipment-safety or output-quality-defect concern -- **ALWAYS escalates** |
| `:actuation/coordinate-shipment` | Coordinate outbound finished-product shipment |

An `:op` outside this set, or any proposal whose `:effect` is not
`:propose`, is a **hard, permanent** governor block -- not merely a
missing-feature no-op. Neither invariant can be worked around by naming a
new op or claiming a different effect.

## The core contract

```
plant/batch record + jurisdiction facts (luggage.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌────────────────────────┐
   │ Luggage-Goods│ ─────────────▶ │ Luggage Governor:         │  (independent system)
   │ Operations   │  + citations    │ spec-basis · effect must │
   │ Advisor      │                 │ be :propose · op must be │
   │ (sealed)     │                 │ allowlisted · plant/batch │
   └──────────────┘         commit ◀────┼───────────▶ hold │ verified · no equipment
                                 │             │           │ control · safety/quality
                           record + ledger  escalate ─▶ human   concern (unconditional)
                                             (ALWAYS for
                                              :flag-safety-concern /
                                              high-stakes actuation)
```

**The advisor never logs a batch or coordinates a shipment the Luggage
Governor would reject, and never does so without a human sign-off.** Hard
violations (fabricated spec-basis; a non-`:propose` effect; an
unallowlisted op; an unverified plant/batch; direct equipment-control
language; a safety/quality concern) force **hold** and *cannot* be
approved past; a clean shipment proposal still always routes to a human
(high-stakes actuation).

## Run

```bash
clojure -M:run          # walk seven scenarios (phase-gated auto-commit/hold,
                        # HARD-blocked safety concern, escalate-then-approve and
                        # escalate-then-reject shipment, unverified-batch block)
                        # through the REAL compiled langgraph-clj StateGraph
clojure -M:test         # advisor · governor contract · phase rollout · store/ledger · facts · operation (real StateGraph, end-to-end)
clojure -M:lint         # clj-kondo (errors fail; CI mirrors this)
```

## Jurisdiction coverage (honest)

`luggage.facts/coverage` reports how many requested jurisdictions actually
have an official spec-basis in `luggage.facts/catalog` -- currently 3
seeded (USA, Italy, Canada) out of ~194 jurisdictions worldwide. This is a
starting catalog to prove the governor contract end-to-end, not a claim of
global coverage. Every citation is a real, verifiable official source
(never fabricated):

| Jurisdiction | Requirement | Spec-basis |
|---|---|---|
| USA | Leather-goods labeling | FTC Guides for Select Leather and Imitation Leather Products, 16 CFR Part 24 |
| USA | Exotic-skin sourcing (CITES) | Endangered Species Act, 16 U.S.C. § 1538; CITES implementing regulations, 50 CFR Part 23 |
| USA | Labor standards | FLSA 29 CFR § 516, OSHA 1910 Subpart A |
| Italy | "Made in Italy" leather-goods labeling | Legge 8 aprile 2010, n. 55 (prodotti tessili, della pelletteria e calzaturieri) |
| Italy | Chromium VI restriction | REACH Regulation (EC) No 1907/2006, Annex XVII, Entry 47 |
| Italy | Species sourcing (wildlife trade) | Council Regulation (EC) No 338/97 |
| Italy | Workplace health & safety | Decreto Legislativo 9 aprile 2008, n. 81 |
| Canada | Consumer-product labelling | Consumer Packaging and Labelling Act, R.S.C. 1985, c. C-38 |
| Canada | Labour standards | Canada Labour Code, R.S.C. 1985, c. L-2 |

Adding a jurisdiction is additive: one map entry in `luggage.facts/catalog`,
citing a real official source -- never fabricate a jurisdiction's
requirements to make coverage look bigger.

## Layout

| File | Role |
|---|---|
| `src/luggage/store.cljc` | In-memory store: plants, production batches, shipments, maintenance log + verification guards + the shipment→batch indirection resolver + the append-only audit ledger (`ledger`/`append-ledger!`) |
| `src/luggage/facts.cljc` | Per-jurisdiction leather-labeling/exotic-skin-sourcing/labor-standards catalog with official spec-basis citations, honest coverage reporting |
| `src/luggage/advisor.cljc` | Luggage-Goods Operations Advisor -- a real `Advisor` protocol + `MockAdvisor`/`mock-advisor`; batch-log/maintenance/safety-concern/shipment proposal builders |
| `src/luggage/registry.cljc` | The closed `allowed-ops` allowlist + hard-invariant helpers + proposal draft constructors |
| `src/luggage/governor.cljc` | **Luggage Governor** -- 6 HARD checks (spec-basis · effect-not-propose · op-not-allowlisted · plant-not-verified · batch-not-verified · process-control-forbidden) + 1 unconditional escalation (safety-concern) + 1 soft (confidence/actuation gate) |
| `src/luggage/phase.cljc` | The real 0->3 rollout gate (`may-auto-commit?`) genuinely consulted by `operation.cljc`'s `:decide` node |
| `src/luggage/operation.cljc` | **The real compiled `langgraph-clj` StateGraph** (`operation/build`): `intake -> advise -> govern -> decide -+-> commit / request-approval -> commit / hold`, `interrupt-before #{:request-approval}` for genuine human-in-the-loop approval |
| `src/luggage/sim.cljc` | demo driver -- drives the real compiled StateGraph end-to-end |
| `test/luggage/*_test.clj` | advisor · governor contract · phase rollout · store/ledger · facts coverage · operation (real StateGraph, end-to-end) |

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`1512`). This vertical's plant/batch records are practice-specific rather
than a shared cross-operator data contract, so `luggage.*` runs on the
generic robotics/identity/forms/dmn/bpmn/audit-ledger/cae stack only -- no
bespoke domain capability lib to reference at all.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot or
skilled plant operator performs the physical domain work**. Here a
plant-floor operator (or, where deployed, a robotics-assisted cutting/
stitching station under `kotoba-lang/robotics` safety classes) performs
the actual cutting, skiving and stitching under this actor, gated by the
independent **Luggage Governor**. The governor never operates equipment
itself; safety-critical actions always require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Luggage Governor, batch/maintenance/shipment draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariants, audit requirements |

## Maturity

`:implemented` -- and, as of this fix, genuinely so. Before this fix
this repository had NO `operation.cljc`/StateGraph at all
(`luggage.sim` hand-called `governor/evaluate` directly, bypassing any
graph entirely), `luggage.phase` was a plain data map whose docstring
FALSELY claimed to be "Built on langgraph-clj StateGraph shape",
`luggage.advisor` had plain functions only (no `Advisor` protocol, no
`MockAdvisor` -- this fleet's standard pattern), and NO
`append-ledger!`/audit mechanism existed anywhere in `luggage.store` --
not dead code, the concept was entirely absent from `src/` despite
`blueprint.edn`'s `:required-technologies [... :audit-ledger]` and this
README's own "immutable audit ledger" line implying one should exist.
`blueprint.edn` nonetheless claimed `:itonami.blueprint/maturity
:implemented`, which was false given all of the above.

Now: `luggage.operation/build` is a genuinely compiled `langgraph.graph`
StateGraph (`intake -> advise -> govern -> decide -+-> commit /
request-approval -> commit / hold`) with `interrupt-before
#{:request-approval}` + an in-memory checkpointer for real
human-in-the-loop resume; `luggage.advisor/Advisor` is a real protocol
with a `MockAdvisor` record; `luggage.phase/may-auto-commit?` is
genuinely consulted by the `:decide` node; and
`luggage.store/ledger`/`append-ledger!` is a real append-only audit
ledger, genuinely wired into both the `:commit` and `:hold` graph nodes.
`luggage.governor`'s hard/soft checks and `luggage.registry`'s
allowlist/drafts are reused UNCHANGED -- this fix only wires the
existing plant-operations compliance policy into a real compiled graph
and a real ledger, it does not redesign it. Proven end-to-end by
`test/luggage/operation_test.clj` (ledger stays empty until a real
commit, phase-gated hold vs. auto-commit for the SAME proposal at
different phases, HARD holds for unverified batch/safety-concern/
unallowlisted-op that never reach human approval even at phase-3,
escalate-then-approve and escalate-then-reject for high-stakes shipment
coordination) through the REAL compiled graph, plus a `clojure -M:run`
demo runner producing real ledger entries for every scenario. All
pre-existing governor-contract/facts tests are unchanged and still
passing. CI (`.github/workflows/ci.yml`) was missing entirely and is
added in this fix.

## License

Code and implementation templates are AGPL-3.0-or-later, forkable by any
qualified operator, so local leather-goods makers never surrender
production and provenance data to a closed SaaS. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
