(ns achtungonline.lobbies-core
  (:require
    [ysera.test :refer [is= is is-not]]
    [ysera.error :refer [error]]
    [achtungonline.utils :as utils]))

(defn create-player
  {:doc  "Creates a player"
   :test (fn []
           (is= (create-player "0" :name "olle")
                {:id   "0"
                 :name "olle"}))}
  [id & kvs]
  (let [player {:id id}]
    (reduce (fn [s [key val]] (assoc s key val)) player (partition 2 kvs))))

(defn create-lobby
  {:doc  "Creates a lobby"
   :test (fn []
           (is= (create-lobby "0")
                {:id          "0"
                 :players     []
                 :game-status :not-started}))}
  [id & kvs]
  (let [lobby {:id          id
               :players     []
               :game-status :not-started}]
    (reduce (fn [s [key val]] (assoc s key val)) lobby (partition 2 kvs))))

(defn create-state
  {:doc  "Add a client to the connected clients list"
   :test (fn []
           (is= (create-state)
                {:lobbies []
                 :players []
                 :counter 0})
           (is= (create-state :counter 5 :players [(create-player "0" :name "olle")] :lobbies [(create-lobby "1")])
                {:lobbies [{:id "1" :players [] :game-status :not-started}]
                 :players [{:id "0" :name "olle"}]
                 :counter 5}))}
  [& kvs]
  (let [state {:lobbies []
               :players []
               :counter 0}]
    (reduce (fn [s [key val]] (assoc s key val)) state (partition 2 kvs))))

(defn get-next-id
  {:doc  "Returns the next id and an updates a counter"
   :test (fn []
           (is= (get-next-id (create-state))
                [(create-state :counter 1)
                 "0"])
           (is= (get-next-id (create-state) "lobby")
                [(create-state :counter 1)
                 "lobby_0"]))}
  ([state] (get-next-id state nil))
  ([state prefix]
   [(assoc state :counter (inc (:counter state))) (str (if prefix (str prefix "_") "") (:counter state))]))

(defn get-player
  {:doc  "Get a player from the players list"
   :test (fn []
           (is= (get-player (create-state) "0")
                nil)
           (is= (get-player (create-state :players [(create-player "0" "olle")]) "0")
                (create-player "0" "olle")))}
  [state id]
  (->> (:players state)
       (filter #(= (:id %) id))
       (first)))

(defn add-player
  {:doc  "Add a player to the players list"
   :test (fn []
           (is= (add-player (create-state) (create-player "0" "olle"))
                (create-state :players [(create-player "0" "olle")])))}
  [state player]
  (assoc state :players (conj (:players state) player)))

(defn add-player-data-to-lobby
  {:doc  "Add a player to the players list"
   :test (fn []
           (is= (add-player-data-to-lobby (create-state :lobbies [(create-lobby "0")]) "0" {:id "1"})
                (create-state :lobbies [{:id "0" :players [{:id "1"}] :game-status :not-started}])))} [state lobby-id player-data]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (if (= (:id lobby) lobby-id)
                                    (update lobby :players conj player-data)
                                    lobby))
                                lobbies))))

(defn add-lobby
  {:doc  "Add a lobby to the lobbies list"
   :test (fn []
           (is= (add-lobby (create-state) "0")
                (create-state :lobbies ["0"])))}
  [state lobby]
  (assoc state :lobbies (conj (:lobbies state) lobby)))

(defn get-lobby
  {:doc  ""
   :test (fn []
           (is= (get-lobby (create-state :lobbies [(create-lobby "0")]) "0")
                (create-lobby "0"))
           (is= (get-lobby (create-state :lobbies [(create-lobby "0")]) "1")
                nil)
           (is= (get-lobby (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])]) "1")
                (create-lobby "0" :players [{:id "1"}])))}
  [state id]
  (->> (:lobbies state)
       (filter (fn [l] (or (= (:id l) id)
                           (some #(= (:id %) id) (:players l)))))
       (first)))

(defn get-lobbies
  {:doc  ""
   :test (fn []
           (is= (get-lobbies (create-state :lobbies [(create-lobby "0")]))
                [(create-lobby "0")]))}
  [state]
  (:lobbies state))

(defn set-player-ready
  {:doc  ""
   :test (fn []
           (is= (set-player-ready (create-state :lobbies [(create-lobby "0" :players [{:id "1" :ready false}])]) "1" true)
                (create-state :lobbies [(create-lobby "0" :players [{:id "1" :ready true}])])))}
  [state player-id ready]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (assoc lobby :players (map (fn [player]
                                                               (if (= (:id player) player-id)
                                                                 (assoc player :ready true)
                                                                 player))
                                                             (:players lobby))))
                                lobbies))))

(defn update-lobby
  {:test (fn []
           (is= (-> (create-state :lobbies [(create-lobby "0")])
                    (update-lobby "0" :something true :value 2))
                (create-state :lobbies [(create-lobby "0" :something true :value 2)])))}
  [state lobby-id & kvs]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (if (= (:id lobby) lobby-id)
                                    (reduce (fn [a [key val]]
                                              (if (fn? val)
                                                (update a key val)
                                                (assoc a key val))
                                              )
                                            lobby
                                            (partition 2 kvs))
                                    lobby))
                                lobbies))))

(defn update-player
  {:test (fn []
           (is= (-> (create-state :players [(create-player "1")])
                    (update-player "1" :something true :value 2))
                (create-state :players [(create-player "1" :something true :value 2)])))}
  [state player-id & kvs]
  (update state :players (fn [players]
                           (map (fn [player]
                                  (if (= (:id player) player-id)
                                    (reduce (fn [a [key val]]
                                              (if (fn? val)
                                                (update a key val)
                                                (assoc a key val))
                                              )
                                            player
                                            (partition 2 kvs))
                                    player))
                                players))))


(defn player?
  {:test (fn []
           (is (-> (create-state :lobbies [(create-lobby "0")] :players [{:id "1"}])
                   (player? "1")))
           (is-not (-> (create-state :lobbies [(create-lobby "0")] :players [{:id "1"}])
                       (player? "0"))))}
  [state id]
  (not (nil? (get-player state id))))

(defn lobby?
  {:test (fn []
           (is (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                   (lobby? "0")))
           (is-not (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                       (lobby? "1"))))}
  [state lobby-id]
  (not (->> (get-lobbies state)
            (map :id)
            (filter (fn [id] (= lobby-id id)))
            (empty?))))

(defn player-id->lobby-id
  {:test (fn []
           (is= (player-id->lobby-id (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])]) "1")
                "0"))}
  [state player-id]
  (->> (:lobbies state)
       (filter (fn [lobby]
                 (some (fn [p-id] (= player-id p-id))
                       (map :id (:players lobby)))))
       ((fn [lobbies]
          (if (empty? lobbies)
            nil
            (:id (first lobbies)))))))

(defn set-lobby-changed
  {:test (fn []
           (is= (-> (create-state :lobbies [(create-lobby "0")])
                    (set-lobby-changed "0"))
                (create-state :lobbies [(create-lobby "0" :changed true)]))
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                    (set-lobby-changed "1"))
                (create-state :lobbies [(create-lobby "0" :players [{:id "1"}] :changed true)] :players [{:id "1"}])))}
  [state lobby-id-or-player-id]
  (let [lobby-id (if (player? state lobby-id-or-player-id)
                   (player-id->lobby-id state lobby-id-or-player-id)
                   lobby-id-or-player-id)]
    (if (not (lobby? state lobby-id))
      (error "Can not set lobby changed. No lobby found with id: " lobby-id " in state: " state)
      (update-lobby state lobby-id :changed true))))


(defn set-player-color
  {:doc  ""
   :test (fn []
           (is= (set-player-color (create-state :lobbies [(create-lobby "0" :players [{:id "1" :color-id :blue}])]) {:player-id "1" :color-id :pink})
                (create-state :lobbies [(create-lobby "0" :players [{:id "1" :color-id :pink}])])))}
  [state {player-id :player-id color-id :color-id}]
  (update-lobby state (player-id->lobby-id state player-id) :players (fn [players]
                                                                       (map (fn [player]
                                                                              (if (= (:id player) player-id)
                                                                                (assoc player :color-id color-id)
                                                                                player))
                                                                            players))))



(defn get-players
  {:test (fn []
           (is= (get-players (create-state :players [{:id "1"}]))
                [{:id "1"}]))}
  [state]
  (:players state))

(defn- get-player-value
  {:test (fn []
           (is= (get-player-value (create-state :players [(create-player "0" :name "olle")]) {:id "0" :key :name})
                "olle"))}
  [state {id :id key :key}]
  (as-> state $
        (get-players $)
        (filter #(= (:id %) id) $)
        (first $)
        (get $ key)))

(defn get-player-name
  {:doc  ""
   :test (fn []
           (is= (get-player-name (create-state :players [(create-player "0" :name "olle")]) {:id "0"})
                "olle"))}
  [state {id :id player :player}]
  (get-player-value state {:id (or id (:id player)) :key :name}))



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

(defn get-next-available-player-color
  {:doc  ""
   :test (fn []
           (is= (get-next-available-player-color (create-lobby "1"))
                (first player-colors))
           (is= (get-next-available-player-color (create-lobby "1" :players [{:color-id (first player-colors)} {:color-id (nth player-colors 2)}]))
                (second player-colors)))}
  [lobby]
  (let [lobby-player-colors (map :color-id (:players lobby))]
    (->> player-colors
         (filter (fn [player-color]
                   (nil? (some #{player-color} lobby-player-colors))))
         (first))))

(defn create-and-add-lobby
  {:doc  "Creates a lobby"
   :test (fn []
           (is= (create-and-add-lobby (create-state))
                (create-state :counter 1 :lobbies [(create-lobby "lobby_0")])))}
  [state]
  (let [[state id] (get-next-id state "lobby")
        lobby (create-lobby id)]
    (add-lobby state lobby)))

(defn set-player-name
  {:test (fn []
           (is= (set-player-name (create-state :players [(create-player "0")]) "0" "olle")
                (create-state :players [(create-player "0" :name "olle")])))}
  [state id name]
  (update-player state id :name name))

(defn get-player-event
  [state {id :id player :player}]
  (get-player-value state {:id (or id (:id player)) :key :event}))

(defn set-player-event
  {:test (fn []
           (is= (-> (set-player-event (create-state :players [(create-player "0")]) {:id "0" :event :entered-lobby})
                    (get-player-event {:id "0"}))
                :entered-lobby))}
  [state {id :id event :event}]
  (update-player state id :event event))

(defn remove-player-event
  {:test (fn []
           (is= (-> (remove-player-event (create-state :players [(create-player "0")]) {:id "0"})
                    (get-player-event {:id "0"}))
                nil))}
  [state {id :id player :player}]
  (set-player-event state {:id (or id (:id player)) :event nil}))


(defn get-open-lobby
  {:doc  ""
   :test (fn []
           (is= (get-open-lobby (create-state))
                nil)
           (is= (get-open-lobby (create-state :lobbies [(create-lobby "0")]))
                (create-lobby "0")))}
  [state]
  (if (:lobbies state)
    (first (:lobbies state))
    nil))

(defn open-lobby-exists
  {:doc  ""
   :test (fn []
           (is= (open-lobby-exists (create-state))
                false)
           (is= (open-lobby-exists (create-state :lobbies [(create-lobby "0")]))
                true))}
  [state]
  (some? (get-open-lobby state)))

(defn lobby-id->match-config
  {:doc  ""
   :test (fn []
           (is= (lobby-id->match-config (create-state :lobbies [(create-lobby "0")]) "0")
                {:players   []
                 :max-score 0
                 :map       {:type "square" :width 800 :height 800}})
           (is= (lobby-id->match-config (create-state :lobbies [(create-lobby "0" :players [{:id "1" :color-id :blue} {:id "2" :color-id :pink}])]) "0")
                {:players   [{:id "1" :color-id :blue :name nil} {:id "2" :color-id :pink :name nil}]
                 :max-score 5
                 :map       {:type "square" :width 800 :height 800}})
           (is= (lobby-id->match-config {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [{:id "client_0" :name "Olle"}] :counter 1} "lobby_0")
                {:players   [{:id "client_0" :color-id nil :name "Olle"}]
                 :max-score 0
                 :map       {:type "square" :width 800 :height 800}}))}
  [state lobby-id]
  (let [lobby (get-lobby state lobby-id)]
    {:players   (map (fn [player] {:id (:id player) :color-id (:color-id player) :name (get-player-name state {:player player})}) (:players lobby))
     :max-score (max (* (- (count (:players lobby)) 1) 5) 0)
     :map       {:type "square" :width 800 :height 800}}))


(defn get-lobby-data
  {:test (fn []
           (is= (get-lobby-data (create-state :lobbies [(create-lobby "0")]) {:id "0"})
                {:match-config {
                                :players   []
                                :max-score 0
                                :map       {:type "square" :width 800 :height 800}
                                }
                 :lobby-data   {:players []}})
           (is= (get-lobby-data (create-state :lobbies [(create-lobby "0" :players [{:id "1"} {:id "2"}])] :players [(create-player "1" :name "olle") (create-player "2" :name "nils")]) {:id "1"})
                {:match-config {
                                :players   [{:id "1" :color-id nil :name "olle"} {:id "2" :color-id nil :name "nils"}]
                                :max-score 5
                                :map       {:type "square" :width 800 :height 800}
                                }
                 :lobby-data   {:players [{:id "1" :name "olle"} {:id "2" :name "nils"}]}})
           (is= (get-lobby-data {:lobbies [{:id "lobby_0" :players [{:id "client_0" :ready false}]}] :players [(create-player "client_0" :name "olle")] :counter 1} {:id "client_0"})
                {:match-config {
                                :players   [{:id "client_0" :color-id nil :name "olle"}]
                                :max-score 0
                                :map       {:type "square" :width 800 :height 800}
                                }
                 :lobby-data   {:players [{:id "client_0" :ready false :name "olle"}]}})
           (is= (get-lobby-data (create-state :lobbies [(create-lobby "0")]) "1")
                nil))}
  [state {id :id}]
  (let [lobby (or (get-lobby state id)
                  (first (filter (fn [lobby]
                                   (not-empty
                                     (filter (fn [player]
                                               (= (:id player) id))
                                             (:players lobby))))
                                 (get-lobbies state))))]
    (when lobby
      {:match-config (lobby-id->match-config state (:id lobby))
       :lobby-data   {:players (->> (:players lobby)
                                    (map (fn [player]
                                           (assoc player :name (get-player-name state {:player player})))))}})))

(defn get-player-entered-lobby-data
  {:test (fn []
           (is= (get-player-entered-lobby-data (create-state :lobbies [(create-lobby "0")]) {:id "0"})
                {:match-config {
                                :players   []
                                :max-score 0
                                :map       {:type "square" :width 800 :height 800}
                                }
                 :lobby-data   {:players []}
                 :player-id    "0"}))}
  [state {id :id}]
  (assoc (get-lobby-data state {:id id}) :player-id id))

(defn color-id-taken?
  {:doc  ""
   :test (fn []
           (is= (color-id-taken? (create-state :lobbies [(create-lobby "0" :players [{:id "1" :color-id :pink}])]) {:id "0" :color-id :blue})
                false)
           (is= (color-id-taken? (create-state :lobbies [(create-lobby "0" :players [{:id "1" :color-id :pink}])]) {:id "0" :color-id :pink})
                true))}
  [state {id :id color-id :color-id}]
  (true? (some #(= color-id %) (map :color-id (:players (get-lobby state id))))))


(defn all-players-ready?
  {:test (fn []
           (is= (all-players-ready? (create-state :lobbies [(create-lobby "0" :players [{:id "1" :ready true} {:id "2" :ready true}])]) "0")
                true)
           (is= (all-players-ready? (create-state :lobbies [(create-lobby "0" :players [{:id "1" :ready true} {:id "2" :ready false}])]) "0")
                false))}
  [state lobby-id]
  (empty? (filter (fn [p] (not (:ready p))) (:players (get-lobby state lobby-id)))))

(defn get-lobbies-to-start
  {:test (fn []
           (is= (get-lobbies-to-start (create-state :lobbies [(create-lobby "0" :players [{:id "3" :ready true}] :game-status :not-started)
                                                              (create-lobby "1" :players [{:id "4" :ready true}] :game-status :started)
                                                              (create-lobby "2" :players [{:id "5" :ready false}] :game-status :not-started)]))
                [(create-lobby "0" :players [{:id "3" :ready true}] :game-status :not-started)]))}
  [state]
  (filter (fn [lobby]
            (and (all-players-ready? state (:id lobby))
                 (= (:game-status lobby) :not-started)))
          (get-lobbies state)))

(defn set-game-started
  {:test (fn []
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "3" :ready true}] :game-status :not-started)])
                    (set-game-started {:lobby-id "0"}))
                (create-state :lobbies [(create-lobby "0" :players [{:id "3" :ready true}] :game-status :started)])))}
  [state {lobby-id :lobby-id}]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (if (= (:id lobby) lobby-id)
                                    (assoc lobby :game-status :started)
                                    lobby))
                                lobbies))))

(defn is-game-started?
  {:test (fn []
           (is (-> (create-state :lobbies [(create-lobby "0")])
                   (set-game-started {:lobby-id "0"})
                   (is-game-started? {:lobby-id "0"})))
           (is-not (-> (create-state :lobbies [(create-lobby "0")])
                       (is-game-started? "0"))))}
  [state {lobby-id :lobby-id}]
  (-> (get-lobby state lobby-id)
      (:game-status)
      (= :started)))

(defn remove-player-from-lobby
  {:test (fn []
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                    (remove-player-from-lobby "1")
                    (get-lobby "0"))
                (create-lobby "0"))
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"} {:id "2"}])] :players [{:id "1"} {:id "2"}])
                    (remove-player-from-lobby "2")
                    (get-lobby "0"))
                (create-lobby "0" :players [{:id "1"}])))}
  [state player-id]
  (update-lobby state (player-id->lobby-id state player-id) :players (fn [players]
                                                                       (filter (fn [player]
                                                                                 (not= (:id player) player-id))
                                                                               players))))

(defn remove-player
  {:test (fn []
           (is= (-> (create-state :players [{:id "1"}])
                    (remove-player "1"))
                (create-state)))}
  [state player-id]
  (update state :players (fn [players]
                           (filter (fn [player]
                                     (not= (:id player) player-id))
                                   players))))

(defn player-connected-to-a-lobby?
  {:test (fn []
           (is (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                   (player-connected-to-a-lobby? "1")))
           (is-not (-> (create-state :players [{:id "1"}])
                       (player-connected-to-a-lobby? "1"))))}
  [state player-id]
  (player-id->lobby-id state player-id))

(defn lobby-changed?
  {:test (fn []
           (is (-> (create-lobby "0" :changed true)
                   (lobby-changed?)))
           (is-not (-> (create-lobby "0")
                       (lobby-changed?))))}
  [lobby]
  (:changed lobby))

(defn any-lobby-changed?
  {:test (fn []
           (is (-> (create-state :lobbies [(create-lobby "0" :changed true)])
                   (any-lobby-changed?)))
           (is-not (-> (create-state :lobbies [(create-lobby "0")])
                       (any-lobby-changed?))))}
  [state]
  (->> (get-lobbies state)
       (filter lobby-changed?)
       (empty?)
       (false?)))


(defn get-changed-lobbies
  {:test (fn []
           (is= (-> (create-state :lobbies [(create-lobby "0" :changed false)])
                    (get-changed-lobbies))
                [])
           (is= (-> (create-state :lobbies [(create-lobby "0" :changed true)])
                    (get-changed-lobbies))
                [(create-lobby "0" :changed true)]))}
  [state]
  (->> (get-lobbies state)
       (filter :changed)))

(defn get-players-ids-with-changed-lobby
  {:test (fn []
           ; No changed lobby
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])])
                    (get-players-ids-with-changed-lobby))
                [])
           ; Lobby changed but no players inside lobby
           (is= (-> (create-state :lobbies [(create-lobby "0" :changed true)])
                    (get-players-ids-with-changed-lobby))
                [])
           ; Lobby changed with players inside
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}] :changed true)])
                    (get-players-ids-with-changed-lobby))
                ["1"]))}
  [state]
  (->> (get-changed-lobbies state)
       (map :players)
       (flatten)
       (map :id)))

(defn get-player-who-entered-lobby
  {:test (fn []
           (is= (-> (create-state :players [{:id "1"}])
                    (set-player-event {:id "1" :event :entered-lobby})
                    (get-player-who-entered-lobby))
                {:id "1" :event :entered-lobby})
           (is= (-> (create-state :players [{:id "1"}])
                    (get-player-who-entered-lobby))
                nil)
           )}
  [state]
  (let [players (->> (get-players state)
                     (filter (fn [player] (= (get-player-event state {:player player}) :entered-lobby))))]
    (when-not (empty? players)
      (first players))))


(defn any-player-entered-lobby?
  {:test (fn []
           (is= (-> (create-state :players [{:id "1"}])
                    (set-player-event {:id "1" :event :entered-lobby})
                    (any-player-entered-lobby?))
                true)
           (is= (-> (create-state :players [{:id "1"}])
                    (any-player-entered-lobby?))
                false)
           )}
  [state]
  (not (nil? (get-player-who-entered-lobby state))))



(defn get-lobby-ready-to-start-game
  {:test (fn []
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])])
                    (get-lobby-ready-to-start-game))
                nil)
           ; Only one player can not start a game
           (is= (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])])
                    (set-player-ready "1" true)
                    (get-lobby-ready-to-start-game))
                nil)
           (let [state (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"} {:id "2"}])])
                           (set-player-ready "1" true)
                           (set-player-ready "2" true))]
             (is= (get-lobby-ready-to-start-game state)
                  (get-lobby state "0")))
           ; Only one player is ready
           (let [state (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"} {:id "2"}])])
                           (set-player-ready "1" true))]
             (is= (get-lobby-ready-to-start-game state)
                  nil))
           ; Lobby has started the game and should not restart...
           (let [state (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"} {:id "2"}])])
                           (set-player-ready "1" true)
                           (set-player-ready "2" true)
                           (set-game-started {:lobby-id "0"}))]
             (is= (get-lobby-ready-to-start-game state)
                  nil)))}
  [state]
  (->> state
       (get-lobbies)
       (filter (fn [lobby]
                 (let [players (:players lobby)]
                   (and (not (is-game-started? state {:lobby-id (:id lobby)}))
                        (> (count players) 1)
                        (-> (filter :ready players)
                            (count)
                            (= (count players)))))))
       (first)))


(defn any-lobby-ready-to-start-game?
  {:test (fn []
           (is-not (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])])
                       (any-lobby-ready-to-start-game?)))
           (is (-> (create-state :lobbies [(create-lobby "0" :players [{:id "1"} {:id "2"}])])
                   (set-player-ready "1" true)
                   (set-player-ready "2" true)
                   (any-lobby-ready-to-start-game?))))}
  [state]
  (not (nil? (get-lobby-ready-to-start-game state))))





;(concat (lc/get-player-ids-inside-lobby old-state) (lc/get-player-ids-inside-lobby new-state))
;(let [affected-lobby-ids (distinct (filter #(if (not (nil? %)) %) [(player-id->lobby-id old-state player-id) (player-id->lobby-id new-state player-id)]))]
;  (->> affected-lobby-ids
;       (map (fn [lobby-id] (get-lobby @lobbies-state-atom lobby-id)))
;       (map :players)
;       (flatten)
;       (map :id)
;       (distinct)
;       ;(map (fn [player-id] (player-id->channel @server-state-atom player-id)))
;       ((fn [player-ids]
;          (doseq [player-id player-ids]
;            (let [lobby-data (lc/get-lobby-data @lobbies-state-atom player-id)]
;              (when lobby-data
;                (send! (player-id->channel @server-state-atom player-id) (create-request-json "lobby_update" lobby-data))))))))))

;(defn any-lobby-changed?
;  {:test (fn [])}
;  [old-state new-state])
;
;(defn player-entered-lobby?
;  {:test (fn [])}
;  [old-state new-state])
;
;(defn get-players-entered-lobby-ids
;  {:test (fn [])}
;  [old-state new-state])
