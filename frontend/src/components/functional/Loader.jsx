import { useEffect } from "react";

import constants from "../../state/constants";
import mutations from "../../state/mutations";

import LoadingIndicator from "./LoadingIndicator";

function Loader(props) {
	const state = props.state;
	const dispatch = props.dispatch;

	useEffect(() => {
		effect();

		async function effect() {
			if (state.status.analyzing) {
				const res = await analyze();
				// console.log(res);
				const parsed = mutations.parseAnalyzeData(res, state.query);
				const ctxTree = mutations.convertToContextTree(parsed.ctxTree, parsed.ctxToCtxJson)

				parsed["ctx_tree"] = ctxTree
				parsed['ctree'] = res.ctree;

				const rootCtx = parsed.ctxTree

				const select_trees = [];
				const group_trees = [];
				const expanded_trees = [];
				const on_trees = [];
				for (let i in parsed.xtree.select_exprs) {
					if (has_nesting(parsed.xtree.select_exprs[i])) select_trees.push(mutations.convertToTreeSelect(parsed.xtree.select_exprs[i], parsed.xNodeToXTree))
				}

				for (let i in parsed.xtree.on_conds) {
					on_trees.push(mutations.convertToTree(parsed.xtree.on_conds[i], parsed.xNodeToXTree))
				}

				if (parsed.ctree.code.tGroup && parsed.ctree.code.tGroup !== -1) {
					let col_names = parsed.ctree.code.tables[parsed.ctree.code.tGroup].column_names;
					for (let i in col_names) {
						// if (parsed.xNodeToXTree[col_names[i]])
						// 	console.log(parsed.xNodeToXTree[col_names[i]].is_aggregate)
						if (parsed.xNodeToXTree[col_names[i]] && has_nesting(parsed.xNodeToXTree[col_names[i]])) {
							if (parsed.xNodeToXTree[col_names[i]].is_aggregate) {
								group_trees.push(mutations.convertToTree(parsed.xNodeToXTree[col_names[i]], parsed.xNodeToXTree))
								expanded_trees.push(mutations.convertToTreeExpanded(parsed.xNodeToXTree[col_names[i]], parsed.xNodeToXTree))
							}
						}
					}
				}

				rootCtx['select_trees'] = select_trees
				rootCtx['group_trees'] = group_trees
				rootCtx['expanded_trees'] = expanded_trees
				rootCtx['on_trees'] = on_trees
				rootCtx['where_tree'] = mutations.convertToTree(parsed.xtree.where_cond, parsed.xNodeToXTree)
				rootCtx['having_tree'] = mutations.convertToTree(parsed.xtree.having_cond, parsed.xNodeToXTree)
				rootCtx['select_ids'] = parsed.xtree.select_exprs.map(item => item.id)
				dispatch(["ANALYZE_FINISH", { raw: res, parsed: parsed, context: parsed.root_dcontext_id }]);
			}
		}

		function has_nesting(node) {
			if (node && node.children && node.children.length) return true;
			else return false;
		}

		async function analyze() {
			return await request("analyze", {
				db: constants.db,
				query: state.query,
				page_size: constants.chunk_size
			});
		}
	}, []);

	return (
		<div className="div-loader">
			<LoadingIndicator></LoadingIndicator>
		</div>
	);

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

export default Loader;
