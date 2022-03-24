(ns ^:figwheel-hooks lasvice.frontend.lan-share
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.ratom :as ratom]
   [reagent.dom :as rdom]
   [clojure.core.async :as a :refer [go put! chan <! >!]]
   [ajax.core :refer [GET POST DELETE]]
   ;; [lasvice.frontend.db :refer [->Test ->Restful connect! get! delete! add!]]
   ))
(comment
  (->> (GET "/api/entries" {:response-type :json
                            :handler (fn [r] (println (get (js->clj (js/JSON.parse r)) "entries")))}))
;
  )

(defn multiply [a b] (* a b))

;; Define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:db []
                          ;; :next-id nil
                          :to-send nil
                          :username nil}))

(defn get-user-name []
  (GET "/api/user" {:response-format :json
                    :keywords? true
                    :handler (fn [r] (println r)(when-let [username (:username r)]
                                         (swap! app-state assoc-in [:username] username)))}))
(get-user-name)

(comment
  (get-in @app-state [:username]))

(def in$ (chan))
(defn populate-db []
  (GET "/api/entries" {:response-format :json
                       :keywords? true
                       :handler (fn [r] (put! in$ (:entries r)))})
  (go
    (let [entries (<! in$)]
      ;; (println entries)
      (swap! app-state update-in [:db] (constantly entries)))))

(populate-db)

(defn add-entry [body]
  (POST "/api/entries" {:body (doto
                               (js/FormData.)
                                (.append "type" "text")
                                (.append "body" body)
                                (.append "user" (get-in @app-state [:username])))
                        :keywords? true
                        :format :json
                        :response-format :json
                        :handler (fn [_] (populate-db))
                        :error-handler (fn [e] (println e))})
  (swap! app-state update-in [:db] conj {:entries-body "Sending..."}))

(defn delete-entries [& ids]

  (let [ids (into [] ids)]
    (println ids)
    (DELETE "/api/entries" {:body (doto
                                   (js/FormData.)
                                    (.append "ids" ids))
                            :keywords? true
                            :format :json
                            :response-format :json
                            :handler (fn [r] (println r) (populate-db))
                            :error-handler (fn [e] (println e))}))
  (swap! app-state update-in [:db] (fn [x] (map #(if ((into #{} ids) (:entries-id %)) {:entries-body "Deleting..."} %) x))))

(comment
  ((into #{} [1 2 3]) 1)
  (delete-entry 2)
  (delete-entries 2)
  (populate-db)
  (add-entry "hahahei"))

;; (def db (->Restful))

;; (defn db-add! [e]
;;   (swap! app-state update-in [:db] conj e)
;;   (add! db e))
;; (defn db-delete! [e]
;;   (swap! app-state update-in [:db] (constantly []))
;;   (delete! db :all))
;; (defn db-get [e]
;;   (get-in @app-state [:db]))
;; (defn populate-db []
;;   (get! db :all (fn [r] (swap! app-state update-in [:db] (constantly (get (js->clj (js/JSON.parse r)) "entries"))))))

;; (connect! db)
;; ;; (add! db "haha")
;; ;; (add! db "haha")
;; ;; (add! db "haha")
;; (populate-db)

(comment
  (get! db :all)
  (add! db "haha")
  (delete! db)
  (db-add! "hello there"))

(defn get-app-element []
  (gdom/getElement "app"))

(defn body [text]
  [:div {:style {:border-style :solid
                 :border-width :0px
                 :border-radius :4px
                 :background-color :#E8E8A6
                 :display :inline-block
                 ;; :padding-left :4px
                 :padding :6px
                 ;; :padding-right :6px
                 }}text])

(def tag-style {:margin-right :8px
                ;; :border-style :solid
                ;; :border-width :1px
                :margin-top :4px
                :padding-left :2px
                :padding-right :2px
                :font-size :0.5em
                :color :#888888
                :background-color :#eeeeee
                :display :inline-block})

(def button-style {;; :margin-right :8px
                   :border-style :solid
                   :border-width :0px
                   :padding :4px
                   :margin-top :2px
                   :color :#219F94
                   :background-color :white
                   :display :inline-block})

(def user-style {:border-radius :4px
                 :border-width :0px
                 ;; :padding-left :4px
                 :padding :6px
                 ;; :padding-right :6px
                 :background-color :#C1DEAE
                 ;; :font-size :0.9em
                 :margin-right :1em
                 :border-style :solid})

(defn type' [text]
  [:div {:style tag-style} text])

(defn user [text m]
  [:span {:style user-style
          :on-click #(delete-entries (:entries-id m))} text])

(defn time' [text]
  [:span {:style tag-style} text])

(def container-style {:margin-top :1em
                      :width :100%
                      :position :relative})

(defn list-item [m]
  (let [{e-body :entries-body
         e-type :types-name
         e-user :users-name
         e-time :entries-time} m]
    [:div.list-item
     [:div
      {:style (merge container-style)}
      [user e-user m]
      [body e-body] [:div]
      [type' e-type]
      [time' e-time]
      ;; [:button.close-btn {:on-click #(delete-entries (:entries-id m))
      ;;                     :style (merge
      ;;                             ;; button-style
      ;;                             {;; :position :absolute
      ;;                                                 ;; :right :0
      ;;                                                 ;; :visibility :hidden
      ;;                              })}"xssssssssss"]
      ]

     ;; (str m)
     ]))

(defn list' [data]
  [:div.list (for [item data]
               ^{:key (:entries-id item)}
               [list-item item])])

(defn history []
  [:div#hist {:style {:overflow-y :auto
                      :height :75vh}}
   [list' (get-in @app-state [:db])]])

(defn input-form []
  [:form {:on-submit (fn [e]
                       (.preventDefault e)
                       (let [to-send (get-in @app-state [:to-send])]
                         (when (not (clojure.string/blank? to-send))
                           (add-entry to-send)
                          ;; (assoc-in @app-state [:to-send] "")
                           (swap! app-state assoc-in [:to-send] "")
                           (set! (.-value (.getElementById js/document "msg-box")) "")
                           (let [hist-div (.getElementById js/document "hist")]
                             (js/setTimeout #(set! (.-scrollTop hist-div) (.-scrollHeight hist-div)) 100)))))}
   ;; [:div (str "To send: " (get-in @app-state [:to-send]))]
   (comment
     (def hist-div (.getElementById js/document "hist"))
     (set! (.-scrollHeight hist-div) (.-scrollTop hist-div))
     (aset hist-div "scrollTop" (.. hist-div -scrollHeight)))
   [:input#msg-box {:type "text"
                    :style {:margin-top :1em :margin-right :1em
                            :width :90%
                            :border :none
                            :height :30px
                            :border-radius :4px
                            :background :#E8E8A6}
            ;; :value (get-in @app-state [:to-send])
                    :on-input #(swap! app-state assoc-in [:to-send] (-> % .-target .-value))}]
   [:input {:type "submit" :value "Send"
            :style (merge
                    {:width :7%
                     :height :30px
                     :border :none
                     :border-radius :4px
                     :color :white
                     :background-color :#219F94})}]])

(defn app []
  [:div
   {:style {:width :720px
            :margin :auto}}
   [:h1 "Lan Share"]
   [:button {:style button-style
             :on-click (fn [_]
                         (apply delete-entries
                                (map #(:entries-id %)
                                     (get-in @app-state [:db]))))}
    "Clear All"]
   [history]
   [input-form]])

(defn mount [el]
  (rdom/render [app] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
