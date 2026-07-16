(ns ferry.ferryadvisor
  "FerryOperationsAdvisor client -- the *contained intelligence node* for
  the passenger-ferry operations-coordination actor.

  It normalizes voyage-record intake, drafts a per-jurisdiction sailing-
  schedule coordination proposal, drafts a maritime-safety-concern
  filing, and drafts a maintenance-coordination proposal. CRITICAL: it
  is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record and NEVER
  a sail-clearance decision. Every output is censored downstream by
  `ferry.governor` before anything touches the SSoT.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  IMPORTANT (the self-trip pitfall, see `ferry.governor`'s
  `scope-exclusion-phrases` docstring): every rationale/disclaimer
  string below that describes what this actor does NOT do is
  deliberately phrased in DIFFERENT words than
  `ferry.governor/scope-exclusion-phrases` -- never restating one of
  those exact action-phrases, not even inside a negation ('this does
  not X' still literally contains the substring X). `test/ferry/
  scope_exclusion_test.cljc` is the regression test that would catch a
  future edit that reintroduces the collision.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the governor's checks
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- this actor never
                                 ; issues a real mutation effect
     :stake      kw|nil         ; :flag-maritime-safety-concern | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
           [clojure.string :as str]
           [ferry.facts :as facts]
           [ferry.registry :as registry]
           [ferry.store :as store]
           [langchain.model :as model]))

(defn- normalize-intake
  "Voyage-record directory upsert -- the LLM only normalizes/validates
  the patch; it does not invent the vessel IMO, certification record,
  manifest count, or jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "航海記録更新: " (pr-str (keys patch)))
   :rationale  "入力patchの正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :propose
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- propose-schedule
  "Draft the SAILING-SCHEDULE coordination proposal. Reads the
  jurisdiction's official spec-basis and the sailing's own recorded
  vessel-IMO / certification / fault / weather-hold fields to draft a
  readiness rationale -- but this is a COORDINATION NOTE, never a
  sail-clearance: the closing sentence of every rationale below
  attributes departure-clearance judgment to the vessel master and the
  maritime-safety authority, in wording distinct from `ferry.governor/
  scope-exclusion-phrases` (see this namespace's docstring)."
  [db {:keys [subject]}]
  (let [sl (store/sailing db subject)
        iso3 (:jurisdiction sl)
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "ferry.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :propose
       :value      {:jurisdiction iso3 :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      (let [imo-ok? (and sl (registry/imo-number-valid? (:vessel-imo sl)))
            cert-ok? (and sl (true? (:certification-verified? sl))
                          (:certification-record-id sl) (not= "" (:certification-record-id sl)))
            fault? (boolean (and sl (:seaworthiness-fault-reported? sl)))
            hold? (boolean (and sl (:weather-hold-active? sl)))
            clean? (and imo-ok? cert-ok? (not fault?) (not hold?))]
        {:summary    (str subject " 向け運航スケジュール調整案 (" (:route sl) ")")
         :rationale  (str "imo-valid?=" imo-ok? " certification-verified?=" cert-ok?
                          " fault-reported?=" fault? " weather-hold-active?=" hold?
                          (when-not clean?
                            " -- 未解消の情報があるため人による確認を推奨する")
                          " -- 本提案は運航スケジュールの調整案に過ぎず、"
                          "出航に関する最終判断は船長および海事安全当局の権限である。")
         :cites      [(:legal-basis sb) (:provenance sb) subject]
         :effect     :propose
         :value      {:jurisdiction iso3 :spec-basis (:provenance sb) :legal-basis (:legal-basis sb)
                      :scheduled-departure (:scheduled-departure sl)}
         :stake      nil
         :confidence (if clean? 0.9 0.4)}))))

(defn- propose-concern
  "Draft the MARITIME-SAFETY-CONCERN filing proposal -- surfacing a
  reported seaworthiness fault, weather hold, or passenger-overcrowding
  observation from the sailing's own recorded fields. ALWAYS `:stake
  :flag-maritime-safety-concern` -- this ALWAYS escalates to human
  sign-off (`ferry.governor/high-stakes` and `ferry.phase` both agree,
  deliberately). This is a REPORT, never a resolution: it does not (and
  cannot) clear the concern or authorize departure despite it."
  [db {:keys [subject]}]
  (let [sl (store/sailing db subject)
        fault? (boolean (and sl (:seaworthiness-fault-reported? sl)))
        hold? (boolean (and sl (:weather-hold-active? sl)))]
    {:summary    (str subject " 向け海事安全懸念の提起"
                      (when sl (str " (fault=" fault? ", weather-hold=" hold? ")")))
     :rationale  (if sl
                   "船舶記録に基づき懸念を提起する。本提案は報告のみであり、懸念の解消または出航の可否判断は行わない -- それは船長および海事安全当局の権限である。"
                   "sailingが見つかりません")
     :cites      (if sl [subject] [])
     :effect     :propose
     :value      {:sailing-id subject :fault-reported? fault? :weather-hold-active? hold?}
     :stake      :flag-maritime-safety-concern
     :confidence (if sl 0.85 0.3)}))

(defn- propose-maintenance
  "Draft the VESSEL-MAINTENANCE-COORDINATION proposal -- scheduling a
  maintenance window for the vessel. Never itself releases the vessel
  from or into service."
  [db {:keys [subject]}]
  (let [sl (store/sailing db subject)]
    {:summary    (str subject " 向け整備ウィンドウ調整案"
                      (when sl (str " (vessel=" (:vessel-name sl) ")")))
     :rationale  (if sl
                   "船舶記録に基づき整備ウィンドウの調整を提案する。整備完了の判定および運航再開の可否判断は行わない。"
                   "sailingが見つかりません")
     :cites      (if sl [subject] [])
     :effect     :propose
     :value      {:sailing-id subject}
     :stake      nil
     :confidence (if sl 0.8 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :log-voyage-record            (normalize-intake db request)
    :schedule-sailing-operation   (propose-schedule db request)
    :flag-maritime-safety-concern (propose-concern db request)
    :coordinate-maintenance       (propose-maintenance db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :propose :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域旅客フェリー事業者の運航調整エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(常に:propose -- 実際の運航や安全上の決定を行う値は絶対に返さない) "
       ":stake(:flag-maritime-safety-concern か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の旅客船安全証明要件を絶対に創作してはいけません。"
       "IMO番号・証明記録の検証状態・不具合報告・気象保留の状態を偽って報告してはいけません。"
       "あなたには出航可否を最終決定する権限が無く、その権限を持つかのような提案を返してはいけません -- "
       "最終判断は常に船長および海事安全当局が行います。"))

(defn- facts-for [st {:keys [subject]}]
  {:sailing (store/sailing st subject)})

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Maritime Safety Governor
  escalates/holds -- an LLM hiccup can never auto-commit a coordination
  proposal, let alone anything resembling a sail-clearance decision."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (assoc :effect :propose))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :propose :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :ferryadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
