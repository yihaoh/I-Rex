from flask import request, jsonify, Blueprint, current_app
import json
import logging
import os
import jpype
import jpype.imports
from psycopg2 import connect, extras


logger = logging.getLogger(__name__)

bp = Blueprint('trace', __name__)

# DB Credentials
dbuser = os.environ.get('DB_USER')
dbpassword = os.environ.get('DB_PASSWORD')
dbhost = os.environ.get('DB_HOST')
dbport = os.environ.get('DB_PORT')

db_analyzer = dict()
db_instances = dict()
db_metadata = dict()
analyzer_conns = dict()


def _conn_kwargs(dbname):
    return {
        'dbname': dbname,
        'user': dbuser,
        'password': dbpassword,
        'host': dbhost,
        'port': dbport,
    }


def _can_connect(dbname):
    try:
        with connect(**_conn_kwargs(dbname)):
            return True
    except Exception as e:
        logger.warning('Skipping database %s: %s', dbname, e)
        return False


def discover_accessible_databases():
    """List PostgreSQL databases and keep only those reachable with DB_USER/DB_PASSWORD."""
    if not all([dbuser, dbpassword, dbhost]):
        logger.error('DB_USER, DB_PASSWORD, and DB_HOST must be set')
        return []

    candidates = []
    for bootstrap_db in ('postgres', 'template1'):
        try:
            with connect(**_conn_kwargs(bootstrap_db)) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT datname FROM pg_database "
                        "WHERE datistemplate = false AND datallowconn = true "
                        "ORDER BY datname"
                    )
                    candidates = [row[0] for row in cur.fetchall()]
            break
        except Exception as e:
            logger.debug('Could not list databases via %s: %s', bootstrap_db, e)

    if not candidates:
        logger.error('Could not list databases on %s:%s', dbhost, dbport)
        return []

    accessible = [db for db in candidates if _can_connect(db)]
    logger.info('Accessible databases: %s', accessible)
    return accessible


db_names = discover_accessible_databases()

# ===================================
#    Initialize JVM and Analyzers 
# ===================================
if not jpype.isJVMStarted():
    jpype.startJVM(classpath=['sqlanalyzer.jar'])
    from edu.duke.cs.irex.sqlanalyzer import Analyzer
    for db in db_names:
        analyzer_conns[db] = Analyzer(dbhost, db, dbuser, dbpassword)


# pre-fetch database schemas to be passed to frontend
for db in db_names:
    with connect(**_conn_kwargs(db)) as conn:
        db_instances[db] = dict()
        db_metadata[db] = dict()
        with conn.cursor(cursor_factory=extras.RealDictCursor) as cur:
            cur.execute("select table_name from information_schema.tables where table_schema = 'public'")
            schema = cur.fetchall()
            for table in schema:
                table_name = table['table_name']
                db_instances[db][table_name] = dict()
                cur.execute(f"SELECT * FROM {table_name}")
                records = cur.fetchall()
                db_instances[db][table_name]['columns'] = cur.column_mapping
                db_instances[db][table_name]['width'] = 0
                for col in db_instances[db][table_name]['columns']:
                    db_instances[db][table_name]['width'] += (len(col) * 15)
                db_instances[db][table_name]['index'] = []
                db_instances[db][table_name]['data'] = []
                for i, record in enumerate(records):
                    db_instances[db][table_name]['index'].append(i)
                    row = []
                    for key in record:
                        row.append(record[key])
                    db_instances[db][table_name]['data'].append(row)
                cur.execute(f"SELECT column_name, data_type \
                                FROM information_schema.columns \
                                WHERE table_name = '{table_name}'")
                meta_records_type = cur.fetchall()
                db_metadata[db][table_name] = dict()
                for meta_record_type in meta_records_type:
                    if meta_record_type['data_type'] == 'character varying':
                        db_metadata[db][table_name][meta_record_type['column_name']] = 'VARCHAR'
                    else:
                        db_metadata[db][table_name][meta_record_type['column_name']] = str(meta_record_type['data_type']).upper()
                cur.execute(f"SELECT kcu.column_name \
                                FROM information_schema.table_constraints tc \
                                JOIN information_schema.key_column_usage kcu \
                                ON tc.constraint_name = kcu.constraint_name \
                                WHERE tc.table_name = '{table_name}' \
                                AND tc.constraint_type = 'PRIMARY KEY'")
                meta_records_pkey = cur.fetchall()
                db_metadata[db][table_name]['pkeys'] = list()
                for meta_record_pkey in meta_records_pkey:
                    db_metadata[db][table_name]['pkeys'].append(meta_record_pkey['column_name'])






# check if jvm is started before each request
@bp.before_request
def load_jvm():
    if not jpype.isJVMStarted():
        jpype.startJVM(classpath=['sqlanalyzer.jar'])
    # from edu.duke.cs.irex.sqlanalyzer import Analyzer
    # if request.is_json:
    #     request_json = request.get_json()
    #     if 'db' in request_json and request.endpoint != 'trace.get_instance':
    #         dbname = request.get_json().get('db')
    #         g.analyzer = Analyzer(dbhost, dbname, dbuser, dbpassword)


# allow requests from different origin
@bp.after_request
def add_cors_headers(response):
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'Content-Type, Authorization'
    return response



@bp.route("/api/analyze", methods=['POST'])
def analyze():
    try:
        user_data = request.get_json()
        query_context_in_java_json = analyzer_conns[user_data['db']].analyzeToJson(user_data['query'], user_data["page_size"])
        return str(query_context_in_java_json)
    except Exception as e:
        current_app.logger.error(f'[API analyze]: {str(e)}')
        return jsonify(error='[API analyze]: failed to analyze the query.', message=str(e))


@bp.route("/api/execute_milestone", methods=["POST"])
def execute_milestone():
    if not request.is_json:
        return jsonify(error="request content type not application/json")
    try:
        request_json = request.get_json()
        # analyzer = Analyzer("localhost", request_json["db"], dbuser, dbpassword)
    except Exception as e:
        return jsonify(error="Unable to process request", message=str(e))
    # return jsonify({"response": "this is the execute API."})
    code_json = request_json.get("sql", None)
    if code_json is None:
        return jsonify(error="code parameter missing")
    bindings_json = request_json.get("bindings")
    if bindings_json is None:
        return jsonify(error="bindings parameter missing")
    pins_json = request_json.get("pins")
    if pins_json is None:
        return jsonify(error="pins parameter missing")
    context_execd_in_java_json = analyzer_conns[request_json["db"]].executeMilestoneToJson(json.dumps(code_json), json.dumps(bindings_json), json.dumps(pins_json))
    return str(context_execd_in_java_json)


@bp.route("/api/execute_page", methods=["POST"])
def execute_page():
    if not request.is_json:
        return jsonify(error="request content type not application/json")
    try:
        request_json = request.get_json()
        # analyzer = Analyzer("localhost", request_json["db"], dbuser, dbpassword)
    except Exception as e:
        return jsonify(error="Unable to process request", message=str(e))
    # return jsonify({"response": "this is the execute API."})
    code_json = request_json.get("sql", None)
    if code_json is None:
        return jsonify(error="code parameter missing")
    bindings_json = request_json.get("bindings")
    if bindings_json is None:
        return jsonify(error="bindings parameter missing")
    filters_json = request_json.get("filters")
    if filters_json is None:
        return jsonify(error="filters parameter missing")
    pins_json = request_json.get("pins")
    if pins_json is None:
        return jsonify(error="pins parameter missing")
    context_execd_in_java_json = analyzer_conns[request_json["db"]].executePageToJson(json.dumps(code_json), json.dumps(bindings_json), json.dumps(filters_json), json.dumps(pins_json))
    return str(context_execd_in_java_json)


@bp.route("/api/execute_eval", methods=["POST"])
def execute_eval():
    if not request.is_json:
        return jsonify(error="request content type not application/json")
    try:
        request_json = request.get_json()
        # analyzer = Analyzer("localhost", request_json["db"], dbuser, dbpassword)
    except Exception as e:
        return jsonify(error="Unable to process request", message=str(e))
    # return jsonify({"response": "this is the execute API."})
    code_json = request_json.get("sql", None)
    if code_json is None:
        return jsonify(error="code parameter missing")
    bindings_json = request_json.get("bindings")
    if bindings_json is None:
        return jsonify(error="bindings parameter missing")
    rows_json = request_json.get("rows")
    if rows_json is None:
        return jsonify(error="rows parameter missing")
    context_execd_in_java_json = analyzer_conns[request_json["db"]].executeEvalToJson(json.dumps(code_json), json.dumps(bindings_json), json.dumps(rows_json))
    return str(context_execd_in_java_json)



@bp.route("/api/db_metadata", methods=['POST', 'GET'])
def get_meta():
    return json.dumps(db_metadata, default=str)




# ===================================
#       Request error handling
# ===================================
@bp.errorhandler(404)
def page_not_found(e):
    return jsonify({'message': 'Not Found'}), 404


@bp.errorhandler(500)
def internal_server_error(e):
    return jsonify({'message': 'Internal Server Error'}), 500
