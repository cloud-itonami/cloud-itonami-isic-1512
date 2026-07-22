(ns luggage.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [luggage.store :as store]))

(deftest mem-store-initialization
  "In-memory store should initialize with reference data."
  (let [st (store/mem-store)]
    (is (map? st))
    (is (contains? st :data))))

(deftest plant-accessors
  "Store should provide plant lookups."
  (let [st (store/mem-store)
        plant (store/plant st "plant-001")]
    (is (map? plant))
    (is (= (:name plant) "Community Leather Goods Workshop A"))
    (is (= (:location plant) "Italy"))))

(deftest production-batch-accessors
  "Store should provide batch lookups."
  (let [st (store/mem-store)
        batch (store/production-batch st "batch-001")]
    (is (map? batch))
    (is (= (:style batch) "full-grain-tote-bag-M"))
    (is (= (:quantity batch) 60))
    (is (= (:plant batch) "plant-001"))))

(deftest shipment-accessors
  "Store should provide shipment lookups."
  (let [st (store/mem-store)
        shipment (store/shipment st "ship-001")]
    (is (map? shipment))
    (is (= (:batch shipment) "batch-001"))
    (is (= (:qty shipment) 60))))

(deftest equipment-accessors
  "Store should provide equipment lookups."
  (let [st (store/mem-store)
        equipment (store/equipment st "maint-001")]
    (is (map? equipment))
    (is (= (:equipment equipment) "leather-cutting-press-02"))
    (is (= (:status equipment) :operational))))

(deftest plant-verified-guard
  "Plant verification guard should check registration status."
  (let [st (store/mem-store)]
    (is (store/plant-verified? st "plant-001"))
    (is (not (store/plant-verified? st "plant-unknown")))))

(deftest batch-verified-guard
  "Batch verification guard should check verification status."
  (let [st (store/mem-store)]
    (is (store/batch-verified? st "batch-001"))
    (is (not (store/batch-verified? st "batch-002")))))

(deftest batch-plant-verified-guard
  "Batch-plant verification should check both batch and its plant."
  (let [st (store/mem-store)]
    (is (store/batch-plant-verified? st "batch-001"))
    ;; batch-002's plant is verified, but we can test with a non-existent batch
    (is (not (store/batch-plant-verified? st "batch-unknown")))))

(deftest shipment-batch-id-resolution
  "Shipment-batch indirection should correctly resolve the underlying
  production-batch ID a shipment refers to (a shipment ID is NOT a batch
  ID -- this is the indirection the governor's plant/batch verification
  checks must resolve through for :actuation/coordinate-shipment)."
  (let [st (store/mem-store)]
    (is (= "batch-001" (store/shipment-batch-id st "ship-001")))
    (is (= "batch-002" (store/shipment-batch-id st "ship-002")))
    (is (nil? (store/shipment-batch-id st "ship-unknown")))))

(deftest missing-records
  "Accessors should handle missing records gracefully."
  (let [st (store/mem-store)]
    (is (nil? (store/plant st "nonexistent")))
    (is (nil? (store/production-batch st "nonexistent")))
    (is (nil? (store/shipment st "nonexistent")))
    (is (nil? (store/equipment st "nonexistent")))))

;; ----------------------------- append-only audit ledger (FIX: this commit) -----------------------------
;; Previously NO `ledger`/`append-ledger!` function existed anywhere in
;; `src/` -- not dead code, the concept was entirely absent.

(deftest ledger-starts-empty
  "A freshly created store's audit ledger is empty until a real commit
  or hold lands -- no proposal, no evaluation, no graph run has happened
  yet."
  (let [st (store/mem-store)]
    (is (empty? (store/ledger st)))))

(deftest append-ledger-is-append-only
  "append-ledger! appends facts in order and never mutates/removes prior
  entries."
  (let [st (store/mem-store)]
    (store/append-ledger! st {:t :committed :op :proposal/schedule-maintenance})
    (store/append-ledger! st {:t :governor-hold :op :actuation/coordinate-shipment})
    (let [l (store/ledger st)]
      (is (= 2 (count l)))
      (is (= :committed (:t (first l))))
      (is (= :governor-hold (:t (second l)))))))

(deftest append-ledger-returns-the-fact
  "append-ledger! returns the fact it appended."
  (let [st (store/mem-store)
        fact {:t :committed :op :proposal/log-production-batch}
        returned (store/append-ledger! st fact)]
    (is (= fact returned))))

(deftest ledger-is-independent-per-store
  "Two independently created stores have independent ledgers -- no shared
  mutable state leaks between them."
  (let [s1 (store/mem-store)
        s2 (store/mem-store)]
    (store/append-ledger! s1 {:t :committed :op :proposal/schedule-maintenance})
    (is (= 1 (count (store/ledger s1))))
    (is (empty? (store/ledger s2)))))
