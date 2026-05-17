import { useEffect, useState, useRef } from "react";
import LoadingIndicator from "./LoadingIndicator";

// import "./Table.css";

function Table(props) {
	const retrieve = props.retrieve;
	const meta = props.meta;
	const active = props.active;

	const [state, c_state] = useState({
		data: [],
		offset: 0,
		prev_local_y: 0,
		window_offset: 0,
		config: false,
		columns: [],
		active: null, // being an "active" row does not necessarily mean it is pinned (there could be nothing pinned)
		pinned: null, // being a "pinned" row DOES necessarily mean it is active
		collapsed: false,
		prev_absolute_y: 0,
		prev_data_y: 0,
	});

	let refMain = useRef();
	let refRow = useRef();
	let refConfig = useRef();

	const preload = 10;
	const window_size = 5;

	let skip = false;

	useEffect(() => {
		scroll();
	}, [active])

	useEffect(() => {
		effect();

		async function effect() {
			const column = [];
			for (let i = 0; i < meta.column_names.length; i++) {
				column[i] = true;
				if (meta.column_names[i] == "IID") {
					column[i] = false;
				}
			}

			const d = await retrieve(0, preload + window_size);
			c_state((prev) => {
				return { ...prev, data: d, columns: column };
			});
		}
	}, []);

	useEffect(() => {
		if (state.data.length) {
			skip = true;
			// refMain.current.scrollTo({
			// 	top: rem2px(2 * state.window_offset),
			// 	behavior: "instant",
			// });
		}
	}, [state]);

	function rem2px(rem) {
		return rem * 16;
	}

	function calc_y() {
		return refMain.current.scrollTop / rem2px(2);
	}

	async function scroll(event) {
		const local_y = calc_y();

		if (skip) {
			skip = false;
			return;
		}

		if (local_y != Math.round(local_y)) return;

		const delta_y = local_y - state.window_offset;

		const current_y = state.offset + delta_y;

		const data_lower_bound = Math.max(current_y - preload, 0);
		const data_upper_bound = Math.min(current_y + window_size + preload, 25);
		const window_offset = Math.min(current_y, preload);

		const new_data = await retrieve(data_lower_bound, data_upper_bound);
		skip = true;
		c_state((prev) => {
			return { ...prev, data: new_data, offset: current_y, prev_local_y: local_y, window_offset: window_offset };
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
				return { ...prev, active: index, pinned: index };
			});
		}
	}

	async function set_active(index) {
		if (state.pinned !== null) return;
		c_state((prev) => {
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

	return (
		<section className="section-table section-table-small">
			<header>
				<div className="div-table-title"> {(meta && meta.title) || "Untitled Table"} </div>
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
								let isActive = i === state.active;
								let isPinned = i === state.pinned;
								let enable = isPinned || state.pinned === null;
								let isActiveClass = isActive ? "tr-active" : "";
								let isHoverableClass = state.pinned === null && !isActive ? "tr-hoverable" : "";

								return (
									<tr key={i} className={isHoverableClass + " " + isActiveClass} onClick={() => set_active(i)}>
										<td>
											<input type="checkbox" onChange={(event) => set_pinned(i, event)} checked={isPinned} disabled={!enable} />
										</td>
										{row.map((cell, k) => {
											if (!state.columns[k]) return;
											return <td key={k}> {cell} </td>;
										})}
									</tr>
								);
							})}
						</tbody>
					</table>
				)}
			</main>
			<footer>
				<span>
					Displaying row {state.offset + 1} {/*} <input type="text" value={state.offset + 1} onChange={(event) => set_row(event)} /> */} out of {meta.rows} rows
				</span>

				{/* <span>
					Jump to <input type="text" onfocus="this.select()" /> of 100 pages
				</span> */}
			</footer>
		</section>
	);
}

export default Table;
