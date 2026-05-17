import { useReducer } from "react";

import State from "./state/state";

import SidePanel from "./components/structural/SidePanel";
import ViewPanel from "./components/structural/ViewPanel";

import ErrorHandler from "./components/functional/Error";

import "./App.css";
import "./dark.css";

function App() {
	const [state, dispatch] = useReducer(State.reducer, State.init);

	return (
		<div className="app">
			<ErrorHandler />
			<SidePanel state={state} dispatch={dispatch}></SidePanel>
			<ViewPanel state={state} dispatch={dispatch}></ViewPanel>
		</div>
	);
}

export default App;
