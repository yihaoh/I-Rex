import _ from "lodash";
import hash from "object-hash";

const mutations = {
	parseAnalyzeData(analyzeData, query) {
		const state = {};

		state.xtree = analyzeData.xtree;
		state.xNodeToXTree = {};
		state.ctxToCtxJson = {};
		state.query = query;
		state.ctxTree = {};
		state.xIDtoCtxID = {};

		/**
		 * Create a deep copy of a dcontext that corresponds to an xnode
		 * @param xNodeID
		 * @returns deep copy of a dcontext
		 */
		function searchCloneContext(xNodeID) {
			for (const i in analyzeData.dcontexts) {
				if (analyzeData.dcontexts[i].XTableValuedNode_id === xNodeID) {
					return _.cloneDeep(analyzeData.dcontexts[i]);
				}
			}
			return {};
		}

		/**
		 * Add more fields to the ctx here (fields not exist in dcontext)
		 * Change the ctx ID so that it is unique when multiple references to
		 * a WITH clause exist in the query
		 *
		 * @param ctx dcontext that need extra fields
		 * @param parentID The parent ctx ID for the @param ctx
		 */
		function populateCtx(ctx, parentID) {
			if (_.isEmpty(ctx)) {
				return;
			}
			ctx.tables = [];
			ctx.dContextExecd = {};
			ctx.bindings = [];
			ctx.parentCtxID = parentID;
			ctx.children = [];
			ctx.id = hash(ctx).substring(0, 8);
		}

		/**
		 * Parse data from /analyze API to corresponding variables in $store.state
		 * by traversing the xtree
		 * @param xtree
		 * @param xNodeToXTree
		 * @param ctxTree
		 * @param parentCtx
		 * @param ctxToCtxJson
		 */
		function parseHelper(xtree, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, inWhere) {
			if (xtree === null) {
				return;
			}
			xNodeToXTree[xtree.id] = xtree;
			let child = {};

			if (!inWith) {
				Object.assign(ctxTree, searchCloneContext(xtree.id));
				populateCtx(ctxTree, parentCtx === null ? null : parentCtx.id);

				if (!_.isEmpty(ctxTree)) {
					ctxToCtxJson[ctxTree.id] = ctxTree;
					xIDtoCtxID[xtree.id] = ctxTree.id;
					// console.log(ctxTree);
					// ctxTree.code.expectedBindings.forEach((b, i) => {
					// 	let bindingWithValue = _.cloneDeep(b);
					// 	bindingWithValue.value = null;
					// 	ctxTree.bindings.push(bindingWithValue);
					// });

					if (parentCtx !== null) {
						parentCtx.children.push(ctxTree);
					}
				}
			}

			if (xtree.type === "XSelectNode") {
				if (!inWith) ctxTree.childrenTypeCnt = [];
				xtree.select_exprs.forEach((n, i) => {
					parseHelper(n, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
					child = {};
				});
				if (!inWith) ctxTree.childrenTypeCnt.push(ctxTree.children.length);

				parseHelper(xtree.from_expr, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
				child = {};
				if (!inWith) ctxTree.childrenTypeCnt.push(ctxTree.children.length - _.sum(ctxTree.childrenTypeCnt));

				xtree.on_conds.forEach((n, i) => {
					parseHelper(n, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
					child = {};
				});
				if (!inWith) ctxTree.childrenTypeCnt.push(ctxTree.children.length - _.sum(ctxTree.childrenTypeCnt));

				if (inWhere) {
					ctxTree.fromWhereContext = true;
				}
				parseHelper(xtree.where_cond, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, true);
				child = {};
				if (!inWith) ctxTree.childrenTypeCnt.push(ctxTree.children.length - _.sum(ctxTree.childrenTypeCnt));

				parseHelper(xtree.group_by_exprs, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
				child = {};
				if (!inWith) ctxTree.childrenTypeCnt.push(ctxTree.children.length - _.sum(ctxTree.childrenTypeCnt));

				parseHelper(xtree.having_cond, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
				child = {};
				if (!inWith) ctxTree.childrenTypeCnt.push(ctxTree.children.length - _.sum(ctxTree.childrenTypeCnt));
			} else if (xtree.type === "XSetOpNode") {
				parseHelper(xtree.left, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
				child = {};

				parseHelper(xtree.right, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
				child = {};
			} else if (xtree.type === "XTableRefNode" && !xtree.in_database) {
				// no need to recurse but need to build a new context
				if (!inWith) {
					let xnode = xNodeToXTree[xtree.XWithItemNode_id].query;
					ctxTree = searchCloneContext(xnode.id);
					populateCtx(ctxTree, parentCtx.id);
					parentCtx.children.push(ctxTree);
					parseHelper(xnode, xNodeToXTree, child, ctxTree, ctxToCtxJson, xIDtoCtxID, inWith, false);
					child = {};
				}
			} else if (xtree.type === "XBasicCallNode") {
				xtree.operands.forEach((n, i) => {
					parseHelper(n, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, inWhere);
					ctxTree = {};
				});
			} else if (xtree.type === "XJoinNode") {
				parseHelper(xtree.left, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, false);
				ctxTree = {};
				parseHelper(xtree.right, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, false);
			} else if (xtree.type === "XWithNode") {
				// no need to build ctx for with node alone
				inWith = true;
				xtree.with_items.forEach((w, i) => {
					parseHelper(w, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, false);
				});
				inWith = false;
				parseHelper(xtree.body, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, false);
			} else if (xtree.type === "XWithItemNode") {
				parseHelper(xtree.query, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, false);
			} else if (xtree.type === "XTableRenameNode") {
				parseHelper(xtree.operand, xNodeToXTree, ctxTree, parentCtx, ctxToCtxJson, xIDtoCtxID, inWith, false);
			}
		}

		parseHelper(analyzeData.xtree, state.xNodeToXTree, state.ctxTree, null, state.ctxToCtxJson, state.xIDtoCtxID, analyzeData.xtree.type === "XWithNode", false);

		state.root_dcontext_id = state.ctxTree.id;

		// console.log("parsed analyze data", state);

		return state;
	},
	sanitizeAnalyzeData(analyzeData, query) { },
	convertToTree(node, xNodeToXTree) {
		return convertToTreeFormat(node, xNodeToXTree);
		function convertToTreeFormat(node, allNodesMap) {
			if (!node) {
				return {};
			}

			// Get the expression for the current node from the node itself.
			// If sql_string isn't available, fall back to 'Unknown Expression'.
			const nodeExpression = node.id ?? "Unknown Expression";

			// Initialize the object to hold the children of the current node
			const childrenDict = {};

			// If the node has children (which are just IDs in the 'children' array),
			// we need to look them up in the allNodesMap.
			if (node.children && node.children.length > 0) {
				node.children.forEach(childId => {
					const childNode = allNodesMap[childId];
					if (childNode) { // Ensure the child node exists in the map
						// Recursively call the function for each child node.
						// The result will be a dictionary like {'child_expression': {grand_children_dict}}
						const transformedChild = convertToTreeFormat(childNode, allNodesMap);
						// Merge the transformed child into the current node's childrenDict
						Object.assign(childrenDict, transformedChild);
					}
				});
			} else if (node.operands && node.operands.length > 0) {
				// Fallback for nodes that use 'operands' instead of 'children' for direct nesting (like XBasicCallNode)
				// This handles cases where operands are full objects, not just IDs.
				node.operands.forEach(operand => {
					const transformedOperand = convertToTreeFormat(operand, allNodesMap);
					Object.assign(childrenDict, transformedOperand);
				});
			}


			// Return an object where the key is the current node's expression,
			// and the value is the object containing its processed children.
			return { [nodeExpression]: childrenDict };
		}
	},
	convertToTreeSelect(node, xNodeToXTree) {
		return convertToTreeFormat(node, xNodeToXTree);
		function convertToTreeFormat(node, allNodesMap) {
			if (!node) {
				return {};
			}

			// Get the expression for the current node from the node itself.
			// If sql_string isn't available, fall back to 'Unknown Expression'.
			const nodeExpression = node.id ?? "Unknown Expression";

			if (node.is_aggregate) {
				return { [nodeExpression]: {} };
			}

			// Initialize the object to hold the children of the current node
			const childrenDict = {};

			// If the node has children (which are just IDs in the 'children' array),
			// we need to look them up in the allNodesMap.
			if (node.children && node.children.length > 0) {
				node.children.forEach(childId => {
					const childNode = allNodesMap[childId];
					if (childNode) { // Ensure the child node exists in the map
						// Recursively call the function for each child node.
						// The result will be a dictionary like {'child_expression': {grand_children_dict}}
						const transformedChild = convertToTreeFormat(childNode, allNodesMap);
						// Merge the transformed child into the current node's childrenDict
						Object.assign(childrenDict, transformedChild);
					}
				});
			} else if (node.operands && node.operands.length > 0) {
				// Fallback for nodes that use 'operands' instead of 'children' for direct nesting (like XBasicCallNode)
				// This handles cases where operands are full objects, not just IDs.
				node.operands.forEach(operand => {
					const transformedOperand = convertToTreeFormat(operand, allNodesMap);
					Object.assign(childrenDict, transformedOperand);
				});
			}


			// Return an object where the key is the current node's expression,
			// and the value is the object containing its processed children.
			return { [nodeExpression]: childrenDict };
		}
	},
	convertToTreeExpanded(node, xNodeToXTree) {
		return convertToTreeFormat(node, xNodeToXTree);
		function convertToTreeFormat(node, allNodesMap) {
			if (!node) {
				return {};
			}

			// Get the expression for the current node from the node itself.
			// If sql_string isn't available, fall back to 'Unknown Expression'.
			const nodeExpression = node.id ?? "Unknown Expression";

			// Initialize the object to hold the children of the current node
			const childrenDict = {};

			// If the node has children (which are just IDs in the 'children' array),
			// we need to look them up in the allNodesMap.
			if (node.children && node.children.length > 0) {
				node.children.forEach(childId => {
					const childNode = allNodesMap[childId];
					if (childNode) { // Ensure the child node exists in the map
						// Recursively call the function for each child node.
						// The result will be a dictionary like {'child_expression': {grand_children_dict}}
						const transformedChild = convertToTreeFormat(childNode, allNodesMap);
						// Merge the transformed child into the current node's childrenDict
						Object.assign(childrenDict, transformedChild);
					}
				});
			} else if (node.operands && node.operands.length > 0) {
				// Fallback for nodes that use 'operands' instead of 'children' for direct nesting (like XBasicCallNode)
				// This handles cases where operands are full objects, not just IDs.
				node.operands.forEach(operand => {
					const transformedOperand = convertToTreeFormat(operand, allNodesMap);
					Object.assign(childrenDict, transformedOperand);
				});
			}


			// Return an object where the key is the current node's expression,
			// and the value is the object containing its processed children.

			if (node.is_aggregate) {
				return childrenDict;
			}

			return { [nodeExpression]: childrenDict };
		}
	},
	convertToContextTree(ctx, ctxMap) {
		return convertToContextTreeFormat(ctx, ctxMap);

		function convertToContextTreeFormat(ctx, ctxMap) {
			if (!ctx) {
				return {};
			}

			const ctxExpression = ctx.id;

			const childrenDict = {};
			let join_dict = {};

			if (ctx.join_context_tree) {
				ctxMap[ctx.join_context_tree.id] = ctx.join_context_tree
				join_dict = convertToJoinTreeFormat(ctx.join_context_tree, ctxMap);
				childrenDict[ctx.join_context_tree.id] = join_dict[ctx.join_context_tree.id];
			}

			if (ctx.children && ctx.children.length > 0) {
				ctx.children.forEach(child => {
					const childNode = ctxMap[child.id];

					// console.log(childNode)
					if (childNode) {
						const transformedChild = convertToContextTreeFormat(childNode, ctxMap);
						Object.assign(childrenDict, transformedChild);
					}
				});
			}

			return {
				[ctxExpression]: childrenDict,
			}
		}

		function convertToJoinTreeFormat(ctx, ctxMap) {
			if (!ctx) {
				return {};
			}

			const ctxExpression = ctx.id;

			const childrenDict = {};

			if (ctx.children && ctx.children.length > 0) {
				ctx.children.forEach(childNode => {
					if (childNode) {
						ctxMap[childNode.id] = childNode
						const transformedChild = convertToJoinTreeFormat(childNode, ctxMap);
						Object.assign(childrenDict, transformedChild);
					}
				});
			}

			return {
				[ctxExpression]: childrenDict,
			}
		}
	}
};

export default mutations;
