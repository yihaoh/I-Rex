# Evaluation of I-Rex

This repo contains the necessary scripts/queries to reproduce the experiment results in the paper. We assume the OS is Ubuntu.

## PostgreSQL Setup
First, install PostgreSQL along with some necessary dependencies.
```
sudo bash system_setup.sh
```

Go into the Postgres and create a superuser for testing:
```
CREATE USER irex WITH SUPERUSER ENCRYPTED PASSWORD 'irex';
```

# Bloom Filter Extension
To build and install necessary UDFs in an extension, go to [sargsum](../hnrq-db/sargsum) and execute the following:

```
sudo make clean
make
sudo make install
```

Now go into the database and execute:
```
CREATE EXTENSION BLMFL;
```


## Database Setup
Use [tpch-dbgen](https://github.com/databricks/tpch-dbgen) to generate the database instance. Move all `*.tbl` files under `tpch-setup` folder, and run `bash setup.sh` to sanitize the `tbl` files. Under `tpch-setup` folder, execute the following commands (replace `$dbname` with the database you wish to load):

```
mkdir data && mv *.tbl data
psql -d $dbname -af create.sql
psql -d $dbname -af load.sql
psql -d $dbname -af index.sql
```


## TPC-H Queries
All TPC-H queries are instantiated with reasonable parameters (see details in `queries_for_test`). In addition, to test Bloom Filter and Predicate Pushdown alone, we pick two queries and they are under `queries_for_test_special`. Each table has three queries: milestone query, page query and table query. 

## Running Test Scripts
Here are the commands to reproduce the test for all queries (as shown in the full technical report appendix):
```
sudo python query_test.py --scale 1  --pg_sz 50 --db tpch1
sudo python query_test.py --scale 1  --pg_sz 100 --db tpch1
sudo python query_test.py --scale 1  --pg_sz 200 --db tpch1

sudo python query_test.py --scale 5 --pg_sz 50 --db tpch5
sudo python query_test.py --scale 5 --pg_sz 100 --db tpch5
sudo python query_test.py --scale 5 --pg_sz 200 --db tpch5

sudo python query_test.py --scale 10 --pg_sz 50 --db tpch10
sudo python query_test.py --scale 10 --pg_sz 100 --db tpch10
sudo python query_test.py --scale 10 --pg_sz 200 --db tpch10
```
The result will be under the generated `results` folder. For more command parameters, please check out `query_test.py`.

Here are the commands to reproduce the test for Bloom Filter and Sargable Predicate Pushdown:
```
sudo python query_test_special.py --scale 1 --pg_sz 20 --db tpch1
sudo python query_test_special.py --scale 1 --pg_sz 50 --db tpch1
sudo python query_test_special.py --scale 1 --pg_sz 100 --db tpch1
sudo python query_test_special.py --scale 5 --pg_sz 50 --db tpch5
sudo python query_test_special.py --scale 10 --pg_sz 50 --db tpch10
```
The result will be under the generated `results_special` folder. For more command parameters, please check out `query_test_special.py`.

Note that each of the above command can take fairly long to run. So it is recommended that they are run in a `tmux` session.

## Plot Results
You may visualize the experiment results for Bloom filter and Sargable Pushdown using `figures.ipynb`.

