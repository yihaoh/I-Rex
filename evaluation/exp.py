import psycopg2
import statistics
import os
from typing import Any
import re
import ast
from copy import deepcopy
import json
import sys
import math

DBNAME = "tpch_small"
USER = "irex"
PASSWORD = "irex"
HOST = "localhost"
PORT = "5432"
THRESHOLD = 0.5
BLMFL_FPR = 0.6


def sanitize_record_type(iid: str) -> str:
    # () is usually surrounded by "", simply remove them
    # string is surrounded by "", need to change to ''
    # print("input: ", iid)
    phase1_result = re.sub(r'"\("', r"(", iid)
    phase1_result = re.sub(r'"\)"', r")", phase1_result)
    phase1_result = re.sub(r'"\(', r"ROW(", phase1_result)
    phase1_result = re.sub(r'\)"', r")", phase1_result)
    phase2_result = phase1_result.replace('"', "'")
    # print("output: ", phase2_result)
    date_pattern = re.compile(r"(?<!')(\d{4}-\d{2}-\d{2})(?!')")
    phase3_result = re.sub(date_pattern, r"'\1'", phase2_result)
    return phase3_result


def sanitize_record_type_for_tuple(iid: str) -> str:
    # () is usually surrounded by "", simply remove them
    # string is surrounded by "", need to change to ''
    phase1_result = re.sub(r'"\("', r"(", iid)
    phase1_result = re.sub(r'"\)"', r")", phase1_result)
    phase1_result = re.sub(r'"\(', r"(", phase1_result)
    phase1_result = re.sub(r'\)"', r")", phase1_result)
    phase2_result = phase1_result.replace('"', "'")  # .replace("\\", "")
    return phase2_result


def sanitize_blmfl(blmfl: str):
    # sample returned from psycopg2.execute: ("\\x6bf9798b593c1ba87b18f616c66ef415",128,50,2,49)
    # need to change " to '
    return blmfl.replace('"', "'").replace("\\\\", "\\")


def overlap_pr(r1: tuple, r2: tuple) -> float:
    start1, end1 = r1
    start2, end2 = r2

    # Check that range1 is contained within range2
    if not (start2 <= start1 and end1 <= end2):
        raise ValueError("Range1 must be fully contained within Range2.")
    
    # Compute the lengths of the ranges
    length1 = abs(end1 - start1)
    length2 = abs(end2 - start2)

    # Calculate the percentage
    percentage = length1 / length2
    return percentage


def remove_between_pattern(query: str, var1: str, var2: str, var3: str) -> str:
    pattern = re.compile(
        rf"^\s*(where|and)\s+{re.escape(var1)}\s+between\s+%\({re.escape(var2)}\)s\s+and\s+%\({re.escape(var3)}\)s\s*(--.*)?$",
        re.IGNORECASE,
    )

    # Split the query into lines
    lines = query.splitlines()

    cleaned_lines = []

    for line in lines:
        # Match the line
        match = pattern.match(line)
        if match:
            # If the match starts with 'where', keep the 'where' keyword
            if match.group(1).lower() == "where":
                cleaned_lines.append("where")
        else:
            # If no match, keep the line as is
            cleaned_lines.append(line)

    # Join the remaining lines back into a single string
    cleaned_query = "\n".join(cleaned_lines).strip()

    return cleaned_query



def remove_blmfl_test_line(sql_query):
    # Split the query into lines
    lines = sql_query.splitlines()

    # Use a regular expression to match lines with 'BLMFL_TEST' (case-insensitive)
    pattern = re.compile(r'BLMFL_TEST', re.IGNORECASE)

    # Filter out lines that match the pattern
    filtered_lines = [line for line in lines if not pattern.search(line)]

    # Join the filtered lines back into a single string
    return '\n'.join(filtered_lines)


# =================== independent query test, no WITH ===================
class SingleTest:
    def __init__(
        self,
        id: str,
        mst_query: str,
        pg_query: str,
        naive_query: str,
        page_size: int = 50,
        measure_num: int = 1,
        cache_page_result: bool = False,
        db_config: dict = {"db": DBNAME, "user": USER, "password": PASSWORD, "host": HOST, "port": PORT},
        threshold: float = THRESHOLD,
        blmfl_fpr: float = BLMFL_FPR,
    ):
        self.id = id
        self.mst_query = mst_query
        self.pg_query = pg_query
        self.naive_query = naive_query
        self.pg_sz = page_size
        self.measure_num = measure_num
        self.cache_pg_res = cache_page_result
        self.db_config = db_config
        self.threshold = threshold
        self.blmfl_fpr = blmfl_fpr
        self.conn = psycopg2.connect(
            dbname=self.db_config["db"],
            user=self.db_config["user"],
            password=self.db_config["password"],
            host=self.db_config["host"],
            port=self.db_config["port"],
        )

        # things to be measured
        self.milestone_schema: list[str] = []
        self.milestones: list[dict[str, Any]] = []
        self.mst_query_time = 0
        self.pg_query_time: list[float] = []
        self.naive_page_query_time: list[float] = []
        self.naive_query_time = 0

        # optional
        self.pg_results = []
        self.naive_results = []

        # reproduce a file with instantiated queries
        # order: one milestone query, 3 page fetch queries, 3 naive page queries
        self.instantiated_queries: list[str] = []
        self.milestone_sz = 0
        self.result_sz = 0
        self.result_row_count = 0
        self.collect_pre_results()
        print(f"Query {self.id} setup finished.")

    def collect_pre_results(self):
        # collect naive query results and adjust page size
        cur = self.conn.cursor()
        os.system("echo 3 > /proc/sys/vm/drop_caches")
        # print(self.naive_query)
        cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {self.naive_query}")
        self.naive_query_time = self._get_time_from_explain(cur.fetchall())
        cur.execute(self.naive_query)
        self.result_row_count = cur.rowcount
        if cur.rowcount <= self.pg_sz * 3:
            self.pg_sz = cur.rowcount // 4  # we need at least 4 pages
        if self.pg_sz <= 1:
            print(f"Query {self.id} needs more pages")
            self.pg_sz += 1
        self.result_sz = sys.getsizeof(cur.fetchone()) * cur.rowcount / 1000**2
        cur.close()

    def _get_time_from_explain(self, cursor_res) -> float:
        return cursor_res[0][0][0]["Execution Time"]

    def run_milestone_query(self) -> None:
        # run milestone queries 3 times for time measurements
        # cache the milestones
        cur = self.conn.cursor()
        cur.execute("SET blmfl.bloomfilter_bitsize TO 1024")
        cur.execute("SET blmfl.estimated_count TO %s", (self.pg_sz,))
        cur.execute("SET blmfl.num_hashes TO %s", (int(1024 / self.pg_sz * math.log(2)),))
        times = []
        for i in range(self.measure_num):
            cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {self.mst_query}", {"pg_sz": self.pg_sz})
            times.append(self._get_time_from_explain(cur.fetchall()))
        cur.execute(self.mst_query, {"pg_sz": self.pg_sz})
        self.milestone_schema = [x.name for x in cur.description]
        self.mst_query_time = statistics.median(times)
        self.milestones = [{name: t[i] for i, name in enumerate(self.milestone_schema)} for t in cur.fetchall()]
        self.milestone_sz = sys.getsizeof(self.milestones[0]) * len(self.milestones) / 1000**2
        self.instantiated_queries.append(cur.mogrify(self.mst_query, {"pg_sz": self.pg_sz}).decode("utf-8"))
        cur.close()

    def _get_milestones_cur_context(self, page: str = "mid") -> tuple[dict[str, Any], dict[str, Any]]:
        if self.pg_sz < 3:
            return {}, {}
        idx = None
        if page == "head":
            idx = 0
        elif page == "mid":
            idx = (len(self.milestones) - 1) // 2
        elif page == "tail":
            idx = len(self.milestones) - 2
        return self.milestones[idx], self.milestones[idx + 1]  # complete milestone, include seq num

    def find_parameters(self, query: str) -> list[str]:
        # find all %()s parameters in the query string, return them without %()s
        pattern = r"%\((.*?)\)s"
        matches = re.findall(pattern, query)
        return matches

    def run_page_query(
        self, dependencies: dict[str, tuple[dict[str, Any], dict[str, Any]]], cache: dict = {}, stats: dict = {}
    ) -> None:
        if self.pg_sz < 3:
            return
        # prepare arguments and execute query
        # note that for regular columns we can leverage psycopg parameterization
        # for iid columns we can only do replacement since psycopg does not accommodate that
        query = self.pg_query
        args = {}
        for k, v in cache.items():
            args[k] = v

        lower = dependencies[self.id][0]
        upper = dependencies[self.id][1]

        for raw_params in self.find_parameters(query):
            params = [x.strip() for x in raw_params.split(",")]
            # raw_params not surrounded by %()s
            if raw_params == "cached_scalar":
                args[raw_params] = cache[raw_params]
            elif "pg_sz" in raw_params:
                args["pg_sz"] = self.pg_sz
            elif "blmfl" in raw_params:
                if len(params) == 1:  # current context blmfl
                    try:
                        cur = self.conn.cursor()
                        cur.execute(f"SELECT BLMFL_FPR({sanitize_blmfl(lower[raw_params])})")
                        fpr = cur.fetchone()[0]
                        cur.close()
                        # print("fpr: ", fpr)
                        if fpr > self.blmfl_fpr:
                            print("removing bloom filter due to FPR")
                            query = remove_blmfl_test_line(query)
                        else:
                            query = query.replace(f"%({raw_params})s", sanitize_blmfl(lower[raw_params]))
                    except Exception as e:
                        print(e)
                        cur.close()
                        self.conn.reset()
                        query = query.replace(f"%({raw_params})s", sanitize_blmfl(lower[raw_params]))
                else:  # dependent context blmfl
                    try:
                        cur = self.conn.cursor()
                        cur.execute(f"SELECT BLMFL_FPR({sanitize_blmfl(dependencies[params[0]][0][params[1]])})")
                        fpr = cur.fetchone()[0]
                        cur.close()
                        print("fpr: ", fpr)
                        if fpr > self.blmfl_fpr:
                            print("removing bloom filter due to FPR")
                            query = remove_blmfl_test_line(query)
                        else:
                            query = query.replace(f"%({raw_params})s", sanitize_blmfl(dependencies[params[0]][0][params[1]]))
                    except Exception as e:
                        print(e)
                        cur.close()
                        self.conn.reset()
                        query = query.replace(f"%({raw_params})s", sanitize_blmfl(dependencies[params[0]][0][params[1]]))
            elif len(params) == 2:  # current context: %(column_name,min/max)s
                if "iid" in params:
                    query = (
                        query.replace(f"%({raw_params})s", f"ROW{sanitize_record_type(lower['iid'])}")
                        if "min" in params
                        else query.replace(f"%({raw_params})s", f"ROW{sanitize_record_type(upper['iid'])}")
                    )
                else:
                    # TODO: check range
                    # print(params[0], overlap_pr(tuple(stats[params[0]]), tuple(lower[params[0]])))
                    if (
                        params[0] in stats
                        and overlap_pr(tuple(lower[params[0]]), tuple(stats[params[0]])) > self.threshold
                    ):
                        query = remove_between_pattern(query, params[0], f"{params[0]},min", f"{params[0]},max")
                    args[raw_params] = lower[params[0]][0] if "min" in params else lower[params[0]][1]
            else:  # dependent context, para: %(context_id,column_name,min/max)s
                if "iid" in params:
                    query = (
                        query.replace(
                            f"%({raw_params})s",
                            f"ROW{sanitize_record_type(dependencies[params[0]][0]['iid'])}",
                        )
                        if "min" in params
                        else query.replace(
                            f"%({raw_params})s",
                            f"ROW{sanitize_record_type(dependencies[params[0]][1]['iid'])}",
                        )
                    )
                else:
                    # TODO: check range
                    if (
                        params[1] in stats
                        and overlap_pr(tuple(dependencies[params[0]][0][params[1]]), tuple(stats[params[1]]))
                        > self.threshold
                    ):
                        query = remove_between_pattern(
                            query, params[1], f"{params[0]},{params[1]},min", f"{params[0]},{params[1]},max"
                        )
                    args[raw_params] = (
                        dependencies[params[0]][0][params[1]][0]
                        if "min" in params
                        else dependencies[params[0]][0][params[1]][1]
                    )
        # print("args: ", args)
        # print(query)

        cur = self.conn.cursor()
        if "q18" in self.id:
            cur.execute("SET enable_nestloop to false")
        self.instantiated_queries.append(cur.mogrify(query, args).decode("utf-8"))
        if self.cache_pg_res:
            cur.execute(query, args)
            self.pg_results.append(cur.fetchall())

        times = []
        for i in range(self.measure_num):
            cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {query}", args)
            times.append(self._get_time_from_explain(cur.fetchall()))

        self.pg_query_time.append(statistics.median(times))
        cur.close()

    def run_naive_query_all_pages(self):
        if self.pg_sz < 3:
            return
        cur = self.conn.cursor()
        for i in range(len(self.milestones) - 1):
            query = f"{self.naive_query} \nOFFSET {i * self.pg_sz} LIMIT {self.pg_sz}"
            # os.system("echo 3 > /proc/sys/vm/drop_caches")
            cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {query}")
            self.naive_page_query_time.append(self._get_time_from_explain(cur.fetchall()))
        cur.close()
        return

    def run_naive_query(self):
        if self.pg_sz < 3:
            return
        # three different pages
        head_page_query = f"{self.naive_query} \nOFFSET 0 LIMIT {self.pg_sz}"
        mid_page_query = (
            f"{self.naive_query} \nOFFSET {self.pg_sz * (len(self.milestones) - 1) // 2} LIMIT {self.pg_sz}"
        )
        last_page_query = f"{self.naive_query} \nOFFSET {self.pg_sz * (len(self.milestones) - 2)} LIMIT {self.pg_sz}"

        cur = self.conn.cursor()
        self.instantiated_queries.append(head_page_query)
        self.instantiated_queries.append(mid_page_query)
        self.instantiated_queries.append(last_page_query)
        if self.cache_pg_res:
            cur.execute(head_page_query)
            self.naive_results.append(cur.fetchall())
            cur.execute(mid_page_query)
            self.naive_results.append(cur.fetchall())
            cur.execute(last_page_query)
            self.naive_results.append(cur.fetchall())

        times = []
        for i in range(self.measure_num):
            os.system("echo 3 > /proc/sys/vm/drop_caches")
            cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {head_page_query}")
            times.append(self._get_time_from_explain(cur.fetchall()))
        self.naive_page_query_time.append(statistics.median(times))

        for i in range(self.measure_num):
            os.system("echo 3 > /proc/sys/vm/drop_caches")
            cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {mid_page_query}")
            times.append(self._get_time_from_explain(cur.fetchall()))
        self.naive_page_query_time.append(statistics.median(times))

        for i in range(self.measure_num):
            os.system("echo 3 > /proc/sys/vm/drop_caches")
            cur.execute(f"EXPLAIN (ANALYZE, FORMAT JSON) {last_page_query}")
            times.append(self._get_time_from_explain(cur.fetchall()))
        self.naive_page_query_time.append(statistics.median(times))
        cur.close()

    def reset(self):
        self.conn.reset()
        # things to be measured
        self.milestone_schema = []
        self.milestones: list[dict[str, Any]] = []
        self.mst_query_time = None
        self.pg_query_time = []
        self.naive_page_query_time = []

        # optional
        self.pg_results = []
        self.naive_results = []

        # reproduce a file with instantiated queries
        self.instantiated_queries: list[str] = []


# ========================== Test Manager ==========================
class TestManager:
    def __init__(
        self,
        filename: str,
        page_size: int = 50,
        db_config: dict = {"db": DBNAME, "user": USER, "password": PASSWORD, "host": HOST, "port": PORT},
        threshold: float = THRESHOLD,
        blmfl_fpr: float = BLMFL_FPR,
        stats: dict = {}
    ):
        self.filename = filename
        self.single_tests: list[SingleTest] = []
        self.id_to_test: dict[str, SingleTest] = {}
        self.cache: dict = {}
        self.stats: dict = stats
        query_groups, query_ids = self.extract_sql_queries(filename)
        self.dependencies: dict[str, list[str]] = {}
        self.db_config = db_config
        self.threshold = threshold
        self.blmfl_fpr = blmfl_fpr
        for i, q in enumerate(query_groups):
            t = SingleTest(query_ids[i], q[0], q[1], q[2], page_size, db_config=db_config, threshold=self.threshold, blmfl_fpr=self.blmfl_fpr)
            self.single_tests.append(t)
            self.id_to_test[query_ids[i]] = t
        
        if not stats:
            self.get_statistics()
        self.extract_context_dependencies()
        self.extract_cached_scalar_query(filename)

    def get_statistics(self):
        table_to_col = {
            "customer": ["c_custkey", "c_nationkey"],
            "lineitem": ["l_orderkey", "l_partkey", "l_suppkey", "l_linenumber"],
            "nation": ["n_nationkey", "n_regionkey"],
            "orders": ["o_orderkey", "o_custkey"],
            "part": ["p_partkey"],
            "partsupp": ["ps_partkey", "ps_suppkey"],
            "region": ["r_regionkey"],
            "supplier": ["s_suppkey", "s_nationkey"],
        }
        conn = psycopg2.connect(
            dbname=self.db_config["db"],
            user=self.db_config["user"],
            password=self.db_config["password"],
            host=self.db_config["host"],
            port=self.db_config["port"],
        )
        cur = conn.cursor()
        for k, v in table_to_col.items():
            cur.execute(f"SELECT {','.join([f'SARG_AGG({c})' for c in v])} FROM {k}")
            for c, r in zip(v, cur.fetchall()[0]):
                self.stats[c] = r
        cur.close()
        conn.close()

    def __getitem__(self, key):
        return self.single_tests[key]

    def close(self):
        for t in self.single_tests:
            try:
                t.conn.close()
            except:
                continue

    def reset(self):
        for t in self.single_tests:
            t.reset()

    def run_milestone_queries(self):
        for t in self.single_tests:
            print(f"processing milstone query {t.id} ===================")
            t.run_milestone_query()

    def _get_parameters_recursive(self, qid, q_lower, q_upper):
        dep = {}
        dep[qid] = (q_lower, q_upper)
        if qid not in self.dependencies:
            return dep
        for qq_id in self.dependencies[qid]:
            if f"{qq_id}_iid" not in q_lower or f"{qq_id}_iid" not in q_upper:
                # go to next one, this context is not directly nested, pursue other path
                print(f"{qq_id}_iid not in context {qid}")
                continue
            # get qq_iid, note that ARRAY[ROW, ROW] will return str in Python space
            raw_iid_range_lower = [x.strip() for x in q_lower[f"{qq_id}_iid"][1:-1].split(",")]
            raw_iid_range_upper = [x.strip() for x in q_upper[f"{qq_id}_iid"][1:-1].split(",")]
            # print(q_lower[f"{qq_id}_iid"][0], sanitize_record_type(q_lower[f"{qq_id}_iid"][0]))
            # print(qq_id)
            # print(q_lower[f"{qq_id}_iid"])
            # print(q_upper[f"{qq_id}_iid"][1:-1])
            # print(",".join(raw_iid_range_lower[: len(raw_iid_range_lower) // 2]))
            # print(",".join(raw_iid_range_upper[: len(raw_iid_range_upper) // 2]))
            qq_lower_iid = ast.literal_eval(
                sanitize_record_type_for_tuple(",".join(raw_iid_range_lower[: len(raw_iid_range_lower) // 2]))
            )
            qq_upper_iid = ast.literal_eval(
                sanitize_record_type_for_tuple(",".join(raw_iid_range_upper[: len(raw_iid_range_upper) // 2]))
            )
            # go into qq_id and search for right interval & merge
            qq_mst = self.id_to_test[qq_id].milestones
            # 2. binary search through self.milestones to find lower and upper
            l = 0
            r = len(qq_mst) - 1
            while l < r - 1:
                mid = (l + r) // 2
                if ast.literal_eval(qq_mst[mid]["iid"]) < qq_lower_iid:
                    l = mid
                else:
                    r = mid
            qq_lower = deepcopy(qq_mst[l])
            lo = l

            l = 0
            r = len(qq_mst) - 1
            while l < r:
                mid = (l + r) // 2
                if ast.literal_eval(qq_mst[mid]["iid"]) < qq_upper_iid:
                    l = mid + 1
                else:
                    r = mid
            qq_upper = deepcopy(qq_mst[l])
            hi = l

            # 3. merge all milestones between [lower and upper)
            i = lo + 1
            while i < hi:
                tp = qq_mst[i]
                for col in self.id_to_test[qq_id].milestone_schema:
                    if type(tp[col]) is list and "iid" not in col:  # it's range, need to merge
                        qq_lower[col][0] = min(qq_lower[col][0], qq_mst[i][col][0])
                        qq_lower[col][1] = max(qq_lower[col][1], qq_mst[i][col][1])
                    elif "blmfl" in col:  # bloom filter condition
                        qq_lower[col] = (
                            f"BLMFL_MERGE({sanitize_blmfl(qq_mst[i][col])}, {sanitize_blmfl(qq_lower[col])})"
                        )
                i += 1
            # recurse
            dep.update(self._get_parameters_recursive(qq_id, qq_lower, qq_upper))
        return dep
    
    def run_page_queries_all_pages(self):
        for t in self.single_tests:
            print(f"processing query {t.id} ===================")
            for i in range(len(t.milestones) - 1):
                t.run_page_query({t.id: (t.milestones[i], t.milestones[i+1])}, self.cache, self.stats)

    def run_page_queries(self):
        for t in self.single_tests:
            print(f"processing query {t.id} ===================")
            for p in ["head", "mid", "tail"]:
                if t.id not in self.dependencies or not self.dependencies[t.id]:
                    t_lower, t_upper = t._get_milestones_cur_context(p)
                    t.run_page_query({t.id: (t_lower, t_upper)}, self.cache, self.stats)
                else:
                    dep = {}
                    t_lower, t_upper = t._get_milestones_cur_context(p)
                    # assume potentially multiple levels of nesting, and multiple dependent context
                    dep = self._get_parameters_recursive(t.id, t_lower, t_upper)
                    # print("dep: ", dep)
                    t.run_page_query(dep, self.cache, self.stats)

    def run_naive_queries_all_pages(self):
        for t in self.single_tests:
            t.run_naive_query_all_pages()

    def run_naive_queries(self):
        for t in self.single_tests:
            t.run_naive_query()

    def extract_cached_scalar_query(self, sql_file_path: str) -> None:
        cached_query = None
        is_inside_cached_section = False
        is_inside_comment_block = False
        current_query_lines = []

        # Regex patterns
        cached_query_pattern = re.compile(r"-- cached query")
        start_comment_block_pattern = re.compile(r"/\*")
        end_comment_block_pattern = re.compile(r"\*/")

        with open(sql_file_path, "r") as file:
            for line in file:
                # Strip leading/trailing whitespace
                stripped_line = line.strip()

                # Detect the start of a cached query section
                if cached_query_pattern.match(stripped_line):
                    is_inside_cached_section = True  # Start looking for the comment block
                    continue  # Skip to the next line

                # If we're inside the cached query section and the comment block starts, start capturing
                if is_inside_cached_section and start_comment_block_pattern.match(stripped_line):
                    is_inside_comment_block = True
                    continue  # Skip this line, it only starts the comment block

                # Capture lines inside the comment block
                if is_inside_comment_block:
                    # Check if this line ends the comment block
                    if end_comment_block_pattern.search(stripped_line):
                        is_inside_comment_block = False  # End of the comment block
                        is_inside_cached_section = False  # Reset the section flag since we expect only 1 query
                        # Join the collected lines into a single SQL query and break
                        cached_query = "\n".join(current_query_lines).strip()
                        break  # Since we assume one query, we can exit the loop
                    else:
                        current_query_lines.append(line.strip())  # Accumulate lines inside the comment block
        if not cached_query:
            return
        conn = psycopg2.connect(
            dbname=self.db_config["db"],
            user=self.db_config["user"],
            password=self.db_config["password"],
            host=self.db_config["host"],
            port=self.db_config["port"],
        )
        cur = conn.cursor()
        cur.execute(cached_query)
        self.cache["cached_scalar"] = cur.fetchall()[0][0]
        cur.close()
        conn.close()

    def extract_context_dependencies(self):
        for t in self.single_tests:
            params = t.find_parameters(t.pg_query)
            tp = set()
            for p in params:
                p_split = p.split(",")
                if len(p_split) == 3:
                    tp.add(p_split[0])
            if len(tp) > 0:
                self.dependencies[t.id] = list(tp)

    def extract_sql_queries(self, sql_file_path: str) -> tuple[list[list[str]], list[str]]:
        queries = []
        query_ids = []
        current_queries = []
        current_query_id = None
        current_query_lines = []

        # Regular expression for matching the comment with the query id
        query_id_pattern = re.compile(r"--\s*query[_\s]id:\s*(.+)")  # re.compile(r"-- query id: (.+)")

        # Regex to remove inline comments (single-line `--` and block `/* */`)
        inline_comment_pattern = re.compile(r"(--.*$|/\*.*?\*/)")

        with open(sql_file_path, "r") as file:
            for line in file:
                # Strip leading/trailing whitespace
                stripped_line = line.strip()

                # Check if the line is a query id comment
                query_id_match = query_id_pattern.match(stripped_line)
                if query_id_match:
                    # If there's a current group, finalize it before starting a new one
                    if current_queries and current_query_id:
                        queries.append(current_queries)
                        query_ids.append(current_query_id)

                    # Start a new query group
                    current_queries = []
                    current_query_id = query_id_match.group(1)
                    current_query_lines = []  # Reset for next group of queries
                    continue  # Skip to next line since this is a comment

                # Ignore random comments (line comments starting with --)
                if stripped_line.startswith("--"):
                    continue

                # Remove inline comments from the SQL line
                cleaned_line = inline_comment_pattern.sub(
                    "", line
                ).rstrip()  # Preserve the original line format minus the comment

                # If the line is not empty, accumulate it into the current query
                if cleaned_line:
                    current_query_lines.append(cleaned_line)

                # If the line ends with a semicolon, it marks the end of a query
                if cleaned_line.endswith(";"):
                    # Join the multi-line query into its original format, preserving newlines
                    full_query = "\n".join(current_query_lines).strip()[:-1]
                    current_queries.append(full_query)
                    current_query_lines = []  # Reset for next query

                # When a group of 3 queries is collected, finalize it
                if len(current_queries) == 3:
                    queries.append(current_queries)
                    query_ids.append(current_query_id)
                    current_queries = []  # Reset for next group
                    current_query_id = None

        # If there are remaining queries in the last group, finalize it
        if current_queries and current_query_id:
            queries.append(current_queries)
            query_ids.append(current_query_id)

        return queries, query_ids

    def export_instantiated_queries(self, filepath="") -> None:
        subtitles = [
            "-- milestone query\n",
            "-- head page query\n",
            "-- mid page query\n",
            "-- tail page query\n",
            "-- head naive query\n",
            "-- mid naive query\n",
            "-- tail naive query\n",
        ]
        filename_comp = self.filename.split(".")
        with open(f"{filepath}/{filename_comp[0].split('/')[-1]}-instantiated.sql", "w+") as f:
            for i, t in enumerate(self.single_tests):
                f.write(f"-- ========= Query ID: {t.id} =========\n")
                for j, q in enumerate(t.instantiated_queries):
                    f.write(subtitles[j])
                    f.write(f"{q};\n\n")
                f.write("\n\n")
        return
    
    def export_instantiated_queries_special(self, filepath="") -> None:
        filename_comp = self.filename.split(".")
        with open(f"{filepath}/{filename_comp[0].split('/')[-1]}-instantiated.sql", "w+") as f:
            for i, t in enumerate(self.single_tests):
                f.write(f"-- ========= Query ID: {t.id} =========\n")
                for j, q in enumerate(t.instantiated_queries):
                    if j == 0:
                        f.write("-- milestone query\n")
                    else:
                        f.write(f"-- page {j} query\n")
                    f.write(f"{q};\n\n")
                f.write("\n\n")
        return

    def export_results(self, filepath="") -> None:
        filename_comp = self.filename.split(".")
        json_obj = {}
        for t in self.single_tests:
            json_obj[t.id] = {
                "page_size": t.pg_sz,
                "milestone_query_time": t.mst_query_time,
                "naive_query_time": t.naive_query_time,
                "page_query_time": t.pg_query_time,
                "naive_page_query_time": t.naive_page_query_time,
                "query_output_tuples": t.result_row_count,
                "query_output_size_in_MB": t.result_sz,
                "milestone_tuples": len(t.milestones),
                "milestone_size_in_MB": t.milestone_sz,
            }
        with open(f"{filepath}/{filename_comp[0].split('/')[-1]}-result.json", "w+") as f:
            json.dump(json_obj, f, indent=4)
