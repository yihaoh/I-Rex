import { useEffect, useState, useRef } from "react";
import LoadingIndicator from "./LoadingIndicator";

// import "./Table.css";

function Table(props) {
	const retrieve = props.retrieve;
	const meta = props.meta;
	const default_active = props.active_row;
	const set_act = props.set_act;

	const [state, c_state] = useState({
		data: [],
		offset: 0,
		prev_local_y: 0,
		window_offset: 0,
		config: false,
		columns: [],
		active: default_active, // being an "active" row does not necessarily mean it is pinned (there could be nothing pinned)
		pinned: null, // being a "pinned" row DOES necessarily mean it is active
		collapsed: false,
		prev_absolute_y: 0,
		prev_data_y: 0,
		highlight: null,
		primary_index: 0,
		offset: 0,
	});

	let refMain = useRef();
	let refRow = useRef();
	let refConfig = useRef();

	const preload = 10;
	const window_size = 5;
	const block_size = 10;

	let skip = false;

	useEffect(() => {
		effect();

		async function effect() {
			const column = [];
			for (let i = 0; i < meta.column_names.length; i++) {
				column[i] = true;
				if (meta.column_use[i] === "IID" || meta.column_use[i] === "NEXT_IID" /*|| meta.hooks[i].length != 0*/) {
					column[i] = false;
				}
			}

			const d = await retrieve(0, block_size * 2);
			c_state((prev) => {
				return { ...prev, data: d, columns: column };
			});
		}
	}, []);

	useEffect(() => {
		effect();

		async function effect() {
			const n_primary_index = Math.floor(default_active / block_size);

			const lower = Math.max((n_primary_index - 1) * block_size, 0);
			const upper = Math.min((n_primary_index + 2) * block_size, meta.rows);

			const d = await retrieve(lower, upper);
			console.log(d);

			skip = true;

			if (!refRow.current) return;

			c_state((prev) => {
				return { ...prev, active: default_active, primary_index: Math.floor(default_active / block_size), offset: default_active % block_size, data: d }
			})

			scroll();
		}
	}, [default_active])

	useEffect(() => {
		if (!state.data.length) return;
		skip = true;
		// refMain.current.scrollTo({
		// 	top: rem2px(2 * state.window_offset),
		// 	behavior: "instant",
		// });
		const expected_index = state.primary_index === 0 ? 0 : 1;
		refMain.current.scrollTo({
			top: (expected_index * block_size + state.offset) * rem2px(2),
			behavior: "instant",
		});
		// refMain.current.scrollTop = (expected_index * block_size + state.offset) * rem2px(2);
	}, [state.data]);

	useEffect(() => {
		if (!state.data.length) return;
		skip = true;
		console.log("offset", state.offset)
		const expected_index = state.primary_index === 0 ? 0 : 1;
		refMain.current.scrollTo({
			top: (expected_index * block_size + state.offset) * rem2px(2),
			behavior: "instant",
		});
	}, [state.active])

	function rem2px(rem) {
		return rem * parseFloat(getComputedStyle(refRow.current).fontSize);
	}

	function calc_y() {
		return refMain.current.scrollTop / rem2px(2);
	}

	// async function scroll(event) {
	// 	const local_y = calc_y();

	// 	if (skip) {
	// 		skip = false;
	// 		return;
	// 	}

	// 	if (local_y != Math.round(local_y)) return;

	// 	const delta_y = local_y - state.window_offset;

	// 	const current_y = state.offset + delta_y;

	// 	const data_lower_bound = Math.max(current_y - preload, 0);
	// 	const data_upper_bound = Math.min(current_y + window_size + preload, 25);
	// 	const window_offset = Math.min(current_y, preload);

	// 	const new_data = await retrieve(data_lower_bound, data_upper_bound);
	// 	skip = true;
	// 	c_state((prev) => {
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

		const expected_index = state.primary_index === 0 ? 0 : 1;

		if (Math.round(local_y) !== local_y) {
			return;
		}

		c_state((prev) => {
			return { ...prev, offset: local_offset };
		});

		if (local_index === expected_index) {
			return;
		}

		if (local_index < expected_index) {
			n_primary_index = Math.max(state.primary_index - 1, 0)
		} else if (local_index > expected_index) {
			n_primary_index = Math.min(state.primary_index + 1, Math.floor(meta.rows / block_size) - 1)
		}

		const lower = Math.max((n_primary_index - 1) * block_size, 0);
		const upper = Math.min((n_primary_index + 2) * block_size, meta.rows);

		console.log(n_primary_index, lower, upper, local_index, local_offset);

		const d = await retrieve(lower, upper);
		console.log(d);
		c_state((prev) => {
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
		c_state((prev) => {
			return { ...prev, data: d, primary_index: n_primary_index, n_offset };
		});

		const expected_index = state.primary_index === 0 ? 0 : 1;

		refMain.current.scrollTo({
			top: (expected_index * block_size + state.offset) * rem2px(2),
			behavior: "instant",
		});
	}

	async function config_open() {
		if (state.config) refConfig.current.classList.remove("div-table-config-open");
		else refConfig.current.classList.add("div-table-config-open");
		c_state((prev) => {
			return { ...prev, config: !prev.config };
		});
	}

	async function set_pinned(index, event) {
		if (!event.target.checked) {
			c_state((prev) => {
				return { ...prev, pinned: null };
			});
		} else {
			c_state((prev) => {
				return { ...prev, active: prev.primary_index * block_size + index, pinned: prev.primary_index * block_size + index };
			});
		}
	}

	async function set_active(index) {
		if (state.pinned !== null) return;
		c_state((prev) => {
			for (let i = 0; i < meta.column_names.length; i++) {
				if (meta.column_names[i] == "IID") {
					set_act(prev.data[index][i][0]);
				}
			}
			return { ...prev, active: index, };
		});
	}

	async function set_column(index) {
		c_state((prev) => {
			prev.columns[index] = !prev.columns[index];
			return { ...prev, columns: prev.columns };
		});
	}

	async function set_collaspsed() {
		c_state((prev) => {
			return { ...prev, collapsed: !prev.collapsed };
		});
	}

	async function set_row(event) {
		console.log(event.target.value)
		c_state((prev) => {
			return { ...prev, offset: event.target.value };
		});
	}

	async function search(event) {
		if (event.target.value === "") {
			c_state((prev) => {
				return { ...prev, highlight: null };
			});

			return;
		}
		for (let i = 0; i < state.data.length; i++) {
			for (let j = 0; j < state.data[i].length; j++) {
				if (event.target.value == state.data[i][j]) {
					c_state((prev) => {
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
				<input type="text" className="input-search" placeholder="Search" onChange={(event) => search(event)} />
				<div className="div-table-config" ref={refConfig}>
					<div className="div-table-config-overlay" onClick={config_open}>
						<span> Select Columns </span>
						<span className="span-table-dropdown">
							<span className="span-table-dropdown-icon"> ▾ </span>
						</span>
					</div>
					<div className="div-table-config-options">
						<div className="div-table-config-desc"> Select Columns </div>
						{meta.column_names.map((column_name, i) => {
							return (
								<div className="div-table-config-option" key={i} onClick={() => set_column(i)}>
									<span className="span-column-name"> {column_name} </span>
									<span className="span-column-status"> {state.columns[i] == true ? "✓" : ""} </span>
								</div>
							);
						})}
					</div>
				</div>
				<div className="div-table-action">
					<button className="button-collapse" onClick={set_collaspsed}>
						{!state.collapsed && "Minimize -"}
						{state.collapsed && "Expand +"}
					</button>
				</div>
			</header>
			<main onScroll={scroll} ref={refMain} style={{ height: state.collapsed ? "4rem" : "12rem", overflow: state.collapsed ? "hidden" : "auto" }}>
				{!state.data.length && <div className="div-table-no-data"> NO DATA ¯\_(ツ)_/¯ </div>}

				{!!state.data.length && (
					<table>
						<thead>
							<tr ref={refRow}>
								<th className="th-pin"> 📌&#xFE0E; </th>
								{meta.column_names.map((column_name, i) => {
									if (!state.columns[i]) return;
									return <th> {column_name} </th>;
								})}
							</tr>
						</thead>
						<tbody>
							{state.data.map((row, i) => {
								const expected_index = state.primary_index === 0 ? 0 : 1;
								let isActive = (state.primary_index - expected_index) * block_size + i === state.active;
								let isPinned = (state.primary_index - expected_index) * block_size + i === state.pinned;
								let isHighlightedClass = i === state.highlight ? "tr-highlight" : "";
								let enable = isPinned || state.pinned === null;
								let isActiveClass = isActive ? "tr-active" : "";
								let isHoverableClass = state.pinned === null && !isActive ? "tr-hoverable" : "";

								return (
									<tr key={i} className={isHoverableClass + " " + isActiveClass + " " + isHighlightedClass} onClick={() => set_active(i)}>
										<td>
											<input type="checkbox" onChange={(event) => set_pinned(i, event)} checked={isPinned} disabled={!enable} />
										</td>
										{row.map((cell, k) => {
											if (!state.columns[k]) return;
											return <td key={k}> {String(cell)} </td>;
										})}
									</tr>
								);
							})}
							<tr className="tr-loading-indicator">
								<div className="div-loading-indicator-wrapper">
									<div className="div-loading-indicator">
										<LoadingIndicator></LoadingIndicator>
									</div>
									<div className="div-loading-text"> Loading... </div>
								</div>
							</tr>
						</tbody>
					</table>
				)}
			</main>
			<footer>
				<span>
					Displaying rows {state.primary_index * block_size + state.offset + 1} to {state.primary_index * block_size + state.offset + 5} {/*<input type="text" value={state.primary_index * block_size + state.offset + 1} onChange={(event) => set_row(event)} />*/} out of {meta.rows} rows
				</span>

				{/* <span>
					Jump to <input type="text" onfocus="this.select()" /> of 100 pages
				</span> */}
			</footer>
		</section>
	);
}

export default Table;