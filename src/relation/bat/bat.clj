(ns relation.bat.bat
  (:require [clojure.repl   :refer :all]
            [clojure.string :as str]
            [clojure.edn    :as edn]))


(load "table")
(load "batOperators")
(load "milOperators")







(def names (bat [{:head 1 :tail "Roland"}
            {:head 2 :tail "Eddie"}
            {:head 3 :tail "Susanna"}]))

(def postal-codes (bat[{:head 1 :tail 123}
                 {:head 2 :tail 456}
                 {:head 3 :tail 789} ]))

(def date-of-birth (bat[{:head 1 :tail "01-01-2001"}
                    {:head 2 :tail "02-02-2001"}
                    {:head 3 :tail "03-03-2001"}]))





(def named-tables {:name  names
                   :postal-code  postal-codes
                   :date-of-birth date-of-birth})

(bat "li" "la" "lu")

 (def nameBAT (bat [{:head 1 :tail "Roland"}
            {:head 2 :tail "Eddie"}
            {:head 3 :tail "Susanna"}]))


 (def NameRelationBAT (bat [{:head 1 :tail 2}
                           {:head 1 :tail 3}
                           {:head 2 :tail 3}
                           {:head 3 :tail 2} ]))




(find nameBAT 2)


(select nameBAT #(= % "Roland"))
(select postal-codes  #(and (> % 123) (<= % 789)))

(def NameRelationBAT (bat [{:head 1 :tail 2}
                           {:head 1 :tail 3}
                           {:head 2 :tail 3}
                           {:head 3 :tail 2} ]))

(select NameRelationBAT (fn [x h l] (and (<= x h) (>= x l))) 3 3)


(join (reverse nameBAT)  postal-codes =)
(join NameRelationBAT nameBAT =)
(join NameRelationBAT nameBAT = 2)
(join NameRelationBAT nameBAT <)

(reverse nameBAT)

(mirror nameBAT)
(mirror (reverse nameBAT))

(mark nameBAT  10)
(mark nameBAT 0)

(project nameBAT "Test")

(slice nameBAT 1 1)
(slice nameBAT 0 0)
(slice nameBAT 2 1)
(slice nameBAT 0 2)

(count nameBAT)

(sum NameRelationBAT)

(max NameRelationBAT)

(min NameRelationBAT)


(def namesBAT2 (bat [{:head 4 :tail "Jake"}
            {:head 2 :tail "Eddie"}
            {:head 3 :tail "Susanna"}]))

(diff nameBAT namesBAT2)
(diff namesBAT2 nameBAT)

(union nameBAT namesBAT2)

(intersect nameBAT namesBAT2)
(intersect namesBAT2 nameBAT)


 (def AB (bat [{:head 1 :tail 1}
                           {:head 2 :tail 1}
                           {:head 3 :tail 2}
                           {:head 4 :tail 2} ]))

 (def CD (bat [{:head 1 :tail "A"}
                           {:head 2 :tail "A"}
                           {:head 3 :tail "B"}
                           {:head 4 :tail "C"} ]))

 (group AB)
 (group (group AB))
 (group (group AB) CD)


 (def AB  (bat [{:head 1 :tail 1231}
            {:head 2 :tail 323}
            {:head 3 :tail 1}]))

(def CD (bat [{:head 1 :tail 1} {:head 2 :tail 3}]))

(fragment  AB CD)




  (def AB (bat [{:head 1 :tail 1}
                           {:head 2 :tail 1}
                           {:head 3 :tail 2}
                           {:head 4 :tail 2} ]))


  (split AB 4)



(def names (bat [{:head 1 :tail "Roland"}
            {:head 2 :tail "Eddie"}
            {:head 3 :tail "Susanna"}]))

(def postal-codes (bat [{:head 3 :tail 123}
                 {:head 2 :tail 456}
                 {:head 1 :tail 789} ]))

(def date-of-birth  (bat [{:head 1 :tail "01-01-2001"}
                    {:head 2 :tail "02-02-2001"}
                    {:head 3 :tail "03-03-2001"}]))


(multijoin str names 0.07 postal-codes date-of-birth)
(multijoin = postal-codes postal-codes)






(def names (bat [{:head 1 :tail "Roland"}
            {:head 2 :tail "Eddie"}
            {:head 3 :tail "Susanna"}]))

(def postal-codes (bat [{:head 3 :tail 123}
                 {:head 2 :tail 456}
                 {:head 1 :tail 789} ]))

(def date-of-birth  (bat [{:head 1 :tail "01-01-2001"}
                    {:head 2 :tail "02-02-2001"}
                    {:head 3 :tail "03-03-2001"}]))

(makeTable [:zip] [:name :zip :birth] names postal-codes date-of-birth)



