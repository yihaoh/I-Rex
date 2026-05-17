-- q22

-- original query
/*
select
    cntrycode,
    count(*) as numcust,
    sum(c_acctbal) as totacctbal
from
    (
        select
            substring(c_phone from 1 for 2) as cntrycode,
            c_acctbal
        from
            customer
        where
            substring(c_phone from 1 for 2) in
                ('19', '13', '17', '20', '14', '12', '25')
            and c_acctbal > (
                select
                    avg(c_acctbal)
                from
                    customer
                where
                    c_acctbal > 0.00
                    and substring(c_phone from 1 for 2) in
                        ('19', '13', '17', '20', '14', '12', '25')
            )
            and not exists (
                select
                    *
                from
                    orders
                where
                    o_custkey = c_custkey
            )
    ) as custsale
group by
    cntrycode
order by
    cntrycode
*/

-- cached query
/*
select avg(c_acctbal)
from customer
where c_acctbal > 0.00
and substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25');
*/



-- query id: custsale_output
-- milestone query
WITH tmp(seq, iid, c_custkey, c_nationkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY c_custkey) - 1,
           ROW(c_custkey),
           c_custkey, c_nationkey
            -- substring(c_phone from 1 for 2) as cntrycode,
            -- c_acctbal
    FROM customer
    WHERE substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
    AND c_acctbal > (
        SELECT avg(c_acctbal)
        FROM customer
        WHERE c_acctbal > 0.00
        AND substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
    )
    AND NOT EXISTS (
        SELECT *
        FROM orders
        WHERE o_custkey = c_custkey
    )
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN(c_custkey), MAX(c_custkey)] c_custkey, ARRAY[MIN(c_nationkey), MAX(c_nationkey)] c_nationkey, 
       BLMFL(numeric_send(c_custkey)) blmfl_filter
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(c_custkey),
        substring(c_phone from 1 for 2) as cntrycode,
        c_acctbal
FROM customer
WHERE substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
AND c_acctbal > %(cached_scalar)s
AND NOT EXISTS (
    SELECT *
    FROM orders
    WHERE o_custkey = c_custkey
    AND o_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s 
)
AND ROW(c_custkey) >= %(iid,min)s 
AND ROW(c_custkey) < %(iid,max)s 
AND c_custkey BETWEEN %(c_custkey,min)s AND %(c_custkey,max)s 
AND c_nationkey BETWEEN %(c_nationkey,min)s AND %(c_nationkey,max)s 
AND BLMFL_TEST(%(blmfl_filter)s, numeric_send(c_custkey))
ORDER BY c_custkey;

-- naive page fetch query
SELECT ROW(c_custkey),
        substring(c_phone from 1 for 2) as cntrycode,
        c_acctbal
FROM customer
WHERE substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
AND c_acctbal > (
    SELECT avg(c_acctbal)
    FROM customer
    WHERE c_acctbal > 0.00
    AND substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
)
AND NOT EXISTS (
    SELECT *
    FROM orders
    WHERE o_custkey = c_custkey
)
ORDER BY c_custkey;


-- query id: q22_output
-- milestone query
WITH tmp(seq, iid, custsale_output_iid) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY cntrycode) - 1,
           ROW(cntrycode),
           ARRAY[MIN_IID(ROW(c_custkey)), MAX_IID(ROW(c_custkey))] custsale_output_iid
        -- cntrycode,
        -- count(*) as numcust,
        -- sum(c_acctbal) as totacctbal
    FROM
        (
            SELECT 
                substring(c_phone from 1 for 2) as cntrycode,
                c_acctbal,
                c_custkey
            FROM customer
            WHERE substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
            AND c_acctbal > (
                SELECT avg(c_acctbal)
                FROM customer
                WHERE c_acctbal > 0.00
                AND substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
            )
            AND NOT EXISTS (
                SELECT *
                FROM orders
                WHERE o_custkey = c_custkey
            )
        ) as custsale
    GROUP BY cntrycode
)
SELECT MIN(seq) seq, MIN_IID(iid) iid, COUNT(*) count,
       ARRAY[MIN_IID(custsale_output_iid[1]), MAX_IID(custsale_output_iid[2])] custsale_output_iid
FROM tmp
GROUP BY seq / %(pg_sz)s 
ORDER BY seq / %(pg_sz)s;

-- page fetch query
SELECT ROW(cntrycode),
        cntrycode,
        count(*) as numcust,
        sum(c_acctbal) as totacctbal,
        MIN_IID(ROW(ROW(c_custkey)))
FROM
    (
        SELECT 
            substring(c_phone from 1 for 2) as cntrycode,
            c_acctbal,
            c_custkey
        FROM customer
        WHERE substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
        AND c_acctbal > (
            SELECT avg(c_acctbal)
            FROM customer
            WHERE c_acctbal > 0.00
            AND substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
        )
        AND NOT EXISTS (
            SELECT *
            FROM orders
            WHERE o_custkey = c_custkey
            AND o_custkey BETWEEN %(custsale_output,c_custkey,min)s AND %(custsale_output,c_custkey,max)s 
        )
        AND ROW(c_custkey) >= %(custsale_output,iid,min)s 
        AND ROW(c_custkey) < %(custsale_output,iid,max)s 
        AND c_custkey BETWEEN %(custsale_output,c_custkey,min)s AND %(custsale_output,c_custkey,max)s 
        AND c_nationkey BETWEEN %(custsale_output,c_nationkey,min)s AND %(custsale_output,c_nationkey,max)s 
        AND BLMFL_TEST(%(custsale_output,blmfl_filter)s, numeric_send(c_custkey))
    ) as custsale
WHERE ROW(cntrycode::int) >= %(iid,min)s
AND ROW(cntrycode::int) < %(iid,max)s
GROUP BY cntrycode
ORDER BY cntrycode;

-- naive page fetch query
SELECT ROW(cntrycode),
        cntrycode,
        count(*) as numcust,
        sum(c_acctbal) as totacctbal,
        MIN_IID(ROW(ROW(c_custkey)))
FROM
    (
        SELECT 
            substring(c_phone from 1 for 2) as cntrycode,
            c_acctbal,
            c_custkey
        FROM customer
        WHERE substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
        AND c_acctbal > (
            SELECT avg(c_acctbal)
            FROM customer
            WHERE c_acctbal > 0.00
            AND substring(c_phone from 1 for 2) in ('19', '13', '17', '20', '14', '12', '25')
        )
        AND NOT EXISTS (
            SELECT *
            FROM orders
            WHERE o_custkey = c_custkey
        )
    ) as custsale
GROUP BY cntrycode
ORDER BY cntrycode;