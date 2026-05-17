-- Q21

-- original query
/*
SELECT s_name, count(*) as numwait
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
	AND o_orderkey = l1.l_orderkey
	AND o_orderstatus = 'F'
	AND l1.l_receiptdate > l1.l_commitdate
	AND EXISTS (SELECT *    
                FROM lineitem l2    
                WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
	AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate)
	AND s_nationkey = n_nationkey
	AND n_name = 'CHINA'
GROUP BY
	s_name
ORDER BY 
	numwait desc,
	s_name
LIMIT 100;
*/


-- ====================== SELECT context ========================

-- ========= 2. join&filter table =========
-- query id: q21_join_filter
-- milestone query
WITH tmp(seq, iid, 
        s_suppkey, s_nationkey,
        l_orderkey, l_linenumber, l_partkey, l_suppkey,
        o_orderkey, o_custkey,
        n_nationkey, n_regionkey
        ) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey) - 1,
            ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey),
            s_suppkey, s_nationkey,
            l_orderkey, l_linenumber, l_partkey, l_suppkey,
            o_orderkey, o_custkey,
            n_nationkey, n_regionkey
    FROM supplier, lineitem l1, orders, nation
    WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND EXISTS (SELECT *
                FROM lineitem l2    
                WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
	    AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
        ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
        ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
        ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
        ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
        ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
        BLMFL(numeric_send(l_orderkey), numeric_send(l_suppkey)) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey), *
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(l_suppkey), numeric_send(o_orderkey))
        AND EXISTS (SELECT *
                    FROM lineitem l2    
                    WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey
                    AND l2.l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s)
	    AND NOT EXISTS (SELECT *
                        FROM lineitem l3
                        WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate
                        AND l3.l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
        AND ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey) >= %(iid,min)s      -- id pushdown
        AND ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey) < %(iid,max)s      -- omit if fetching last page
        AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s                   -- column value pushdown
        AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
        AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
        AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
        AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
        AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
        AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
        AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
        AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
        AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
ORDER BY s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey;

-- naive page fetch query
SELECT ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey), *
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND EXISTS (SELECT *
                    FROM lineitem l2    
                    WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
	    AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
ORDER BY s_suppkey, l_orderkey, l_linenumber, o_orderkey, n_nationkey;

-- ---- 3. group table ----
-- query id: q21_group
-- milestone query
WITH tmp(seq, iid, 
        s_suppkey, s_nationkey,
        l_orderkey, l_linenumber, l_partkey, l_suppkey, 
        o_orderkey, o_custkey,
        n_nationkey, n_regionkey, 
        blmfl_filter
    ) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_name) - 1,
            ROW(s_name),
            ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
            ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
            ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
            ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
            BLMFL(numeric_send(l_orderkey), numeric_send(l_suppkey)) blmfl_filter
    FROM supplier, lineitem l1, orders, nation
    WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND EXISTS (SELECT *
                FROM lineitem l2    
                WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
	    AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
    GROUP BY s_name 
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count, 
        ARRAY[MIN(s_suppkey[1]), MAX(s_suppkey[2])] s_suppkey, ARRAY[MIN(s_nationkey[1]), MAX(s_nationkey[2])] s_nationkey, 
        ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber, 
        ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, 
        ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(o_custkey[1]), MAX(o_custkey[2])] o_custkey, 
        ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey, ARRAY[MIN(n_regionkey[1]), MAX(n_regionkey[2])] n_regionkey, 
        BLMFL_AGG(blmfl_filter) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(s_name), s_name
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
        AND o_orderkey = l1.l_orderkey
        AND o_orderstatus = 'F'
        AND l1.l_receiptdate > l1.l_commitdate
        AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(l_orderkey), numeric_send(l_suppkey))
        AND EXISTS (SELECT *
                    FROM lineitem l2    
                    WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey
                    AND l2.l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
                )
        AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
                        AND l3.l_receiptdate > l3.l_commitdate
                        AND l3.l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
                    ) 
        AND s_nationkey = n_nationkey
        AND n_name = 'CHINA'
        AND ROW(s_name) >= %(iid,min)s      -- id pushdown
        AND ROW(s_name) < %(iid,max)s      -- omit if fetching last page
        AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s                   -- column value pushdown
        AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
        AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
        AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
        AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
        AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
        AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
        AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
        AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
        AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
GROUP BY s_name
ORDER BY s_name;

-- naive page fetch query
SELECT ROW(s_name), s_name
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
        AND o_orderkey = l1.l_orderkey
        AND o_orderstatus = 'F'
        AND l1.l_receiptdate > l1.l_commitdate
        AND EXISTS (SELECT *
                    FROM lineitem l2    
                    WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
        AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
                        AND l3.l_receiptdate > l3.l_commitdate) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
GROUP BY s_name
ORDER BY s_name;

-- ---- 4. output table ----
-- query id: q21_output
-- milestone query
WITH tmp(seq, iid, 
        s_suppkey, s_nationkey,
        l_orderkey, l_linenumber, l_partkey, l_suppkey, 
        o_orderkey, o_custkey,
        n_nationkey, n_regionkey 
    ) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_name) - 1,
            ROW(s_name),
            ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
            ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
            ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
            ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
            BLMFL(numeric_send(l_orderkey), numeric_send(l_suppkey)) blmfl_filter
    FROM supplier, lineitem l1, orders, nation
    WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND EXISTS (SELECT *
                FROM lineitem l2    
                WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
	    AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
    GROUP BY s_name     -- Note: The added group by statement appears here
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
        ARRAY[MIN(s_suppkey[1]), MAX(s_suppkey[2])] s_suppkey, ARRAY[MIN(s_nationkey[1]), MAX(s_nationkey[2])] s_nationkey, 
        ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber, 
        ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, 
        ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(o_custkey[1]), MAX(o_custkey[2])] o_custkey, 
        ARRAY[MIN(n_nationkey[1]), MAX(n_nationkey[2])] n_nationkey, ARRAY[MIN(n_regionkey[1]), MAX(n_regionkey[2])] n_regionkey, 
        BLMFL_AGG(blmfl_filter) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;


-- page fetch query
SELECT ROW(s_name) iid,
        s_name, 
        COUNT(*) as numwait,
        MIN_IID(ROW(ROW(s_suppkey), ROW(l1.l_orderkey, l1.l_linenumber), ROW(o_orderkey), ROW(n_nationkey)))  -- provenance
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(l_orderkey), numeric_send(l_suppkey))
        AND EXISTS (SELECT *
                    FROM lineitem l2    
                    WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey
                    AND l2.l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s)
	    AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate
                        AND l3.l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
        AND ROW(s_name) >= %(iid,min)s      -- id pushdown
        AND ROW(s_name) < %(iid,max)s       -- omit if fetching last page
        AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s                   -- column value pushdown
        AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
        AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
        AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
        AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
        AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
        AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
        AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
        AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
        AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
GROUP BY s_name
ORDER BY s_name, numwait;

-- naive page fetch
SELECT ROW(s_name), s_name, count(*) as numwait
FROM supplier, lineitem l1, orders, nation
WHERE s_suppkey = l1.l_suppkey
	    AND o_orderkey = l1.l_orderkey
	    AND o_orderstatus = 'F'
	    AND l1.l_receiptdate > l1.l_commitdate
        AND EXISTS (SELECT *
                    FROM lineitem l2    
                    WHERE l2.l_orderkey = l1.l_orderkey 
                    AND l2.l_suppkey <> l1.l_suppkey)
	    AND NOT EXISTS (SELECT *
                    FROM lineitem l3
                    WHERE l3.l_orderkey = l1.l_orderkey
                        AND l3.l_suppkey <> l1.l_suppkey
			            AND l3.l_receiptdate > l3.l_commitdate) 
        AND s_nationkey = n_nationkey
	    AND n_name = 'CHINA'
GROUP BY s_name
ORDER BY s_name, numwait;
