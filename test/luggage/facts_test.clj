(ns luggage.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [luggage.facts :as facts]))

(deftest catalog-has-jurisdictions
  "Catalog should define at least 4 jurisdictions with official spec-basis."
  (is (>= (count facts/catalog) 4))
  (is (contains? facts/catalog :USA))
  (is (contains? facts/catalog :ITA))
  (is (contains? facts/catalog :CAN))
  (is (contains? facts/catalog :MEX)))

(deftest jurisdiction-coverage-honest
  "Coverage reporting should be honest about scope."
  (let [cov (facts/coverage)]
    (is (map? cov))
    (is (>= (:implemented cov) 4))
    (is (= (:worldwide-jurisdictions cov) 194))
    (is (> (:coverage-pct cov) 0))
    (is (contains? cov :note))))

(deftest usa-requirements
  "USA jurisdiction should have official spec-basis for all requirements."
  (let [reqs (facts/requirement-citations :USA)]
    (is (map? reqs))
    (is (contains? reqs :leather-labeling))
    (is (contains? reqs :exotic-skin-sourcing))
    (is (contains? reqs :labor-standards))
    ;; Each requirement should have spec-basis
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))))

(deftest evidence-satisfaction
  "Test jurisdiction-specific evidence checklist satisfaction."
  (testing "USA complete leather-labeling + exotic-skin-sourcing + labor requirements"
    (let [complete {:product-label true :material-content-verified true :country-of-origin-marking true
                    :cites-permit true :species-origin-cert true
                    :wage-hour-record true :safety-training true}]
      (is (facts/required-evidence-satisfied? :USA complete))))

  (testing "Incomplete evidence should fail"
    (let [checklist {:product-label true}]
      (is (not (facts/required-evidence-satisfied? :USA checklist)))))

  (testing "Italy complete requirements"
    (let [checklist {:product-label true :origin-chain-verified true
                     :cr6-test-report true :tannery-cert true
                     :cites-permit true :species-origin-cert true
                     :risk-assessment true :safety-training true}]
      (is (facts/required-evidence-satisfied? :ITA checklist))))

  (testing "Canada complete requirements"
    (let [checklist {:product-label true :dealer-info-marking true
                     :wage-record true :safety-training true}]
      (is (facts/required-evidence-satisfied? :CAN checklist))))

  (testing "Mexico complete requirements"
    (let [checklist {:product-label true :material-content-verified true
                     :wage-hour-record true :safety-training true}]
      (is (facts/required-evidence-satisfied? :MEX checklist)))))

(deftest spec-basis-citations
  "All spec-basis citations should be strings (official references)."
  (doseq [[_jurisdiction jurisdiction-data] facts/catalog]
    (let [reqs (:requirements jurisdiction-data)]
      (doseq [[_req-key req-spec] reqs]
        (is (string? (:spec-basis req-spec))
          (str "Spec-basis should be a string in " _jurisdiction "/" _req-key))))))
