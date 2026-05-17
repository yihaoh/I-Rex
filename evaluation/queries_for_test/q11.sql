-- Q11

-- original query
/*
SELECT ps_partkey, SUM(ps_supplycost * ps_availqty) AS value
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey 
AND s_nationkey = n_nationkey AND n_name = 'PERU'
GROUP BY ps_partkey
HAVING SUM(ps_supplycost * ps_availqty) > (
    SELECT SUM(ps_supplycost * ps_availqty) * 2
    FROM partsupp, supplier, nation
    WHERE ps_suppkey = s_suppkey 
    AND s_nationkey = n_nationkey
    AND n_name = 'PERU'
)
ORDER BY value desc;
*/

-- cached query
/*
SELECT SUM(ps_supplycost * ps_availqty) * 0.000002
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey 
AND s_nationkey = n_nationkey
AND n_name = 'PERU'
*/


-- ========= 2. join&filter table =========
-- query id: q11_join_filter
-- milestone query
WITH tmp(seq, iid, ps_partkey, ps_suppkey, s_suppkey, s_nationkey, n_nationkey, n_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY ps_partkey, ps_suppkey, s_suppkey, n_nationkey) - 1,
           ROW(ps_partkey, ps_suppkey, s_suppkey, n_nationkey),
           ps_partkey, ps_suppkey, s_suppkey, s_nationkey, n_nationkey, n_regionkey
    FROM partsupp, supplier, nation
    WHERE ps_suppkey = s_suppkey
    AND s_nationkey = n_nationkey AND n_name = 'PERU'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
       ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
       ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(ps_partkey, ps_suppkey, s_suppkey, n_nationkey),
       *
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey
AND s_nationkey = n_nationkey AND n_name = 'PERU'
AND ROW(ps_partkey, ps_suppkey, s_suppkey, n_nationkey) >= %(iid,min)s 
AND ROW(ps_partkey, ps_suppkey, s_suppkey, n_nationkey) < %(iid,max)s    -- id pushdown
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s   -- sargable pushdown starts here
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
ORDER BY ps_partkey, ps_suppkey, s_suppkey, n_nationkey;

-- naive page fetch query
SELECT ROW(ps_partkey, ps_suppkey, s_suppkey, n_nationkey), *
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey
AND s_nationkey = n_nationkey
AND n_name = 'PERU'
ORDER BY ps_partkey, ps_suppkey, s_suppkey, n_nationkey;

-- ========= 3. group table =========
-- query id: q11_group
-- milestone query
WITH tmp(seq, iid, ps_partkey, ps_suppkey, s_suppkey, s_nationkey, n_nationkey, n_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY ps_partkey) - 1,
           ROW(ps_partkey),
           ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
            ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
            ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey
    FROM partsupp, supplier, nation
    WHERE ps_suppkey = s_suppkey
    AND s_nationkey = n_nationkey AND n_name = 'PERU'
    GROUP BY ps_partkey
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(ps_partkey[1]), MAX(ps_partkey[2])] ps_partkey, ARRAY[MIN(ps_suppkey[1]), MAX(ps_suppkey[2])] ps_suppkey, 
       ARRAY[MIN(s_suppkey[1]), MAX(s_suppkey[2])] s_suppkey, ARRAY[MIN(s_nationkey[1]), MAX(s_nationkey[2])] s_nationkey, 
       ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey, ARRAY[MIN(n_regionkey[1]), MAX(n_regionkey[2])] n_regionkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(ps_partkey),
       ps_partkey,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(s_suppkey), ROW(n_nationkey)))
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey
AND s_nationkey = n_nationkey AND n_name = 'PERU'
AND ROW(ps_partkey) >= %(iid,min)s 
AND ROW(ps_partkey) < %(iid,max)s    -- id pushdown
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s   -- sargable pushdown starts here
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
GROUP BY ps_partkey
ORDER BY ps_partkey;

-- naive page fetch query
SELECT ps_partkey, sum(ps_supplycost * ps_availqty) as value,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(s_suppkey), ROW(n_nationkey)))
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey
AND s_nationkey = n_nationkey
AND n_name = 'PERU'
GROUP BY ps_partkey
ORDER BY ps_partkey;


-- ========= 4. output table (tbd, need bloom filter) =========
-- query id: q11_output
-- milestone query
WITH tmp(seq, iid, ps_partkey, ps_suppkey, s_suppkey, s_nationkey, n_nationkey, n_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY ps_partkey) - 1,
           ROW(ps_partkey),
           ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
            ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
            ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey
    FROM partsupp, supplier, nation
    WHERE ps_suppkey = s_suppkey
    AND s_nationkey = n_nationkey AND n_name = 'PERU'
    GROUP BY ps_partkey
    HAVING SUM(ps_supplycost * ps_availqty) > (SELECT SUM(ps_supplycost * ps_availqty) * 0.000002
                                                FROM partsupp, supplier, nation
                                                WHERE ps_suppkey = s_suppkey 
                                                AND s_nationkey = n_nationkey
                                                AND n_name = 'PERU'
                                              )  -- independent scalar query
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(ps_partkey[1]), MAX(ps_partkey[2])] ps_partkey, ARRAY[MIN(ps_suppkey[1]), MAX(ps_suppkey[2])] ps_suppkey, 
       ARRAY[MIN(s_suppkey[1]), MAX(s_suppkey[2])] s_suppkey, ARRAY[MIN(s_nationkey[1]), MAX(s_nationkey[2])] s_nationkey, 
       ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey, ARRAY[MIN(n_regionkey[1]), MAX(n_regionkey[2])] n_regionkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(ps_partkey),
       ps_partkey,
       SUM(ps_supplycost * ps_availqty) AS value,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(s_suppkey), ROW(n_nationkey)))
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey
AND s_nationkey = n_nationkey AND n_name = 'PERU'
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s   -- sargable pushdown starts here
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
AND ROW(ps_partkey) >= %(iid,min)s   -- note that id has to be in HAVING due to SUM
AND ROW(ps_partkey) < %(iid,max)s
GROUP BY ps_partkey
HAVING SUM(ps_supplycost * ps_availqty) > %(cached_scalar)s  -- independent scalar query
ORDER BY ps_partkey, value;

-- naive page fetch query
SELECT ROW(ps_partkey),
       ps_partkey,
       SUM(ps_supplycost * ps_availqty) AS value,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(s_suppkey), ROW(n_nationkey)))
FROM partsupp, supplier, nation
WHERE ps_suppkey = s_suppkey
AND s_nationkey = n_nationkey AND n_name = 'PERU'
GROUP BY ps_partkey
HAVING SUM(ps_supplycost * ps_availqty) > (
    SELECT SUM(ps_supplycost * ps_availqty) * 0.000002
    FROM partsupp, supplier, nation
    WHERE ps_suppkey = s_suppkey 
    AND s_nationkey = n_nationkey
    AND n_name = 'PERU'
)
ORDER BY ps_partkey, value;