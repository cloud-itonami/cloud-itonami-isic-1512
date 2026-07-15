(ns luggage.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [luggage.store :as store]
            [luggage.advisor :as advisor]
            [luggage.governor :as governor]
            [luggage.registry :as registry]))

(deftest spec-basis-hard-gate
  "Spec-basis is a HARD gate: never allow proposals without official citations."
  (let [st (store/mem-store)
        proposal {:op :actuation/coordinate-shipment
                  :subject "ship-001"
                  :effect :propose
                  :value {:evidence {:export-permit true}
                          :confidence 0.9}
                  :cites []}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with empty cites should hold")
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :no-spec-basis) (:hard-violations eval))))))

(deftest process-control-block
  "HARD BLOCK: Proposals mentioning cutting speed, stitching tension, or
  other equipment-control terms are immediately rejected. Those remain
  plant-engineer exclusive authority."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9
                          :detail "Please increase cutting speed to 500 units/hr"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Process-control proposal should hold")
      (is (some #(= (:rule %) :process-control-forbidden) (:hard-violations eval))
        "Should have process-control-forbidden violation"))))

(deftest process-control-block-leather-specific-terms
  "HARD BLOCK: leather-goods-specific process terms (skiving an edge,
  positioning a piece on a clicking press) are also forbidden
  equipment-control language, not just generic cutting/stitching terms."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9
                          :detail "Please check the skiving thickness and clicking press placement"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Leather-specific process-control proposal should hold")
      (is (some #(= (:rule %) :process-control-forbidden) (:hard-violations eval))
        "Should have process-control-forbidden violation"))))

(deftest safety-concern-escalation
  "Safety/quality concerns ALWAYS escalate to human. Never silently log a concern."
  (let [st (store/mem-store)
        proposal {:op :proposal/flag-safety-concern
                  :subject "batch-002"
                  :effect :propose
                  :cites ["REACH Regulation (EC) No 1907/2006, Annex XVII, Entry 47"]
                  :value {:evidence {:concern-documented true}
                          :confidence 0.92
                          :concern-type "labeling-error"
                          :detail "Material-content disclosure missing on shipment"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Safety/quality concern should hold")
      (is (some #(= (:rule %) :safety-concern-escalates) (:hard-violations eval))
        "Should have safety-concern-escalates violation"))))

(deftest shipment-requires-escalation
  "Shipment coordination is high-stakes actuation and requires human
  sign-off, even when all other checks are clean -- and, because ship-001
  points at a fully verified batch/plant, there should be NO hard
  violations at all (only the soft escalate), proving the shipment->batch
  indirection resolves correctly rather than false-triggering on every
  shipment regardless of its underlying batch's real verification state."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        shipment-proposal (advisor/shipment-proposal adv "ship-001")]
    (let [eval (governor/evaluate shipment-proposal st)]
      (is (empty? (:hard-violations eval))
        "A clean, fully-verified shipment should have zero hard violations")
      (is (not (:holds? eval)) "Should not hold on hard gates")
      (is (seq (:soft-violations eval)) "Should have soft violations for actuation")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate high-stakes actuation"))))

(deftest shipment-batch-indirection-blocks-unverified-batch
  "A shipment whose underlying batch is NOT verified must be blocked on
  :batch-not-verified via the shipment->batch indirection (ship-002 ->
  batch-002, which is seeded unverified)."
  (let [st (store/mem-store)
        proposal (registry/shipment-draft "ship-002"
                   ["CITES implementing regulations, 50 CFR Part 23"]
                   {:export-permit true :shipping-manifest true}
                   0.9
                   "Coordinate shipment of unverified batch")]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Shipment on unverified batch should hold")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block shipment whose underlying batch is unverified"))))

(deftest shipment-unknown-blocks
  "A shipment coordination proposal for a nonexistent shipment ID cannot
  resolve any underlying batch, so it must hold on :batch-not-verified."
  (let [st (store/mem-store)
        proposal (registry/shipment-draft "ship-unknown"
                   ["CITES implementing regulations, 50 CFR Part 23"]
                   {:export-permit true}
                   0.9
                   "Coordinate shipment of unknown shipment id")]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Unknown shipment should hold")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block on an unresolvable batch"))))

(deftest plant-not-verified-blocks
  "Production batch from unverified plant is blocked."
  (let [st (store/mem-store)
        ;; Create a batch with unverified plant
        _ (swap! (-> st :data) assoc-in [:production-batches "batch-unverified" :plant] "plant-unknown")
        proposal (registry/batch-log-draft "batch-unverified"
                   ["FTC Guides for Select Leather and Imitation Leather Products, 16 CFR Part 24"]
                   {:batch-verified true}
                   0.85
                   "Log batch from plant")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :plant-not-verified) (:hard-violations eval))
        "Should block unverified plant"))))

(deftest batch-not-verified-blocks
  "Production batch logging with unverified batch is blocked."
  (let [st (store/mem-store)
        proposal (registry/batch-log-draft "batch-002"
                   ["FTC Guides for Select Leather and Imitation Leather Products, 16 CFR Part 24"]
                   {:batch-verified true}
                   0.88
                   "Log unverified batch")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block unverified batch"))))

(deftest low-confidence-escalates
  "Low confidence proposals escalate to human, even if otherwise clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["FTC Guides for Select Leather and Imitation Leather Products, 16 CFR Part 24"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.45
                          :detail "Batch logged"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate low-confidence"))))

(deftest clean-proposal
  "A proposal with all evidence, valid spec-basis (where required), high
  confidence, and no high-stakes actuation or process-control is clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/schedule-maintenance
                  :subject "maint-001"
                  :effect :propose
                  :cites ["Canada Labour Code, R.S.C. 1985, c. L-2"]
                  :value {:evidence {:equipment-record true :maintenance-schedule-ok true}
                          :confidence 0.9
                          :detail "Maintenance scheduled"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations")
      (is (empty? (:soft-violations eval)) "Should have no soft violations"))))

(deftest op-not-allowlisted-blocks
  "HARD: An :op outside the closed `luggage.registry/allowed-ops` set is
  rejected outright, whatever it claims to be -- proving the actor cannot
  be induced to actuate outside its four allowed proposal kinds by simply
  naming a new op."
  (let [st (store/mem-store)
        proposal {:op :actuation/operate-cutting-line
                  :subject "plant-001"
                  :effect :propose
                  :cites ["n/a"]
                  :value {:evidence {} :confidence 0.95 :detail "direct cutting-line control"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Unallowlisted op should hold")
      (is (some #(= (:rule %) :op-not-allowlisted) (:hard-violations eval))
        "Should block an op outside the closed allowlist"))))

(deftest effect-not-propose-blocks
  "HARD: :effect must always be :propose. Even a proposal on an otherwise
  fully allowlisted, fully verified op is blocked if :effect claims a
  direct actuation."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        proposal (assoc (advisor/batch-log-proposal adv "batch-001") :effect :actuate)]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Non-:propose effect should hold")
      (is (some #(= (:rule %) :effect-not-propose) (:hard-violations eval))
        "Should block any :effect other than :propose"))))

(deftest allowed-ops-is-closed-four-op-set
  "The allowlist itself must be exactly the four documented ops -- no more,
  no less -- so a future edit that silently widens it fails this test."
  (is (= #{:proposal/log-production-batch
           :proposal/schedule-maintenance
           :proposal/flag-safety-concern
           :actuation/coordinate-shipment}
         registry/allowed-ops)))
