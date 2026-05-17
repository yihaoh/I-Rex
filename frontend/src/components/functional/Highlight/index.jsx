function Highlight(props) {
    const text = props.text
    const begin = props.begin
    const end = props.end

    // Edge case: validate input
    if (begin >= end || begin < 0 || end > text.length) {
        return <div>{text}</div>;
    }

    const before = text.slice(0, begin);
    const highlight = text.slice(begin, end);
    const after = text.slice(end);

    return (
        <div>
            <span>{before}</span>
            <span
                style={{
                    backgroundColor: '#ffff00',
                    borderRadius: '4px',
                }}
            >
                {highlight}
            </span>
            <span>{after}</span>
        </div>
    );
}

export default Highlight