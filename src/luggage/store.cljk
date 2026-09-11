(ns luggage.store
  "In-memory store for luggage/handbag/saddlery-and-harness manufacturing
  plant operations state. Plants in this domain receive raw or semi-
  finished leather/synthetic material and cut, skive, stitch and assemble
  it into finished luggage, handbags, saddlery and harness on cutting/
  stitching/assembly lines. This is a reference implementation; production
  systems would use Datomic or a similar persistent event store for audit
  and replay.

  FIX (this commit): the append-only audit ledger (`ledger`/
  `append-ledger!`) is this actor's core missing plumbing before this
  fix -- no such function existed anywhere in `src/`, despite
  `blueprint.edn`'s `:required-technologies [... :audit-ledger]` and the
  README's 'immutable audit ledger' line implying one should exist.
  `luggage.operation`'s `:commit`/`:hold` graph nodes now append every
  committed/held/approval-rejected decision fact here, so a plant/batch's
  operating history (every :proposal/log-production-batch /
  :proposal/schedule-maintenance / :proposal/flag-safety-concern /
  :actuation/coordinate-shipment decision) is always a query over an
  immutable log. The ledger lives inside the SAME `:data` atom as every
  other record here (a plain `:ledger` vector key) -- ALL pre-existing
  accessors below (`plant`, `production-batch`, `shipment`, `equipment`,
  the verification guards, `shipment-batch-id`) and `mem-store`'s shape
  (a map with a `:data` atom, as depended on directly by
  `store-contract-test`/`governor-contract-test`) are UNCHANGED.")

;; ----------------------------- store initialization -----------------------------

(defn mem-store
  "Create an in-memory store with reference data for luggage/handbag/
  saddlery-and-harness manufacturing."
  []
  {:data (atom {
           :ledger []
           :plants {
             "plant-001" {:name "Community Leather Goods Workshop A"
                         :location "Italy"
                         :registered? true
                         :jurisdiction :ITA}}
           :production-batches {
             "batch-001" {:plant "plant-001"
                         :style "full-grain-tote-bag-M"
                         :quantity 60
                         :verified? true
                         :quality-grade "standard"
                         :material-source "cowhide-full-grain"}
             "batch-002" {:plant "plant-001"
                         :style "python-clutch-S"
                         :quantity 15
                         :verified? false
                         :quality-grade "standard"
                         :material-source "farmed-python"}}
           :shipments {
             "ship-001" {:batch "batch-001"
                        :destination "wholesale-buyer-A"
                        :qty 60
                        :scheduled-date "2026-08-01"
                        :status :pending}
             "ship-002" {:batch "batch-002"
                        :destination "wholesale-buyer-B"
                        :qty 15
                        :scheduled-date "2026-08-05"
                        :status :pending}}
           :maintenance-log {
             "maint-001" {:equipment "leather-cutting-press-02"
                         :last-service "2026-06-20"
                         :status :operational}}})})

;; ----------------------------- accessors -----------------------------

(defn plant
  "Get plant record by ID."
  [st plant-id]
  (get-in @(:data st) [:plants plant-id]))

(defn production-batch
  "Get production batch record by ID."
  [st batch-id]
  (get-in @(:data st) [:production-batches batch-id]))

(defn shipment
  "Get shipment record by ID."
  [st shipment-id]
  (get-in @(:data st) [:shipments shipment-id]))

(defn equipment
  "Get equipment maintenance record by ID."
  [st equipment-id]
  (get-in @(:data st) [:maintenance-log equipment-id]))

;; ----------------------------- guards -----------------------------

(defn plant-verified?
  "Check if plant is registered and authorized."
  [st plant-id]
  (let [p (plant st plant-id)]
    (:registered? p false)))

(defn batch-verified?
  "Check if production batch is verified."
  [st batch-id]
  (let [b (production-batch st batch-id)]
    (:verified? b false)))

(defn batch-plant-verified?
  "Check if batch's plant is verified."
  [st batch-id]
  (let [b (production-batch st batch-id)
        plant-id (:plant b)]
    (plant-verified? st plant-id)))

(defn shipment-batch-id
  "Resolve the production-batch ID a shipment refers to. A shipment's
  :subject on a proposal is a shipment ID, NOT a batch ID directly -- this
  indirection must be resolved explicitly before checking plant/batch
  verification for a shipment-coordination proposal, otherwise the
  verification check silently no-ops or silently always-fails against the
  wrong keyspace."
  [st shipment-id]
  (:batch (shipment st shipment-id)))

;; ----------------------------- append-only audit ledger -----------------------------

(defn ledger
  "The append-only audit ledger: every committed/held/approval-rejected
  decision fact, in append order. Genuinely wired into
  `luggage.operation`'s `:commit`/`:hold` nodes -- not test-only
  plumbing."
  [st]
  (get @(:data st) :ledger []))

(defn append-ledger!
  "Append one immutable decision fact to the ledger. Returns the fact.
  The ledger is append-only -- there is deliberately no update/remove
  function."
  [st fact]
  (swap! (:data st) update :ledger (fnil conj []) fact)
  fact)
