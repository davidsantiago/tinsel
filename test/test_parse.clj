(ns test-parse
  (:use hew.core
        clojure.test)
  (:require [clojure.zip :as zip]))

(deftest test-parse-html-string
  (is (= [[:html {}]]
           (html-string "<html></html>"))))

(deftest test-parse-hiccup-string
  (is (= [[:html]]
           (hiccup-string "[:html]"))))

(deftemplate html-template [[:html [:body#a]]] ;  (html-string "<html></html>")
  [arg-map]
  (tag= :html)
  (set-content [:head [:title (:msg arg-map)]]))

(deftest test-html-template
  (is (= "<html><head><title>Wow, that worked!</title></head></html>"
         (html-template {:msg "Wow, that worked!"}))))
