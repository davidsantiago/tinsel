(ns test-hew
  (:use hew.core
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
(deftemplate set-content-template
  [[:body [:h1#replace-me "Replace me!"]]]
  [arg-map]
  (id= :replace-me)
  (set-content (:replacement arg-map)))

(deftest test-set-content-template
  (is (= "<body><h1 id=\"replace-me\">You have been replaced.</h1></body>"
         (set-content-template {:replacement "You have been replaced."}))))

;; Append content
(deftemplate append-content-template
  [[:body [:ul#add-to-me
           [:li "Add something after me!"]]]]
  [arg-map]
  (id= :add-to-me)
  (append-content (:addition arg-map)))

(deftest test-append-content-template
  (is (= "<body><ul id=\"add-to-me\"><li>Add something after me!</li><li>Ohai!</li></ul></body>"
         (append-content-template {:addition [:li "Ohai!"]}))))

;; Prepend content
(deftemplate prepend-content-template
  [[:body [:ul#add-to-me
           [:li "Add something before me!"]]]]
  [arg-map]
  (id= :add-to-me)
  (prepend-content (:addition arg-map)))

(deftest test-prepend-content-template
  (is (= "<body><ul id=\"add-to-me\"><li>Ohai!</li><li>Add something before me!</li></ul></body>"
         (prepend-content-template {:addition [:li "Ohai!"]}))))