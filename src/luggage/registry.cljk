(ns luggage.registry
  "Proposal registry and drafting helpers for luggage/handbag/saddlery-and-
  harness manufacturing plant operations. Every proposal carries its
  spec-basis and evidence checklist.")

;; ----------------------------- closed op allowlist -----------------------------

(def allowed-ops
  "The CLOSED set of operations this actor may EVER propose. Any :op outside
  this set is a hard, permanent governor violation -- see
  `luggage.governor`'s `op-not-allowlisted-violations` check, which
  requires this same def (rather than re-declaring its own copy) so the two
  can never drift out of sync."
  #{:proposal/log-production-batch
    :proposal/schedule-maintenance
    :proposal/flag-safety-concern
    :actuation/coordinate-shipment})

;; ----------------------------- hard invariants -----------------------------

(defn hard-invariant-violations
  "Hard invariants that CANNOT be overridden:
  - If operation affects shipment or a safety/quality concern, it must carry spec-basis."
  [op-type value]
  (when (contains? #{:actuation/coordinate-shipment :proposal/flag-safety-concern} op-type)
    (when (or (empty? (:cites value))
              (and (contains? value :spec-basis) (nil? (:spec-basis value))))
      [{:rule :no-spec-basis
        :detail "公式な仕様基準の引用が無い提案は処理できない"}])))

(defn protected-operation-violations
  "Operations that require human sign-off and can never be autonomous:
  - Shipment coordination (even proposals)
  - Safety/quality concern flagging (always escalates)"
  [op-type]
  (when (contains? #{:actuation/coordinate-shipment :proposal/flag-safety-concern} op-type)
    [{:rule :requires-human-approval
      :detail "製品出荷と安全・品質懸念の報告には人間の承認が必須"}]))

;; ----------------------------- proposal drafts -----------------------------

(defn batch-log-draft
  "Draft a production batch logging proposal (cutting/stitching/assembly
  batch, output-quality data).
  subject: batch ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items (batch verification, etc.)"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/log-production-batch
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn maintenance-draft
  "Draft a cutting/stitching-line-equipment maintenance scheduling proposal.
  subject: equipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified maintenance records"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/schedule-maintenance
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn safety-concern-draft
  "Draft an equipment-safety/quality-defect concern flag (ALWAYS escalates).
  subject: batch or equipment ID
  cites: spec-basis citations
  concern-type: category of concern (equipment-safety, quality-defect, labeling, etc.)
  evidence-checklist: map of verified concern evidence
  detail: narrative description"
  [subject cites concern-type evidence-checklist confidence detail]
  {:op :proposal/flag-safety-concern
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :concern-type concern-type
           :detail detail}})

(defn shipment-draft
  "Draft an outbound finished-product shipment coordination proposal
  (high-stakes actuation).
  subject: shipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified shipping/export documentation"
  [subject cites evidence-checklist confidence detail]
  {:op :actuation/coordinate-shipment
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})
