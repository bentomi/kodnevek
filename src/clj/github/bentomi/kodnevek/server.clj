(ns github.bentomi.kodnevek.server
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
            [github.bentomi.kodnevek.game :as game]
            [github.bentomi.kodnevek.words :as words]
            [github.bentomi.kodnevek.ws :as ws]))

(defn- index [config request]
  (->  (hpage/html5
        [:head
         [:meta {:charset "UTF-8"}]
         (hpage/include-css "css/style.css")]
        [:body
         [:div#app]
         (hpage/include-js (::main-script config))])
       resp/response
       (resp/content-type "text/html; charset=utf-8")))

(defn- get-languages [config _request]
  (let [wp (::words/provider config)]
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
   #{["/" :get (partial index config) :route-name :index]
     ["/languages"
      :get [coerce-body content-neg-intc (partial get-languages config)]
      :route-name :languages]}))

(defn- ws-paths [config]
  {"/ws" {:on-connect (pws/start-ws-connection ws/new-ws-client)
          :on-text (partial ws/handle-message (::event-handler config))
          :on-binary (fn [payload offset length]
                       (log/warn :msg "Binary Message!" :bytes payload))
          :on-error (fn [t] (log/error :msg "WS Error happened" :exception t))
          :on-close (fn [num-code reason-text]
                      (log/info :msg "WS Closed:" :reason reason-text))}})

(defn- create-server [config]
  (-> {::http/routes (routes config)
       ::http/mime-types mime/default-mime-types
       ::http/secure-headers {:content-security-policy-settings
                              "object-src 'none'; default-src 'self'"}
       ::http/container-options {:context-configurator
                                 #(pws/add-ws-endpoints % (ws-paths config))}}
      (merge config)
      http/default-interceptors
      http/create-server))

(defmethod ig/init-key ::container [_key config]
  (-> config create-server http/start))

(defmethod ig/halt-key! ::container [_key server]
  (http/stop server))
