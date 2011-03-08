(ns test-core
  (:use tinsel.core
        clojure.test)
  ;; These two are just for testing, see test-ns-membership.
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [hiccup.core :as hiccup]
            [hiccup.page-helpers :as hiccup-ph])
  (:use clojure.walk))

;; The most basic test: one with no transformations.
(deftemplate no-transforms-template [[:h1 "Hi!"]] [])

(deftest test-no-transform-template
  (is (= "<h1>Hi!</h1>"
         (no-transforms-template))))

;; Slightly more advanced: a transformation, but not based on arguments.
(deftemplate simple-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  (fn [zip-loc] (= (first (zip/node zip-loc)) "title"))
  (fn [node] [:title "Cool Title"]))

(deftest test-simple-transform-template
  (is (= "<title>Cool Title</title><h1>Hi!</h1>"
         (simple-transform-template {}))))

;; A transformation, but based on template args.
(deftemplate argument-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  #(= (first (zip/node %)) "title")
  (fn [node] [:title '(:title arg-map)]))

(deftest test-argument-transform-template
  (is (= "<title>Cool Title</title><h1>Hi!</h1>"
         (argument-transform-template {:title "Cool Title"}))))

;; Two transformations.
(deftemplate multiple-transform-template [[:title "Title"] [:h1 "Hi!"]]
  [arg-map]
  #(= (first (zip/node %)) "title")
  (fn [node] [:title '(:title arg-map)])
  #(= (first (zip/node %)) "h1")
  (fn [node] [:h1 '(:header arg-map)]))

(deftest test-multiple-transform-template
  (is (= "<title>Cool Title</title><h1>A Header</h1>"
         (multiple-transform-template {:title "Cool Title"
                                       :header "A Header"}))))

;;
;; Test Selectors
;;


;; Select multiple nodes by tag.
(deftemplate select-tag-template
  [[:body#some-id [:h1] [:h1]]]
  [arg-map]
  (tag= :h1)
  (fn [node] [:h1 '(:heading arg-map)]))

(deftest test-select-tag-template
  (is (= "<body id=\"some-id\"><h1>HEADING</h1><h1>HEADING</h1></body>"
         (select-tag-template {:heading "HEADING"}))))

;; Select by presence of attribute.
(deftemplate select-attr-present-template
  [[:body [:ul {:some-attr "someval"} [:li "An item"]]]]
  [added-li]
  (has-attr? :some-attr)
  (append-content [:li added-li]))

(deftest test-select-attr-present-template
  (is (= "<body><ul some-attr=\"someval\"><li>An item</li><li>Another item</li></ul></body>"
         (select-attr-present-template "Another item"))))

;; Select by equality of attribute.
(deftemplate select-attr-equal-template
  [[:body [:ul {:some-attr "someval"} [:li "An item"]]]]
  [added-li]
  (attr= :some-attr "someval")
  (append-content [:li added-li]))

(deftest test-select-attr-equal-template
  (is (= "<body><ul some-attr=\"someval\"><li>An item</li><li>Another item</li></ul></body>"
         (select-attr-equal-template "Another item"))))

;; Select by id.
(deftemplate select-id-template
  [[:body#short-id [:h1 {:id "long-id"} "A heading."]]]
  [arg-map]
  (id= :long-id)
  (fn [node] [:h1 {:id "long-id"} '(:heading arg-map)]))

(deftest test-select-id-template
  (is (= "<body id=\"short-id\"><h1 id=\"long-id\">Some cool heading.</h1></body>"
         (select-id-template {:heading "Some cool heading."}))))

;; Select by class.
(deftemplate select-class-template
  [[:body [:ul [:li.a] [:li.a.b] [:li.a.b.c]]]]
  [a b c]
  (has-class? :a) (append-content a)
  (has-class? :b) (append-content b)
  (has-class? :c) (append-content c))

(deftest test-select-class-template
  (is (= "<body><ul><li class=\"a\">A</li><li class=\"a b\">AB</li><li class=\"a b c\">ABC</li></ul></body>"
         (select-class-template "A" "B" "C"))))

;; Select by nth child.
(deftemplate select-nth-child-template
  [[:body [:ul [:li] [:li] [:li#here] [:li]]]]
  [third-list-item]
  (nth-child? 3)
  (set-content third-list-item))

(deftemplate select-1st-child-template
  [[:body [:ul [:li] [:li]]]] ;; Want to ensure root isn't selected.
  [added-attrs]
  (nth-child? 1)
  (set-attrs added-attrs))

(deftest test-select-nth-child-template
  (is (= "<body><ul><li></li><li></li><li id=\"here\">HERE</li><li></li></ul></body>"
         (select-nth-child-template "HERE")))
  (is (= "<body><ul first-child=\"true\"><li first-child=\"true\"></li><li></li></ul></body>"
         (select-1st-child-template {:first-child "true"}))))

;; Select by nth last child.
(deftemplate select-nth-last-child-template
  [[:body [:ul [:li] [:li] [:li#here] [:li]]]]
  [third-list-item]
  (nth-last-child? 2)
  (set-content third-list-item))

(deftemplate select-last-child-template
  [[:body [:ul [:li] [:li]]]] ;; Want to ensure root isn't selected.
  [added-attrs]
  (nth-last-child? 1)
  (set-attrs added-attrs))

(deftest test-select-nth-last-child-template
  (is (= "<body><ul><li></li><li></li><li id=\"here\">HERE</li><li></li></ul></body>"
         (select-nth-last-child-template "HERE")))
  (is (= "<body><ul last-child=\"true\"><li></li><li last-child=\"true\"></li></ul></body>"
         (select-last-child-template {:last-child "true"}))))

;;
;; Test Selector Combinators
;;

;; Test both every-pred and some-fn combinators.
(deftemplate select-every-template
  [[:body [:div.a] [:div.b] [:span.a] [:span.b]]]
  [a b]
  (every-selector (tag= :div)
                  (has-class? :a))
  (append-content [:p (str "Div.a: " a)])
  (some-selector (tag= :span)
                 (has-class? :b))
  (append-content [:p (str "Span or .b: " b)]))

(deftest test-select-every-template
  (is (= "<body><div class=\"a\"><p>Div.a: A</p></div><div class=\"b\"><p>Span or .b: B</p></div><span class=\"a\"><p>Span or .b: B</p></span><span class=\"b\"><p>Span or .b: B</p></span></body>"
         (select-every-template "A" "B"))))

;; Test select combinator.
(deftemplate select-path-template
  [[:body [:div.a [:div.b [:span.c [:span.d "Bullseye"]]
                          [:span#fakeout [:span.d]]]]]]
  [new-content]
  (select (has-class? :a)
          (has-class? :b)
          (every-selector (has-class? :c)
                          (tag= :span))
          (has-class? :d))
  (set-content new-content))

(deftest test-select-path-template
  (is (= "<body><div class=\"a\"><div class=\"b\"><span class=\"c\"><span class=\"d\">Wow, found it!</span></span><span id=\"fakeout\"><span class=\"d\"></span></span></div></div></body>"
         (select-path-template "Wow, found it!"))))

;; Test or-ancestor combinator.
(deftemplate select-or-ancestor-template
  [[:body [:div.a [:div.b [:span.c [:span.d "Bullseye"]]
                          [:span#fakeout [:span.d]]]]]]
  [new-content]
  (select (or-ancestor (tag= :div))
          (or-ancestor (has-class? :c))
          (has-class? :d))
  (set-content new-content))

(deftest test-select-or-ancestor-template
  (is (= "<body><div class=\"a\"><div class=\"b\"><span class=\"c\"><span class=\"d\">Wow, found it!</span></span><span id=\"fakeout\"><span class=\"d\"></span></span></div></div></body>"
         (select-or-ancestor-template "Wow, found it!"))))


;;
;; Test Transformers
;;

;; Set content
(deftemplate set-content-template1
  [[:body [:h1#replace-me "Replace me!"]]]
  [arg-map]
  (id= :replace-me)
  (set-content (:replacement arg-map)))

(deftemplate set-content-template2
  [[:body [:h1#replace-me "Replace me!"]]]
  [arg-map]
  (id= :replace-me)
  (set-content [:em (:replacement arg-map)]))

;; Make sure we can replace the root node's content.
(deftemplate set-content-template3
  [[:html [:h1 "I'm going to be replaced!"]]]
  [arg-map]
  (tag= :html)
  (set-content [:h2 (:replacement arg-map)]))

(deftest test-set-content-template
  (is (= "<body><h1 id=\"replace-me\">You have been replaced.</h1></body>"
         (set-content-template1 {:replacement "You have been replaced."})))
  (is (= "<body><h1 id=\"replace-me\"><em>You have been replaced.</em></h1></body>"
         (set-content-template2 {:replacement "You have been replaced."})))
  (is (= "<html><h2>I'm the replacement.</h2></html>"
         (set-content-template3 {:replacement "I'm the replacement."}))))

;; Append content
(deftemplate append-content-template1
  [[:body [:ul#add-to-me
           [:li "Add something after me!"]]]]
  [arg-map]
  (id= :add-to-me)
  (append-content (:addition arg-map)))

(deftemplate append-content-template2
  [[:body [:ul#add-to-me
           [:li "Add something after me!"]]]]
  [addition]
  (id= :add-to-me)
  (append-content addition))

;; Make sure we can append content to an empty root node.
(deftemplate append-content-template3
  [[:html#doc]]
  [addition]
  (id= :doc)
  (append-content addition))

(deftest test-append-content-template
  (is (= "<body><ul id=\"add-to-me\"><li>Add something after me!</li><li>Ohai!</li></ul></body>"
         (append-content-template1 {:addition [:li "Ohai!"]})))
  (is (= "<body><ul id=\"add-to-me\"><li>Add something after me!</li><li>Ohai!</li></ul></body>"
         (append-content-template2 [:li "Ohai!"])))
  (is (= "<html id=\"doc\"><h1>The content.</h1></html>"
         (append-content-template3 [:h1 "The content."]))))

;; Prepend content
(deftemplate prepend-content-template1
  [[:body [:ul#add-to-me
           [:li "Add something before me!"]]]]
  [arg-map]
  (id= :add-to-me)
  (prepend-content (:addition arg-map)))

(deftemplate prepend-content-template2
  [[:body [:ul#add-to-me
           [:li "Add something before me!"]]]]
  [addition]
  (id= :add-to-me)
  (prepend-content addition))

;; Make sure we can prepend content to an empty root node.
(deftemplate prepend-content-template3
  [[:html#doc]]
  [addition]
  (tag= :html)
  (prepend-content addition))

(deftest test-prepend-content-template
  (is (= "<body><ul id=\"add-to-me\"><li>Ohai!</li><li>Add something before me!</li></ul></body>"
         (prepend-content-template1 {:addition [:li "Ohai!"]})))
  (is (= "<body><ul id=\"add-to-me\"><li>Ohai!</li><li>Add something before me!</li></ul></body>"
         (prepend-content-template2 [:li "Ohai!"])))
  (is (= "<html id=\"doc\"><h1>The content.</h1></html>"
         (prepend-content-template3 [:h1 "The content."]))))

;; Set Attributes
(deftemplate set-attribute-template1
  [[:body [:img]]]
  [arg-map]
  (tag= :img)
  (set-attrs {:src (:url arg-map)}))

(deftemplate set-attribute-template2
  [[:body [:img]]]
  [arg-url]
  (tag= :img)
  (set-attrs {:src arg-url}))

(deftemplate set-attribute-template3
  [[:body [:a "Some link text"]]]
  [arg-url]
  (tag= :a)
  (set-attrs {:href arg-url}))

(deftemplate set-attribute-template4  ;; Testing map arg in a symbol...
  [[:body [:img]]]
  [arg-map]
  (tag= :img)
  (set-attrs arg-map))

(deftest test-set-attribute-template
  (is (= "<body><img src=\"http://example.com/img.jpg\" /></body>"
         (set-attribute-template1 {:url "http://example.com/img.jpg"})))
  (is (= "<body><img src=\"http://example.com/img.jpg\" /></body>"
         (set-attribute-template2 "http://example.com/img.jpg")))
  (is (= "<body><a href=\"http://fark.com\">Some link text</a></body>"
         (set-attribute-template3 "http://fark.com")))
  (is (= "<body><img src=\"http://example.com/img.jpg\" /></body>"
         (set-attribute-template4 {:src "http://example.com/img.jpg"}))))

;; A slightly larger template, check output quality against hiccup.
(deftemplate medium-template
  [[:html
    [:head
     [:title "Literal String"]]
    [:body
     [:div.example]
     [:ul.times-table]]]]
  [text]
  (tag= :div)
  (set-content text)
  (tag= :ul)
  (set-content (for [n (range 1 13)]
                 [:li n " * 9 = " (* n 9)])))

(deftest test-medium-template
  (is (= (medium-template "Some text")
         (let [text "Some text"]
           (hiccup/html
            [:html
             [:head
              [:title "Literal String"]]
             [:body
              [:div.example text]
              [:ul.times-table
               (for [n (range 1 13)]
                 [:li n " * 9 = " (* n 9)])]]])))))

;; Test for hiccup page-helpers to work.
(deftemplate hiccup-page-helpers
  [[:html [:head (hiccup-ph/include-js "some-js.js")]]]
  [css-filename]
  (tag= :head)
  (append-content (hiccup-ph/include-css css-filename)))

(deftest test-hiccup-page-helpers
  (is (= "<html><head><script src=\"some-js.js\" type=\"text/javascript\"></script><link href=\"some-css.css\" rel=\"stylesheet\" type=\"text/css\" /></head></html>"
       (hiccup-page-helpers "some-css.css"))))