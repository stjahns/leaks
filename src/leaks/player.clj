(ns ripple.leaks.player
  (:use [pallet.thread-expr])
  (:require [brute.entity :as e]
            [ripple.components :as c]
            [ripple.rendering :as r]
            [ripple.subsystem :as s]
            [ripple.physics :as physics]
            [ripple.sprites :as sprites]
            [ripple.event :as event])
  (:import [com.badlogic.gdx.math Vector2]
           [com.badlogic.gdx Input$Keys]
           [com.badlogic.gdx Gdx]))

(defn- on-player-death
  [system entity event]
  (doto (:body (e/get-component system entity 'PhysicsBody))
    (.setGravityScale 0)
    (.setLinearVelocity 0.1 0.1)
    (.setAngularVelocity 0.1)
    (.setFixedRotation false))
  (-> system
      (let-> [player (e/get-component system entity 'Player)]
        (sprites/play-animation entity (:death-animation player)))
      (e/update-component entity 'Player #(assoc % :dead true))))

(defn- get-move-direction []
  "Return normalized movement direction for whatever movement keys are currently depressed"
  (let [keys-to-direction {Input$Keys/DPAD_UP (Vector2. 0 1)
                           Input$Keys/DPAD_DOWN (Vector2. 0 -1)
                           Input$Keys/DPAD_LEFT (Vector2. -1 0)
                           Input$Keys/DPAD_RIGHT (Vector2. 1 0)
                           Input$Keys/W (Vector2. 0 1)
                           Input$Keys/S (Vector2. 0 -1)
                           Input$Keys/A (Vector2. -1 0)
                           Input$Keys/D (Vector2. 1 0)}]
    (-> (reduce (fn [move-direction [keycode direction]]
                  (if (.isKeyPressed Gdx/input keycode)
                    (.add move-direction direction)
                    move-direction))
                (Vector2. 0 0) keys-to-direction)
        (.nor))))

(defn- apply-jet-force
  [system entity direction]
  (let [body (-> (e/get-component system entity 'PhysicsBody)
                 (:body))
        force (-> (e/get-component system entity 'Player)
                  (:jet-force))
        force (.scl direction (float force))]
    (.applyForceToCenter body force true)))

(defn- update-player-movement
  [system entity]
  (let [direction (get-move-direction)]
    (when (> (.len2 direction) 0)
      (apply-jet-force system entity direction))
    system))

(defn- update-camera-target
  [system entity]
  (let [[x y] (:position (e/get-component system entity 'Transform))
        camera (get-in system [:renderer :camera])]
    (r/update-camera-position camera x y))
  system)

(defn- get-player-aim-direction
  [system entity]
  (let [[mouse-x mouse-y] (r/screen-to-world system (.getX Gdx/input) (.getY Gdx/input))
        [player-x player-y] (:position (e/get-component system entity 'Transform))
        player-to-mouse (Vector2. (- mouse-x player-x)
                                  (- mouse-y player-y))]
    (.nor player-to-mouse)))

(defn- aim-broom
  "Aims broom in direciton of mouse and flips player sprite to face mouse"
  [system entity]
  (if-let [broom-root (first (event/get-entities-with-tag system "PlayerArmRoot"))]
    (let [aim-direction (get-player-aim-direction system entity)
          aim-rotation (- (.angle aim-direction) 180)
          facing-left (< (.x aim-direction) 0)]
      (-> system
          ;; Aim broom at mouse
          (e/update-component broom-root 'Transform #(assoc % :rotation aim-rotation))
          ;; Player sprite should face mouse.
          (e/update-component entity 'SpriteRenderer #(assoc % :flip-x facing-left))))
    system))

(defn- check-player-restart
  [system]
  (-> system 
      (when-> (.isKeyPressed Gdx/input Input$Keys/R)
              (event/send-event-to-tag "GameController" {:event-id :restart-game}))))

(defn- update-player
  [system entity]
  (let [player (e/get-component system entity 'Player)]
    (-> system
        (update-camera-target entity)
        (when-> (:dead player)
                (check-player-restart))
        (when-not-> (:dead player)
                    (aim-broom entity)
                    (update-player-movement entity)))))

(c/defcomponent Player
  :on-event [:player-death on-player-death]
  :on-pre-render update-player
  :fields [:dead {:default false}
           :death-animation {:asset true}
           :jet-force {:default 100}])

(s/defsubsystem player
  :component-defs ['Player])
