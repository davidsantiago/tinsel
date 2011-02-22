(ns hew.core
  "Soy/StringTemplate style templates with Hiccup."
  (:use [hiccup core]
        clojure.walk))

;;
;; Thunks/Syntax
;;

(defmacro ^{:hew/syntax true} attach
  "You can use this to insert the contents of a map key's value at the given
   location in the template, or you can just grab the value with it for
   further manipulation."
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
  "Not strictly necessary; you can just check if a given key resolves to nil.
   But it can look nicer."
  [arg-name key]
  `(contains? ~arg-name ~key))

;;
;; Compiler
;;
;; Note: Everything here runs at template compile time (that is, macro
;; expansion, and not during the template's render time.

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

(defn code-form?
  "Takes a form and returns true if it is a 'code' form. In other words,
   it is attempting to express code to be executed for its content, instead
   of being a marker for hiccup to process into HTML."
  [form]
  (and (sequential? form) ;; (seq? {}) -> true, (sequential? {}) -> false
       (not (vector? form))
       (not= 'quote (first form))))

(defn syntax-transformer
  "Given a symbol naming the template's map parameter and a form, applies a
   number of transformations as part of the compilation process and returns
   a new form that should be used instead."
  [arg-name form]
  (cond
   ;; First: If we see a list with a symbol in the fn slot, and that symbol
   ;; resolves to this namespace and has a :hew/syntax in its metadata,
   ;; insert the arg-name into the form as its first argument.
   (and (code-form? form)
        (symbol? (first form))
        (in-namespace? 'hew.core
                       (first form))
        (:hew/syntax (meta (ns-resolve 'hew.core (first form)))))
   `(-> ~arg-name ~form)
   ;; Next, check for a symbol that is being deref'ed (ie, @some-sym). Turn
   ;; that into a call to get that value out of the arg map. Basically works
   ;; the same as attach.
   (and (code-form? form)
        (= 'clojure.core/deref (first form)))
   `(attach ~arg-name (keyword (second (quote ~form))))
   :else ;; Anything else, just let the form pass through.
   form))

(defn transform-forms
  "This function goes through the forms passed in and makes modifications to
   the structure so that it becomes correctly executable code. For now, this
   means adding the map argument to template functions."
  [forms arg-name]
  (postwalk (partial syntax-transformer arg-name)
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
