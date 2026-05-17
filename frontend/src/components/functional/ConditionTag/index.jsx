import React, { useState, useMemo, useContext, createContext } from 'react';
import { Tag } from 'antd';
import { CheckCircleOutlined } from '@ant-design/icons';

// --- Mocking Vuex Store for demonstration ---
// In a real React app, you would have your actual store setup
// For simplicity, we'll create a mock context for demonstration.
const MockStoreContext = createContext(null);

const MockStoreProvider = ({ children }) => {
    const [store, setStore] = useState({
        curCtx: {
            children: {
                'BLOCK-1': { key: 'ctxKey1' },
                'BLOCK-2': { key: 'ctxKey2' },
            },
            finalAgg: {
                cursor: [0],
                data: [[100, 200]],
            },
            groupByList: [],
            havingIds: ['condId1'],
        },
        tables: {
            'tableId1': {
                tableId: 'dbTable1',
                identifier: 'tbl1_alias',
                dbTableName: 'REAL_DB_TABLE_1',
                cursor: [0],
            },
            'NS_SELECT_1': {
                tableId: 'dbTable_NS_SELECT_1',
                identifier: 'ns_select_alias',
            },
            'NS_IDENT_1': {
                blockId: 'SOME_BLOCK-123',
                identifier: 'ns_ident_alias',
            },
        },
        db: {
            'REAL_DB_TABLE_1': {
                columns: ['COL1', 'COL2'],
                data: [['value1', 'VALUE_FROM_DB']],
            },
        },
    });

    const getters = useMemo(() => ({
        ctxByKey: (key) => {
            // Mock implementation for ctxByKey
            if (key === 'ctxKey1') {
                return {
                    scopedSlots: {
                        tagText: 'Block Context 1',
                        color: 'blue',
                        key: 'selectedKey1',
                    },
                };
            }
            if (key === 'ctxKey2') {
                return {
                    scopedSlots: {
                        tagText: 'Block Context 2',
                        color: 'orange',
                        key: 'selectedKey2',
                    },
                };
            }
            return null;
        },
        ctxByTableId: (tableId) => {
            // Mock implementation for ctxByTableId
            if (tableId === 'tableId1') {
                return {
                    scopedSlots: {
                        tagText: 'Table Context 1',
                        color: 'purple',
                    },
                };
            }
            return null;
        },
        queryExcerpt: (pos, length) => `Query Excerpt at ${pos} (len ${length})`,
    }), []);

    const dispatch = useMemo(() => ({
        changeContext: ({ key, selectedKeys }) => {
            console.log(`Dispatching changeContext: key=${key}, selectedKeys=${selectedKeys}`);
            // In a real app, this would update the store
        },
    }), []);

    return (
        <MockStoreContext.Provider value={{ store, getters, dispatch }}>
            {children}
        </MockStoreContext.Provider>
    );
};

// --- Helper Constants (replace with your actual constants) ---
const SUBQUERY_TYPES = ['SUBQUERY_TYPE_A', 'SUBQUERY_TYPE_B'];
const BOOL_TYPES = ['TRUE', 'FALSE'];

// --- React Component ---
const ConditionTag = ({ cond, tagType }) => {
    const { store, getters, dispatch } = useContext(MockStoreContext);

    const tagStyle = { fontSize: '18px' };

    const tagProps = useMemo(() => {
        let text = 'placeholder';
        let tagColor = null;
        let result = {};

        if (SUBQUERY_TYPES.includes(cond.value)) {
            text = cond.value;
            result = { text };
        } else if (BOOL_TYPES.includes(cond.value)) {
            text = cond.value;
            result = { text };
        } else if (tagType.startsWith('BLOCK')) {
            let targetKey = null;
            for (const child in store.curCtx.children) {
                if (child === tagType) {
                    targetKey = store.curCtx.children[child].key;
                    break;
                }
            }
            const ctx = getters.ctxByKey(targetKey);
            if (ctx) {
                text = ctx.scopedSlots.tagText;
                tagColor = ctx.scopedSlots.color;
            }
            result = { text, tagColor };
        } else if (tagType.startsWith('inner')) {
            text = tagType.split('-')[1];
            result = { text };
        } else if (tagType.startsWith('column')) {
            // get actual table dbName, alias
            const [tableId, column] = cond.value.split('.');
            let dbTableName = null;
            let identifier = null;

            if (store.tables[tableId]) {
                if (tableId.includes("NS_SELECT") || tableId.includes("NS_SETOP")) {
                    dbTableName = store.tables[tableId].tableId;
                    identifier = store.tables[tableId].identifier;
                } else if (tableId.includes("NS_IDENT") && store.tables[tableId].hasOwnProperty('blockId')) {
                    dbTableName = store.tables[tableId]['blockId'].split("-")[0];
                    identifier = store.tables[tableId].identifier;
                } else {
                    dbTableName = store.tables[tableId].dbTableName;
                    identifier = store.tables[tableId].identifier;
                }
            }

            const ctx = getters.ctxByTableId(tableId);
            // props for ctx tag
            const ctxTagText = ctx ? ctx.scopedSlots.tagText : null;
            const ctxTagColor = ctx ? ctx.scopedSlots.color : null;

            // get actual value binding
            const dbTable = store.db[dbTableName];
            let actualVal = '(null)';
            if (dbTable && store.tables[tableId] && store.tables[tableId].cursor) {
                const colInd = dbTable.columns.indexOf(column.toUpperCase());
                const rowInd = store.tables[tableId].cursor[0];
                if (rowInd >= 0 && colInd >= 0 && dbTable.data && dbTable.data[rowInd]) {
                    actualVal = dbTable.data[rowInd][colInd];
                }
            }

            result = { column, dbTableName, identifier, ctxTagText, ctxTagColor, actualVal };
        } else if (tagType.startsWith('agg')) {
            text = getters.queryExcerpt(cond.pos, 30);
            let actualVal = '(null)';
            if (store.curCtx.finalAgg && store.curCtx.finalAgg.cursor) {
                const rowInd = store.curCtx.finalAgg.cursor[0];
                const colInd = store.curCtx.groupByList.length + store.curCtx.havingIds.indexOf(cond.id);
                if (rowInd >= 0 && colInd >= 0 && store.curCtx.finalAgg.data && store.curCtx.finalAgg.data[rowInd]) {
                    actualVal = store.curCtx.finalAgg.data[rowInd][colInd];
                }
            }
            result = { text, actualVal };
        } else if (cond.pos === undefined) {
            text = cond.naturalText;
            result = { text };
        } else {
            text = getters.queryExcerpt(cond.pos, 30);
            result = { text };
        }

        return result;
    }, [cond, tagType, store, getters]); // Dependencies for useMemo

    const onClickCtxTag = () => {
        let targetKey = null;
        for (const child in store.curCtx.children) {
            if (child === tagType) {
                targetKey = store.curCtx.children[child].key;
                break;
            }
        }
        const newCtx = getters.ctxByKey(targetKey);
        const newSelectedKey = newCtx ? [newCtx.scopedSlots.key] : [];
        dispatch('changeContext', { key: targetKey, selectedKeys: newSelectedKey });
    };

    const tagContent = tagProps.text || tagProps.column || tagProps.ctxTagText || 'N/A';
    const tagDisplayColor = tagProps.tagColor || 'green';

    return (
        <div>
            <Tag style={tagStyle} color={tagDisplayColor} onClick={tagType.startsWith('BLOCK') ? onClickCtxTag : undefined}>
                {tagContent}
                <CheckCircleOutlined twoToneColor="#52c41a" />
            </Tag>
        </div>
    );
};

export default ConditionTag;
export { MockStoreProvider }; // Export the provider for wrapping your app