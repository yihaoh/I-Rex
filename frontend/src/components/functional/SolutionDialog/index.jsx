import "./index.css";

import icon_accepted from "./assets/accepted.svg";
import icon_incorrect from "./assets/incorrect.svg";

export default function SolutionDialog(props) {
	const state = props.state;
	const dispatch = props.dispatch;

	const correct = true;

	return (
		<div className="div-dialog">
			<div className="div-solution-dialog">
				{correct && (
					<div className="div-solution-res">
						<span className="span-solution"> Your solution is </span> <span className="span-solution-accepted"> accepted </span> <img className="img-solution" src={icon_accepted} alt="accepted" />
					</div>
				)}

				{!correct && (
					<div className="div-solution-res">
						<span className="span-solution"> Your solution is </span> <span className="span-solution-incorrect"> incorrect </span> <img className="img-solution" src={icon_incorrect} alt="incorrect" />
					</div>
				)}
			</div>
		</div>
	);
}
