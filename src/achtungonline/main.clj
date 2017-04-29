(ns achtungonline.main
  (:require
    [clojure.string :refer [upper-case]]
    [org.httpkit.server :refer :all]
    [compojure.core :refer :all]
    [clojure.data.json :as json]
    [ysera.test :refer [is= is is-not]]
    [achtungonline.lobbies-core :as lc]
    [achtungonline.lobbies-handlers :as lh]
    [achtungonline.server-core :as connected-clients]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.walk :refer [keywordize-keys]])
  (:import (io.netty.handler.codec.json JsonObjectDecoder)))

(import [java.net ServerSocket])

(defonce lobbies-atom (atom (lc/create-state)))

(defonce connected-clients-atom (atom (connected-clients/create-state)))

(defonce client-server-atom (atom nil))

(defonce game-server-atom (atom {:connection :offline
                                 :writer     nil}))
(defn write [writer message]
  (do
    ;(println "Write message: " message)
    (.write writer (str message "\n"))
    (.flush writer)))

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

(defn send-to-player-channel! [player-id type data]
  (let [request-json-data (create-request-json data)]
    (println "Sending data to: " player-id " type: " type)
    (send! (connected-clients/client-id->channel @connected-clients-atom player-id) (create-request-json type data))))

(defn send-to-players-channels! [player-ids type data]
  (doseq [player-id player-ids]
    (send-to-player-channel! player-id type (if (fn? data) (data player-id) data))))

(add-watch lobbies-atom :state-listener
           (fn [_ _ _ state]
             (cond
               (lc/any-player-entered-lobby? state)
               (do
                 (println "entered lobby")
                 (let [player (lc/get-player-who-entered-lobby state)
                       player-id (:id player)]
                   (send-to-player-channel! player-id "lobby_entered" (lc/get-player-entered-lobby-data state {:id player-id}))
                   (swap! lobbies-atom lc/remove-player-event {:player player})))

               (lc/any-lobby-changed? state)
               (do
                 (println "lobby changed")
                 (send-to-players-channels! (lc/get-players-ids-with-changed-lobby state) "lobby_update" (fn [player-id] (lc/get-lobby-data state {:id player-id})))
                 (swap! lobbies-atom lh/handle-lobbies-updated))

               (lc/any-lobby-ready-to-start-game? state)
               (do
                 (println "start lobby game")
                 (let [lobby (lc/get-lobby-ready-to-start-game state)
                       lobby-id (:id lobby)]
                   (write (:writer @game-server-atom) (create-request-json "start_match" {:lobby-id lobby-id :match-config (lc/lobby-id->match-config state lobby-id)}))
                   (swap! lobbies-atom lc/set-game-started {:lobby-id lobby-id})))
               )))

(defn handle-client-request
  {:test (fn [])}
  [lobbies-state player-id data]
  (let [data-type (:type data)
        lobby-id (lc/player-id->lobby-id lobbies-state player-id)]
    (do
      (cond
        (= data-type "player_ready")
        (lh/handle-player-ready lobbies-state player-id (:ready data))

        (= data-type "color_change")
        (lh/handle-player-color-change lobbies-state {:player-id player-id :color-id (keyword (:colorId data))})

        (= data-type "enter_lobby")
        (lh/handle-player-enter-lobby lobbies-state player-id (:playerName data))

        (= data-type "player_disconnect")
        (lh/handle-player-disconnect lobbies-state player-id)

        (= data-type "player_leave")
        (lh/handle-player-leave-lobby lobbies-state player-id)

        :else (do
                (println "Unknown data-type: " data-type " with data: " data)
                lobbies-state)))))

(defn client-server [req]
  (with-channel req channel
                (println "New channel:" channel "req:" req)
                (swap! connected-clients-atom connected-clients/add-client channel)
                (on-receive channel (fn [data-string]
                                      (let [client-id (connected-clients/channel->client-id @connected-clients-atom channel)
                                            data (keywordize-keys (json/read-str data-string))]
                                        (println "Recieved data from client:" client-id "with data-string:" data-string " converted to data: " data)
                                        (if (= (:type data) "player_steering")
                                          ; TODO Special treatment of player-steering until the client can communicate directly with the game server
                                          (write (:writer @game-server-atom) (create-request-json "player_steering" {:lobby-id (lc/player-id->lobby-id @lobbies-atom client-id) :player-id client-id :steering (:steering data)}))
                                          (swap! lobbies-atom handle-client-request client-id data)))))
                (on-close channel (fn [status]
                                    (let [client-id (connected-clients/channel->client-id @connected-clients-atom channel)]
                                      (println "Channel closed from client:" client-id)
                                      (swap! lobbies-atom handle-client-request client-id {:type "player_disconnect"})
                                      (swap! connected-clients-atom connected-clients/remove-client client-id))))))

(defn start-client-server! []
  (reset! client-server-atom (run-server client-server {:port 3000})))

(defn stop-client-server! []
  (when-not (nil? @client-server-atom)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    (@client-server-atom :timeout 100)
    (reset! client-server-atom nil)))

(defn reset-atoms! []
  (swap! lobbies-atom lc/create-state)
  (swap! connected-clients-atom connected-clients/create-state))

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

(defn on-game-server-connect [{writer :writer reader :reader}]
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
  (println "HORA" lobby-id message)
  (->> (lc/get-lobby @lobbies-atom lobby-id)
       (:players)
       (map :id)
       ;(map (fn [player-id] (player-id->channel @server-state-atom player-id)))
       ((fn [player-ids]
          (doseq [player-id player-ids]
            ;(let [lobby-data (lc/get-lobby-data @lobbies-atom player-id)]
            ;  (when lobby-data
            (send! (connected-clients/client-id->channel @connected-clients-atom player-id) message))))))

(def thread (future (do
                      (while @alive
                        (let [socket (.accept game-server)
                              writer (io/writer socket)
                              reader (io/reader socket)]
                          (println "New game server!")
                          (on-game-server-connect {:writer writer :reader reader})
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

;(defn get-channels-connected-to-same-lobby
;  {:test (fn []
;           (is= (get-channels-connected-to-same-lobby (sc/create-state :connected-clients [{:id "1" :channel "channel_1"}
;                                                                                               {:id "2" :channel "channel_2"}
;                                                                                               {:id "3" :channel "channel_3"}])
;                                                      (lc/create-state :lobbies [{:players [{:id "1"} {:id "2"}]}])
;                                                      "2")
;                ["channel_1" "channel_2"])
;           (is= (get-channels-connected-to-same-lobby (sc/create-state :connected-clients [{:id "1" :channel "channel_1"}
;                                                                                               {:id "2" :channel "channel_2"}
;                                                                                               {:id "3" :channel "channel_3"}])
;                                                      (lc/create-state :lobbies [{:id "3" :players [{:id "1"} {:id "2"}]}])
;                                                      "3")
;                ["channel_1" "channel_2"])
;           (is= (get-channels-connected-to-same-lobby {:connected-clients [{:id "client_0" :channel "channel_1"}] :counter 1}
;                                                      {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [{:id "client_0" :name "Olle"}] :counter 1}
;                                                      "client_0")
;                ["channel_1"]))}
;  [server-state lobbies-state id]
;  (->> (:connected-clients server-state)
;       (filter (fn [cc]
;                 (some #(= (:id cc) %) (map :id (:players (:lobby-data (lc/get-lobby-data lobbies-state id)))))))
;       (map :channel)))


;
;(defn
;  ^{:doc  ""
;    :test (fn []
;            (is= (player-id->channel {:connected-clients [{:id 2 :channel "mocked channel"}]}
;                                     2)
;                 "mocked channel"))
;    }
;  player-id->channel [server-state player-id]
;  (some #(when (= player-id (:id %)) (:channel %))
;        (:connected-clients server-state)))
;


;(defn send-lobby-update-to-clients! [changed-lobbies-ids]
;  (let [changed-lobbies-ids (lc/get-changed-lobby-ids @state-atom)]
;    (->> affected-lobby-ids
;         (map (fn [lobby-id] (lc/get-lobby @state-atom lobby-id)))
;         (map :players)
;         (flatten)
;         (map :id)
;         (distinct)
;         ;(map (fn [player-id] (player-id->channel @server-state-atom player-id)))
;         ((fn [player-ids]
;            (doseq [player-id player-ids]
;              (let [lobby-data (lc/get-lobby-data @state-atom player-id)]
;                (when lobby-data
;                  (send-to-player-channel! player-id "lobby_update" lobby-data)))))))))

;(add-watch state-atom :state-listener
;           (fn [_ _ old-state new-state]
;             (cond
;               (lc/any-lobby-changed? old-state new-state)
;               (doseq [player-id (lc/get-players-ids-with-changed-lobby old-state new-state)]
;                 (send-to-player-channel! player-id "lobby_update" (lc/get-lobby-data new-state player-id)))
;
;               (lc/player-entered-lobby? old-state new-state)
;               (doseq [player-id (lc/get-players-entered-lobby-ids old-state new-state)]
;                 (send-to-player-channel! player-id "lobby_entered" (assoc (lc/get-lobby-data @state-atom player-id) :player-id player-id))))))

;(defonce server-atom (atom nil))

;(def port 3002)
;(defonce game-server (ServerSocket. port))
;
;(def alive (atom true))
;
;(reset! alive true)
;
;(defn
;  ^{:doc  "Returns the next id and an updates a counter"
;    :test (fn []
;            (is= (get-next-client-id (create-server-state))
;                 [(create-server-state :counter 1)
;                  "client_0"]))}
;  get-next-client-id [server-state]
;  [(assoc server-state :counter (inc (:counter server-state))) (str "client_" (:counter server-state))])
;

;

;
;
;
;
;
;(defn player-disconnect []
;  (println "player-disconnect function"))
;
;(defn
;  ^{
;    :doc  ""
;    :test (fn []
;            (is= (get-channels-connected-to-same-lobby (create-server-state :connected-clients [{:id "1" :channel "channel_1"}
;                                                                                                {:id "2" :channel "channel_2"}
;                                                                                                {:id "3" :channel "channel_3"}])
;                                                       (lc/create-state :lobbies [{:players [{:id "1"} {:id "2"}]}])
;                                                       "2")
;                 ["channel_1" "channel_2"])
;            (is= (get-channels-connected-to-same-lobby (create-server-state :connected-clients [{:id "1" :channel "channel_1"}
;                                                                                                {:id "2" :channel "channel_2"}
;                                                                                                {:id "3" :channel "channel_3"}])
;                                                       (lc/create-state :lobbies [{:id "3" :players [{:id "1"} {:id "2"}]}])
;                                                       "3")
;                 ["channel_1" "channel_2"])
;            (is= (get-channels-connected-to-same-lobby {:connected-clients [{:id "client_0" :channel "channel_1"}] :counter 1}
;                                                       {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [{:id "client_0" :name "Olle"}] :counter 1}
;                                                       "client_0")
;                 ["channel_1"]))}
;  get-channels-connected-to-same-lobby [server-state lobbies-state id]
;  (->> (:connected-clients server-state)
;       (filter (fn [cc]
;                 (some #(= (:id cc) %) (map :id (:players (:lobby-data (lc/get-lobby-data lobbies-state id)))))))
;       (map :channel)))
;
;
;
;(defn send-to-lobby [lobby-id message]
;  (->> (:players (lc/get-lobby @state-atom lobby-id))
;       (map :id)
;       ;(map (fn [player-id] (player-id->channel @server-state-atom player-id)))
;       ((fn [player-ids]
;          (doseq [player-id player-ids]
;            (let [lobby-data (lc/get-lobby-data @state-atom player-id)]
;              (when lobby-data
;                (send! (player-id->channel @server-state-atom player-id) message))))))))
;
;
;(def game-server-atom (atom {:connection :offile
;                             :writer     nil}))
;
;(defn write [writer message]
;  (do (.write writer (str message "\n"))
;      (.flush writer)))
;
;(defn player-steering [lobby-id player-id steering]
;  (println "STEERING" player-id steering)
;  (write (:writer @game-server-atom) (create-request-json "player_steering" {:lobby-id lobby-id :player-id player-id :steering steering})))
;
;
;(defn handle-client-request
;  {:test (fn [])}
;  [state player-id data]
;  (let [data-obj (json/read-str data)
;        data-type (get data-obj "type")
;        lobby-id (lc/player-id->lobby-id @state-atom player-id)
;        lobbies-state-before @state-atom]
;    (do
;      (cond
;        (= data-type "player_ready")
;        (lc/set-player-ready state player-id (get data-obj "ready"))
;
;        (= data-type "color_change")
;        (lc/player-color-change state {:player-id player-id :color-id (keyword (get data-obj "colorId"))})
;
;        (= data-type "enter_lobby")
;        (lc/player-enter-lobby state player-id (get data-obj "playerName"))
;
;        ;(= data-type "player-disconnect")
;        ;(player-disconnect)
;
;        (= data-type "player_leave")
;        (lc/player-leave-lobby state player-id)
;
;        (= data-type "player_steering")
;        (player-steering lobby-id player-id (get data-obj "steering"))
;
;        :else (println "Unknown data-type" data-obj)))))
;

;
;(defn server [req]
;  (with-channel req channel                                 ; get the channel
;                ;; communicate with client using method defined above
;                ;(inc-next-id server-state)
;                (println "New channel:" channel "req:" req)
;                (swap! server-state-atom add-connected-client channel)
;                ;(send! channel (json/write-str {"type" "id" "id" (channel->player->id @server-state-atom channel)}))
;                (on-receive channel (fn [data]              ; data received from client
;                                      ;; An optional param can pass to send!: close-after-send?
;                                      ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
;                                      ;; and false for WebSocket.  (send! channel data close-after-send?)
;                                      (println "Recieved data" data)
;                                      (swap! state-atom handle-client-request (channel->player->id @server-state-atom channel) data)))
;                (on-close channel (fn [status]
;                                    (println "channel closed:" channel)
;                                    (handle-client-closed (channel->player->id @server-state-atom channel)))))) ; data is sent directly to the client
;
;
;
;
;
;
;
;
;(defn start! []
;  (reset! server-atom (run-server server {:port 3000})))
;
;(defn stop! []
;  (when-not (nil? @server-atom)
;    ;; graceful shutdown: wait 100ms for existing requests to be finished
;    (@server-atom :timeout 100)
;    (reset! server-atom nil)))
;
;(defn reset-atoms! []
;  (do
;    (swap! state-atom lc/create-state)
;    (swap! server-state-atom create-server-state)
;    nil))
;
;(defn restart! []
;  (stop!)
;  (start!))
;
;(defn full-restart! []
;  (do (stop!)
;      (reset-atoms!)
;      (start!)))
;






;
;
;
;
;(defn start-game! [lobby]
;  (swap! state-atom lc/set-game-started (:id lobby))
;  (let [writer (:writer @game-server-atom)]
;    (write writer (create-request-json "start_match" {:lobby-id (:id lobby) :match-config (lc/lobby-id->match-config @state-atom (:id lobby))}))))
;
;(add-watch state-atom :game-server-listener
;           (fn [_ _ _ lobby-state]
;             ;(do (println "FITTA" (lc/get-lobbies-to-start lobby-state))
;             (doseq [lobby (lc/get-lobbies-to-start lobby-state)]
;               ;(do (println "BAJS" lobby)
;               (start-game! lobby))))
;
;(defn on-game-server-connect [{writer :writer reader :reader}]
;  (swap! game-server-atom (fn [state]
;                            (-> state
;                                (assoc :connection :online)
;                                (assoc :writer writer)))))
;
;(defn handle-message [msg]
;  msg)
;
;(full-restart!)
;
;
;
;(def thread (future (do
;                      (while @alive
;                        (let [socket (.accept game-server)
;                              writer (io/writer socket)
;                              reader (io/reader socket)]
;                          (println "new client")
;                          (on-game-server-connect {:writer writer :reader reader})
;                          (future (do
;                                    (let [socket-open (atom true)]
;                                      (while (and @alive @socket-open)
;                                        ;(println "loop")
;                                        (let [msg (.readLine reader)]
;                                          (if (not (nil? msg))
;                                            (let [response (handle-message msg)]
;                                              (when (not (nil? response))
;                                                ;(print response)
;                                                (send-to-lobby (get (json/read-str response) "lobbyId") response)))
;                                            (reset! socket-open false)))))
;                                    (println "game-server disconnected")
;                                    (.close socket)))))
;                      (println "not listeing anymore."))))















