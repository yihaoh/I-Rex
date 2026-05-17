import React from "react";
import ErrorMessage from "../ErrorMessage"

class ErrorBoundary extends React.Component {
    state = { error: null };

    static getDerivedStateFromError(error) {
        return { error };
    }

    render() {
        return (
            <>
                <ErrorMessage error={this.state.error} />
                {this.props.children}
            </>
        );
    }
}


export default ErrorBoundary