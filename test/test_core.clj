(ns test-tinsel
  (:use tinsel.core
        clojure.test)
  ;; These two are just for testing, see test-ns-membership.
  (:require [clojure.string :as str]
            [clojure.zip :as zip])
  (:use clojure.walk))

;; The most basic test: one with no transformations.
(deftemplate no-transforms-template [[:h1 "Hi!"]] [])

(deftest test-no-transform-template
  (is (= "<h1>Hi!</h1>"
         (no-transforms-template))))

;; Slightly more advanced: a transformation, but not based on arguments.
(deftemplate simple-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  (fn [zip-loc] (= (first (zip/node zip-loc)) "title"))
  (fn [node] [:title "Cool Title"]))

(deftest test-simple-transform-template
  (is (= "<title>Cool Title</title><h1>Hi!</h1>"
         (simple-transform-template {}))))

;; A transformation, but based on template args.
(deftemplate argument-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  #(= (first (zip/node %)) "title")
  (fn [node] [:title '(:title arg-map)]))

(deftest test-argument-transform-template
  (is (= "<title>Cool Title</title><h1>Hi!</h1>"
         (argument-transform-template {:title "Cool Title"}))))

;; Two transformations.
(deftemplate multiple-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  #(= (first (zip/node %)) "title")
  (fn [node] [:title '(:title arg-map)])
  #(= (first (zip/node %)) "h1")
  (fn [node] [:h1 '(:header arg-map)]))

(deftest test-multiple-transform-template
  (is (= "<title>Cool Title</title><h1>A Header</h1>"
         (multiple-transform-template {:title "Cool Title"
                                       :header "A Header"}))))

;;
;; Test Selectors
;;


;; Select multiple nodes by tag.
(deftemplate select-tag-template
  [[:body#some-id [:h1] [:h1]]]
  [arg-map]
  (tag= :h1)
  (fn [node] [:h1 '(:heading arg-map)]))

(deftest test-select-tag-template
  (is (= "<body id=\"some-id\"><h1>HEADING</h1><h1>HEADING</h1></body>"
         (select-tag-template {:heading "HEADING"}))))

;; Select by id.
(deftemplate select-id-template
  [[:body#short-id [:h1 {:id "long-id"} "A heading."]]]
  [arg-map]
  (id= :long-id)
  (fn [node] [:h1 {:id "long-id"} '(:heading arg-map)]))

(deftest test-select-id-template
  (is (= "<body id=\"short-id\"><h1 id=\"long-id\">Some cool heading.</h1></body>"
         (select-id-template {:heading "Some cool heading."}))))


;;
;; Test Transformers
;;

;; Set content
(deftemplate set-content-template1
  [[:body [:h1#replace-me "Replace me!"]]]
  [arg-map]
  (id= :replace-me)
  (set-content (:replacement arg-map)))

(deftemplate set-content-template2
  [[:body [:h1#replace-me "Replace me!"]]]
  [arg-map]
  (id= :replace-me)
  (set-content [:em (:replacement arg-map)]))

;; Make sure we can replace the root node's content.
(deftemplate set-content-template3
  [[:html [:h1 "I'm going to be replaced!"]]]
  [arg-map]
  (tag= :html)
  (set-content [:h2 (:replacement arg-map)]))

(deftest test-set-content-template
  (is (= "<body><h1 id=\"replace-me\">You have been replaced.</h1></body>"
         (set-content-template1 {:replacement "You have been replaced."})))
  (is (= "<body><h1 id=\"replace-me\"><em>You have been replaced.</em></h1></body>"
         (set-content-template2 {:replacement "You have been replaced."})))
  (is (= "<html><h2>I'm the replacement.</h2></html>"
         (set-content-template3 {:replacement "I'm the replacement."}))))

;; Append content
(deftemplate append-content-template1
  [[:body [:ul#add-to-me
           [:li "Add something after me!"]]]]
  [arg-map]
  (id= :add-to-me)
  (append-content (:addition arg-map)))

(deftemplate append-content-template2
  [[:body [:ul#add-to-me
           [:li "Add something after me!"]]]]
  [addition]
  (id= :add-to-me)
  (append-content addition))

;; Make sure we can append content to an empty root node.
(deftemplate append-content-template3
  [[:html#doc]]
  [addition]
  (id= :doc)
  (append-content addition))

(deftest test-append-content-template
  (is (= "<body><ul id=\"add-to-me\"><li>Add something after me!</li><li>Ohai!</li></ul></body>"
         (append-content-template1 {:addition [:li "Ohai!"]})))
  (is (= "<body><ul id=\"add-to-me\"><li>Add something after me!</li><li>Ohai!</li></ul></body>"
         (append-content-template2 [:li "Ohai!"])))
  (is (= "<html id=\"doc\"><h1>The content.</h1></html>"
         (append-content-template3 [:h1 "The content."]))))

;; Prepend content
(deftemplate prepend-content-template1
  [[:body [:ul#add-to-me
           [:li "Add something before me!"]]]]
  [arg-map]
  (id= :add-to-me)
  (prepend-content (:addition arg-map)))

(deftemplate prepend-content-template2
  [[:body [:ul#add-to-me
           [:li "Add something before me!"]]]]
  [addition]
  (id= :add-to-me)
  (prepend-content addition))

;; Make sure we can prepend content to an empty root node.
(deftemplate prepend-content-template3
  [[:html#doc]]
  [addition]
  (tag= :html)
  (prepend-content addition))

(deftest test-prepend-content-template
  (is (= "<body><ul id=\"add-to-me\"><li>Ohai!</li><li>Add something before me!</li></ul></body>"
         (prepend-content-template1 {:addition [:li "Ohai!"]})))
  (is (= "<body><ul id=\"add-to-me\"><li>Ohai!</li><li>Add something before me!</li></ul></body>"
         (prepend-content-template2 [:li "Ohai!"])))
  (is (= "<html id=\"doc\"><h1>The content.</h1></html>"
         (prepend-content-template3 [:h1 "The content."]))))

;; Set Attributes
(deftemplate set-attribute-template1
  [[:body [:img]]]
  [arg-map]
  (tag= :img)
  (set-attrs {:src (:url arg-map)}))

(deftemplate set-attribute-template2
  [[:body [:img]]]
  [arg-url]
  (tag= :img)
  (set-attrs {:src arg-url}))

(deftest test-set-attribute-template
  (is (= "<body><img src=\"http://example.com/img.jpg\" /></body>"
         (set-attribute-template1 {:url "http://example.com/img.jpg"}))
      (= "<body><img src=\"http://example.com/img.jpg\" /></body>"
         (set-attribute-template2 "http://example.com/img.jpg"))))



