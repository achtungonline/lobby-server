(ns achtungonline.lobbies-state
  (:require
    [achtungonline.test.core :refer [is is= is-not]]))



(defn
  ^{:doc  "Creates a player"
    :test (fn []
            (is= (create-player "0" "olle")
                 {:id   "0"
                  :name "olle"}))}
  create-player [id name]
  {:id id :name name})

(defn
  ^{:doc  "Creates a lobby"
    :test (fn []
            (is= (create-lobby "0")
                 {:id      "0"
                  :players []}))}
  create-lobby [id & kvs]
  (let [lobby {:id      id
               :players []}]
    (reduce (fn [s [key val]] (assoc s key val)) lobby (partition 2 kvs))))

(defn
  ^{:doc  "Add a client to the connected clients list"
    :test (fn []
            (is= (create-state)
                 {:lobbies []
                  :players []
                  :counter 0})
            (is= (create-state :counter 5 :players [(create-player "0" "olle")] :lobbies [(create-lobby "1")])
                 {:lobbies [{:id "1" :players []}]
                  :players [{:id "0" :name "olle"}]
                  :counter 5}))}
  create-state [& kvs]
  (let [state {:lobbies []
               :players []
               :counter 0}]
    (reduce (fn [s [key val]] (assoc s key val)) state (partition 2 kvs))))

(defn
  ^{:doc  "Returns the next id and an updates a counter"
    :test (fn []
            (is= (get-next-id (create-state))
                 [(create-state :counter 1)
                  "0"])
            (is= (get-next-id (create-state) "lobby")
                 [(create-state :counter 1)
                  "lobby_0"]))}
  get-next-id
  ([state] (get-next-id state nil))
  ([state prefix]
   [(assoc state :counter (inc (:counter state))) (str (if prefix (str prefix "_") "") (:counter state))]))

(defn
  ^{:doc  "Get a player from the players list"
    :test (fn []
            (is= (get-player (create-state) "0")
                 nil)
            (is= (get-player (create-state :players [(create-player "0" "olle")]) "0")
                 (create-player "0" "olle")))}
  get-player [state id]
  (->> (:players state)
       (filter #(= (:id %) id))
       (first)))

(defn
  ^{:doc  "Add a player to the players list"
    :test (fn []
            (is= (add-player (create-state) (create-player "0" "olle"))
                 (create-state :players [(create-player "0" "olle")])))}
  add-player [state player]
  (assoc state :players (conj (:players state) player)))

(defn
  ^{:doc  "Add a player to the players list"
    :test (fn []
            (is= (add-player-data-to-lobby (create-state :lobbies [(create-lobby "0")]) "0" {:id "1"})
                 (create-state :lobbies [{:id "0" :players [{:id "1"}]}])))}
  add-player-data-to-lobby [state lobby-id player-data]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (if (= (:id lobby) lobby-id)
                                    (update lobby :players conj player-data)
                                    lobby))
                                lobbies))))

(defn
  ^{:doc  "Add a lobby to the lobbies list"
    :test (fn []
            (is= (add-lobby (create-state) "0")
                 (create-state :lobbies ["0"])))}
  add-lobby [state lobby]
  (assoc state :lobbies (conj (:lobbies state) lobby)))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (get-lobby (create-state :lobbies [(create-lobby "0")]) "0")
                 (create-lobby "0")))}
  get-lobby [state lobby-id]
  (->> (:lobbies state)
       (filter (fn [l] (= (:id l) lobby-id)))
       (first)))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (get-lobbies (create-state :lobbies [(create-lobby "0")]))
                 [(create-lobby "0")]))}
  get-lobbies [state]
  (:lobbies state))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (set-player-ready (create-state :lobbies [(create-lobby "0" :players [{:id "1" :ready false}])]) "1" true)
                 (create-state :lobbies [(create-lobby "0" :players [{:id "1" :ready true}])])))}
  set-player-ready [state player-id ready]
  (update state :lobbies (fn [lobbies]
                           (map (fn [lobby]
                                  (assoc lobby :players (map (fn [player]
                                                               (if (= (:id player) player-id)
                                                                 (assoc player :ready true)
                                                                 player))
                                                             (:players lobby))))
                                lobbies))))

(defn
  ^{
    :doc  ""
    :test (fn []
            (is= (player-id->lobby-id (create-state :lobbies [(create-lobby "0" :players [{:id "1"}])]) "1")
                 "0"))}
  player-id->lobby-id [state player-id]
  (->> (:lobbies state)
       (filter (fn [lobby]
                 (some (fn [p-id] (= player-id p-id))
                       (map :id (:players lobby)))))
       ((fn [lobbies]
         (if (empty? lobbies)
           nil
           (:id (first lobbies)))))))
