(ns ferry.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave0 Lane A): this repo previously had NO build-time operator-console
  generator. This namespace drives the REAL actor stack
  (`ferry.operation` -> `ferry.governor` -> `ferry.store`) through a
  scenario adapted from this repo's own `ferry.sim` demo driver
  (`clojure -M:dev:run`, confirmed BEFORE writing this file to produce
  a sensible ledger against the real seeded sailing ids `sail-1`..
  `sail-6` -- ids DO match `ferry.store/demo-data`, so it was safe to
  reuse rather than author from scratch), trimmed to a representative
  subset (one full log->schedule->concern->maintenance lifecycle, and
  four distinct HARD-hold reasons) and rendered deterministically --
  no invented numbers, no timestamps in the page content, byte-
  identical across reruns against the same seed (verify by diffing two
  consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [ferry.store :as store]
            [ferry.operation :as op]
            [ferry.phase :as phase]
            [ferry.governor :as governor]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :ferry-operations-dispatcher :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every
  disposition this actor can reach: sail-1 clears a full lifecycle --
  voyage-record log (auto-commit clean at phase 3, no capital risk), a
  sailing-schedule coordination (phase-gated -- not auto-eligible --
  approved), a maritime-safety-concern filing (ALWAYS escalates --
  permanently high-stakes, never auto at any phase -- approved) and a
  maintenance coordination (approved); sail-2 HARD-holds a schedule
  proposal with no official spec-basis for its (deliberately
  unregistered) jurisdiction ATL; sail-3 HARD-holds on an invalid IMO
  check digit; sail-4 HARD-holds on incomplete certification for
  schedule (and is also exercised for concern + maintenance so the
  certification-incomplete rule is not only via happy-path actuation);
  sail-5 HARD-holds on already-scheduled. Every HARD hold never reaches
  a human. Returns the resulting store -- every field read by `render`
  below is real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :log-voyage-record :subject "sail-1"
                       :patch {:id "sail-1" :passenger-manifest-count 118}})

    (exec! actor "t2" {:op :schedule-sailing-operation :subject "sail-1"})
    (approve! actor "t2")

    (exec! actor "t3" {:op :flag-maritime-safety-concern :subject "sail-1"})
    (approve! actor "t3")

    (exec! actor "t4" {:op :coordinate-maintenance :subject "sail-1"})
    (approve! actor "t4")

    (exec! actor "t5" {:op :schedule-sailing-operation :subject "sail-2"})

    (exec! actor "t6" {:op :schedule-sailing-operation :subject "sail-3"})

    (exec! actor "t7" {:op :schedule-sailing-operation :subject "sail-4"})

    (exec! actor "t8" {:op :flag-maritime-safety-concern :subject "sail-4"})

    (exec! actor "t9" {:op :coordinate-maintenance :subject "sail-4"})

    (exec! actor "t10" {:op :schedule-sailing-operation :subject "sail-5"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger sailing-id]
  (last (filter #(= (:subject %) sailing-id) ledger)))

(defn- status-cell [ledger sailing-id]
  (let [f (last-fact-for ledger sailing-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [{:keys [scheduled? maintenance-coordinated?]}]
  (cond
    (and scheduled? maintenance-coordinated?)
    "<span class=\"ok\">scheduled &amp; maintenance coordinated</span>"
    scheduled? "<span class=\"warn\">scheduled, maintenance open</span>"
    maintenance-coordinated? "<span class=\"warn\">maintenance only</span>"
    :else "<span class=\"muted\">intake</span>"))

(defn- sailing-row [ledger {:keys [id vessel-name route jurisdiction vessel-imo] :as sl}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc vessel-name) (esc route) (esc jurisdiction) (esc vessel-imo)
          (lifecycle-cell sl)
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops`, `ferry.governor`/`ferry.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-voyage-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk</span></td></tr>"
   "        <tr><td><code>:schedule-sailing-operation</code></td><td><span class=\"warn\">phase-3: human approval (not auto-eligible) · HARD on cert/IMO/spec-basis/already-scheduled</span></td></tr>"
   "        <tr><td><code>:flag-maritime-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase &middot; high-stakes</span></td></tr>"
   "        <tr><td><code>:coordinate-maintenance</code></td><td><span class=\"warn\">phase-3: human approval · HARD on certification-incomplete</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        sailings (store/all-sailings db)
        sailing-rows (str/join "\n" (map (partial sailing-row ledger) sailings))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        hard-holds (count (filter #(= :governor-hold (:t %)) ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-5011 &middot; passenger-ferry-operations</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Sea and coastal passenger water transport (ISIC 5011) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · concern filing always human-approved · phase "
     phase/default-phase " (" (esc (:label (get phase/phases phase/default-phase))) ")</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Sailings</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>ferry.store</code> via <code>ferry.render-html</code> (<code>clojure -M:dev:render-html</code>). HARD holds this run: "
     hard-holds ".</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Sailing</th><th>Vessel</th><th>Route</th><th>Jurisdiction</th><th>IMO</th><th>Lifecycle</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     sailing-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Maritime Safety Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Sail-clearance / weather-hold override language is a permanent scope exclusion. Certification and IMO are re-checked at the point of the act, never trusted from the proposal alone. High-stakes ops: "
     (esc (str/join ", " (map name governor/high-stakes))) ".</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Sailing</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        parent (.getParentFile (java.io.File. out))]
    (when parent (.mkdirs parent))
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/schedule-history db)) "schedules,"
             (count (store/concern-history db)) "concerns,"
             (count (store/maintenance-history db)) "maintenances,"
             (count (filter #(= :governor-hold (:t %)) (store/ledger db))) "HARD holds )")))
