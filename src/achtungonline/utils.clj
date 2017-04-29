(ns achtungonline.utils
  (:require
    [ysera.test :refer [is= is is-not]]))

(defn only-duplicates
  {:test (fn []
           (is= (only-duplicates [1 2 1])
                     [1])
           (is= (only-duplicates [1 2])
                []))}
  [seq]
  (for [[id freq] (frequencies seq)  ;; get the frequencies, destructure
        :when (> freq 1)]            ;; this is the filter condition
    id))