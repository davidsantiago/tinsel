(ns hew.core
  "Enlive-style templates with Hiccup."
  (:use [hiccup core])
  (:require [hew.utils :as utils]
            [pl.danieljanus.tagsoup :as ts]
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
  (let [normalized-new-content (if (vector? new-content)
                                 (utils/normalize-form new-content)
                                 new-content)]
    `(fn [node#]
       (vector (first node#)
               (second node#)
               (quote ~normalized-new-content)))))

(defmacro append-content
  [new-content]
  (let [normalized-new-content (if (vector? new-content)
                                 (utils/normalize-form new-content)
                                 new-content)]
    `(fn [node#]
       (conj node# (quote ~normalized-new-content)))))

(defmacro prepend-content
  [new-content]
  (let [normalized-new-content (if (vector? new-content)
                                 (utils/normalize-form new-content)
                                 new-content)]
    `(fn [node#]
       (apply vector
              (first node#)
              (second node#)
              (quote ~normalized-new-content)
              (rest (rest node#))))))

(defmacro set-attrs
  [attr-map]
  `(fn [node#]
     (vector (first node#)
             (merge (second node#)
                    (quote ~attr-map)))))


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
    (if (zip/end? loc)
      (zip/root loc)
      (recur (zip/next (if (and (vector? (zip/node loc))
                                (select? loc))
                         (zip/rightmost (zip/down (zip/edit loc transform)))
                         loc))))))

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

;;
;; Template Loading
;;

(defn html-file
  "Parse HTML out of the argument (which can be anything accepted by
   clojure.contrib's reader function)."
  [file-path]
  (vector (ts/parse file-path)))

(defn html-string
  "Parse HTML out of the string given."
  [html-string]
  (vector (ts/parse-string html-string)))

(defn hiccup-file
  "Parse hiccup forms out of the argument."
  [file-path]
  (vector (load file-path)))

(defn hiccup-string
  "Parse hiccup forms out of the string argument."
  [hiccup-string]
  (vector (load-string hiccup-string)))