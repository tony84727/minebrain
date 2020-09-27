(ns minebrain.chat
  (:require [clojure.core.async :as async])
  (:import [com.github.tony84727.proto ChatGrpc]
           [io.grpc ManagedChannel ManagedChannelBuilder]
           [io.grpc.stub StreamObserver]))

(defonce connection (atom nil))

(defn stream-observer
  [out]
  (reify StreamObserver
    (onNext [this message] (async/>!! out message))
    (onCompleted [this] (async/close! out))
    (onError [this throwable] (async/close! out) (throw throwable))))



(defn do-connect [message-in chat-event-out]
  (let [channel (-> (ManagedChannelBuilder.)
                    (.forTarget "localhost:30000")
                    (.usePlaintext)
                    (.build))
        stub (ChatGrpc/newBlockingStub channel)
        out-stream-observer (.connect stub (stream-observer chat-event-out))]
    (async/go-loop []
      (let [message (async/<! message-in)]
        (if (nil? message)
          (.onCompleted out-stream-observer)
          (do (.onNext out-stream-observer message)
              (recur)))))
    channel))

(defn connect! [message-in chat-event-out]
  (reset! connection
          (fn [previous]
            (when-not
             (nil? previous)
              (do (.shutdown previous) ))
            (do-connect message-in chat-event-out))))

(defn shutdown! []
  (reset! connection
          (fn [previous]
            (when-not (nil? previous) (.shutdown previous) nil))))
(defn receive-chat-event [event]
  (println event))

(defn start-listening []
  (let [out (async/chan)]
    (connect! nil out)
    (async/go-loop []
      (when-let [event (async/<! out)]
        (receive-chat-event event)))))
