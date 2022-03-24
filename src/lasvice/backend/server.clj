(ns lasvice.backend.server
  (:gen-class)
  (:require
   [compojure.core :as c :refer [defroutes GET POST PUT DELETE]]
   [compojure.route :as route]
   [ring.adapter.jetty :as r :refer [run-jetty]]
   [ring.util.response :as resp :refer [response]]
   [ring.middleware.json :refer [wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [clojure.data.json :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.date-time :as dt]
   [honey.sql :as sql]
   [honey.sql.helpers :as h]
   [clojure.string :as str]
   ;; [ring.middleware.cors :refer [wrap-cors]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
   [clojure.java.io :as io]))

;; Read the jdbcClob as String
(extend-protocol rs/ReadableColumn
  java.sql.Clob
  (read-column-by-label [^java.sql.Clob v _]
    (rs/clob->string v))
  (read-column-by-index [^java.sql.Clob v _2 _3]
    (rs/clob->string v)))
;; Auto convert sql datetime to local date time
(dt/read-as-local)

(def user (atom "user"))

(def db {:dbtype "h2" :dbname "app"})
(def ds (jdbc/get-datasource db))
(defn create-tables []
  (jdbc/execute! ds (-> (h/create-table :types)
                        (h/with-columns [[:id :int :auto-increment [:primary-key]]
                                         [:name [:varchar 64] [:not :null] :unique]])
                        (sql/format)))
  (jdbc/execute! ds (-> (h/create-table :users)
                        (h/with-columns [[:id :int :auto-increment [:primary-key]]
                                         [:name [:varchar 64] [:not :null] :unique]])
                        (sql/format)))
  (jdbc/execute! ds (-> (h/create-table :entries)
                        (h/with-columns [[:id :int :auto-increment [:primary-key]]
                                         [:body :text]
                                         [:type :int]
                                         [:user :int]
                                         [:time :datetime [:default [:now]]]
                                         [[:foreign-key :type]
                                          [:references :types :id] :on :delete :cascade]
                                         [[:foreign-key :user]
                                          [:references :users :id] :on :delete :cascade]])
                        (sql/format))))

(defn setup-init-data []
  (jdbc/execute! ds (-> (h/insert-into :types)
                        (h/columns :name)
                        (h/values [["text"]
                                   ["file"]
                                   ["image"]
                                   ["video"]
                                   ["sound"]])
                        (sql/format)))
  (jdbc/execute! ds (-> (h/insert-into :users)
                        (h/columns :name)
                        (h/values [[@user]])
                        (sql/format)))
  (jdbc/execute! ds (-> (h/insert-into :entries)
                        (h/columns :body :type :user)
                        (h/values [["The first snippet of text."
                                    (-> (h/select :id)
                                        (h/from [:types])
                                        (h/where := "text" :name))
                                    (-> (h/select :id)
                                        (h/from [:users])
                                        (h/where := @user :name))]
                                   ["The second snippet of text."
                                    (-> (h/select :id)
                                        (h/from [:types])
                                        (h/where := "text" :name))
                                    (-> (h/select :id)
                                        (h/from [:users])
                                        (h/where := @user :name))]])
                        (sql/format))))

(defn add-entry [entry]
  (jdbc/execute! ds (-> (h/insert-into :entries)
                        (h/columns :body :type :user)
                        (h/values [[(str (:body entry))
                                    (-> (h/select :id)
                                        (h/from [:types])
                                        (h/where := (str (:type entry)) :name))
                                    (-> (h/select :id)
                                        (h/from [:users])
                                        (h/where := (str (:user entry)) :name))]])
                        (sql/format))))

(defn delete-entries [ids]
  (jdbc/execute! ds (-> (h/delete-from :entries)
                        ;; (h/where [:= :id id])
                        (h/where [:in :id ids])
                        (sql/format))))

;; (defn delete-entries [ids]
;;   (-> (h/delete-from :entries)
;;       ;; (h/where [:= :id id])
;;       (h/where [:in :id ids])
;;       (sql/format)))
(comment
  (-> (h/delete-from :entries)
      (h/where [:in :id [1 2 3]])
      (sql/format))
  (delete-entries [1]))

(defn java-datetime->string [dt]
  (-> dt
      (.toString)
      (str/split #"\.")
      (first)
      (str/split #"T")
      (#(str/join " " %))))

;; transform keyword in oder not to lise info using json
(defn transform-keyword [kw]
  (->> kw
       (str/lower-case)
       (map #(if (= % \/) \- %))
       (drop 1)
       (str/join)
       (keyword)))

(defn get-all-entries []
  (->> (jdbc/execute! ds (-> (h/select :e.id :e.body :t.name :u.name :e.time)
                             (h/from [:entries :e])
                             (h/join-by (h/join [:types :t]
                                                [:= :e.type :t.id])
                                        (h/join [:users :u]
                                                [:= :e.user :u.id]))
                             (sql/format)))
       (map #(update-in % [:ENTRIES/TIME] java-datetime->string))
       (map #(into {} (for [[k v] %]
                        [(transform-keyword k) v])))))

(comment
  (get-all-entries)
  (-> (first (get-all-entries))
      (:ENTRIES/TIME)
      (.toString)
      (str/split #"\.")
      (first)
      (str/split #"T")
      (#(str/join " " %)))
  ;
  )

(defroutes handler
  (GET "/" []
    (some-> (resp/resource-response "index.html" {:root "public"})
            (resp/content-type "text/html; charset=utf-8")))
  ;; (GET "/cljs-out/dev-main.js" [] (response/))
  (GET "/api/entries" [] (response {:entries (get-all-entries)}))
  (DELETE "/api/entries" {:keys [params]} (do
                                            ;; (println params)
                                            ;; (println (type params))
                                            ;; (println (:ids params))
                                            ;; (println (type (:ids params)))
                                            ;; (println (read-string (:ids params)))
                                            ;; (println (type (read-string (:ids params))))
                                            ;; (println (type (first (read-string (:ids params)))))
                                            ;; (println (delete-entries (read-string (:ids params))))
                                            (delete-entries (read-string (:ids params)))
                                            (response {:res "cool!"})))
  (POST "/api/entries" {:keys [params]} (do
                                          (add-entry params)
                                          (response {:res "yeah!"})))
  (GET "/api/user" [] (do
                        (response {:username @user})))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app (-> handler
             ;; (wrap-defaults site-defaults)
             (wrap-defaults api-defaults)
             wrap-json-response
             wrap-params
             wrap-multipart-params))

(defn username-exists? [username]
  (seq (jdbc/execute! ds (-> (h/select :name)
                             (h/from [:users])
                             (h/where [:= :name username])
                             (sql/format)))))
(defn add-username [username]
  (jdbc/execute! ds (-> (h/insert-into :users)
                        (h/columns :name)
                        (h/values [[username]])
                        (sql/format))))
(comment
  (username-exists? "lasv")
  (add-username "lll")
  (username-exists? "lll")
  (username-exists? "user")
  (seq [])
  (seq [1])
  @user)

(defn -main [& args]
  (when-let [username (first args)]
    (reset! user username))
  (when-not (.exists (io/file "app.mv.db"))
    (create-tables)
    (setup-init-data))
  (when-not (username-exists? @user)
    (add-username @user))
  (run-jetty #'app {:port 3000 :join? false}))

(comment (-main))

;; ;;; Allow cors
;; (def allowed-origins [#"https://localhost:9500" #"https://localhost" #"https://127.0.0.1"])
;; (def allowed-methods [:get :post :put :delete])
;; (def allowed-headers #{:accept :content-type})
;; ;; my-routes already defined somewhere
;; (def handler
;;   (wrap-cors app :access-control-allow-origin allowed-origins
;;              :access-control-allow-methods allowed-methods
;;              :access-control-allow-headers allowed-headers))

;; (def my-server (run-jetty (wrap-defaults #'app site-defaults) ; very important to add #'!! to support reloading from repl
;;                           {:port 3000
;;                            :join? false}))

(comment
  (.stop my-server))

(comment

  (jdbc/execute! ds (-> (h/select :*)
                        (h/from :types)
                        (sql/format)))
  (jdbc/execute! ds (-> (h/select :*)
                        (h/from :users)
                        (sql/format)))
  (jdbc/execute! ds (-> (h/select :*)
                        (h/from :entries)
                        (sql/format)))
;
  )

