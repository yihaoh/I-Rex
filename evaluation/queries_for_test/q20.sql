-- Q20:
-- NOTES:
--
-- 1. the subquery following ps_partkey IN is not correlated,
-- but result may be large to cache upfront;
-- leave it as is for now but bring it out to WITH if needed.
--
-- 2. we will ignore the final LIMIT 1 --- debugging certainly
-- should not be limited to just to one row.
--
-- 3. suppose A is indexed.
-- if there is WHERE condition on A already in the query,
-- should we still SARG_AGG A?
-- * if the condition can be tightened further (e.g., A > ...),
--   then we should, because a window may tighten it further than the original query.
-- * otherwise (e.g., A = ...), then we shouldn't.
--
-- 4. suppose the iid for the current query is (A, B).
-- strictly speaking, SARG_AGG(A) is unnecessary because it can be derived from the milestones,
-- but for now we include it for convenience for windowing and for potential pushdown.
-- * we need it for windowing because postgresql may fail to use index for ROW(...) >.
-- * we need it for pushdown because we need to infer condition on A alone.
-- for B, SARG_AGG(B) is generally needed (if B is indexed) because milestones are not ordered by B.
--
-- 5. when do we create a bloom filter?
-- if a subquery is parameterized by A and/or its result is compared with A,
-- then we create a bloom filter on A.
-- if multiple attributes are involved, we give them all to the same bloom filter.
--
-- select s_name, s_address
-- from supplier, nation
-- where s_suppkey in (
-- 		select ps_suppkey
-- 		from partsupp,
--             (select l_partkey agg_partkey,
-- 					l_suppkey agg_suppkey,
-- 					0.5 * sum(l_quantity) AS agg_quantity
-- 			from lineitem
-- 			where l_shipdate >= date '1996-01-01'
--             and l_shipdate < date '1996-01-01' + interval '1' year
-- 			group by l_partkey, l_suppkey
-- 			) agg_lineitem
-- 		where
-- 			agg_partkey = ps_partkey
-- 			and agg_suppkey = ps_suppkey
-- 			and ps_partkey in (
-- 				select p_partkey
-- 				from part
-- 				where p_name like '%%chocolate%%'
-- 			)
-- 			and ps_availqty > agg_quantity
-- 	)
-- 	and s_nationkey = n_nationkey
-- 	and n_name = 'CHINA'
-- order by s_name
-- LIMIT 1;

-- ********************************************************************************
-- context: agg_lineitem (subquery in FROM in a subquery)
-- *****
-- select l_partkey agg_partkey,
--     l_suppkey agg_suppkey,
-- 	0.5 * sum(l_quantity) AS agg_quantity
-- from lineitem
-- where l_shipdate >= date '1996-01-01'
-- and l_shipdate < date '1996-01-01' + interval '1' year
-- group by l_partkey, l_suppkey;

-- *****
-- query_id: agg_lineitem_join_filter
-- basically sorts the filtered lineitem table according to the group by
-- 1. milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_orderkey, l_linenumber) - 1,
        ROW(l_orderkey, l_linenumber),
        l_orderkey, l_partkey, l_suppkey, l_linenumber
    from lineitem
    where l_shipdate >= date '1996-01-01'
    and l_shipdate < date '1996-01-01' + interval '1' year
)
SELECT MIN(seq) AS seq,
    MIN_IID(iid) AS iid,
    COUNT(*) AS count,
    -- sargs:
    ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
    ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- 2. page fetch query
SELECT ROW(l_orderkey, l_linenumber) AS iid, -- iid
    -- provenance omitted since it's a single-table non-aggregate query
    *
from lineitem
where l_shipdate >= date '1996-01-01'
and l_shipdate < date '1996-01-01' + interval '1' year
-- sarg-windowing:
AND l_orderkey BETWEEN  %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN  %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN  %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN  %(l_linenumber,min)s AND %(l_linenumber,max)s
-- windowing:
AND ROW(l_orderkey, l_linenumber) >= %(iid,min)s
AND ROW(l_orderkey, l_linenumber) < %(iid,max)s
ORDER BY l_orderkey, l_linenumber;

-- 3. page fetch naive query 
SELECT ROW(l_orderkey, l_linenumber) AS iid, -- iid
    -- provenance omiitted since it's a single-table non-aggregate query
    *
FROM lineitem
ORDER BY l_orderkey, l_linenumber;

-- *****
-- query_id: agg_lineitem_output
-- 1. milestone query
WITH tmp(seq, iid, l_orderkey, l_partkey, l_suppkey, l_linenumber) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY l_partkey, l_suppkey) - 1 AS seq,
        ROW(l_partkey, l_suppkey) AS iid,
        -- sargs to be further grouped:
        ARRAY[MIN(l_orderkey), MAX(l_orderkey)] l_orderkey, ARRAY[MIN(l_partkey), MAX(l_partkey)] l_partkey, 
        ARRAY[MIN(l_suppkey), MAX(l_suppkey)] l_suppkey, ARRAY[MIN(l_linenumber), MAX(l_linenumber)] l_linenumber
    FROM lineitem
    WHERE l_shipdate >= date '1996-01-01'
    and l_shipdate < date '1996-01-01' + interval '1' year
    GROUP BY l_partkey, l_suppkey
)
SELECT MIN(seq) AS seq,
    MIN_IID(iid) AS iid,
    COUNT(*) AS count,
    -- srags:
    ARRAY[MIN(l_orderkey[1]), MAX(l_orderkey[2])] l_orderkey, ARRAY[MIN(l_partkey[1]), MAX(l_partkey[2])] l_partkey, 
    ARRAY[MIN(l_suppkey[1]), MAX(l_suppkey[2])] l_suppkey, ARRAY[MIN(l_linenumber[1]), MAX(l_linenumber[2])] l_linenumber
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- 2. page fetch query
select ROW(l_partkey, l_suppkey) iid, -- iid
    MIN_IID(ROW(ROW(l_orderkey, l_linenumber))) prov,  -- provenance
    l_partkey agg_partkey,
    l_suppkey agg_suppkey,
    0.5 * sum(l_quantity) AS agg_quantity
from lineitem
where l_shipdate >= date '1996-01-01'
and l_shipdate < date '1996-01-01' + interval '1' year
AND l_orderkey BETWEEN  %(l_orderkey,min)s AND %(l_orderkey,max)s
AND l_partkey BETWEEN  %(l_partkey,min)s AND %(l_partkey,max)s
AND l_suppkey BETWEEN  %(l_suppkey,min)s AND %(l_suppkey,max)s
AND l_linenumber BETWEEN  %(l_linenumber,min)s AND %(l_linenumber,max)s
AND ROW(l_partkey, l_suppkey) >= %(iid,min)s
AND ROW(l_partkey, l_suppkey) < %(iid,max)s
group by l_partkey, l_suppkey
ORDER BY l_partkey, l_suppkey;

-- 3. page fetch naive query 
select ROW(l_partkey, l_suppkey) iid, -- iid
    l_partkey agg_partkey,
    l_suppkey agg_suppkey,
    0.5 * sum(l_quantity) AS agg_quantity,
    MIN_IID(ROW(ROW(l_orderkey, l_linenumber))) prov  -- provenance
from lineitem
where l_shipdate >= date '1996-01-01'
and l_shipdate < date '1996-01-01' + interval '1' year
group by l_partkey, l_suppkey
ORDER BY l_partkey, l_suppkey;

-- ********************************************************************************
-- context: relevant_suppkey (subquery following s_suppkey IN)
-- *****
-- select ps_suppkey
-- from partsupp,
--     (select l_partkey agg_partkey,
--             l_suppkey agg_suppkey,
--             0.5 * sum(l_quantity) AS agg_quantity
--     from lineitem
--     where l_shipdate >= date '1996-01-01'
--     and l_shipdate < date '1996-01-01' + interval '1' year
--     group by l_partkey, l_suppkey
--     ) agg_lineitem
-- where
--     agg_partkey = ps_partkey
--     and agg_suppkey = ps_suppkey
--     and ps_partkey in (
--         select p_partkey
--         from part
--         where p_name like '%%chocolate%%'
--     )
--     and ps_availqty > agg_quantity;

-- *****
-- query_id: relevant_suppkey_output
-- 1. milestone query
WITH tmp(seq, iid, agg_lineitem_output_iid, ps_partkey, ps_suppkey, agg_partkey, agg_suppkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY ps_partkey) - 1 AS seq,
        ROW(ps_suppkey) AS iid,
        ROW(agg_partkey, agg_suppkey) AS agg_lineitem_output_iid,
        ps_partkey, ps_suppkey, agg_partkey, agg_suppkey
    from partsupp,
        (
            select l_partkey agg_partkey,
                    l_suppkey agg_suppkey,
                    0.5 * sum(l_quantity) AS agg_quantity
            from lineitem
            where l_shipdate >= date '1996-01-01'
            and l_shipdate < date '1996-01-01' + interval '1' year
            group by l_partkey, l_suppkey
        ) agg_lineitem
    where agg_partkey = ps_partkey
    and agg_suppkey = ps_suppkey
    and ps_partkey in (
        select p_partkey
        from part
        where p_name like '%%chocolate%%'
    )
    and ps_availqty > agg_quantity
)
SELECT MIN(seq) AS seq,
    MIN_IID(iid) AS iid,
    COUNT(*) AS count,
    ARRAY[MIN_IID(agg_lineitem_output_iid), MAX_IID(agg_lineitem_output_iid)] AS agg_lineitem_output_iid,
    -- sargs:
    ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
    ARRAY[MIN(agg_partkey), MAX(agg_partkey)] agg_partkey, ARRAY[MIN(agg_suppkey), MAX(agg_suppkey)] agg_suppkey,
    BLMFL(numeric_send(ps_partkey)) AS blmfl_ps_partkey -- for ps_partkey in (...)
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- 2. page fetch query
select ROW(ps_suppkey), -- iid
    -- provenance omitted as it is implied by iid
    ps_suppkey
from partsupp,
    (
        select l_partkey agg_partkey,
            l_suppkey agg_suppkey,
            0.5 * sum(l_quantity) AS agg_quantity
        from lineitem
        where l_shipdate >= date '1996-01-01'
        and l_shipdate < date '1996-01-01' + interval '1' year
        -- id pushdown
        AND ROW(l_partkey, l_suppkey) >= %(agg_lineitem_output,iid,min)s
        AND ROW(l_partkey, l_suppkey) < %(agg_lineitem_output,iid,max)s
        -- pushdown:
        AND l_orderkey BETWEEN  %(agg_lineitem_output,l_orderkey,min)s AND %(agg_lineitem_output,l_orderkey,max)s
        AND l_partkey BETWEEN  %(agg_lineitem_output,l_partkey,min)s AND %(agg_lineitem_output,l_partkey,max)s
        AND l_suppkey BETWEEN  %(agg_lineitem_output,l_suppkey,min)s AND %(agg_lineitem_output,l_suppkey,max)s
        AND l_linenumber BETWEEN  %(agg_lineitem_output,l_linenumber,min)s AND %(agg_lineitem_output,l_linenumber,max)s
        group by l_partkey, l_suppkey
    ) agg_lineitem
where
    agg_partkey = ps_partkey
    and agg_suppkey = ps_suppkey
AND BLMFL_TEST(%(blmfl_ps_partkey)s, numeric_send(ps_partkey))
AND ps_partkey in (
    select p_partkey
    from part
    where p_name like '%%chocolate%%'
    -- pushdown:
    AND p_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s
)
AND ps_availqty > agg_quantity
AND ps_partkey BETWEEN %(ps_partkey,min)s AND %(ps_partkey,max)s
AND ps_suppkey BETWEEN %(ps_suppkey,min)s AND %(ps_suppkey,max)s
AND ROW(ps_suppkey) >= %(iid,min)s
AND ROW(ps_suppkey) < %(iid,max)s
ORDER BY ps_suppkey;

-- 3. page fetch naive query 
select ROW(ps_suppkey), -- iid
    -- provenance omitted as it is implied by iid
    ps_suppkey
from partsupp,
    (select l_partkey agg_partkey,
            l_suppkey agg_suppkey,
            0.5 * sum(l_quantity) AS agg_quantity
    from lineitem
    where l_shipdate >= date '1996-01-01'
    and l_shipdate < date '1996-01-01' + interval '1' year
    group by l_partkey, l_suppkey
    ) agg_lineitem
where
    agg_partkey = ps_partkey
    and agg_suppkey = ps_suppkey
    and ps_partkey in (
        select p_partkey
        from part
        where p_name like '%%chocolate%%'
    )
    and ps_availqty > agg_quantity
ORDER BY ps_partkey, ps_suppkey, agg_partkey, agg_suppkey;

-- ********************************************************************************
-- context: Q20
-- *****
-- select s_name, s_address
-- from supplier, nation
-- where s_suppkey in (
-- 		select ps_suppkey
-- 		from partsupp,
--             (select l_partkey agg_partkey,
-- 					l_suppkey agg_suppkey,
-- 					0.5 * sum(l_quantity) AS agg_quantity
-- 			from lineitem
-- 			where l_shipdate >= date '1996-01-01'
--             and l_shipdate < date '1996-01-01' + interval '1' year
-- 			group by l_partkey, l_suppkey
-- 			) agg_lineitem
-- 		where
-- 			agg_partkey = ps_partkey
-- 			and agg_suppkey = ps_suppkey
-- 			and ps_partkey in (
-- 				select p_partkey
-- 				from part
-- 				where p_name like '%%chocolate%%'
-- 			)
-- 			and ps_availqty > agg_quantity
-- 	)
-- 	and s_nationkey = n_nationkey
-- 	and n_name = 'CHINA'
-- order by s_name;

-- *****
-- query_id: Q20_join_filter
-- before the final ordering
-- 1. milestone query
WITH tmp(seq, iid, relevant_suppkey_output_iid, s_suppkey, s_nationkey, n_nationkey, n_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_suppkey, n_nationkey) - 1 AS seq,
        ROW(s_suppkey, n_nationkey) AS iid,
        ROW(s_suppkey) AS relevant_suppkey_output_iid,
        s_suppkey, s_nationkey, n_nationkey, n_regionkey
    from supplier, nation
    where s_suppkey in (
            select ps_suppkey
            from partsupp,
                (select l_partkey agg_partkey,
                        l_suppkey agg_suppkey,
                        0.5 * sum(l_quantity) AS agg_quantity
                from lineitem
                where l_shipdate >= date '1996-01-01'
                and l_shipdate < date '1996-01-01' + interval '1' year
                group by l_partkey, l_suppkey
                ) agg_lineitem
            where
                agg_partkey = ps_partkey
                and agg_suppkey = ps_suppkey
                and ps_partkey in (
                    select p_partkey
                    from part
                    where p_name like '%%chocolate%%'
                )
                and ps_availqty > agg_quantity
        )
        and s_nationkey = n_nationkey
        and n_name = 'CHINA'
)
SELECT MIN(seq) AS seq,
    MIN_IID(iid) AS iid,
    COUNT(*) AS count,
    ARRAY[MIN_IID(relevant_suppkey_output_iid), MAX_IID(relevant_suppkey_output_iid)] AS relevant_suppkey_output_iid,
    -- sargs:
    ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
    ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey, 
    -- blmfs:
    BLMFL(numeric_send(s_suppkey)) AS blmfl_s_suppkey -- for s_suppkey in (...)
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- 2. page fetch query
SELECT ROW(s_suppkey, n_nationkey), -- iid
    -- provenance omitted as it is implied by iid
    *
from supplier, nation
where s_suppkey in (
        select ps_suppkey
        from partsupp,
            (
                select l_partkey agg_partkey,
                    l_suppkey agg_suppkey,
                    0.5 * sum(l_quantity) AS agg_quantity
                from lineitem
                where l_shipdate >= date '1996-01-01'
                and l_shipdate < date '1996-01-01' + interval '1' year
                -- id pushdown
                AND ROW(l_partkey, l_suppkey) >= %(agg_lineitem_output,iid,min)s
                AND ROW(l_partkey, l_suppkey) < %(agg_lineitem_output,iid,max)s
                -- pushdown:
                AND l_orderkey BETWEEN  %(agg_lineitem_output,l_orderkey,min)s AND %(agg_lineitem_output,l_orderkey,max)s
                AND l_partkey BETWEEN  %(agg_lineitem_output,l_partkey,min)s AND %(agg_lineitem_output,l_partkey,max)s
                AND l_suppkey BETWEEN  %(agg_lineitem_output,l_suppkey,min)s AND %(agg_lineitem_output,l_suppkey,max)s
                AND l_linenumber BETWEEN  %(agg_lineitem_output,l_linenumber,min)s AND %(agg_lineitem_output,l_linenumber,max)s
                group by l_partkey, l_suppkey
            ) agg_lineitem
        where
            agg_partkey = ps_partkey
            and agg_suppkey = ps_suppkey
            and ps_partkey in (
                select p_partkey
                from part
                where p_name like '%%chocolate%%'
                and p_partkey BETWEEN %(relevant_suppkey_output,ps_partkey,min)s AND %(relevant_suppkey_output,ps_partkey,max)s
            )
            and ps_availqty > agg_quantity
            -- pushdown:
            AND ps_partkey BETWEEN %(relevant_suppkey_output,ps_partkey,min)s AND %(relevant_suppkey_output,ps_partkey,max)s
            AND ps_suppkey BETWEEN %(relevant_suppkey_output,ps_suppkey,min)s AND %(relevant_suppkey_output,ps_suppkey,max)s
            AND ROW(ps_partkey) >= %(relevant_suppkey_output,iid,min)s
            AND ROW(ps_partkey) < %(relevant_suppkey_output,iid,max)s
    )
    and s_nationkey = n_nationkey
    and n_name = 'CHINA'
    -- sarg-windowing:
    AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
    AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
    AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
    AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
    -- windowing:
    AND ROW(s_suppkey, n_nationkey) >= %(iid,min)s
    AND ROW(s_suppkey, n_nationkey) < %(iid,max)s
    AND BLMFL_TEST(%(blmfl_s_suppkey)s, numeric_send(s_suppkey))
ORDER BY s_suppkey, n_nationkey;

-- 3. page fetch naive query
SELECT ROW(s_suppkey, n_nationkey), -- iid
    -- provenance omitted as it is implied by iid
    *
from supplier, nation
where s_suppkey in (
        select ps_suppkey
        from partsupp,
            (select l_partkey agg_partkey,
                    l_suppkey agg_suppkey,
                    0.5 * sum(l_quantity) AS agg_quantity
            from lineitem
            where l_shipdate >= date '1996-01-01'
            and l_shipdate < date '1996-01-01' + interval '1' year
            group by l_partkey, l_suppkey
            ) agg_lineitem
        where
            agg_partkey = ps_partkey
            and agg_suppkey = ps_suppkey
            and ps_partkey in (
                select p_partkey
                from part
                where p_name like '%%chocolate%%'
            )
            and ps_availqty > agg_quantity
    )
    and s_nationkey = n_nationkey
    and n_name = 'CHINA'
ORDER BY s_suppkey, n_nationkey;

-- *****
-- query_id: Q20_output
-- 1. milestone query
WITH tmp(seq, iid, relevant_suppkey_output_iid, s_suppkey, s_nationkey, n_nationkey, n_regionkey) AS (
    SELECT ROW_NUMBER() OVER (ORDER BY s_name, s_suppkey, n_nationkey) - 1 AS seq, -- note the old iid at the end
        ROW(s_name, s_suppkey, n_nationkey) AS iid, -- note the sort column at the beginning
        ROW(s_suppkey) AS relevant_suppkey_output_iid,
        s_suppkey, s_nationkey, n_nationkey, n_regionkey
    from supplier, nation
    where s_suppkey in (
            select ps_suppkey
            from partsupp,
                (select l_partkey agg_partkey,
                        l_suppkey agg_suppkey,
                        0.5 * sum(l_quantity) AS agg_quantity
                from lineitem
                where l_shipdate >= date '1996-01-01'
                and l_shipdate < date '1996-01-01' + interval '1' year
                group by l_partkey, l_suppkey
                ) agg_lineitem
            where
                agg_partkey = ps_partkey
                and agg_suppkey = ps_suppkey
                and ps_partkey in (
                    select p_partkey
                    from part
                    where p_name like '%%chocolate%%'
                )
                and ps_availqty > agg_quantity
        )
        and s_nationkey = n_nationkey
        and n_name = 'CHINA'
)
SELECT MIN(seq) AS seq,
    MIN_IID(iid) AS iid,
    COUNT(*) AS count,
    ARRAY[MIN_IID(relevant_suppkey_output_iid), MAX_IID(relevant_suppkey_output_iid)] relevant_suppkey_output_iid,
    -- sargs:
    ARRAY[MIN(s_suppkey), MAX(s_suppkey)] s_suppkey, ARRAY[MIN(s_nationkey), MAX(s_nationkey)] s_nationkey, 
    ARRAY[MIN(n_nationkey), MAX(n_nationkey)] n_nationkey, ARRAY[MIN(n_regionkey), MAX(n_regionkey)] n_regionkey
FROM tmp
GROUP BY seq / %(pg_sz)s
ORDER BY seq / %(pg_sz)s;

-- 2. page fetch query
SELECT ROW(s_name, s_suppkey, n_nationkey), -- iid
    -- provenance omitted as it is implied by iid
    s_name, s_address
from supplier, nation
where s_suppkey in (
        select ps_suppkey
        from partsupp,
            (
                select l_partkey agg_partkey,
                    l_suppkey agg_suppkey,
                    0.5 * sum(l_quantity) AS agg_quantity
                from lineitem
                where l_shipdate >= date '1996-01-01'
                and l_shipdate < date '1996-01-01' + interval '1' year
                -- id pushdown
                AND ROW(l_partkey, l_suppkey) >= %(agg_lineitem_output,iid,min)s
                AND ROW(l_partkey, l_suppkey) < %(agg_lineitem_output,iid,max)s
                -- pushdown:
                AND l_orderkey BETWEEN  %(agg_lineitem_output,l_orderkey,min)s AND %(agg_lineitem_output,l_orderkey,max)s
                AND l_partkey BETWEEN  %(agg_lineitem_output,l_partkey,min)s AND %(agg_lineitem_output,l_partkey,max)s
                AND l_suppkey BETWEEN  %(agg_lineitem_output,l_suppkey,min)s AND %(agg_lineitem_output,l_suppkey,max)s
                AND l_linenumber BETWEEN  %(agg_lineitem_output,l_linenumber,min)s AND %(agg_lineitem_output,l_linenumber,max)s
                group by l_partkey, l_suppkey
            ) agg_lineitem
        where
            agg_partkey = ps_partkey
            and agg_suppkey = ps_suppkey
            and ps_partkey in (
                select p_partkey
                from part
                where p_name like '%%chocolate%%'
                and p_partkey BETWEEN %(relevant_suppkey_output,ps_partkey,min)s AND %(relevant_suppkey_output,ps_partkey,max)s
            )
            and ps_availqty > agg_quantity
            -- pushdown:
            AND ps_partkey BETWEEN %(relevant_suppkey_output,ps_partkey,min)s AND %(relevant_suppkey_output,ps_partkey,max)s
            AND ps_suppkey BETWEEN %(relevant_suppkey_output,ps_suppkey,min)s AND %(relevant_suppkey_output,ps_suppkey,max)s
            AND ROW(ps_partkey) >= %(relevant_suppkey_output,iid,min)s
            AND ROW(ps_partkey) < %(relevant_suppkey_output,iid,max)s
    )
    and s_nationkey = n_nationkey
    and n_name = 'CHINA'
    -- sarg-windowing:
    -- AND s_name BETWEEN %(s_name,min)s AND %(s_name,max)s
    AND s_suppkey BETWEEN %(s_suppkey,min)s AND %(s_suppkey,max)s
    AND s_nationkey BETWEEN %(s_nationkey,min)s AND %(s_nationkey,max)s
    AND n_nationkey BETWEEN %(n_nationkey,min)s AND %(n_nationkey,max)s
    AND n_regionkey BETWEEN %(n_regionkey,min)s AND %(n_regionkey,max)s
    -- windowing:
    AND ROW(s_name, s_suppkey, n_nationkey) >= %(iid,min)s
    AND ROW(s_name, s_suppkey, n_nationkey) < %(iid,max)s
ORDER BY s_name, s_suppkey, n_nationkey;

-- 3. naive page fetch query
select ROW(s_name, s_suppkey, n_nationkey), s_name, s_address
from supplier, nation
where s_suppkey in (
        select ps_suppkey
        from partsupp,
            (select l_partkey agg_partkey,
                    l_suppkey agg_suppkey,
                    0.5 * sum(l_quantity) AS agg_quantity
            from lineitem
            where l_shipdate >= date '1996-01-01'
            and l_shipdate < date '1996-01-01' + interval '1' year
            group by l_partkey, l_suppkey
            ) agg_lineitem
        where
            agg_partkey = ps_partkey
            and agg_suppkey = ps_suppkey
            and ps_partkey in (
                select p_partkey
                from part
                where p_name like '%%chocolate%%'
            )
            and ps_availqty > agg_quantity
    )
    and s_nationkey = n_nationkey
    and n_name = 'CHINA'
order by s_name, s_suppkey, n_nationkey;
