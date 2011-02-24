(ns hew.core
  "Enlive-style templates with Hiccup."
  (:use [hiccup core])
  (:require [clojure.zip :as zip]))

;;
;; Compiler
;;
;; Note: Everything here runs at template compile time (that is, macro
;; expansion, and not during the template's render time.

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
   it where the selector dictates to the forms given."
  [[select? transform] forms]
  ;; Iterate through all the nodes in depth-first order, replacing any applicable.
  (loop [loc (zip/vector-zip forms)]
    ;; If this node is selected by the selector, transform it.
    (prn "Before transform: " (zip/root loc))
    (let [transformed-loc (if (and (vector? (zip/node loc))
                                   (select? (zip/node loc)))
                            (zip/edit loc transform)
                            loc)]
      (prn "After transform: " (zip/root transformed-loc))
      (if (do (prn "About to check zip/end?")
              (prn transformed-loc)
              (zip/end? transformed-loc))
        (do (prn "It was the end!")
            (prn "Final result: " (zip/root transformed-loc))
            (zip/root transformed-loc))
        (do (prn "It was not the end!")
            (prn "Next is: " (zip/next transformed-loc))
            (recur (zip/next transformed-loc)))))))

(defn apply-transforms
  [transform-list forms]
  (if (empty? transform-list)
    (do (prn "After transform: " forms)
        forms)
    (recur (rest transform-list)
           (apply-transform (first transform-list) forms))))

(defmacro deftemplate
  [tmpl-name source arg-list & transforms]
  (let [source-forms source
        transforms (partition 2 (map eval transforms))
        transformed-forms (apply-transforms transforms source-forms)]
    `(defn ~tmpl-name
       ~arg-list
       (html ~@transformed-forms))))
