(ns relation.transrelational.operators
  (:use [relation.transrelational.table]))


(require 'clojure.tools.trace)


;; #######################################################################################################################################
;; Tools
;; #######################################################################################################################################




(defn- drop-index [col idx]
  (filter identity (map-indexed #(if (not= %1 idx) %2) col)))

(defmacro tr-fn
  "Behaves like fn, but stores the source code in the metadata to allow
  optimisation."
  [args body]
  (with-meta (list 'fn args body)
              {:original (list 'quote body)}))





 (defn travel [rrt row column steps]
   (let [columns (map #(nth rrt (mod %  (count rrt)))
                      (range  column (+ column steps 1)))
         rec-travel (fn [row columns ]
                      (if (empty? (rest columns))
                        (nth (first columns) row)
                        (recur (second (nth (first columns) row))  (rest columns))))]
     (rec-travel row columns)))





(defn zigzag
  "Get a row of data by the numeric position of one of the cells in the transrelational table."
  [trans-table row column]
  (let [rrt (recordReconst trans-table)
        rrt (apply conj (vec (drop column rrt)) (drop-last  (- (count rrt) column ) rrt))
        recMakeTuple (fn[rrt row result]
                       (if (empty? rrt)
                         result
                         (let [[value-link next-row] (nth (first rrt) row)]
                           (recur (rest rrt) next-row (conj result value-link)))))]
  (recMakeTuple rrt row [])))



;; #######################################################################################################################################
;; Basic operations
;; #######################################################################################################################################



(defn retrieve
  "Get a row of data by the numeric position of one of the cells in the transrelational table.  "
  [trans-table row column]
  (let [orig-attrs  (keyorder trans-table)
        attrs (apply conj (vec (drop column orig-attrs)) (drop-last (- (count orig-attrs) column ) orig-attrs))
        indizes (zigzag trans-table row column)]
   (conj {} (select-keys (into {} (map (fn [attr index][attr (fieldValueOf trans-table index attr)] ) attrs indizes)) orig-attrs))))




(defn convert
  "Get a collection of all reconstructed rows by a transrelational table ordered by the first attribute or by a sequence of ordering attributes."
  ([trans-table]
   (map (fn[row] (retrieve trans-table  row 0)) (range (count trans-table))))
  ([trans-table order]
   (if (empty? order)
     (convert trans-table)
     (if (not-any? #(contains? (set (keyorder trans-table)) %) order)
       (throw (IllegalArgumentException. "Order attribute not part of relation"))
       (let [attrs (keyorder trans-table)
             row-of-last (.indexOf attrs (last order))
             preorderd-tr (map (fn[row] (retrieve trans-table  row row-of-last)) (range (count trans-table)))]
         (if (empty? (drop-last 1 order))
           preorderd-tr
           (sort-by (apply juxt (drop-last 1 order) ) preorderd-tr )))))))





(defn delete
  "Deletes one related data row by the numeric position of one of it cells and returns the resulting transrelational table."
  [trans-table  row column]
  (let [attrs (let [attrs (keyorder trans-table)]
                (apply conj (vec (drop column attrs)) (drop-last (- (count attrs) column ) attrs)))
        rrt (let [ rrt (recordReconst trans-table)]
              (apply conj (vec (drop column rrt)) (drop-last  (- (count rrt) column ) rrt)))
        recMakeTuple (fn[attrs rrt row result]
                       (if (empty? attrs)
                         result
                         (let [ cell  (nth (first rrt) row)]
                           (recur (rest attrs) (rest rrt) (second cell) (assoc result (first attrs) cell)))))
        indizes (recMakeTuple attrs rrt row {})
        delete-Value (fn [attr index]
                       (let[untouched (take index (get (fieldValues trans-table) attr))
                            target (let [zwerg (merge-with - (nth (get (fieldValues trans-table) attr) index) {:to 1} )]
                                     (if (< (:to zwerg ) (:from zwerg))
                                       '()
                                       [zwerg]))
                            tail (map (fn [m] (merge-with - m {:from 1 :to 1} )) (drop (inc index) (get (fieldValues trans-table) attr))) ]
                         [(concat untouched target  tail)
                          (empty? target)]))
        fvt-manipulation (map (fn [[attr [index _]]] (delete-Value attr index)) indizes)
        new-fvt (apply merge (mapv (fn [attr [column _]] {attr column}) attrs fvt-manipulation))
        entry-infos (let [indizes (map #(get indizes %) attrs)]
                      (mapv (fn [[_ a] [b c d ]] [a b c d])
                            (apply conj [(last indizes)] (drop-last indizes))
                            (mapv (fn [[a b] c] [a b c])  indizes (map second fvt-manipulation))))
        new-rrt (let [filtered-rrt (mapv (fn [[index _ _ _] column]
                                           (concat (take index column) (drop (inc index) column)))
                                         entry-infos rrt)
                      new-rrt (mapv (fn [[_ value-link next-link is-deleted] column]
                                     ( map (fn[[cell-value cell-next]]
                                            [(if (and is-deleted (> cell-value value-link)) (dec cell-value) cell-value)
                                             (if (> cell-next next-link) (dec cell-next) cell-next)]) column)) entry-infos filtered-rrt)]
                  (apply conj (vec (drop (- (count rrt) column ) new-rrt)) (drop-last  column new-rrt)))]
   (tr (keyorder trans-table)  new-fvt new-rrt)))



(defn distinct-tr
  "Creates set consistency in the table by deleting duplicated data rows."
  [trans-table]
  (let [indezes (vals (clojure.set/map-invert (into (sorted-map-by >) (map (fn [x] [x (zigzag trans-table x 0)]) (range (count trans-table))))))
        to-delete (filter #(not (contains? (set indezes) %)) (range (count trans-table)))]
    (reduce (fn [tr index] (delete tr index 0)) trans-table to-delete)))




(defn insert
  "Returns the given transrelational table with the included datarow. The datarow has to be a map."
  [trans-table data-row]
  (when-not (= (set (keys data-row)) (set (keyorder trans-table)))
    (throw (IllegalArgumentException. "DataRow has not the same schema as the table")))
  (let [fvt-manipulation (reduce (fn [m [k v]](assoc m k
                                       (let[old-column (get  (fieldValues trans-table) k)
                                            untouched (filter #(neg? (compare (:value %) v)) old-column)
                                            increased (sequence (comp
                                                                 (map (fn[cell] (merge-with + cell {:from 1 :to 1})))
                                                                 (filter #(pos? (compare (:value %) v)))
                                                                 ) old-column)
                                            target (let[found (first (filter #(= (:value %) v) old-column))]
                                                     (if (nil? found)
                                                       (let [index (if (empty? untouched) 0 (inc (:to (last untouched))))]{:value v :from index :to index})
                                                       (merge-with + found {:to 1})))]
                                          [(concat untouched [target] increased) [target (count untouched)]]))) {}  data-row )
        new-fvt (reduce (fn[m [k [ v _ ]]] (assoc m k v)) {} fvt-manipulation)
        new-rrt (let [inserts (reduce (fn[m [k [ _ v ]]] (assoc m k v)) {} fvt-manipulation)
                      inserts-in-order (map #(get inserts %) (keyorder trans-table))
                      entry-infos (mapv (fn [a b] [ (second a)
                                                    (:to (first a))
                                                    (:to (first b))
                                                    (= (:to (first a)) (:from (first a)))] )
                                        inserts-in-order (conj (vec (rest inserts-in-order)) (first inserts-in-order)))]
                  (mapv (fn[column [a-value-index a-next-pointer b-next-pointer a-is-new]]
                          (let [prepared-column (map (fn [[ a b ]] [ (if (and (>= a a-value-index) a-is-new) (inc a) a)
                                                                     (if (>= b b-next-pointer) (inc b) b) ]) column)]
                                                 (concat (take a-next-pointer prepared-column) [[a-value-index b-next-pointer]] (drop a-next-pointer prepared-column) )))
                        (recordReconst trans-table) entry-infos))]
    (distinct-tr (tr (keyorder trans-table) new-fvt new-rrt))))


(defn update
  "Returns the given transrelational table with an updated data row.
  The row is specificated by the numeric position of one of it cells.
  The update is specified by a map of attributes as their keys and the new values following.

  Example: (update table 1 2 {:id 10}) to set the attribute :id of the data row relates to cell [1,2]."
  [trans-table  row column update-map]
  (when (not-any? #(contains? (set (keyorder trans-table)) %) (keys update-map))
    (throw (IllegalArgumentException. "Update map contains illegal attribute.")))
  (let [old-row (retrieve trans-table row column)
        new-row (merge old-row update-map)]
    (insert (delete trans-table row column) new-row) ))







;; #######################################################################################################################################
;; Algera operations
;; #######################################################################################################################################




(defn project
    ""
  [trans-table attrs]
  (when (not-any? #(contains? (set (keyorder trans-table)) %) attrs)
    (throw (IllegalArgumentException. "Update map contains illegal attribute.")))
  (let [to-delete (filterv #(not (contains? (set attrs) %)) (keyorder trans-table))
        new-ko (filterv #(contains? (set attrs) %) (keyorder trans-table))
        new-fvt (reduce (fn[m attr] (dissoc m attr)) (fieldValues trans-table) to-delete)
        new-rrt (let [delete-indizes (map (fn[attr] (.indexOf (keyorder trans-table) attr)) to-delete)
                      change-indizes (map #(mod (dec %) (count (keyorder trans-table))) delete-indizes)
                      melted (sort  (mapv (fn [a b] [a b]) change-indizes delete-indizes ))
                      merged (reduce (fn[m [a b]] (if (contains?  m b)
                                                    (assoc (dissoc m b) a (get m b))
                                                    (if (contains? (set (vals m)) a)
                                                      (assoc m (get (clojure.set/map-invert m) a)  b)
                                                      (assoc m a b)))) {} melted )
                      replace-link (fn[column-index steps]
                                     (let [columns (map #(nth (recordReconst trans-table) (mod %  (count (keyorder trans-table))))
                                                        (range (inc column-index) (+ column-index steps 1)))
                                           rec-replace (fn[base columns]
                                                         (if (empty? columns)
                                                           base
                                                           (recur
                                                            (map (fn [[a b]] [a (second (nth (first columns) b))]) base)
                                                            (rest columns))))]
                                       (rec-replace (nth (recordReconst trans-table) column-index) columns)))
                      manipulated-columns (reduce (fn[m [a b] ] (assoc m a (replace-link a (mod (- b a) (count (keyorder trans-table)))))) {} merged)
                      new-rrt (let [ with-new-colums (reduce (fn[m [k v]](assoc m k v)) (vec (recordReconst trans-table)) manipulated-columns )]
                                (filter #(not (contains? (set delete-indizes) (.indexOf with-new-colums %))) with-new-colums))
                      sorted (if (= 0 (compare attrs new-ko))
                               new-rrt
                               (let [index-vector (map (fn[x] (.indexOf new-ko x)) attrs)
                                     pair-vector (map (fn[ x y] [x y]) index-vector (flatten [(rest index-vector) (first index-vector)]))]
                                    (map (fn[[a b]] (let[orig-column (nth new-rrt a)]
                                                   (map (fn [[x y] i] [x  (second (travel new-rrt i a (mod (- b a 1) (count pair-vector))))])
                                                        orig-column
                                                        (range (count orig-column)))))
                                      pair-vector)))]
                  sorted)]
     (distinct-tr (tr attrs new-fvt new-rrt))))



(defn project+
    ""
  [trans-table attrs]
  (when (not-any? #(contains? (set (keyorder trans-table)) %) attrs)
    (throw (IllegalArgumentException. "Update map contains illegal attribute.")))
  (let [converted (convert trans-table)
        new-converted (distinct (map (fn [m] (select-keys m attrs)) converted))]
     (tr new-converted)))





(defn extend
  ""
  [trans-table preds]
  (let[table (convert trans-table)
       extended (map (fn [row]( reduce (fn [m [k v]] (assoc m k (v m))) row preds )) table)]
    (tr extended)))



(defn union
  ""
  [tr1 & more]
   (tr (flatten (apply conj  (convert tr1) (map convert more)))))




(defn intersection
  ""
  [tr1 & more]
  (tr (apply clojure.set/intersection (set (convert tr1)) (map #(set (convert %)) more))))




(defn difference
  ""
  [tr1 & more]
  (tr (clojure.set/difference (set (convert tr1))  (map #(set (convert %)) more))))





(defmacro tr-fn
  "Behaves like fn, but stores the source code in the metadata to allow
  optimisation."
  [args body]
  (with-meta (list 'fn args body)
              {:body (list 'quote body)
               :args (list 'quote args)}))




(def people (tr [ {:id "S1" :name "Smith" :status 20 :city "London"}
      {:id "S2" :name "Jones" :status 10 :city "Paris"}
      {:id "S3" :name "Blake" :status 30 :city "Paris"}
      {:id "S4" :name "Clark" :status 20 :city "London"}
      {:id "S5" :name "Adams" :status 30 :city "Athens"}]))








(def replace-map
  '{and intersection
    or union})


(replace replace-map '(or (+ 1 1) (- 2 3)))



(defn- down-to-up-scan
  ""
  [column f right-value]
  (loop [c column
         result []]
    (if (and (not-empty c) (f (:value (last c)) right-value))
      (recur (drop-last c) (conj result (last c)))
      result)))




(defn- up-to-down-scan
  ""
  [column f right-value]
  (loop [c column
         result []]
    (if (and (not-empty c) (f (:value (first c)) right-value))
      (recur (rest c) (conj result (first c)))
      result)))




(defn- not=-scan
  ""
  [column value]
  (let [ops (if (string? value)
              [#(neg? (compare %1 %2)) #(pos? (compare %1 %2))]
              [< >])]
  (apply conj (up-to-down-scan column (first ops) value) (down-to-up-scan column (second ops) value))))



(defn- area-search
  ""
  [trans-table attr f right-value]
  (let [up-to-down #{<= <}
        down-to-up #{>= >}
        column (get (fieldValues trans-table) attr)]
    (map #(retrieve trans-table % (.indexOf (keyorder trans-table) attr))
    (flatten (map (fn [n] (range (:from n) (inc (:to n))))
       (cond
         (contains? up-to-down f) (up-to-down-scan column f right-value)
         (contains? down-to-up f) (down-to-up-scan column f right-value)
         (= not= f) (not=-scan column right-value)
         :else []))))))




(defn- point-search
  ""
  [trans-table attr value]
  (let [binary-search (fn [column value] (java.util.Collections/binarySearch column value #(compare (:value %1) %2)))
        found (binary-search (get (fieldValues trans-table) attr) value)]
    (if (neg? found)
      '()
      (let [entry (nth  (get (fieldValues trans-table) attr) found)]
        (map #(retrieve trans-table % (.indexOf (keyorder trans-table) attr)) (range (:from entry) (inc (:to entry))))))))



(def flip-compare-map  {< >=
                        > <=
                        >= <
                        <= >
                        = =
                        not= not=})



(defmacro dbg[x] `(let [x# ~x] (println "dbg:" '~x "=" x#) x#))


(defmacro unflat
  [ast]
  (let [op (first ast)
        args (rest ast)
        pairs (map (fn [arg1 arg2] (list op arg1 arg2)) args (rest args))
        new-ast (conj pairs 'and)]
    (list 'quote new-ast)))



(defmacro key-of-tr?
  [tr-alias term]
  (cond
   (and (= 2 (count term))
        (keyword? (first term))
        (= tr-alias (second term))) true
   (and (= 3 (count term))
        (= 'get (first term))
        (= tr-alias (second term))) true
   :else false))





(use 'clojure.tools.trace)

(defmacro map-key-of-tr?
  [tr-alias terms]
  (let [check (fn [term]  (println :penis term)
                          (cond
                             (and (coll? term)
                                  (= 2 (count term))
                                  (keyword? (first term))
                                  (= tr-alias (second term))) (first term),
                             (and (coll? term)
                                  (= 3 (count term))
                                  (= 'get (first term))
                                  (= tr-alias (second term))) (last term),
                             :else nil))
        result (map check terms)]
                    (list 'quote result)))




(map-key-of-tr? t (<=  (:k45ey t) (:key t) (:kewerty t) (get t "blabla")))

(if :234
  true
  false)



(defn- optimize
  ""
  [arg ast]
  (let []
    (cond
       (and (coll? ast) (contains? #{ 'and 'or} (first ast)))
            (reverse (into
              (case (first ast)
                and '(intersection)
                or '(union))
              (map #(optimize arg %) (rest ast)))),

       (and (coll? ast) (contains? #{'< '> '<= '>= '= 'not=} (first ast)))
         (if
           (< 2 (count (rest ast)))
           (optimize arg '(unflat ast))
           (let [key-map (map-key-of-tr? arg (rest ast))]
             (if
               (some #(not (nil? %)) key-map)
                (let [f (if (last key-map)
                            (get flip-compare-map arg)
                            arg)
                      ast (if (last key-map)
                             (reverse (rest ast))
                             (rest ast))]
                              (cond
                                (contains? (keys flip-compare-map) (first ast)) (apply conj '() (last ast) (first ast) 'area-search)
                                (= '= (first ast))  (apply conj '() (last ast) (first ast) 'point-search)
                                (= 'not= (first ast)) (apply conj 'not=-scan)))
               ast))), ;TODO compare without tuple

       (coll? ast) '(), ;TODO not our business

       :else ast))) ;TODO const




(defmacro restrict-fn
  ""
  [arg body]
  (with-meta (list 'fn arg body)
              {:body (let[new-list (optimize arg body)]
                       (list 'quote  new-list))}))


(meta (restrict-fn [t] (and (>= (:status t) 30) (= (:city t) "Paris") (= (:name t) (:city t)))))


 (area-search people :status >=  30)
  (area-search people :status < 30)
  (area-search people :city not= "London")


 (point-search people :city "London")

