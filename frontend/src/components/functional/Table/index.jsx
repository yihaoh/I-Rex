import { useEffect, useState, useRef } from "react";
import constants from "../../../state/constants";
import LoadingIndicator from "../LoadingIndicator";

import icon_collapse from "./assets/icon_collapse.svg"
import icon_expand from "./assets/icon_expand.svg"
import icon_dropdown from "./assets/icon_dropdown.svg"

import "./index.css";

function Table(props) {
	const retrieve = props.retrieve;
	const meta = props.meta;
	const set_iid = props.set_iid;
	const set_val = props.set_val;
	const input_key = props.input_key;
	const default_cols = props.default_cols;
	const iid_col = props.iid_col;
	const expanded_space = props.expanded_space;
	const final_cols = props.final_cols;
	const context_down = props.context_down;
	const context_up = props.context_up;
	const change_ctx = props.change_ctx;

	let default_active = props.active_row;

	const [table_state, c_table_state] = useState({
		data: [],
		offset: 0,
		prev_local_y: 0,
		window_offset: 0,
		config: false,
		columns: default_cols ?? [],
		active: default_active,
		pinned: null,
		collapsed: false,
		prev_absolute_y: 0,
		prev_data_y: 0,
		highlight: null,
		primary_index: 0,
		offset: 0,
		iid_column: iid_col,
	});

	let refMain = useRef();
	let refRow = useRef();
	let refConfig = useRef();

	const block_size = constants.block_size;

	let skip = false;

	useEffect(() => {
		effect();

		async function effect() {
			const d = await retrieve(0, block_size * 2);
			c_table_state((prev) => {
				return { ...prev, data: d };
			})
		}
	}, []);

	useEffect(() => {
		effect();

		async function effect() {
			if (meta.type !== "expanded") {
				// console.log(meta.type, default_active)
				c_table_state((prev) => {
					return { ...prev, active: default_active }
				})

				if (default_active) await jump_to(default_active, true)
			} else if (expanded_space) {
				const d = await retrieve(expanded_space[0], expanded_space[1])

				console.log(expanded_space, d)

				c_table_state((prev) => {
					return { ...prev, data: d, active: default_active - expanded_space[0] }
				})
			} else {
				c_table_state((prev) => {
					return { ...prev, data: [], active: null }
				})
			}
		}
	}, [default_active])

	useEffect(() => {
		effect();
		async function effect() {
			if (set_iid && table_state.iid_column !== null && (table_state.active || table_state.active === 0)) {
				const iid_row = await retrieve(table_state.active, table_state.active);
				if (iid_row && iid_row[0]) set_iid(iid_row[0][table_state.iid_column], input_key)

				const new_row = [];

				if (iid_row[0]) {
					for (let i = 0; i < iid_row[0].length; i++) {
						if (i == table_state.iid_column) continue;
						new_row.push(iid_row[0][i]);
					}
				}

				if (set_val) {
					set_val(meta.title, meta.column_names, meta.column_types, new_row, meta.xid)
				}
			}
		}
	}, [table_state.iid_column, table_state.active])

	useEffect(() => {
		if (!table_state.data.length) return;
		skip = true;
		// refMain.current.scrollTo({
		// 	top: rem2px(2 * table_state.window_offset),
		// 	behavior: "instant",
		// });
		const expected_index = table_state.primary_index === 0 ? 0 : 1;
		refMain.current.scrollTo({
			top: (expected_index * block_size + table_state.offset) * rem2px(2),
			behavior: "instant",
		});
		// refMain.current.scrollTop = (expected_index * block_size + table_state.offset) * rem2px(2);
	}, [table_state.data]);

	useEffect(() => {
		if (!table_state.data.length) return;
		skip = true;
		// console.log("offset", table_state.offset)
		const expected_index = table_state.primary_index === 0 ? 0 : 1;
		refMain.current.scrollTo({
			top: (expected_index * block_size + table_state.offset) * rem2px(2),
			behavior: "instant",
		});
	}, [table_state.active])

	function rem2px(rem) {
		return rem * parseFloat(getComputedStyle(refRow.current).fontSize);
	}

	function calc_y() {
		return refMain.current.scrollTop / rem2px(2);
	}

	async function jump_to(row, activate) {
		const n_primary_index = Math.floor(row / block_size);

		const lower = Math.max((n_primary_index - 1) * block_size, 0);
		const upper = Math.min((n_primary_index + 2) * block_size, meta.rows);

		const d = await retrieve(lower, upper);
		// console.log(d);

		skip = true;

		if (!refRow.current) return;

		if (activate && set_iid && table_state.iid_column !== null) {
			const iid_row = await retrieve(row, row);
			if (iid_row) set_iid(iid_row[0][table_state.iid_column], input_key)
		}

		c_table_state((prev) => {
			if (activate) return { ...prev, active: row, primary_index: Math.floor(row / block_size), offset: row % block_size, data: d }
			return { ...prev, primary_index: Math.floor(row / block_size), offset: row % block_size, data: d }
		})

		scroll();
	}

	// async function scroll(event) {
	// 	const local_y = calc_y();

	// 	if (skip) {
	// 		skip = false;
	// 		return;
	// 	}

	// 	if (local_y != Math.round(local_y)) return;

	// 	const delta_y = local_y - table_state.window_offset;

	// 	const current_y = table_state.offset + delta_y;

	// 	const data_lower_bound = Math.max(current_y - preload, 0);
	// 	const data_upper_bound = Math.min(current_y + window_size + preload, 25);
	// 	const window_offset = Math.min(current_y, preload);

	// 	const new_data = await retrieve(data_lower_bound, data_upper_bound);
	// 	skip = true;
	// 	c_table_state((prev) => {
	// 		return { ...prev, data: new_data, offset: current_y, prev_local_y: local_y, window_offset: window_offset };
	// 	});
	// }

	async function scroll(event) {
		const local_y = calc_y();
		const local_index = Math.floor(local_y / block_size);
		const local_offset = local_y - local_index * block_size;
		let n_primary_index = 0;

		if (skip) {
			skip = false;
			return;
		}

		const expected_index = table_state.primary_index === 0 ? 0 : 1;

		if (Math.round(local_y) !== local_y) {
			return;
		}

		c_table_state((prev) => {
			return { ...prev, offset: local_offset };
		});

		if (local_index === expected_index) {
			return;
		}

		if (local_index < expected_index) {
			n_primary_index = Math.max(table_state.primary_index - 1, 0)
		} else if (local_index > expected_index) {
			n_primary_index = Math.min(table_state.primary_index + 1, Math.floor(meta.rows / block_size) - 1)
		}

		const lower = Math.max((n_primary_index - 1) * block_size, 0);
		const upper = Math.min((n_primary_index + 2) * block_size, meta.rows);

		// console.log(n_primary_index, lower, upper, local_index, local_offset);

		const d = await retrieve(lower, upper);
		// console.log(lower, upper, d);
		c_table_state((prev) => {
			return { ...prev, data: d, primary_index: n_primary_index, offset: local_offset };
		});
	}

	async function scroll_to(row) {
		let n_primary_index = Math.floor(row / block_size)
		let n_offset = row % block_size;

		const lower = Math.max((n_primary_index - 1) * block_size, 0);
		const upper = Math.min((n_primary_index + 2) * block_size, meta.rows);

		const d = await retrieve(lower, upper);
		console.log(d);
		c_table_state((prev) => {
			return { ...prev, data: d, primary_index: n_primary_index, n_offset };
		});

		const expected_index = table_state.primary_index === 0 ? 0 : 1;

		refMain.current.scrollTo({
			top: (expected_index * block_size + table_state.offset) * rem2px(2),
			behavior: "instant",
		});
	}

	async function config_open() {
		if (table_state.config) refConfig.current.classList.remove("div-table-config-open");
		else refConfig.current.classList.add("div-table-config-open");
		c_table_state((prev) => {
			return { ...prev, config: !prev.config };
		});
	}

	async function set_pinned(index, event) {
		if (!event.target.checked) {
			c_table_state((prev) => {
				return { ...prev, pinned: null };
			});
		} else {
			c_table_state((prev) => {
				return { ...prev, active: prev.primary_index * block_size + index, pinned: prev.primary_index * block_size + index };
			});
		}
	}

	async function set_view(index, event) {
		event.target.value = ""
		if (typeof index !== "number") return;
		if (isNaN(index)) return;
		if (index < 0) return;
		if (meta.type === "expanded" && (!expanded_space || expanded_space && index > expanded_space[1] - expanded_space[0])) return;
		if (index > meta.rows) return;
		jump_to(index)
	}

	async function set_active(index) {
		if (table_state.pinned !== null) return;
		c_table_state((prev) => {
			return { ...prev, active: prev.primary_index * block_size + index, };
		});
	}

	async function set_column(index) {
		c_table_state((prev) => {
			prev.columns[index] = !prev.columns[index];
			return { ...prev, columns: prev.columns };
		});
	}

	async function set_collaspsed() {
		c_table_state((prev) => {
			return { ...prev, collapsed: !prev.collapsed };
		});
	}

	async function set_row(event) {
		console.log(event.target.value)
		c_table_state((prev) => {
			return { ...prev, offset: event.target.value };
		});
	}

	async function search(event) {
		if (event.target.value === "") {
			c_table_state((prev) => {
				return { ...prev, highlight: null };
			});

			return;
		}
		for (let i = 0; i < table_state.data.length; i++) {
			for (let j = 0; j < table_state.data[i].length; j++) {
				if (event.target.value == table_state.data[i][j]) {
					c_table_state((prev) => {
						return { ...prev, highlight: i };
					});

					return;
				}
			}
		}
	}

	return (
		<section className="section-table">
			<header>
				<div className="div-table-title"> {(meta && meta.title) || "Untitled Table"} </div>
				{/* <input type="text" className="input-search" placeholder="Search" onChange={(event) => search(event)} /> */}
				<div className="div-table-config" ref={refConfig}>
					<div className="div-table-config-overlay" onClick={config_open}>
						<span> Select Columns </span>
						<span className="span-table-dropdown">
							<img className="img-table-dropdown-icon" src={icon_dropdown} alt="" />
							{/* <span className="span-table-dropdown-icon"> ▾ </span> */}
						</span>
					</div>
					<div className="div-table-config-options">
						<div className="div-table-config-desc"> Select Columns </div>
						{meta.column_names.map((column_name, i) => {
							return (
								<div className="div-table-config-option" key={i} onClick={() => set_column(i)}>
									<div className="div-column-name"><span className="span-column-name"> {column_name} </span></div>
									<span className="span-column-status"> {table_state.columns[i] == true ? "✓" : ""} </span>
								</div>
							);
						})}
					</div>
				</div>
				{
					context_down && <div className="div-table-action">
						<button className="button-drill" onClick={() => change_ctx(context_down)}> Drill Down to Child Context </button>
					</div>
				}

				{
					context_up && <div className="div-table-action">
						<button className="button-drill" onClick={() => change_ctx(context_up)}> Drill Up to Parent Context </button>
					</div>
				}
				<div className="div-table-action div-table-collapse">
					<button className="button-collapse" onClick={set_collaspsed}>
						{!table_state.collapsed && <><span> Minimize </span> <img className="img-table-icon" src={icon_collapse} /></>}
						{table_state.collapsed && <><span> Expand </span> <img className="img-table-icon" src={icon_expand} /></>}
					</button>
				</div>
			</header>
			<main onScroll={scroll} ref={refMain} style={{ height: table_state.collapsed ? "4rem" : "12rem", overflow: table_state.collapsed ? "hidden" : "auto" }}>
				{!table_state.data.length && <div className="div-table-no-data"> NO DATA {/*¯\_(ツ)_/¯*/} </div>}

				{!!table_state.data.length && (
					<table>
						<thead>
							<tr ref={refRow}>
								{/* <th className="th-pin"> 📌&#xFE0E; </th> */}
								<th className="th-pin"> # </th>

								{meta.column_names.map((column_name, i) => {
									if (!table_state.columns[i]) return;
									const tag = final_cols && final_cols.includes(meta.column_ids[i])
									return <th key={i}> {column_name} {tag && <div className="div-tag"> output column </div>}</th>;
								})}
							</tr>
						</thead>
						<tbody>
							{table_state.data.map((row, i) => {
								const expected_index = table_state.primary_index === 0 ? 0 : 1;
								let isActive = (table_state.primary_index - expected_index) * block_size + i === table_state.active;
								let isPinned = (table_state.primary_index - expected_index) * block_size + i === table_state.pinned;
								let isHighlightedClass = i === table_state.highlight ? "tr-highlight" : "";
								let enable = isPinned || table_state.pinned === null;
								let isActiveClass = isActive ? "tr-active" : "";
								let isHoverableClass = table_state.pinned === null && !isActive ? "tr-hoverable" : "";

								if (!row) return;

								return (
									<tr key={i} className={isHoverableClass + " " + isActiveClass + " " + isHighlightedClass} onClick={() => set_active(i - expected_index * block_size)}>
										<td>
											<input type="checkbox" onChange={(event) => set_pinned(i, event)} checked={isPinned} disabled={!enable} />
										</td>
										{row.map((cell, k) => {
											if (!table_state.columns[k]) return;
											return <td key={k}> {String(cell)} </td>;
										})}
									</tr>
								);
							})}
							{!((table_state.primary_index * block_size + table_state.offset + 5 >= meta.rows) || (expanded_space && (table_state.primary_index * block_size + table_state.offset + 5 >= expanded_space[1] - expanded_space[0]))) && <tr className="tr-loading-indicator">
								<div className="div-loading-indicator-wrapper">
									<div className="div-loading-indicator">
										<LoadingIndicator></LoadingIndicator>
									</div>
									<div className="div-loading-text"> Loading... </div>
								</div>
							</tr>}
						</tbody>
					</table>
				)}
			</main>
			<footer>
				<span className="span-footer-left">
					{/* Displaying row <input type="text" value={table_state.primary_index * block_size + table_state.offset + 1} onChange={(event) => set_row(event)} /> out of {meta.rows} rows */}

					{/* {!table_state.collapsed && <> Displaying rows {Math.max(table_state.primary_index * block_size + table_state.offset + 1, 1)} to {Math.min(table_state.primary_index * block_size + table_state.offset + 5, meta.rows)} out of {meta.rows} rows </>}
					{table_state.collapsed && <> Displaying row {Math.min(Math.max(table_state.primary_index * block_size + table_state.offset + 1, 1), meta.rows)} out of {meta.rows} rows </>} */}

					{((meta.type === "expanded" && !expanded_space) || meta.rows === 0) && <> No records </>}

					{!(meta.type === "expanded" && !expanded_space) && !table_state.collapsed && meta.rows !== 0 && <> {Math.min(Math.max(table_state.primary_index * block_size + table_state.offset + 1, 1), expanded_space ? expanded_space[1] - expanded_space[0] : (meta.type === "expanded" ? 0 : Infinity))}-{meta.type === "expanded" ? Math.min(table_state.primary_index * block_size + table_state.offset + 5, meta.rows, expanded_space ? expanded_space[1] - expanded_space[0] + 1 : 0) : Math.min(table_state.primary_index * block_size + table_state.offset + 5, meta.rows)} of {meta.type === "expanded" ? (expanded_space ? expanded_space[1] - expanded_space[0] + 1 : 0) : meta.rows} </>}
					{!(meta.type === "expanded" && !expanded_space) && table_state.collapsed && meta.rows !== 0 && <> {Math.min(Math.max(table_state.primary_index * block_size + table_state.offset + 1, 1), meta.rows)} of {meta.rows} </>}

				</span>

				{meta.type !== "from" &&
					<span className={"span-footer-status " + ((default_active || default_active === 0 || table_state.active || table_state.active === 0) ? "span-footer-match" : "span-footer-none")}>
						{(default_active || default_active === 0 || table_state.active || table_state.active === 0) && meta.type !== "from" && `Row ${table_state.active + 1 ?? default_active + 1} corresponds to active input combination`}
						{!(default_active || default_active === 0 || table_state.active || table_state.active === 0) && meta.type !== "from" && "No row corresponds to active input combination"}
					</span>
				}

				<span className="span-footer-right">
					{(meta.type === "expanded" && !expanded_space) || meta.rows === 0 && <> No records </>}
					{!(meta.type === "expanded" && !expanded_space) && meta.rows !== 0 && <> Jump to <input type="text" onBlur={(event) => { set_view(parseInt(event.target.value) - 1, event) }} /> of {meta.type === "expanded" ? (expanded_space ? expanded_space[1] - expanded_space[0] + 1 : 0) : meta.rows} </>}
				</span>
			</footer>
		</section>
	);
}

export default Table;
