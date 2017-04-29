(ns achtungonline.server-core
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

;
;(defn
;  ^{:doc  ""
;    :test (fn []
;            (is= (create-request-json "enter" {:player-name "someValue"})
;                 "{\"type\":\"enter\",\"playerName\":\"someValue\"}")
;            (is= (create-request-json "enter")
;                 "{\"type\":\"enter\"}"))}
;
;  create-request-json
;  ([type] (create-request-json type {}))
;  ([type data]
;   (-> (merge {:type type} data)
;       (json/write-str :key-fn (fn [key]
;                                 (-> key
;                                     (clojure.string/replace #"(-)(.)" (fn [a] (upper-case (get a 2))))
;                                     (clojure.string/replace #":" "")))))))
;

;
;(defn send-to-player-channel! [player-id type data]
;  (send! (player-id->channel @server-state-atom player-id) (create-request-json type data)))

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

;
;(defn
;  ^{:doc  "Add a client to the connected clients list"
;    :test (fn []
;            (is= (add-connected-client (create-server-state) "mocked channel")
;                 {:connected-clients [{:id      "client_0"
;                                       :channel "mocked channel"}]
;                  :counter           1}))}
;  add-connected-client [server-state channel]
;  (let [[server-state id] (get-next-client-id server-state)]
;    (update server-state :connected-clients conj {:id id :channel channel})))
;
;(defn
;  ^{:doc  "Takes a channel and returns the id connected to the channel"
;    :test (fn []
;            (is= (channel->player->id {:connected-clients [{:id 2 :channel "mocked channel"}]}
;                                      "mocked channel")
;                 2))
;    }
;  channel->player->id [server-state channel]
;  (some #(when (= channel (:channel %)) (:id %))
;        (:connected-clients server-state)))
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
;(defn handle-client-closed
;  {:test (fn [])}
;  [player-id]
;  ())
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















