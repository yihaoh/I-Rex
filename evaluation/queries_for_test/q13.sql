-- q13

-- original query
/*
select
    c_count,
    count(*) as custdist
from
    (
        select
            c_custkey,
            count(o_orderkey)
        from
            customer left outer join orders on
                c_custkey = o_custkey
                and o_comment not like '%%hello%%'
        group by
            c_custkey
    ) as c_orders (c_custkey, c_count)
group by
    c_count
order by
    custdist desc,
    c_count desc
*/


-- query id: c_orders_join_filter
-- milestone query
WITH tmp(seq, iid, c_custkey, o_orderkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey, o_orderkey) - 1,
           ROW(c_custkey, o_orderkey),
           c_custkey, o_orderkey
    FROM  customer LEFT OUTER JOIN orders 
          ON c_custkey = o_custkey and o_comment not like '%%hello%%'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey, o_orderkey),
       *
FROM customer LEFT OUTER JOIN orders 
     ON c_custkey = o_custkey and o_comment not like '%%hello%%'
WHERE ROW(c_custkey, o_orderkey) >= %(iid,min)s 
AND ROW(c_custkey, o_orderkey) < %(iid,max)s 
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
ORDER BY c_custkey, o_orderkey;

-- naive page fetch query
SELECT ROW(c_custkey, o_orderkey)
FROM customer LEFT OUTER JOIN orders 
     ON c_custkey = o_custkey and o_comment not like '%%hello%%'
ORDER BY c_custkey, o_orderkey;



-- query id: c_orders_output
-- milestone query
WITH tmp(seq, iid, c_custkey, o_orderkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey) - 1,
           ROW(c_custkey),
           ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey
    FROM customer left outer join orders on
            c_custkey = o_custkey
            and o_comment not like '%%hello%%'
    GROUP BY c_custkey
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey, ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey), c_custkey, COUNT(o_orderkey),
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey)))
FROM customer LEFT OUTER JOIN orders ON
        c_custkey = o_custkey
        AND o_comment NOT LIKE '%%hello%%'
WHERE ROW(c_custkey) >= %(iid,min)s 
AND ROW(c_custkey) < %(iid,max)s 
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
GROUP BY c_custkey
ORDER BY c_custkey;

-- naive page fetch query
SELECT ROW(c_custkey), c_custkey, COUNT(o_orderkey),
       MIN_IID(ROW(ROW(c_custkey), ROW(o_orderkey)))
FROM customer LEFT OUTER JOIN orders ON
        c_custkey = o_custkey
        AND o_comment NOT LIKE '%%hello%%'
GROUP BY c_custkey
ORDER BY c_custkey;


-- query id: q13_output
-- milestone query
WITH tmp(seq, iid, c_orders_output_iid, c_count, c_custkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_count, COUNT(*)) - 1,
           ROW(c_count, COUNT(*)),
           ARRAY[MIN_IID(ROW(c_custkey)), MAX_IID(ROW(c_custkey))],
           c_count, ARRAY[MIN(c_custkey), MAX(c_custkey)]
    FROM (
        SELECT c_custkey, count(o_orderkey)
        FROM customer left outer join orders on
                c_custkey = o_custkey
                and o_comment not like '%%hello%%'
        GROUP BY c_custkey
    ) as c_orders(c_custkey, c_count)
    GROUP BY c_count
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(c_orders_output_iid[1]), MAX_IID(c_orders_output_iid[2])] c_orders_output_iid,
       ARRAY[MIN(c_count), MAX(c_count)] c_count, ARRAY[MIN(c_custkey[1]), MAX(c_custkey[2])] c_custkey
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_count, COUNT(*)), c_count, COUNT(*) as custdist,
       MIN_IID(ROW(ROW(c_custkey)))
FROM (
    SELECT c_custkey, count(o_orderkey)
    FROM customer left outer join orders on
            c_custkey = o_custkey
            and o_comment not like '%%hello%%'
    WHERE ROW(c_custkey) >= %(c_orders_output,iid,min)s 
    AND ROW(c_custkey) < %(c_orders_output,iid,max)s 
    AND c_custkey BETWEEN %(c_orders_output,c_custkey,min)s AND %(c_orders_output,c_custkey,max)s
    AND o_orderkey BETWEEN %(c_orders_output,o_orderkey,min)s AND %(c_orders_output,o_orderkey,max)s
    GROUP BY c_custkey
) as c_orders(c_custkey, c_count)
WHERE c_count BETWEEN %(c_count,min)s AND %(c_count,max)s
GROUP BY c_count
ORDER BY c_count, custdist;

-- naive page fetch query
SELECT ROW(c_count, COUNT(*)), c_count, COUNT(*) as custdist,
       MIN_IID(ROW(ROW(c_custkey)))
FROM (
    SELECT c_custkey, count(o_orderkey)
    FROM customer left outer join orders on
            c_custkey = o_custkey
            and o_comment not like '%%hello%%'
    GROUP BY c_custkey
) as c_orders(c_custkey, c_count)
GROUP BY c_count
ORDER BY c_count, custdist;
