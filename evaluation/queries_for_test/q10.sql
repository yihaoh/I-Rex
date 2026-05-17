-- q10

-- original query
/*
select
    c_custkey,
    c_name,
    sum(l_extendedprice * (1 - l_discount)) as revenue,
    c_acctbal,
    n_name,
    c_address,
    c_phone,
    c_comment
from
    customer,
    orders,
    lineitem,
    nation
where
    c_custkey = o_custkey
    and l_orderkey = o_orderkey
    and o_orderdate >= date '1996-01-01'
    and o_orderdate < date '1996-01-01' + interval '3' month
    and l_returnflag = 'R'
    and c_nationkey = n_nationkey
group by
    c_custkey,
    c_name,
    c_acctbal,
    c_phone,
    n_name,
    c_address,
    c_comment
order by
    revenue desc
*/


-- query id: q10_join_filter
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, n_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey) - 1,
           ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey),
           c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, n_nationkey
    FROM customer, orders, lineitem, nation
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND o_orderdate >= date '1996-01-01'
    AND o_orderdate < date '1996-01-01' + interval '3' month
    -- AND l_returnflag = 'R'
    AND c_nationkey = n_nationkey
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
       ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
       ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
       ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey),
        *
FROM customer, orders, lineitem, nation
WHERE c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate >= date '1996-01-01'
AND o_orderdate < date '1996-01-01' + interval '3' month
-- AND l_returnflag = 'R'
AND c_nationkey = n_nationkey
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey) >= %(iid,min)s 
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey) < %(iid,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s 
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s 
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s 
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s 
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s 
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s 
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s 
AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s 
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey;

-- naive page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey),
        *
FROM customer, orders, lineitem, nation
WHERE c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate >= date '1996-01-01'
AND o_orderdate < date '1996-01-01' + interval '3' month
-- AND l_returnflag = 'R'
AND c_nationkey = n_nationkey
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber, n_nationkey;


-- query id: q10_group
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, n_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey) - 1,
           ROW(c_custkey),
           ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
            ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
            ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
            ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey
    FROM customer, orders, lineitem, nation
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND o_orderdate >= date '1996-01-01'
    AND o_orderdate < date '1996-01-01' + interval '3' month
    -- AND l_returnflag = 'R'
    AND c_nationkey = n_nationkey
    GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(c_nationkey[1]), MAX(c_nationkey[2])] c_nationkey, 
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, 
       ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, 
       ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber, ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey),
       c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey), ROW(l_linenumber), ROW(n_nationkey)))
FROM customer, orders, lineitem, nation
WHERE c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate >= date '1996-01-01'
AND o_orderdate < date '1996-01-01' + interval '3' month
-- AND l_returnflag = 'R'
AND c_nationkey = n_nationkey
AND ROW(c_custkey) >= %(iid,min)s 
AND ROW(c_custkey) < %(iid,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s 
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s 
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s 
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s 
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s 
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s 
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s 
AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s 
GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment
ORDER BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment;

-- naive page fetch query
SELECT ROW(c_custkey),
       c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey), ROW(l_linenumber), ROW(n_nationkey)))
FROM customer, orders, lineitem, nation
WHERE c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate >= date '1996-01-01'
AND o_orderdate < date '1996-01-01' + interval '3' month
-- AND l_returnflag = 'R'
AND c_nationkey = n_nationkey
GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment
ORDER BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment;


-- query id: q10_output
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey, o_orderkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, n_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey) - 1,
           ROW(c_custkey),
           ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
            ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
            ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
            ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey
    FROM customer, orders, lineitem, nation
    WHERE c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND o_orderdate >= date '1996-01-01'
    AND o_orderdate < date '1996-01-01' + interval '3' month
    -- AND l_returnflag = 'R'
    AND c_nationkey = n_nationkey
    GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(c_nationkey[1]), MAX(c_nationkey[2])] c_nationkey, 
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, 
       ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, 
       ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber, ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey),
       c_custkey, c_name, sum(l_extendedprice * (1 - l_discount)) as revenue, c_acctbal, n_name, c_address, c_phone, c_comment,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey), ROW(l_linenumber), ROW(n_nationkey)))
FROM customer, orders, lineitem, nation
WHERE c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate >= date '1996-01-01'
AND o_orderdate < date '1996-01-01' + interval '3' month
-- AND l_returnflag = 'R'
AND c_nationkey = n_nationkey
AND ROW(c_custkey) >= %(iid,min)s 
AND ROW(c_custkey) < %(iid,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s 
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s 
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s 
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s 
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s 
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s 
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s 
AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s 
GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment
ORDER BY c_custkey, c_name, revenue, c_acctbal, n_name, c_address, c_phone, c_comment;

-- naive page fetch query
SELECT ROW(c_custkey),
       c_custkey, c_name, sum(l_extendedprice * (1 - l_discount)) as revenue, c_acctbal, n_name, c_address, c_phone, c_comment,
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey), ROW(l_linenumber), ROW(n_nationkey)))
FROM customer, orders, lineitem, nation
WHERE c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate >= date '1996-01-01'
AND o_orderdate < date '1996-01-01' + interval '3' month
-- AND l_returnflag = 'R'
AND c_nationkey = n_nationkey
GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment
ORDER BY c_custkey, c_name, revenue, c_acctbal, n_name, c_address, c_phone, c_comment;
