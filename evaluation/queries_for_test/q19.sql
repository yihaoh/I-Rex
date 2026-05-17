-- q19

-- original query
/*
select
    sum(l_extendedprice* (1 - l_discount)) as revenue
from
    lineitem,
    part
where
    (
        p_partkey = l_partkey
        and p_brand = 'Brand#35  '
        and p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
        and l_quantity >= 50 and l_quantity <= 50 + 10
        and p_size between 1 and 5
        and l_shipmode in ('AIR', 'AIR REG')
        and l_shipinstruct = 'DELIVER IN PERSON'
    )
    or
    (
        p_partkey = l_partkey
        and p_brand = 'Brand#32  '
        and p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
        and l_quantity >= 30 and l_quantity <= 30 + 10
        and p_size between 1 and 10
        and l_shipmode in ('AIR', 'AIR REG')
        and l_shipinstruct = 'DELIVER IN PERSON'
    )
    or
    (
        p_partkey = l_partkey
        and p_brand = 'Brand#43  '
        and p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
        and l_quantity >= 60 and l_quantity <= 60 + 10
        and p_size between 1 and 15
        and l_shipmode in ('AIR', 'AIR REG')
        and l_shipinstruct = 'DELIVER IN PERSON'
    )
*/


-- query id: q19_join_filter
-- milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber, p_partkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber, p_partkey) - 1,
           ROW(l_orderkey, l_linenumber, p_partkey),
           l_orderkey, l_partkey, l_suppkey, l_linenumber, p_partkey
    FROM lineitem, part
    WHERE (
        p_partkey = l_partkey
        AND p_brand = 'Brand#35  '
        AND p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
        AND l_quantity >= 50 and l_quantity <= 50 + 10
        -- AND p_size between 1 and 5
        -- AND l_shipmode in ('AIR', 'AIR REG')
        -- AND l_shipinstruct = 'DELIVER IN PERSON'
    )
    OR
    (
        p_partkey = l_partkey
        AND p_brand = 'Brand#32  '
        AND p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
        AND l_quantity >= 30 and l_quantity <= 30 + 10
        -- AND p_size between 1 and 10
        -- AND l_shipmode in ('AIR', 'AIR REG')
        -- AND l_shipinstruct = 'DELIVER IN PERSON'
    )
    OR
    (
        p_partkey = l_partkey
        AND p_brand = 'Brand#43  '
        AND p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
        AND l_quantity >= 60 and l_quantity <= 60 + 10
        -- AND p_size between 1 and 15
        -- AND l_shipmode in ('AIR', 'AIR REG')
        -- AND l_shipinstruct = 'DELIVER IN PERSON'
    )
)
SELECT MIN(seq) seq, MIN_IID(iid), COUNT(*) count,
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_orderkey, l_linenumber, p_partkey),
        *
FROM lineitem, part
WHERE (
    p_partkey = l_partkey
    AND p_brand = 'Brand#35  '
    AND p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
    AND l_quantity >= 50 and l_quantity <= 50 + 10
    -- AND p_size between 1 and 5
    -- AND l_shipmode in ('AIR', 'AIR REG')
    -- AND l_shipinstruct = 'DELIVER IN PERSON'
)
OR
(
    p_partkey = l_partkey
    AND p_brand = 'Brand#32  '
    AND p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
    AND l_quantity >= 30 and l_quantity <= 30 + 10
    -- AND p_size between 1 and 10
    -- AND l_shipmode in ('AIR', 'AIR REG')
    -- AND l_shipinstruct = 'DELIVER IN PERSON'
)
OR
(
    p_partkey = l_partkey
    AND p_brand = 'Brand#43  '
    AND p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
    AND l_quantity >= 60 and l_quantity <= 60 + 10
    -- AND p_size between 1 and 15
    -- AND l_shipmode in ('AIR', 'AIR REG')
    -- AND l_shipinstruct = 'DELIVER IN PERSON'
)
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
ORDER BY l_orderkey, l_linenumber, p_partkey;

-- naive page fetch query
SELECT ROW(l_orderkey, l_linenumber, p_partkey),
        *
FROM lineitem, part
WHERE (
    p_partkey = l_partkey
    AND p_brand = 'Brand#35  '
    AND p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
    AND l_quantity >= 50 and l_quantity <= 50 + 10
    -- AND p_size between 1 and 5
    -- AND l_shipmode in ('AIR', 'AIR REG')
    -- AND l_shipinstruct = 'DELIVER IN PERSON'
)
OR
(
    p_partkey = l_partkey
    AND p_brand = 'Brand#32  '
    AND p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
    AND l_quantity >= 30 and l_quantity <= 30 + 10
    -- AND p_size between 1 and 10
    -- AND l_shipmode in ('AIR', 'AIR REG')
    -- AND l_shipinstruct = 'DELIVER IN PERSON'
)
OR
(
    p_partkey = l_partkey
    AND p_brand = 'Brand#43  '
    AND p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
    AND l_quantity >= 60 and l_quantity <= 60 + 10
    -- AND p_size between 1 and 15
    -- AND l_shipmode in ('AIR', 'AIR REG')
    -- AND l_shipinstruct = 'DELIVER IN PERSON'
)
ORDER BY l_orderkey, l_linenumber, p_partkey;
