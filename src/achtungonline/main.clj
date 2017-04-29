(ns achtungonline.main
  (:require
    [clojure.string :refer [upper-case]]
    [org.httpkit.server :refer :all]
    [compojure.core :refer :all]
    [clojure.data.json :as json]
    [ysera.test :refer [is= is is-not]]
    [achtungonline.lobbies-core :as lc]
    [achtungonline.lobbies-handlers :as lh]
    [achtungonline.server_core :as server_core]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.walk :refer [keywordize-keys]])
  (:import (io.netty.handler.codec.json JsonObjectDecoder)))

(import [java.net ServerSocket])

(defonce lobbies-atom (atom (lc/create-state)))

(defonce connected-clients-atom (atom (server_core/create-state)))

(defonce client-server-atom (atom nil))

(defonce game-server-atom (atom {:connection :offline
                                 :writer     nil}))

; Writing to server logic

(defn write-to-game-server [message]
  (let [writer (:writer @game-server-atom)]
    (.write writer (str message "\n"))
    (.flush writer)))

(defn send-to-player-channel! [player-id type data]
  (let [request-json-data (server_core/create-request-json data)]
    (send! (server_core/client-id->channel @connected-clients-atom player-id) (server_core/create-request-json type data))))

(defn send-to-players-channels! [player-ids type data]
  (doseq [player-id player-ids]
    (send-to-player-channel! player-id type (if (fn? data) (data player-id) data))))

; ------------------------

(defn handle-player-entered-lobby! []
  (println "entered lobby")
  (let [player (lc/get-player-who-entered-lobby @lobbies-atom)
        player-id (:id player)]
    (send-to-player-channel! player-id "lobby_entered" (lc/get-player-entered-lobby-data @lobbies-atom {:id player-id}))
    (swap! lobbies-atom lc/remove-player-event {:player player})))

(defn handle-lobby-changed! []
  (println "lobby changed")
  (send-to-players-channels! (lc/get-players-ids-with-changed-lobby @lobbies-atom) "lobby_update" (fn [player-id] (lc/get-lobby-data @lobbies-atom {:id player-id})))
  (swap! lobbies-atom lh/handle-lobbies-updated))

(defn handle-lobby-ready-to-start-game []
  (println "start lobby game")
  (let [lobby (lc/get-lobby-ready-to-start-game @lobbies-atom)
        lobby-id (:id lobby)]
    (write-to-game-server (server_core/create-request-json "start_match" {:lobby-id lobby-id :match-config (lc/lobby-id->match-config @lobbies-atom lobby-id)}))
    (swap! lobbies-atom lc/set-game-started {:lobby-id lobby-id})))

(add-watch lobbies-atom :state-listener
           (fn [_ _ _ state]
             (cond
               (lc/any-player-entered-lobby? state)
               (handle-player-entered-lobby!)

               (lc/any-lobby-changed? state)
               (handle-lobby-changed!)

               (lc/any-lobby-ready-to-start-game? state)
               (handle-lobby-ready-to-start-game))))

(defn handle-client-request
  {:test (fn [])}
  [lobbies-state player-id data]
  (let [data-type (:type data)
        lobby-id (lc/player-id->lobby-id lobbies-state player-id)]
    (do
      (cond
        (= data-type "player_ready")
        (lh/handle-player-ready-request lobbies-state player-id (:ready data))

        (= data-type "color_change")
        (lh/handle-player-color-change-request lobbies-state {:player-id player-id :color-id (keyword (:colorId data))})

        (= data-type "enter_lobby")
        (lh/handle-player-enter-lobby-request lobbies-state player-id (:playerName data))

        (= data-type "player_disconnect")
        (lh/handle-player-disconnect-request lobbies-state player-id)

        (= data-type "player_leave")
        (lh/handle-player-leave-lobby-request lobbies-state player-id)

        :else (do
                (println "Unknown data-type: " data-type " with data: " data)
                lobbies-state)))))

(defn client-server [req]
  (with-channel req channel
                (println "New channel:" channel "req:" req)
                (swap! connected-clients-atom server_core/add-client channel)
                (on-receive channel (fn [data-string]
                                      (let [client-id (server_core/channel->client-id @connected-clients-atom channel)
                                            data (keywordize-keys (json/read-str data-string))]
                                        (println "Recieved data from client:" client-id "with data-string:" data-string " converted to data: " data)
                                        (if (= (:type data) "player_steering")
                                          ; TODO Special treatment of player-steering until the client can communicate directly with the game server
                                          (write-to-game-server (server_core/create-request-json "player_steering" {:lobby-id (lc/player-id->lobby-id @lobbies-atom client-id) :player-id client-id :steering (:steering data)}))
                                          (swap! lobbies-atom handle-client-request client-id data)))))
                (on-close channel (fn [status]
                                    (let [client-id (server_core/channel->client-id @connected-clients-atom channel)]
                                      (println "Channel closed from client:" client-id)
                                      (swap! lobbies-atom handle-client-request client-id {:type "player_disconnect"})
                                      (swap! connected-clients-atom server_core/remove-client client-id))))))

(defn start-client-server! []
  (reset! client-server-atom (run-server client-server {:port 3000})))

(defn stop-client-server! []
  (when-not (nil? @client-server-atom)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    (@client-server-atom :timeout 100)
    (reset! client-server-atom nil)))

(defn reset-atoms! []
  (swap! lobbies-atom lc/create-state)
  (swap! connected-clients-atom server_core/create-state))

(defn restart-client-server! []
  (do
    (stop-client-server!)
    (start-client-server!)))

(defn full-restart-client-server! []
  (do (stop-client-server!)
      (reset-atoms!)
      (start-client-server!)))

(start-client-server!)




; Game server related

(defn on-game-server-connect! [{writer :writer reader :reader}]
  (swap! game-server-atom (fn [state]
                            (-> state
                                (assoc :connection :online)
                                (assoc :writer writer)))))

(defn handle-message [msg]
  msg)

(def port 3002)
(def game-server (ServerSocket. port))

(def alive (atom true))

(reset! alive true)

(defn send-to-lobby [lobby-id message]
  ;TODO should not be handled here
  (->> (lc/get-lobby @lobbies-atom lobby-id)
       (:players)
       (map :id)
       ((fn [player-ids]
          (doseq [player-id player-ids]
            (send! (server_core/client-id->channel @connected-clients-atom player-id) message))))))

(def thread (future (do
                      (while @alive
                        (let [socket (.accept game-server)
                              writer (io/writer socket)
                              reader (io/reader socket)]
                          (println "New game server!")
                          (on-game-server-connect! {:writer writer :reader reader})
                          (future (do
                                    (let [socket-open (atom true)]
                                      (while (and @alive @socket-open)
                                        (let [msg (.readLine reader)]
                                          (if (not (nil? msg))
                                            (let [response (handle-message msg)]
                                              (when (not (nil? response))
                                                (send-to-lobby (get (json/read-str response) "lobbyId") response)))
                                            (reset! socket-open false)))))
                                    (println "game-server disconnected")
                                    (.close socket)))))
                      (println "not listeing anymore."))))









