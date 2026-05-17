import "./index.css"

import icon_link from "./assets/icon_link.svg"

function Linker(props) {
    const attributes = props.attribute;
    const evaluations = props.evaluations;

    return <section className="section-linker">
        <div className="div-linker-middle">

            <div className="div-linker-left">
                {attributes.map((attribute, i) => {
                    return <div className="div-linker-left-item" key={i}> {attribute.sql_string} </div>
                })}
            </div>
            <div className="div-linker-center">
                <div className="div-linker-line"></div>
                <div className="div-linker-icon-wrapper">
                    <img src={icon_link} alt="" />
                </div>
                <div className="div-linker-line"></div>
            </div>
            <div className="div-linker-right">
                {!evaluations && attributes.map((attribute, i) => {
                    return <div className="div-linker-right-item div-linker-item-none" key={i}> No active group record </div>
                })
                }
                {evaluations && evaluations.map((evaluation, i) => {
                    return <div className="div-linker-right-item" key={i}> {evaluation} </div>
                })}
            </div>
        </div>
    </section>
}

export default Linker;