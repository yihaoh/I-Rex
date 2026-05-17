-- q14


-- original query
/*
select
    100.00 * sum(case
        when p_type like 'PROMO%'
            then l_extendedprice * (1 - l_discount)
        else 0
    end) / sum(l_extendedprice * (1 - l_discount)) as promo_revenue
from
    lineitem,
    part
where
    l_partkey = p_partkey
    and l_shipdate >= date '1996-06-01'
    and l_shipdate < date '1996-06-01' + interval '1' month
*/

-- query id: q14_join_filter
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber, p_partkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber, p_partkey) - 1,
           ROW(l_orderkey, l_linenumber, p_partkey),
           l_orderkey, l_partkey, l_suppkey, l_linenumber, p_partkey
    FROM lineitem, part
    WHERE l_partkey = p_partkey
    AND l_shipdate >= date '1996-06-01'
    AND l_shipdate < date '1996-06-01' + interval '1' month
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber, p_partkey),
       *
FROM lineitem, part
WHERE l_partkey = p_partkey
AND l_shipdate >= date '1996-06-01'
AND l_shipdate < date '1996-06-01' + interval '1' month
AND ROW(l_orderkey, l_linenumber, p_partkey) >= %(iid,min)s
AND ROW(l_orderkey, l_linenumber, p_partkey) < %(iid,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
ORDER BY l_orderkey, l_linenumber, p_partkey;

-- naive page fetch query
SELECT ROW(l_orderkey, l_linenumber, p_partkey),
       *
FROM lineitem, part
WHERE l_partkey = p_partkey
AND l_shipdate >= date '1996-06-01'
AND l_shipdate < date '1996-06-01' + interval '1' month
ORDER BY l_orderkey, l_linenumber, p_partkey;
