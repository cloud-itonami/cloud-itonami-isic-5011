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
  fabricate one, and the governor holds if it tries.

  Citation verified 2026-07-22: PHL's Maritime Industry Authority
  (MARINA) directly confirmed as the Passenger Ship Safety Certificate
  issuing authority via MARINA's own hosted Revised Implementing Rules
  and Regulations of Republic Act No. 9295 (the Domestic Shipping
  Development Act of 2004, marina.gov.ph), which lists 'Passenger Ship
  Safety Certificate' among the Ship Safety Certificates required for
  domestic vessels. HIGH confidence, read directly (pdftotext).

  Citation verified 2026-07-23: GRC. Two sources read directly this
  session:
  (1) EUR-Lex CELEX:32009L0045 (Directive 2009/45/EC of 6 May 2009 on
  safety rules and standards for passenger ships (Recast), binding on
  Greece as an EU/EEA member state; EUR-Lex marks it 'In force', with a
  current consolidated version dated 31/07/2022). Its Annex I Chapter
  III, headed 'LIFE SAVING APPLIANCES', states verbatim: 'All above
  appliances, including their launching appliances where applicable,
  shall comply with the regulations of Chapter III of the Annex to the
  1974 SOLAS Convention, as amended...' -- i.e. this Directive's
  passenger-ship regime is a direct EU-law implementation of SOLAS
  Chapter III, the same narrow angle as every other entry in this
  catalog. Article 13 requires: 'All new and existing passenger ships
  shall be provided with a Passenger Ship Safety Certificate in
  compliance with this Directive... issued by the Administration of
  the flag State after an initial survey'.
  (2) The Greek Ministry of Maritime Affairs and Insular Policy's own
  site (ynanp.gr), specifically the page filed under Κλάδος Ελέγχου
  Πλοίων (Ship Inspection Branch) -> Διεύθυνση Μελετών & Κατασκευών
  Πλοίων (ΔΙΜΕΚΑΠ) -> Συντονισμού & Καταμέρτησης -> Πιστοποιητικά
  Ασφάλειας -> 'Κοινοτική Οδηγία 2009 45' ('Community Directive
  2009/45'), which lists Π.Δ. 20/2012 (ΦΕΚ Α' 46/2012) verbatim as:
  'Τροποποίηση διατάξεων του π.δ. 103/1999 «Κανόνες και πρότυπα
  ασφαλείας για τα επιβατηγά πλοία σύμφωνα με την Οδηγία 98/18/ΕΚ του
  Συμβουλίου της 17ης Μαρτίου 1998» (Α΄ 110), όπως ισχύει μετά την
  τροποποίησή του με τα π.δ. 309/2003 (Α΄ 261), 3/2005 (Α΄2) και
  66/2005 (Α΄100)...' (own translation: 'Amendment of provisions of
  Presidential Decree 103/1999 \"Rules and safety standards for
  passenger ships in accordance with Council Directive 98/18/EC of the
  Council of 17 March 1998\" (Gov't Gazette A'110), as in force after
  its amendment by Presidential Decrees 309/2003 (A'261), 3/2005 (A'2)
  and 66/2005 (A'100)...'). This is the Ministry's OWN filing of Π.Δ.
  103/1999 (as amended) under its 'Directive 2009/45' heading -- direct
  confirmation that this Presidential Decree lineage is Greece's
  national transposition instrument (Directive 2009/45/EC recast and
  replaced 98/18/EC; Article 14 of 2009/45/EC provides that references
  to the repealed 98/18/EC 'shall be construed as references to this
  Directive'). A sibling page in the SAME certificate-category branch
  ('Πιστοποιητικά Ασφάλειας -> Solas -> Επιβατηγά', i.e. 'Safety
  Certificates -> SOLAS -> Passenger [ships]') lists IMO SOLAS
  amendment resolutions (MSC.338(91), MSC.344(91), MSC.395(95)),
  confirming this exact ΥΝΑΝΠ branch (Κλάδος Ελέγχου Πλοίων / ΔΙΜΕΚΑΠ) is
  the SOLAS passenger-ship certification authority; enforcement/vessel
  inspection is carried out via the Hellenic Coast Guard (Λιμενικό Σώμα
  - Ελληνική Ακτοφυλακή), which sits under the same Ministry per
  ynanp.gr's own org chart ('Αρχηγείο Λιμενικού Σώματος - Ελληνικής
  Ακτοφυλακής').
  DISCLOSURE: both eur-lex.europa.eu and ynanp.gr/hcg.gr returned
  automated bot-detection challenges to direct fetch this session
  (EUR-Lex: AWS WAF 'x-amzn-waf-action: challenge'; ynanp.gr/hcg.gr:
  Akamai 403). Per this fleet's hard rule against bypassing such
  challenges, both pages above were instead read from Internet Archive
  Wayback Machine snapshots (eur-lex.europa.eu snapshot dated
  2025-11-19; ynanp.gr snapshot dated 2020-08-09/2020-09-21) -- content
  quoted above was read directly from those archived captures, not
  fabricated. MEDIUM-HIGH confidence: the EU Directive text is current
  (EUR-Lex 'in force' status confirmed live via response headers even
  though body content came from the archive); the Greek P.D. citation
  reflects the Ministry's own most recently observed (2020) filing and
  was not independently cross-checked against a live current ynanp.gr
  page or the ΦΕΚ text of Π.Δ. 103/1999 itself (that PDF was not found
  in the Wayback Machine) -- a reviewer with live access to ynanp.gr
  should confirm no newer amending Π.Δ. has since superseded Π.Δ.
  20/2012 in this category.")

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
                              "stability and load-line certificate"]}
   "PHL" {:name "Philippines"
          :owner-authority "Maritime Industry Authority (MARINA)"
          :legal-basis "Republic Act No. 9295 (Domestic Shipping Development Act of 2004) and its Revised Implementing Rules and Regulations -- Passenger Ship Safety Certificate among the required Ship Safety Certificates"
          :provenance "https://marina.gov.ph/wp-content/uploads/2018/06/Revised-IRR-of-RA-9295.pdf"
          :required-evidence ["passenger-ship-safety-certificate"
                              "flag-state registry / vessel record"
                              "life-saving-appliance (LSA) inspection record"
                              "stability and load-line certificate"]}
   "GRC" {:name "Greece"
          :owner-authority "Υπουργείο Ναυτιλίας και Νησιωτικής Πολιτικής (Ministry of Maritime Affairs and Insular Policy, ΥΝΑΝΠ) -- Κλάδος Ελέγχου Πλοίων (Ship Inspection Branch) / ΔΙΜΕΚΑΠ, enforced via the Hellenic Coast Guard (Λιμενικό Σώμα - Ελληνική Ακτοφυλακή)"
          :legal-basis "Π.Δ. 103/1999 'Κανόνες και πρότυπα ασφαλείας για τα επιβατηγά πλοία' (ΦΕΚ Α' 110/1999), transposing Council Directive 98/18/EC -- recast as Directive 2009/45/EC (Annex I Chapter III implements SOLAS Chapter III life-saving appliances; Article 13 requires a Passenger Ship Safety Certificate) -- as amended by Π.Δ. 309/2003 (Α' 261), Π.Δ. 3/2005 (Α' 2), Π.Δ. 66/2005 (Α' 100) and Π.Δ. 20/2012 (Α' 46)"
          :provenance "https://www.ynanp.gr/el/gia-ton-polith/nomo8esia/nomothesia-klados-elenchou-emporikon-ploemporikon-ploion/nomothesia-kep-dieuthynse-meleton-kataskdimekap-/dimekap-syntonismou-katamerteses/dimekap-sk-pistopoietika-asphaleias/dimekap-sk-pistopoietika-asphaleias-koin2009-45/"
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
