-- Q1

-- original query
/*
SELECT
       l_returnflag,
       l_linestatus,
       SUM(l_quantity) as sum_qty,
       SUM(l_extendedprice) as sum_base_price,
       SUM(l_extendedprice * (1 - l_discount)) as sum_disc_price,
       SUM(l_extendedprice * (1 - l_discount) * (1 + l_tax)) as sum_charge,
       AVG(l_quantity) as avg_qty,
       AVG(l_extendedprice) as avg_price,
       AVG(l_discount) as avg_disc,
       COUNT(*) as count_order
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
GROUP BY l_returnflag, l_linestatus
ORDER BY l_returnflag, l_linestatus
LIMIT 1;
*/


-- ====================== SELECT context ========================
-- ========= 1. input tables =========
-- omit since it is input table

-- ========= 2. join&filter table =========
-- query id: q1_join_filter
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber) - 1,
        ROW(l_orderkey, l_linenumber), 
        l_orderkey, l_partkey, l_suppkey, l_linenumber
    FROM lineitem
    WHERE l_shipdate <= date '1998-12-01' - interval '730' day
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber), *
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
AND ROW(l_orderkey, l_linenumber) >= %(iid,min)s      -- id pushdown
AND ROW(l_orderkey, l_linenumber) < %(iid,max)s       -- omit if fetching last page
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s                   -- column value pushdown
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
ORDER BY l_orderkey, l_linenumber;

-- naive page fetch query
SELECT ROW(l_orderkey, l_linenumber), *
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
ORDER BY l_orderkey, l_linenumber;

-- ---- 3. group table ----
-- query id: q1_group
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_returnflag, l_linestatus) - 1,
            ROW('''' || l_returnflag || '''', '''' || l_linestatus || ''''),     -- have to use a trick here to work with testing script, oh well...
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
            ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_orderkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM lineitem
    WHERE l_shipdate <= date '1998-12-01' - interval '730' day
    GROUP BY l_returnflag, l_linestatus
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count, 
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
       ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_returnflag, l_linestatus), 
       l_returnflag, 
       l_linestatus
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
AND ROW(l_returnflag, l_linestatus) >= %(iid,min)s      -- id pushdown
AND ROW(l_returnflag, l_linestatus) < %(iid,max)s       -- omit if last page
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s    -- sargable pushdown here
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY l_returnflag, l_linestatus
ORDER BY l_returnflag, l_linestatus;

-- naive page fetch query
SELECT ROW(l_returnflag, l_linestatus), 
       l_returnflag, 
       l_linestatus
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
GROUP BY l_returnflag, l_linestatus
ORDER BY l_returnflag, l_linestatus;

-- ---- 4. output table ----
-- query id: q1_output
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_returnflag, l_linestatus) - 1,
            ROW('''' || l_returnflag || '''', '''' || l_linestatus || ''''),  -- have to use a trick here to work with testing script, oh well...
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
            ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM lineitem
    WHERE l_shipdate <= date '1998-12-01' - interval '730' day
    GROUP BY l_returnflag, l_linestatus
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
       ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_returnflag, l_linestatus), 
        l_returnflag, 
        l_linestatus,
        SUM(l_quantity) as sum_qty,
        SUM(l_extendedprice) as sum_base_price,
        SUM(l_extendedprice * (1 - l_discount)) as sum_disc_price,
        SUM(l_extendedprice * (1 - l_discount) * (1 + l_tax)) as sum_charge,
        AVG(l_quantity) as avg_qty,
        AVG(l_extendedprice) as avg_price,
        AVG(l_discount) as avg_disc,
        MIN_IID(ROW(ROW(l_orderkey, l_linenumber)))  -- provenance
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
AND ROW(l_returnflag, l_linestatus) >= %(iid,min)s      -- id pushdown
AND ROW(l_returnflag, l_linestatus) < %(iid,max)s       -- omit if last page
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s    -- sargable pushdown here
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY l_returnflag, l_linestatus
ORDER BY l_returnflag, l_linestatus;

-- naive page fetch
SELECT ROW(l_returnflag, l_linestatus), 
        l_returnflag, 
        l_linestatus,
              SUM(l_quantity) as sum_qty,
        SUM(l_extendedprice) as sum_base_price,
        SUM(l_extendedprice * (1 - l_discount)) as sum_disc_price,
        SUM(l_extendedprice * (1 - l_discount) * (1 + l_tax)) as sum_charge,
        AVG(l_quantity) as avg_qty,
        AVG(l_extendedprice) as avg_price,
        AVG(l_discount) as avg_disc,
        MIN_IID(ROW(ROW(l_orderkey, l_linenumber)))
FROM lineitem
WHERE l_shipdate <= date '1998-12-01' - interval '730' day
GROUP BY l_returnflag, l_linestatus
ORDER BY l_returnflag, l_linestatus;
