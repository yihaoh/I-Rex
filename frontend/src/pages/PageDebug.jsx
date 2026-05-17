import TableManager from "../components/functional/TableManager";

function PageDebug(props) {
    const state = props.state;
	const dispatch = props.dispatch;

    return <TableManager state={state} dispatch={dispatch}></TableManager>
}

export default PageDebug;