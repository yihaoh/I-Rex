import argparse
import os
from exp import *
import time
import psycopg2

def get_statistics(db_config):
    res = {}
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
        dbname=db_config["db"],
        user=db_config["user"],
        password=db_config["password"],
        host=db_config["host"],
        port=db_config["port"],
    )
    cur = conn.cursor()
    for k, v in table_to_col.items():
        cur.execute(f"SELECT {','.join([f'ARRAY[MIN({c}), MAX({c})]' for c in v])} FROM {k}")
        for c, r in zip(v, cur.fetchall()[0]):
            res[c] = r
    cur.close()
    conn.close()
    return res

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--pg_sz", type=int, default=50)
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--db", type=str, default="tpch1")
    parser.add_argument("--user", type=str, default="irex")
    parser.add_argument("--password", type=str, default="irex")
    parser.add_argument("--host", type=str, default="localhost")
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--threshold", type=float, default=0.3)
    parser.add_argument("--blmfl_fpr", type=float, default=0.5)
    hp = parser.parse_args()

    output_path = f"results_special/s{hp.scale}_p{hp.pg_sz}"
    os.makedirs(output_path, exist_ok=True)
    db_config = {"db": hp.db, "user": hp.user, "password": hp.password, "host": hp.host, "port": hp.port}
    print("Getting stats first...")
    stats = get_statistics(db_config)
    process_time = []

    for filename in os.listdir("queries_for_test_special"):

        print(f"=============== Processing {filename} ================")
        start = time.time()
        tm = TestManager(f"queries_for_test_special/{filename}", hp.pg_sz, db_config, hp.threshold, hp.blmfl_fpr, stats=stats)
        print(f"Start milestone query")
        tm.run_milestone_queries()

        print(f"Start page query")
        tm.run_page_queries_all_pages()

        print(f"Start naive query")
        tm.run_naive_queries_all_pages()
        tm.export_results(output_path)
        tm.export_instantiated_queries_special(output_path)
        end = time.time()
        process_time.append(end - start)
        print(f"{filename} total time: {end - start}")

        with open(f"{output_path}/runtimes.json", "w+") as f:
            json.dump(process_time, f, indent=4)
