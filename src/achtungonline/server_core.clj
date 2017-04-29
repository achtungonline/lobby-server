(ns achtungonline.server_core
  (:require
    [clojure.string :refer [upper-case]]
    [org.httpkit.server :refer :all]
    [compojure.core :refer :all]
    [clojure.data.json :as json]
    [ysera.test :refer [is= is is-not]]
    [achtungonline.lobbies-core :as lc]
    [aleph.tcp :as tcp]
    [clojure.edn :as edn]
    [clojure.java.io :as io])
  (:import (io.netty.handler.codec.json JsonObjectDecoder)))

(import [java.net ServerSocket])

(defn create-state
  {:test (fn []
           (is= (create-state)
                {:connected-clients  []
                 :counter            0})
           (is= (:counter (create-state :counter 5))
                5))}
  [& kvs]
  (let [state {:connected-clients  []
               :counter            0}]
    (reduce (fn [s [key val]] (assoc s key val)) state (partition 2 kvs))))

(defn get-next-client-id
  {:doc  "Returns the next id and an updates a counter"
   :test (fn []
           (is= (get-next-client-id (create-state))
                [(create-state :counter 1)
                 "client_0"]))}
  [state]
  [(assoc state :counter (inc (:counter state))) (str "client_" (:counter state))])

(defn channel->client-id
  {:doc  "Takes a channel and returns the id connected to the channel"
   :test (fn []
           (is= (channel->client-id {:connected-clients [{:id 2 :channel "mocked channel"}]}
                                    "mocked channel")
                2))
   }
  [state channel]
  (some #(when (= channel (:channel %)) (:id %))
        (:connected-clients state)))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (client-id->channel {:connected-clients [{:id 2 :channel "mocked channel"}]}
                                     2)
                 "mocked channel"))
    }
  client-id->channel [server-state player-id]
  (some #(when (= player-id (:id %)) (:channel %))
        (:connected-clients server-state)))

(defn add-client
  {:doc  "Add a client to the connected clients list"
   :test (fn []
           (is= (-> (create-state)
                    (add-client "mocked channel")
                    (select-keys [:connected-clients :counter]))
                {:connected-clients [{:id      "client_0"
                                      :channel "mocked channel"}]
                 :counter           1}))}
  [state channel]
  (let [[state id] (get-next-client-id state)]
    (update state :connected-clients conj {:id id :channel channel})))

(defn remove-client
  {:doc  "Removes a client from the connected clients list"
   :test (fn []
           (is= (-> (create-state :connected-clients [{:id "client_0"} {:id "client_1"}])
                    (remove-client "client_0")
                    (:connected-clients))
                [{:id "client_1"}]))}
  [state client-id]
  (update state :connected-clients (fn [connected-clients]
                                     (filter (fn [client]
                                               (not= (:id client) client-id))
                                       connected-clients))))

(defn create-request-json
  {:test (fn []
           (is= (create-request-json "enter" {:player-name "someValue"})
                "{\"type\":\"enter\",\"playerName\":\"someValue\"}")
           (is= (create-request-json "enter")
                "{\"type\":\"enter\"}"))}
  ([type] (create-request-json type {}))
  ([type data]
   (-> (merge {:type type} data)
       (json/write-str :key-fn (fn [key]
                                 (-> key
                                     (clojure.string/replace #"(-)(.)" (fn [a] (upper-case (get a 2))))
                                     (clojure.string/replace #":" "")))))))








