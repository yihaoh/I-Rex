-- q6

-- original query
/*
select
    sum(l_extendedprice * l_discount) as revenue
from
    lineitem
where
    l_shipdate >= date '1995-06-01'
    and l_shipdate < date '1995-06-01' + interval '1' year
    and l_discount between 0.05 - 0.01 and 0.05 + 0.01
    and l_quantity < 60;
*/

-- query id: q6_join_filter
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber) - 1,
           ROW(l_orderkey, l_linenumber),
           l_orderkey, l_partkey, l_suppkey, l_linenumber
    FROM lineitem
    WHERE l_shipdate >= date '1995-06-01'
    AND l_shipdate < date '1995-06-01' + interval '1' year
    AND l_discount between 0.05 - 0.01 and 0.05 + 0.01
    AND l_quantity < 60
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey,
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber),
       *
FROM lineitem
WHERE l_shipdate >= date '1995-06-01'
AND l_shipdate < date '1995-06-01' + interval '1' year
AND l_discount between 0.05 - 0.01 and 0.05 + 0.01
AND l_quantity < 60
AND ROW(l_orderkey, l_linenumber) >= %(iid,min)s
AND ROW(l_orderkey, l_linenumber) < %(iid,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
ORDER BY l_orderkey, l_linenumber;

-- naive page fetch query
SELECT ROW(l_orderkey, l_linenumber),
       *
FROM lineitem
WHERE l_shipdate >= date '1995-06-01'
AND l_shipdate < date '1995-06-01' + interval '1' year
AND l_discount between 0.05 - 0.01 and 0.05 + 0.01
AND l_quantity < 60
ORDER BY l_orderkey, l_linenumber;
