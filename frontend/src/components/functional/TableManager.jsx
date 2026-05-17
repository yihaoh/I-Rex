import { useState, useEffect } from "react";

import constants from "../../state/constants";
import mutations from "../../state/mutations";

import DataTable from "./DataTable";
import GroupTable from "./GroupTable";
import Divider from "./Divider";
import LoadingIndicator from "./LoadingIndicator";
import TreeSection from "./TreeSection";
import MultitreeSection from "./MultitreeSection";

function TableManager(props) {
	const state = props.state;
	const dispatch = props.dispatch;

	const analyze = state.cache.parsed.analyze;
	const context = analyze.ctxToCtxJson[state.context];
	const contextNode = analyze.xNodeToXTree[context.XTableValuedNode_id];

	const tables = context.code.tables;
	const table_from_where_index = context.code.tJoinFilter;
	const table_expanded_group_index = context.code.tExpandGroup;
	const table_group_by_index = context.code.tGroup;
	const table_last_index = context.code.tOutput;
	const table_names = contextNode.input_table_aliases;
	const table_xids = contextNode.input_XTableValuedNode_ids;
	const table_hooks = context.code.tableHooks;
	const where_tree = context.where_tree;
	const having_tree = context.having_tree;
	const select_trees = context.select_trees;
	const group_trees = context.group_trees;
	const expanded_tree = context.expanded_trees;
	const on_trees = context.on_trees;

	const ctree = state.cache.raw.analyze.ctree
	const code = context.code
	const milestoneSQLs = code.milestoneSQLs
	const pageSQLs = code.pageSQLs
	const id_map = state.cache.parsed.analyze.xNodeToXTree
	const ctx_map = state.cache.parsed.analyze.ctxToCtxJson

	const [table_metas, set_table_metas] = useState([]);
	const [loaded, set_loaded] = useState(false);
	const [input_rows, set_input_rows] = useState([]);
	const [active_iids, set_active_iids] = useState([]);
	const [active_final_iid, set_active_final_iid] = useState([]);
	const [active_rows_map, set_active_rows_map] = useState({});
	const [active_rows, set_active_rows] = useState({
		input: null,
		join_filter: null,
		group: null,
		expanded: null,
		final: null
	});

	const [active_vals, set_active_vals] = useState({});
	const [where_tree_data, set_where_tree_data] = useState({});
	const [having_tree_data, set_having_tree_data] = useState({});
	const [group_tree_data, set_group_tree_data] = useState({});
	const [select_tree_data, set_select_tree_data] = useState({});

	const [join_input_tables, set_join_input_tables] = useState([]);

	state.cache.active_rows_map = active_rows_map;
	state.cache.id_map = id_map;

	let parent_context = context.parentCtxID || context.parent_id;

	if (parent_context === null || parent_context === undefined) {
		for (let i in ctx_map) {
			if (ctx_map[i].join_context_tree?.id === context.id) {
				parent_context = ctx_map[i].id;
				break;
			}
			if (!ctx_map[i].children || ctx_map[i].children.length === 0) continue;
			for (let k in ctx_map[i].children) {
				// console.log(ctx_map[i].children[k].id, context.id)
				if (ctx_map[i].children[k].id === context.id) {
					parent_context = ctx_map[i].id;
				}
			}
		}
	}
	// console.log(parent_context)

	const join_ctx_tree = context.join_context_tree

	let inputs = join_ctx_tree ? find_inputs(join_ctx_tree) : original_inputs();
	let original_input_tables = original_input_refs(original_inputs());

	// console.log(original_input_tables);

	// console.log(context)
	// console.log(contextNode)

	const is_join_ctx = context.type === "DJoinContext";

	if (is_join_ctx) original_input_tables = original_input_refs(original_join_inputs());

	// if (context.type === "DJoinContext") { // if join context, render 

	// }

	function original_join_inputs() {
		let out = [];

		for (let i in context.input_XTableValuedNode_ids) {
			out.push(id_map[context.input_XTableValuedNode_ids[i]])
		}

		return out;
	}

	function original_inputs() {
		let out = [];

		for (let i in contextNode.input_XTableValuedNode_ids) {
			out.push(id_map[contextNode.input_XTableValuedNode_ids[i]])
		}

		return out;
	}

	function original_input_refs(tables) {
		let out = [];

		for (let i in tables) {
			if (tables[i].type === "XTableRenameNode") {
				out.push(tables[i].operand)
			} else {
				out.push(tables[i])
			}
		}

		return out
	}

	function find_inputs(join_ctx) {
		let out = [];
		if (join_ctx.join_type == "CROSS" || join_ctx.join_type == "COMMA") { // if cross or comma, deeper
			let input_xids = join_ctx.input_XTableValuedNode_ids

			for (let xid in input_xids) {
				let xnode = id_map[input_xids[xid]]

				out = out.concat(find_inputs(xnode))
			}
		} else { // otherwise, not deeper
			out.push(join_ctx)
		}

		return out;
	}

	let input_tables = convert_to_tables(inputs)
	// console.log(inputs, input_tables)
	// console.log(contextNode)

	function convert_to_tables(inputs) {
		const out = {
			ids: [],
			tables: [],
			milestoneSQLs: [],
			pageSQLs: [],
			table_hooks: [],
			table_contexts: []
		}
		for (let i in inputs) {
			const index = contextNode.input_XTableValuedNode_ids.indexOf(inputs[i].id)
			// console.log(inputs)
			// console.log(index)
			if (index > -1) {
				out.ids.push(inputs[i].id)
				out.tables.push(tables[index])
				out.milestoneSQLs.push(milestoneSQLs[index])
				out.pageSQLs.push(pageSQLs[index])
				out.table_hooks.push(table_hooks[index])
				out.table_contexts.push(null)
			} else {
				for (let ctx_id in analyze.ctxToCtxJson) {
					const ctx = analyze.ctxToCtxJson[ctx_id];
					if (ctx.XTableValuedNode_id == inputs[i].id || ctx.id == inputs[i].id) {
						out.ids.push(inputs[i].id)
						out.tables.push(ctx.code.tables[ctx.code.tables.length - 1])
						out.milestoneSQLs.push(ctx.code.milestoneSQLs[ctx.code.tables.length - 1])
						out.pageSQLs.push(ctx.code.pageSQLs[ctx.code.tables.length - 1])
						if (ctx.code.tables_hooks) {
							out.table_hooks.push(ctx.code.tables_hooks[ctx.code.tables.length - 1])
						}
						out.table_contexts.push(ctx.id)
					}
				}
			}
		}

		// console.log(out)

		return out;
	}

	// function find_context_inputs(context) {
	// 	if (context.type == "DSelectContext") {
	// 		id_map[context.XTableValuedNode_id].
	// 	} else if (context.type == "DJoinContext") {

	// 	}
	// }
	function fill_eval_req(active, inputs, expected_rows) {
		// console.log(active, inputs, expected_rows)

		const out = expected_rows;
		const keys = Object.keys(active);
		let active_index = 0, active_subindex = 0;

		// for (let i = 0; i < )

		// console.log(inputs)

		for (let i in inputs) {
			let values = []

			if (!active[keys[active_index]] || !active[keys[active_index]].value) continue;

			for (let k = 0; k < inputs[i].schema.length; k++) {
				// console.log(active[keys[active_index]].value, k, active_subindex, active_index, active[keys[active_index]].value.length)
				values.push(active[keys[active_index]].value[active_subindex])
				active_subindex++;
			}

			expected_rows[i].value = values

			// console.log(active_subindex === active[keys[active_index]].value.length - 1, active_subindex, active[keys[active_index]].value.length - 1)

			// console.log(active[keys[active_index]], inputs[i].schema.length)

			if (active_subindex >= active[keys[active_index]].value.length - 1) {
				// console.log("HIT")
				active_index++;
				active_subindex = 0;
			}
		}

		// console.log(active, inputs, expected_rows)

		// for (let a in active) {
		// 	if (a )
		// for (let i in inputs) {
		// 	const len = inputs[i].internal_consistent_sort_order.exprs.length
		// }
		// }
	}

	let join_condition_tree;

	if (context.type == "DJoinContext" && id_map[context.XTableValuedNode_id].condition) join_condition_tree = mutations.convertToTree(id_map[context.XTableValuedNode_id].condition, id_map)

	useEffect(() => {
		// console.log(state.context)

		effect();
		async function effect() {
			const temp_metas = [];
			const temp_active_vals = {};

			set_active_iids([])

			set_input_rows((prev) => {
				return [];
			});

			let available_tables = []

			join_input_tables.length = 0;

			if (context.type == "DSelectContext") {
				find_join_inputs(context.join_context_tree, available_tables)
			} else {
				find_join_inputs(context.children[0], available_tables)
			}

			available_tables = available_tables.filter(element => element !== undefined);

			function find_join_inputs(join_node, tables) {
				if (!join_node || !join_node.input_XTableValuedNode_ids) return;

				for (let node in join_node.input_XTableValuedNode_ids) {
					tables.push(id_map[join_node.input_XTableValuedNode_ids[node]].name ?? id_map[join_node.input_XTableValuedNode_ids[node]].new_name)
					if (id_map[join_node.input_XTableValuedNode_ids[node]].type == "XTableRefNode") {
						join_input_tables.push(id_map[join_node.input_XTableValuedNode_ids[node]])
					} else {
						join_input_tables.push(id_map[join_node.input_XTableValuedNode_ids[node]].operand)
					}
				}

				for (let ctx in join_node.children) {
					find_join_inputs(join_node.children[ctx], tables)
				}
			}

			if (inputs.length === 0) { // inside join context

				let join_result_input = 0;

				// console.log(join_condition_tree)

				// console.log(context)

				for (let i = 0; i < context.code.tables.length; i++) {

					const join_result = context.input_XTableValuedNode_ids === undefined || context.input_XTableValuedNode_ids[i] === undefined || id_map[context.input_XTableValuedNode_ids[i]].new_name === undefined ? true : false;

					let table_type = "from", table_name = join_result ? "Join Result" : id_map[context.input_XTableValuedNode_ids[i]].new_name, is_from = i !== context.code.tables.length - 1, table_context = null;

					if (join_result && i !== context.code.tables.length - 1) {
						// console.log(context.children[join_result_input], join_result_input, i)
						table_name = "Join Result (accessible in current context: " + available_tables.join(", ") + ")"
						table_context = context.children[join_result_input]?.id || null;
						join_result_input++;
					}

					if (!join_result) {
						if (id_map[context.input_XTableValuedNode_ids[i]].type == "XTableRefNode") {
							join_input_tables.push(id_map[context.input_XTableValuedNode_ids[i]])
						} else if (id_map[context.input_XTableValuedNode_ids[i]].type == "XTableRenameNode") {
							join_input_tables.push(id_map[context.input_XTableValuedNode_ids[i]].operand)
						}
					}

					let milestone;

					await fetch(constants.backend + "/execute_milestone", {
						method: "POST",
						headers: {
							"Content-Type": "application/json",
						},
						body: JSON.stringify({
							db: constants.db,
							sql: context.code.milestoneSQLs[i],
							bindings: context.code.milestoneSQLs[i].expected_bindings,
						}),
					})
						.then((res) => res.json())
						.then((data) => {
							milestone = data.contents
						});

					let rows = milestone.length === 0 ? 0 : milestone[milestone.length - 1][0] + milestone[milestone.length - 1][1]
					let pages = milestone.length;
					let id = state.cache.get_id(table_type, rows, pages);
					let iid_col = 0;

					if (is_from) {
						set_input_rows((prev) => {
							prev.push(rows)
							return prev;
						})

						set_active_iids((prev) => {
							prev.push();
							return prev;
						})
					}

					for (let k = 0; k < context.code.tables[i].column_use.length; k++) {
						if (context.code.tables[i].column_use[k] === "IID") {
							iid_col = k;
							break;
						}
					}

					const meta = { ...context.code.tables[i], id: id, type: table_type, name: table_name, pageSQL: context.code.pageSQLs[i], milestone: milestone, hooks: null, rows: rows, iid_col: iid_col, context: table_context };

					// console.log(meta)

					// console.log(input_tables, i, meta)

					if (is_from) temp_active_vals[table_name] = {}
					temp_metas.push(meta);
					state.cache.set_meta(id, meta)
					// console.log(meta)
				}

			} else { // select context 

				for (let i = 0; i < input_tables.tables.length; i++) {
					let table_type = "from", table_name = table_xids.indexOf(input_tables.ids[i]) === -1 ? "Join Result" : table_names[table_xids.indexOf(input_tables.ids[i])], is_from = true;

					if (table_name === "Join Result") {
						// console.log(input_tables.ids[i])
						// console.log(input_tables.tables[i])

						table_name = "Join Result (accessible in currect context: " + available_tables.join(", ") + ")"
					}

					// console.log(input_tables)

					let milestone;

					let context_bindings = input_tables.milestoneSQLs[i].expected_bindings;

					for (let b in context_bindings) {
						const binding = context_bindings[b];
						let context_xid = binding.xSelectNode_id;
						let table_xid = id_map[context_xid].input_XTableValuedNode_ids[binding.xSelectNode_input_index];
						// console.log(table_xid)
						if (active_rows_map[table_xid]) {
							binding.value = active_rows_map[table_xid][binding.xSelectNode_input_column_index]
						}
					}

					await fetch(constants.backend + "/execute_milestone", {
						method: "POST",
						headers: {
							"Content-Type": "application/json",
						},
						body: JSON.stringify({
							db: constants.db,
							sql: input_tables.milestoneSQLs[i],
							bindings: input_tables.milestoneSQLs[i].expected_bindings,
						}),
					})
						.then((res) => res.json())
						.then((data) => {
							milestone = data.contents
						});

					let rows = milestone.length === 0 ? 0 : milestone[milestone.length - 1][0] + milestone[milestone.length - 1][1]
					let pages = milestone.length;
					let id = state.cache.get_id(table_type, rows, pages);
					let iid_col = 0;

					if (is_from) {
						set_input_rows((prev) => {
							prev.push(rows)
							return prev;
						})

						set_active_iids((prev) => {
							prev.push();
							return prev;
						})
					}

					for (let k = 0; k < input_tables.tables[i].column_use.length; k++) {
						if (input_tables.tables[i].column_use[k] === "IID") {
							iid_col = k;
							break;
						}
					}

					const meta = { ...input_tables.tables[i], ctxn_xid: contextNode.id, xid: original_input_tables[i].id, id: id, type: table_type, name: table_name, pageSQL: input_tables.pageSQLs[i], milestone: milestone, hooks: table_hooks[i], rows: rows, iid_col: iid_col, context: input_tables.table_contexts[i] };

					console.log(meta)

					// console.log(input_tables, i, meta)

					temp_active_vals[table_name] = {}
					temp_metas.push(meta);
					state.cache.set_meta(id, meta)
					// console.log(meta)
				}

				for (let i = 0; i < tables.length; i++) {
					let table_type, table_name, is_from = false;

					if (i === table_from_where_index) {
						table_type = "join_filter";
						table_name = "Join & Filter";
					} else if (i === table_group_by_index) {
						table_type = "group_by";
						table_name = "Group By";
					} else if (i === table_last_index) {
						table_type = "final";
						table_name = "Final";
					} else if (i === table_expanded_group_index) {
						table_type = "expanded";
						table_name = "Expanded Group";
					} else {
						// continue;

						// table_type = "from";
						// table_name = table_names[i];

						// is_from = true;
					}

					let milestone;

					let context_bindings = milestoneSQLs[i].expected_bindings;

					for (let b in context_bindings) {
						const binding = context_bindings[b];
						let context_xid = binding.xSelectNode_id;
						let table_xid = id_map[context_xid].input_XTableValuedNode_ids[binding.xSelectNode_input_index];
						console.log(table_xid, active_rows_map)
						if (active_rows_map[table_xid]) {
							// console.log("TRUE!")
							binding.value = active_rows_map[table_xid][binding.xSelectNode_input_column_index];
							console.log(binding, binding.value)
						}
					}

					await fetch(constants.backend + "/execute_milestone", {
						method: "POST",
						headers: {
							"Content-Type": "application/json",
						},
						body: JSON.stringify({
							db: constants.db,
							sql: milestoneSQLs[i],
							bindings: milestoneSQLs[i].expected_bindings,
							reason: table_type
						}),
					})
						.then((res) => res.json())
						.then((data) => {
							milestone = data.contents
						});

					state.cache.set_milestone(i, milestone);

					let rows = milestone.length === 0 ? 0 : milestone[milestone.length - 1][0] + milestone[milestone.length - 1][1]
					let pages = milestone.length;
					let id = state.cache.get_id(table_type, rows, pages);
					let iid_col = 0;

					if (is_from) {
						set_input_rows((prev) => {
							prev.push(rows)
							return prev;
						})

						set_active_iids((prev) => {
							prev.push();
							return prev;
						})
					}

					for (let k = 0; k < tables[i].column_use.length; k++) {
						if (tables[i].column_use[k] === "IID") {
							iid_col = k;
							break;
						}
					}

					const meta = { ...tables[i], id: id, type: table_type, name: table_name, pageSQL: pageSQLs[i], milestone: milestone, hooks: table_hooks[i], rows: rows, iid_col: iid_col };
					temp_metas.push(meta);
					state.cache.set_meta(id, meta)
					// console.log(meta)
				}

			}

			// for (let i = 0; i < input_tables.tables.length; i++) {
			// 	let table_type = "from", table_name = "N/A", is_from = true;

			// 	let milestone;

			// 	await fetch(constants.backend + "/execute_milestone", {
			// 		method: "POST",
			// 		headers: {
			// 			"Content-Type": "application/json",
			// 		},
			// 		body: JSON.stringify({
			// 			db: constants.db,
			// 			sql: input_tables.milestoneSQLs[i],
			// 			bindings: input_tables.milestoneSQLs[i].expected_bindings,
			// 		}),
			// 	})
			// 		.then((res) => res.json())
			// 		.then((data) => {
			// 			milestone = data.contents
			// 		});

			// 	let rows = milestone.length === 0 ? 0 : milestone[milestone.length - 1][0] + milestone[milestone.length - 1][1]
			// 	let pages = milestone.length;
			// 	let id = state.cache.get_id(table_type, rows, pages);
			// 	let iid_col = 0;

			// 	if (is_from) {
			// 		set_input_rows((prev) => {
			// 			prev.push(rows)
			// 			return prev;
			// 		})

			// 		set_active_iids((prev) => {
			// 			prev.push();
			// 			return prev;
			// 		})
			// 	}

			// 	for (let k = 0; k < input_tables.tables[i].column_use.length; k++) {
			// 		if (input_tables.tables[i].column_use[k] === "IID") {
			// 			iid_col = k;
			// 			break;
			// 		}
			// 	}

			// 	const meta = { ...input_tables.tables[i], id: id, type: table_type, name: table_name, pageSQL: pageSQLs[i], milestone: milestone, hooks: table_hooks[i], rows: rows, iid_col: iid_col };
			// 	temp_metas.push(meta);
			// 	state.cache.set_meta(id, meta)
			// }

			// console.log(temp_metas)

			temp_metas.push();

			set_table_metas((prev) => {
				let new_metas = prev.slice();
				// console.log(temp_metas)
				return temp_metas;
			});

			set_active_vals((prev) => {
				return temp_active_vals;
			})

			// set_table_metas((prev) => {
			// 	let new_metas = prev.slice();
			// 	new_metas = [{ name: "Input" }, { name: "Join & Filter" }, { name: "Final" }];
			// 	return new_metas;
			// });
			set_loaded(true);
		}
	}, [state.context]);

	// async function request_eval(route, body) {
	// 	const res = await fetch(constants.backend + "/execute_eval", {
	// 		method: "POST",
	// 		headers: constants.headers,
	// 		body: JSON.stringify(body),
	// 	});
	// 	const json = await res.json();
	// 	return json;
	// }

	// useEffect(() => {
	// 	console.log(input_rows)
	// 	console.log(convert_to_rows(state.current))
	// }, [state.current])

	useEffect(() => {
		// console.log("active", active_iids)

		// console.log("effect ok")

		effect()

		async function effect() {
			const trace_active_rows = await state.cache.forward_trace(active_iids);

			// console.log(trace_active_rows)

			evaluate_trees(trace_active_rows);

			set_active_rows((prev) => {
				return trace_active_rows
			})

			// console.log(trace_active_rows.join_filter)

		}
	}, [active_iids])

	async function evaluate_trees(trace_active_rows) {

		if ((trace_active_rows.group || trace_active_rows.group === 0) && (table_group_by_index || table_group_by_index === 0) && table_metas[input_tables.tables.length + table_group_by_index]) {
			const having_eval = {};
			const group_eval = {};
			const row = (await state.cache.fetch(table_metas[input_tables.tables.length + table_group_by_index], trace_active_rows.group, trace_active_rows.group))[0]
			const column_names = table_metas[input_tables.tables.length + table_group_by_index].column_names;
			const column_use = table_metas[input_tables.tables.length + table_group_by_index].column_use;

			// console.log(row)

			for (let i = 0; i < column_use.length; i++) {
				if (column_use[i] === "EVAL") {
					// console.log(row, i, row[i])
					having_eval[column_names[i]] = row[i];
					group_eval[column_names[i]] = row[i];
				}
			}

			if ((trace_active_rows.expanded || trace_active_rows.expanded === 0) && (table_expanded_group_index || table_expanded_group_index === 0) && table_metas[input_tables.tables.length + table_expanded_group_index]) {
				const row = (await state.cache.fetch(table_metas[input_tables.tables.length + table_expanded_group_index], trace_active_rows.expanded, trace_active_rows.expanded))[0]
				const column_names = table_metas[input_tables.tables.length + table_expanded_group_index].column_names;
				const column_use = table_metas[input_tables.tables.length + table_expanded_group_index].column_use;

				// console.log(row)

				for (let i = 0; i < column_use.length; i++) {
					if (column_use[i] === "EVAL") {
						// console.log(row, i, row[i])
						group_eval[column_names[i]] = row[i];
					}
				}
			}

			if (having_eval) {
				set_having_tree_data(having_eval)
				// console.log(having_eval)
			}

			if (group_eval) {
				set_group_tree_data(group_eval)
				// console.log(group_eval)
			}

			// console.log(having_tree, having_eval)
		} else {
			set_having_tree_data({})
			set_group_tree_data({})
		}

		if ((trace_active_rows.final || trace_active_rows.final === 0) && (table_last_index || table_last_index === 0) && table_metas[input_tables.tables.length + table_last_index]) {
			const select_eval = {};
			const row = (await state.cache.fetch(table_metas[input_tables.tables.length + table_last_index], trace_active_rows.final, trace_active_rows.final))[0]
			const column_names = table_metas[input_tables.tables.length + table_last_index].column_names;
			const column_use = table_metas[input_tables.tables.length + table_last_index].column_use;

			// console.log(row)

			for (let i = 0; i < column_use.length; i++) {
				if (column_use[i] === "EVAL") {
					// console.log(row, i, row[i])
					select_eval[column_names[i]] = row[i];
				}
			}

			if (select_eval) {
				set_select_tree_data(select_eval)
			}

			// console.log(having_tree, having_eval)
		} else {
			set_select_tree_data({})
		}
	}

	useEffect(() => {
		effect()
		async function effect() {
			// console.log(active_final_iid)
			const trace_active_rows = await state.cache.backward_trace_final(active_final_iid);

			console.log(trace_active_rows)

			if (!trace_active_rows) return;

			// console.log(trace_active_rows)

			if ((trace_active_rows.group || trace_active_rows.group === 0) && (table_group_by_index || table_group_by_index === 0) && table_metas[table_group_by_index]) {
				const having_eval = {};
				const row = (await state.cache.fetch(table_metas[table_group_by_index], trace_active_rows.group, trace_active_rows.group))[0]
				const column_names = table_metas[table_group_by_index].column_names;
				const column_use = table_metas[table_group_by_index].column_use;

				// console.log(row)

				for (let i = 0; i < column_use.length; i++) {
					if (column_use[i] === "EVAL") {
						// console.log(row, i, row[i])
						having_eval[column_names[i]] = row[i];
					}
				}

				if (having_eval) {
					set_having_tree_data(having_eval)
				}

				// console.log(having_tree, having_eval)
			} else {
				set_having_tree_data({})
				set_group_tree_data({})
			}
			set_active_iids(prev => {
				return trace_active_rows.input
			})

			dispatch(["SET_CURRENT", convert_from_rows(trace_active_rows.input)])

			// set_active_rows((prev) => {
			// 	return trace_active_rows
			// })
		}
	}, [active_final_iid])

	useEffect(() => {
		effect();

		// async function effect() {
		// 	const rows = []

		// 	for (let key in active_vals) {
		// 		rows.push(active_vals[key])
		// 	}

		// 	await fetch(constants.backend + "/execute_eval", {
		// 		method: "POST",
		// 		headers: {
		// 			"Content-Type": "application/json",
		// 		},
		// 		body: JSON.stringify({
		// 			db: constants.db,
		// 			sql: code.onWhereEvalSQL,
		// 			bindings: [],
		// 			filters: {},
		// 			rows: rows
		// 		}),
		// 	})
		// 		.then((res) => res.json())
		// 		.then((data) => {
		// 			if (!data.contents) return;
		// 			// console.log(data.contents)

		// 			const eval_map = {};
		// 			for (let i in code.onWhereEvalHooks) {
		// 				eval_map[code.onWhereEvalHooks[i]] = data.contents[0][i]
		// 			}

		// 			set_where_tree_data(() => {
		// 				return eval_map;
		// 			})
		// 		});
		// }

		async function effect() {
			if (!code.onWhereEvalSQL && !code.onEvalSQL) return

			if (code.onWhereEvalSQL) {
				fill_eval_req(active_vals, original_input_tables, code.onWhereEvalSQL.expected_rows)
			} else if (code.onEvalSQL) {
				fill_eval_req(active_vals, join_input_tables, code.onEvalSQL.expected_rows)
			}

			const rows = code.onWhereEvalSQL?.expected_rows || code.onEvalSQL?.expected_rows
			const sql = code.onWhereEvalSQL || code.onEvalSQL

			// console.log(active_vals, join_input_tables)

			// console.log(code.onWhereEvalSQL, code.onEvalSQL)

			// console.log(code.onWhereEvalSQL.expected_rows)

			await fetch(constants.backend + "/execute_eval", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					db: constants.db,
					sql: sql,
					bindings: [],
					filters: {},
					rows: rows
				}),
			})
				.then((res) => res.json())
				.then((data) => {
					if (!data.contents) return;
					// console.log(data.contents)

					const eval_map = {};
					for (let i in code.onWhereEvalHooks) {
						eval_map[code.onWhereEvalHooks[i]] = data.contents[0][i]
					}

					for (let i in code.onEvalHooks) {
						eval_map[code.onEvalHooks[i]] = data.contents[0][i]
					}

					set_where_tree_data(() => {
						return eval_map;
					})
				});
		}

		// console.log(code, active_vals)
	}, [active_vals])

	useEffect(() => {
		set_table_metas(prev => {
			return []
		})

		set_active_vals(prev => {
			return {}
		})
	}, [state.context])

	function convert_to_rows(current) {
		let out = [];

		for (let i = input_rows.length - 1; i >= 0; i--) {
			out.unshift(current % input_rows[i])
			current = Math.floor(current / input_rows[i])
		}

		return out;
	}

	function convert_from_rows(rows_array) {
		let current = 0;

		for (let i = 0; i < rows_array.length; i++) {
			current = current * input_rows[i] + rows_array[i];
		}

		return current;
	}

	function set_iid(iid, id) {
		// console.log("SETIID", iid, id)
		set_active_iids(prev => {
			let new_iids = prev.slice();
			new_iids[id] = iid;
			return new_iids;
		})
	}

	function set_final_iid(iid) {
		// console.log("FINAL IID", iid)
		set_active_final_iid(iid)
	}

	function set_val(name, columns, types, value, xid) {
		set_active_vals(prev => {
			return {
				...prev, // Copy all existing properties from 'prev'
				[name]: { // Add/overwrite the 'name' property
					table_name: name,
					columns: columns,
					types: types,
					value: value,
				}
			};
		})

		// console.log(xid, value)

		set_active_rows_map(prev => {
			return {
				...prev,
				[xid]: value
			}
		})
	}

	function change_ctx(context) {
		// console.log("Drilling up/down to ", context)
		state.cache.reset_context();
		dispatch(["SET_CONTEXT", context]);
	}

	return (
		<div className="div-page">

			{loaded && context.code.type === "DSelectContextCode" && <Divider title={"Input Tables"}></Divider>}
			{loaded && context.code.type !== "DSelectContextCode" && table_metas.length >= 1 && <Divider title={"Input Tables"}></Divider>}
			{loaded && context.code.type !== "DSelectContextCode" && table_metas.length < 1 && <div style={{ height: "1rem" }}></div>}

			{/* {console.log(group_trees, select_trees, on_trees, contextNode.on_conds)} */}

			{loaded &&
				table_metas.map((table_meta, i) => {
					if (context.code.type === "DJoinContextCode" && table_metas.length >= 2 && i == table_metas.length - 2) return <DataTable state={state} meta={table_meta} key={i} active_row={convert_to_rows(state.current)[i]} set_iid={set_iid} set_val={set_val} input_key={i} context_down={table_meta.context} change_ctx={change_ctx}></DataTable>
					if (context.code.type === "DJoinContextCode" && table_metas.length >= 2 && i == table_metas.length - 1) return <>
						<Divider title="Join Condition"></Divider>
						{join_condition_tree && <TreeSection data={join_condition_tree} tid={89999} id_map={id_map} eval_map={where_tree_data}></TreeSection>}
						<Divider title="Join Result"></Divider>
						<DataTable state={state} meta={table_meta} key={i} active_row={convert_to_rows(state.current)[i]} set_iid={set_iid} set_val={set_val} input_key={i} context_down={table_meta.context} context_up={parent_context} change_ctx={change_ctx}></DataTable>
						<div style={{ width: "100%", height: "1rem" }}></div>
					</>
					// console.log(table_meta, i)
					if (table_meta.type === "expanded") return;
					if (table_meta.type === "group_by") return <>
						<Divider title={"Group By Tables"}></Divider>
						<GroupTable state={state} meta={[table_meta, table_metas[i - 1]]} key={i} active_group={active_rows.group} active_expanded={active_rows.expanded} expanded_space={active_rows.expanded_space} group_iid={active_rows.group_iid}></GroupTable>
						{/* <MultitreeSection data={data} tids={[2, 3]}></MultitreeSection>*/}
						{group_trees?.length !== 0 && <>  <Divider title={"Member Contribution to Group"}></Divider> {active_rows.group === null && <div style={{ color: "#9f1f1f", background: "#ffdfdf", border: "1px solid #df7f7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > No active group record to evaluate member expression trees </div >} {active_rows.group !== null && <div style={{ color: "#1f9f1f", background: "#dfffdf", border: "1px solid #7fdf7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > Evaluated member expression trees for active group record </div >} <MultitreeSection data={expanded_tree} tids={Array.from({ length: group_trees?.length }, (_, i) => 2 + select_trees?.length + i)} id_map={id_map} eval_maps={group_tree_data}> </MultitreeSection> </>}
						<Divider title="Aggregation Expressions"> </Divider>
						{active_rows.group === null && <div style={{ color: "#9f1f1f", background: "#ffdfdf", border: "1px solid #df7f7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > No active group record to evaluate group expression trees </div >}
						{active_rows.group !== null && <div style={{ color: "#1f9f1f", background: "#dfffdf", border: "1px solid #7fdf7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > Evaluated group expression trees for active group record </div >}
						<MultitreeSection data={group_trees} tids={Array.from({ length: group_trees?.length }, (_, i) => 100 + select_trees?.length + i)} id_map={id_map} eval_maps={group_tree_data}> </MultitreeSection>
						<Divider title="Having Condition"></Divider>
						{active_rows.group === null && <div style={{ color: "#9f1f1f", background: "#ffdfdf", border: "1px solid #df7f7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > No active group record to evaluate HAVING expression tree </div >}
						{active_rows.group !== null && <div style={{ color: "#1f9f1f", background: "#dfffdf", border: "1px solid #7fdf7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > Evaluated HAVING expression tree for active group record </div >}
						<TreeSection data={having_tree} tid={1} id_map={id_map} eval_map={having_tree_data}></TreeSection>
					</>
					if (table_meta.type === "join_filter") return <> {!!contextNode.where_cond && <><Divider title="Where Condition"></Divider><TreeSection data={where_tree} tid={0} id_map={id_map} eval_map={where_tree_data}></TreeSection></>} {false && contextNode.on_conds && !!contextNode.on_conds.length && <><Divider title="On Conditions"></Divider><MultitreeSection data={on_trees} tids={Array.from({ length: on_trees?.length }, (_, i) => 5000 + i)} id_map={id_map} eval_maps={where_tree_data}></MultitreeSection></>} <Divider title={"Join & Filter Table"}></Divider> <DataTable state={state} meta={table_meta} key={i} active_row={active_rows.join_filter}></DataTable> </>
					if (table_meta.type === "final") return <> <Divider title={"Final Table"}></Divider> <DataTable state={state} meta={table_meta} key={i} active_row={active_rows.final} set_iid={set_final_iid} context_up={parent_context} change_ctx={change_ctx}></DataTable> <Divider title={"Select Expressions"}> </Divider> {active_rows.final === null && <div style={{ color: "#9f1f1f", background: "#ffdfdf", border: "1px solid #df7f7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > No active final record to evaluate SELECT expression trees </div >} {active_rows.final !== null && <div style={{ color: "#1f9f1f", background: "#dfffdf", border: "1px solid #7fdf7f", margin: "0 1rem", padding: "0.5rem", borderRadius: "0.2rem", fontWeight: "bold" }} > Evaluated SELECT expression trees for active final record </div >} <MultitreeSection data={select_trees} tids={Array.from({ length: select_trees?.length }, (_, i) => 2 + i)} id_map={id_map} eval_maps={select_tree_data}> </MultitreeSection> </>
					// if (i == table_from_where_index - 1) return <DataTable state={state} meta={table_meta} key={i} active_row={convert_to_rows(state.current)[i]} set_iid={set_iid} set_val={set_val} input_key={i}></DataTable>
					// if (i < table_from_where_index) return <> <DataTable state={state} meta={table_meta} key={i} active_row={convert_to_rows(state.current)[i]} set_iid={set_iid} set_val={set_val} input_key={i}></DataTable> <div style={{ width: "100%", height: "1rem" }}></div> </>;
					if (input_tables.tables.length === 0 || i < input_tables.tables.length) return <> <DataTable state={state} meta={table_meta} key={i} active_row={convert_to_rows(state.current)[i]} set_iid={set_iid} set_val={set_val} input_key={i} context_down={table_meta.context} change_ctx={change_ctx}></DataTable> <div style={{ width: "100%", height: "1rem" }}></div> </>;
				})}

			<div style={{ width: "100%", height: "1rem" }}></div>

			{/* {loaded && <DataTable meta={table_metas[0]}></DataTable>} */}

			{!loaded && <LoadingIndicator></LoadingIndicator>}
		</div >
	);

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

export default TableManager;
