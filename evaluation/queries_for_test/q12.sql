-- q12

-- original query
/*
select
    l_shipmode,
    sum(case
        when o_orderpriority = '1-URGENT'
            or o_orderpriority = '2-HIGH'
            then 1
        else 0
    end) as high_line_count,
    sum(case
        when o_orderpriority <> '1-URGENT'
            and o_orderpriority <> '2-HIGH'
            then 1
        else 0
    end) as low_line_count
from
    orders,
    lineitem
where
    o_orderkey = l_orderkey
    and l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
    and l_commitdate < l_receiptdate
    and l_shipdate < l_commitdate
    and l_receiptdate >= date '1996-01-01'
    and l_receiptdate < date '1996-01-01' + interval '1' year
group by
    l_shipmode
order by
    l_shipmode

*/


-- query id: q12_join_filter
-- milestone query
WITH tmp(seq, iid, o_orderkey, o_custkey, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_orderkey, l_orderkey, l_linenumber) - 1,
           ROW(o_orderkey, l_orderkey, l_linenumber),
           o_orderkey, o_custkey, l_orderkey, l_partkey, l_suppkey, l_linenumber
    FROM orders, lineitem
    WHERE o_orderkey = l_orderkey
    AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
    AND l_commitdate < l_receiptdate
    AND l_shipdate < l_commitdate
    AND l_receiptdate >= date '1996-01-01'
    AND l_receiptdate < date '1996-01-01' + interval '1' year
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(o_orderkey, l_orderkey, l_linenumber),
        o_orderkey, o_custkey, l_orderkey, l_partkey, l_suppkey, l_linenumber
FROM orders, lineitem
WHERE o_orderkey = l_orderkey
AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
AND l_commitdate < l_receiptdate
AND l_shipdate < l_commitdate
AND l_receiptdate >= date '1996-01-01'
AND l_receiptdate < date '1996-01-01' + interval '1' year
AND ROW(o_orderkey, l_orderkey, l_linenumber) >= %(iid,min)s
AND ROW(o_orderkey, l_orderkey, l_linenumber) < %(iid,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
ORDER BY o_orderkey, l_orderkey, l_linenumber;

-- naive page fetch query
SELECT ROW(o_orderkey, l_orderkey, l_linenumber),
        o_orderkey, o_custkey, l_orderkey, l_partkey, l_suppkey, l_linenumber
FROM orders, lineitem
WHERE o_orderkey = l_orderkey
AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
AND l_commitdate < l_receiptdate
AND l_shipdate < l_commitdate
AND l_receiptdate >= date '1996-01-01'
AND l_receiptdate < date '1996-01-01' + interval '1' year
ORDER BY o_orderkey, l_orderkey, l_linenumber;


-- query id: q12_group
-- milestone query
WITH tmp(seq, iid, o_orderkey, o_custkey, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_shipmode) - 1,
           ROW(l_shipmode),
           ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
            ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM orders, lineitem
    WHERE o_orderkey = l_orderkey
    AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
    AND l_commitdate < l_receiptdate
    AND l_shipdate < l_commitdate
    AND l_receiptdate >= date '1996-01-01'
    AND l_receiptdate < date '1996-01-01' + interval '1' year
    GROUP BY l_shipmode
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(o_custkey[1]), MAX(o_custkey[2])] o_custkey, 
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
       ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_shipmode), l_shipmode, MIN_IID(ROW(ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM orders, lineitem
WHERE o_orderkey = l_orderkey
AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
AND l_commitdate < l_receiptdate
AND l_shipdate < l_commitdate
AND l_receiptdate >= date '1996-01-01'
AND l_receiptdate < date '1996-01-01' + interval '1' year
AND ROW(l_shipmode) >= %(iid,min)s
AND ROW(l_shipmode) < %(iid,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY l_shipmode
ORDER BY l_shipmode;

-- naive page fetch query
SELECT ROW(l_shipmode), l_shipmode, MIN_IID(ROW(ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM orders, lineitem
WHERE o_orderkey = l_orderkey
AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
AND l_commitdate < l_receiptdate
AND l_shipdate < l_commitdate
AND l_receiptdate >= date '1996-01-01'
AND l_receiptdate < date '1996-01-01' + interval '1' year
GROUP BY l_shipmode
ORDER BY l_shipmode;


-- query id: q12_output
-- milestone query
WITH tmp(seq, iid, o_orderkey, o_custkey, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_shipmode) - 1,
           ROW(l_shipmode),
           ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, 
            ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
            ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM orders, lineitem
    WHERE o_orderkey = l_orderkey
    AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
    AND l_commitdate < l_receiptdate
    AND l_shipdate < l_commitdate
    AND l_receiptdate >= date '1996-01-01'
    AND l_receiptdate < date '1996-01-01' + interval '1' year
    GROUP BY l_shipmode
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(o_orderkey[1]), MAX(o_orderkey[2])] o_orderkey, ARRAY[MIN(o_custkey[1]), MAX(o_custkey[2])] o_custkey, 
       ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
       ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(l_shipmode), l_shipmode,
        l_shipmode,
        sum(case
            when o_orderpriority = '1-URGENT'
                or o_orderpriority = '2-HIGH'
                then 1
            else 0
        end) as high_line_count,
        sum(case
            when o_orderpriority <> '1-URGENT'
                and o_orderpriority <> '2-HIGH'
                then 1
            else 0
        end) as low_line_count,
        MIN_IID(ROW(ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM orders, lineitem
WHERE o_orderkey = l_orderkey
AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
AND l_commitdate < l_receiptdate
AND l_shipdate < l_commitdate
AND l_receiptdate >= date '1996-01-01'
AND l_receiptdate < date '1996-01-01' + interval '1' year
AND ROW(l_shipmode) >= %(iid,min)s
AND ROW(l_shipmode) < %(iid,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
GROUP BY l_shipmode
ORDER BY l_shipmode;

-- naive page fetch query
SELECT ROW(l_shipmode), l_shipmode,
        l_shipmode,
        sum(case
            when o_orderpriority = '1-URGENT'
                or o_orderpriority = '2-HIGH'
                then 1
            else 0
        end) as high_line_count,
        sum(case
            when o_orderpriority <> '1-URGENT'
                and o_orderpriority <> '2-HIGH'
                then 1
            else 0
        end) as low_line_count,
        MIN_IID(ROW(ROW(o_orderkey), ROW(l_orderkey, l_linenumber)))
FROM orders, lineitem
WHERE o_orderkey = l_orderkey
AND l_shipmode in ('FOB', 'TRUCK', 'REG AIR', 'AIR', 'MAIL', 'RAIL', 'SHIP')
AND l_commitdate < l_receiptdate
AND l_shipdate < l_commitdate
AND l_receiptdate >= date '1996-01-01'
AND l_receiptdate < date '1996-01-01' + interval '1' year
GROUP BY l_shipmode
ORDER BY l_shipmode;