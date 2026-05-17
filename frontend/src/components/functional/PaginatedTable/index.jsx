import { useEffect, useState, useRef } from "react";
import constants from "../../../state/constants";
import LoadingIndicator from "../LoadingIndicator";

import icon_collapse from "./assets/icon_collapse.svg";
import icon_expand from "./assets/icon_expand.svg";
import icon_dropdown from "./assets/icon_dropdown.svg";
import icon_left from "./assets/icon_left.svg";
import icon_right from "./assets/icon_right.svg";

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

	const block_size = constants.block_size;
	// We'll keep the original "two-block" window as a single page for pagination
	const pageSize = 5;

	const [table_state, c_table_state] = useState({
		data: [],
		// page is the current 0-based page index
		page: 0,
		config: false,
		columns: default_cols ?? [],
		active: default_active,
		pinned: null,
		collapsed: false,
		highlight: null,
		iid_column: iid_col,
	});

	let refRow = useRef();
	let refConfig = useRef();

	// initial load: page 0
	useEffect(() => {
		(async function effect() {
			const d = await retrieve(0, pageSize);
			c_table_state((prev) => {
				return { ...prev, data: d, page: 0 };
			});
		})();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	// respond to changes in default_active (e.g., external selection)
	useEffect(() => {
		(async function effect() {
			if (meta.type !== "expanded") {
				c_table_state((prev) => {
					return { ...prev, active: default_active };
				});

				if (default_active !== undefined && default_active !== null)
					await jump_to(default_active, true);
			} else if (expanded_space) {
				// If we're in expanded mode with a provided space, load that range directly.
				const d = await retrieve(expanded_space[0], expanded_space[1]);
				c_table_state((prev) => {
					// Keep active relative to the returned data as before
					return {
						...prev,
						data: d,
						page: 0,
						active:
							default_active != null
								? default_active - expanded_space[0]
								: null,
					};
				});
			} else {
				c_table_state((prev) => {
					return { ...prev, data: [], active: null };
				});
			}
		})();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [default_active]);

	// when active changes and we have iid column, fetch iid and set input values
	useEffect(() => {
		(async function effect() {
			if (
				set_iid &&
				table_state.iid_column !== null &&
				(table_state.active || table_state.active === 0)
			) {
				// retrieve single row for the active index
				const iid_row = await retrieve(table_state.active, table_state.active);
				if (iid_row && iid_row[0]) set_iid(iid_row[0][table_state.iid_column], input_key);

				const new_row = [];

				if (iid_row && iid_row[0]) {
					for (let i = 0; i < iid_row[0].length; i++) {
						if (i == table_state.iid_column) continue;
						new_row.push(iid_row[0][i]);
					}
				}

				if (set_val) {
					set_val(meta.title, meta.column_names, meta.column_types, new_row);
				}
			}
		})();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [table_state.iid_column, table_state.active]);

	// helper: go to page (0-based)
	async function goToPage(newPage) {
		if (!meta || !meta.rows) return;
		const maxPage = Math.max(Math.ceil(meta.rows / pageSize) - 1, 0);
		const clamped = Math.max(0, Math.min(newPage, maxPage));
		const start = clamped * pageSize;
		const end = Math.min(start + pageSize, meta.rows);

		const d = await retrieve(start, end);
		c_table_state((prev) => {
			return { ...prev, data: d, page: clamped };
		});
	}

	function prevPage() {
		goToPage(table_state.page - 1);
	}

	function nextPage() {
		goToPage(table_state.page + 1);
	}

	// jump_to a specific absolute row index, optionally activate it
	async function jump_to(row, activate) {
		if (row == null || isNaN(row)) return;
		const n_page = Math.floor(row / pageSize);
		const start = Math.max(n_page * pageSize, 0);
		const end = Math.min(start + pageSize, meta.rows);

		const d = await retrieve(start, end);

		if (activate && set_iid && table_state.iid_column !== null) {
			const iid_row = await retrieve(row, row);
			if (iid_row && iid_row[0]) set_iid(iid_row[0][table_state.iid_column], input_key);
		}

		c_table_state((prev) => {
			if (activate) return { ...prev, page: n_page, data: d, active: row };
			return { ...prev, page: n_page, data: d };
		});
	}

	function config_open() {
		if (table_state.config) refConfig.current.classList.remove("div-table-config-open");
		else refConfig.current.classList.add("div-table-config-open");
		c_table_state((prev) => {
			return { ...prev, config: !prev.config };
		});
	}

	function set_pinned(index, event) {
		const startIndex = table_state.page * pageSize;
		const rowIndex = startIndex + index;

		if (!event.target.checked) {
			c_table_state((prev) => {
				return { ...prev, pinned: null };
			});
		} else {
			c_table_state((prev) => {
				return { ...prev, active: rowIndex, pinned: rowIndex };
			});
		}
	}

	function set_view(index, event) {
		event.target.value = "";
		if (typeof index !== "number") return;
		if (isNaN(index)) return;
		if (index < 0) return;
		if (meta.type === "expanded" && (!expanded_space || (expanded_space && index > expanded_space[1] - expanded_space[0])))
			return;
		if (index > meta.rows) return;
		jump_to(index);
	}

	function set_active(index) {
		// index is local index within current page (0-based)
		if (table_state.pinned !== null) return;
		const startIndex = table_state.page * pageSize;
		c_table_state((prev) => {
			return { ...prev, active: startIndex + index };
		});
	}

	function set_column(index) {
		c_table_state((prev) => {
			const cols = [...prev.columns];
			cols[index] = !cols[index];
			return { ...prev, columns: cols };
		});
	}

	function set_collaspsed() {
		c_table_state((prev) => {
			return { ...prev, collapsed: !prev.collapsed };
		});
	}

	async function set_row(event) {
		// allow jumping to a specific row number (1-based in UI)
		const val = Number(event.target.value);
		if (isNaN(val)) return;
		const row = Math.max(0, val - 1);
		await jump_to(row, false);
	}

	function search(event) {
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

	// computed helpers for render
	const totalRows = meta ? (meta.type === "expanded" && expanded_space ? expanded_space[1] - expanded_space[0] + 1 : meta.rows) : 0;
	const currentStartIndex = table_state.page * pageSize;
	const currentEndIndex = Math.min(currentStartIndex + pageSize, totalRows);

	return (
		<section className="section-table">
			<header>
				<div className="div-table-title"> {(meta && meta.title) || "Untitled Table"} </div>

				<div className="div-table-config" ref={refConfig}>
					<div className="div-table-config-overlay" onClick={config_open}>
						<span> Select Columns </span>
						<span className="span-table-dropdown">
							<img className="img-table-dropdown-icon" src={icon_dropdown} alt="" />
						</span>
					</div>
					<div className="div-table-config-options">
						<div className="div-table-config-desc"> Select Columns </div>
						{meta &&
							meta.column_names.map((column_name, i) => {
								return (
									<div className="div-table-config-option" key={i} onClick={() => set_column(i)}>
										<div className="div-column-name"><span className="span-column-name"> {column_name} </span></div>
										<span className="span-column-status"> {table_state.columns[i] == true ? "✓" : ""} </span>
									</div>
								);
							})}
					</div>
				</div>

				{context_down && <div className="div-table-action">
					<button className="button-drill" onClick={() => change_ctx(context_down)}> Drill Down to Child Context </button>
				</div>}

				{context_up && <div className="div-table-action">
					<button className="button-drill" onClick={() => change_ctx(context_up)}> Drill Up to Parent Context </button>
				</div>}

				<div className="div-table-action div-table-collapse">
					<button className="button-collapse" onClick={set_collaspsed}>
						{!table_state.collapsed && <><span> Minimize </span> <img className="img-table-icon" src={icon_collapse} alt="" /></>}
						{table_state.collapsed && <><span> Expand </span> <img className="img-table-icon" src={icon_expand} alt="" /></>}
					</button>
				</div>
			</header>

			<main className="main-paginated" style={{ height: table_state.collapsed ? "4rem" : "12rem" }}>
				{!table_state.data.length && <div className="div-table-no-data"> NO DATA </div>}

				{!!table_state.data.length && (
					<table>
						<thead>
							<tr ref={refRow}>
								{/* <th className="th-pin"> 📌&#xFE0E; </th> */}
								<th className="th-pin" style={{ color: "#7f7f7f" }}> # </th>
								{meta &&
									meta.column_names.map((column_name, i) => {
										if (!table_state.columns[i]) return null;
										const tag = final_cols && final_cols.includes(meta.column_ids[i]);
										return <th key={i}> {column_name} {tag && <div className="div-tag"> output column </div>}</th>;
									})}
							</tr>
						</thead>
						<tbody>
							{table_state.data.map((row, i) => {
								if (!row) return null;
								const rowIndex = currentStartIndex + i;
								let isActive = rowIndex === table_state.active;
								let isPinned = rowIndex === table_state.pinned;
								let isHighlightedClass = i === table_state.highlight ? "tr-highlight" : "";
								let enable = isPinned || table_state.pinned === null;
								let isActiveClass = isActive ? "tr-active" : "";
								let isHoverableClass = table_state.pinned === null && !isActive ? "tr-hoverable" : "";

								return (
									<tr key={i} className={isHoverableClass + " " + isActiveClass + " " + isHighlightedClass} onClick={() => set_active(i)}>
										<td style={{ color: "#7f7f7f" }}>
											{(table_state.page) * 5 + i + 1}
											{/* <input type="checkbox" onChange={(event) => set_pinned(i, event)} checked={isPinned} disabled={!enable} /> */}
										</td>
										{row.map((cell, k) => {
											if (!table_state.columns[k]) return null;
											return <td key={k}> {String(cell)} </td>;
										})}
									</tr>
								);
							})}
						</tbody>
					</table>
				)}
			</main>

			<footer>
				<span className="span-footer-left">
					{((meta && meta.type === "expanded" && !expanded_space) || (meta && meta.rows === 0)) && <> No records </>}
					{meta && !(meta.type === "expanded" && !expanded_space) && !table_state.collapsed && meta.rows !== 0 && <>
						{currentStartIndex + 1}-{currentEndIndex} of {totalRows}
					</>}
					{meta && !(meta.type === "expanded" && !expanded_space) && table_state.collapsed && meta.rows !== 0 && <>
						{Math.min(Math.max(table_state.active + 1 ?? 1, 1), meta.rows)} of {meta.rows}
					</>}
				</span>

				{meta && meta.type !== "from" &&
					<span className={"span-footer-status " + ((default_active || default_active === 0 || table_state.active || table_state.active === 0) ? "span-footer-match" : "span-footer-none")}>
						{(default_active || default_active === 0 || table_state.active || table_state.active === 0) && meta.type !== "from" && `Row ${(table_state.active != null ? table_state.active + 1 : default_active + 1)} corresponds to active input combination`}
						{!(default_active || default_active === 0 || table_state.active || table_state.active === 0) && meta.type !== "from" && "No row corresponds to active input combination"}
					</span>
				}

				<span className="span-footer-right">
					<button className="button-paginate" onClick={prevPage} disabled={table_state.page === 0}> <img src={icon_left} alt="" /> </button>
					<span className="span-paginate">page {table_state.page + 1} of {meta ? Math.max(Math.ceil(totalRows / pageSize), 1) : 1}</span>
					<button className="button-paginate" onClick={nextPage} disabled={currentEndIndex >= totalRows}> <img src={icon_right} alt="" /> </button>
					{/* quick jump to row input (1-based) */}
					<input
						type="text"
						placeholder="row #"
						onKeyDown={(e) => {
							if (e.key === "Enter") {
								set_row(e);
								e.target.value = ""
							}
						}}
					/>
				</span>
			</footer>
		</section>
	);
}

export default Table;
