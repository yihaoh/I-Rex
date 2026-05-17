-- Q5

-- original query

/*
select n_name, sum(l_extendedprice * (1 - l_discount)) as revenue
from customer, orders, lineitem, supplier, nation, region
where
    c_custkey = o_custkey
    and l_orderkey = o_orderkey
    and l_suppkey = s_suppkey
    and c_nationkey = s_nationkey
    and s_nationkey = n_nationkey
    and n_regionkey = r_regionkey
    and r_name = 'ASIA'
    and o_orderdate >= date '1996-01-01'
    and o_orderdate < date '1996-01-01' + interval '2' YEAR
group by n_name
order by revenue desc
LIMIT 1; 
*/


-- ========= 2. join&filter table =========
-- query id: q5_join_filter
-- milestone query
WITH tmp(seq, iid, c_custkey, o_custkey, l_orderkey, o_orderkey, l_suppkey, s_suppkey, c_nationkey, s_nationkey, n_nationkey, n_regionkey, r_regionkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey) - 1,
           ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey),
           c_custkey, o_custkey, l_orderkey, o_orderkey, l_suppkey, s_suppkey, c_nationkey, s_nationkey, n_nationkey, n_regionkey, r_regionkey, l_linenumber
    FROM customer, orders, lineitem, supplier, nation, region
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
       ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
       ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
       ARRAY[MIN(r_regionkey), MAX(r_regionkey)] r_regionkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey),
       *
FROM customer, orders, lineitem, supplier, nation, region
WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey) >= %(iid,min)s 
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey) < %(iid,max)s    -- id pushdown
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
-- AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s
-- AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
-- AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
-- AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
-- AND r_regionkey BETWEEN %(r_regionkey,min)s AND %(r_regionkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey;

-- naive page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey), 
       *
FROM customer, orders, lineitem, supplier, nation, region
WHERE
    c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber, s_suppkey, n_nationkey, r_regionkey;

-- ========= 3. group table =========
-- query id: q5_group
-- milestone query
WITH tmp(seq, iid, c_custkey, o_custkey, l_orderkey, o_orderkey, l_suppkey, s_suppkey, c_nationkey, s_nationkey, n_nationkey, n_regionkey, r_regionkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY n_name) - 1,
           ROW(n_name),
           ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, 
            ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
            ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
            ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
            ARRAY[MIN(r_regionkey), MAX(r_regionkey)] r_regionkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM customer, orders, lineitem, supplier, nation, region
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
    GROUP BY n_name
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(o_custkey[1]), MAX(o_custkey[2])] o_custkey, 
        ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, 
        ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(s_suppkey[1]), MAX(s_suppkey[2])] s_suppkey, 
        ARRAY[MIN(c_nationkey[1]), MAX(c_nationkey[2])] c_nationkey, ARRAY[MIN(s_nationkey[1]), MAX(s_nationkey[2])] s_nationkey, 
        ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey, ARRAY[MIN(n_regionkey[1]), MAX(n_regionkey[2])] n_regionkey, 
        ARRAY[MIN(r_regionkey[1]), MAX(r_regionkey[2])] r_regionkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(n_name),
       n_name,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_linenumber), ROW(s_suppkey), ROW(n_nationkey), ROW(r_regionkey)))
FROM customer, orders, lineitem, supplier, nation, region
WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
AND ROW(n_name) >= %(iid,min)s 
AND ROW(n_name) < %(iid,max)s -- id pushdown
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s -- sargable pushdown starts here
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
-- AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s
-- AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
-- AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
-- AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
-- AND r_regionkey BETWEEN %(r_regionkey,min)s AND %(r_regionkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY n_name
ORDER BY n_name;

-- naive page fetch query
SELECT ROW(n_name), n_name,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_linenumber), ROW(s_suppkey), ROW(n_nationkey), ROW(r_regionkey)))
FROM customer, orders, lineitem, supplier, nation, region
WHERE
    c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
GROUP BY n_name
ORDER BY n_name;

-- ========= 4. output table =========
-- query id: q5_output
-- milestone query
WITH tmp(seq, iid, c_custkey, o_custkey, l_orderkey, o_orderkey, l_suppkey, s_suppkey, c_nationkey, s_nationkey, n_nationkey, n_regionkey, r_regionkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY n_name) - 1,
            ROW(n_name),
            ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, 
            ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
            ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
            ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
            ARRAY[MIN(r_regionkey), MAX(r_regionkey)] r_regionkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM customer, orders, lineitem, supplier, nation, region
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
    GROUP BY n_name
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(o_custkey[1]), MAX(o_custkey[2])] o_custkey, 
        ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, 
        ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(s_suppkey[1]), MAX(s_suppkey[2])] s_suppkey, 
        ARRAY[MIN(c_nationkey[1]), MAX(c_nationkey[2])] c_nationkey, ARRAY[MIN(s_nationkey[1]), MAX(s_nationkey[2])] s_nationkey, 
        ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey, ARRAY[MIN(n_regionkey[1]), MAX(n_regionkey[2])] n_regionkey, 
        ARRAY[MIN(r_regionkey[1]), MAX(r_regionkey[2])] r_regionkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(n_name),
       n_name,
       SUM(l_extendedprice * (1 - l_discount)) revenue,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_linenumber), ROW(s_suppkey), ROW(n_nationkey), ROW(r_regionkey)))
FROM customer, orders, lineitem, supplier, nation, region
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s -- sargable pushdown starts here
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
-- AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s
-- AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
-- AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
-- AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
-- AND r_regionkey BETWEEN %(r_regionkey,min)s AND %(r_regionkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY n_name
ORDER BY n_name, revenue;

-- naive page fetch query
SELECT ROW(SUM(l_extendedprice * (1 - l_discount)), n_name),
       n_name,
       SUM(l_extendedprice * (1 - l_discount)) revenue,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_linenumber), ROW(s_suppkey), ROW(n_nationkey), ROW(r_regionkey)))
FROM customer, orders, lineitem, supplier, nation, region
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND l_suppkey = s_suppkey
    AND c_nationkey = s_nationkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    -- AND r_name = 'ASIA'
    AND o_orderdate >= DATE '1996-01-01'
    AND o_orderdate < DATE '1996-01-01' + interval '2' YEAR
GROUP BY n_name
ORDER BY n_name, revenue;