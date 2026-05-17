import Tree from "../Tree";

import "./index.css";

export default function MultitreeSection(props) {
    const data = props.data;
    const tids = props.tids;
    const id_map = props.id_map;
    const eval_maps = props.eval_maps;

    return (
        <div className="div-multitree-section">
            {tids.map((tid, i) => {
                return <div className="div-tree-section" key={tid}>
                    <Tree data={data[i]} tid={tid} id_map={id_map} eval_map={eval_maps}></Tree>
                </div>
            })}
        </div>
    );
}
