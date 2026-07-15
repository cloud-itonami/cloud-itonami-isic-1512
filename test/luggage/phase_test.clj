(ns luggage.phase-test
  (:require [clojure.test :refer [deftest is]]
            [luggage.phase :as phase]))

(deftest phase-table-structure
  "Phase table should define correct graph structure."
  (let [pt phase/phase-table]
    (is (map? pt))
    (is (contains? pt :start))
    (is (contains? pt :nodes))
    (is (contains? pt :edges))
    (is (contains? pt :output-node))
    (is (= (:start pt) phase/ADVISOR-NODE))))

(deftest phase-nodes-defined
  "All required nodes should be defined in phase table."
  (let [pt phase/phase-table
        nodes (:nodes pt)]
    (is (contains? nodes phase/ADVISOR-NODE))
    (is (contains? nodes phase/GOVERNOR-NODE))
    (is (contains? nodes phase/HOLD-NODE))
    (is (contains? nodes phase/COMPLETE-NODE))))

(deftest phase-edges-defined
  "Edges should define correct flow."
  (let [pt phase/phase-table
        edges (:edges pt)]
    (is (seq edges))
    ;; Should have at least advisor -> governor edge
    (is (some #(= (first %) phase/ADVISOR-NODE) edges))))

(deftest starting-node
  "Starting node should be ADVISOR-NODE."
  (is (= (phase/starting-node) phase/ADVISOR-NODE)))

(deftest terminal-nodes
  "Terminal nodes should be HOLD-NODE and COMPLETE-NODE."
  (is (phase/is-terminal? phase/HOLD-NODE))
  (is (phase/is-terminal? phase/COMPLETE-NODE))
  (is (not (phase/is-terminal? phase/ADVISOR-NODE)))
  (is (not (phase/is-terminal? phase/GOVERNOR-NODE))))
