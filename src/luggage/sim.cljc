(ns luggage.sim
  "Simulation harness for Luggage/Handbag/Saddlery-and-Harness Manufacturing
  Plant Operations Coordinator actor.
  Run with: clojure -M:dev:run"
  (:require [luggage.advisor :as advisor]
            [luggage.governor :as governor]
            [luggage.store :as store]))

(defn -main
  "Drive a simple luggage/handbag/saddlery-and-harness manufacturing
  workflow through the governor."
  [& _args]
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)

        ;; Scenario 1: Production batch logging (verified batch)
        batch-proposal (advisor/batch-log-proposal adv "batch-001")
        batch-eval (governor/evaluate batch-proposal st)

        ;; Scenario 2: Safety/quality concern flagging (always escalates)
        concern-proposal (advisor/safety-concern-proposal adv "batch-002" "labeling-error")
        concern-eval (governor/evaluate concern-proposal st)

        ;; Scenario 3: Shipment coordination (high-stakes actuation, batch verified)
        shipment-proposal (advisor/shipment-proposal adv "ship-001")
        shipment-eval (governor/evaluate shipment-proposal st)

        ;; Scenario 4: Maintenance scheduling
        maintenance-proposal (advisor/maintenance-proposal adv "maint-001")
        maintenance-eval (governor/evaluate maintenance-proposal st)

        ;; Scenario 5: Unallowlisted op (direct cutting-line control attempt) -- HARD blocked
        rogue-proposal {:op :actuation/operate-cutting-line
                        :subject "plant-001"
                        :effect :propose
                        :cites ["n/a"]
                        :value {:evidence {} :confidence 0.95 :detail "operate the cutting line directly"}}
        rogue-eval (governor/evaluate rogue-proposal st)

        ;; Scenario 6: Non-:propose effect -- HARD blocked regardless of op
        actuate-proposal (assoc (advisor/batch-log-proposal adv "batch-001") :effect :actuate)
        actuate-eval (governor/evaluate actuate-proposal st)]

    (println "=== LUGGAGE/HANDBAG/SADDLERY-AND-HARNESS MANUFACTURING PLANT OPERATIONS COORDINATOR SIMULATION ===\n")

    (println "--- Scenario 1: Production Batch Logging (Verified Batch) ---")
    (println "Proposal:" batch-proposal)
    (println "Evaluation:" batch-eval)
    (println "Result:" (if (:clean? batch-eval) "APPROVED" "ESCALATE TO HUMAN"))
    (println)

    (println "--- Scenario 2: Safety/Quality Concern Flagging (Always Escalates) ---")
    (println "Proposal:" concern-proposal)
    (println "Evaluation:" concern-eval)
    (println "Hard Violations:" (:hard-violations concern-eval))
    (println "Result:" (if (:holds? concern-eval) "ESCALATE TO HUMAN" "ERROR"))
    (println)

    (println "--- Scenario 3: Shipment Coordination (High-Stakes Actuation) ---")
    (println "Proposal:" shipment-proposal)
    (println "Evaluation:" shipment-eval)
    (println "Hard Violations:" (:hard-violations shipment-eval))
    (println "Soft Violations:" (:soft-violations shipment-eval))
    (println "Result:" (if (:holds? shipment-eval) "HOLD - Hard violations" "ESCALATE - High-stakes actuation"))
    (println)

    (println "--- Scenario 4: Maintenance Scheduling ---")
    (println "Proposal:" maintenance-proposal)
    (println "Evaluation:" maintenance-eval)
    (println "Result:" (if (:clean? maintenance-eval) "APPROVED" "ESCALATE TO HUMAN"))
    (println)

    (println "--- Scenario 5: Unallowlisted Op (Direct Cutting-Line Control Attempt) ---")
    (println "Proposal:" rogue-proposal)
    (println "Evaluation:" rogue-eval)
    (println "Result:" (if (:holds? rogue-eval) "HOLD - op-not-allowlisted" "ERROR"))
    (println)

    (println "--- Scenario 6: Non-:propose Effect (HARD blocked regardless of op) ---")
    (println "Proposal:" actuate-proposal)
    (println "Evaluation:" actuate-eval)
    (println "Result:" (if (:holds? actuate-eval) "HOLD - effect-not-propose" "ERROR"))
    (println)))
