-- Q18

-- original query
/*
select
    c_name,
    c_custkey,
    o_orderkey,
    o_orderdate,
    o_totalprice,
    sum(l_quantity)
from
    customer,
    orders,
    lineitem
where
    o_orderkey in (
        select
            l_orderkey
        from
            lineitem
        group by
            l_orderkey having
                sum(l_quantity) > 100
    )
    and c_custkey = o_custkey
    and o_orderkey = l_orderkey
group by
    c_name,
    c_custkey,
    o_orderkey,
    o_orderdate,
    o_totalprice
order by
    o_totalprice desc,
    o_orderdate
limit 100;
*/


-- query id: q18_join_filter
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderdate) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber) - 1,
           ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber),
           c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderdate
    FROM customer, orders, lineitem
    WHERE o_orderkey IN (
        SELECT l_orderkey
        FROM lineitem
        GROUP BY l_orderkey 
        HAVING sum(l_quantity) > 100
    )
    and c_custkey = o_custkey
    and o_orderkey = l_orderkey
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
       ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
       ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
       ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate,
       BLMFL(numeric_send(o_orderkey)) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber),
       *
FROM customer, orders, lineitem
WHERE o_orderkey IN (
    SELECT l_orderkey
    FROM lineitem
    WHERE l_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
    GROUP BY l_orderkey 
    HAVING sum(l_quantity) > 100
)
AND c_custkey = o_custkey
AND o_orderkey = l_orderkey
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber) >= %(iid,min)s 
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber) < %(iid,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(o_orderkey))
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber;

-- naive page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber),
       *
FROM customer, orders, lineitem
WHERE o_orderkey IN (
    SELECT l_orderkey
    FROM lineitem
    GROUP BY l_orderkey 
    HAVING sum(l_quantity) > 100
)
AND c_custkey = o_custkey
AND o_orderkey = l_orderkey
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber;


-- query id: q18_group
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderdate, blmfl_filter) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey, o_orderkey, o_orderdate, o_totalprice) - 1,
           ROW(c_custkey, o_orderkey, o_orderdate, o_totalprice),
           ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
            ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
            ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
            ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate, 
            BLMFL(numeric_send(o_orderkey)) blmfl_filter
    FROM customer, orders, lineitem
    WHERE o_orderkey IN (
        SELECT l_orderkey
        FROM lineitem
        GROUP BY l_orderkey 
        HAVING sum(l_quantity) > 100
    )
    AND c_custkey = o_custkey
    AND o_orderkey = l_orderkey
    GROUP BY c_custkey, o_orderkey, o_orderdate, o_totalprice
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(c_nationkey[1]), MAX(c_nationkey[2])] c_nationkey, 
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, 
       ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, 
       ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber, ARRAY[MIN(o_orderdate[1]), MAX(o_orderdate[2])] o_orderdate,
       BLMFL_AGG(blmfl_filter) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey, o_orderkey, o_orderdate, o_totalprice),
        c_custkey, o_orderkey, o_orderdate, o_totalprice,
        MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM customer, orders, lineitem
WHERE o_orderkey IN (
    SELECT l_orderkey
    FROM lineitem
    WHERE l_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
    GROUP BY l_orderkey 
    HAVING sum(l_quantity) > 100
)
AND c_custkey = o_custkey
AND o_orderkey = l_orderkey
AND ROW(c_custkey, o_orderkey, o_orderdate, o_totalprice) >= %(iid,min)s 
AND ROW(c_custkey, o_orderkey, o_orderdate, o_totalprice) < %(iid,max)s 
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(o_orderkey))
GROUP BY c_custkey, o_orderkey, o_orderdate, o_totalprice
ORDER BY c_custkey, o_orderkey, o_orderdate, o_totalprice;

-- naive page fetch query
SELECT ROW(c_custkey, o_orderkey, o_orderdate, o_totalprice),
        c_custkey, o_orderkey, o_orderdate, o_totalprice,
        MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM customer, orders, lineitem
WHERE o_orderkey IN (
    SELECT l_orderkey
    FROM lineitem
    GROUP BY l_orderkey 
    HAVING sum(l_quantity) > 100
)
AND c_custkey = o_custkey
AND o_orderkey = l_orderkey
GROUP BY c_custkey, o_orderkey, o_orderdate, o_totalprice
ORDER BY c_custkey, o_orderkey, o_orderdate, o_totalprice;


-- query id: q18_output
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderdate, blmfl_filter) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_totalprice, o_orderdate, c_custkey, o_orderkey) - 1,
           ROW(o_totalprice, o_orderdate, c_custkey, o_orderkey),
           ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
            ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
            ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
            ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate,
            BLMFL(numeric_send(o_orderkey)) blmfl_filter
    FROM customer, orders, lineitem
    WHERE o_orderkey IN (
        SELECT l_orderkey
        FROM lineitem
        GROUP BY l_orderkey 
        HAVING sum(l_quantity) > 100
    )
    AND c_custkey = o_custkey
    AND o_orderkey = l_orderkey
    GROUP BY c_custkey, o_orderkey, c_name, o_orderdate, o_totalprice
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(c_nationkey[1]), MAX(c_nationkey[2])] c_nationkey, 
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, 
       ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, 
       ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber, ARRAY[MIN(o_orderdate[1]), MAX(o_orderdate[2])] o_orderdate, 
       BLMFL_AGG(blmfl_filter) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey),
        c_name, c_custkey, o_orderkey, o_orderdate, o_totalprice, sum(l_quantity),
        MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM customer, orders, lineitem
WHERE o_orderkey IN (
    SELECT l_orderkey
    FROM lineitem
    WHERE l_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
    GROUP BY l_orderkey 
    HAVING sum(l_quantity) > 100
)
AND c_custkey = o_custkey
AND o_orderkey = l_orderkey
AND ROW(o_totalprice, o_orderdate, c_custkey, o_orderkey) >= %(iid,min)s 
AND ROW(o_totalprice, o_orderdate, c_custkey, o_orderkey) < %(iid,max)s 
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(o_orderkey))
GROUP BY c_custkey, o_orderkey, c_name, o_orderdate, o_totalprice
ORDER BY o_totalprice, o_orderdate, c_custkey, o_orderkey;

-- naive page fetch query
SELECT ROW(c_custkey),
        c_name, c_custkey, o_orderkey, o_orderdate, o_totalprice, sum(l_quantity),
        MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM customer, orders, lineitem
WHERE o_orderkey IN (
    SELECT l_orderkey
    FROM lineitem
    GROUP BY l_orderkey 
    HAVING sum(l_quantity) > 100
)
AND c_custkey = o_custkey
AND o_orderkey = l_orderkey
GROUP BY c_custkey, o_orderkey, c_name, o_orderdate, o_totalprice
ORDER BY o_totalprice, o_orderdate, c_custkey, o_orderkey;
