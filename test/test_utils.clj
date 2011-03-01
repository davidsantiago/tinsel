(ns test-utils
  (:use clojure.test)
  (:require [hew.utils :as utils]))

;; Test name.
(deftest name-test
  (is (= "test" (utils/name "test")))
  (is (= "test" (utils/name :test)))
  (is (= "test" (utils/name 'test)))
  (is (= nil (utils/name nil)))
  (is (= nil (utils/name [])))
  (is (= nil (utils/name {}))))


;; Test code-form?
(deftest code-form?-test
  (is (= true (utils/code-form? '(+ 1 2))))
  (is (= false (utils/code-form? ['+ 1 2])))
  (is (= false (utils/code-form? {:+ 1})))
  (is (= true (utils/code-form? (seq ['+ 1 2])))))

;;
;; Do some testing of normalize-form in various ways.
;;

(deftest simple-normalize-form
  (is (= ["html" {:id nil :class nil}]
         (utils/normalize-form [:html])))
  (is (= ["html" {:id nil :class nil}]
         (utils/normalize-form [:html {}])))
  (is (= ["html" {:id :doc :class nil}]
         (utils/normalize-form [:html {:id :doc}])))
  (is (= ["html" {:id "doc" :class nil}]
         (utils/normalize-form [:html#doc])))
  (is (= ["html" {:id :doc :class nil}]
         (utils/normalize-form [:html#fakeout {:id :doc}])))
  (is (= ["html" {:id nil :class "a b c"}]
         (utils/normalize-form [:html.a.b.c])))
  (is (= ["html" {:id nil :class "c"}] ;; A sucky behavior, but matches hiccup.
         (utils/normalize-form [:html.a.b {:class "c"}]))))

(deftest recursive-normalize-form
  (is (= ["html" {:id nil :class nil}
          ["body" {:id nil :class nil}
           ["h1" {:id nil :class nil}]]]
         (utils/normalize-form [:html [:body [:h1]]])))
  (is (= ["html" {:id nil :class nil}
          ["head" {:id nil :class nil}
           ["title" {:id nil :class nil}]]
          ["body" {:id nil :class nil}]]
         (utils/normalize-form [:html [:head [:title]] [:body]]))))

(deftest normalize-form-with-code
  (is (= ["html" {:id nil :class nil} '(:msg arg-map)]
         (utils/normalize-form [:html '(:msg arg-map)])))
  (is (= ["html" {:id nil :class nil} 'something]
         (utils/normalize-form [:html 'something]))))