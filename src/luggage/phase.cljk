(ns luggage.phase
  "Phase 0->3 rollout control for the Luggage/Handbag/Saddlery-and-Harness
  Manufacturing Plant Operations actor.

  FIX (this commit): this namespace previously held an inert data map
  (`phase-table`, a made-up :nodes/:edges/:predicate description of a
  StateGraph) with a docstring that FALSELY claimed 'Built on langgraph-clj
  StateGraph for portable execution' -- no `langgraph.graph` function ever
  consumed `phase-table`, no `:decide` node existed anywhere, and the
  actual actor flow was `luggage.sim` hand-calling `governor/evaluate`
  directly, never even referencing this namespace. That claim was
  fabricated. This is now the real thing: `may-auto-commit?` is genuinely
  called from `luggage.operation`'s `:decide` node (the real compiled
  `langgraph.graph` StateGraph) to decide whether a Governor-clean
  proposal may auto-commit at the actor's current rollout phase, or must
  still be held for a human.

  Phase 0: intake-only -- nothing auto-commits, even a fully clean proposal.
  Phase 1: low-risk auto-commit -- maintenance scheduling only.
  Phase 2: + medium-risk auto-commit -- production-batch logging joins.
  Phase 3: full autonomy for what CAN ever be clean -- same auto set as
    phase 2 (there is no higher-risk op left to add: shipment coordination
    is `luggage.governor/high-stakes` and safety-concern flagging is a
    HARD, permanent Governor block, so BOTH already require human sign-off
    at every phase, independent of this table -- see the explicit
    `never-auto-commit-ops` override below, defense-in-depth matching the
    Governor's own un-overridable rules rather than relying on the
    Governor alone).")

;; ----------------------------- phase rollout table -----------------------------

(defn phase-config
  "The phase rollout table. Each phase specifies which ops may auto-commit
  once the Governor has already found the proposal fully clean (no hard
  or soft violations). An op outside :auto for the given phase is held
  for human review even when clean."
  [phase-num]
  (case phase-num
    0 {:auto #{}}
    1 {:auto #{:proposal/schedule-maintenance}}
    2 {:auto #{:proposal/schedule-maintenance
               :proposal/log-production-batch}}
    3 {:auto #{:proposal/schedule-maintenance
               :proposal/log-production-batch}}
    ;; Unknown/unrecognized phase number: conservative default (all held).
    {:auto #{}}))

(def never-auto-commit-ops
  "Ops that must NEVER auto-commit at any phase, regardless of the phase
  table above. Both are already independently blocked by
  `luggage.governor/evaluate` (safety-concern-escalates is a HARD
  violation; high-stakes actuation is a soft :escalate violation), so a
  Governor-clean evaluation should never even be possible for these ops
  in practice -- this is intentional defense-in-depth, the same two-layer
  discipline this fleet's other actors use (governor + phase both
  independently agree an op is never autonomous)."
  #{:proposal/flag-safety-concern :actuation/coordinate-shipment})

(defn may-auto-commit?
  "True if `op` may auto-commit at `phase-num`, AFTER the Governor has
  already found the proposal clean. Callers (see `luggage.operation`'s
  `:decide` node) must independently check Governor cleanliness first --
  this function only answers the phase-rollout question, not the
  compliance question."
  [op phase-num]
  (if (contains? never-auto-commit-ops op)
    false
    (contains? (:auto (phase-config phase-num)) op)))
