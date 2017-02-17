(ns achtungonline.server
  (:require
    [org.httpkit.server :refer :all]
    [compojure.core :refer :all]
    [clojure.data.json :as json]
    [achtungonline.test.core :refer [is is= is-not]]
    [achtungonline.lobbies-state :as ls]
    [achtungonline.lobbies-core :as lc]))

(defn
  ^{:doc  "Add a client to the connected clients list"
    :test (fn []
            (is= (create-server-state)
                 {:connected-clients []
                  :counter           0})
            (is= (create-server-state :counter 5)
                 {:connected-clients []
                  :counter           5}))}
  create-server-state [& kvs]
  (let [state {:connected-clients []
               :counter           0}]
    (reduce (fn [s [key val]] (assoc s key val)) state (partition 2 kvs))))

(defn
  ^{:doc  "Returns the next id and an updates a counter"
    :test (fn []
            (is= (get-next-client-id (create-server-state))
                 [(create-server-state :counter 1)
                  "client_0"]))}
  get-next-client-id [server-state]
  [(assoc server-state :counter (inc (:counter server-state))) (str "client_" (:counter server-state))])

(defn
  ^{:doc  "Add a client to the connected clients list"
    :test (fn []
            (is= (add-connected-client (create-server-state) "mocked channel")
                 {:connected-clients [{:id      "client_0"
                                       :channel "mocked channel"}]
                  :counter           1}))}
  add-connected-client [server-state channel]
  (let [[server-state id] (get-next-client-id server-state)]
    (update server-state :connected-clients conj {:id id :channel channel})))

(defn
  ^{:doc  "Takes a channel and returns the id connected to the channel"
    :test (fn []
            (is= (channel->id {:connected-clients [{:id 2 :channel "mocked channel"}]}
                              "mocked channel")
                 2))
    }
  channel->id [server-state channel]
  (some #(when (= channel (:channel %)) (:id %))
        (:connected-clients server-state)))


(defonce lobbies-state-atom (atom (ls/create-state)))

(defonce server-state-atom (atom (create-server-state)))

(defonce server-atom (atom nil))


(defn player-ready []
  (println "player-ready function"))

(defn player-color-change []
  (println "player-color-change function"))

(defn player-disconnect []
  (println "player-disconnect function"))

(defn player-leave []
  (println "player-leave function"))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (create-request-json "enter" {:value "someValue"})
                 "{\"type\":\"enter\",\"value\":\"someValue\"}")
            (is= (create-request-json "enter")
                 "{\"type\":\"enter\"}"))}

  create-request-json
  ([type] (create-request-json type {}))
  ([type data]
   (-> (merge {:type type} data)
       (json/write-str))))

(defn enter-lobby! [channel data]
  (do
    (lc/player-enter-lobby @lobbies-state-atom (channel->id @server-state-atom channel) (get data "playerName"))
    (send! channel (create-request-json "lobbyEntered"))))

(defn server [req]
  (with-channel req channel                                 ; get the channel
                ;; communicate with client using method defined above
                ;(inc-next-id server-state)
                (swap! server-state-atom add-connected-client channel)
                ;(send! channel (json/write-str {"type" "id" "id" (channel->id @server-state-atom channel)}))
                (on-receive channel (fn [data]              ; data received from client
                                      ;; An optional param can pass to send!: close-after-send?
                                      ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
                                      ;; and false for WebSocket.  (send! channel data close-after-send?)
                                      (let [data-obj (json/read-str data)
                                            data-type (get data-obj "type")]
                                        (cond
                                          (= data-type "ready") (player-ready)
                                          (= data-type "color_change") (player-color-change)
                                          (= data-type "enter_lobby") (enter-lobby! channel data)
                                          (= data-type "player-disconnect") (player-disconnect)
                                          (= data-type "player-leave") (player-leave)
                                          :else (println data-obj))
                                        )))
                (on-close channel (fn [status]
                                    (println "channel closed"))))) ; data is sent directly to the client

(defn start! []
  (reset! server-atom (run-server server {:port 3000})))

(defn stop! []
  (when-not (nil? @server-atom)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    (@server-atom :timeout 100)
    (reset! server-atom nil)))

(defn restart! []
  (stop!)
  (start!))