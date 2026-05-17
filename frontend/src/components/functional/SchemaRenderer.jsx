function SchemaRenderer(props) {
    const schema = props.schema;

    if (!schema || Object.keys(schema).length === 0) {
        return <div>Loading schema...</div>;
    }

    return (
        <div>
            {Object.entries(schema).map(([db, tables]) =>
                Object.entries(tables).map(([table, fields]) => {
                    const pkeys = fields.pkeys || [];
                    return (
                        <div key={table} style={{ marginBottom: 8 }}>
                            <h4 style={{ margin: "4px 0", fontStyle: "normal" }}>{table}</h4>
                            <ul style={{ paddingLeft: 0, margin: 0, listStyle: "none", fontStyle: "normal" }}>
                                {Object.entries(fields).map(([field, type]) =>
                                    field === "pkeys" ? null : (
                                        <li key={field} style={{ lineHeight: 1.2 }}>
                                            <span style={{ textDecoration: pkeys.includes(field) ? "underline" : "none" }}>
                                                {field}
                                            </span>: {type}
                                        </li>
                                    )
                                )}
                            </ul>
                        </div>
                    );
                })
            )}
        </div>
    );
}

export default SchemaRenderer;
