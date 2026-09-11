(ns luggage.phase-test
  "Tests for the 0->3 phase rollout control.

  FIX (this commit): the previous version of this test file exercised an
  inert `phase-table` data map (:nodes/:edges/:predicate) that no
  `langgraph.graph` function ever consumed -- it was testing decoration,
  not behavior. `luggage.phase` no longer has that shape at all; these
  tests exercise the REAL `may-auto-commit?` rollout gate that
  `luggage.operation`'s `:decide` node genuinely calls."
  (:require [clojure.test :refer [deftest is testing]]
            [luggage.phase :as phase]))

(deftest phase-0-all-held
  (testing "Phase 0: nothing auto-commits, even a fully clean proposal"
    (doseq [op [:proposal/log-production-batch :proposal/schedule-maintenance
                :proposal/flag-safety-concern :actuation/coordinate-shipment]]
      (is (false? (phase/may-auto-commit? op 0))))))

(deftest phase-1-low-risk-auto
  (testing "Phase 1: only maintenance scheduling auto-commits"
    (is (true? (phase/may-auto-commit? :proposal/schedule-maintenance 1)))
    (is (false? (phase/may-auto-commit? :proposal/log-production-batch 1)))
    (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern 1)))
    (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment 1)))))

(deftest phase-2-medium-risk-auto
  (testing "Phase 2: maintenance + production-batch logging both auto-commit"
    (is (true? (phase/may-auto-commit? :proposal/schedule-maintenance 2)))
    (is (true? (phase/may-auto-commit? :proposal/log-production-batch 2)))
    (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern 2)))
    (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment 2)))))

(deftest phase-3-full-autonomy-for-what-can-ever-be-clean
  (testing "Phase 3: same auto set as phase 2 -- safety-concern and
            shipment can never actually reach a clean evaluation (HARD /
            high-stakes governor gates), so there is no higher tier to
            add"
    (is (true? (phase/may-auto-commit? :proposal/schedule-maintenance 3)))
    (is (true? (phase/may-auto-commit? :proposal/log-production-batch 3)))
    (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern 3)))
    (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment 3)))))

(deftest unknown-phase-conservative-default
  (testing "An unrecognized phase number holds everything"
    (doseq [op [:proposal/log-production-batch :proposal/schedule-maintenance]]
      (is (false? (phase/may-auto-commit? op 99))))))

(deftest safety-concern-and-shipment-never-auto-commit-at-any-phase
  (testing ":proposal/flag-safety-concern and :actuation/coordinate-shipment
            are NEVER auto-commit at any phase -- defense-in-depth on top
            of the Governor's own independent hard/soft blocks"
    (doseq [phase-num [0 1 2 3]]
      (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern phase-num)))
      (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment phase-num))))))
