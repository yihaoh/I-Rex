import { useState } from 'react';
import './index.css';

import icon_expand from './assets/icon_expand.svg';

export default function Tree(props) {
    // const meta = props.meta;
    const data = props.data;
    const tid = props.tid;
    const id_map = props.id_map;
    const eval_map = props.eval_map;

    if (!data) return;

    return (
        <div className="div-tree-root">
            <TreeLayer data={data} tid={tid} id_map={id_map} eval_map={eval_map}></TreeLayer>
        </div>
    );
}

function TreeLayer(props) {
    const data = props.data;
    const branch = props.branch || '';
    const trace = props.trace || [];
    const keys = Object.keys(data);
    const tid = props.tid;
    const id_map = props.id_map;
    const eval_map = props.eval_map;

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
    const display_string = id_map[k].display_string;
    let eval_string = display_string ? display_string : eval_map[k] === false ? "false" : eval_map[k] === true ? "true" : eval_map[k] === null ? "NULL" : eval_map[k];
    const eval_style = eval_map[k] === false ? "span-tree-node-eval-false" : eval_map[k] === true ? "span-tree-node-eval-true" : eval_map[k] === null ? "span-tree-node-eval-null" : "";

    if (!eval_string) {
        for (let id in id_map) {
            if (id_map[id].sql_string === id_map[k].sql_string) {
                eval_string = eval_map[id];
            }
        }
    }

    const [checked, c_checked] = useState(false);

    function check() {
        c_checked(() => !checked);
    }

    return (
        <div className="div-tree-layer">
            <div className="div-tree-node">
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
                <span className="span-tree-node"> {id_map[k].sql_string} </span>
                {(eval_string === 0 || eval_string) && <span className={"span-tree-node span-tree-eval " + eval_style}> {eval_string} </span>}
            </div>
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

            <div className="div-tree-sublayer">
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
            </div>
        </div>
    );
}
