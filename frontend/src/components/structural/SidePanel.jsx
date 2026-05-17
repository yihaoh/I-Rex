import SideDebug from "../functional/SideDebug";
import SideQuery from "../functional/SideQuery";
import SideResult from "../functional/SideResult";

function SidePanel(props) {
	const state = props.state;
	const dispatch = props.dispatch;

	return (
		<div className="div-sidebar">
			{/* <div className="div-sidebar-main">
				<div className="div-header-branding">
					<span className="span-header-branding"> I-REX </span>
					<span className="span-header-version"> 2.0 </span>
				</div>
			</div>
			<div>
				<div className="div-view-switch">
					<button className={`button-view ${state.view === "query" && "button-view-active"}`} onClick={() => dispatch(["VIEW", "query"])}>
						Query
					</button>
					<button className={`button-view ${state.view === "result" && "button-view-active"}`} onClick={() => dispatch(["VIEW", "result"])}>
						Result
					</button>
					<button className={`button-view ${state.view === "debug" && "button-view-active"}`} onClick={() => dispatch(["VIEW", "debug"])}>
						Debug
					</button>
				</div>
			</div> */}

			<div className="div-sidebar-main">
				<div className="div-header-branding">
					<span className="span-header-branding"> I-REX </span>
					<span className="span-header-version"> 2.0 </span>
				</div>
			</div>
			<div>
				<div className="div-view-switch">
					<button className={`button-view ${state.view === "query" && "button-view-active"}`} onClick={() => dispatch(["VIEW", "query"])}>
						Query
					</button>
					<button className={`button-view ${state.view === "debug" && "button-view-active"}`} onClick={() => dispatch(["VIEW", "debug"])}>
						Debug
					</button>
				</div>
			</div>

			{state.view === "query" && <SideQuery></SideQuery>}
			{state.view === "debug" && <SideDebug state={state} dispatch={dispatch} query={state.query}></SideDebug>}
			{state.view === "result" && <SideResult></SideResult>}

			<p className="p-header-group"> DUKE UNIVERSITY DB RESEARCH </p>
		</div>
	);
}

export default SidePanel;
