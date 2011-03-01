(ns test-parse
  (:use hew.core
        clojure.test)
  (:require [clojure.zip :as zip]))

(deftest test-parse-html-string
  (is (= [[:html {}]]
           (html-string "<html></html>"))))

(deftemplate html-template (html-string "<html></html>")
  [arg-map]
  (tag= :html)
  (set-content [:head [:title (:msg arg-map)]]))

(deftest test-html-template
  (is (= "<html><head><title>Wow, that worked!</title></head></html>"
         (html-template {:msg "Wow, that worked!"}))))

(deftemplate hiccup-template [[:html]]
  [arg-map]
  (tag= :html)
  (set-content [:head [:title (:msg arg-map)]]))

(deftest test-hiccup-template
  (is (= "<html><head><title>Wow, that worked!</title></head></html>"
         (hiccup-template {:msg "Wow, that worked!"}))))

;; To make sure deftemplate correctly evaluates code forms for source.
(def some-hiccup-src [[:html [:head [:title]]]])

(deftemplate hiccup-template-in-var some-hiccup-src
  [arg-map]
  (tag= :title)
  (set-content (:title arg-map)))

(deftest test-hiccup-template-in-var
  (is (= "<html><head><title>TITLE</title></head></html>"
         (hiccup-template-in-var {:title "TITLE"}))))