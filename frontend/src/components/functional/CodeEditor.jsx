function CodeEditor(props) {
    const state = props.state;
	const dispatch = props.dispatch;
    
    const lines = [];
	for (let i = 1; i <= state.query.split("\n").length; i++) lines.push(i);

    return (
        <div className="div-page">
            <div className="div-lines">
                {lines.map((line) => {
                    return <p key={line}> {line} </p>
                })}
            </div>
            <textarea name="" id="" cols="30" rows="10" onChange={(event) => dispatch(["QUERY", event.target.value])} value={state.query} spellCheck={false}> </textarea>
        </div>
    )

}

export default CodeEditor;