(ns frereth-web.system-test
  (:require [clojure.test :as test :refer (is testing)]
            [com.frereth.web.system :refer :all]
            [schema.core :as s]))

(test/deftest schema-extractors
  []
  (testing "Minimalist schema loading"
    (let [descr {'one '[schema-a schema-b]
                 'two 'schema-a}]
      (require-schematic-namespaces! descr)
      (is (= (load-var 'one 'schema-a)
             {:a s/Int, :b s/Int}))
      (testing "Schema description merge"
        ;; The order in which these are returned really is
        ;; just an implementation detail
        ;; For now, this approach makes the test quite a bit simpler
        (is (= (extract-schema descr)
               [{:a s/Int, :b s/Int}
                {:z s/Str, :y s/Keyword}
                {:a s/Str :z s/Int}]))))))
