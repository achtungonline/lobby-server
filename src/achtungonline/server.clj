(ns achtungonline.server
  (:require
    [clojure.string :refer [upper-case]]
    [org.httpkit.server :refer :all]
    [compojure.core :refer :all]
    [clojure.data.json :as json]
    [achtungonline.test.core :refer [is is= is-not]]
    [achtungonline.lobbies-state :as ls]
    [achtungonline.lobbies-core :as lc]
    [aleph.tcp :as tcp]
    [clojure.edn :as edn]
    [clojure.java.io :as io])
  (:import (io.netty.handler.codec.json JsonObjectDecoder)))

(import [java.net ServerSocket])

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
            (is= (channel->player->id {:connected-clients [{:id 2 :channel "mocked channel"}]}
                                      "mocked channel")
                 2))
    }
  channel->player->id [server-state channel]
  (some #(when (= channel (:channel %)) (:id %))
        (:connected-clients server-state)))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (player-id->channel {:connected-clients [{:id 2 :channel "mocked channel"}]}
                                     2)
                 "mocked channel"))
    }
  player-id->channel [server-state player-id]
  (some #(when (= player-id (:id %)) (:channel %))
        (:connected-clients server-state)))




(defonce lobbies-state-atom (atom (ls/create-state)))

(defonce server-state-atom (atom (create-server-state)))

(defonce server-atom (atom nil))


(defn player-disconnect []
  (println "player-disconnect function"))

(defn
  ^{
    :doc  ""
    :test (fn []
            (is= (get-channels-connected-to-same-lobby (create-server-state :connected-clients [{:id "1" :channel "channel_1"}
                                                                                                {:id "2" :channel "channel_2"}
                                                                                                {:id "3" :channel "channel_3"}])
                                                       (ls/create-state :lobbies [{:players [{:id "1"} {:id "2"}]}])
                                                       "2")
                 ["channel_1" "channel_2"])
            (is= (get-channels-connected-to-same-lobby (create-server-state :connected-clients [{:id "1" :channel "channel_1"}
                                                                                                {:id "2" :channel "channel_2"}
                                                                                                {:id "3" :channel "channel_3"}])
                                                       (ls/create-state :lobbies [{:id "3" :players [{:id "1"} {:id "2"}]}])
                                                       "3")
                 ["channel_1" "channel_2"])
            (is= (get-channels-connected-to-same-lobby {:connected-clients [{:id "client_0" :channel "channel_1"}] :counter 1}
                                                       {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [{:id "client_0" :name "Olle"}] :counter 1}
                                                       "client_0")
                 ["channel_1"]))}
  get-channels-connected-to-same-lobby [server-state lobbies-state id]
  (->> (:connected-clients server-state)
       (filter (fn [cc]
                 (some #(= (:id cc) %) (map :id (:players (:lobby-data (lc/get-lobby-data lobbies-state id)))))))
       (map :channel)))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (create-request-json "enter" {:player-name "someValue"})
                 "{\"type\":\"enter\",\"playerName\":\"someValue\"}")
            (is= (create-request-json "enter")
                 "{\"type\":\"enter\"}"))}

  create-request-json
  ([type] (create-request-json type {}))
  ([type data]
   (-> (merge {:type type} data)
       (json/write-str :key-fn (fn [key]
                                 (-> key
                                     (clojure.string/replace #"(-)(.)" (fn [a] (upper-case (get a 2))))
                                     (clojure.string/replace #":" "")))))))

(defn enter-lobby! [lobbies-state-atom {player-id :player-id player-name :player-name}]
  (swap! lobbies-state-atom lc/player-enter-lobby player-id player-name))
;(send! channel (create-request-json "lobbyEntered" (lc/get-lobby-data @lobbies-state-atom player-id)))))

(defn player-ready! [lobbies-state-atom {player-id :player-id ready :ready}]
  ;(do
  (swap! lobbies-state-atom ls/set-player-ready player-id ready))

(defn player-leave! [lobbies-state-atom {player-id :player-id}]
  ;(do
  (swap! lobbies-state-atom lc/player-leave-lobby player-id))

(defn player-color-change! [lobbies-state-atom {player-id :player-id color-id :color-id}]
  (do
    (println player-id color-id)
    (swap! lobbies-state-atom lc/player-color-change {:player-id player-id :color-id color-id})))


(defn send-to-lobby [lobby-id message]
  (->> (:players (ls/get-lobby @lobbies-state-atom lobby-id))
       (map :id)
       ;(map (fn [player-id] (player-id->channel @server-state-atom player-id)))
       ((fn [player-ids]
          (doseq [player-id player-ids]
            (let [lobby-data (lc/get-lobby-data @lobbies-state-atom player-id)]
              (when lobby-data
                (send! (player-id->channel @server-state-atom player-id) message))))))))


(def game-server-atom (atom {:connection :offile
                             :writer     nil}))

(defn write [writer message]
  (do (.write writer (str message "\n"))
      (.flush writer)))

(defn player-steering [lobby-id player-id steering]
  (println "STEERING" player-id steering)
  (write (:writer @game-server-atom) (create-request-json "player_steering" {:lobby-id lobby-id :player-id player-id :steering steering})))

(defn server [req]
  (with-channel req channel                                 ; get the channel
                ;; communicate with client using method defined above
                ;(inc-next-id server-state)
                (println "New channel!")
                (swap! server-state-atom add-connected-client channel)
                ;(send! channel (json/write-str {"type" "id" "id" (channel->player->id @server-state-atom channel)}))
                (on-receive channel (fn [data]              ; data received from client
                                      ;; An optional param can pass to send!: close-after-send?
                                      ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
                                      ;; and false for WebSocket.  (send! channel data close-after-send?)
                                      (do
                                        ;(println "Recieved data" data)
                                        (let [data-obj (json/read-str data)
                                              data-type (get data-obj "type")
                                              player-id (channel->player->id @server-state-atom channel)
                                              lobby-id (ls/player-id->lobby-id @lobbies-state-atom player-id)
                                              lobbies-state-bofore @lobbies-state-atom]
                                          (do
                                            (cond
                                              (= data-type "player_ready")
                                              (player-ready! lobbies-state-atom {:player-id player-id :ready (get data-obj "ready")})

                                              (= data-type "color_change")
                                              (player-color-change! lobbies-state-atom {:player-id player-id :color-id (keyword (get data-obj "colorId"))})

                                              (= data-type "enter_lobby")
                                              (do (enter-lobby! lobbies-state-atom {:player-id player-id :player-name (get data-obj "playerName")})
                                                  (send! channel (create-request-json "lobby_entered" (assoc (lc/get-lobby-data @lobbies-state-atom player-id) :player-id player-id))))

                                              (= data-type "player-disconnect")
                                              (player-disconnect)

                                              (= data-type "player_leave")
                                              (player-leave! lobbies-state-atom {:player-id player-id})

                                              (= data-type "player_steering")
                                              (player-steering lobby-id player-id (get data-obj "steering"))

                                              :else (println "Unknown data-type" data-obj))

                                            ;(println "Server-state" @server-state-atom)
                                            ;(println "Lobbies-state-before" lobbies-state-bofore)
                                            ;(println "Lobbies-state" @lobbies-state-atom)
                                            ;(println "player-id" player-id)
                                            ;(println "Channels" (distinct (concat (get-channels-connected-to-same-lobby @server-state-atom @lobbies-state-atom player-id) (get-channels-connected-to-same-lobby @server-state-atom lobbies-state-bofore player-id))))
                                            ;(println)
                                            ; Now we want to send an lobbyUpdate to all participants
                                            (let [affected-lobby-ids (distinct (filter #(if (not (nil? %)) %) [(ls/player-id->lobby-id @lobbies-state-atom player-id) (ls/player-id->lobby-id lobbies-state-bofore player-id)]))]
                                              (->> affected-lobby-ids
                                                   (map (fn [lobby-id] (ls/get-lobby @lobbies-state-atom lobby-id)))
                                                   (map :players)
                                                   (flatten)
                                                   (map :id)
                                                   (distinct)
                                                   ;(map (fn [player-id] (player-id->channel @server-state-atom player-id)))
                                                   ((fn [player-ids]
                                                      (doseq [player-id player-ids]
                                                        (let [lobby-data (lc/get-lobby-data @lobbies-state-atom player-id)]
                                                          (when lobby-data
                                                            (send! (player-id->channel @server-state-atom player-id) (create-request-json "lobby_update" lobby-data)))))))))
                                            )))))
                (on-close channel (fn [status]
                                    (println "channel closed"))))) ; data is sent directly to the client



(defn start! []
  (reset! server-atom (run-server server {:port 3000})))

(defn stop! []
  (when-not (nil? @server-atom)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    (@server-atom :timeout 100)
    (reset! server-atom nil)))

(defn reset-atoms! []
  (do
    (swap! lobbies-state-atom ls/create-state)
    (swap! server-state-atom create-server-state)
    nil))

(defn restart! []
  (stop!)
  (start!))

(defn full-restart! []
  (do (stop!)
      (reset-atoms!)
      (start!)))

(defn start-game! [lobby]
  (swap! lobbies-state-atom lc/set-game-started (:id lobby))
  (let [writer (:writer @game-server-atom)]
    (write writer (create-request-json "start_match" {:lobby-id (:id lobby) :match-config (lc/lobby-id->match-config @lobbies-state-atom (:id lobby))}))))

(add-watch lobbies-state-atom :game-server-listener
           (fn [_ _ _ lobby-state]
             ;(do (println "FITTA" (lc/get-lobbies-to-start lobby-state))
                 (doseq [lobby (lc/get-lobbies-to-start lobby-state)]
                   ;(do (println "BAJS" lobby)
                       (start-game! lobby))))

(defn on-game-server-connect [{writer :writer reader :reader}]
  (swap! game-server-atom (fn [state]
                            (-> state
                                (assoc :connection :online)
                                (assoc :writer writer)))))

(defn handle-message [msg]
  msg)

(full-restart!)

(def port 3002)
(def game-server (ServerSocket. port))

(def alive (atom true))

(reset! alive true)

(def thread (future (do
                      (while @alive
                        (let [socket (.accept game-server)
                              writer (io/writer socket)
                              reader (io/reader socket)]
                          (println "new client")
                          (on-game-server-connect {:writer writer :reader reader})
                          (future (do
                                    (let [socket-open (atom true)]
                                      (while (and @alive @socket-open)
                                        ;(println "loop")
                                        (let [msg (.readLine reader)]
                                          (if (not (nil? msg))
                                            (let [response (handle-message msg)]
                                              (when (not (nil? response))
                                                ;(print response)
                                                (send-to-lobby (get (json/read-str response) "lobbyId") response)))
                                            (reset! socket-open false)))))
                                    (println "game-server disconnected")
                                    (.close socket)))))
                      (println "not listeing anymore."))))















