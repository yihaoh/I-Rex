import PageDebug from "../../pages/PageDebug";
import PageQuery from "../../pages/PageQuery";
import PageResult from "../../pages/PageResult";
import Loader from "../functional/Loader";

function ViewPanel(props) {
	const state = props.state;
	const dispatch = props.dispatch;

	let action_ok = true;
	for (let i = 0; i < state.actions.length; i++) if (state.actions[i]) action_ok = false;

	return (
		<div className="div-content">
			<div className="div-main-top">
				{state.view === "debug" && <button
					className="button-secondary"
					onClick={() => {
						dispatch(["SET_CURRENT", state.current - 1]);
					}}
					disabled={!action_ok}>
					<div className="div-button-text" hidden={state.actions[0]}>
						Step Backward
					</div>
					<div className={`${state.actions[0] && "div-button-loading"}`}></div>
				</button>}
				{state.view === "debug" && <button
					className="button-secondary"
					onClick={() => {
						dispatch(["SET_CURRENT", state.current + 1]);
					}}
					disabled={!action_ok}>
					<div className="div-button-text" hidden={state.actions[0]}>
						Step Forward
					</div>
					<div className={`${state.actions[0] && "div-button-loading"}`}></div>
				</button>}
				{state.view === "query" && <button
					className="button-secondary"
					onClick={() => {
						dispatch(["ACTION", 0]);
					}}
					disabled={!action_ok}>
					<div className="div-button-text" hidden={state.actions[0]}>
						Format
					</div>
					<div className={`${state.actions[0] && "div-button-loading"}`}></div>
				</button>}

				<button
					className="button-primary"
					onClick={() => {
						dispatch(["LOAD", "analyze"]);
					}}
					disabled={!action_ok}>
					<div className="div-button-text" hidden={state.actions[1]}>
						Execute
					</div>
					<div className={`${state.actions[1] && "div-button-loading"}`}></div>
				</button>
			</div>

			<div className="div-main">
				{state.view === "query" && <PageQuery state={state} dispatch={dispatch}></PageQuery>}
				{state.view === "debug" && <PageDebug state={state} dispatch={dispatch}></PageDebug>}
				{state.view === "result" && <PageResult state={state} dispatch={dispatch}></PageResult>}

				{state.status.analyzing && <Loader state={state} dispatch={dispatch}></Loader>}
			</div>
		</div>
	);
}

export default ViewPanel;
