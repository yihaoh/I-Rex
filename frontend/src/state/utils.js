/**
 * This file should contains functions that is purely for the purpose of
 * computation. If you base your computation on data in the variables of
 * $store.state, please add your functions to getters.js. This file should
 * contain only general-purpose functions.
 */


/**
 * Given an entire query, split into three sections:
 * before, inner, after
 * "inner" is the section of the query specified by begin and end index
 * @param query
 * @param begin
 * @param end
 * @returns {{before: string, after: string, inner: string}}
 */
export function sliceQueryByPos(query, begin, end) {
    let before = query.substring(0, begin);
    let inner = query.substring(begin, end);
    let after = query.substring(end, query.length)


    return { before, inner, after };
}

/**
 * Convert a backend formatted table to ANTD table format
 Backend Table format:
 table: [{
            name: [@state xtree[xtreeID].input_table_aliases]
            columns: [@state CtxToCode.tables.column_names ] 
            content: [@state dContextExecd.tables.contents ]
            activeTupleIndex: index of active input tuple
            pinnedTupleIndex: index of pinned tuple
            pinnedRange: [ ] list of index of pinnable tuples
            dcontextId: context id if it is a derived table, otherwise null
        }]
 ANTD format:
 const columns = [
 {
      title: 'Name',
      dataIndex: 'name',
    }, ...]
 const data = [
 {
      key: '1',
      name: 'John Brown',
      money: '￥300,000.00',
      address: 'New York No. 1 Lake Park',
    },
 {
      key: '2',
      name: 'Jim Green',
      money: '￥1,256,000.00',
      address: 'London No. 1 Lake Park',
    }, ...]
 */
export function convertTable(col, data, name) {
    const columns = [];
    const rows = [];

    // convert columns
    var i = 0;
    var colAliasMap = {};
    for (const columnName of col) {
        columns.push({
            title: columnName,
            dataIndex: i + "@" + columnName,
        });
        colAliasMap[columnName] = i + "@" + columnName;
        i++;
    }

    // convert rows
    for (let r = 0; r < data.length; r++) {
        let curRow = { key: r };
        for (let i = 0; i < columns.length; i++) {
            curRow[columns[i].dataIndex] = data[r][i] == null ? "null" : data[r][i];
        }
        rows.push(curRow);
    }

    return { name: name, columns, data: rows };
}


export function convertSetOpTable(table, dup = true) {
    const columns = [];
    const rows = [];

    // convert columns
    let i = 0;
    for (const columnName of table.columns) {
        columns.push({
            title: columnName,
            dataIndex: i + "@" + columnName,
        });
        i++;
    }

    // convert rows
    let rowContent = [];
    let key = 0;
    for (let r = 0; r < table.content.length; r++) {
        let curRow = { key: key };
        for (let i = 0; i < columns.length; i++) {
            curRow[columns[i].dataIndex] = table.content[r][i] == null ? "null" : table.content[r][i];
        }
        if (dup) {
            rows.push(curRow);
            key++;
        }
        else {
            if (!rowContent.includes(JSON.stringify(table.content[r]))) {
                rows.push(curRow);
                rowContent.push(JSON.stringify(table.content[r]));
                key++;
            }
        }
    }

    return { name: table.name, columns, data: rows };
}



/**
* Given a WHERE condition tree whose leaf nodes T/F values have been evaluated,
* evaluate the T/F of intermediate tree nodes.
* @param tree
* @returns {null|(function(*=, *): *)}
*/
export function fillCondTreeBool(tree) {
    let op = null;
    let binaryOp = true;
    switch (tree.value) {
        case 'AND':
            op = (x, y) => x && y;
            break;
        case 'OR':
            op = (x, y) => x || y;
            break;
        case 'NOT':
            op = (x) => !x;
            binaryOp = false;
            break;
        default:
            return tree.boolVal;
    }

    let res = null;
    if (binaryOp) {
        const c0 = fillCondTreeBool(tree.children[0]);
        const c1 = fillCondTreeBool(tree.children[1]);
        res = op(c0, c1);
    } else {
        const c0 = fillCondTreeBool(tree.children[0]);
        res = op(c0);
    }

    tree.boolVal = res;
    tree.scopedSlots.tagType = `bool-${res}`;
    return res;
}

export function getConditionTree(data, tables, parent_key, dep, ord, flags) {
    const tree = {};
    tree.type = data.id;
    tree.value = data.value;
    tree.boolVal = null;
    let tagType = 'regular';

    if (data.id.includes('BLOCK')) {
        tagType = data.value;
    } else if (data.id.startsWith('COLUMN')) {
        tagType = 'column';
    } else if (data.id.includes('AGG')) {
        tagType = 'agg';
    } else if (data.id in flags) {
        const boolVal = flags[data.id];
        tagType = `bool-${boolVal}`;
        tree.boolVal = boolVal;
    }


    tree.title = ' ';
    tree.scopedSlots = {
        icon: 'custom',
        tagType: tagType,
        cond: {
            type: data.id,
            value: data.value,
            id: data.id,
            pos: data.pos,
            naturalText: data.naturalText,
        }
    };
    const key = parent_key + '-' + ord;
    const children_list = [];
    if (!data.id.includes('AGG') && data.children.length > 0) {
        let i = 0;
        for (const [k, value] of Object.entries(data['children'])) {
            children_list.push(getConditionTree(value, tables, key, dep + 1, i, flags));
            i++;
        }
        tree.children = children_list;
    }
    return tree;
}

/**
 * traverse the context tree to find the node with given ctxId
 * @param ctx
 * @param ctxId
 * @returns {null|*}
 */
export function ctxTreeSearch(ctx, ctxId) {
    if (ctx.id === ctxId) return ctx;
    for (const [k, value] of Object.entries(ctx.children)) {
        let result = ctxTreeSearch(value, ctxId);
        if (result) return result;
    }
    return null;
}

/**
 * traverse the context tree to find the node with given key
 * @param ctx
 * @param key
 * @returns {null|*}
 */
export function ctxTreeSearchByKey(ctx, key) {
    if (ctx.key === key) return ctx;
    for (const [k, value] of Object.entries(ctx.children)) {
        let result = ctxTreeSearchByKey(value, key);
        if (result) return result;
    }
    return null;
}

/**
 * Fetches the bindings values based on what tuple values are selected in outer context
 * @param newCtx the ctx json object for the new context that the user is navigating to
 * @returns [array of binding values]
 */
export function fetchCurBindings(newCtx, state) {
    let curBindings = []
    for (let i = 0; i < newCtx.bindings.length; i++) {
        const ctxId = state['xIDtoCtxID'][newCtx.bindings[i]['xSelectNode_id']];
        const bindingCtx = state['ctxToCtxJson'][ctxId];
        const tableIndex = newCtx.bindings[i]['xSelectNode_input_index'];
        const columnIndex = newCtx.bindings[i]['xSelectNode_input_column_index'];

        const bindingTable = bindingCtx.tables[tableIndex];
        const rowIndex = bindingTable.activeTupleIndex;

        const bindingValue = bindingTable.content[rowIndex][columnIndex];

        curBindings.push(bindingValue);
    }

    return curBindings;
}

/**
 * Parses the aggregation/groupby table so that it can be rendered by a-tree component 
 * @param data the table content
 * @param name the name of the table
 * @returns {String: name, Array: column names, Array: row contents, Array: column info}
 */
export function convertNestedTable(data, name, state) {
    //convert aggregation columns
    const xTree = state['xNodeToXTree'][state.curCtx["XTableValuedNode_id"]];
    const groupXTree = xTree['group_by_exprs'];
    const selectXTree = xTree['select_exprs'];

    let groupColumn = [];
    for (let i = 0; i < groupXTree.length; i++) {
        groupColumn.push(groupXTree[i]['sql_string']);
    }

    let selectColumn = [];
    for (let i = 0; i < selectXTree.length; i++) {
        let columnType = selectXTree[i]['type'];
        if (columnType !== 'XColumnRefNode') {
            selectColumn.push(selectXTree[i]['sql_string']);
        }
    }

    let havingColumn;
    if (xTree['having_cond'] != null) {
        havingColumn = [xTree['having_cond']['sql_string']];
        // let opId = xTree['having_cond']['operands'][0].id
        // let singleHaving = state.curCtx['dContextExecd']['xToValue'][opId];
        // if (singleHaving == null) {
        //     havingColumn.push(xTree['having_cond']['sql_string']);
        // } else {
        //     for (let i = 0; i < xTree['having_cond']['operands'].length; i++) {
        //         havingColumn.push(xTree['having_cond']['operands'][i]['sql_string']);
        //     }
        // }
    }

    let groupByColumns = [groupColumn];
    if (xTree['having_cond'] != null) {
        groupByColumns.push(havingColumn);
    }

    let aggColumns = [];

    aggColumns.push({
        title: "Group",
        children: [],
    });

    if (xTree['having_cond'] != null) {
        aggColumns.push({
            title: "Having",
            children: [],
        });
    }


    var key = 0;
    let colRef = [];
    for (let i = 0; i < aggColumns.length; i++) {
        for (let j = 0; j < groupByColumns[i].length; j++) {
            const columnName = groupByColumns[i][j];
            aggColumns[i].children.push({
                title: columnName,
                dataIndex: key + "@" + columnName,
            });
            colRef.push(aggColumns[i].children[j].dataIndex);
            key++;
        }
    }

    let havingID;
    let havingData;

    if (xTree['having_cond'] != null) {
        havingID = xTree['having_cond'].id;
        havingData = state.curCtx['dContextExecd']['xToValue'][havingID];
    }


    const selectData = [];
    for (let i = 0; i < selectXTree.length; i++) {
        const selectID = selectXTree[i].id;
        selectData.push(state.curCtx['dContextExecd']['xToValue'][selectID]);
    }

    const rows = [];
    for (let r = 0; r < data.length; r++) {
        let curRow = { key: r };

        for (let i = 0; i < groupXTree.length; i++) {
            curRow[colRef[i]] = data[r][i];
        }

        if (xTree['having_cond'] != null) {
            let opId = xTree['having_cond'].id;
            let havingData = state.curCtx['dContextExecd']['xToValue'][opId];
            if (havingData[r]) {
                curRow[colRef[groupXTree.length]] = "True";
            } else {
                curRow[colRef[groupXTree.length]] = "False";
            }
        }

        rows.push(curRow);
    }

    return { name: name, columns: aggColumns, data: rows, dataIndex: colRef };
}



/**
 * Given the current operand and context, the function
 * will return the appropriate tree node for the condition tree
 * @param condNum
 * @param opNum
 * @param curOp
 * @param curCtx
 * @returns {{title: operand name, key: unique identifier for tree node, children: []}}
 */
export function getCondTreeData(condNum, opNum, curOp, curCtx) {
    let condValue;
    if (curOp.type === "XLiteralNode") {
        condValue = curOp.sql_string;
    } else if (curOp.type === "XSelectNode") {
        condValue = "";
    } else {
        let inputTableSize = curCtx.dContextExecd.tFromWhere;
        let activeTuples = [];

        for (let i = 0; i < inputTableSize; i++) {
            activeTuples.push(curCtx.tables[i].activeTupleIndex);
        }

        let crossResult = curCtx.dContextExecd.xToValueCross[curOp.id];

        let crossValue;
        for (let i = 0; i < crossResult.length; i++) {
            let curCross = crossResult[i];
            let foundTuple = true;
            for (let j = 0; j < activeTuples.length; j++) {
                if (activeTuples[j] !== curCross[j]) {
                    foundTuple = false;
                    break;
                }
            }
            if (foundTuple) {
                crossValue = curCross[curCross.length - 1];
                break;
            }
        }
        if (crossValue === null) {
            condValue = "NULL";
        } else if (crossValue === true || crossValue === false) {
            condValue = crossValue === true ? "True" : "False";
        } else {
            condValue = crossValue;
        }
    }

    return { title: condValue, key: condNum * 10 + opNum, children: [] };
}

export function getCondTreeDataGroup(condNum, opNum, curOp, curCtx) {
    let condValue;
    if (curOp.type === "XLiteralNode") {
        condValue = curOp.sql_string;
    } else {
        let tableIndex = curCtx.dContextExecd.tFromWhere;
        let activeTuple = curCtx.tables[tableIndex].activeTupleIndex;
        let crossResult = curCtx.dContextExecd.xToValue[curOp.id];

        if (!activeTuple) {
            condValue = curOp.sql_string + ": ";
        } else {
            condValue = curOp.sql_string + ": " + crossResult[activeTuple];
        }
    }

    return { title: condValue, key: condNum * 10 + opNum, children: [] };
}

export function getCondTreeDataHaving(condNum, opNum, curOp, curCtx) {
    let condValue;
    if (curOp.type === "XLiteralNode") {
        condValue = curOp.sql_string;
    } else {
        if (curOp.type === "XBasicCallNode") {
            let crossResult = curCtx.dContextExecd.xToValue[curOp.id];
            let tableIndex = curCtx.dContextExecd.tGroupBy;
            let activeTupleIndex = curCtx.tables[tableIndex].activeTupleIndex;

            let innerCrossValue;
            if (activeTupleIndex == null) {
                innerCrossValue = null;
            } else {
                innerCrossValue = crossResult[activeTupleIndex];
            }

            if (innerCrossValue === null) {
                condValue = "NULL";
            } else if (innerCrossValue === true || innerCrossValue === false) {
                condValue = innerCrossValue === true ? "True" : "False";
            } else {
                condValue = innerCrossValue;
            }
        } else {
            let tableIndex = curOp.XSelectNode_input_index;
            let columnIndex = curOp.XSelectNode_input_column_index;
            let activeIndex = curCtx.tables[tableIndex].activeTupleIndex;
            let groupByActiveIndex = curCtx.tables[curCtx.dContextExecd.tGroupBy].activeTupleIndex;

            let crossValue;
            if (groupByActiveIndex == null) {
                crossValue = null;
            } else {
                crossValue = curCtx.tables[tableIndex].content[activeIndex][columnIndex];
            }

            if (crossValue === null) {
                condValue = "NULL";
            } else if (crossValue === true || crossValue === false) {
                condValue = crossValue === true ? "True" : "False";
            } else {
                condValue = crossValue;
            }
        }
    }

    return { title: condValue, key: condNum * 10 + opNum, children: [] };
}

/**
 * Given an operand and the current context
 * it will return whether the current operand is
 * true or false depending on the pinned tuples
 * @param curOp
 * @param curCtx
 * @returns {actual value if condition is satisfied or false if it isn't}
 */
export function getCondBoolean(curOp, curCtx) {
    let boolValue;
    let inputTableSize = curCtx.dContextExecd.tFromWhere;
    let activeTuples = [];

    for (let i = 0; i < inputTableSize; i++) {
        if (curCtx.tables[i].activeTupleIndex === -1) {
            return null;
        }
        activeTuples.push(curCtx.tables[i].activeTupleIndex);
    }

    let crossResult = curCtx.dContextExecd.xToValueCross[curOp.id]
    let crossValue;
    for (let i = 0; i < crossResult.length; i++) {
        let curCross = crossResult[i];
        let foundTuple = true;
        for (let j = 0; j < activeTuples.length; j++) {
            if (activeTuples[j] !== curCross[j]) {
                foundTuple = false;
                break;
            }
        }

        if (foundTuple) {
            crossValue = curCross[curCross.length - 1];
            break;
        }
    }

    if (crossValue == null) {
        boolValue = null;
    } else if (crossValue) {
        boolValue = crossValue;
    } else {
        boolValue = false;
    }

    return boolValue;
}

export function getCondBooleanHaving(curOp, curCtx) {
    let boolValue;
    let tableIndex = curCtx.dContextExecd.tGroupBy;
    let activeTuples = [];

    if (curCtx.tables[tableIndex].activeTupleIndex === -1) {
        return null;
    } else {
        activeTuples.push(curCtx.tables[tableIndex].activeTupleIndex);
    }

    let crossResult = curCtx.dContextExecd.xToValue[curOp.id]
    let crossValue = crossResult[activeTuples[0]];

    if (crossValue == null) {
        boolValue = null;
    } else if (crossValue) {
        boolValue = crossValue;
    } else {
        boolValue = false;
    }

    return boolValue;
}

/**
 * Returns index of ALL occurrences of element in an array
 * @param array the array to be searched
 * @param ele the element that needs to be found
 * @returns Array: indexes of elements in array that match ele
 */

export function findAll(array, ele) {
    const res = [];
    let idx = array.indexOf(ele);
    while (idx !== -1) {
        res.push(idx);
        idx = array.indexOf(ele, idx + 1);
    }
    return res;
}

/**
 * Given an operand, check if there are still aggregation
 * operands in its children.
 * @param curCondTree
 * @returns {true if such aggregation operands exist, otherwise false}
 */

export function checkAggregateChildren(curCondTree) {
    if (!('operands' in curCondTree) || !('is_aggregate' in curCondTree)) {
        return false;
    }
    if (curCondTree.is_aggregate) {
        return true;
    }
    let res = false;
    for (const operand of curCondTree.operands) {
        res = res || checkAggregateChildren(operand);
        if (res) {
            return true;
        }
    }
    return res;
}
