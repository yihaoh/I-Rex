import { useState, useMemo, useCallback } from 'react';
import { Divider, Tree, Tag, Tooltip } from 'antd';
import { CaretDownOutlined } from '@ant-design/icons'; // Import specific Ant Design icons
import {
    getCondTreeData,
    getCondBoolean,
    fetchCurBindings,
    getCondBooleanHaving,
    getCondTreeDataHaving,
    getCondTreeDataGroup,
    checkAggregateChildren
} from '../../../state/utils'; // Adjust path as necessary

// Placeholder for your Redux/Context store.
// You'll need to replace these with your actual store integration.
const useStore = () => {
    // This is a simplified mock. In a real app, you'd use useSelector from react-redux or useContext
    const [state, setState] = useState({
        curCtx: {
            tables: {
                // Mock data for curCtx.tables. Assuming 'someTableId' exists
                someTableId: { activeTupleIndex: 0 },
                // Add other necessary table data if curCtx.dContextExecd.tFromWhere points to them
            },
            dContextExecd: {
                xToValue: {}, // Mock xToValue
                tFromWhere: 'someTableId', // Mock tFromWhere
            },
            bindings: [], // Mock bindings
            XTableValuedNode_id: 'mockXTableValuedNodeId',
        },
        xNodeToXTree: {
            mockXTableValuedNodeId: {
                group_by_exprs: [],
            },
        },
        xIDtoCtxID: {},
        ctxToCtxJson: {},
        schema_option: 'default_db',
    });

    const dispatch = async (action) => {
        // Mock dispatch functionality
        if (action.type === 'executeQuery') {
            console.log('Executing query for:', action.payload.queryContext);
            // Simulate API call or data processing
            await new Promise(resolve => setTimeout(resolve, 100)); // Simulate async
            // Update state as if query results came back
            setState(prevState => ({
                ...prevState,
                curCtx: {
                    ...prevState.curCtx,
                    tables: {
                        ...prevState.curCtx.tables,
                        // Update tables based on query result if needed
                    },
                    dContextExecd: {
                        ...prevState.curCtx.dContextExecd,
                        // Update xToValue based on query result if needed
                    },
                    bindings: action.payload.queryContext.bindings,
                }
            }));
        }
    };

    const commit = (type, payload) => {
        // Mock commit functionality (direct state update)
        if (type === 'swapCurCtx') {
            setState(prevState => ({
                ...prevState,
                curCtx: payload,
            }));
        }
    };

    return { state, dispatch, commit };
};


function ConditionTree({ treeData, dataTable, name }) {
    const { state, dispatch, commit } = useStore(); // Replace with your actual store hook
    const { curCtx } = state;

    const type = useMemo(() => name, [name]);

    const newTreeData = useMemo(() => {
        let ret = [];
        let keyIndex = 0;

        if (type === "JOIN TREE") {
            const recurseCondTree = (arr, curCondTree) => {
                const toAdd = {};
                if (curCondTree.type === 'XSetOpNode' || curCondTree.type === "XSelectNode" || (curCondTree.type === "XBasicCallNode" && curCondTree["operator_name"] === "$SCALAR_QUERY")) {
                    toAdd.title = (
                        <Tag color="white">
                            <Tag color="orange">DRILL DOWN</Tag>
                        </Tag>
                    );
                    if (curCondTree.type === "XBasicCallNode" && curCondTree["operator_name"] === "$SCALAR_QUERY") {
                        toAdd.key = curCondTree.operands[0].id;
                    } else {
                        toAdd.key = curCondTree.id;
                    }
                    toAdd.children = [];
                    arr.push(toAdd);
                    return;
                }
                if (curCondTree.type !== "XBasicCallNode") return;

                let opName = curCondTree.operator_name;
                let condBool = getCondBoolean(curCondTree, curCtx);
                let singleOp = ['AND', 'OR', 'NOT', 'IN', 'NOT IN', 'EXISTS'];

                if (singleOp.includes(opName)) {
                    if (condBool === null) {
                        toAdd.title = (
                            <Tag color="white">
                                <Tag>{opName}</Tag>
                                <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                <Tag>
                                    <b>{"NULL"}</b>
                                </Tag>
                            </Tag>
                        );
                    } else if (condBool === false) {
                        toAdd.title = (
                            <Tag color="white">
                                <Tag color="red">{opName}</Tag>
                                <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                <Tag color="red">
                                    <b>{"False"}</b>
                                </Tag>
                            </Tag>
                        );
                    } else {
                        toAdd.title = (
                            <Tag color="white">
                                <Tag color="green">{opName}</Tag>
                                <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                <Tag color="green">
                                    <b>{"True"}</b>
                                </Tag>
                            </Tag>
                        );
                    }
                } else {
                    let sql_strings = [];
                    for (const obj of curCondTree.sql_string.split(opName)) {
                        sql_strings.push(obj);
                    }
                    let title = sql_strings[0] + opName + sql_strings[1];
                    if (condBool === null) {
                        toAdd.title = (
                            <Tag color="white">
                                <Tooltip title={<Tag>{title}</Tag>}>
                                    <Tag>{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag>
                                        <b>{"NULL"}</b>
                                    </Tag>
                                </Tooltip>
                            </Tag>
                        );
                    } else if (condBool === false) {
                        toAdd.title = (
                            <Tag color="white">
                                <Tooltip title={<Tag color="red">{title}</Tag>}>
                                    <Tag color="red">{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag color="red">
                                        <b>{"False"}</b>
                                    </Tag>
                                </Tooltip>
                            </Tag>
                        );
                    } else {
                        toAdd.title = condBool === true ? (
                            <Tag color="white">
                                <Tooltip title={<Tag color="green">{title}</Tag>}>
                                    <Tag color="green">{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag color="green">
                                        <b>{"True"}</b>
                                    </Tag>
                                </Tooltip>
                            </Tag>
                        ) : (
                            <Tag color="white">
                                <Tooltip title={<Tag color="blue">{title}</Tag>}>
                                    <Tag color="blue">{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag color="blue">
                                        <b>{condBool}</b>
                                    </Tag>
                                </Tooltip>
                            </Tag>
                        );
                    }
                }
                toAdd.key = keyIndex++;
                toAdd.children = [];
                arr.push(toAdd);
                let containCallNode = false;
                for (let i = 0; i < curCondTree.operands.length; i++) {
                    let curOp = curCondTree.operands[i];
                    if (curOp.type === "XSetOpNode" || curOp.type === "XBasicCallNode" || curOp.type === "XSelectNode") {
                        recurseCondTree(toAdd.children, curOp, keyIndex++);
                    } else {
                        let condTreeData = getCondTreeData(0, keyIndex++, curOp, curCtx);
                        if (curOp.type === 'XLiteralNode' || curOp.type === 'XSelectNode') {
                            condTreeData.title = (
                                <Tag color="white">
                                    <Tag color="cyan">{condTreeData.title}</Tag>
                                </Tag>
                            );
                        } else {
                            condTreeData.title = (
                                <Tag color="white">
                                    <Tag color="cyan">{curOp.sql_string}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag color="cyan">
                                        <b>{condTreeData.title}</b>
                                    </Tag>
                                </Tag>
                            );
                        }
                        toAdd.children.push(condTreeData);
                    }
                }
                if (containCallNode) {
                    return;
                }
            };
            for (const tree of treeData) {
                recurseCondTree(ret, tree, keyIndex);
            }
        } else if (type === "SELECT EXPRESSION") {
            const recurseCondTree = (arr, curCondTree, currentKeyIndex) => {
                let active = curCondTree.type === "XColumnRefNode" ? curCtx.tables[curCtx.dContextExecd.tFromWhere]?.activeTupleIndex
                    : checkAggregateChildren(curCondTree) ? dataTable[0]?.activeTupleIndex
                        : curCtx.tables[curCtx.dContextExecd.tFromWhere]?.activeTupleIndex;
                const toAdd = {};
                let activeValue = "";
                try {
                    if (active != null && (state.xNodeToXTree[curCtx.XTableValuedNode_id]?.group_by_exprs === null || state.xNodeToXTree[curCtx.XTableValuedNode_id]?.group_by_exprs.length === 0)) {
                        activeValue = curCondTree.type === "XLiteralNode" ? " : " + curCondTree.sql_string
                            : " : " + curCtx.dContextExecd.xToValue[curCondTree.id]?.[active];
                    }
                } catch (error) {
                    console.error("Error getting active value:", error);
                }
                toAdd.children = [];
                if ('operands' in curCondTree && curCondTree.operator_name !== "$SCALAR_QUERY") {
                    toAdd.title = <Tag>{curCondTree.operator_name + activeValue}</Tag>;
                    for (let i = 0; i < curCondTree.operands.length; i++) {
                        recurseCondTree(toAdd.children, curCondTree.operands[i], ++currentKeyIndex);
                    }
                } else if ('operand' in curCondTree) {
                    toAdd.title = <Tag>{curCondTree.sql_string}</Tag>;
                    recurseCondTree(toAdd.children, curCondTree.operand, ++currentKeyIndex);
                } else {
                    let title = curCondTree.type === "XLiteralNode" ? curCondTree.sql_string : curCondTree.sql_string + activeValue;
                    if (currentKeyIndex === 0) toAdd.title = <Tag>{title}</Tag>;
                    else toAdd.title = title;
                }
                arr.push(toAdd);
            };
            for (let i = 0; i < treeData.length; i++) {
                recurseCondTree(ret, treeData[i], 0);
            }

        } else {
            let whereConds = treeData[0];
            const recurseCondTree = (arr, curCondTree, currentKeyIndex) => {
                const toAdd = {};
                if (curCondTree.type === 'XSetOpNode' || curCondTree.type === "XSelectNode" || (curCondTree.type === "XBasicCallNode" && curCondTree["operator_name"] === "$SCALAR_QUERY")) {
                    toAdd.title = (
                        <Tag color="white">
                            <Tag color="orange">DRILL DOWN</Tag>
                        </Tag>
                    );
                    if (curCondTree.type === "XBasicCallNode" && curCondTree["operator_name"] === "$SCALAR_QUERY") {
                        toAdd.key = curCondTree.operands[0].id;
                    } else {
                        toAdd.key = curCondTree.id;
                    }
                    toAdd.children = [];
                    arr.push(toAdd);
                } else {
                    if (curCondTree.type !== "XBasicCallNode") return;

                    let opName = curCondTree.operator_name;
                    let condBool = type === "WHERE TREE" ? getCondBoolean(curCondTree, curCtx) : getCondBooleanHaving(curCondTree, curCtx);
                    let singleOp = ['AND', 'OR', 'NOT', 'IN', 'NOT IN', 'EXISTS'];

                    if (singleOp.includes(opName)) {
                        if (condBool === null) {
                            toAdd.title = (
                                <Tag color="white">
                                    <Tag>{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag>
                                        <b>{"NULL"}</b>
                                    </Tag>
                                </Tag>
                            );
                        } else if (condBool === false) {
                            toAdd.title = (
                                <Tag color="white">
                                    <Tag color="red">{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag color="red">
                                        <b>{"False"}</b>
                                    </Tag>
                                </Tag>
                            );
                        } else {
                            toAdd.title = (
                                <Tag color="white">
                                    <Tag color="green">{opName}</Tag>
                                    <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                    <Tag color="green">
                                        <b>{"True"}</b>
                                    </Tag>
                                </Tag>
                            );
                        }
                    } else {
                        let sql_strings = [];
                        for (const obj of curCondTree.sql_string.split(opName)) {
                            sql_strings.push(obj);
                        }
                        let title = sql_strings[0] + opName + sql_strings[1];
                        if (condBool === null) {
                            toAdd.title = (
                                <Tag color="white">
                                    <Tooltip title={<Tag>{title}</Tag>}>
                                        <Tag>{opName}</Tag>
                                        <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                        <Tag>
                                            <b>{"NULL"}</b>
                                        </Tag>
                                    </Tooltip>
                                </Tag>
                            );
                        } else if (condBool === false) {
                            toAdd.title = (
                                <Tag color="white">
                                    <Tooltip title={<Tag color="red">{title}</Tag>}>
                                        <Tag color="red">{opName}</Tag>
                                        <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                        <Tag color="red">
                                            <b>{"False"}</b>
                                        </Tag>
                                    </Tooltip>
                                </Tag>
                            );
                        } else {
                            toAdd.title = condBool === true ? (
                                <Tag color="white">
                                    <Tooltip title={<Tag color="green">{title}</Tag>}>
                                        <Tag color="green">{opName}</Tag>
                                        <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                        <Tag color="green">
                                            <b>{"True"}</b>
                                        </Tag>
                                    </Tooltip>
                                </Tag>
                            ) : (
                                <Tag color="white">
                                    <Tooltip title={<Tag color="blue">{title}</Tag>}>
                                        <Tag color="blue">{opName}</Tag>
                                        <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                        <Tag color="blue">
                                            <b>{condBool}</b>
                                        </Tag>
                                    </Tooltip>
                                </Tag>
                            );
                        }
                    }
                    toAdd.key = currentKeyIndex++;
                    toAdd.children = [];
                    arr.push(toAdd);
                    let containCallNode = false;
                    for (let i = 0; i < curCondTree.operands.length; i++) {
                        let curOp = curCondTree.operands[i];
                        if (type === "WHERE TREE") {
                            if (curOp.type === "XBasicCallNode" || curOp.type === "XSelectNode" || curOp.type === "XSetOpNode") {
                                recurseCondTree(toAdd.children, curOp, currentKeyIndex++);
                            } else {
                                let condTreeData = getCondTreeData(0, currentKeyIndex++, curOp, curCtx);
                                if (curOp.type === 'XLiteralNode' || curOp.type === 'XSelectNode') {
                                    condTreeData.title = (
                                        <Tag color="white">
                                            <Tag color="cyan">{condTreeData.title}</Tag>
                                        </Tag>
                                    );
                                } else {
                                    condTreeData.title = (
                                        <Tag color="white">
                                            <Tag color="cyan">{curOp.sql_string}</Tag>
                                            <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                            <Tag color="cyan">
                                                <b>{condTreeData.title}</b>
                                            </Tag>
                                        </Tag>
                                    );
                                }
                                toAdd.children.push(condTreeData);
                            }
                        } else {
                            let condTreeDataHaving = getCondTreeDataHaving(0, currentKeyIndex++, curOp, curCtx);
                            if (curOp.type === 'XLiteralNode' || curOp.type === 'XSelectNode') {
                                condTreeDataHaving.title = (
                                    <Tag color="white">
                                        <Tag color="cyan">{condTreeDataHaving.title}</Tag>
                                    </Tag>
                                );
                            } else {
                                condTreeDataHaving.title = (
                                    <Tag color="white">
                                        <Tag color="cyan">{curOp.sql_string}</Tag>
                                        <span style={{ color: 'black', fontWeight: 'bold' }}>⟶ </span>
                                        <Tag color="cyan">
                                            <b>{condTreeDataHaving.title}</b>
                                        </Tag>
                                    </Tag>
                                );
                            }
                            toAdd.children.push(condTreeDataHaving);
                            if (curOp.type === "XBasicCallNode") {
                                containCallNode = true;
                            }
                        }
                    }
                    if (containCallNode) {
                        return;
                    }
                }
            };
            recurseCondTree(ret, whereConds, keyIndex);
        }

        return ret;
    }, [treeData, dataTable, type, curCtx, state.xNodeToXTree]);


    const onSelect = useCallback(async (selectedKeys) => {
        if (typeof selectedKeys[0] === "string") {
            let newCtxId = state.xIDtoCtxID[selectedKeys[0]];
            let newCtx = state.ctxToCtxJson[newCtxId];

            if (!newCtx) {
                console.warn("newCtx not found for selected key:", selectedKeys[0]);
                return;
            }

            if (newCtx.bindings.length === 0) {
                if (newCtx.tables.length === 0) {
                    await dispatch({
                        type: 'executeQuery',
                        payload: {
                            db: state.schema_option,
                            queryContext: newCtx
                        }
                    });
                    commit('swapCurCtx', newCtx);
                } else {
                    commit('swapCurCtx', newCtx);
                }
            } else {
                let hasChanged = false;
                let hasBindings = true;

                let prevBindings = [];
                for (let i = 0; i < newCtx.bindings.length; i++) {
                    if (newCtx.bindings[i].value == null) {
                        hasBindings = false;
                        hasChanged = true;
                        break;
                    }
                    prevBindings.push(newCtx.bindings[i].value);
                }

                let curBindings = fetchCurBindings(newCtx, state);

                if (hasBindings) {
                    for (let i = 0; i < curBindings.length; i++) {
                        if (curBindings[i] !== prevBindings[i]) {
                            hasChanged = true;
                            break;
                        }
                    }
                }

                if (hasChanged) {
                    for (let i = 0; i < newCtx.bindings.length; i++) {
                        newCtx.bindings[i].value = curBindings[i];
                    }
                    newCtx.tables = []; // Clear tables to trigger re-fetch if needed
                    await dispatch({
                        type: 'executeQuery',
                        payload: {
                            db: state.schema_option,
                            queryContext: newCtx
                        }
                    });
                    commit('swapCurCtx', newCtx);
                } else {
                    commit('swapCurCtx', newCtx);
                }
            }
        }
    }, [state, dispatch, commit]);

    return (
        <div style={{ overflowX: 'scroll' }}>
            {treeData[0]?.children.length > 0 && <Divider>{type}</Divider>}
            <Tree
                treeData={newTreeData}
                selectedKeys={[]}
                showIcon={true}
                defaultExpandAll
                onSelect={onSelect}
                switcherIcon={<CaretDownOutlined />}
            >
                {/* Ant Design Tree doesn't use slots for icons in the same way Vue does.
                    You typically pass `icon` prop to individual tree nodes in `treeData`
                    or use custom render. For general icons, you can use `switcherIcon`.
                    Smile and Meh icons would be part of the `title` or `icon` property of `treeData` nodes if needed.
                */}
            </Tree>
        </div>
    );
}

export default ConditionTree;