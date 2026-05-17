--original query 
-- select
-- 	p_brand,
-- 	p_type,
-- 	p_size,
-- 	count(distinct ps_suppkey) as supplier_cnt
-- from
-- 	partsupp,
-- 	part
-- where
-- 	p_partkey = ps_partkey
-- 	and p_brand <> 'Brand#55  '
-- 	and p_type not like 'MEDIUM%%'
-- 	and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
-- 	and ps_suppkey not in (
-- 		select
-- 			s_suppkey
-- 		from
-- 			supplier
-- 		where
-- 			s_comment like '%%Customer%%Complaints%%'
-- 	)
-- group by
-- 	p_brand,
-- 	p_type,
-- 	p_size
-- order by
-- 	supplier_cnt desc,
-- 	p_brand,
-- 	p_type,
-- 	p_size
-- LIMIT 1;


--- join and filter --- 
-- query id: q16_join_filter
--milestone 
with tmp(seq, iid, ps_partkey, ps_suppkey, p_partkey) as (
    select row_number() over (order by ps_partkey, ps_suppkey, p_partkey) - 1,
    row(ps_partkey, ps_suppkey, p_partkey), ps_partkey, ps_suppkey, p_partkey
    from partsupp, part
    where p_partkey = ps_partkey
        and p_brand <> 'Brand#55  '
        and p_type not like 'MEDIUM%%'
        and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
        and ps_suppkey not in (
            select s_suppkey
            from supplier
            where s_comment like '%%Customer%%Complaints%%'
        )
)
select min(seq) seq, min_iid(iid) iid, count(*) count,
       ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, 
       ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
       ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey,
       BLMFL(numeric_send(ps_suppkey)) blmfl_filter
from tmp 
group by seq / %(pg_sz)s
order by seq / %(pg_sz)s;

-- page fetch query
select row(row(ps_partkey, ps_suppkey), row(p_partkey)), *
from partsupp, part
where p_partkey = ps_partkey
    and p_brand <> 'Brand#55  '
    and p_type not like 'MEDIUM%%'
    and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
    and ps_suppkey not in (
        select s_suppkey
        from supplier
        where s_comment like '%%Customer%%Complaints%%'
        and s_suppkey between %(ps_suppkey,min)s and %(ps_suppkey,max)s
    )
    and row(ps_partkey, ps_suppkey, p_partkey) >= %(iid,min)s
    and row(ps_partkey, ps_suppkey, p_partkey) < %(iid,max)s
    and p_partkey between %(p_partkey,min)s and %(p_partkey,max)s
    and ps_partkey between %(ps_partkey,min)s and %(ps_partkey,max)s
    and ps_suppkey between %(ps_suppkey,min)s and %(ps_suppkey,max)s
    and BLMFL_TEST(%(blmfl_filter)s, numeric_send(ps_suppkey))
order by ps_partkey, ps_suppkey, p_partkey; 

-- naive page fetch 
select row(p_partkey, ps_partkey, ps_suppkey), *
from partsupp, part
where p_partkey = ps_partkey
    and p_brand <> 'Brand#55  '
    and p_type not like 'MEDIUM%%'
    and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
    and ps_suppkey not in (
        select s_suppkey
        from supplier
        where s_comment like '%%Customer%%Complaints%%'
    )
ORDER BY ps_partkey, ps_suppkey, p_partkey;

--rename sarg_agg columns 

-- group by 
-- query id: q16_group
with tmp(seq, iid, ps_partkey, ps_suppkey, p_partkey, blmfl_filter) as (
    select row_number() over (order by p_brand, p_type, p_size) - 1,
    row(p_brand, p_type, p_size),
    ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, 
    ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
    ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey,
    BLMFL(numeric_send(ps_suppkey))
    from
        partsupp,
        part
    where
        p_partkey = ps_partkey
        and p_brand <> 'Brand#55  '
        and p_type not like 'MEDIUM%%'
        and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
        and ps_suppkey not in (
            select s_suppkey
            from supplier
            where s_comment like '%%Customer%%Complaints%%'
        )
    group by
        p_brand,
        p_type,
        p_size
)
select min(seq) seq, min_iid(iid) iid, count(*) count,
       ARRAY[MIN(ps_partkey[1]), MAX(ps_partkey[2])] ps_partkey, 
       ARRAY[MIN(ps_suppkey[1]), MAX(ps_suppkey[2])] ps_suppkey, 
       ARRAY[MIN(p_partkey[1]), MAX(p_partkey[2])] p_partkey,
       BLMFL_AGG(blmfl_filter) blmfl_filter
from tmp 
group by seq / %(pg_sz)s 
order by seq / %(pg_sz)s; 

-- page fetch query 
select row(p_brand, p_type, p_size) iid, 
       p_brand, p_type, p_size,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(p_partkey)))
from
    partsupp,
    part
where
    p_partkey = ps_partkey
    and p_brand <> 'Brand#55  '
    and p_type not like 'MEDIUM%%'
    and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
    and ps_suppkey not in (
        select s_suppkey
        from supplier
        where s_comment like '%%Customer%%Complaints%%'
        and s_suppkey between %(ps_suppkey,min)s and %(ps_suppkey,max)s
    )
    and row(p_brand, p_type, p_size) >= %(iid,min)s 
    and row(p_brand, p_type, p_size) < %(iid,max)s
    and p_partkey between %(p_partkey,min)s and %(p_partkey,max)s
    and ps_partkey between %(ps_partkey,min)s and %(ps_partkey,max)s
    and ps_suppkey between %(ps_suppkey,min)s and %(ps_suppkey,max)s
    and BLMFL_TEST(%(blmfl_filter)s, numeric_send(ps_suppkey))
group by p_brand, p_type, p_size
order by p_brand, p_type, p_size; 

-- naive page fetch 
select row(p_brand, p_type, p_size) iid, 
       p_brand, p_type, p_size,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(p_partkey)))
from
    partsupp,
    part
where
    p_partkey = ps_partkey
    and p_brand <> 'Brand#55  '
    and p_type not like 'MEDIUM%%'
    and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
    and ps_suppkey not in  (
        select s_suppkey
        from supplier
        where s_comment like '%%Customer%%Complaints%%'
    )
group by p_brand, p_type, p_size
order by p_brand, p_type, p_size; 

-- output 
-- query id: q16_output
with tmp(seq, iid, ps_partkey, ps_suppkey, p_partkey, blmfl_filter) as (
    select row_number() over (order by p_brand, p_type, p_size) - 1,
    row(p_brand, p_type, p_size),
    ARRAY[MIN(ps_partkey), MAX(ps_partkey)] ps_partkey, 
    ARRAY[MIN(ps_suppkey), MAX(ps_suppkey)] ps_suppkey, 
    ARRAY[MIN(p_partkey), MAX(p_partkey)] p_partkey,
    BLMFL(numeric_send(ps_suppkey))
    from
        partsupp,
        part
    where
        p_partkey = ps_partkey
        and p_brand <> 'Brand#55  '
        and p_type not like 'MEDIUM%%'
        and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
        and ps_suppkey not in (
            select s_suppkey
            from supplier
            where s_comment like '%%Customer%%Complaints%%'
        )
    group by p_brand, p_type, p_size
)
select min(seq) seq, min_iid(iid) iid, count(*) count,
       ARRAY[MIN(ps_partkey[1]), MAX(ps_partkey[2])] ps_partkey, 
       ARRAY[MIN(ps_suppkey[1]), MAX(ps_suppkey[2])] ps_suppkey, 
       ARRAY[MIN(p_partkey[1]), MAX(p_partkey[2])] p_partkey,
       BLMFL_AGG(blmfl_filter) blmfl_filter
from tmp 
group by seq / %(pg_sz)s 
order by seq / %(pg_sz)s; 

-- page fetch query 
select row(count(distinct ps_suppkey), p_brand, p_type, p_size) iid, 
       count(distinct ps_suppkey) as supplier_cnt, p_brand, p_type, p_size,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(p_partkey)))
from
    partsupp,
    part
where
    p_partkey = ps_partkey
    and p_brand <> 'Brand#55  '
    and p_type not like 'MEDIUM%%'
    and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
    and ps_suppkey not in (
        select s_suppkey
        from supplier
        where s_comment like '%%Customer%%Complaints%%'
        and s_suppkey between %(ps_suppkey,min)s and %(ps_suppkey,max)s
    )
    and row(p_brand, p_type, p_size) >= %(iid,min)s 
    and row(p_brand, p_type, p_size) < %(iid,max)s
    and p_partkey between %(p_partkey,min)s and %(p_partkey,max)s
    and ps_partkey between %(ps_partkey,min)s and %(ps_partkey,max)s
    and ps_suppkey between %(ps_suppkey,min)s and %(ps_suppkey,max)s
    and BLMFL_TEST(%(blmfl_filter)s, numeric_send(ps_suppkey))
group by p_brand, p_type, p_size
order by p_brand, p_type, p_size, supplier_cnt; 

-- naive page fetch 
select row(p_brand, p_type, p_size) iid, 
       p_brand, p_type, p_size, count(distinct ps_suppkey) as supplier_cnt,
       MIN_IID(ROW(ROW(ps_partkey, ps_suppkey), ROW(p_partkey)))
from
    partsupp,
    part
where
    p_partkey = ps_partkey
    and p_brand <> 'Brand#55  '
    and p_type not like 'MEDIUM%%'
    and p_size in (35, 42, 20, 26, 8, 1, 7, 2)
    and ps_suppkey not in  (
        select s_suppkey
        from supplier
        where s_comment like '%%Customer%%Complaints%%'
    )
group by p_brand, p_type, p_size
order by p_brand, p_type, p_size, supplier_cnt; 

