import constants from "./constants";

const Actions = [
	async function fetch(db, fetch_query, marker_lower, marker_upper, offset, limit) {
		const res = await fetch(constants.backend + "/fetch/", {
			method: "POST",
			body: JSON.stringify({
				db: db,
				fetch_query: fetch_query,
				marker_lower: 5,
				marker_upper: 10,
				offset: offset,
				limit: limit,
			}),
		});
		const json = await res.json();
		return json;
	},
	async function prepare(state) {
		const res = await fetch(constants.backend + "/prepare", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
			},
			body: JSON.stringify({
				db: constants.db,
				query: state.query,
				chunk_size: constants.chunk_size,
				group_dups: true,
			}),
		});
		const json = await res.json();
		return json;
	},
	async function analyze(db, query) {
		const res = await fetch(constants.backend + "/analyze", {
			method: "POST",
			body: JSON.stringify({
				db: db,
				query: query,
				page_size: 50
			}),
		});
		const json = await res.json();
		return json;
	},
	async function format() {},
];

export default Actions;
