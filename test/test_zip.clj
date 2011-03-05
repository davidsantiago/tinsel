(ns test-zip
  (:use tinsel.zip
        clojure.test)
  (:require [clojure.zip :as zip]
            [tinsel.utils :as utils]
            [hiccup.core :as hiccup]))

;;
;; hiccup zipper
;;
;; Due to the very sketchy docs on how zippers should work, I'm not sure how
;; exactly to unit test the basic functionality. So just try to give our hiccup
;; zipper a workout.

(def unnormalized-small-zip (hiccup-zip [:body [:ul [:li] [:li]]]))

(deftest test-basic-fns-unnormalized
  (is (= [:body [:ul [:li] [:li]]]
           (-> unnormalized-small-zip zip/down zip/down zip/root)))
  (is (= [:ul [:li] [:li]]
           (-> unnormalized-small-zip zip/down zip/node)))
  (is (= '([:li] [:li])
         (-> unnormalized-small-zip zip/down zip/children)))
  (is (= [:li]
           (-> unnormalized-small-zip zip/next zip/next zip/next zip/node))))

(def normalized-small-zip
     (hiccup-zip (utils/normalize-form [:body [:ul [:li] [:li]]])))

(deftest test-basic-fns-normalized
  (is (= ["body" {:id nil :class nil}
          ["ul" {:id nil :class nil}
           ["li" {:id nil :class nil}] ["li" {:id nil :class nil}]]]
           (-> normalized-small-zip zip/down zip/down zip/root)))
  (is (= ["ul" {:id nil :class nil}
          ["li" {:id nil :class nil}] ["li" {:id nil :class nil}]]
           (-> normalized-small-zip zip/down zip/node)))
  (is (= '(["li" {:id nil :class nil}] ["li" {:id nil :class nil}])
         (-> normalized-small-zip zip/down zip/children)))
  (is (= ["li" {:id nil :class nil}]
           (-> normalized-small-zip zip/next zip/next zip/next zip/node))))

(deftest test-basic-edit-unnormalized
  (is (= [:body [:ul [:p "Hi"] [:li]]]
           (-> unnormalized-small-zip zip/down zip/down
               (zip/edit (fn [_] [:p "Hi"])) zip/root))))

(deftest test-basic-edit-normalized
  (is (= ["body" {:id nil :class nil}
          ["ul" {:id nil :class nil}
           ["p" "Hi"] ["li" {:id nil :class nil}]]]
           (-> normalized-small-zip zip/down zip/down
               (zip/edit (fn [_] ["p" "Hi"])) zip/root))))