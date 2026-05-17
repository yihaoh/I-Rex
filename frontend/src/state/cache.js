import constants from "./constants";

class Cache {
	constructor() {
		this.raw = {};
		this.parsed = {};
		this.table = [];
		this.milestones = [];

		this.count = 0;
	}

	reset_context() {
		this.table = []
		this.milestones = []
		this.count = 0;
	}

	set_raw(typ, data) {
		this.raw[typ] = data;
	}

	set_parsed(typ, data) {
		this.parsed[typ] = data;
	}

	set_milestone(id, data) {
		this.milestones[id] = data;
	}

	get_milestone(id) {
		return this.milestones[id];
	}

	get_id(type, rows, pages) {
		const id = this.table.length;

		this.table[id] = {
			type: type,
			lower: 0,
			upper: 0,
			rows: rows,
			pages: pages,
			contents: [],
			fetched_pages: []
		}
		return id;
	}

	set_meta(id, meta) {
		this.table[id].meta = meta;

		// console.log(meta)
	}

	async fetch(meta, lower, upper) {
		const id = meta.id;

		const starting_page = Math.floor(lower / constants.chunk_size);
		const ending_page = Math.floor(upper / constants.chunk_size);

		if (!this.table[id]) return [];

		// console.log(starting_page, ending_page)

		// console.log(meta.type, id, starting_page, ending_page)

		for (let i = starting_page; i <= ending_page; i++) {
			if (this.table[id].fetched_pages[i]) continue;

			// console.log("1", meta.type)

			let d = [];
			const filters = {};
			let page_index = i;

			if (meta.milestone.length === 0) return [];

			if (!meta.milestone[page_index]) return [];

			if (meta.type == "from") {
				for (let expected_filter_key in meta.pageSQL.expected_filters) {

					let filter = meta.pageSQL.expected_filters[expected_filter_key]
					filter.iid_lower = meta.milestone[page_index][2]
					filter.iid_upper = meta.milestone[page_index + 1] ? meta.milestone[page_index + 1][2] : null;
					filter.sarg_values = meta.milestone[page_index].slice(3);
					filter.blm_value = null;

					filters[expected_filter_key] = filter
				}
			} else {
				for (let expected_filter_key in meta.pageSQL.expected_filters) {
					let filter = meta.pageSQL.expected_filters[expected_filter_key]
					let index = filter.milestone_index
					if (!(this.milestones && this.milestones[index] && this.milestones[index].length !== 0)) break;
					filter.iid_lower = this.milestones[index][page_index][2]
					filter.iid_upper = this.milestones[index][page_index + 1] ? this.milestones[index][page_index + 1][2] : null;
					filter.sarg_values = this.milestones[index][page_index].slice(3)
					// console.log("sarg values", filter.sarg_values)
					filter.blm_value = null;

					filters[expected_filter_key] = filter
				}
			}

			let context_bindings = meta.pageSQL.expected_bindings;

			for (let b in context_bindings) {
				const binding = context_bindings[b];
				let context_xid = binding.xSelectNode_id;
				let table_xid = this.id_map[context_xid].input_XTableValuedNode_ids[binding.xSelectNode_input_index];
				if (this.active_rows_map[table_xid]) {
					binding.value = this.active_rows_map[table_xid][binding.xSelectNode_input_column_index]
				}
			}

			// console.log("2", meta.type)

			await fetch(constants.backend + "/execute_page", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					db: constants.db,
					sql: meta.pageSQL,
					bindings: meta.pageSQL.expected_bindings,
					filters: filters,
					rows: [],
					reason: meta.type
				}),
			})
				.then((res) => res.json())
				.then((data) => {
					d = data.contents;
				});

			if (!d) return [];

			for (let i = 0; i < d.length; i++) {
				this.table[id].contents[page_index * constants.chunk_size + i] = d[i];
			}

			this.table[id].fetched_pages[i] = true;
		}

		let out = [];

		for (let i = 0; i <= upper - lower; i++) {
			out[i] = this.table[id].contents[lower + i];
		}

		// console.log(this.table[id])

		return out;

		// while (Math.min(upper, meta.rows) > this.table[id].upper) {
		// 	let d = [];
		// 	const filters = {};
		// 	let page_index = Math.floor(upper / constants.chunk_size)

		// 	if (meta.type == "from") {
		// 		for (let expected_filter_key in meta.pageSQL.expected_filters) {
		// 			let filter = meta.pageSQL.expected_filters[expected_filter_key]
		// 			filter.iid_lower = meta.milestone[page_index][2]
		// 			filter.iid_upper = meta.milestone[page_index + 1][2]
		// 			filter.sarg_values = null;
		// 			filter.blm_value = null;

		// 			filters[expected_filter_key] = filter
		// 		}
		// 	} else {
		// 		for (let expected_filter_key in meta.pageSQL.expected_filters) {
		// 			let filter = meta.pageSQL.expected_filters[expected_filter_key]
		// 			filter.iid_lower = meta.milestone[page_index][2]
		// 			filter.iid_upper = meta.milestone[page_index + 1][2]
		// 			filter.sarg_values = meta.milestone[page_index].slice(3)
		// 			filter.blm_value = null;

		// 			filters[expected_filter_key] = filter
		// 		}
		// 	}

		// 	await fetch(constants.backend + "/execute_page", {
		// 		method: "POST",
		// 		headers: {
		// 			"Content-Type": "application/json",
		// 		},
		// 		body: JSON.stringify({
		// 			db: constants.db,
		// 			sql: meta.pageSQL,
		// 			bindings: meta.pageSQL.expected_bindings,
		// 			filters: filters
		// 		}),
		// 	})
		// 		.then((res) => res.json())
		// 		.then((data) => {
		// 			d = data.contents;
		// 		});

		// 	for (let i = 0; i < d.length; i++) {
		// 		this.table[id].contents[page_index * constants.chunk_size + i] = d[i];
		// 	}

		// 	this.table[id].upper = Math.min(this.table[id].upper + constants.chunk_size, meta.rows, this.table[id].contents.length)
		// }

		// let out = [];

		// for (let i = 0; i < upper - lower; i++) {
		// 	out[i] = this.table[id].contents[lower + i];
		// }

		// // console.log(this.table[id])

		// return out;
	}

	async forward_trace(input_iids) {
		let join_filter_iids = []

		if (!input_iids) return {
			join_filter: null,
			group: null,
			expanded: null,
			expanded_space: null,
			final: null
		};

		for (let i = 0; i < input_iids.length; i++) {
			if (!input_iids[i]) break;
			for (let k = 0; k < input_iids[i].length; k++) {
				join_filter_iids.push(input_iids[i][k]);
			}
		}

		let join_filter_id = null, group_id = null, expanded_id = null, final_id = null;

		for (let i = 0; i < this.table.length; i++) {
			if (this.table[i].type == "join_filter") {
				join_filter_id = i;
			} else if (this.table[i].type == "group_by") {
				group_id = i;
			} else if (this.table[i].type == "expanded") {
				expanded_id = i;
			} else if (this.table[i].type == "final") {
				final_id = i;
			}
		}

		if (join_filter_id === null) return {
			join_filter: null,
			group: null,
			expanded: null,
			expanded_space: null,
			final: null,
		};

		// console.log(this.table[join_filter_id])

		// console.log("finding join filter")
		const join_filter_search = await this.binary_search(join_filter_id, join_filter_iids);
		if (join_filter_search === -1) return {
			join_filter: null,
			group: null,
			expanded: null,
			expanded_space: null,
			final: null
		};

		const join_filter_row = this.table[join_filter_id].contents[join_filter_search]

		if (group_id !== null) {
			let group_col = null;
			let expanded_col = null;

			for (let i = 0; i < this.table[join_filter_id].meta.column_names.length; i++) {
				if (this.table[join_filter_id].meta.column_names[i] === "Group_IID") {
					group_col = i;
				} else if (this.table[join_filter_id].meta.column_names[i] === "ExpandGroup_IID") {
					expanded_col = i;
				}
			}

			const group_iid = join_filter_row[group_col]
			const expanded_iid = join_filter_row[expanded_col]
			// console.log(group_iid, join_filter_row, expanded_col)

			let expanded_gid_col;

			for (let i = 0; i < this.table[expanded_id].meta.column_names.length; i++) {
				if (this.table[expanded_id].meta.column_names[i] === "GROUP_IID") {
					expanded_gid_col = i;
				}
			}
			// console.log(group_col, join_filter_row, group_iid, join_filter_search, this.table[join_filter_id].meta)

			// console.log("finding group")
			const group_search = await this.binary_search(group_id, group_iid)
			if (group_search === -1) return {
				join_filter: join_filter_search,
				group: null,
				expanded: null,
				expanded_space: null,
				final: null
			};

			let final_col = null;

			for (let i = 0; i < this.table[group_id].meta.column_names.length; i++) {
				if (this.table[group_id].meta.column_names[i] === "Output_IID") {
					final_col = i;
				}
			}

			const group_row = this.table[group_id].contents[group_search]
			const final_iid = group_row[final_col]

			// console.log(group_row, final_id, final_iid)

			// console.log("finding final")
			// console.log(this.table)

			const expanded_space = await this.get_expanded_space(group_iid, expanded_id, expanded_gid_col)

			const expanded_search = await this.binary_search(expanded_id, expanded_iid);
			if (expanded_search === -1) return {
				join_filter: join_filter_search,
				group: group_search,
				expanded: null,
				expanded_space: null,
				final: null
			};

			const final_search = await this.binary_search(final_id, final_iid)
			if (final_search === -1) return {
				join_filter: join_filter_search,
				group: group_search,
				group_iid: group_iid,
				expanded: expanded_search,
				expanded_space: expanded_space,
				final: null
			};

			const out = {
				join_filter: join_filter_search,
				group: group_search,
				group_iid: group_iid,
				expanded: expanded_search,
				expanded_space: expanded_space,
				final: final_search
			}

			return out
		} else {
			let final_col;

			for (let i = 0; i < this.table[join_filter_id].meta.column_names.length; i++) {
				if (this.table[join_filter_id].meta.column_names[i] === "IID") {
					final_col = i;
				}
			}

			const final_iid = join_filter_row[final_col]

			const final_search = await this.binary_search(final_id, final_iid)
			if (final_search === -1) return {
				join_filter: join_filter_search,
				group: null,
				group_iid: null,
				expanded: null,
				expanded_space: null,
				final: null
			};

			const out = {
				join_filter: join_filter_search,
				group: null,
				group_iid: null,
				expanded: null,
				expanded_space: null,
				final: final_search
			}

			return out
		}
	}

	async backward_trace_final(final_iid) {
		if (!final_iid) return;
		let join_filter_id = null, group_id = null, expanded_id = null, final_id = null;

		for (let i = 0; i < this.table.length; i++) {
			if (this.table[i].type == "join_filter") {
				join_filter_id = i;
			} else if (this.table[i].type == "group_by") {
				group_id = i;
			} else if (this.table[i].type == "expanded") {
				expanded_id = i;
			} else if (this.table[i].type == "final") {
				final_id = i;
			}
		}

		if (!final_id || !final_iid) return
		const final_search = await this.binary_search(final_id, final_iid)

		// console.log("ok1")

		if (!group_id) return;

		let group_col = 0;

		for (let i = 0; i < this.table[group_id].meta.column_names.length; i++) {
			if (this.table[group_id].meta.column_names[i] === "Output_IID") {
				group_col = i;
			}
		}

		// console.log("ok2")

		if (!group_col || !this.table[group_id].meta.iid_col) return;

		const group_search = await this.binary_search_flex(group_id, final_iid, group_col)
		if (!group_search && group_search !== 0) return;

		// console.log("ok3")
		const group_row = this.table[group_id].contents[group_search]
		if (!group_row) return;
		const group_iid = group_row[this.table[group_id].meta.iid_col]

		let join_filter_col = 0;
		let expanded_col = null;

		for (let i = 0; i < this.table[join_filter_id].meta.column_names.length; i++) {
			if (this.table[join_filter_id].meta.column_names[i] === "Group_IID") {
				join_filter_col = i;
			} else if (this.table[join_filter_id].meta.column_names[i] === "ExpandGroup_IID") {
				expanded_col = i;
			}
		}

		const join_filter_search = await this.binary_search_f(join_filter_id, group_iid, join_filter_col)
		const join_filter_row = this.table[join_filter_id].contents[join_filter_search]
		const input_iids = join_filter_row[this.table[join_filter_id].meta.iid_col]
		const expanded_iid = join_filter_row[expanded_col]

		let expanded_gid_col;

		for (let i = 0; i < this.table[expanded_id].meta.column_names.length; i++) {
			if (this.table[expanded_id].meta.column_names[i] === "GROUP_IID") {
				expanded_gid_col = i;
			}
		}

		const expanded_space = await this.get_expanded_space(group_iid, expanded_id, expanded_gid_col)

		const expanded_search = await this.binary_search(expanded_id, expanded_iid);

		const input_tables_key_lengths = [];
		const input_tables = []

		for (let i in this.table) {
			if (this.table[i].type === "from") {
				const row0 = (await this.fetch(this.table[i].meta, 0, 0))[0];

				input_tables_key_lengths.push(row0[this.table[i].meta.iid_col].length)
				input_tables.push(i);
			}
		}

		function parse_input(inputArray, lengthsArray) {
			const result = [];
			let currentIndex = 0;

			for (let i = 0; i < lengthsArray.length; i++) {
				const length = lengthsArray[i];

				const subArray = inputArray.slice(currentIndex, currentIndex + length);

				result.push(subArray);

				// Move our index forward by the length of the sub-array we just extracted
				currentIndex += length;
			}

			return result;
		}

		const iid_combo = parse_input(input_iids, input_tables_key_lengths)
		console.log(iid_combo)

		// console.log(input_tables_key_lengths, input_iids)

		const input_search = [];

		let iid_index = 0;
		for (let i in input_tables) {
			console.log(i, iid_combo[iid_index])
			input_search.push(await this.binary_search(i, iid_combo[iid_index]))

			iid_index++;
		}

		console.log(input_search)

		return {
			input: input_search,
			join_filter: join_filter_search,
			group: group_search,
			group_iid: group_iid,
			expanded: expanded_search,
			expanded_space: expanded_space,
			final: final_search
		}
	}

	compare(iid1, iid2) {
		if (!(iid1 && iid2)) {
			// console.warn("invalid iid");
			return;
		}
		if (iid1.length !== iid2.length) {
			// console.warn("iids differ in length");
			return;
		}

		for (let i = 0; i < iid1.length; i++) {
			if (typeof iid1[i] !== typeof iid2[i]) {
				// console.warn("iids of different types at index " + i);
				return;
			}
			if (iid1[i] === iid2[i]) continue;
			if (typeof iid1[i] === 'number' && typeof iid2[i] === 'number') {
				return iid1[i] - iid2[i];
			} else {
				return iid1[i] < iid2[i] ? -1 : (iid1[i] > iid2[i] ? 1 : 0);
			}
		}

		return 0;
	}

	async binary_search_flex(id, iid, iid_col) {
		let start = 0, end = this.table[id].rows, counter = 0;

		while (start <= end && counter < 100) {
			const m = Math.floor((start + end) / 2);

			if (!this.table[id].contents[m]) {
				await this.fetch(this.table[id].meta, m, m)
			}

			// console.log(m, this.table[id].contents[m], this.table[id].contents[m][this.table[id].meta.iid_col])

			const comp = this.compare(this.table[id].contents[m][iid_col], iid)
			// console.log(this.table[id].contents[m][this.table[id].meta.iid_col], iid, comp)
			if (comp === undefined) return -1;
			if (comp < 0) {
				start = m + 1;
			} else if (comp > 0) {
				end = m - 1;
			} else {
				return m;
			}

			counter++;
		}

		return -1;
	}

	async binary_search(id, iid) {
		let start = 0, end = this.table[id].rows, counter = 0;

		while (start <= end && counter < 100) {
			const m = Math.floor((start + end) / 2);

			if (!this.table[id].contents[m]) {
				await this.fetch(this.table[id].meta, m, m)
			}

			// console.log(m, this.table[id].contents[m], this.table[id].contents[m][this.table[id].meta.iid_col])

			if (!(this.table[id].contents[m] && this.table[id]?.meta.iid_col) && (this.table[id].type !== "expanded")) return -1

			const comp = this.compare(this.table[id].contents[m][this.table[id].meta.iid_col], iid)
			// console.log(this.table[id].contents[m][this.table[id].meta.iid_col], iid, comp)
			if (comp === undefined) return -1;
			if (comp < 0) {
				start = m + 1;
			} else if (comp > 0) {
				end = m - 1;
			} else {
				return m;
			}

			counter++;
		}

		return -1;
	}

	// async binary_search(id, iid) {
	// 	let start = 0, end = this.table[id].rows;

	// 	while (start <= end) {
	// 		const m = Math.floor((start + end) / 2);

	// 		if (!this.table[id].contents[m]) {
	// 			await this.fetch(this.table[id].meta, m, m)
	// 		}

	// 		// console.log(m, this.table[id].contents[m], this.table[id].contents[m][this.table[id].meta.iid_col])

	// 		const comp = this.compare(this.table[id].contents[m][this.table[id].meta.iid_col], iid)
	// 		// console.log(this.table[id].contents[m][this.table[id].meta.iid_col], iid, comp)
	// 		if (comp === undefined) return -1;
	// 		if (comp < 0) {
	// 			start = m + 1;
	// 		} else if (comp > 0) {
	// 			end = m - 1;
	// 		} else {
	// 			return m;
	// 		}
	// 	}

	// 	return -1;
	// }

	async binary_search_f(id, iid, col) {
		let start = 0, end = this.table[id].rows, result = -1;

		while (start <= end) {
			const m = Math.floor((start + end) / 2);

			if (!this.table[id].contents[m]) {
				await this.fetch(this.table[id].meta, m, m)
			}

			// console.log(m, this.table[id].contents[m], this.table[id].contents[m][this.table[id].meta.iid_col])

			const comp = this.compare(this.table[id].contents[m][col], iid)
			// console.log(this.table[id].contents[m][this.table[id].meta.iid_col], iid, comp)
			if (comp === undefined) return -1;
			if (comp < 0) {
				start = m + 1;
			} else if (comp > 0) {
				end = m - 1;
			} else {
				result = m;
				end = m - 1;
			}

		}

		return result;
	}

	async binary_search_l(id, iid, col) {
		let start = 0, end = this.table[id].rows, result = -1;

		while (start <= end) {
			const m = Math.floor((start + end) / 2);

			if (!this.table[id].contents[m]) {
				await this.fetch(this.table[id].meta, m, m)
			}

			// console.log(m, this.table[id].contents[m], this.table[id].contents[m][this.table[id].meta.iid_col])

			const comp = this.compare(this.table[id].contents[m][col], iid)
			// console.log(this.table[id].contents[m][this.table[id].meta.iid_col], iid, comp)
			if (comp === undefined) return -1;
			if (comp < 0) {
				start = m + 1;
			} else if (comp > 0) {
				end = m - 1;
			} else {
				result = m;
				start = m + 1;
			}

		}

		return result;
	}

	async get_expanded_space(group_iid, expanded_id, expanded_gid_col) {
		const first_match = await this.binary_search_f(expanded_id, group_iid, expanded_gid_col);
		const last_match = await this.binary_search_l(expanded_id, group_iid, expanded_gid_col);

		return [first_match, last_match]
	}

	async find(meta, match) {
		// const id = meta.id;

		// let found = false;

		// while (!found) {
		// 	let d = [];
		// 	const filters = {};
		// 	let page_index = Math.floor(this.table[id].upper / constants.chunk_size)

		// 	if (meta.type == "from") {
		// 		for (let expected_filter_key in meta.pageSQL.expected_filters) {
		// 			let filter = meta.pageSQL.expected_filters[expected_filter_key]
		// 			filter.iid_lower = meta.milestone[page_index][2]
		// 			filter.iid_upper = meta.milestone[page_index + 1][2]
		// 			filter.sarg_values = null;
		// 			filter.blm_value = null;

		// 			filters[expected_filter_key] = filter
		// 		}
		// 	} else {
		// 		for (let expected_filter_key in meta.pageSQL.expected_filters) {
		// 			let filter = meta.pageSQL.expected_filters[expected_filter_key]
		// 			filter.iid_lower = meta.milestone[page_index][2]
		// 			filter.iid_upper = meta.milestone[page_index + 1][2]
		// 			filter.sarg_values = meta.milestone[page_index].slice(3)
		// 			filter.blm_value = null;

		// 			filters[expected_filter_key] = filter
		// 		}
		// 	} 

		// 	await fetch(constants.backend + "/execute_page", {
		// 		method: "POST",
		// 		headers: {
		// 			"Content-Type": "application/json",
		// 		},
		// 		body: JSON.stringify({
		// 			db: constants.db,
		// 			sql: meta.pageSQL,
		// 			bindings: meta.pageSQL.expected_bindings,
		// 			filters: filters
		// 		}),
		// 	})
		// 	.then((res) => res.json())
		// 	.then((data) => {
		// 		d = data.contents;
		// 	});

		// 	for (let i = 0; i < d.length; i++) {
		// 		this.table[id].contents[page_index * constants.chunk_size + i] = d[i];
		// 	}

		// 	this.table[id].upper = Math.min(this.table[id].upper + constants.chunk_size, meta.rows)
		// }

		// let out = [];

		// for (let i = 0; i < upper - lower; i++) {
		// 	out[i] = this.table[id].contents[lower + i];
		// }

		// return out;
	}
}

export default Cache;
