(ns tinsel.test.zip
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

;;
;; Postorder traversal
;;

(def v1 [:8 [:4 [:1] [:3 [:2]]] [:6 [:5]] [:7]])
(def v2 [:4 [:1] [:2] [:3]])
(def v3 [:8 [:7 [:6 [:5 [:4 [:3 [:2 [:1]]]]]]]])

(def hzip1 (hiccup-zip v1))
(def hzip2 (hiccup-zip v2))
(def hzip3 (hiccup-zip v3))

(def vzip1 (zip/vector-zip v1))
(def vzip2 (zip/vector-zip v2))
(def vzip3 (zip/vector-zip v3))

(deftest test-postorder-first
  (is (= [:1] (zip/node (postorder-first hzip1))))
  (is (= [:1] (zip/node (postorder-first hzip2))))
  (is (= [:1] (zip/node (postorder-first hzip3))))
  (is (= :8 (zip/node (postorder-first vzip1))))
  (is (= :4 (zip/node (postorder-first vzip2))))
  (is (= :8 (zip/node (postorder-first vzip3)))))


(deftest test-postorder-next
  (is (= [:2] (zip/node (-> hzip1 postorder-first postorder-next))))
  (is (= [:3 [:2]] (zip/node (-> hzip1 postorder-first postorder-next
                                 postorder-next))))
  (is (= [:2] (zip/node (-> hzip2 postorder-first postorder-next))))
  (is (= [:3] (zip/node (-> hzip2 postorder-first postorder-next
                            postorder-next))))
  (is (= [:2 [:1]] (zip/node (-> hzip3 postorder-first postorder-next))))
  (is (= [:3 [:2 [:1]]] (zip/node (-> hzip3 postorder-first postorder-next
                                      postorder-next))))
  (is (= :4 (zip/node (-> vzip1 postorder-first postorder-next))))
  (is (= :1 (zip/node (-> vzip1 postorder-first postorder-next
                          postorder-next))))
  (is (= :1     (zip/node (-> vzip2 postorder-first postorder-next))))
  (is (= [:1]   (zip/node (-> vzip2 postorder-first postorder-next
                              postorder-next))))
  (is (= :7 (zip/node (-> vzip3 postorder-first postorder-next))))
  (is (= :6 (zip/node (-> vzip3 postorder-first postorder-next
                          postorder-next)))))
