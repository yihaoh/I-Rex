-- Q15

-- original query
/*
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
)
SELECT s_suppkey, s_name, s_address, s_phone, total_revenue
FROM supplier, revenue
WHERE s_suppkey = supplier_no
AND total_revenue < (select max(total_revenue) from revenue) / 2
ORDER BY s_suppkey;
*/


-- cached query
/*
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
)
select max(total_revenue) from revenue;
*/


-- ====================== WITH context (revenue table) ========================

-- ---- join&filter table, context id 1 ----
-- query id: rev_join
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber) - 1,
           ROW(l_orderkey, l_linenumber),
           l_orderkey, l_partkey, l_suppkey, l_linenumber
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber) iid, *
FROM lineitem
WHERE l_shipdate >= date '1992-01-02' 
AND l_shipdate < date '1992-04-01' + interval '3' month
AND ROW(l_orderkey, l_linenumber) >= %(iid,min)s      -- id pushdown
AND ROW(l_orderkey, l_linenumber) < %(iid,max)s      -- omit if fetching last page
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s                   -- column value pushdown
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
ORDER BY l_orderkey, l_linenumber;

-- page fetch naive query 
SELECT ROW(l_orderkey, l_linenumber) iid, * 
FROM lineitem
WHERE l_shipdate >= date '1992-01-02' 
AND l_shipdate < date '1992-04-01' + interval '3' month
ORDER BY l_orderkey, l_linenumber;

-- ---- group table ----
-- milestones and page fetching are the same as 4. output table 
-- but removing the SUM in SELECT when fetching a page

-- ---- output table, context id 2 ----
-- query id: rev_output
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_suppkey) - 1,
           ROW(l_suppkey),
           ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
           ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02'
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
       ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_suppkey) iid,
       l_suppkey, 
       SUM(l_extendedprice * (1 - l_discount)),
       MIN_IID(ROW(ROW(l_orderkey, l_linenumber))) prov  -- provenance
FROM lineitem
WHERE l_shipdate >= date '1992-01-02'
AND l_shipdate < date '1992-04-01' + interval '3' month
AND ROW(l_suppkey) >= %(iid,min)s      -- id pushdown
AND ROW(l_suppkey) < %(iid,max)s       -- omit if last page
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s    -- sargable pushdown here
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY l_suppkey
ORDER BY l_suppkey;  -- we can do this because there is no user-defined order

-- page fetch naive query 
SELECT ROW(l_suppkey) iid,
       l_suppkey, 
       SUM(l_extendedprice * (1 - l_discount)),
       MIN_IID(ROW(ROW(l_orderkey, l_linenumber))) prov  -- provenance
FROM lineitem
WHERE l_shipdate >= date '1992-01-02'
AND l_shipdate < date '1992-04-01' + interval '3' month
GROUP BY l_suppkey
ORDER BY l_suppkey;


-- ====================== outer SELECT context =========================
-- -- (2) revenue table
-- milestone query and page fetch query are the same as Revenue output table

-- ---- join&filter table, context id 4 ----
-- query id: q15_join_filter
-- milestone query
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
),
tmp(seq, iid, rev_output_iid, s_suppkey, s_nationkey, supplier_no) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY supplier.s_suppkey, revenue.supplier_no) - 1,
           ROW(supplier.s_suppkey, revenue.supplier_no),
           ROW(revenue.supplier_no),
           s_suppkey, s_nationkey, supplier_no
    FROM supplier, revenue
    WHERE s_suppkey = supplier_no
    AND total_revenue < (select max(total_revenue) from revenue) / 2  -- independent scalar query
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(rev_output_iid), MAX_IID(rev_output_iid)] rev_output_iid, 
       ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, ARRAY[MIN(supplier_no), MAX(supplier_no)] supplier_no
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;


-- page fetch query
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    AND ROW(l_suppkey) >= %(rev_output,iid,min)s AND ROW(l_suppkey) < %(rev_output,iid,max)s    -- pushdown by revenue table id
    AND l_orderkey BETWEEN %(rev_output,l_orderkey,min)s AND %(rev_output,l_orderkey,max)s          -- sargable pushdown starts here
    AND l_partkey BETWEEN %(rev_output,l_partkey,min)s AND %(rev_output,l_partkey,max)s
    AND l_suppkey BETWEEN %(rev_output,l_suppkey,min)s AND %(rev_output,l_suppkey,max)s
    AND l_linenumber BETWEEN %(rev_output,l_linenumber,min)s AND %(rev_output,l_linenumber,max)s
    GROUP BY l_suppkey
)
SELECT ROW(supplier.ctid, revenue.supplier_no) iid, *
FROM supplier, revenue
WHERE s_suppkey = supplier_no
AND total_revenue < %(cached_scalar)s / 2                       -- cached result
AND ROW(supplier.s_suppkey, revenue.supplier_no) >= %(iid,min)s
AND ROW(supplier.s_suppkey, revenue.supplier_no) < %(iid,max)s                  -- id pushdown 
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s                   -- sargable pushdown starts here, can include only s_suppkey, s_nationkey, supplier_no as they are key/index columns
AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s 
AND supplier_no BETWEEN %(supplier_no,min)s AND %(supplier_no,max)s
ORDER BY supplier.ctid, revenue.supplier_no;

-- naive page fetch query
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
)
SELECT ROW(supplier.ctid, revenue.supplier_no) iid, *
FROM supplier, revenue
WHERE s_suppkey = supplier_no
AND total_revenue < (SELECT MAX(total_revenue) FROM revenue) / 2
ORDER BY supplier.ctid, revenue.supplier_no;

-- ---- output table, context id 5 ----
-- very similar to join&filter table, with minor modification
-- query id: q15_output
-- milestone query (same)
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
),
tmp(seq, iid, rev_output_iid, s_suppkey, s_nationkey, supplier_no) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY supplier.s_suppkey, revenue.supplier_no) - 1,
           ROW(supplier.s_suppkey, revenue.supplier_no),
           ROW(revenue.supplier_no),
           s_suppkey, s_nationkey, supplier_no
    FROM supplier, revenue
    WHERE s_suppkey = supplier_no
    AND total_revenue < (select max(total_revenue) from revenue) / 2  -- independent scalar query
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(rev_output_iid), MAX_IID(rev_output_iid)] rev_output_iid, 
       ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, ARRAY[MIN(supplier_no), MAX(supplier_no)] supplier_no
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query (SELECT clause differs)
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    AND ROW(l_suppkey) >= %(rev_output,iid,min)s AND ROW(l_suppkey) < %(rev_output,iid,max)s    -- pushdown by revenue table id
    AND l_orderkey BETWEEN %(rev_output,l_orderkey,min)s AND %(rev_output,l_orderkey,max)s          -- sargable pushdown starts here
    AND l_partkey BETWEEN %(rev_output,l_partkey,min)s AND %(rev_output,l_partkey,max)s
    AND l_suppkey BETWEEN %(rev_output,l_suppkey,min)s AND %(rev_output,l_partkey,max)s
    AND l_linenumber BETWEEN %(rev_output,l_linenumber,min)s AND %(rev_output,l_linenumber,max)s
    GROUP BY l_suppkey
)
SELECT ROW(supplier.s_suppkey, revenue.supplier_no) iid, 
       s_suppkey,
       s_name,
       s_address,
       s_phone,
       total_revenue
FROM supplier, revenue
WHERE s_suppkey = supplier_no
AND total_revenue < %(cached_scalar)s / 2  -- independent scalar query
AND ROW(supplier.s_suppkey, revenue.supplier_no) >= %(iid,min)s
AND ROW(supplier.s_suppkey, revenue.supplier_no) < %(iid,max)s                  -- id pushdown 
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s                   -- sargable pushdown starts here, can include only s_suppkey, s_nationkey, supplier_no as they are key/index columns
AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s 
AND supplier_no BETWEEN %(supplier_no,min)s AND %(supplier_no,max)s
ORDER BY s_suppkey;  -- user-defined order exist, cannot simply order by 1 (id) in case of descending order!

-- naive page fetch query
WITH revenue(supplier_no, total_revenue) AS (
    SELECT l_suppkey, SUM(l_extendedprice * (1 - l_discount))
    FROM lineitem
    WHERE l_shipdate >= date '1992-01-02' 
    AND l_shipdate < date '1992-04-01' + interval '3' month
    GROUP BY l_suppkey
)
SELECT ROW(supplier.s_suppkey, revenue.supplier_no) iid, 
       s_suppkey,
       s_name,
       s_address,
       s_phone,
       total_revenue
FROM supplier, revenue
WHERE s_suppkey = supplier_no
AND total_revenue < (select max(total_revenue) from revenue) / 2  -- independent scalar query
ORDER BY s_suppkey;  -- user-defined order exist, cannot simply order by 1 (id) in case of descending order!

