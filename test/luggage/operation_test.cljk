(ns luggage.operation-test
  "Integration tests for `luggage.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / phase-gated-hold /
  phase-gated-auto-commit / HARD-hold / escalate-approve /
  escalate-reject routes.

  This replaces the previous state of the codebase, where
  `luggage.operation` DID NOT EXIST AT ALL -- `luggage.sim` called
  `luggage.governor/evaluate` directly on hand-built proposals, and
  `luggage.phase`'s own docstring FALSELY claimed to be 'Built on
  langgraph-clj StateGraph shape' over an inert, unconsumed data map,
  despite `blueprint.edn` claiming `:itonami.blueprint/maturity
  :implemented`. These tests are FALSIFIABLE on real StateGraph
  behavior, not hardcoded pass strings: the ledger stays empty until a
  real commit, escalated proposals hold-until-approved via a genuine
  checkpointed `interrupt-before`, and a governor rejection (HARD
  violation or a human's :rejected decision) blocks commit entirely."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [luggage.operation :as op]
            [luggage.store :as store]))

(defn- exec [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(deftest ledger-starts-empty
  (testing "a freshly created store's audit ledger is empty until a real
            commit or hold lands -- no proposal, no evaluation, no graph
            run has happened yet"
    (let [s (store/mem-store)]
      (is (empty? (store/ledger s))))))

(deftest commit-path-maintenance-auto-commits-at-phase-1
  (testing ":proposal/schedule-maintenance is clean and IS in phase-1's
            auto set -- it genuinely commits through the real compiled
            graph (no interrupt), and appends exactly one :committed fact
            to the audit ledger"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-commit-maint" {:op :proposal/schedule-maintenance
                                                :subject "maint-001"} 1)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :proposal/schedule-maintenance (:op (first ledger))))
        (is (= "maint-001" (:subject (first ledger))))))))

(deftest hold-path-batch-logging-not-in-phase-1-auto-set
  (testing ":proposal/log-production-batch on a fully verified batch is
            Governor-clean, but phase-1's auto set does NOT include it
            yet -- the real graph routes to :hold (NOT :request-approval,
            no interrupt) and records a :not-in-phase-auto-set reason,
            distinct from a governor-violation hold"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-hold-phase1" {:op :proposal/log-production-batch
                                               :subject "batch-001"} 1)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :not-in-phase-auto-set (:reason (first ledger))))
        (is (empty? (:hard-violations (first ledger)))
            "this is a phase-gate hold, not a governor-violation hold")))))

(deftest commit-path-batch-logging-auto-commits-at-phase-2
  (testing "the SAME batch-logging proposal, once phase advances to 2,
            genuinely auto-commits -- proves phase.cljc is actually
            consulted by the real graph, not decorative"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-commit-phase2" {:op :proposal/log-production-batch
                                                 :subject "batch-001"} 2)
          state (:state result)]
      (is (= :commit (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))))))

(deftest hard-hold-path-unverified-batch
  (testing "logging an unverified batch (batch-002) is a HARD, permanent
            governor violation -- the real graph routes straight to
            :hold EVEN AT PHASE-3 (full autonomy), never through
            :request-approval, and the ledger never gets a :committed
            fact"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-hold-unverified" {:op :proposal/log-production-batch
                                                   :subject "batch-002"} 3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :governor-violation (:reason (first ledger))))
        (is (some #(= :batch-not-verified (:rule %)) (:hard-violations (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "governor rejection blocks commit -- no :committed fact ever lands")))))

(deftest hard-hold-path-safety-concern-never-interrupts
  (testing ":proposal/flag-safety-concern is a HARD, permanent governor
            violation (safety-concern-escalation-violations) -- it NEVER
            reaches :request-approval, even at phase-3. The graph
            finishes :done (not :interrupted) straight to :hold"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-hold-safety" {:op :proposal/flag-safety-concern
                                               :subject "batch-002"
                                               :concern-type "labeling-error"} 3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #(= :safety-concern-escalates (:rule %)) (:hard-violations (first ledger))))))))

(deftest escalate-then-approve-commits-shipment
  (testing ":actuation/coordinate-shipment on a verified batch (ship-001
            -> batch-001) has zero hard violations but IS high-stakes --
            the real graph GENUINELY interrupts (checkpointed) at
            :request-approval; the ledger stays EMPTY until a human
            resumes the SAME compiled graph and commits via the graph's
            own :request-approval -> :commit edge"
    (let [s (store/mem-store)
          actor (op/build s)
          held (exec actor "t-escalate-approve" {:op :actuation/coordinate-shipment
                                                  :subject "ship-001"} 3)]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s))
          "hold-until-approved: not yet committed -- ledger stays empty
          until a human signs off")
      (let [approved (g/run* actor {:approval {:status :approved :by "compliance-officer-01"}}
                             {:thread-id "t-escalate-approve" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:decision approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= "compliance-officer-01" (:approved-by (first ledger)))))))))

(deftest escalate-then-reject-holds-shipment
  (testing "a human rejecting an escalated shipment-coordination proposal
            routes to :hold via the :request-approval node's own
            decision -- governor rejection blocks commit"
    (let [s (store/mem-store)
          actor (op/build s)
          _held (exec actor "t-escalate-reject" {:op :actuation/coordinate-shipment
                                                  :subject "ship-001"} 3)
          rejected (g/run* actor {:approval {:status :rejected :by "compliance-officer-01"}}
                           {:thread-id "t-escalate-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:decision rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "a rejected approval never reaches :commit")))))

(deftest hard-hold-path-shipment-on-unverified-batch
  (testing "shipment coordination whose underlying batch is unverified
            (ship-002 -> batch-002) is a HARD violation via the
            shipment->batch indirection -- straight to :hold, never
            offered for human approval, even though shipment coordination
            is otherwise high-stakes/escalatable"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-hold-shipment-unverified" {:op :actuation/coordinate-shipment
                                                            :subject "ship-002"} 3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (is (some #(= :batch-not-verified (:rule %))
                (:hard-violations (first (store/ledger s))))))))

(deftest hard-hold-path-unallowlisted-op
  (testing "an :op outside the closed `luggage.registry/allowed-ops` set
            falls through the advisor's fallback (zero-confidence stub)
            and is independently rejected by the Governor's own closed
            allowlist -- HARD hold, proving the actor cannot be induced
            to actuate outside its four allowed proposal kinds"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-hold-unallowlisted" {:op :actuation/operate-cutting-line
                                                      :subject "plant-001"} 3)
          state (:state result)]
      (is (= :hold (:decision state)))
      (is (some #(= :op-not-allowlisted (:rule %))
                (:hard-violations (first (store/ledger s))))))))

(deftest never-auto-commit-safety-concern-at-every-phase
  (testing "at every phase (including phase-3, full autonomy for what CAN
            auto-commit), :proposal/flag-safety-concern never
            auto-commits and never even interrupts -- always a HARD,
            immediate :hold, proven against the real graph"
    (doseq [phase-num [0 1 2 3]]
      (let [s (store/mem-store)
            actor (op/build s)
            result (exec actor (str "t-safety-" phase-num)
                        {:op :proposal/flag-safety-concern :subject "batch-002"
                         :concern-type "frost-damage"} phase-num)]
        (is (= :done (:status result)) (str "phase " phase-num))
        (is (= :hold (:decision (:state result))) (str "phase " phase-num))))))

(deftest shipment-always-escalates-at-every-phase
  (testing "a clean, high-stakes shipment-coordination proposal always
            interrupts for human approval at every phase -- the phase
            rollout gate never lets a high-stakes actuation bypass a
            human"
    (doseq [phase-num [0 1 2 3]]
      (let [s (store/mem-store)
            actor (op/build s)
            held (exec actor (str "t-shipment-" phase-num)
                       {:op :actuation/coordinate-shipment :subject "ship-001"} phase-num)]
        (is (= :interrupted (:status held)) (str "phase " phase-num))
        (is (= [:request-approval] (:frontier held)) (str "phase " phase-num))))))

(deftest audit-trail-records-decision-fact-on-commit
  (testing "the audit trail on a real commit includes the :committed fact
            carrying the full advisor proposal"
    (let [s (store/mem-store)
          actor (op/build s)
          result (exec actor "t-audit-commit" {:op :proposal/schedule-maintenance
                                                :subject "maint-001"} 1)
          audit (:audit (:state result))]
      (is (= 1 (count audit)))
      (is (= :committed (:t (first audit))))
      (is (map? (:proposal (first audit)))))))
