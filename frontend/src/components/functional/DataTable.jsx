import Table from "./PaginatedTable";

function DataTable(props) {
	const meta = props.meta;
	const state = props.state;
	const active_row = props.active_row;
	const set_iid = props.set_iid;
	const set_val = props.set_val;
	const input_key = props.input_key;
	const context_down = props.context_down;
	const context_up = props.context_up;
	const change_ctx = props.change_ctx;

	let default_cols = [];
	let display_names = [];
	let iid_col = 0;

	for (let i = 0; i < meta.column_names.length; i++) {
		if (meta.column_use[i] == "IID") {
			iid_col = i;
		}

		if (meta.column_use[i] == "DISPLAY" /*|| meta.hooks[i].length != 0*/) {
			default_cols[i] = true;
		} else {
			default_cols[i] = false;
		}

		if (meta.column_names[i] in state.cache.parsed.analyze.xNodeToXTree) {
			display_names.push(state.cache.parsed.analyze.xNodeToXTree[meta.column_names[i]].sql_string)
		} else {
			display_names.push(meta.column_names[i])
		}
	}

	// if (meta.type === "final") {
	// 	for (let i = 0; i < iid_col; i++) {
	// 		if (default_cols[i] == true) {
	// 			default_cols[i + iid_col + 1] = true;
	// 			default_cols[i] = false;
	// 		}
	// 	}
	// }

	async function retrieve(lower, upper) {
		return await state.cache.fetch(meta, lower, upper);
	}

	return <Table retrieve={retrieve} meta={{ type: meta.type, title: meta.name, column_names: display_names, column_use: meta.column_use, hooks: meta.hooks, rows: meta.rows, column_types: meta.column_types, column_ids: meta.column_names, xid: meta.xid }} active_row={active_row} set_iid={set_iid} set_val={set_val} input_key={input_key} iid_col={iid_col} default_cols={default_cols} final_cols={state.cache.parsed.analyze.select_ids} context_up={context_up} context_down={context_down} change_ctx={change_ctx}></Table>;
}

export default DataTable;
