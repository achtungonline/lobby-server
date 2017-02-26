(ns achtungonline.lobbies-core
  (:require
    [achtungonline.test.core :refer [is is= is-not]]
    [achtungonline.lobbies-state :as ls]))



(def player-colors [
                    :blue
                    :pink
                    :green
                    :purple
                    :orange
                    :lime
                    :indigo
                    :teal
                    :black
                    :bluegrey])

(defn
  ^{:doc  ""
    :test (fn []
            (is= (get-next-available-player-color (ls/create-lobby "1"))
                 (first player-colors))
            (is= (get-next-available-player-color (ls/create-lobby "1" :players [{:colorId (first player-colors)} {:colorId (nth player-colors 2)}]))
                 (second player-colors)))}
  get-next-available-player-color [lobby]
  (let [lobby-player-colors (map :colorId (:players lobby))]
    (->> player-colors
         (filter (fn [player-color]
                   (nil? (some #{player-color} lobby-player-colors))))
         (first))))

(defn
  ^{:doc  "Creates a lobby"
    :test (fn []
            (is= (create-and-add-lobby (ls/create-state))
                 (ls/create-state :counter 1 :lobbies [(ls/create-lobby "lobby_0")])))}
  create-and-add-lobby [state]
  (let [[state id] (ls/get-next-id state "lobby")
        lobby (ls/create-lobby id)]
    (ls/add-lobby state lobby)))

^{:doc  ""
  :test (fn []
          (is= (update-player (ls/create-state) "0" "olle")
               (ls/create-state :players [{:id "0" :name "olle"}])))}
(defn update-player [state id name]
  (let [player (ls/get-player state id)]
    (if player
      state                                                 ;TODO update the player name
      (ls/add-player state (ls/create-player id name)))))


(defn
  ^{:doc  ""
    :test (fn []
            (is= (get-open-lobby (ls/create-state))
                 nil)
            (is= (get-open-lobby (ls/create-state :lobbies [(ls/create-lobby "0")]))
                 (ls/create-lobby "0")))}
  get-open-lobby [state]
  (if (:lobbies state)
    (first (:lobbies state))
    nil))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (open-lobby-exists (ls/create-state))
                 false)
            (is= (open-lobby-exists (ls/create-state :lobbies [(ls/create-lobby "0")]))
                 true))}
  open-lobby-exists [state]
  (some? (get-open-lobby state)))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (player-enter-lobby (ls/create-state) "0" "olle")
                 (ls/create-state :players [{:id "0" :name "olle"}]
                                  :lobbies [{:id "lobby_0" :players [{:id "0" :ready false :colorId :blue}]}]
                                  :counter 1)))}
  player-enter-lobby [state player-id player-name]
  (let [[state lobby] (if (open-lobby-exists state)
                        [state (get-open-lobby state)]
                        (let [state (create-and-add-lobby state)]
                          [state (get-open-lobby state)]))]
    (-> state
        (update-player player-id player-name)
        (ls/add-player-data-to-lobby (:id lobby) {:id player-id :ready false :colorId (get-next-available-player-color lobby)}))))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (lobby-id->match-config (ls/create-state :lobbies [(ls/create-lobby "0")]) "0")
                 {:players   []
                  :max-score 0
                  :map       {:type "square" :width 800 :height 800}})
            (is= (lobby-id->match-config (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :colorId :blue} {:id "2" :colorId :pink}])]) "0")
                 {:players   [{:id "1" :colorId :blue :name nil} {:id "2" :colorId :pink :name nil}]
                  :max-score 5
                  :map       {:type "square" :width 800 :height 800}})
            (is= (lobby-id->match-config {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [{:id "client_0" :name "Olle"}] :counter 1} "lobby_0")
                 {:players   [{:id "client_0" :colorId nil :name "Olle"}]
                  :max-score 0
                  :map       {:type "square" :width 800 :height 800}}))}
  lobby-id->match-config [state lobby-id]
  (let [lobby (ls/get-lobby state lobby-id)]
    {:players   (map (fn [player] {:id (:id player) :colorId (:colorId player) :name (ls/get-player-name state (:id player))}) (:players lobby))
     :max-score (max (* (- (count (:players lobby)) 1) 5) 0)
     :map       {:type "square" :width 800 :height 800}}))


(defn
  ^{:doc  ""
    :test (fn []
            (is= (get-lobby-data (ls/create-state :lobbies [(ls/create-lobby "0")]) "0")
                 {:match-config {
                                 :players   []
                                 :max-score 0
                                 :map       {:type "square" :width 800 :height 800}
                                 }
                  :lobby-data   {:players []}})
            (is= (get-lobby-data (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1"} {:id "2"}])] :players [(ls/create-player "1" "olle") (ls/create-player "2" "nils")]) "1")
                 {:match-config {
                                 :players   [{:id "1" :colorId nil :name "olle"} {:id "2" :colorId nil :name "nils"}]
                                 :max-score 5
                                 :map       {:type "square" :width 800 :height 800}
                                 }
                  :lobby-data   {:players [{:id "1" :name "olle"} {:id "2" :name "nils"}]}})
            (is= (get-lobby-data {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [(ls/create-player "client_0" "olle")] :counter 1} "client_0")
                 {:match-config {
                                 :players   [{:id "client_0" :colorId nil :name "olle"}]
                                 :max-score 0
                                 :map       {:type "square" :width 800 :height 800}
                                 }
                  :lobby-data   {:players [{:id "client_0" :ready false :name "olle"}]}})
            (is= (get-lobby-data (ls/create-state :lobbies [(ls/create-lobby "0")]) "1")
                 nil))}
  get-lobby-data [state id]
  (let [lobby (or (ls/get-lobby state id)
                  (first (filter (fn [lobby]
                                   (not-empty
                                     (filter (fn [player]
                                               (= (:id player) id))
                                             (:players lobby))))
                                 (ls/get-lobbies state))))]
    (when lobby
      {:match-config (lobby-id->match-config state (:id lobby))
       :lobby-data   {:players (->> (:players lobby)
                                    (map (fn [player]
                                           (assoc player :name (ls/get-player-name state (:id player))))))}})))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (player-leave-lobby (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1"}])]) "1")
                 (ls/create-state :lobbies [(ls/create-lobby "0")]))
            (is= (player-leave-lobby (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1"} {:id "2"}])]) "2")
                 (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1"}])])))}
  player-leave-lobby [state player-id]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (assoc lobby :players (filter (fn [player]
                                                                  (not= (:id player) player-id))
                                                                (:players lobby))))
                                lobbies))))
