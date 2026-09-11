(ns luggage.operation
  "OperationActor -- one luggage/handbag/saddlery-and-harness plant
  operations request = one supervised actor run, expressed as a REAL
  compiled `langgraph-clj` `StateGraph` (`langgraph.graph/state-graph` +
  `compile-graph`). The advisor (`luggage.advisor/Advisor`) is sealed
  into a single node (`:advise`); its proposal is ALWAYS routed through
  the independent `luggage.governor` and the `luggage.phase` rollout gate
  before anything commits to the SSoT.

  FIX (this commit): this namespace DID NOT EXIST AT ALL before this fix
  -- there was no `operation.cljc`, no StateGraph, nothing. `luggage.sim`
  hand-called `governor/evaluate` directly on proposals it built itself,
  bypassing any graph entirely, while `luggage.phase`'s own docstring
  FALSELY claimed to be 'Built on langgraph-clj StateGraph shape' over an
  inert, unconsumed data map. `blueprint.edn` nonetheless claimed
  `:itonami.blueprint/maturity :implemented`, which was false given all
  of the above -- worse than the usual deferred-stub pattern found
  elsewhere in this fleet, because here the StateGraph concept was
  entirely absent from `src/`, not merely stubbed out.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`luggage.store/mem-store`, or any equivalent map
                     shape with the same accessor functions)
    - the Advisor  (mock today; `luggage.advisor/Advisor` is already the
                     injection point)
    - the Phase    (0->3 rollout; passed per-request via `:phase-num`,
                     not frozen at `build` time)

  `luggage.governor/evaluate` and `luggage.phase/may-auto-commit?` are
  reused UNCHANGED here -- this fix only wires the existing plant-
  operations compliance policy into a real compiled graph and a real
  ledger, it does not redesign it.

  One graph run = one plant-operations coordination request (batch
  logging / maintenance scheduling / safety-concern flag / shipment
  coordination). No unbounded inner loop -- each run is auditable and
  checkpointed. Every commit/hold/approval-rejected decision fact lands
  in `luggage.store`'s append-only ledger (`store/append-ledger!`),
  genuinely wired into both the `:commit` and `:hold` terminal nodes.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` GENUINELY pauses (checkpointed)
  the actor at the `:request-approval` node and hands the decision to a
  human plant operator/compliance officer. The approver resumes the SAME
  compiled graph/thread with `{:approval {:status :approved :by ...}}`
  (or `:rejected`). Note that `:proposal/flag-safety-concern` is a HARD,
  permanent Governor violation (`luggage.governor/safety-concern-
  escalation-violations`) -- it NEVER reaches `:request-approval` at all,
  it routes straight to `:hold` (a human still reviews it via the
  ledger's hold fact, but the automated approve/reject resume path is not
  offered for it, matching the Governor's own 'HARD violations cannot be
  overridden' contract). `:actuation/coordinate-shipment` is high-stakes
  (`luggage.governor/high-stakes`), always producing a soft :escalate
  violation, so a clean shipment proposal always routes through
  `:request-approval` regardless of phase."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [luggage.advisor :as advisor]
            [luggage.governor :as governor]
            [luggage.phase :as phase]
            [luggage.store :as store]))

;; ============================================================================
;; Audit-fact builders
;; ============================================================================

(defn- hold-fact
  "The audit fact written when a proposal is held -- either a permanent
  HARD governor block (`:holds?` true) or a clean-or-soft-violation
  proposal that simply isn't in the current phase's auto-commit set.
  `reason` distinguishes the cases in the ledger without changing the
  terminal node they route to."
  [request proposal evaluation reason]
  {:t               :governor-hold
   :op              (:op request)
   :subject         (:subject proposal)
   :disposition     :hold
   :hard-violations (:hard-violations evaluation)
   :soft-violations (:soft-violations evaluation)
   :reason          reason})

(defn- commit-fact
  "The audit fact written when a proposal commits. `:proposal` carries
  the full advisor proposal (batch-log/maintenance/safety-concern/
  shipment data + its citations) -- this actor has no separate stateful
  commit-record! entity beyond the plant/batch/shipment directory itself,
  so the ledger fact is the durable record of what happened."
  [request proposal approval]
  (cond-> {:t           :committed
           :op          (:op request)
           :subject     (:subject proposal)
           :disposition :commit
           :basis       (:cites proposal)
           :detail      (get-in proposal [:value :detail])
           :proposal    proposal}
    approval (assoc :approved-by (:by approval))))

;; ============================================================================
;; Compiled StateGraph
;; ============================================================================

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `luggage.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request .. :phase-num ..}`, where
  `:request` is `{:op .. :subject .. [:concern-type ..]}` (`:op` one of
  `luggage.registry/allowed-ops`) and `:phase-num` is the actor's current
  0->3 rollout phase (per-request, not frozen at `build` time)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request    {:default nil}
         :phase-num  {:default 0}
         :proposal   {:default nil}
         :evaluation {:default nil}
         :decision   {:default nil}
         :approval   {:default nil}
         :audit      {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          {:proposal (advisor/advise advisor request store)}))

      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:evaluation (governor/evaluate proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request proposal evaluation phase-num]}]
          (let [holds?       (boolean (:holds? evaluation))
                soft         (:soft-violations evaluation)
                clean?       (boolean (:clean? evaluation))
                auto-commit? (and clean? (phase/may-auto-commit? (:op proposal) phase-num))]
            (cond
              ;; HARD governor violations are a permanent block -- NEVER
              ;; routed through human approval, straight to :hold. This
              ;; is where :proposal/flag-safety-concern always lands
              ;; (safety-concern-escalation-violations is a hard check).
              holds?
              {:decision :hold
               :audit [(hold-fact request proposal evaluation :governor-violation)]}

              ;; Soft violations (low confidence OR high-stakes actuation
              ;; -- see luggage.governor/confidence-gate-violations) are
              ;; human-approvable, never auto-committed.
              (seq soft)
              {:decision :escalate
               :audit [{:t          :approval-requested
                        :op         (:op request)
                        :subject    (:subject proposal)
                        :reason     (if (some #(= :escalate (:rule %)) soft)
                                      :escalate :soft-violation)
                        :phase      phase-num
                        :confidence (get-in proposal [:value :confidence])}]}

              auto-commit?
              {:decision :commit}

              :else
              {:decision :hold
               :audit [(hold-fact request proposal evaluation :not-in-phase-auto-set)]}))))

      (g/add-node :request-approval
        (fn [{:keys [request proposal approval evaluation]}]
          (if (= :approved (:status approval))
            {:decision :commit
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject proposal) :by (:by approval)}]}
            {:decision :hold
             :audit [(assoc (hold-fact request proposal evaluation :approver-rejected)
                            :t :approval-rejected)]})))

      (g/add-node :commit
        (fn [{:keys [request proposal approval]}]
          (let [f (commit-fact request proposal approval)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [decision]}]
          (case decision
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [decision]}]
          (if (= :commit decision) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
