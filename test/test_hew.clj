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
(deftemplate snippet2
  [:h1 (if (present? :title)
         (attach :title)
         "Default Title")])

(deftest test-conditional-template
  (is (= "<h1>Default Title</h1>"
         (snippet2)))
  (is (= "<h1>Default Title</h1>"
         (snippet2 nil)))
  (is (= "<h1>Default Title</h1>"
         (snippet2 {})))
  (is (= "<h1>Cool Specific Title</h1>"
         (snippet2 {:title "Cool Specific Title"}))))