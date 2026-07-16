(ns ferry.facts
  "Per-jurisdiction passenger-vessel safety-certification regulatory
  catalog -- the G2-style spec-basis table the Maritime Safety Governor
  checks every `:schedule-sailing-operation` proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's
  passenger-vessel safety-certification regime, or did it invent one?').

  Each entry below is a REAL jurisdiction with a REAL passenger-vessel
  safety regime: Japan's MLIT Maritime Bureau 船舶安全法 (Ship Safety
  Act) jurisdiction over passenger ships (旅客船) plus SOLAS Chapter III
  (life-saving appliances), the US Coast Guard's passenger-vessel regime
  (46 C.F.R. Subchapter H/K) grounded in SOLAS Chapter III, the UK
  Maritime and Coastguard Agency's Merchant Shipping (Passenger Ship
  Construction) Regulations (SOLAS Chapter III), and the Norwegian
  Maritime Authority's Ship Safety and Security Act. The required-
  evidence set (Passenger Ship Safety Certificate, flag-state registry /
  vessel record, life-saving-appliance inspection record, stability and
  load-line certificate) mirrors the certification a flag-state /
  port-state control inspector actually demands before a passenger
  vessel is authorized to embark passengers.

  This is a SEPARATE regime from `cloud-itonami-isic-5020`'s marine-cargo
  / tanker facts catalog (`tanker.facts`) -- passenger-vessel safety
  certification (SOLAS Chapter III life-saving appliances, passenger
  capacity limits) is a materially different regime from cargo-vessel
  certification (SOLAS Chapter II-2 inert-gas / fire protection) in
  every jurisdiction below, per this repo's own README `Scope note`.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the passenger-vessel
  safety-certification evidence set (Passenger Ship Safety Certificate,
  flag-state registry / vessel record, life-saving-appliance (LSA)
  inspection record, stability and load-line certificate);
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any `:schedule-sailing-operation`
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省 (MLIT) 海事局"
          :legal-basis "船舶安全法 (Ship Safety Act, 旅客船); SOLAS Chapter III (life-saving appliances)"
          :provenance "https://www.mlit.go.jp/common/001383958.pdf"
          :required-evidence ["passenger-ship-safety-certificate"
                              "flag-state registry / vessel record"
                              "life-saving-appliance (LSA) inspection record"
                              "stability and load-line certificate"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Coast Guard (USCG)"
          :legal-basis "Passenger Vessels (46 C.F.R. Subchapter H/K); SOLAS Chapter III (life-saving appliances)"
          :provenance "https://www.ecfr.gov/current/title-46"
          :required-evidence ["passenger-ship-safety-certificate"
                              "flag-state registry / vessel record"
                              "life-saving-appliance (LSA) inspection record"
                              "stability and load-line certificate"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Maritime and Coastguard Agency (MCA)"
          :legal-basis "Merchant Shipping (Passenger Ship Construction) Regulations (SOLAS Chapter III)"
          :provenance "https://www.gov.uk/government/organisations/maritime-and-coastguard-agency"
          :required-evidence ["passenger-ship-safety-certificate"
                              "flag-state registry / vessel record"
                              "life-saving-appliance (LSA) inspection record"
                              "stability and load-line certificate"]}
   "NOR" {:name "Norway"
          :owner-authority "Norwegian Maritime Authority (NMA / Sjøfartsdirektoratet)"
          :legal-basis "Norwegian Ship Safety and Security Act; SOLAS Chapter III"
          :provenance "https://www.sdir.no/en/"
          :required-evidence ["passenger-ship-safety-certificate"
                              "flag-state registry / vessel record"
                              "life-saving-appliance (LSA) inspection record"
                              "stability and load-line certificate"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to schedule a
  sailing operation on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-5011 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `ferry.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
