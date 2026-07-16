(ns ferry.registry
  "Pure-function sailing-schedule / concern-filing / maintenance-
  coordination record construction -- an append-only ferry-operations
  book-of-record draft -- AND the pure structural check the Maritime
  Safety Governor calls to re-verify a sailing's own vessel identity
  before any sailing-schedule proposal.

  This is a COORDINATION actor, not a maritime-safety authority: every
  record built here is a DRAFT coordination note (`register-*`), never a
  sail-clearance, a resolved safety determination, or a real dispatch.
  See README `Actuation` -- the actor may draft, log and recommend; the
  vessel master and the flag-state / port-state maritime-safety
  authority retain sole authority to clear a vessel to sail.

  `imo-number-valid?` reapplies `cloud-itonami-isic-5020` (`tanker.
  registry`)'s honest reapplication of the SOLAS / IMO resolution
  A.600(15) seven-digit check-digit scheme -- the scheme applies to
  EVERY SOLAS-class ship's IMO Ship Identification Number, not only
  tankers, so reapplying it to a passenger vessel's own IMO number is
  the SAME 'reuse a validated structural check' discipline, not a new
  invention.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a sailing-schedule, concern-filing or
  maintenance-coordination record -- every operator/jurisdiction assigns
  its own reference format. This namespace does NOT invent one beyond a
  jurisdiction-scoped (or global, for concern/maintenance) sequence
  number; it validates the record's required fields, the same honest,
  non-fabricating discipline `ferry.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network call
  to any real AIS / VTS / vessel-management system. It builds the RECORD
  an operator would keep, not the act of scheduling a real sailing,
  filing a real safety concern, or coordinating real maintenance itself
  (that is `ferry.operation`'s governed ops, always human-gated for
  anything beyond routine voyage-record logging -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature (i.e.
  an actual sail-clearance, concern resolution, or maintenance release)
  is the vessel master's / maritime-safety authority's act, never this
  actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- vessel-identity structural check (pure) -----------------------------
;;
;; The Maritime Safety Governor calls this to INDEPENDENTLY re-verify a
;; sailing's own recorded vessel IMO number before authorizing a
;; sailing-schedule proposal. Returns true only when the value is
;; provably a structurally valid IMO Ship Identification Number -- the
;; conservative discipline every sibling governor's re-verification
;; functions establish: missing/malformed data is treated as invalid,
;; never as "unknown therefore ok".

(defn- imo-digits
  "The 7 integer digits of an IMO number string, or nil if it is not
  exactly 7 base-10 digits. Portable across JVM / cljs (single-char
  ASCII digit arithmetic, no platform parseInt)."
  [s]
  (when (and (string? s) (= 7 (count s)) (re-find #"^[0-9]{7}$" s))
    (mapv #(- (int %) (int \0)) s)))

(defn imo-number-valid?
  "IMO ship identification number structural validation -- the SOLAS /
  IMO resolution A.600(15) seven-digit check-digit scheme, which applies
  to every SOLAS-class ship (including passenger vessels), not only
  tankers. The first six digits are multiplied by weights 7, 6, 5, 4, 3,
  2 (left to right); the units digit of the sum MUST equal the 7th
  (check) digit. Pure (no I/O), portable (.cljc). Returns false for any
  non-7-digit / non-numeric / wrong-check-digit input; the governor
  treats false as a HARD `:imo-number-invalid` hold."
  [imo]
  (boolean
   (when-let [ds (imo-digits (str imo))]
     (let [weights [7 6 5 4 3 2]
           chk (mod (reduce + (map * (take 6 ds) weights)) 10)]
       (= chk (nth ds 6))))))

;; ----------------------------- record construction -----------------------------

(defn register-schedule-record
  "Validate + construct the SAILING-SCHEDULE registration DRAFT -- the
  operator's own coordination note proposing a scheduled sailing. Pure
  function -- does not touch any real AIS / VTS / berth-management
  system, and does NOT clear the vessel to sail; it builds the RECORD an
  operator would keep. `ferry.governor` independently re-verifies the
  sailing's own vessel-IMO and certification ground truth, and blocks a
  double-schedule of the same sailing, before this is ever allowed to
  commit."
  [sailing-id jurisdiction sequence]
  (when-not (and sailing-id (not= sailing-id ""))
    (throw (ex-info "schedule-sailing-operation: sailing_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "schedule-sailing-operation: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "schedule-sailing-operation: sequence must be >= 0" {})))
  (let [schedule-number (str (str/upper-case jurisdiction) "-SCHEDULE-" (zero-pad sequence 6))
        record {"record_id" schedule-number
                "kind" "sailing-schedule-draft"
                "sailing_id" sailing-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "schedule_number" schedule-number
     "certificate" (unsigned-certificate "SailingScheduleCoordinationNote" schedule-number schedule-number)}))

(defn register-concern-record
  "Validate + construct the MARITIME-SAFETY-CONCERN filing DRAFT -- a
  coordination note surfacing a reported seaworthiness fault, weather
  hold, or overcrowding concern for human sign-off. Pure function --
  this is a REPORT, never a resolution, override, or clearance: it does
  not (and structurally cannot) clear the concern or authorize sailing
  despite it. `ferry.governor` ALWAYS routes this to human approval --
  see README `Actuation`."
  [sailing-id sequence]
  (when-not (and sailing-id (not= sailing-id ""))
    (throw (ex-info "flag-maritime-safety-concern: sailing_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "flag-maritime-safety-concern: sequence must be >= 0" {})))
  (let [concern-number (str "CONCERN-" (zero-pad sequence 6))
        record {"record_id" concern-number
                "kind" "maritime-safety-concern-report"
                "sailing_id" sailing-id
                "immutable" true}]
    {"record" record "concern_number" concern-number
     "certificate" (unsigned-certificate "MaritimeSafetyConcernReport" concern-number concern-number)}))

(defn register-maintenance-record
  "Validate + construct the VESSEL-MAINTENANCE-COORDINATION DRAFT -- a
  coordination note proposing a maintenance window for the vessel. Pure
  function -- does not touch any real maintenance-management system and
  does not itself release the vessel from or into service; it builds the
  RECORD an operator would keep."
  [sailing-id sequence]
  (when-not (and sailing-id (not= sailing-id ""))
    (throw (ex-info "coordinate-maintenance: sailing_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "coordinate-maintenance: sequence must be >= 0" {})))
  (let [maint-number (str "MAINT-" (zero-pad sequence 6))
        record {"record_id" maint-number
                "kind" "maintenance-coordination-draft"
                "sailing_id" sailing-id
                "immutable" true}]
    {"record" record "maintenance_number" maint-number
     "certificate" (unsigned-certificate "MaintenanceCoordinationNote" maint-number maint-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
