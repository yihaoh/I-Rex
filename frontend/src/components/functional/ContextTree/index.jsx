import { useState } from 'react';
import './index.css';

import icon_current from "./assets/icon_current.svg"
import icon_expand from './assets/icon_expand.svg';
import icon_jump from "./assets/icon_jump.svg"

export default function Tree(props) {
    // const meta = props.meta;
    const data = props.data;
    const tid = props.tid;
    const id_map = props.id_map;
    const eval_map = props.eval_map;
    const current = props.current;
    const change = props.change;

    return (
        <div className="div-ctx-tree-root">
            <TreeLayer data={data} tid={tid} id_map={id_map} eval_map={eval_map} current={current} change={change}></TreeLayer>
        </div>
    );
}

const type_map = {
    "DSelectContext": "Select Context",
    "DJoinContext": "Join Context"
}

function TreeLayer(props) {
    const data = props.data;
    const branch = props.branch || '';
    const trace = props.trace || [];
    const keys = Object.keys(data);
    const tid = props.tid;
    const id_map = props.id_map;
    const eval_map = props.eval_map;
    const current = props.current;
    const change = props.change;

    if (!keys.length) return;

    return keys.map((k, i) => {
        return (
            <TreeNode
                data={data[k]}
                branch={branch + i}
                trace={structuredClone(trace).concat([k])}
                k={k}
                tid={tid}
                id_map={id_map}
                eval_map={eval_map}
                current={current}
                change={change}
            ></TreeNode>
        );
    });
}

function TreeNode(props) {
    const data = props.data;
    const branch = props.branch;
    const trace = props.trace;
    const k = props.k;
    const tid = props.tid;
    const deeper = Object.keys(data).length ? true : false;
    const id_map = props.id_map;
    const eval_map = props.eval_map;
    const current = props.current;
    const change = props.change;

    const [checked, c_checked] = useState(false);

    function check() {
        c_checked(() => !checked);
    }

    return (
        <div className="div-tree-layer">
            {(id_map[k].type !== "DJoinContext" || id_map[k].join_type === "INNER" || id_map[k].join_type === "LEFT" || id_map[k].join_type === "RIGHT") && <div className="div-tree-node">
                <input
                    className="input-tree-node"
                    id={`input-tree-node-${tid}-${branch}`}
                    type="checkbox"
                    onChange={check}
                />
                {/* <label
                    className={`label-tree-node ${deeper ? '' : 'label-tree-node-end'
                        }`}
                    htmlFor={`input-tree-node-${tid}-${branch}`}
                >
                    <img className="img-tree-node" src={icon_expand} alt="" />
                </label> */}
                {deeper && <label
                    className={`label-tree-node ${deeper ? '' : 'label-tree-node-end'
                        }`}
                    htmlFor={`input-tree-node-${tid}-${branch}`}
                >
                    <img className="img-tree-node" src={icon_expand} alt="" />
                </label>}
                <div className="div-tree-node-text">
                    {id_map[k] ? (type_map[id_map[k].type]) : k}
                    {id_map[k].join_type && <div className="div-tree-node-type">
                        {id_map[k].join_type}
                    </div>}
                </div>

                {current !== k && (id_map[k].type !== "DJoinContext" || id_map[k].join_type === "INNER" || id_map[k].join_type === "LEFT" || id_map[k].join_type === "RIGHT") && < img src={icon_jump} className="img-jump" onClick={function () { if (change) change(k) }} />}
                {current === k && <img src={icon_current} className="img-current" />}

            </div>}
            {/* <div className="div-tree-sublayer">
                {deeper && checked && (
                    <TreeLayer
                        data={data}
                        branch={branch}
                        trace={trace}
                        tid={tid}
                        id_map={id_map}
                        eval_map={eval_map}
                    ></TreeLayer>
                )}
            </div> */}

            {!(id_map[k].type !== "DJoinContext" || id_map[k].join_type === "INNER" || id_map[k].join_type === "LEFT" || id_map[k].join_type === "RIGHT") && deeper && <TreeLayer
                data={data}
                branch={branch}
                trace={trace}
                tid={tid}
                id_map={id_map}
                eval_map={eval_map}
                current={current}
                change={change}
            ></TreeLayer>}

            {((id_map[k].type !== "DJoinContext" || id_map[k].join_type === "INNER" || id_map[k].join_type === "LEFT" || id_map[k].join_type === "RIGHT")) && <div className="div-tree-sublayer">
                {deeper && checked && (
                    <TreeLayer
                        data={data}
                        branch={branch}
                        trace={trace}
                        tid={tid}
                        id_map={id_map}
                        eval_map={eval_map}
                        current={current}
                        change={change}
                    ></TreeLayer>
                )}
            </div>}
        </div>
    );
}
