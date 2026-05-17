import Tree from "../Tree";

import "./index.css";

export default function TreeSection(props) {
    const data = props.data;
    const tid = props.tid;
    const id_map = props.id_map;
    const eval_map = props.eval_map;

    return (
        <div className="div-tree-section">
            <Tree data={data} tid={tid} id_map={id_map} eval_map={eval_map}></Tree>
        </div>
    );
}
