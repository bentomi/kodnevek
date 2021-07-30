(ns com.github.bentomi.kodnevek.server
  "HTTP server handling the routing."
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [jsonista.core :as json]
            [ring.util.mime-type :as mime]
            [ring.util.response :as resp]
            [hiccup.page :as hpage]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.content-negotiation :as conneg]
            [io.pedestal.http.jetty.websockets :as pws]
            [com.github.bentomi.kodnevek.words :as words]
            [com.github.bentomi.kodnevek.ws :as ws])
  (:import (org.eclipse.jetty.servlet ServletContextHandler)
           (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(defn- gzip-configurator [^ServletContextHandler ctx]
  (doto ctx (.setGzipHandler (GzipHandler.))))

#_(defn- env-page [_request]
  (-> (hpage/html5
       [:body
        [:table
         [:tbody
          (for [[k v] (sort-by key (System/getenv))]
            [:tr [:td [:pre k]] [:td [:pre v]]])]]])
      resp/response
      (resp/content-type "text/html; charset=utf-8")))

(defn- main-page [config app-attrs]
  (-> (hpage/html5
       [:head
        [:meta {:charset "UTF-8"}]
        (hpage/include-css "css/style.css")]
       [:body
        [:div#app app-attrs]
        (hpage/include-js (::main-script config))])
      resp/response
      (resp/content-type "text/html; charset=utf-8")))

(defn- index [config _request]
  (main-page config {}))

(defn- index-with-id [config attr request]
  (main-page config {attr (get-in request [:query-params :id])}))

(defn- get-languages [config _request]
  (let [wp (::word-provider config)]
    (resp/response (words/get-languages wp))))

(def ^:private supported-types
  ["application/json" "application/edn" "text/html" "text/plain"])

(def ^:private content-neg-intc
  (conneg/negotiate-content supported-types))

(defn- accepted-type [context]
  (get-in context [:request :accept :field] "text/plain"))

(defn- transform-content [body content-type]
  (case content-type
    "text/html" body
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/write-value-as-string body)))

(defn- coerce-to [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def ^:private coerce-body
  {:name ::coerce-body
   :leave (fn [context]
            (cond-> context
              (nil? (get-in context [:response :headers "Content-Type"]))
              (update :response coerce-to (accepted-type context))))})

(defn- routes [config]
  (route/expand-routes
   #{["/" :get #(index config %) :route-name :index]
     ["/open" :get #(index-with-id config :data-game-id %) :route-name :open]
     ["/join" :get #(index-with-id config :data-invite %) :route-name :join]
     ["/languages"
      :get [coerce-body content-neg-intc #(get-languages config %)]
      :route-name :languages]
     #_["/env" :get env-page :route-name :env]}))

(defn- ws-paths [config]
  (let [ws-handler (::ws-handler config)
        event-handler (::event-handler config)]
    {"/ws" {:on-connect (pws/start-ws-connection
                         (partial ws/new-ws-client ws-handler))
            :on-text (partial ws/handle-message ws-handler event-handler)
            :on-binary (fn [payload _offset _length]
                         (log/warn :msg "Binary Message!" :bytes payload))
            :on-error (fn [t] (log/error :msg "WS Error happened" :exception t))
            :on-close (partial ws/handle-close ws-handler)}}))

(defn- create-server [config]
  (-> {::http/routes (routes config)
       ::http/mime-types mime/default-mime-types
       ::http/secure-headers
       {:content-security-policy-settings
        "object-src 'none'; default-src 'self'"}
       ::http/container-options
       {:context-configurator
        #(-> %
             gzip-configurator
             (pws/add-ws-endpoints (ws-paths config)))}}
      (merge config)
      http/default-interceptors
      http/create-server))

(defmethod ig/init-key ::container [_key config]
  (-> config create-server http/start))

(defmethod ig/halt-key! ::container [_key server]
  (http/stop server))
