(ns tinsel.zip
  "Zipper support for tinsel."
  (:require [clojure.zip :as zip]
            [tinsel.utils :as utils]))

;;
;; A zipper for hiccup forms.
;;
;; Just to make things easier, we go ahead and do the work here to make
;; hiccup zippers work on both normalized and unnormalized hiccup forms.


(defn children
  "Takes a hiccup node (normalized or not) and returns its children nodes."
  [node]
  (if (map? (second node))   ;; There is an attr map in second slot.
    (seq (subvec node 2))    ;; So skip tag and attr vec.
    (seq (subvec node 1))))  ;; Otherwise, just skip tag.

;; Note, it's not made clear at all in the docs for clojure.zip, but as far as
;; I can tell, you are given a node potentially with existing children and
;; the sequence of children that should totally replace the existing children.
(defn make
  "Takes a hiccup node (normalized or not) and a sequence of children nodes,
   and returns a new node that has the the children argument as its children."
  [node children]
  (if (map? (second node))                ;; Again, check for normalized vec.
    (conj (subvec node 0 2) children)     ;; Attach children after tag&attrs.
    (apply vector (first node) children)));; Otherwise, attach after tag.

(defn hiccup-zip
  "Returns a zipper for Hiccup forms, given a root form."
  [root]
  (zip/zipper vector?
              children
              make
              root))

(defn print-tree
  "Prints the nodes of the tree by repeatedly applying the argument
   function to the location until the end is reached. By default, the
   next-fn argument is zip/next."
  ([start-loc]
     (print-tree start-loc zip/next))
  ([start-loc next-fn]
     (loop [loc start-loc]
       (if (zip/end? loc)
         (zip/root loc)
         (recur (next-fn (do (println (zip/node loc))
                             loc)))))))