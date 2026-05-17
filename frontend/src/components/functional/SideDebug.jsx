import { useState } from 'react'
import ContextTree from "./ContextTree";
import Highlight from "./Highlight";

function SideDebug(params) {
	const state = params.state;
	const dispatch = params.dispatch;

	const [highlight, c_highlight] = useState(false);

	function change_ctx(context) {
		console.log(context)
		state.cache.reset_context();
		dispatch(["SET_CONTEXT", context]);
	}

	const pos = state.cache.parsed.analyze.xNodeToXTree[state.cache.parsed.analyze.ctxToCtxJson[state.context].XTableValuedNode_id].pos_in_original_query
	const begin = pos.begin_str_index
	const end = pos.end_str_index_excl

	return (
		<div className="div-side-item">
			<div className="div-side-title"><span>SQL Code</span> <span>Highlight Context <input style={{ verticalAlign: "middle" }} type="checkbox" name="" id="" onChange={() => c_highlight(!highlight)} /></span></div>
			<div className="div-code">
				{highlight && <Highlight text={params.query} begin={begin} end={end}></Highlight>}
				{!highlight && params.query}
			</div>
			<div className="div-side-title">Context Navigation</div>
			<div className="div-code"><ContextTree tid={99999} data={state.cache.parsed.analyze.ctx_tree} id_map={state.cache.parsed.analyze.ctxToCtxJson} eval_map={{}} current={state.context} change={change_ctx}></ContextTree></div>
		</div>
	);
}

export default SideDebug;
