(ns lasvice.frontend.db
  (:require [ajax.core :refer [GET POST]]))

(defonce storage (atom []))

(defprotocol DB
  (connect! [this])
  (get! [this target handler])
  (delete! [this target])
  (add! [this target]))

(defrecord Test []
  DB
  (connect! [this] nil)
  (get! [this target handler] @storage)
  (delete! [this target] (reset! storage []))
  (add! [this target] (swap! storage conj target)))

(defrecord Restful []
  DB
  (connect! [this] nil)
  (get! [this target handler] (GET "/api/entries" {:response-type :json
                                                   :handler handler}))
  (delete! [this target] nil)
  (add! [this target] nil))

(comment
  (->> (GET "https://localhost:3000/api/entries" {:response-format :json})
       (js/console.log))
  (.stringify js/JSON #js {:t 1})
  (+ 1 3)
;
  )
