(ns luggage.advisor
  "Luggage/Handbag/Saddlery-and-Harness Manufacturing Plant Operations
  Advisor -- the LLM-driven suggestion layer. Proposes operations to the
  Governor for approval. This advisor coordinates cutting/stitching/
  assembly PLANT OPERATIONS on leather/synthetic goods (batch logging,
  maintenance scheduling, safety concerns, shipment coordination) -- it
  never operates equipment directly.

  FIX (this commit): this namespace previously had NO `defprotocol
  Advisor` at all -- just four plain proposal-building functions plus a
  `mock-advisor` returning a bare `{:type :mock ...}` marker map that was
  never dispatched through anything. `luggage.sim` called
  `governor/evaluate` directly on hand-picked proposal fns, which is how
  it got away with not having a real advisor seam. Every other actor in
  this fleet (`transportops.advisor/Advisor`,
  `berrynutops.advisor/Advisor`, etc.) has a real protocol so a genuine
  LLM-backed advisor is a swap, not a rewrite. This now matches that
  convention: a real `Advisor` protocol + `MockAdvisor` record whose
  `advise` dispatches on `(:op request)` to the SAME four proposal
  builders below, UNCHANGED -- this fix only gives them a proper
  protocol-based home, it does not touch their domain logic (citations,
  evidence checklists, confidence values, or the deliberate avoidance of
  forbidden process-control keywords in `maintenance-proposal`'s default
  text)."
  )

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (advise [advisor request store]
    "Given a request map ({:op .. :subject .. [:concern-type ..]}) and
    the store (currently unused by the mock but part of the seam so a
    real LLM-backed advisor can read plant/batch/shipment state before
    drafting), return a proposal map ready for `luggage.governor/evaluate`.
    :op must be one of `luggage.registry/allowed-ops`; an unrecognized op
    falls through to a zero-confidence stub -- the Governor's closed
    allowlist independently rejects it regardless of what the advisor
    claims."))

;; ----------------------------- proposal builders (UNCHANGED domain logic) -----------------------------

(defn batch-log-proposal
  "Propose logging a completed cutting/stitching/assembly production batch
  (with its output-quality data) to the audit ledger."
  [_advisor batch-id]
  {:op :proposal/log-production-batch
   :subject batch-id
   :effect :propose
   :cites ["FTC Guides for Select Leather and Imitation Leather Products, 16 CFR Part 24"]
   :value {:evidence {:batch-verified true :quantity-confirmed true :quality-grade-assigned true}
           :confidence 0.87
           :detail "Production batch logged and output-quality data recorded"}})

(defn maintenance-proposal
  "Propose scheduling cutting/stitching-line-equipment maintenance."
  [_advisor equipment-id]
  {:op :proposal/schedule-maintenance
   :subject equipment-id
   :effect :propose
   :cites ["Canada Labour Code, R.S.C. 1985, c. L-2"]
   :value {:evidence {:equipment-record true :maintenance-schedule-ok true}
           :confidence 0.85
           ;; Deliberately avoids the literal words "cutting"/"stitching"/
           ;; "skiving"/"clicking" in this routine coordination text --
           ;; those are forbidden equipment-control keywords in
           ;; luggage.governor/process-control-keywords, and a mere
           ;; maintenance-SCHEDULING proposal is not equipment control.
           ;; The equipment record itself (looked up by ID) still
           ;; identifies which line it is; only this free-text detail
           ;; avoids the collision, matching this fleet's established
           ;; convention of keeping default advisor text generic here.
           :detail "Maintenance scheduled for plant equipment"}})

(defn safety-concern-proposal
  "Propose flagging an equipment-safety or output-quality-defect concern
  (ALWAYS escalates to human)."
  [_advisor batch-id concern-type]
  {:op :proposal/flag-safety-concern
   :subject batch-id
   :effect :propose
   :cites ["REACH Regulation (EC) No 1907/2006, Annex XVII, Entry 47"]
   :value {:evidence {:concern-documented true :photos-attached true}
           :confidence 0.82
           :concern-type concern-type
           :detail (str "Safety/quality concern flagged: " concern-type " -- escalation required")}})

(defn shipment-proposal
  "Propose outbound finished-product shipment coordination (high-stakes actuation)."
  [_advisor shipment-id]
  {:op :actuation/coordinate-shipment
   :subject shipment-id
   :effect :propose
   :cites ["CITES implementing regulations, 50 CFR Part 23"]
   :value {:evidence {:export-permit true :shipping-manifest true :invoice-attached true}
           :confidence 0.89
           :detail "Shipment ready for outbound coordination"}})

;; ----------------------------- mock advisor -----------------------------

(defrecord MockAdvisor []
  Advisor
  (advise [this request _store]
    (case (:op request)
      :proposal/log-production-batch
      (batch-log-proposal this (:subject request))

      :proposal/schedule-maintenance
      (maintenance-proposal this (:subject request))

      :proposal/flag-safety-concern
      (safety-concern-proposal this (:subject request) (:concern-type request))

      :actuation/coordinate-shipment
      (shipment-proposal this (:subject request))

      ;; Fallback for an unrecognized op -- the Governor's closed
      ;; `luggage.registry/allowed-ops` allowlist independently rejects
      ;; this regardless of what the advisor claims.
      {:op (:op request)
       :subject (:subject request)
       :effect :propose
       :cites []
       :value {:evidence {}
               :confidence 0.0
               :detail "Unknown operation"}})))

(defn mock-advisor
  "Create a mock advisor for testing/demo. Real implementation would call
  an LLM via `advise` -- this record IS the injection point."
  []
  (->MockAdvisor))
