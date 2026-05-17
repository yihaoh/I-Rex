import CodeEditor from "../components/functional/CodeEditor";

function PageQuery(props) {
    const state = props.state;
	const dispatch = props.dispatch;

    return <CodeEditor state={state} dispatch={dispatch}></CodeEditor>
}

export default PageQuery;