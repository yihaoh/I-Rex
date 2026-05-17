-- q7

-- original query
/*
select
    supp_nation,
    cust_nation,
    l_year,
    sum(volume) as revenue
from
    (
        select
            n1.n_name as supp_nation,
            n2.n_name as cust_nation,
            extract(year from l_shipdate) as l_year,
            l_extendedprice * (1 - l_discount) as volume
        from
            supplier,
            lineitem,
            orders,
            customer,
            nation n1,
            nation n2
        where
            s_suppkey = l_suppkey
            and o_orderkey = l_orderkey
            and c_custkey = o_custkey
            and s_nationkey = n1.n_nationkey
            and c_nationkey = n2.n_nationkey
            and (
                (n1.n_name = 'CHINA' and n2.n_name = 'UNITED STATES')
                or (n1.n_name = 'UNITED STATES' and n2.n_name = 'CHINA')
            )
            and l_shipdate between date '1993-01-01' and date '1996-12-31'
    ) as shipping
group by
    supp_nation,
    cust_nation,
    l_year
order by
    supp_nation,
    cust_nation,
    l_year
*/


-- query id: shipping_final
-- milestone query
WITH tmp(seq, iid, s_suppkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderkey, o_orderdate, o_custkey, c_custkey, n1_n_nationkey, n2_n_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey) - 1,
           ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey),
           s_suppkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderkey, o_orderdate, o_custkey, c_custkey, n1.n_nationkey, n2.n_nationkey
        -- n1.n_name as supp_nation,
        -- n2.n_name as cust_nation,
        -- extract(year from l_shipdate) as l_year,
        -- l_extendedprice * (1 - l_discount) as volume
    FROM
        supplier,
        lineitem,
        orders,
        customer,
        nation n1,
        nation n2
    WHERE s_suppkey = l_suppkey
    AND o_orderkey = l_orderkey
    AND c_custkey = o_custkey
    AND s_nationkey = n1.n_nationkey
    AND c_nationkey = n2.n_nationkey
    AND (
        (n1.n_name = 'CHINA' and n2.n_name = 'UNITED STATES')
        or (n1.n_name = 'UNITED STATES' and n2.n_name = 'CHINA')
    )
    AND l_shipdate between date '1993-01-01' and date '1996-12-31'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, 
       ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, 
       ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(o_orderdate), MAX(o_orderdate)] o_orderdate, 
       ARRAY[MIN(o_custkey), MAX(o_custkey)] o_custkey, ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, 
       ARRAY[MIN(n1_n_nationkey), MAX(n1_n_nationkey)] n1_n_nationkey, ARRAY[MIN(n2_n_nationkey), MAX(n2_n_nationkey)] n2_n_nationkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey),
        n1.n_name as supp_nation,
        n2.n_name as cust_nation,
        extract(year from l_shipdate) as l_year,
        l_extendedprice * (1 - l_discount) as volume
FROM
    supplier,
    lineitem,
    orders,
    customer,
    nation n1,
    nation n2
WHERE s_suppkey = l_suppkey
AND o_orderkey = l_orderkey
AND c_custkey = o_custkey
AND s_nationkey = n1.n_nationkey
AND c_nationkey = n2.n_nationkey
AND (
    (n1.n_name = 'CHINA' and n2.n_name = 'UNITED STATES')
    or (n1.n_name = 'UNITED STATES' and n2.n_name = 'CHINA')
)
AND l_shipdate BETWEEN date '1993-01-01' AND date '1996-12-31'
AND ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey) >= %(iid,min)s
AND ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey) < %(iid,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND o_orderdate BETWEEN %(o_orderdate,min)s AND %(o_orderdate,max)s
AND o_custkey BETWEEN %(o_custkey,min)s AND %(o_custkey,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
-- AND n1.n_nationkey BETWEEN %(n1_n_nationkey,min)s AND %(n1_n_nationkey,max)s
-- AND n2.n_nationkey BETWEEN %(n2_n_nationkey,min)s AND %(n2_n_nationkey,max)s
ORDER BY s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey;

-- naive page fetch query
SELECT ROW(s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey),
        n1.n_name as supp_nation,
        n2.n_name as cust_nation,
        extract(year from l_shipdate) as l_year,
        l_extendedprice * (1 - l_discount) as volume
FROM
    supplier,
    lineitem,
    orders,
    customer,
    nation n1,
    nation n2
WHERE s_suppkey = l_suppkey
AND o_orderkey = l_orderkey
AND c_custkey = o_custkey
AND s_nationkey = n1.n_nationkey
AND c_nationkey = n2.n_nationkey
AND (
    (n1.n_name = 'CHINA' and n2.n_name = 'UNITED STATES')
    or (n1.n_name = 'UNITED STATES' and n2.n_name = 'CHINA')
)
AND l_shipdate BETWEEN date '1993-01-01' AND date '1996-12-31'
ORDER BY s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey;

