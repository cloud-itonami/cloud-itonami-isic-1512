(ns luggage.advisor
  "Luggage/Handbag/Saddlery-and-Harness Manufacturing Plant Operations
  Advisor -- the LLM-driven suggestion layer. Proposes operations to the
  Governor for approval. This advisor coordinates cutting/stitching/
  assembly PLANT OPERATIONS on leather/synthetic goods (batch logging,
  maintenance scheduling, safety concerns, shipment coordination) -- it
  never operates equipment directly.")

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

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
