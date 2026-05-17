import { useEffect, useState } from "react";
import Table from "./Table";
import Linker from "./Linker";
// import GroupByTable from "./GroupByTable";
// import ExpandedTable from "./ExpandedTable";

function DataTable(props) {
    const meta = props.meta;
    const expanded = meta[1];
    const group = meta[0];
    const state = props.state;
    const active_group = props.active_group;
    const active_expanded = props.active_expanded;
    const expanded_space = props.expanded_space;
    const group_iid = props.group_iid;

    const [active, set_active] = useState(null);

    async function retrieve0(lower, upper) {
        return await state.cache.fetch(group, lower, upper);
    }

    async function retrieve1(lower, upper) {
        if (!expanded_space) return [];
        if (expanded_space[0] === -1 || expanded_space[1] === -1 || expanded_space[0] > expanded_space[1]) return [];

        // let contents = await state.cache.fetch(expanded, expanded_space[0] + lower, Math.min(expanded_space[1], expanded_space[0] + upper));
        let contents = await state.cache.fetch(expanded, lower, upper);

        // console.log(lower, expanded_space[0], expanded_space[0] + lower, Math.min(expanded_space[1], expanded_space[0] + upper), contents)
        // let iid_column = 0;

        // for (let i = 0; i < expanded.column_names.length; i++) {
        //     if (expanded.column_names[i] == "GROUP_IID") {
        //         iid_column = i;
        //     }
        // }

        // let matching_contents = [];

        // for (let i = 0; i < contents.length; i++) {
        //     if (contents[i][iid_column] == active) {
        //         matching_contents.push(contents[i]);
        //     }
        // }
        return contents;
    }

    function group_table_context(column_names, column_use) {
        let default_cols = [];
        let display_names = [];
        let iid_col = 0;

        for (let i = 0; i < column_names.length; i++) {
            if (column_use[i] == "IID") {
                iid_col = i;
            }

            if (column_use[i] == "DISPLAY" || (state.cache.parsed.analyze.xNodeToXTree[column_names[i]] && state.cache.parsed.analyze.xNodeToXTree[column_names[i]].is_aggregate)/*|| meta.hooks[i].length != 0*/) {
                default_cols[i] = true;
            } else {
                default_cols[i] = false;
            }

            if (column_names[i] in state.cache.parsed.analyze.xNodeToXTree) {
                display_names.push(state.cache.parsed.analyze.xNodeToXTree[column_names[i]].sql_string)
            } else {
                display_names.push(column_names[i])
            }
        }

        // console.log(column_names)

        return {
            default_cols: default_cols,
            display_names: display_names,
            iid_col: iid_col,
        }
    }

    function expanded_table_context(column_names, column_use) {
        let default_cols = [];
        let display_names = [];
        let iid_col = 0;

        for (let i = 0; i < column_names.length; i++) {
            if (column_use[i] == "IID") {
                iid_col = i;
            }

            if (column_use[i] == "IID" /*|| meta.hooks[i].length != 0*/) {
                default_cols[i] = false;
            } else {
                default_cols[i] = true;
            }

            if (column_names[i] in state.cache.parsed.analyze.xNodeToXTree) {
                display_names.push(state.cache.parsed.analyze.xNodeToXTree[column_names[i]].sql_string)
            } else {
                display_names.push(column_names[i])
            }
        }

        return {
            default_cols: default_cols,
            display_names: display_names,
            iid_col: iid_col,
        }
    }

    const group_context = group_table_context(group.column_names, group.column_use);
    const expanded_context = expanded_table_context(expanded.column_names, expanded.column_use);

    useEffect(() => {
        effect();

        async function effect() {
            if (active_group === null) {
                set_active(null)
                return
            };

            let iid_column;
            for (let i = 0; i < expanded.column_names.length; i++) {
                if (expanded.column_names[i] == "GROUP_IID") {
                    iid_column = i;
                }
            }

            set_active((await state.cache.fetch(expanded, active_group, active_group))[0][iid_column])
        }
    }, [active_group])

    return <div style={{ display: "flex", flexDirection: "column" }}>
        <Table retrieve={retrieve0} set_act={set_active} meta={{ title: group.name, column_names: group_context.display_names, hooks: group.hooks, column_use: group.column_use, rows: group.rows, type: group.type, column_ids: group.column_names, column_types: group.column_types }} active_row={active_group} iid_col={group_context.iid_col} default_cols={group_context.default_cols} final_cols={state.cache.parsed.analyze.select_ids}></Table>
        <Linker attribute={state.cache.parsed.analyze.xtree.group_by_exprs} evaluations={group_iid}></Linker>
        <Table retrieve={retrieve1} active={active} meta={{ title: expanded.name, column_names: expanded_context.display_names, hooks: expanded.hooks, column_use: expanded.column_use, rows: expanded.rows, type: expanded.type, column_types: expanded.column_types }} active_row={active_expanded} iid_col={expanded_context.iid_col} default_cols={expanded_context.default_cols} expanded_space={expanded_space}></Table>
    </div>;
}

export default DataTable;