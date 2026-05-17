import { useState, useEffect } from "react";

import constants from "../state/constants";

import SolutionDialog from "../components/functional/SolutionDialog";
import DataTable from "../components/functional/DataTable";
import LoadingIndicator from "../components/functional/LoadingIndicator";

function PageResult(props) {
	const state = props.state;
	// const dispatch = props.dispatch;

	const tables = state.cache.parsed.analyze.ctxTree.code.tables;
	const table_sqls = state.cache.parsed.analyze.ctxTree.code.tableSQLs;
	const table_from_where_index = state.cache.parsed.analyze.ctxTree.code.tFromWhere;
	const table_group_by_index = state.cache.parsed.analyze.ctxTree.code.tGroupBy;
	const table_last_index = state.cache.parsed.analyze.ctxTree.code.tLast;
	const table_names = state.cache.raw.analyze.xtree.input_table_aliases;

	const [table_metas, set_table_metas] = useState([]);
	const [loaded, set_loaded] = useState(false);

	useEffect(() => {
		effect();

		async function effect() {
			const temp_metas = [];
			for (let i = 0; i < tables.length; i++) {
				let table_type, table_name;

				if (i === table_from_where_index) {
					table_type = "from_where";
					table_name = "FromWhere";
				} else if (i === table_group_by_index) {
					table_type = "group_by";
					table_name = "GroupBy";
				} else if (i === table_last_index) {
					table_type = "final";
					table_name = "Final Result";
				} else {
					table_type = "from";
					table_name = "From";
				}

				const res = await prepare(table_sqls[i].sql);
				const meta = { ...res, ...tables[i], type: table_type, name: table_name };
				temp_metas.push(meta);
			}

			set_table_metas((prev) => {
				let new_metas = prev.slice();
				new_metas = temp_metas;
				return new_metas;
			});
			set_loaded(true);
		}
	}, []);

	if (loaded)
		return (
			<div className="div-page">
				<SolutionDialog></SolutionDialog>
				<DataTable meta={table_metas[table_last_index]}></DataTable>
			</div>
		);
	else return <LoadingIndicator></LoadingIndicator>;

	async function prepare(query) {
		return await request("prepare", {
			db: constants.db,
			query: query,
			chunk_size: constants.chunk_size,
			group_dups: true,
		});
	}

	async function request(route, body) {
		const res = await fetch(constants.backend + "/" + route, {
			method: "POST",
			headers: constants.headers,
			body: JSON.stringify(body),
		});
		const json = await res.json();
		return json;
	}
}

export default PageResult;
