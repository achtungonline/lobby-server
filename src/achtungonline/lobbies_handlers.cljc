(ns achtungonline.lobbies-handlers
  (:require
    [ysera.test :refer [is= is is-not]]
    [achtungonline.utils :as utils]
    [achtungonline.lobbies-core :as lc]))

(defn handle-player-ready-request
  {:test (fn []
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1" :ready false}])] :players [{:id "1"}] :changed false)
                    (handle-player-ready-request "1" true)
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :players [{:id "1" :ready true}] :changed true)))}
  [state player-id ready]
  (-> (lc/set-player-ready state player-id ready)
      (lc/set-lobby-changed player-id)))

(defn handle-player-color-change-request
  {:test (fn []
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1" :color-id :pink}])] :players [{:id "1"}])
                    (handle-player-color-change-request {:player-id "1" :color-id :blue})
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :players [{:id "1" :color-id :blue}] :changed true))

           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1" :color-id :pink}])] :players [{:id "1"}])
                    (handle-player-color-change-request {:player-id "1" :color-id :color-does-not-exist})
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :players [{:id "1" :color-id :pink}] :changed true))

           ; Should not be able to change to an opponents color
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1" :color-id :pink} {:id "2" :color-id :blue}])] :players [{:id "1"}])
                    (handle-player-color-change-request {:player-id "1" :color-id :blue})
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :players [{:id "1" :color-id :pink} {:id "2" :color-id :blue}] :changed true))

           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1" :color-id :pink}])] :players [{:id "1"}])
                    (handle-player-color-change-request {:player-id "1" :color-id :blue})
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :players [{:id "1" :color-id :blue}] :changed true)))}
  [state {player-id :player-id color-id :color-id}]

  (-> (if (or (lc/color-id-taken? state {:id player-id :color-id color-id}) (empty? (filter #(= color-id %) lc/player-colors)))
        state
        (lc/set-player-color state {:player-id player-id :color-id color-id}))
      (lc/set-lobby-changed player-id)))

(defn handle-player-enter-lobby-request
  {:test (fn []
           (is= (-> (lc/create-state)
                    (handle-player-enter-lobby-request "0" "olle"))
                (lc/create-state :players [(lc/create-player "0" :name "olle" :event :entered-lobby)]
                                 :lobbies [(lc/create-lobby "lobby_0" :players [{:id "0" :ready false :color-id :blue}] :changed true)]
                                 :counter 1)))}
  [state player-id player-name]
  (let [[state lobby] (if (lc/open-lobby-exists state)
                        [state (lc/get-open-lobby state)]
                        (let [state (lc/create-and-add-lobby state)]

                          [state (lc/get-open-lobby state)]))]
    (->
      (if (lc/get-player state player-id)
        state
        (lc/add-player state (lc/create-player player-id name)))
      (lc/set-player-name player-id player-name)
      (lc/set-player-event {:id player-id :event :entered-lobby})
      (lc/add-player-data-to-lobby (:id lobby) {:id player-id :ready false :color-id (lc/get-next-available-player-color lobby)})
      (lc/set-lobby-changed player-id))))

(defn handle-player-leave-lobby-request
  {:test (fn []
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                    (handle-player-leave-lobby-request "1")
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :changed true))
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1"} {:id "2"}])] :players [{:id "1"} {:id "2"}])
                    (handle-player-leave-lobby-request "2")
                    (lc/get-lobby "0"))
                (lc/create-lobby "0" :players [{:id "1"}] :changed true)))}
  [state player-id]
  (-> (lc/set-lobby-changed state player-id)
      (lc/remove-player-from-lobby player-id)))

(defn handle-player-disconnect-request
  {:test (fn []
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :players [{:id "1"}])] :players [{:id "1"}])
                    (handle-player-disconnect-request "1"))
                (lc/create-state :lobbies [(lc/create-lobby "0" :changed true)]))

           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0")] :players [{:id "1"} {:id "2"}])
                    (handle-player-disconnect-request "2"))
                (lc/create-state :lobbies [(lc/create-lobby "0")] :players [{:id "1"}])))}
  [state player-id]
  (-> (if (lc/player-connected-to-a-lobby? state player-id)
        (handle-player-leave-lobby-request state player-id)
        state)
      (lc/remove-player player-id)))

(defn handle-lobbies-updated
  {:test (fn []
           (is= (-> (lc/create-state :lobbies [(lc/create-lobby "0" :changed true)])
                    (handle-lobbies-updated))
                (lc/create-state :lobbies [(lc/create-lobby "0" :changed false)])))}
  [state]
  (let [changed-lobbies (lc/get-changed-lobbies state)]
    (reduce (fn [a lobby]
              (lc/update-lobby a (:id lobby) :changed false))
            state
            changed-lobbies)))
