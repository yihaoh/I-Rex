-- Q3

-- original query
/*
SELECT
    l_orderkey,
	SUM(l_extendedprice * (1 - l_discount)) AS revenue,
	o_orderdate,
	o_shippriority
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
	AND c_custkey = o_custkey
	AND l_orderkey = o_orderkey
	AND o_orderdate < date '1996-01-01'
	AND l_shipdate > date '1996-01-01'
GROUP BY l_orderkey, o_orderdate, o_shippriority
ORDER BY revenue DESC, o_orderdate
LIMIT 10;
*/


-- ====================== SELECT context ========================

-- ========= 2. join&filter table =========
-- query id: q3_join_filter
-- milestone query
WITH tmp(seq, iid, c_custkey, o_orderkey, l_orderkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber) - 1,
        ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber), 
        c_custkey, o_orderkey, l_orderkey, l_linenumber, o_orderdate
    FROM customer, orders, lineitem
    WHERE c_mktsegment = 'HOUSEHOLD'
	    AND c_custkey = o_custkey
	    AND l_orderkey = o_orderkey
	    AND o_orderdate < date '1996-01-01'
	    AND l_shipdate > date '1996-01-01'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, 
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber,
       ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber), *
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
AND c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate < date '1996-01-01'
AND l_shipdate > date '1996-01-01'
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber) >= %(iid,min)s      -- id pushdown
AND ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber) < %(iid,max)s       -- omit if fetching last page
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s                   -- column value pushdown
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber;

-- naive page fetch query
SELECT ROW(c_custkey, o_orderkey, l_orderkey, l_linenumber), *
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
AND c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate < date '1996-01-01'
AND l_shipdate > date '1996-01-01'
ORDER BY c_custkey, o_orderkey, l_orderkey, l_linenumber;

-- ---- 3. group table ----
-- query id: q3_group
-- milestone query
WITH tmp(seq, iid, c_custkey, o_orderkey, l_orderkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, o_orderdate, o_shippriority) - 1,
            ROW(l_orderkey, o_orderdate, o_shippriority), 
            ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber,
            ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate
    FROM customer, orders, lineitem
    WHERE c_mktsegment = 'HOUSEHOLD'
    AND c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND o_orderdate < date '1996-01-01'
    AND l_shipdate > date '1996-01-01'
    GROUP BY l_orderkey, o_orderdate, o_shippriority
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count, 
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[1])] o_orderkey, 
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber,
       ARRAY[MIN(o_orderdate[1]), MAX(o_orderdate[2])] o_orderdate
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, o_orderdate, o_shippriority), 
       l_orderkey, 
       o_orderdate, 
       o_shippriority
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
AND c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate < date '1996-01-01'
AND l_shipdate > date '1996-01-01'
AND ROW(l_orderkey, o_orderdate, o_shippriority) >= %(iid,min)s      -- id pushdown
AND ROW(l_orderkey, o_orderdate, o_shippriority) < %(iid,max)s       -- omit if last page
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s                   -- column value pushdown
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
GROUP BY l_orderkey, o_orderdate, o_shippriority
ORDER BY l_orderkey, o_orderdate, o_shippriority;

-- naive page fetch query
SELECT ROW(l_orderkey, o_orderdate, o_shippriority), 
       l_orderkey, 
       o_orderdate, 
       o_shippriority
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
AND c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate < date '1996-01-01'
AND l_shipdate > date '1996-01-01'
GROUP BY l_orderkey, o_orderdate, o_shippriority
ORDER BY l_orderkey, o_orderdate, o_shippriority;

-- ---- 4. output table (tbd, need bloom filter) ----
-- query id: q3_output
-- milestone query
WITH tmp(seq, iid, c_custkey, o_orderkey, l_orderkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_orderdate, l_orderkey, o_shippriority) - 1,
            ROW(o_orderdate, l_orderkey, o_shippriority),
            ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber,
            ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate
    FROM customer, orders, lineitem
    WHERE c_mktsegment = 'HOUSEHOLD'
    AND c_custkey = o_custkey
    AND l_orderkey = o_orderkey
    AND o_orderdate < date '1996-01-01'
    AND l_shipdate > date '1996-01-01'
    GROUP BY l_orderkey, o_orderdate, o_shippriority
    ORDER BY l_orderkey, o_orderdate, o_shippriority
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[1])] o_orderkey, 
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber,
       ARRAY[MIN(o_orderdate[1]), MAX(o_orderdate[2])] o_orderdate
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(o_orderdate, l_orderkey, o_shippriority),
        l_orderkey,
	    SUM(l_extendedprice * (1 - l_discount)) AS revenue,
	    o_orderdate,
	    o_shippriority,
        MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey), ROW(l_linenumber)))
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
AND c_custkey = o_custkey
AND l_orderkey = o_orderkey
AND o_orderdate < date '1996-01-01'
AND l_shipdate > date '1996-01-01'
AND ROW(o_orderdate, l_orderkey, o_shippriority) >= %(iid,min)s
AND ROW(o_orderdate, l_orderkey, o_shippriority) < %(iid,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s                   -- column value pushdown
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
GROUP BY l_orderkey, o_orderdate, o_shippriority
ORDER BY o_orderdate, revenue;

-- naive page fetch query
SELECT ROW(o_orderdate, l_orderkey, o_shippriority),
        l_orderkey,
	    SUM(l_extendedprice * (1 - l_discount)) AS revenue,
	    o_orderdate,
	    o_shippriority,
        MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey), ROW(l_orderkey), ROW(l_linenumber)))
FROM customer, orders, lineitem
WHERE c_mktsegment = 'HOUSEHOLD'
	AND c_custkey = o_custkey
	AND l_orderkey = o_orderkey
	AND o_orderdate < date '1996-01-01'
	AND l_shipdate > date '1996-01-01'
GROUP BY l_orderkey, o_orderdate, o_shippriority
ORDER BY o_orderdate, revenue;
