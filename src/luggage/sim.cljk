(ns luggage.sim
  "Demo driver -- `clojure -M:dev:run`. Drives the REAL compiled
  `langgraph-clj` `StateGraph` (`luggage.operation/build`) end-to-end
  through a phase-1 auto-commit (maintenance scheduling), a phase-1 hold
  gated by the rollout table (batch logging not yet in the phase-1 auto
  set), a phase-2 auto-commit of that SAME op once it joins the auto set,
  a HARD-blocked safety-concern flag (never even offered for human
  approval -- straight to :hold), a high-stakes shipment-coordination
  escalation approved by a human, the same escalation rejected, and a
  HARD-blocked unverified-batch shipment attempt, then prints the
  resulting audit ledger.

  FIX (this commit): this namespace previously called
  `luggage.governor/evaluate` DIRECTLY on hand-built proposals, bypassing
  any graph -- there was no `luggage.operation` namespace to drive at
  all. `build` now returns a genuine `CompiledGraph`, driven via
  `langgraph.graph/run*`."
  (:require [langgraph.graph :as g]
            [luggage.operation :as operation]
            [luggage.store :as store]))

(defn- scenario [title]
  (println "\n==========================================")
  (println (str "Scenario: " title))
  (println "=========================================="))

(defn- exec [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(defn- approve! [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid by]
  (g/run* actor {:approval {:status :rejected :by by}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through phase-gated auto-commit, a HARD-
  blocked safety-concern flag, an escalate-then-approve and an
  escalate-then-reject high-stakes shipment, and a HARD-blocked
  unverified-batch attempt; print each result and the final audit
  ledger."
  []
  (println "Luggage/Handbag/Saddlery-and-Harness Manufacturing Plant Operations Coordinator - Demo")

  (scenario "Phase 1: Auto-commit maintenance scheduling (low-risk)")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec actor "t1" {:op :proposal/schedule-maintenance :subject "maint-001"} 1)]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "Phase 1: Batch logging held -- NOT yet in phase-1's auto set")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec actor "t2" {:op :proposal/log-production-batch :subject "batch-001"} 1)]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "Phase 2: The SAME batch-logging proposal now auto-commits")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec actor "t3" {:op :proposal/log-production-batch :subject "batch-001"} 2)]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "HARD block: safety/quality concern -- ALWAYS holds, never offered for approval")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec actor "t4" {:op :proposal/flag-safety-concern :subject "batch-002"
                                 :concern-type "labeling-error"} 3)]
    (println "Status:" (:status result) "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "High-stakes: shipment coordination escalates, then a human approves")
  (let [s (store/mem-store)
        actor (operation/build s)
        held (exec actor "t5" {:op :actuation/coordinate-shipment :subject "ship-001"} 3)]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "Ledger (should be empty until approval):" (store/ledger s))
    (let [approved (approve! actor "t5" "compliance-officer-01")]
      (println "Decision:" (:decision (:state approved)))
      (println "Ledger:" (store/ledger s))))

  (scenario "High-stakes: shipment coordination escalates, then a human rejects")
  (let [s (store/mem-store)
        actor (operation/build s)
        held (exec actor "t6" {:op :actuation/coordinate-shipment :subject "ship-001"} 3)]
    (println "Status:" (:status held))
    (let [rejected (reject! actor "t6" "compliance-officer-01")]
      (println "Decision:" (:decision (:state rejected)))
      (println "Ledger:" (store/ledger s))))

  (scenario "HARD block: shipment coordination on an unverified batch (ship-002 -> batch-002)")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec actor "t7" {:op :actuation/coordinate-shipment :subject "ship-002"} 3)]
    (println "Decision:" (:decision (:state result)))
    (println "Violations:" (mapv :rule (:hard-violations (first (store/ledger s))))))

  (println "\n==========================================")
  (println "Demo completed successfully")
  (println "=========================================="))

(defn -main [& _args]
  (demo))

(comment
  (demo))
