-- q2

-- original query
-- select
-- 	s_acctbal,
-- 	s_name,
-- 	n_name,
-- 	p_partkey,
-- 	p_mfgr,
-- 	s_address,
-- 	s_phone,
-- 	s_comment
-- from
-- 	part,
-- 	supplier,
-- 	partsupp,
-- 	nation,
-- 	region
-- where
-- 	p_partkey = ps_partkey
-- 	and s_suppkey = ps_suppkey
-- 	and p_size = 35
-- 	and p_type like '%%STEEL'
-- 	and s_nationkey = n_nationkey
-- 	and n_regionkey = r_regionkey
-- 	and r_name = 'AMERICA'
-- 	and ps_supplycost = (
-- 		select
-- 			min(ps_supplycost)
-- 		from
-- 			partsupp,
-- 			supplier,
-- 			nation,
-- 			region
-- 		where
-- 			p_partkey = ps_partkey
-- 			and s_suppkey = ps_suppkey
-- 			and s_nationkey = n_nationkey
-- 			and n_regionkey = r_regionkey
-- 			and r_name = 'AMERICA'
-- 	)
-- order by
-- 	s_acctbal desc,
-- 	n_name,
-- 	s_name,
-- 	p_partkey


-- query id: q2_join_filter
-- milestone query
WITH tmp(seq, iid, p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, n_regionkey, r_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey) - 1,
           ROW(p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey),
           p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, n_regionkey, r_regionkey
    FROM part, supplier, partsupp, nation, region
    WHERE p_partkey = ps_partkey
    AND s_suppkey = ps_suppkey
    AND p_size = 35
    AND p_type LIKE '%%STEEL'
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    AND r_name = 'AMERICA'
    AND ps_supplycost = (
        SELECT MIN(ps_supplycost)
        FROM partsupp, supplier, nation, region
        WHERE p_partkey = ps_partkey
        AND s_suppkey = ps_suppkey
        AND s_nationkey = n_nationkey
        AND n_regionkey = r_regionkey
        AND r_name = 'AMERICA'
    )
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
       ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
       ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey,
       ARRAY[MIN(r_regionkey), MAX(r_regionkey)] r_regionkey,
       BLMFL(numeric_send(p_partkey)) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey) iid,
       *
FROM part, supplier, partsupp, nation, region
WHERE p_partkey = ps_partkey
AND s_suppkey = ps_suppkey
AND p_size = 35
AND p_type LIKE '%%STEEL'
AND s_nationkey = n_nationkey
AND n_regionkey = r_regionkey
AND r_name = 'AMERICA'
AND ps_supplycost = (
    SELECT MIN(ps_supplycost)
    FROM partsupp, supplier, nation, region
    WHERE p_partkey = ps_partkey
    AND s_suppkey = ps_suppkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    AND r_name = 'AMERICA'
    AND ps_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
)
AND ROW(p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey) >= %(iid,min)s
AND ROW(p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey) < %(iid,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
-- AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
-- AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
-- AND r_regionkey BETWEEN %(r_regionkey,min)s AND %(r_regionkey,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(p_partkey))
ORDER BY p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey;

-- naive page fetch query
SELECT ROW(p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey),
        *
FROM part, supplier, partsupp, nation, region
WHERE p_partkey = ps_partkey
AND s_suppkey = ps_suppkey
AND p_size = 35
AND p_type LIKE '%%STEEL'
AND s_nationkey = n_nationkey
AND n_regionkey = r_regionkey
AND r_name = 'AMERICA'
AND ps_supplycost = (
    SELECT MIN(ps_supplycost)
    FROM partsupp, supplier, nation, region
    WHERE p_partkey = ps_partkey
    AND s_suppkey = ps_suppkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    AND r_name = 'AMERICA'
)
ORDER BY p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, r_regionkey;



-- query id: q2_output
-- milestone query
WITH tmp(seq, iid, p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, n_regionkey, r_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY  n_name, s_name, p_partkey, s_suppkey, n_nationkey) - 1,
           ROW(n_name, s_name, p_partkey, s_suppkey, n_nationkey),
           p_partkey, s_suppkey, ps_partkey, ps_suppkey, n_nationkey, n_regionkey, r_regionkey
    FROM part, supplier, partsupp, nation, region
    WHERE p_partkey = ps_partkey
    AND s_suppkey = ps_suppkey
    AND p_size = 35
    AND p_type LIKE '%%STEEL'
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    AND r_name = 'AMERICA'
    AND ps_supplycost = (
        SELECT MIN(ps_supplycost)
        FROM partsupp, supplier, nation, region
        WHERE p_partkey = ps_partkey
        AND s_suppkey = ps_suppkey
        AND s_nationkey = n_nationkey
        AND n_regionkey = r_regionkey
        AND r_name = 'AMERICA'
    )
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey, ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, 
       ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
       ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey,
       ARRAY[MIN(r_regionkey), MAX(r_regionkey)] r_regionkey,
       BLMFL(numeric_send(p_partkey)) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(s_acctbal, n_name, s_name, p_partkey, s_suppkey, n_nationkey) iid,
       s_acctbal, s_name, n_name, p_partkey, p_mfgr, s_address, s_phone, s_comment
FROM part, supplier, partsupp, nation, region
WHERE p_partkey = ps_partkey
AND s_suppkey = ps_suppkey
AND p_size = 35
AND p_type LIKE '%%STEEL'
AND s_nationkey = n_nationkey
AND n_regionkey = r_regionkey
AND r_name = 'AMERICA'
AND ps_supplycost = (
    SELECT MIN(ps_supplycost)
    FROM partsupp, supplier, nation, region
    WHERE p_partkey = ps_partkey
    AND s_suppkey = ps_suppkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    AND r_name = 'AMERICA'
    AND ps_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
)
AND ROW(n_name, s_name, p_partkey, s_suppkey, n_nationkey) >= %(iid,min)s
AND ROW(n_name, s_name, p_partkey, s_suppkey, n_nationkey) < %(iid,max)s
AND p_partkey BETWEEN %(p_partkey,min)s AND %(p_partkey,max)s
AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
-- AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
-- AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
-- AND r_regionkey BETWEEN %(r_regionkey,min)s AND %(r_regionkey,max)s
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(p_partkey))
ORDER BY n_name, s_name, p_partkey, s_suppkey, n_nationkey;

-- naive page fetch query
SELECT ROW( n_name, s_name, p_partkey, s_suppkey, n_nationkey),
       s_name, n_name, p_partkey, p_mfgr, s_address, s_phone, s_comment
FROM part, supplier, partsupp, nation, region
WHERE p_partkey = ps_partkey
AND s_suppkey = ps_suppkey
AND p_size = 35
AND p_type LIKE '%%STEEL'
AND s_nationkey = n_nationkey
AND n_regionkey = r_regionkey
AND r_name = 'AMERICA'
AND ps_supplycost = (
    SELECT MIN(ps_supplycost)
    FROM partsupp, supplier, nation, region
    WHERE p_partkey = ps_partkey
    AND s_suppkey = ps_suppkey
    AND s_nationkey = n_nationkey
    AND n_regionkey = r_regionkey
    AND r_name = 'AMERICA'
)
ORDER BY n_name, s_name, p_partkey, s_suppkey, n_nationkey;