(ns test-hew
  (:use hew.core
        clojure.test)
  ;; These two are just for testing, see test-ns-membership.
  (:require [clojure.string :as str])
  (:use clojure.walk))

;; Test our namespace membership testing. Tricky, since it resolves names.
(deftest test-ns-membership
  ;; Note, we didn't refer clojure.string into this ns...
  (is (= false
         (in-namespace? 'clojure.string 'join)))
  (is (= true
         (in-namespace? 'clojure.string 'str/join)))
  ;; But we did use clojure.walk, so plain 'walk should resolve to it.
  (is (= true
         (in-namespace? 'clojure.walk 'walk)))
  (is (= true
         (in-namespace? 'clojure.walk 'clojure.walk/walk))))

;; Test the basic transformations from the "template language" to
;; executable Clojure code.
(deftest test-transform-forms
  (is (= '[[:h1 (clojure.core/-> test (attach :x))]]
         (transform-forms '[[:h1 (attach :x)]] 'test)))
  (is (= '[[:h1 (clojure.core/-> test (call a))]]
         (transform-forms '[[:h1 (call a)]] 'test))))

(deftemplate snippet1
  [:h1 (attach :title)])

;; Test that both artities work
(deftest test-template-arity
  (is (= "<h1></h1>"
         (snippet1)))
  (is (= "<h1>Title</h1>"
         (snippet1 {:title "Title"}))))

;; Test call and attach
(deftemplate document1
  [:html
   [:head [:title (attach :title)]]
   [:body (call snippet1)]])

(deftemplate document2
  [:html
   [:body (call snippet1 {:title "Alternate Title!"})]])

(deftest test-call-attach
  (is (= "<h1>Title</h1>"
         (snippet1 {:title "Title"})))
  (is (= "<html><head><title>Title</title></head><body><h1>Title</h1></body></html>"
         (document1 {:title "Title"})))
  (is (= "<html><body><h1>Alternate Title!</h1></body></html>"
         (document2 {:title "Alternate Title!"}))))

;; Test conditional logic based on presence of key
(deftemplate snippet-conditional
  [:h1 (if (present? :title)
         (attach :title)
         "Default Title")])

(deftest test-conditional-template
  (is (= "<h1>Default Title</h1>"
         (snippet-conditional)))
  (is (= "<h1>Default Title</h1>"
         (snippet-conditional nil)))
  (is (= "<h1>Default Title</h1>"
         (snippet-conditional {})))
  (is (= "<h1>Cool Specific Title</h1>"
         (snippet-conditional {:title "Cool Specific Title"}))))

;; Test the deref key access syntax
(deftemplate snippet-deref1
  [:h1 @title])

(deftemplate snippet-deref2
  [:h1 @test/title])

(deftest test-deref-syntax
  (is (= "<h1>Title</h1>"
         (snippet-deref1 {:title "Title"})))
  (is (= "<h1></h1>"
         (snippet-deref1 {})))
  (is (= "<h1>Title</h1>"
         (snippet-deref2 {:test/title "Title"}))))

;; Test a for form
(deftemplate snippet-for
  [:ul (for [a @items]
         [:li a])])

(deftest test-for
  (is (= "<ul><li>Item 1</li></ul>"
         (snippet-for {:items ["Item 1"]})))
  (is (= "<ul><li>Item 1</li><li>Item 2</li></ul>"
         (snippet-for {:items ["Item 1" "Item 2"]}))))

;; Test math in a form
(deftemplate snippet-math1
  [:h1 "Welcome Visitor #" (+ 1 2)])

(deftemplate snippet-math2
  [:h1 "Welcome Visitor #" (+ @males @females)])

(deftest test-math
  (is (= "<h1>Welcome Visitor #3</h1>"
         (snippet-math1)))
  (is (= "<h1>Welcome Visitor #11</h1>"
         (snippet-math2 {:males 5 :females 6}))))

;; Test string-pasting.
(deftemplate snippet-stringpaste1
  [:h1 "Welcome " (reverse @firstname) (str " " @lastname) "!"])

(deftemplate snippet-stringpaste2
  [:h1 "Welcome " (str/upper-case @person) "!"])

(deftest test-stringpaste
  (is (= "<h1>Welcome Fatty Corpuscle!</h1>"
         (snippet-stringpaste1 {:firstname "yttaF" :lastname "Corpuscle"})))
  (is (= "<h1>Welcome FATTY CORPUSCLE!</h1>"
         (snippet-stringpaste2 {:person "fatty corpuscle"}))))

;; Test deep data structure extraction.
(deftemplate snippet-deepdata
  [:h1 "Children of " (:name @person)]
  [:ul (for [c (:children @person)]
         [:li c])])

(deftest test-deepdata
  (is (= "<h1>Children of Peter Griffin</h1><ul><li>Chris</li><li>Meg</li><li>Stewie</li></ul>"
         (snippet-deepdata {:person {:name "Peter Griffin"
                                     :children ["Chris" "Meg" "Stewie"]}}))))

;; Test modifying the contents of an argument map inside a template.
(deftemplate called-snippet
  [:li @item])
(deftemplate calling-snippet
  [:ul (for [i @items]
         (call called-snippet {:item i}))])

(deftest test-dynamic-argmaps
  (is (= "<ul><li>1</li><li>2</li><li>3</li></ul>"
         (calling-snippet {:items [1 2 3]}))))