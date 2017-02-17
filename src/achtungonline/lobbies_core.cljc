(ns achtungonline.lobbies-core
  (:require
    [achtungonline.test.core :refer [is is= is-not]]
    [achtungonline.lobbies-state :as ls]))

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
                                  :lobbies [{:id "lobby_0" :players ["0"]}]
                                  :counter 1)))}
  player-enter-lobby [state player-id player-name]
  (let [[state lobby] (if (open-lobby-exists state)
                        [state (get-open-lobby state)]
                        (let [state (create-and-add-lobby state)]
                          [state (get-open-lobby state)]))]
    (-> state
        (update-player player-id player-name)
        (ls/add-player-to-lobby player-id (:id lobby)))))

(defn
  ^{:doc  ""
    :test (fn []
            (is= (lobby->match-config (ls/create-state :lobbies [(ls/create-lobby "0")]) "0")
                 {:players   []
                  :max-score 0
                  :map       nil}))}
  lobby->match-config [state lobby-id]
  (let [lobby (ls/get-lobby state lobby-id)]
  {:players (:players lobby)
   :max-score 0
   :map nil}))