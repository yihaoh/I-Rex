import SchemaRenderer from "./SchemaRenderer";
import { useEffect, useState } from "react";
import constants from "../../state/constants";

function SideQuery() {
	const [schema, setSchema] = useState({});

	useEffect(() => {
		async function fetchSchema() {
			try {
				const res = await fetch(constants.backend + "/db_metadata");
				const data = await res.json();
				setSchema(data);
			} catch (error) {
				console.error("Failed to fetch schema:", error);
			}
		}
		fetchSchema();
	}, []);

	return <div className="div-side-item">
		<div className="div-side-title">Schema</div>
		<div className="div-code">
			<SchemaRenderer schema={schema}></SchemaRenderer>
		</div>
		<div className="div-side-section">
			<button onClick={() => window.location.href = 'https://forms.gle/pAxixtPiTo7dTnBK6'} style={{ margin: "1rem 0" }}> Report Bugs</button>
		</div>
	</div >;
}

export default SideQuery;
