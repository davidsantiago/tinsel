(ns hew.core
  "Enlive-style templates with Hiccup."
  (:use [hiccup core])
  (:require [clojure.zip :as zip]
            [clojure.walk :as walk]))

;;
;; Compiler
;;
;; Note: Everything here runs at template compile time (that is, macro
;; expansion, and not during the template's render time).

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapted from hiccup/core.clj for compatibility.
(def ^{:private true
       :doc "Regular expression that parses a CSS-style id and class from a tag name."}
     re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn normalize-element
  "Ensure a tag vector is of the form [tag-name attrs content1...contentN].
   This is not quite the same as how hiccup normalizes, but it is much easier
   for us to work with here."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (IllegalArgumentException. (str tag " is not a valid tag name."))))
  (let [[_ tag id class] (re-matches re-tag (name tag))
        tag-attrs        {:id id
                          :class (if class (.replace ^String class "." " "))}
        map-attrs        (first content)]
    (if (map? map-attrs)
      (apply vector tag (merge tag-attrs map-attrs) (next content))
      (apply vector tag tag-attrs content))))
 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalize-form
  "Given a hiccup form, recursively normalizes it using normalize-element."
  [form-list]
  (walk/postwalk #(if (vector? %)
                    (normalize-element %)
                    %)
                 form-list))

(defn code-form?
  "Takes a form and returns true if it is a 'code' form. In other words,
   it is attempting to express code to be executed for its content, instead
   of being a marker for hiccup to process into HTML."
  [form]
  (and (sequential? form) ;; (seq? {}) -> true, (sequential? {}) -> false
       (not (vector? form))
       (not= 'quote (first form))))

(comment (defn transform-forms
           "This function goes through the forms passed in and makes modifications to
   the structure so that it becomes correctly executable code. For now, this
   means adding the map argument to template functions."
           [forms arg-name]
           (postwalk (partial syntax-transformer arg-name)
                     forms)))

(defn apply-transform
  "Given a transform (a selector function and a tranformation function), applies
   it where the selector dictates to the list of forms given."
  [[select? transform] forms]
  ;; Iterate through all the nodes in depth-first order, replacing any applicable.
  (loop [loc (zip/vector-zip forms)]
    ;; If this node is selected by the selector, transform it.
    (let [transformed-loc (if (and (vector? (zip/node loc))
                                   (select? (zip/node loc)))
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
    (do (prn "After all transforms: " forms)
        forms)
    (recur (rest transform-list)
           (apply-transform (first transform-list) forms))))

(defmacro deftemplate
  [tmpl-name source arg-list & transforms]
  (let [source-forms (map normalize-form source)
        transforms (partition 2 (map eval transforms))
        transformed-forms (apply-transforms transforms source-forms)]
    `(defn ~tmpl-name
       ~arg-list
       (html ~@transformed-forms))))
