-- q17

-- original query
/*
select
    sum(l_extendedprice) / 7.0 as avg_yearly
from
    lineitem,
    part,
    (SELECT l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity FROM lineitem GROUP BY l_partkey) part_agg
where
    p_partkey = l_partkey
    and agg_partkey = l_partkey
    and p_brand = 'Brand#35  '
    and p_container = 'LG JAR   '
    and l_quantity < avg_quantity

*/


-- query id: part_agg_final
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_partkey) - 1,
           ROW(l_partkey),
           ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
           ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    -- l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity 
    FROM lineitem 
    GROUP BY l_partkey
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
       ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_partkey), 
       l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity 
FROM lineitem 
WHERE ROW(l_partkey) >= %(iid,min)s
AND ROW(l_partkey) < %(iid,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY l_partkey
ORDER BY l_partkey;

-- naive page fetch query
SELECT l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity 
FROM lineitem 
GROUP BY l_partkey
ORDER BY l_partkey;



-- query id: q17_join_filter
-- milestone query
WITH tmp(seq, iid, part_agg_final_iid, l_orderkey, l_partkey, l_suppkey, l_linenumber, p_partkey, agg_partkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber, p_partkey, agg_partkey) - 1,
           ROW(l_orderkey, l_linenumber, p_partkey, agg_partkey),
           ROW(agg_partkey),
           l_orderkey, l_partkey, l_suppkey, l_linenumber, p_partkey, agg_partkey
    FROM lineitem,
         part,
         (SELECT l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity FROM lineitem GROUP BY l_partkey) part_agg
    WHERE p_partkey = l_partkey
    AND agg_partkey = l_partkey
    AND p_brand = 'Brand#35  '
    -- AND p_container = 'LG JAR   '
    AND l_quantity < avg_quantity
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(part_agg_final_iid), MAX_IID(part_agg_final_iid)] part_agg_final_iid,
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber,
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey, ARRAY[MIN(agg_partkey), MAX(agg_partkey)] agg_partkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber, p_partkey, agg_partkey),
       *
FROM lineitem,
     part,
     (
        SELECT l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity 
        FROM lineitem 
        WHERE ROW(l_partkey) >= %(part_agg_final,iid,min)s
        AND ROW(l_partkey) < %(part_agg_final,iid,max)s
        AND l_orderkey BETWEEN %(part_agg_final,l_orderkey,min)s AND %(part_agg_final,l_orderkey,max)s
        AND l_partkey BETWEEN %(part_agg_final,l_partkey,min)s AND %(part_agg_final,l_partkey,max)s
        AND l_suppkey BETWEEN %(part_agg_final,l_suppkey,min)s AND %(part_agg_final,l_suppkey,max)s
        AND l_linenumber BETWEEN %(part_agg_final,l_linenumber,min)s AND %(part_agg_final,l_linenumber,max)s
        GROUP BY l_partkey
     ) part_agg
WHERE p_partkey = l_partkey
AND agg_partkey = l_partkey
AND p_brand = 'Brand#35  '
-- AND p_container = 'LG JAR   '
AND l_quantity < avg_quantity
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
AND agg_partkey BETWEEN %(agg_partkey,min)s AND %(agg_partkey,max)s
ORDER BY l_orderkey, l_linenumber, p_partkey, agg_partkey;

-- naive page fetch query
SELECT ROW(l_orderkey, l_linenumber, p_partkey, agg_partkey),
       *
FROM lineitem,
     part,
     (SELECT l_partkey AS agg_partkey, 0.2 * avg(l_quantity) AS avg_quantity FROM lineitem GROUP BY l_partkey) part_agg
WHERE p_partkey = l_partkey
AND agg_partkey = l_partkey
AND p_brand = 'Brand#35  '
-- AND p_container = 'LG JAR   '
AND l_quantity < avg_quantity
ORDER BY l_orderkey, l_linenumber, p_partkey, agg_partkey;
