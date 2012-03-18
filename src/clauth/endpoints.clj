(ns clauth.endpoints
  (:use   [clauth.token])
  (:use   [clauth.client])
  (:use   [cheshire.core])
  (:import [org.apache.commons.codec.binary Base64]))



(defn decorate-token 
  "Take a token map and decorate it according to specs

  http://tools.ietf.org/html/draft-ietf-oauth-v2-25#section-5.1"
  [token]

  { :access_token (:token token) :token_type "bearer"}
  )

(defn token-response 
  "Create a ring response for a token response"
  [token]
  {  :status 200
      :headers {"Content-Type" "application/json"}
      :body (generate-string (decorate-token token))})

(defn error-response 
  "Create a ring response for a oauth error"
  [error]
  {  :status 400
      :headers {"Content-Type" "application/json"}
      :body (generate-string {:error error })})

(defn respond-with-new-token
  "create a new token and respond with json"
  [client owner]
  (token-response (create-token client owner)))

(defn basic-authentication-credentials 
  "decode basic authentication credentials.

   If it exists it returns a vector of username and password.

   If not nil."
  [req]
  (if-let [ basic-token (last (re-find #"^Basic (.*)$" ((req :headers {}) "authorization" ""))) ]
    (if-let [ credentials (String. (Base64/decodeBase64 basic-token))]
      (clojure.string/split credentials #":" )
      )))

(defn client-authenticated-request 
  "Check that request is authenticated by client either using Basic authentication or url form encoded parameters.

   The client_id and client_secret are checked against the authenticate-client function.

   If authenticate-client returns a client map it runs success function with the request and the client."
  [req authenticator success]
  (let [ basic (basic-authentication-credentials req)
         client_id (if basic (first basic) ((req :params ) "client_id"))
         client_secret (if basic (last basic) ((req :params) "client_secret"))
         client (authenticator client_id client_secret)]
          (if client 
            (success req client)
            (error-response "invalid_client"))))

(defn grant-type
  "extract grant type from request"
  [req _] ((req :params) "grant_type"))

(defmulti token-request-handler grant-type)

(defmethod token-request-handler "client_credentials" [req authenticator]
  (client-authenticated-request 
    req 
    authenticator
    (fn [req client] (respond-with-new-token client client))))

(defmethod token-request-handler :default [req authenticator]
  (error-response "unsupported_grant_type"))


(defn token-handler
  ([]
    (token-handler clauth.client/authenticate-client))
  ([authenticator]
    (fn [req]
      (token-request-handler req authenticator)
      )))
 