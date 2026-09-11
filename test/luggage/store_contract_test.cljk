(ns luggage.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [luggage.store :as store]))

(deftest ^{:doc "In-memory store should initialize with reference data."} mem-store-initialization
  (let [st (store/mem-store)]
    (is (map? st))
    (is (contains? st :data))))

(deftest ^{:doc "Store should provide plant lookups."} plant-accessors
  (let [st (store/mem-store)
        plant (store/plant st "plant-001")]
    (is (map? plant))
    (is (= (:name plant) "Community Leather Goods Workshop A"))
    (is (= (:location plant) "Italy"))))

(deftest ^{:doc "Store should provide batch lookups."} production-batch-accessors
  (let [st (store/mem-store)
        batch (store/production-batch st "batch-001")]
    (is (map? batch))
    (is (= (:style batch) "full-grain-tote-bag-M"))
    (is (= (:quantity batch) 60))
    (is (= (:plant batch) "plant-001"))))

(deftest ^{:doc "Store should provide shipment lookups."} shipment-accessors
  (let [st (store/mem-store)
        shipment (store/shipment st "ship-001")]
    (is (map? shipment))
    (is (= (:batch shipment) "batch-001"))
    (is (= (:qty shipment) 60))))

(deftest ^{:doc "Store should provide equipment lookups."} equipment-accessors
  (let [st (store/mem-store)
        equipment (store/equipment st "maint-001")]
    (is (map? equipment))
    (is (= (:equipment equipment) "leather-cutting-press-02"))
    (is (= (:status equipment) :operational))))

(deftest ^{:doc "Plant verification guard should check registration status."} plant-verified-guard
  (let [st (store/mem-store)]
    (is (store/plant-verified? st "plant-001"))
    (is (not (store/plant-verified? st "plant-unknown")))))

(deftest ^{:doc "Batch verification guard should check verification status."} batch-verified-guard
  (let [st (store/mem-store)]
    (is (store/batch-verified? st "batch-001"))
    (is (not (store/batch-verified? st "batch-002")))))

(deftest ^{:doc "Batch-plant verification should check both batch and its plant."} batch-plant-verified-guard
  (let [st (store/mem-store)]
    (is (store/batch-plant-verified? st "batch-001"))
    ;; batch-002's plant is verified, but we can test with a non-existent batch
    (is (not (store/batch-plant-verified? st "batch-unknown")))))

(deftest ^{:doc "Shipment-batch indirection should correctly resolve the underlying
  production-batch ID a shipment refers to (a shipment ID is NOT a batch
  ID -- this is the indirection the governor's plant/batch verification
  checks must resolve through for :actuation/coordinate-shipment)."} shipment-batch-id-resolution
  (let [st (store/mem-store)]
    (is (= "batch-001" (store/shipment-batch-id st "ship-001")))
    (is (= "batch-002" (store/shipment-batch-id st "ship-002")))
    (is (nil? (store/shipment-batch-id st "ship-unknown")))))

(deftest ^{:doc "Accessors should handle missing records gracefully."} missing-records
  (let [st (store/mem-store)]
    (is (nil? (store/plant st "nonexistent")))
    (is (nil? (store/production-batch st "nonexistent")))
    (is (nil? (store/shipment st "nonexistent")))
    (is (nil? (store/equipment st "nonexistent")))))

;; ----------------------------- append-only audit ledger (FIX: this commit) -----------------------------
;; Previously NO `ledger`/`append-ledger!` function existed anywhere in
;; `src/` -- not dead code, the concept was entirely absent.

(deftest ^{:doc "A freshly created store's audit ledger is empty until a real commit
  or hold lands -- no proposal, no evaluation, no graph run has happened
  yet."} ledger-starts-empty
  (let [st (store/mem-store)]
    (is (empty? (store/ledger st)))))

(deftest ^{:doc "append-ledger! appends facts in order and never mutates/removes prior
  entries."} append-ledger-is-append-only
  (let [st (store/mem-store)]
    (store/append-ledger! st {:t :committed :op :proposal/schedule-maintenance})
    (store/append-ledger! st {:t :governor-hold :op :actuation/coordinate-shipment})
    (let [l (store/ledger st)]
      (is (= 2 (count l)))
      (is (= :committed (:t (first l))))
      (is (= :governor-hold (:t (second l)))))))

(deftest ^{:doc "append-ledger! returns the fact it appended."} append-ledger-returns-the-fact
  (let [st (store/mem-store)
        fact {:t :committed :op :proposal/log-production-batch}
        returned (store/append-ledger! st fact)]
    (is (= fact returned))))

(deftest ^{:doc "Two independently created stores have independent ledgers -- no shared
  mutable state leaks between them."} ledger-is-independent-per-store
  (let [s1 (store/mem-store)
        s2 (store/mem-store)]
    (store/append-ledger! s1 {:t :committed :op :proposal/schedule-maintenance})
    (is (= 1 (count (store/ledger s1))))
    (is (empty? (store/ledger s2)))))
