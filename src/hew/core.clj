(ns hew.core
  "Enlive-style templates with Hiccup."
  (:use [hiccup core])
  (:require [hew.utils :as utils]
            [clojure.zip :as zip]
            [clojure.walk :as walk]))

;;
;; Selectors
;;
;; Note: Selectors are functions of a zipper location that return true if that
;; hiccup node is the one of interest. They do not execute in the environment
;; of the template; that is, your template arguments are not available. Thus,
;; selections must be based entirely on properties of the template itself.

(defn tag=
  "Returns a function that returns true if the node has tag equal to arg."
  [tag]
  (fn [zip-loc]
    (= (utils/name tag)
       (utils/name (first (zip/node zip-loc))))))

(defn id=
  "Returns a function that returns true if the node has id equal to id."
  [id]
  (fn [zip-loc]
    (let [res (= (utils/name id)
                 (utils/name (:id (second (zip/node zip-loc)))))]
      res)))

;;
;; Transformers
;;
;; Note: Transformers are functions of a node that return the new version of
;; that node taking into account the user's desired transformation. They do
;; execute in the environment of the template, so template arguments are
;; available. However, keep in mind that the output is a hiccup form that
;; will be compiled and run later, at runtime. Code that should run at
;; template runtime should be expressed as a form in hiccup.

(defmacro set-content
  [new-content]
  `(fn [node#]
     (vector (first node#)
             (second node#)
             (quote ~new-content))))

(defmacro append-content
  [new-content]
  `(fn [node#]
     (conj node# (quote ~new-content))))

(defmacro prepend-content
  [new-content]
  `(fn [node#]
     (apply vector
            (first node#)
            (second node#)
            (quote ~new-content)
            (rest (rest node#)))))

;;
;; Compiler
;;
;; Note: Everything here runs at template compile time (that is, macro
;; expansion, and not during the template's render time).

(defn apply-transform
  "Given a transform (a selector function and a tranformation function), applies
   it where the selector dictates to the form given."
  [[select? transform] form]
  ;; Iterate through all the nodes in depth-first order, replacing any applicable.
  (loop [loc (zip/vector-zip form)]
    ;; If this node is selected by the selector, transform it.
    (let [transformed-loc (if (and (vector? (zip/node loc))
                                   (select? loc))
                            (zip/edit loc transform)
                            loc)]
      (if (zip/end? transformed-loc)
        (zip/root transformed-loc)
        (recur (zip/next transformed-loc))))))

(defn apply-transforms
  "transform-list is a list of pairs of functions. The first in each pair is a
   selector function; it returns true if the node is one of interest. The second
   is the transformer function; it is applied to nodes whose selector is true.
   The argument forms is a list of hiccup forms to apply all the transformations
   to in order."
  [transform-list forms]
  (if (empty? transform-list)
    forms
    (recur (rest transform-list)
           (doall (for [form forms]
                    (apply-transform (first transform-list) form))))))

(defmacro deftemplate
  [tmpl-name source arg-list & transforms]
  (let [source-forms (map utils/normalize-form
                          source) ;; ["tag" {attrs} content...]
        transforms (partition 2 (map eval transforms))
        transformed-forms (apply-transforms transforms source-forms)]
    `(defn ~tmpl-name
       ~arg-list
       (html ~@transformed-forms))))