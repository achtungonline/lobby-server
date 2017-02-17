;(ns achtungonline.test.core
;  (:require #?(:clj [clojure.test])))
;
;#?(:clj  (defmacro deftest [name & body]
;           (concat (list 'clojure.test/deftest name) body))
;   :cljs (defn deftest [name & body]
;               (println "Not implemented.")))
;
;#?(:clj  (defmacro is
;           ([form]
;            (clojure.test/is form))
;           ([form msg]
;            (clojure.test/is form msg)))
;   :cljs (defn is
;               ([form]
;                 (println "Not implemented."))
;               ([form msg]
;                 (println "Not implemented."))))
;
;#?(:clj  (defmacro is= [actual expected]
;           `(let [actual# ~actual
;                  expected# ~expected
;                  equal# (= actual# expected#)]
;              (do
;                (when-not equal#
;                  (println "Actual:\t\t" actual# "\nExpected:\t" expected#))
;                (clojure.test/is (= actual# expected#)))))
;   :cljs (defn is= [actual expected]
;               (println "Not implemented.")))
;
;#?(:clj  (defmacro is-not [actual]
;           `(clojure.test/is (not ~actual)))
;   :cljs (defn is-not [actual]
;               (println "Not implemented.")))
;
;#?(:clj  (defmacro error? [actual]
;           `(try (do
;                   ~actual
;                   (println "An error was expected.")
;                   (clojure.test/is false))
;                 (catch #?(:clj Exception :cljs js/Object) e#
;                   (clojure.test/is true))))
;   :cljs (defn error? [actual]
;               (println "Not implemented.")))
;
