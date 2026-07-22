(ns luggage.facts
  "Per-jurisdiction luggage/handbag/saddlery-and-harness manufacturing
  compliance requirements. Every jurisdiction in this catalog is backed by
  an official spec-basis. NEVER invent requirements without an official
  citation.

  This is deliberately a starting catalog (honest coverage reporting) to
  prove the governor contract end-to-end, not a claim of global coverage.
  Adding a jurisdiction is additive: one map entry citing a real official
  source -- never fabricate a jurisdiction's requirements to make coverage
  look bigger.

  Citations verified 2026-07-21: MEX leather-labeling confirmed directly
  against PROFECO's official NOM listing (profeco.gob.mx/juridico/normas/
  noms_economia.asp), NOM-020-SCFI-1997, in force since 1999-04-27. MEX
  labor-standards confirmed via primary text of the Ley Federal del Trabajo
  fetched from the official gob.mx mirror (gob.mx/cms/uploads/attachment/
  file/156203/1044_Ley_Federal_del_Trabajo.pdf). Both HIGH confidence.

  Citations verified 2026-07-22: JPN quality-labeling confirmed directly
  against the primary text of the Household Goods Quality Labeling Act
  (家庭用品品質表示法, Act No. 104 of 1962) and its Enforcement Order
  (家庭用品品質表示法施行令, Cabinet Order No. 390 of 1962), fetched via
  e-Gov's law-data API (laws.e-gov.go.jp/api/1/lawdata/<law-id> --
  e-Gov's newer elaws.e-gov.go.jp/document UI is a JS-rendered SPA that
  returns only an empty app shell to direct fetch, so the API endpoint
  was used instead). The Enforcement Order's Appended Table item 11
  designates 'かばん' (bags, limited to those made of cowhide or other
  Cabinet-Office-Ordinance-designated material) as a household good
  subject to the Act's Article 3 standardized-labeling regime. HIGH
  confidence (primary text of both the Act and its designating Order
  read directly).")

;; ----------------------------- jurisdiction catalog -----------------------------

(def catalog
  "Per-jurisdiction luggage/handbag/saddlery-and-harness manufacturing
  compliance requirements with official spec-basis citations."
  {
   :USA
   {:name "United States"
    :requirements
    {:leather-labeling {:description "Country-of-origin and material-content labeling on leather goods, and truthful marketing distinguishing genuine leather from imitation leather"
                        :required true
                        :spec-basis "FTC Guides for Select Leather and Imitation Leather Products, 16 CFR Part 24"
                        :evidence #{:product-label :material-content-verified :country-of-origin-marking}}
     :exotic-skin-sourcing {:description "CITES import/export permits and origin certification for regulated-species exotic leather (alligator, crocodile, python, etc.) used in luggage, handbags and saddlery"
                            :required true
                            :spec-basis "Endangered Species Act, 16 U.S.C. § 1538; CITES implementing regulations, 50 CFR Part 23"
                            :evidence [:cites-permit :species-origin-cert]}
     :labor-standards {:description "Compliance with Fair Labor Standards Act and workplace safety"
                      :required true
                      :spec-basis "FLSA 29 CFR § 516, OSHA 1910 Subpart A"
                      :evidence [:wage-hour-record :safety-training]}}}

   :ITA
   {:name "Italy"
    :requirements
    {:made-in-italy-labeling {:description "Leather-goods (pelletteria) products claiming full 'Made in Italy' origin must satisfy the entire domestic production-chain requirement and bear compliant labeling"
                              :required true
                              :spec-basis "Legge 8 aprile 2010, n. 55, Disposizioni concernenti la commercializzazione di prodotti tessili, della pelletteria e calzaturieri"
                              :evidence [:product-label :origin-chain-verified]}
     :chromium-vi-restriction {:description "Leather articles or leather parts of articles coming into contact with skin must not exceed the Chromium VI content limit"
                               :required true
                               :spec-basis "REACH Regulation (EC) No 1907/2006, Annex XVII, Entry 47"
                               :evidence [:cr6-test-report :tannery-cert]}
     :species-sourcing {:description "Wildlife-trade protection for exotic hides sourced from regulated species"
                       :required true
                       :spec-basis "Council Regulation (EC) No 338/97 on the protection of species of wild fauna and flora by regulating trade therein"
                       :evidence [:cites-permit :species-origin-cert]}
     :labor-standards {:description "Workplace health and safety compliance for plant personnel"
                      :required true
                      :spec-basis "Decreto Legislativo 9 aprile 2008, n. 81 (Testo Unico sulla Salute e Sicurezza sul Lavoro)"
                      :evidence [:risk-assessment :safety-training]}}}

   :CAN
   {:name "Canada"
    :requirements
    {:leather-labeling {:description "General consumer-product identity and dealer-information labelling for leather goods sold at retail"
                        :required true
                        :spec-basis "Consumer Packaging and Labelling Act, R.S.C. 1985, c. C-38"
                        :evidence [:product-label :dealer-info-marking]}
     :labor-standards {:description "Federal labour-standards compliance for plant personnel"
                      :required true
                      :spec-basis "Canada Labour Code, R.S.C. 1985, c. L-2"
                      :evidence [:wage-record :safety-training]}}}

   :MEX
   {:name "Mexico"
    :requirements
    {:leather-labeling {:description "Commercial-information labeling for tanned natural leather and hides, and synthetic materials with that appearance, used in footwear, leather goods (marroquineria) and related products"
                        :required true
                        :spec-basis "NOM-020-SCFI-1997, Informacion comercial-Etiquetado de cueros y pieles curtidas naturales y materiales sinteticos o artificiales con esa apariencia, calzado, marroquineria asi como los productos elaborados con dichos materiales"
                        :evidence [:product-label :material-content-verified]}
     :labor-standards {:description "Federal labor-law compliance for plant personnel, including wages, hours and workplace safety"
                      :required true
                      :spec-basis "Ley Federal del Trabajo (Diario Oficial de la Federacion, 1 de abril de 1970, ultima reforma DOF 12-06-2015)"
                      :evidence [:wage-hour-record :safety-training]}}}

   :JPN
   {:name "Japan"
    :requirements
    {:quality-labeling {:description "Leather bags (かばん) made of cowhide or other Cabinet-Office-Ordinance-designated material are a designated household good subject to standardized quality-labeling content (materials, care, other matters) set by the Prime Minister under Article 3"
                       :required true
                       :spec-basis "家庭用品品質表示法 (Household Goods Quality Labeling Act, Act No. 104 of 1962) Art. 1-3; 家庭用品品質表示法施行令 (Enforcement Order, Cabinet Order No. 390 of 1962) Appended Table item 11 (かばん)"
                       :evidence [:product-label :material-content-verified]}}}})

;; ----------------------------- coverage reporting (honest) -----------------------------

(defn coverage
  "Report what fraction of worldwide jurisdictions have official spec-basis
  in this catalog. Honest about out-of-scope coverage."
  []
  (let [catalog-count (count catalog)
        world-jurisdictions 194]
    {:implemented catalog-count
     :worldwide-jurisdictions world-jurisdictions
     :coverage-pct (* 100.0 (/ catalog-count world-jurisdictions))
     :note "Starting catalog to prove governor contract end-to-end, not global coverage claim"}))

;; ----------------------------- helpers -----------------------------

(defn requirement-citations
  "Get all official citations for a jurisdiction's requirements."
  [jurisdiction]
  (get-in catalog [jurisdiction :requirements]))

(defn required-evidence-satisfied?
  "Check if a checklist satisfies this jurisdiction's evidence requirements."
  [jurisdiction checklist]
  (let [reqs (get-in catalog [jurisdiction :requirements])]
    (every? (fn [[_req-key req-spec]]
              (if (:required req-spec)
                (let [evidence-keys (set (:evidence req-spec))]
                  (every? #(contains? checklist %) evidence-keys))
                true))
            reqs)))
