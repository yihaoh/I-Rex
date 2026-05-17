-- q8

-- original query
/*
select
    o_year,
    sum(case
        when nation = 'UNITED STATES' then volume
        else 0
    end) / sum(volume) as mkt_share
from
    (
        select
            extract(year from o_orderdate) as o_year,
            l_extendedprice * (1 - l_discount) as volume,
            n2.n_name as nation
        from
            part,
            supplier,
            lineitem,
            orders,
            customer,
            nation n1,
            nation n2,
            region
        where
            p_partkey = l_partkey
            and s_suppkey = l_suppkey
            and l_orderkey = o_orderkey
            and o_custkey = c_custkey
            and c_nationkey = n1.n_nationkey
            and n1.n_regionkey = r_regionkey
            and r_name = 'AMERICA'
            and s_nationkey = n2.n_nationkey
            and o_orderdate between date '1993-01-01' and date '1997-12-31'
            and p_type = 'LARGE BURNISHED TIN'
    ) as all_nations
group by
    o_year
order by
    o_year

*/



-- query id: all_nations_output
-- milestone query
WITH tmp(seq, iid, p_partkey, s_suppkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderkey, c_custkey, n1_n_nationkey, n2_n_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey) - 1,
           ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey),
           p_partkey, s_suppkey, l_orderkey, l_partkey, l_suppkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey
            -- extract(year from o_orderdate) as o_year,
            -- l_extendedprice * (1 - l_discount) as volume,
            -- n2.n_name as nation
    FROM
        part,
        supplier,
        lineitem,
        orders,
        customer,
        nation n1,
        nation n2,
        region
    WHERE
        p_partkey = l_partkey
        AND s_suppkey = l_suppkey
        AND l_orderkey = o_orderkey
        AND o_custkey = c_custkey
        AND c_nationkey = n1.n_nationkey
        AND n1.n_regionkey = r_regionkey
        AND r_name = 'AMERICA'
        AND s_nationkey = n2.n_nationkey
        AND o_orderdate BETWEEN date '1993-01-01' AND date '1997-12-31'
        -- AND p_type = 'LARGE BURNISHED TIN'
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
       ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
       ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber, 
       ARRAY[MIN(o_orderkey), MAX(o_orderkey)] o_orderkey, ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, 
       ARRAY[MIN(n1_n_nationkey), MAX(n1_n_nationkey)] n1_n_nationkey, ARRAY[MIN(n2_n_nationkey), MAX(n2_n_nationkey)] n2_n_nationkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey),
        extract(year from o_orderdate) as o_year,
        l_extendedprice * (1 - l_discount) as volume,
        n2.n_name as nation
FROM
    part,
    supplier,
    lineitem,
    orders,
    customer,
    nation n1,
    nation n2,
    region
WHERE p_partkey = l_partkey
AND s_suppkey = l_suppkey
AND l_orderkey = o_orderkey
AND o_custkey = c_custkey
AND c_nationkey = n1.n_nationkey
AND n1.n_regionkey = r_regionkey
AND r_name = 'AMERICA'
AND s_nationkey = n2.n_nationkey
AND o_orderdate BETWEEN date '1993-01-01' AND date '1997-12-31'
-- AND p_type = 'LARGE BURNISHED TIN'
AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey) >= %(iid,min)s
AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey) < %(iid,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND l_orderkey BETWEEN %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN %(l_linenumber,min)s AND %(l_linenumber,max)s
AND o_orderkey BETWEEN %(o_orderkey,min)s AND %(o_orderkey,max)s
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s
-- AND n1.n_nationkey BETWEEN %(n1_n_nationkey,min)s AND %(n1_n_nationkey,max)s
-- AND n2.n_nationkey BETWEEN %(n2_n_nationkey,min)s AND %(n2_n_nationkey,max)s
ORDER BY p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey;

-- naive page fetch query
SELECT ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey),
        extract(year from o_orderdate) as o_year,
        l_extendedprice * (1 - l_discount) as volume,
        n2.n_name as nation
FROM
    part,
    supplier,
    lineitem,
    orders,
    customer,
    nation n1,
    nation n2,
    region
WHERE p_partkey = l_partkey
AND s_suppkey = l_suppkey
AND l_orderkey = o_orderkey
AND o_custkey = c_custkey
AND c_nationkey = n1.n_nationkey
AND n1.n_regionkey = r_regionkey
AND r_name = 'AMERICA'
AND s_nationkey = n2.n_nationkey
AND o_orderdate BETWEEN date '1993-01-01' AND date '1997-12-31'
-- AND p_type = 'LARGE BURNISHED TIN'
ORDER BY p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey;



-- query id: q8_output
-- milestone query
WITH tmp(seq, iid, all_nations_output_iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY o_year) - 1,
           ROW(o_year),
           ARRAY[MIN_IID(ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1_n_nationkey, n2_n_nationkey, r_regionkey)), 
                 MAX_IID(ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1_n_nationkey, n2_n_nationkey, r_regionkey))]
    FROM 
        (
            SELECT
                extract(year from o_orderdate) as o_year,
                l_extendedprice * (1 - l_discount) as volume,
                n2.n_name as nation,
                p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey n1_n_nationkey, n2.n_nationkey n2_n_nationkey, r_regionkey
            FROM
                part,
                supplier,
                lineitem,
                orders,
                customer,
                nation n1,
                nation n2,
                region
            WHERE
                p_partkey = l_partkey
                and s_suppkey = l_suppkey
                and l_orderkey = o_orderkey
                and o_custkey = c_custkey
                and c_nationkey = n1.n_nationkey
                and n1.n_regionkey = r_regionkey
                and r_name = 'AMERICA'
                and s_nationkey = n2.n_nationkey
                and o_orderdate between date '1993-01-01' and date '1997-12-31'
                -- and p_type = 'LARGE BURNISHED TIN'
        ) as all_nations
    GROUP BY o_year
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(all_nations_output_iid[1]), MAX_IID(all_nations_output_iid[2])] all_nations_output_iid
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(o_year),
       o_year,
        sum(case
            when nation = 'UNITED STATES' then volume
            else 0
        end) / sum(volume) as mkt_share,
        MIN_IID(ROW(ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1_n_nationkey, n2_n_nationkey, r_regionkey)))
FROM 
    (
        SELECT
            extract(year from o_orderdate) as o_year,
            l_extendedprice * (1 - l_discount) as volume,
            n2.n_name as nation,
            p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey n1_n_nationkey, n2.n_nationkey n2_n_nationkey, r_regionkey
        FROM
            part,
            supplier,
            lineitem,
            orders,
            customer,
            nation n1,
            nation n2,
            region
        WHERE p_partkey = l_partkey
        AND s_suppkey = l_suppkey
        AND l_orderkey = o_orderkey
        AND o_custkey = c_custkey
        AND c_nationkey = n1.n_nationkey
        AND n1.n_regionkey = r_regionkey
        AND r_name = 'AMERICA'
        AND s_nationkey = n2.n_nationkey
        AND o_orderdate between date '1993-01-01' and date '1997-12-31'
        -- AND p_type = 'LARGE BURNISHED TIN'
        AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey) >= %(all_nations_output,iid,min)s
        AND ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey, n2.n_nationkey, r_regionkey) < %(all_nations_output,iid,max)s
        AND p_partkey BETWEEN %(all_nations_output,p_partkey,min)s AND %(all_nations_output,p_partkey,max)s
        AND s_suppkey BETWEEN %(all_nations_output,s_suppkey,min)s AND %(all_nations_output,s_suppkey,max)s
        AND l_orderkey BETWEEN %(all_nations_output,l_orderkey,min)s AND %(all_nations_output,l_orderkey,max)s
        AND l_partkey BETWEEN %(all_nations_output,l_partkey,min)s AND %(all_nations_output,l_partkey,max)s
        AND l_suppkey BETWEEN %(all_nations_output,l_suppkey,min)s AND %(all_nations_output,l_suppkey,max)s
        AND l_linenumber BETWEEN %(all_nations_output,l_linenumber,min)s AND %(all_nations_output,l_linenumber,max)s
        AND o_orderkey BETWEEN %(all_nations_output,o_orderkey,min)s AND %(all_nations_output,o_orderkey,max)s
        AND c_custkey BETWEEN %(all_nations_output,c_custkey,min)s AND %(all_nations_output,c_custkey,max)s
        -- AND n1.n_nationkey BETWEEN %(all_nations_output,n1_n_nationkey,min)s AND %(all_nations_output,n1_n_nationkey,max)s
        -- AND n2.n_nationkey BETWEEN %(all_nations_output,n2_n_nationkey,min)s AND %(all_nations_output,n2_n_nationkey,max)s
    ) as all_nations
GROUP BY o_year
ORDER BY o_year;

-- naive page fetch query
SELECT ROW(o_year),
       o_year,
        sum(case
            when nation = 'UNITED STATES' then volume
            else 0
        end) / sum(volume) as mkt_share,
        MIN_IID(ROW(ROW(p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1_n_nationkey, n2_n_nationkey, r_regionkey)))
FROM 
    (
        SELECT
            extract(year from o_orderdate) as o_year,
            l_extendedprice * (1 - l_discount) as volume,
            n2.n_name as nation,
            p_partkey, s_suppkey, l_orderkey, l_linenumber, o_orderkey, c_custkey, n1.n_nationkey n1_n_nationkey, n2.n_nationkey n2_n_nationkey, r_regionkey
        FROM
            part,
            supplier,
            lineitem,
            orders,
            customer,
            nation n1,
            nation n2,
            region
        WHERE p_partkey = l_partkey
        AND s_suppkey = l_suppkey
        AND l_orderkey = o_orderkey
        AND o_custkey = c_custkey
        AND c_nationkey = n1.n_nationkey
        and n1.n_regionkey = r_regionkey
        AND r_name = 'AMERICA'
        AND s_nationkey = n2.n_nationkey
        AND o_orderdate BETWEEN date '1993-01-01' AND date '1997-12-31'
        -- AND p_type = 'LARGE BURNISHED TIN'
    ) as all_nations
GROUP BY o_year
ORDER BY o_year;
