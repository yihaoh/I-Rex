import "./index.css";

export default function Divider(props) {
	const title = props.title;

	return (
		<div className="div-separator"> {title} </div>
	);
}
