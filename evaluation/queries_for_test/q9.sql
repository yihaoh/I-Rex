-- q9

-- original query
/*
select
    nation,
    o_year,
    sum(amount) as sum_profit
from
    (
        select
            n_name as nation,
            extract(year from o_orderdate) as o_year,
            l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
        from
            part,
            supplier,
            lineitem,
            partsupp,
            orders,
            nation
        where
            s_suppkey = l_suppkey
            and ps_suppkey = l_suppkey
            and ps_partkey = l_partkey
            and p_partkey = l_partkey
            and o_orderkey = l_orderkey
            and s_nationkey = n_nationkey
            and p_name like '%%cyan%%'
    ) as profit
group by
    nation,
    o_year
order by
    nation,
    o_year desc
*/


-- query id: profit_output
-- milestone query
WITH tmp(seq, iid, p_partkey, s_suppkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, o_orderdate, n_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey) - 1,
           ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey),
           p_partkey, s_suppkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, o_orderdate, n_nationkey
    FROM part, supplier, lineitem, partsupp, orders, nation
    WHERE s_suppkey = l_suppkey
    AND ps_suppkey = l_suppkey
    AND ps_partkey = l_partkey
    AND p_partkey = l_partkey
    AND o_orderkey = l_orderkey
    AND s_nationkey = n_nationkey
    AND p_name like '%%cyan%%'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
       ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
       ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate, 
       ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey 
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey),
        n_name as nation, extract(year from o_orderdate) as o_year, l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
FROM part, supplier, lineitem, partsupp, orders, nation
WHERE s_suppkey = l_suppkey
AND ps_suppkey = l_suppkey
AND ps_partkey = l_partkey
AND p_partkey = l_partkey
AND o_orderkey = l_orderkey
AND s_nationkey = n_nationkey
AND p_name like '%%cyan%%'
AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey) >= %(iid,min)s 
AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey) < %(iid,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
-- AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
ORDER BY nation, o_year, amount;

-- naive page fetch query
SELECT ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey),
        n_name as nation, extract(year from o_orderdate) as o_year, l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
FROM part, supplier, lineitem, partsupp, orders, nation
WHERE s_suppkey = l_suppkey
AND ps_suppkey = l_suppkey
AND ps_partkey = l_partkey
AND p_partkey = l_partkey
AND o_orderkey = l_orderkey
AND s_nationkey = n_nationkey
AND p_name like '%%cyan%%'
ORDER BY nation, o_year, amount;


-- query id: q9_output
-- milestone query
WITH tmp(seq, iid, profit_output_iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY nation, o_year) - 1,
           ROW(nation, o_year),
           ARRAY[MIN_IID(ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey)), 
                 MAX_IID(ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey))]
    FROM (
        SELECT p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey,
                n_name as nation, extract(year from o_orderdate) as o_year, l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
        FROM part, supplier, lineitem, partsupp, orders, nation
        WHERE s_suppkey = l_suppkey
        AND ps_suppkey = l_suppkey
        AND ps_partkey = l_partkey
        AND p_partkey = l_partkey
        AND o_orderkey = l_orderkey
        AND s_nationkey = n_nationkey
        AND p_name like '%%cyan%%'
    ) as profit
    GROUP BY nation, o_year
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(profit_output_iid[1]), MAX_IID(profit_output_iid[2])] profit_output_iid
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(nation, o_year),
        nation, o_year, sum(amount) as sum_profit,
        MIN_IID(ROW(ROW(nation, o_year)))
FROM (
    SELECT n_name as nation, extract(year from o_orderdate) as o_year, l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
    FROM part, supplier, lineitem, partsupp, orders, nation
    WHERE s_suppkey = l_suppkey
    AND ps_suppkey = l_suppkey
    AND ps_partkey = l_partkey
    AND p_partkey = l_partkey
    AND o_orderkey = l_orderkey
    AND s_nationkey = n_nationkey
    AND p_name like '%%cyan%%'
    AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey) >= %(profit_output,iid,min)s 
    AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, ps_partkey, ps_suppkey, o_orderkey, n_nationkey) < %(profit_output,iid,max)s
    AND p_partkey BETWEEN %(profit_output,p_partkey,min)s AND %(profit_output,p_partkey,max)s
    AND s_suppkey BETWEEN %(profit_output,s_suppkey,min)s AND %(profit_output,s_suppkey,max)s
    AND l_orderkey BETWEEN %(profit_output,l_orderkey,min)s AND %(profit_output,l_orderkey,max)s
    AND l_partkey BETWEEN %(profit_output,l_partkey,min)s AND %(profit_output,l_partkey,max)s
    AND l_suppkey BETWEEN %(profit_output,l_suppkey,min)s AND %(profit_output,l_suppkey,max)s
    AND l_linenumber BETWEEN %(profit_output,l_linenumber,min)s AND %(profit_output,l_linenumber,max)s
    AND ps_partkey BETWEEN %(profit_output,ps_partkey,min)s AND %(profit_output,ps_partkey,max)s
    AND ps_suppkey BETWEEN %(profit_output,ps_suppkey,min)s AND %(profit_output,ps_suppkey,max)s
    AND o_orderkey BETWEEN %(profit_output,o_orderkey,min)s AND %(profit_output,o_orderkey,max)s
    AND o_orderdate BETWEEN %(profit_output,o_orderdate,min)s AND %(profit_output,o_orderdate,max)s
    -- AND n_nationkey BETWEEN %(profit_output,n_nationkey,min)s AND %(profit_output,n_nationkey,max)s
) as profit
WHERE ROW(nation, o_year) >= %(iid,min)s
AND ROW(nation, o_year) >= %(iid,max)s
GROUP BY nation, o_year
ORDER BY nation, o_year;

-- naive page fetch query
SELECT ROW(nation, o_year),
        nation, o_year, sum(amount) as sum_profit,
        MIN_IID(ROW(ROW(nation, o_year)))
FROM (
    SELECT n_name as nation, extract(year from o_orderdate) as o_year, l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
    FROM part, supplier, lineitem, partsupp, orders, nation
    WHERE s_suppkey = l_suppkey
    AND ps_suppkey = l_suppkey
    AND ps_partkey = l_partkey
    AND p_partkey = l_partkey
    AND o_orderkey = l_orderkey
    AND s_nationkey = n_nationkey
    AND p_name like '%%cyan%%'
) as profit
GROUP BY nation, o_year
ORDER BY nation, o_year;
