(ns ferry.governor
  "Maritime Safety Governor -- the independent compliance layer that
  earns the FerryOperationsAdvisor the right to commit. The LLM has no
  authority over passenger-vessel safety certification, no license to
  clear a vessel to sail, no way to independently know whether a
  sailing's own IMO number actually passes its 7-digit check-digit
  validation, and no way to know when an act stops being a coordination
  draft and becomes something that reads like a real sail-clearance
  decision, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:maritime-safety-governor` (declared
  in this repo's own `blueprint.edn`), matching the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase 0->3
  rollout) established by `cloud-itonami-isic-6511` and applied
  fleet-wide, here as a passenger-ferry OPERATIONS COORDINATION actor --
  NOT a maritime-safety authority and NOT vessel control (see README
  `Scope`).

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks a
  human to look (low confidence / high-stakes), and the human may
  approve -- but see `ferry.phase`: `:flag-maritime-safety-concern` is
  NEVER in any phase's `:auto` set, and no phase ever puts every write
  op in `:auto` either (only `:log-voyage-record`, the no-capital-risk
  op). Two independent layers agree that a maritime-safety concern
  filing is always a human call.

    1. Op not allowed          -- the closed op-allowlist
                                   (`:log-voyage-record` /
                                   `:schedule-sailing-operation` /
                                   `:flag-maritime-safety-concern` /
                                   `:coordinate-maintenance`) is enforced
                                   HERE too, independent of whatever
                                   `ferry.ferryadvisor` itself would ever
                                   route.
    2. Effect not `:propose`   -- every proposal from this actor is a
                                   PROPOSE-only coordination note, never
                                   a real mutation of vessel-control or
                                   safety-authority state. A proposal
                                   whose `:effect` is not literally
                                   `:propose` is a HARD, un-overridable
                                   violation.
    3. Scope-exclusion          -- ANY proposal that reads as directly
       (sail-clearance override)  finalizing a sail-clearance / weather-
                                   hold / maritime-safety override is a
                                   HARD, PERMANENT block -- this actor
                                   structurally never holds that
                                   authority (see `scope-exclusion-
                                   phrases` docstring for the self-trip
                                   pitfall this check is deliberately
                                   phrased to avoid).
    4. Certification incomplete -- for `:schedule-sailing-operation`,
                                   `:flag-maritime-safety-concern` and
                                   `:coordinate-maintenance`, has the
                                   sailing's own vessel/voyage
                                   certification record actually been
                                   independently verified and
                                   registered? (`:log-voyage-record` is
                                   exempt -- it is how the record enters
                                   the system in the first place, the
                                   same discipline `cloud-itonami-isic-
                                   5020`'s `:vessel/intake` exemption
                                   establishes for its own evidence-
                                   incomplete check.)
    5. IMO number invalid       -- for `:schedule-sailing-operation`,
                                   INDEPENDENTLY validate the vessel's
                                   IMO number via `ferry.registry/imo-
                                   number-valid?` (the SAME SOLAS /
                                   IMO A.600(15) check-digit scheme
                                   `cloud-itonami-isic-5020` reapplies,
                                   honestly reapplied here since it
                                   governs every SOLAS-class ship, not
                                   only tankers).
    6. Spec-basis                -- did the `:schedule-sailing-
                                   operation` proposal cite an OFFICIAL
                                   source (`ferry.facts`), or invent one?
    7. Already scheduled         -- refuses to schedule the SAME sailing
                                   twice, off a dedicated `:scheduled?`
                                   fact (never a `:status` value) -- the
                                   SAME 'check a dedicated boolean, not
                                   status' discipline every prior
                                   governor's guards establish."
  (:require [clojure.string :as str]
            [ferry.registry :as registry]
            [ferry.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed op-allowlist. Any request whose `:op` is outside this set
  is a HARD violation, independent of what `ferry.ferryadvisor` itself
  would route it to."
  #{:log-voyage-record :schedule-sailing-operation
    :flag-maritime-safety-concern :coordinate-maintenance})

(def high-stakes
  "Stakes grave enough to always require a human, even when the
  governor is otherwise clean. `:flag-maritime-safety-concern` is the
  one member on purpose: surfacing a seaworthiness / weather / passenger-
  overcrowding concern must always reach a human, per this actor's
  design invariant -- it may never silently auto-commit, even as a mere
  'report was filed' coordination note."
  #{:flag-maritime-safety-concern})

;; ----------------------------- scope-exclusion phrases -----------------------------
;;
;; KNOWN BUG CLASS, fixed here deliberately: a governor's scope-exclusion
;; term list phrased as a bare noun (e.g. "safety", "clearance",
;; "weather-hold") will accidentally match INSIDE this very actor's own
;; default advisor rationale/disclaimer text -- any honest disclaimer
;; explaining what the actor does NOT do ("this does not clear the
;; vessel to sail") necessarily uses the bare nouns it is disclaiming,
;; so a bare-noun scan self-trips on the actor's own happy path. Every
;; phrase below is instead the full FINALIZATION/EXECUTION ACTION
;; (verb + object, not the bare noun) that this actor must never
;; utter as something it is DOING -- and `ferry.ferryadvisor`'s own
;; default rationale/disclaimer text is written to describe the
;; boundary in DIFFERENT words (never restating one of these exact
;; phrases, not even inside a negation), which is the actual fix: see
;; `test/ferry/scope_exclusion_test.cljc`'s regression test asserting
;; every default mock-advisor proposal, across every op and every demo
;; sailing, never trips this check.
(def scope-exclusion-phrases
  #{"finalize the sail-clearance override"
    "finalize the sail clearance override"
    "finalize the maritime-safety override"
    "override the weather-hold"
    "override the weather hold"
    "clear the vessel to sail despite the fault"
    "clear the vessel to sail despite the reported fault"
    "authorize departure despite the reported fault"})

(defn- proposal-text
  "The full scannable text of a proposal -- summary + rationale + the
  printed :value map -- lowercased once for a case-insensitive scan."
  [proposal]
  (str/lower-case (str (:summary proposal) " " (:rationale proposal) " "
                       (pr-str (:value proposal)))))

;; ----------------------------- checks -----------------------------

(defn- op-not-allowed-violations
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " は閉域op許可リストに含まれない -- 提案は進められない")}]))

(defn- effect-not-propose-violations
  "Every proposal from this actor must carry `:effect :propose` -- this
  is a PROPOSE-only coordination actor, never a direct vessel-control /
  safety-authority mutation. Any other `:effect` value is a HARD,
  un-overridable violation, independent of everything else about the
  proposal."
  [_request proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str "提案の:effectは:proposeのみ許容される -- 実際の値: " (pr-str (:effect proposal)))}]))

(defn- scope-exclusion-violations
  "A proposal that reads as directly finalizing a sail-clearance /
  weather-hold / maritime-safety override is a HARD, PERMANENT block --
  this actor structurally never holds that authority. See the
  `scope-exclusion-phrases` docstring for why each phrase is a full
  action, not a bare noun."
  [_request proposal]
  (let [text (proposal-text proposal)]
    (when (some #(str/includes? text %) scope-exclusion-phrases)
      [{:rule :scope-exclusion-sail-clearance
        :detail "提案が出航許可/気象保留の最終決定または解除に該当する表現を含む -- 本アクターは海事安全当局の権限を持たないため永続的にブロックされる"}])))

(defn- certification-incomplete-violations
  "For `:schedule-sailing-operation`, `:flag-maritime-safety-concern` and
  `:coordinate-maintenance`, the sailing's own vessel/voyage
  certification record must actually be independently verified and
  registered -- do not trust the advisor's self-reported confidence
  alone. `:log-voyage-record` is exempt: it is how the record enters the
  system in the first place."
  [{:keys [op subject]} st]
  (when (contains? #{:schedule-sailing-operation :flag-maritime-safety-concern
                     :coordinate-maintenance} op)
    (let [sl (store/sailing st subject)]
      (when-not (and sl (true? (:certification-verified? sl))
                     (:certification-record-id sl)
                     (not= "" (:certification-record-id sl)))
        [{:rule :certification-incomplete
          :detail (str subject " の船舶/航海の証明記録が独立検証・登録されていない状態での提案")}]))))

(defn- imo-number-invalid-violations
  "For `:schedule-sailing-operation`, INDEPENDENTLY validate the
  vessel's IMO number via `ferry.registry/imo-number-valid?` (the SOLAS
  / IMO A.600(15) check-digit scheme, honestly reapplied from
  `cloud-itonami-isic-5020`)."
  [{:keys [op subject]} st]
  (when (= op :schedule-sailing-operation)
    (let [sl (store/sailing st subject)]
      (when (not (registry/imo-number-valid? (:vessel-imo sl)))
        [{:rule :imo-number-invalid
          :detail (str subject " のIMO番号(" (:vessel-imo sl)
                      ")は7桁検査数字検証に失敗 -- 構造的に無効な船籍識別のためスケジュール提案は進められない")}]))))

(defn- spec-basis-violations
  "A `:schedule-sailing-operation` proposal with no spec-basis citation
  is a HARD violation -- never invent a jurisdiction's passenger-vessel
  safety-certification requirements."
  [{:keys [op]} proposal]
  (when (= op :schedule-sailing-operation)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- already-scheduled-violations
  "For `:schedule-sailing-operation`, refuses to schedule the SAME
  sailing twice, off a dedicated `:scheduled?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :schedule-sailing-operation)
    (when (store/sailing-already-scheduled? st subject)
      [{:rule :already-scheduled
        :detail (str subject " は既にスケジュール登録済み")}])))

(defn check
  "Censors a FerryOperationsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (scope-exclusion-violations request proposal)
                           (certification-incomplete-violations request st)
                           (imo-number-invalid-violations request st)
                           (spec-basis-violations request proposal)
                           (already-scheduled-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
