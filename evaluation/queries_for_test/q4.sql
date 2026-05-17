-- q4

-- original query
/*
select
    o_orderpriority,
    count(*) as order_count
from
    orders
where
    o_orderdate >= date '1996-06-01'
    and o_orderdate < date '1996-06-01' + interval '3' month
    and exists (
        select
            *
        from
            lineitem
        where
            l_orderkey = o_orderkey
            and l_commitdate < l_receiptdate
    )
group by
    o_orderpriority
order by
    o_orderpriority
*/


-- query id: q4_join_filter
-- milestone query
WITH tmp(seq, iid, o_orderkey, o_custkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_orderkey) - 1,
           ROW(o_orderkey),
           o_orderkey, o_custkey
    FROM orders
    WHERE o_orderdate >= date '1996-06-01'
    AND o_orderdate < date '1996-06-01' + interval '3' month
    AND EXISTS (
        SELECT *
        FROM lineitem
        WHERE l_orderkey = o_orderkey
        AND l_commitdate < l_receiptdate
    )
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey,
       BLMFL(numeric_send(o_orderkey)) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(o_orderkey), *
FROM orders
WHERE o_orderdate >= date '1996-06-01'
AND o_orderdate < date '1996-06-01' + interval '3' month
AND EXISTS (
    SELECT *
    FROM lineitem
    WHERE l_orderkey = o_orderkey
    AND l_commitdate < l_receiptdate
    AND l_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
)
AND ROW(o_orderkey) >= %(iid,min)s
AND ROW(o_orderkey) < %(iid,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(o_orderkey))
ORDER BY o_orderkey;

-- naive page fetch query
SELECT ROW(o_orderkey), *
FROM orders
WHERE o_orderdate >= date '1996-06-01'
AND o_orderdate < date '1996-06-01' + interval '3' month
AND EXISTS (
    SELECT *
    FROM lineitem
    WHERE l_orderkey = o_orderkey
    AND l_commitdate < l_receiptdate
)
ORDER BY o_orderkey;


-- query id: q4_output
-- milestone query
WITH tmp(seq, iid, o_orderkey, o_custkey, blmfl_filter) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_orderpriority) - 1,
           ROW(o_orderpriority),
           ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey,
           BLMFL(numeric_send(o_orderkey)) blmfl_filter
    FROM orders
    WHERE o_orderdate >= date '1996-06-01'
    AND o_orderdate < date '1996-06-01' + interval '3' month
    AND EXISTS (
        SELECT *
        FROM lineitem
        WHERE l_orderkey = o_orderkey
        AND l_commitdate < l_receiptdate
    )
    GROUP BY o_orderpriority
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[1])] o_orderkey, 
       ARRAY[MIN(o_custkey[2]), MAX(o_custkey[2])] o_custkey,
       BLMFL_AGG(blmfl_filter) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(o_orderpriority), o_orderpriority, COUNT(*) as order_count,
       MIN_IID(ROW(ROW(o_orderkey)))
FROM orders
WHERE o_orderdate >= date '1996-06-01'
AND o_orderdate < date '1996-06-01' + interval '3' month
AND EXISTS (
    SELECT *
    FROM lineitem
    WHERE l_orderkey = o_orderkey
    AND l_commitdate < l_receiptdate
    AND l_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
)
AND ROW(o_orderpriority) >= %(iid,min)s
AND ROW(o_orderpriority) < %(iid,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(o_orderkey))
GROUP BY o_orderpriority
ORDER BY o_orderpriority;

-- naive page fetch query
SELECT ROW(o_orderpriority), o_orderpriority, COUNT(*) as order_count,
       MIN_IID(ROW(ROW(o_orderkey)))
FROM orders
WHERE o_orderdate >= date '1996-06-01'
AND o_orderdate < date '1996-06-01' + interval '3' month
AND EXISTS (
    SELECT *
    FROM lineitem
    WHERE l_orderkey = o_orderkey
    AND l_commitdate < l_receiptdate
)
GROUP BY o_orderpriority
ORDER BY o_orderpriority;
