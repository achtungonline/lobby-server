(ns achtungonline.lobbies-core
  (:require
    [ysera.test :refer [is= is is-not]]
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
            (is= (get-next-available-player-color (ls/create-lobby "1" :players [{:color-id (first player-colors)} {:color-id (nth player-colors 2)}]))
                 (second player-colors)))}
  get-next-available-player-color [lobby]
  (let [lobby-player-colors (map :color-id (:players lobby))]
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
                                  :lobbies [{:id "lobby_0" :players [{:id "0" :ready false :color-id :blue}] :game-status :not-started}]
                                  :counter 1)))}
  player-enter-lobby [state player-id player-name]
  (let [[state lobby] (if (open-lobby-exists state)
                        [state (get-open-lobby state)]
                        (let [state (create-and-add-lobby state)]
                          [state (get-open-lobby state)]))]
    (-> state
        (update-player player-id player-name)
        (ls/add-player-data-to-lobby (:id lobby) {:id player-id :ready false :color-id (get-next-available-player-color lobby)}))))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (lobby-id->match-config (ls/create-state :lobbies [(ls/create-lobby "0")]) "0")
                 {:players   []
                  :max-score 0
                  :map       {:type "square" :width 800 :height 800}})
            (is= (lobby-id->match-config (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :blue} {:id "2" :color-id :pink}])]) "0")
                 {:players   [{:id "1" :color-id :blue :name nil} {:id "2" :color-id :pink :name nil}]
                  :max-score 5
                  :map       {:type "square" :width 800 :height 800}})
            (is= (lobby-id->match-config {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [{:id "client_0" :name "Olle"}] :counter 1} "lobby_0")
                 {:players   [{:id "client_0" :color-id nil :name "Olle"}]
                  :max-score 0
                  :map       {:type "square" :width 800 :height 800}}))}
  lobby-id->match-config [state lobby-id]
  (let [lobby (ls/get-lobby state lobby-id)]
    {:players   (map (fn [player] {:id (:id player) :color-id (:color-id player) :name (ls/get-player-name state (:id player))}) (:players lobby))
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
                                 :players   [{:id "1" :color-id nil :name "olle"} {:id "2" :color-id nil :name "nils"}]
                                 :max-score 5
                                 :map       {:type "square" :width 800 :height 800}
                                 }
                  :lobby-data   {:players [{:id "1" :name "olle"} {:id "2" :name "nils"}]}})
            (is= (get-lobby-data {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [(ls/create-player "client_0" "olle")] :counter 1} "client_0")
                 {:match-config {
                                 :players   [{:id "client_0" :color-id nil :name "olle"}]
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

(defn
  ^{:doc  ""
    :test (fn []
            (is= (color-id-taken? (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink}])]) {:id "0" :color-id :blue})
                 false)
            (is= (color-id-taken? (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink}])]) {:id "0" :color-id :pink})
                 true))}
  color-id-taken? [state {id :id color-id :color-id}]
  (true? (some #(= color-id %) (map :color-id (:players (ls/get-lobby state id))))))


(defn
  ^{:doc  ""
    :test (fn []
            (is= (player-color-change (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink}])]) {:player-id "1" :color-id :blue})
                 (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :blue}])]))
            (is= (player-color-change (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink}])]) {:player-id "1" :color-id :color-does-not-exist})
                 (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink}])]))
            ; Should not be able to change to an opponents color
            (is= (player-color-change (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink} {:id "2" :color-id :blue}])]) {:player-id "1" :color-id :blue})
                 (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :color-id :pink} {:id "2" :color-id :blue}])])))}
  player-color-change [state {player-id :player-id color-id :color-id}]
  (if (or (color-id-taken? state {:id player-id :color-id color-id}) (empty? (filter #(= color-id %) player-colors)))
    state
    (ls/set-player-color state {:player-id player-id :color-id color-id})))


(defn
  ^{:test (fn []
            (is= (all-players-ready? (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :ready true} {:id "2" :ready true}])]) "0")
                 true)
            (is= (all-players-ready? (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "1" :ready true} {:id "2" :ready false}])]) "0")
                 false))}
  all-players-ready? [state lobby-id]
  (empty? (filter (fn [p] (not (:ready p))) (:players (ls/get-lobby state lobby-id)))))

(defn
  ^{:test (fn []
            (is= (get-lobbies-to-start (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "3" :ready true}] :game-status :not-started)
                                                                  (ls/create-lobby "1" :players [{:id "4" :ready true}] :game-status :started)
                                                                  (ls/create-lobby "2" :players [{:id "5" :ready false}] :game-status :not-started)]))
                 [(ls/create-lobby "0" :players [{:id "3" :ready true}] :game-status :not-started)]))}
  get-lobbies-to-start [state]
  (filter (fn [lobby]
            (and (all-players-ready? state (:id lobby))
                 (= (:game-status lobby) :not-started)))
          (ls/get-lobbies state)))

(defn
  ^{:test (fn []
            (is= (set-game-started (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "3" :ready true}] :game-status :not-started)]) "0")
                 (ls/create-state :lobbies [(ls/create-lobby "0" :players [{:id "3" :ready true}] :game-status :started)])))}
  set-game-started [state lobby-id]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (if (= (:id lobby) lobby-id)
                                    (assoc lobby :game-status :started)
                                    lobby))
                                lobbies))))