-- q base, testing all base table data retrieval


-- -- (1) customer
-- query id: customer
-- milestone query
WITH tmp(seq, iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey) - 1,
           ROW(c_custkey)
    FROM customer
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey), *
FROM customer
WHERE ROW(c_custkey) >= %(iid,min)s  -- pushdown
AND ROW(c_custkey) < %(iid,max)s    -- omit if fetching last page
ORDER BY c_custkey;

-- naive page fetch query
SELECT ROW(c_custkey), *
FROM customer
ORDER BY c_custkey;

-- -- (2) lineitem
-- query id: lineitem
WITH tmp(seq, iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber) - 1,
           ROW(l_orderkey, l_linenumber)
    FROM lineitem
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber), *
FROM lineitem
WHERE ROW(l_orderkey, l_linenumber) >= %(iid,min)s  -- pushdown
AND ROW(l_orderkey, l_linenumber) < %(iid,max)s     -- omit this predicate if fetching last page
ORDER BY l_orderkey, l_linenumber;

-- page fetch naive query 
SELECT ROW(l_orderkey, l_linenumber), * FROM lineitem ORDER BY l_orderkey, l_linenumber;


-- -- (3) nation
-- omitted due to the small size of table

-- -- (4) orders
-- query id: orders
-- milestone query
WITH tmp(seq, iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_orderkey) - 1,
           ROW(o_orderkey)
    FROM orders
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(o_orderkey), *
FROM orders
WHERE ROW(o_orderkey) >= %(iid,min)s  -- pushdown
AND ROW(o_orderkey) < %(iid,max)s     -- omit this predicate if fetching last page
ORDER BY o_orderkey;

-- page fetch naive query 
SELECT ROW(o_orderkey), * FROM orders ORDER BY o_orderkey;



-- -- (5) part
-- query id: part
-- milestone query
WITH tmp(seq, iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY p_partkey) - 1,
           ROW(p_partkey)
    FROM part
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(p_partkey), *
FROM part
WHERE ROW(p_partkey) >= %(iid,min)s  -- pushdown
AND ROW(p_partkey) < %(iid,max)s     -- omit this predicate if fetching last page
ORDER BY p_partkey;

-- page fetch naive query 
SELECT ROW(p_partkey), * FROM part ORDER BY p_partkey;


-- -- (6) partsupp
-- query id: partsupp
-- milestone query
WITH tmp(seq, iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY ps_partkey, ps_suppkey) - 1,
           ROW(ps_partkey, ps_suppkey)
    FROM partsupp
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(ps_partkey, ps_suppkey), *
FROM partsupp
WHERE ROW(ps_partkey, ps_suppkey) >= %(iid,min)s  -- pushdown
AND ROW(ps_partkey, ps_suppkey) < %(iid,max)s     -- omit this predicate if fetching last page
ORDER BY ps_partkey, ps_suppkey;

-- page fetch naive query 
SELECT ROW(ps_partkey, ps_suppkey), * FROM partsupp ORDER BY ps_partkey, ps_suppkey;


-- -- (7) region
-- omitted due to its small size

-- -- (8) supplier
-- query id: supplier
-- milestone query
WITH tmp(seq, iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_suppkey) - 1,
           ROW(s_suppkey)
    FROM supplier
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(s_suppkey), *
FROM supplier
WHERE ROW(s_suppkey) >= %(iid,min)s  -- pushdown
AND ROW(s_suppkey) < %(iid,max)s     -- omit this predicate if fetching last page
ORDER BY s_suppkey;

-- page fetch naive query 
SELECT ROW(s_suppkey), * FROM supplier ORDER BY s_suppkey;

