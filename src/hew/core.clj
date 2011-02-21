(ns hew.core
  "Soy/StringTemplate style templates with Hiccup."
  (:use [hiccup core]
        clojure.walk))

;;
;; Thunks/Syntax
;;

(defmacro ^{:hew/syntax true} attach
  [arg-name key]
  `(~key ~arg-name))

(defmacro ^{:hew/syntax true} call
  "Used in a template like (call some-template) or (call some-template {:a 1}).
   If the former form is used, the same arg map the callee got is passed in. If
   the latter form is used, the supplied map is merged into the callee's arg
   map."
  [arg-name tmpl-name & opt-args]
  (if opt-args
    `(~tmpl-name (merge ~arg-name ~(first opt-args)))
    `(~tmpl-name ~arg-name)))

(defmacro ^{:hew/syntax true} present?
  [arg-name key]
  `(contains? ~arg-name ~key))

;;
;; Compiler
;;

(defmacro in-namespace?
  "Takes a namespace name (ie, 'clojure.string) and a symbol, and returns true
   if the symbol resolves to one in that namespace. This is a macro so that it
   can do this calculation in the environment of the caller (ie, work as you
   would expect)."
  [ns sym]
  ;; We have to capture the value of *ns* at the call site during macro-
  ;; expansion time, and not at the template's runtime. Who knows what that
  ;; will be. This is what the client code expects, so that we can resolve sym
  ;; in the namespace in effect at that point, and not whatever ns is ultimately
  ;; calling the template later.
  (let [ns-here *ns*]
    `(or (contains? (set (vals (ns-publics ~ns)))
                    (ns-resolve ~ns-here ~sym)))))

(defn transform-forms
  "This function goes through the forms passed in and makes modifications to
   the structure so that it becomes correctly executable code. For now, this
   means adding the map argument to template functions."
  [forms arg-name]
  (postwalk (fn [x]
              (if (and (list? x)
                       (symbol? (first x))
                       (in-namespace? 'hew.core
                                      (first x))
                       (:hew/syntax (meta (ns-resolve 'hew.core (first x)))))
                `(-> ~arg-name ~x)
                x))
            forms))

(defmacro deftemplate
  "Creates a function that renders the template given. Takes either one or no
   arguments; if there is an argument it is a map. The map arguments are used
   to populate the template forms given and return the result as a string."
  [tmpl-name & forms]
  (let [arg-name (gensym)
        forms (transform-forms forms arg-name)]
    `(defn ~tmpl-name
       ([]
          (~tmpl-name nil))
       ([~arg-name]
           (html ~@forms)))))
