(ns tinsel.core
  "Enlive-style templates with Hiccup."
  (:use [hiccup core])
  (:require [tinsel.utils :as utils]
            [tinsel.zip :as tzip]
            [pl.danieljanus.tagsoup :as ts]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clojure.walk :as walk]))

;;
;; Selectors
;;
;; Note: Selectors are functions of a zipper location that return a zipper
;; location if a node satisfies their requirements, or nil if it does not.
;; In general, the location returned should be "the location that satisfied
;; the requirement," although what exactly that means is specific to the
;; selector. Probably most selectors should return the same location passed
;; in, though. Exceptions would be selectors for properties of ancestors,
;; siblings, etc.

;; They do not execute in the environment
;; of the template; that is, your template arguments are not available. Thus,
;; selections must be based entirely on properties of the template itself.

(defn tag=
  "Returns a function that returns true if the node has tag equal to arg."
  [tag]
  (fn [zip-loc]
    (if (= (utils/name tag)
           (utils/name (utils/tag (zip/node zip-loc))))
      zip-loc)))

(defn has-attr?
  "Returns a function that returns true if the node has the given attribute
   (with any value). Argument can be string/keyword/symbol."
  [attr-name]
  (fn [zip-loc]
    (if (contains? (utils/attrs (zip/node zip-loc))
                   (keyword attr-name))
      zip-loc)))

(defn attr=
  "Returns a function that returns true if the node has the given attribute
   with the given value."
  [attr-name attr-value]
  (fn [zip-loc]
    (if (= ((keyword attr-name) (utils/attrs (zip/node zip-loc)))
           attr-value)
      zip-loc)))

(defn id=
  "Returns a function that returns true if the node has id equal to id."
  [id]
  (fn [zip-loc]
    (if (= (utils/name id)
           (utils/name (:id (utils/attrs (zip/node zip-loc)))))
      zip-loc)))

(defn has-class?
  "Returns a function that returns true if the node has the given class."
  [class]
  (fn [zip-loc]
    (let [class-str (:class (utils/attrs (zip/node zip-loc)))
          classes (if class-str
                    (apply hash-set (str/split class-str #" ")))]
      (if (contains? classes (utils/name class))
        zip-loc))))

(defn nth-child?
  "Returns a function that returns true if the node is the nth child of
   its parent (and it has a parent). First element is 1, last is n."
  [n]
  (fn [zip-loc]
    ;; We create move-to-nth to be a single function to move right n times.
    ;; Because we have to subtract 1 from n, we might have movements = '().
    ;; Obviously, comp won't like that, so we make the first arg be identity.
    (let [movements (repeat (- n 1) zip/right)
          move-to-nth (apply comp identity movements)
          nth-child (move-to-nth (zip/leftmost zip-loc))]
      (if (and (= zip-loc nth-child)
               (zip/up zip-loc)) ;; Check for parent.
        zip-loc))))

(defn nth-last-child?
  "Returns a function that returns true if the node is the nth last child
   of its parent (and it has a parent)."
  [n]
  (fn [zip-loc]
    ;; The left has no padding elements, so we just subtract 1 as expected.
    ;; Again, identity is so there is something to apply to when n=1.
    (let [movements (repeat (- n 1) zip/left)
          move-to-nth-last (apply comp identity movements)
          nth-last-child (move-to-nth-last (zip/rightmost zip-loc))]
      (if (and (= zip-loc nth-last-child)
               (zip/up zip-loc)) ;; Check for parent.
        zip-loc))))

;;
;; Selector combinators
;;
;; These selectors take other selectors as arguments, returning compound
;; selectors.

(defn every-selector
  "Takes any number of selectors and returns a selector that is true if
   all of the argument selectors are true."
  [& selectors]
  (fn [zip-loc]
    (if (every? #(% zip-loc) selectors)
      zip-loc)))

(defn some-selector
  "Takes any number of selectors and returns a selector that is true if
   any of the argument selectors are true."
  [& selectors]
  (fn [zip-loc]
    (if (some #(% zip-loc) selectors)
      zip-loc)))

(defn select
  "Takes any number of selectors as arguments and selects nodes that match
   the path down the tree specified. Returns the loc of the matched node."
  [& selectors]
  (fn [zip-loc]
    (loop [curr-loc zip-loc
           selectors (reverse selectors)] ;; Start at end and work back up tree.
      (if (empty? selectors)
        zip-loc ;; Got this far satisfying selectors, return the selected node.
        (if-let [next-loc ((first selectors) curr-loc)]
          (recur (zip/up next-loc)
                 (rest selectors))
          nil))))) ;; Didn't match, so we short circuit and return nil.

(defn or-ancestor
  "Takes a selector as argument and returns a selector that will match any
   node for which either that node or some ancestor node in the tree matches
   the argument selector. Returns the loc of the matching ancestor node."
  [selector]
  (fn [zip-loc]
    (loop [curr-loc zip-loc]
      (let [next-loc (selector curr-loc)] ;; Check if selector matches here.
        (if next-loc
          next-loc ;; Matched, return matching node.
          ;; Didn't match, so recur iff the parent node exists.
          (if-let [parent-loc (zip/up curr-loc)]
            (recur parent-loc)))))))




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
       (vector (utils/tag node#)
               (utils/attrs node#)
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
              (utils/tag node#)
              (utils/attrs node#)
              (quote ~normalized-new-content)
              (utils/contents node#)))))

(defmacro set-attrs
  [attr-map]
  `(fn [node#]
     (apply vector
            (utils/tag node#)
            `(merge (utils/attrs ~node#)
                    ~'~attr-map)
            (utils/contents node#))))

;;
;; Transformer Combinators
;;
;; These transformers take other transformers as arguments, returning a
;; compound transformer.

(defn accumulate
  "Takes a sequence of transformers as argument and returns a transformer that
   applies them all in order to a node, using the output of the previous as
   the input to the next."
  [& transformers]
  (apply comp (reverse transformers)))


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
  (loop [loc (tzip/postorder-first (tzip/hiccup-zip form))]
    ;; If this node is selected by the selector, transform it.
    (if (zip/end? loc)
      (zip/root loc)
      (recur (tzip/postorder-next
              (if (and (vector? (zip/node loc))
                       (select? loc))
                (zip/edit loc transform)
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
  (let [source (if (utils/code-form? source) ;; Need to force eval if source is
                 (eval source)               ;; not hiccup vectors.
                 source)
        source-forms (map utils/normalize-form ;; ["tag" {attrs} content...]
                          source)
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