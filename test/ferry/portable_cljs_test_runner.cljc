(ns ferry.portable-cljs-test-runner
  "PRIMARY automated quality gate for this actor under a real
  ClojureScript host (cljs.main --target node) -- the runtime-priority
  rule this workspace's CLAUDE.md and skill `build-actor` establish:

      kotoba wasm runtime  >  clojurewasm  >  ClojureScript  >  nbb
      (JVM / babashka are last-resort compat, not the design target)

  The whole test suite is portable .cljc and runs UNCHANGED here and on
  the JVM (`clojure -M:dev:test`, secondary compat gate). This includes
  `ferry.store-contract-test`, which exercises the langchain.db
  Datomic-API-compatible store -- the kotoba-server / kotobase datom
  seam -- under ClojureScript.

  Invoke from the repo root (the :test alias's :main-opts would steal
  -m if combined, hence -Sdeps for the extra path):

    clojure -Sdeps '{:paths [\"src\" \"test\"]}' \\
      -M:dev:cljs -m cljs.main --target node \\
      -m ferry.portable-cljs-test-runner"
  (:require [clojure.test :as t :refer [run-tests]]
            [ferry.facts-test]
            [ferry.governor-test]
            [ferry.governor-contract-test]
            [ferry.phase-test]
            [ferry.registry-test]
            [ferry.scope-exclusion-test]
            [ferry.store-contract-test]))

#?(:cljs
   (defmethod t/report [:cljs.test/default :end-run-tests] [m]
     (when-not (t/successful? m)
       (set! (.-exitCode js/process) 1))))

(defn -main []
  (run-tests 'ferry.facts-test
             'ferry.registry-test
             'ferry.phase-test
             'ferry.governor-test
             'ferry.governor-contract-test
             'ferry.scope-exclusion-test
             'ferry.store-contract-test))
