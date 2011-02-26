(ns test-hew
  (:use hew.core
        clojure.test)
  ;; These two are just for testing, see test-ns-membership.
  (:require [clojure.string :as str])
  (:use clojure.walk))

;; The most basic test: one with no transformations.
(deftemplate no-transforms-template [[:h1 "Hi!"]] [])

(deftest test-no-transform-template
  (is (= "<h1>Hi!</h1>"
         (no-transforms-template))))

;; Slightly more advanced: a transformation, but not based on arguments.
(deftemplate simple-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  (fn [node] (= (first node) "title"))
  (fn [node] [:title "Cool Title"]))

(deftest test-simple-transform-template
  (is (= "<title>Cool Title</title><h1>Hi!</h1>"
         (simple-transform-template {}))))

;; A transformation, but based on template args.
(deftemplate argument-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  #(= (first %) "title")
  (fn [node] [:title '(:title arg-map)]))

(deftest test-argument-transform-template
  (is (= "<title>Cool Title</title><h1>Hi!</h1>"
         (argument-transform-template {:title "Cool Title"}))))

;; Two transformations.
(deftemplate multiple-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  #(= (first %) "title")
  (fn [node] [:title '(:title arg-map)])
  #(= (first %) "h1")
  (fn [node] [:h1 '(:header arg-map)]))

(deftest test-multiple-transform-template
  (is (= "<title>Cool Title</title><h1>A Header</h1>"
         (multiple-transform-template {:title "Cool Title"
                                       :header "A Header"}))))

;; Select by id.
(deftemplate select-id-template
  [[:body#short-id [:h1 {:id "long-id"} "A heading."]]]
  [arg-map]
  (id= :long-id)
  (fn [node] [:h1 {:id "long-id"} '(:heading arg-map)]))

(deftest test-select-id-template
  (is (= "<body id=\"short-id\"><h1 id=\"long-id\">Some cool heading.</h1></body>"
         (select-id-template {:heading "Some cool heading."}))))