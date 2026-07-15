(ns luggage.governor
  "Luggage/Handbag/Saddlery-and-Harness Manufacturing Plant Operations
  Governor -- the independent compliance layer that earns the
  Luggage-Goods Operations Advisor the right to propose and log actions.
  The LLM has no notion of exotic-skin-sourcing law, leather-labeling
  regulation, or when a shipment or batch-logging is a real-world
  actuation, so this MUST be a separate system able to *reject* a proposal
  and fall back to HOLD.

  HARD violations (a human approver CANNOT override):
    1. Spec-basis            -- no official jurisdiction citation
    2. Effect not :propose   -- this actor may only ever PROPOSE, never
                                 actuate directly (`luggage.registry/allowed-ops`
                                 entries are all closed-allowlist proposals)
    3. Op not allowlisted    -- :op outside the CLOSED
                                 `luggage.registry/allowed-ops` set is rejected,
                                 whatever it claims to be
    4. Plant not verified    -- batch's plant registration must be confirmed
                                 before any action that logs or ships it
    5. Batch not verified    -- batch must be confirmed verified before any
                                 action that logs or ships it
    6. Direct equipment control -- NO cutting/stitching/assembly-line
                                 operation (those remain plant-engineer
                                 exclusive)
    7. Safety/quality concerns -- ALWAYS escalate (never silently logged)

  SOFT violation (can be approved by human):
    8. Confidence floor / high-stakes actuation -- low confidence OR real actuation

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates PLANT OPERATIONS around luggage/handbag/saddlery-
  and-harness manufacturing -- cutting, stitching and assembling leather or
  synthetic goods. It does NOT:
    - Operate cutting equipment or stitching machines
    - Make design decisions about patterns, cuts or hide-matching
    - Control production-line parameters (speed, tension, etc.)
    - Approve raw-material/tanning quality (that is the upstream
      tannery's/material supplier's responsibility, not this plant's)

  Those remain the exclusive authority of plant production engineers."
  (:require [clojure.string :as str]
            [luggage.registry :as registry]
            [luggage.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Operations that require human sign-off for real-world actuation:
  Shipment coordination with export/species-provenance implications."
  #{:actuation/coordinate-shipment})

(def process-control-keywords
  "Words that indicate process-engineering authority (FORBIDDEN for this
  actor). If a proposal mentions any of these, it's a hard block. Includes
  general cutting/stitching-line terms plus leather-goods-specific process
  terms (skiving/clicking/riveting/punching -- the leather trade's own
  vocabulary for thinning an edge, die-cutting a pattern piece on a
  clicking press, setting a rivet, and punching a stitch hole)."
  #{"speed" "tension" "needle" "presser" "feed" "stitch" "pattern"
    "cutting" "sewing" "stitching" "operate" "control" "blade" "angle"
    "parameter" "thread" "adjust" "trim" "skiving" "clicking" "riveting"
    "punching"})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's requirements."
  [proposal _st]
  (let [op (:op proposal)]
    (when (contains? #{:actuation/coordinate-shipment :proposal/flag-safety-concern} op)
      (when (or (empty? (:cites proposal))
                (and (contains? (:value proposal) :spec-basis)
                     (nil? (:spec-basis (:value proposal)))))
        [{:rule :no-spec-basis
          :detail "公式な仕様基準の引用が無い提案は処理できない"}]))))

(defn- effect-not-propose-violations
  "HARD: this actor may only ever propose. Any proposal whose :effect is
  not :propose is rejected outright, regardless of which op it names."
  [proposal _st]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail "この actor は :propose 以外の :effect を持つ提案を処理できない"}]))

(defn- op-not-allowlisted-violations
  "HARD: :op must be a member of the CLOSED `luggage.registry/allowed-ops`
  set. An unrecognized/unlisted op is rejected outright rather than
  silently falling through every other check with zero violations."
  [proposal _st]
  (when-not (contains? registry/allowed-ops (:op proposal))
    [{:rule :op-not-allowlisted
      :detail (str "許可されていない op です: " (:op proposal))}]))

(defn- resolve-batch-id
  "Resolve the production-batch ID a proposal's :subject actually refers
  to. For `:proposal/log-production-batch` the subject IS the batch ID.
  For `:actuation/coordinate-shipment` the subject is a SHIPMENT ID, so it
  must be resolved through the shipment record's own :batch field --
  looking the shipment ID up directly in the batch keyspace would silently
  no-op (nil plant-id) or silently always-fail (never-verified nil batch)
  against the wrong data."
  [op subject st]
  (case op
    :proposal/log-production-batch subject
    :actuation/coordinate-shipment (store/shipment-batch-id st subject)
    nil))

(defn- plant-verification-violations
  "Batch must belong to a verified plant before any action that logs or
  ships it."
  [{:keys [op subject]} st]
  (when (contains? #{:proposal/log-production-batch :actuation/coordinate-shipment} op)
    (let [batch-id (resolve-batch-id op subject st)
          batch (when batch-id (store/production-batch st batch-id))
          plant-id (:plant batch)]
      (when plant-id
        (when-not (store/plant-verified? st plant-id)
          [{:rule :plant-not-verified
            :detail "製造施設が登録・検証されていない"}])))))

(defn- batch-verification-violations
  "Batch must be independently verified before logging or shipment."
  [{:keys [op subject]} st]
  (when (contains? #{:proposal/log-production-batch :actuation/coordinate-shipment} op)
    (let [batch-id (resolve-batch-id op subject st)]
      (when-not (and batch-id (store/batch-verified? st batch-id))
        [{:rule :batch-not-verified
          :detail "製造ロットが検証されていない"}]))))

(defn- process-control-block-violations
  "HARD BLOCK: This actor does NOT operate cutting/stitching-line
  equipment. If a proposal mentions cutting speed, stitching tension,
  needle control, skiving/clicking/riveting/punching, or other process
  parameters, reject it immediately. Those decisions remain the exclusive
  authority of licensed plant production engineers."
  [proposal _st]
  (let [detail (str (:detail (:value proposal) "") " " (:op proposal))
        words (re-seq #"\w+" (str/lower-case detail))
        ;; `some` over a set-as-predicate returns the actual matched word
        ;; (truthy) rather than a bare `true`/`nil`, so the violation detail
        ;; below can name the specific forbidden keyword found.
        forbidden (some process-control-keywords words)]
    (when forbidden
      [{:rule :process-control-forbidden
        :detail (str "設備操作は認可エンジニアの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- safety-concern-escalation-violations
  "Safety/quality concerns ALWAYS escalate to human. Never silently log a
  concern."
  [{:keys [op]} _st]
  (when (= op :proposal/flag-safety-concern)
    [{:rule :safety-concern-escalates
      :detail "安全・品質懸念は必ず人間にエスカレートされる"}]))

(defn- confidence-gate-violations
  "Low confidence or high-stakes actuation -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (contains? high-stakes op))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "実際の操作には人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [spec-basis-violations
                           effect-not-propose-violations
                           op-not-allowlisted-violations
                           plant-verification-violations
                           batch-verification-violations
                           process-control-block-violations]
        hard-checks-value [safety-concern-escalation-violations]
        soft-checks [confidence-gate-violations]
        hard-violations-store (mapcat #(% proposal st) hard-checks-store)
        hard-violations-value (mapcat #(% proposal (:value proposal)) hard-checks-value)
        hard-violations (concat hard-violations-store hard-violations-value)
        soft-violations (mapcat #(% proposal (:value proposal)) soft-checks)]
    {:holds? (boolean (seq hard-violations))
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
