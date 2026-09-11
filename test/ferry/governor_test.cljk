(ns ferry.governor-test
  "Direct unit tests of the pure `ferry.governor/check` function --
  including the two defense-in-depth checks (`:op-not-allowed`,
  `:effect-not-propose`) and the scope-exclusion permanent block that a
  well-behaved `ferry.ferryadvisor` proposal would never itself
  construct, so they are exercised here with hand-crafted proposals
  rather than via the full `ferry.operation` graph (see
  `governor_contract_test.cljc` for the graph-level flows, and
  `scope_exclusion_test.cljc` for the dedicated regression proving the
  DEFAULT mock advisor never trips the scope-exclusion check)."
  (:require [clojure.test :refer [deftest is testing]]
            [ferry.governor :as governor]
            [ferry.store :as store]))

(defn- clean-proposal []
  {:summary "スケジュール調整案" :rationale "imo-valid?=true certification-verified?=true"
   :cites ["cite-1"] :effect :propose :value {} :stake nil :confidence 0.9})

(deftest op-not-allowed-is-hard-blocked-and-unoverridable
  (testing "a request whose :op is outside the closed allowlist -> HOLD, independent of the advisor"
    (let [db (store/seed-db)
          verdict (governor/check {:op :clear-vessel-to-sail :subject "sail-1"} {}
                                  (clean-proposal) db)]
      (is (:hard? verdict))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard-blocked-and-unoverridable
  (testing "a proposal whose :effect is not literally :propose -> HOLD, no matter how clean everything else is"
    (let [db (store/seed-db)
          verdict (governor/check {:op :log-voyage-record :subject "sail-1"} {}
                                  (assoc (clean-proposal) :effect :vessel/clear-to-sail)
                                  db)]
      (is (:hard? verdict))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest scope-exclusion-phrase-is-hard-blocked-and-permanent
  (testing "every scope-exclusion phrase, if it ever appeared in a proposal's rationale, is a HARD, permanent block"
    (let [db (store/seed-db)]
      (doseq [phrase governor/scope-exclusion-phrases]
        (let [verdict (governor/check {:op :schedule-sailing-operation :subject "sail-1"} {}
                                      (assoc (clean-proposal) :rationale
                                             (str "検討の結果、" phrase " を行う。"))
                                      db)]
          (is (:hard? verdict) (str "phrase should hard-block: " phrase))
          (is (some #{:scope-exclusion-sail-clearance} (map :rule (:violations verdict)))
              (str "phrase should trip :scope-exclusion-sail-clearance: " phrase)))))))

(deftest scope-exclusion-also-scans-summary-and-value
  (testing "the scan covers :summary and the printed :value too, not only :rationale"
    (let [db (store/seed-db)
          phrase (first governor/scope-exclusion-phrases)]
      (is (:hard? (governor/check {:op :schedule-sailing-operation :subject "sail-1"} {}
                                  (assoc (clean-proposal) :summary phrase) db)))
      (is (:hard? (governor/check {:op :schedule-sailing-operation :subject "sail-1"} {}
                                  (assoc (clean-proposal) :value {:note phrase}) db))))))

(deftest a-clean-proposal-with-no-scope-exclusion-phrase-is-not-blocked-by-it
  (testing "sanity: the check does not fire on unrelated text (not a vacuous always-block)"
    (let [db (store/seed-db)
          verdict (governor/check {:op :schedule-sailing-operation :subject "sail-1"} {}
                                  (assoc (clean-proposal) :cites ["船舶安全法" "https://www.mlit.go.jp/common/001383958.pdf"])
                                  db)]
      (is (not (some #{:scope-exclusion-sail-clearance} (map :rule (:violations verdict))))))))
