(ns tinsel.utils
  "Some utilities of use elsewhere."
  (:refer-clojure :exclude [name]))

(defn name
  "Given a string, keyword, or symbol, returns its name as a string.
   Otherwise, nil. This exists because clojure.core/name will blow up with a
   NullPointerException if you pass in nil and this easier than checking for
   nil everywhere."
  {:tag String}
  [x]
  (cond (string? x) x
        (instance? clojure.lang.Named x) (clojure.core/name x)
        :else nil))

(defn code-form?
  "Takes a form and returns true if it is a 'code' form. In other words,
   it is attempting to express code to be executed for its content, instead
   of being a marker for hiccup to process into HTML."
  [form]
  (or (symbol? form)
      (and (seq? form) ;; Macros return seqs, not lists.
           (not= `quote (first form)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adapted from hiccup/core.clj for compatibility.
(def ^{:private true
       :doc "Regular expression that parses a CSS-style id and class from a tag name."}
     re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn- normalize-element
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
  "Given a hiccup form, recursively normalizes it using
   normalize-element."
  [form]
  (if (string? form) ; Should we allow more here? Keywords?
    form
    ;; Do a pre-order walk and save the first two items, then do the children,
    ;; then glue them back together.
    (let [[tag attrs & contents] (normalize-element form)]
      (apply vector tag attrs (map #(if (vector? %) ;; Recurse only  on vec children.
                                      (normalize-form %)
                                      %)
                                   contents)))))

;;
;; Selector/Transformer building blocks
;;

(defn tag
  "Given a normalized hiccup node, returns the tag as a string."
  [node]
  (first node))

(defn attrs
  "Given a normalized hiccup node, returns the attribute map."
  [node]
  (second node))

(defn contents
  "Given a normalized hiccup node, returns the content as a seq."
  [node]
  (rest (rest node)))
