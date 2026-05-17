import { format } from "@sqltools/formatter";

import Cache from "./cache";

const init = {
	view: "query",
	query: "",
	cache: new Cache(),
	status: {
		analyzing: false,
		preparing: [],
		fetching: [],
	},
	actions: [0, 0],
	current: 0,
	context: null,
};

function reducer(state = init, [type, payload]) {
	switch (type) {
		case "VIEW":
			return { ...state, view: payload };
		case "QUERY":
			return { ...state, query: payload };
		case "ACTION":
			if (payload === 0) {
				let query = format(state.query);

				if (query[query.length - 1] === ";") {
					query = query.slice(0, -1);
				};

				return { ...state, query: query };
			} else if (payload === 1) {
				const n_status = { ...state.status, analyzing: true };
				return { ...state, status: n_status };
			}
			break;
		case "LOAD":
			if (payload === "analyze") {
				const n_status = { ...state.status, analyzing: true };
				return { ...state, status: n_status };
			} else if (payload === "prepare") {
				// const n_status = { ...state.status, preparing: true };
				// return { ...state, status: n_status };
			} else if (payload === "fetch") {
			}
			break;
		case "ANALYZE_FINISH":
			const n_status = { ...state.status, analyzing: false };
			state.cache.set_raw("analyze", payload.raw);
			state.cache.set_parsed("analyze", payload.parsed);
			console.log(payload.parsed);
			// console.log(JSON.stringify(payload.parsed.xNodeToXTree))
			// console.log(payload.raw, payload.parsed);
			return { ...state, view: "debug", status: n_status, context: payload.context };
		case "SET_CURRENT":
			if (payload < 0) return state;
			return { ...state, current: payload }
		case "SET_CONTEXT":
			return { ...state, context: payload }
		default:
			return state;
	}
}

const State = {
	init: init,
	reducer: reducer,
};

export default State;
