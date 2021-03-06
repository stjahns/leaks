(ns leaks.core
  (:require [play-clj.core :refer :all]
            [ripple.core :as ripple]
            [ripple.rendering :as rendering]
            [ripple.sprites :as sprites]
            [ripple.physics :as physics]
            [ripple.audio :as audio]
            [ripple.components :as c]
            [ripple.assets :as a]
            [ripple.subsystem :as subsystem]
            [ripple.prefab :as prefab]
            [ripple.event :as event]
            [ripple.tiled-map :as tiled-map]
            [ripple.transform :as transform]
            [leaks.player :as player]
            [leaks.components :as leaks-components])
  (:import [com.badlogic.gdx.backends.lwjgl LwjglApplication]
           [com.badlogic.gdx ApplicationListener]
           [org.lwjgl.input Keyboard])
  (:gen-class))

(declare shutdown restart)

(def subsystems [transform/transform
                 event/events
                 rendering/rendering
                 physics/physics
                 prefab/prefabs
                 sprites/sprites
                 audio/audio
                 tiled-map/level
                 player/player
                 leaks-components/leak-systems])

(def asset-sources ["leaks/leaks-assets.yaml"])

(defn on-initialized
  "Load the LeaksLevel"
  [system]
  (-> system
      (prefab/instantiate "LeaksLevel" {})))

(defscreen main-screen
  :on-show
  (fn [screen entities]

    ;; Initialize Ripple
    (reset! ripple/sys (ripple/initialize subsystems asset-sources on-initialized))

    ;; Use an orthographic camera
    (update! screen :renderer (stage) :camera (orthographic))

    nil)

  :on-touch-down
  (fn [screen entities]
    (reset! ripple/sys (-> @ripple/sys (subsystem/on-system-event :on-touch-down)))
    nil)

  :on-render
  (fn [screen entities]
    (reset! ripple/sys (-> @ripple/sys
                    (subsystem/on-system-event :on-pre-render)
                    (subsystem/on-system-event :on-render)))
    (when (:restart @ripple/sys)
      (shutdown)
      (restart))
    nil)

  :on-resize
  (fn [screen entities]
    (reset! ripple/sys (-> @ripple/sys (subsystem/on-system-event :on-resize)))
    nil))

(defgame leaks
  :on-create
  (fn [this]
    (set-screen! this main-screen)))

(defscreen blank-screen
  :on-render
  (fn [screen entities]
    (clear!)))

(defn shutdown []
  (set-screen! leaks blank-screen)
  (Thread/sleep 100)
  (subsystem/on-system-event @ripple/sys :on-shutdown))

(defn restart []
  (set-screen! leaks main-screen))

;; For exception handling...
(set-screen-wrapper! (fn [screen screen-fn]
                       (try (screen-fn)
                         (catch Exception e
                           (.printStackTrace e)
                           (set-screen! leaks blank-screen)))))
(defn -main []
  (LwjglApplication. leaks "LEAKS!!" 800 600)
  (Keyboard/enableRepeatEvents true))
