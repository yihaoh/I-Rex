import React, { useState, useEffect } from 'react';

function ErrorNotification(props) {
    const [errors, setErrors] = useState([]);

    // Function to add a new error
    function addError(error) {
        const id = Date.now(); // Unique ID for each error
        setErrors(prevErrors => [...prevErrors, { id, message: error.message || String(error) }]);

        // Remove the error after 5 seconds
        setTimeout(() => {
            setErrors(prevErrors => prevErrors.filter(e => e.id !== id));
        }, 5000);
    }

    // Error boundary effect - catches errors from children
    useEffect(() => {
        if (props.error) {
            addError(props.error);
        }
    }, [props.error]);

    // Global error handler
    useEffect(() => {
        const originalErrorHandler = window.onerror;
        const originalUnhandledRejectionHandler = window.onunhandledrejection;

        // Handle runtime errors
        window.onerror = function (message, source, lineno, colno, error) {
            addError(error || message);
            return true; // Prevent default browser error handling
        };

        // Handle promise rejections
        window.onunhandledrejection = function (event) {
            addError(event.reason);
            return true; // Prevent default browser error handling
        };

        // Cleanup
        return () => {
            window.onerror = originalErrorHandler;
            window.onunhandledrejection = originalUnhandledRejectionHandler;
        };
    }, []);

    if (errors.length === 0) {
        return null; // Don't render anything if no errors
    }

    return (
        <div style={{
            position: 'fixed',
            bottom: '20px',
            right: '20px',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            gap: '10px'
        }}>
            {errors.map((error, index) => (
                <div key={error.id} style={{
                    padding: '15px',
                    backgroundColor: '#ffebee',
                    borderLeft: '4px solid #f44336',
                    borderRadius: '4px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
                    minWidth: '250px',
                    animation: 'fadeIn 0.3s',
                    opacity: 1 - (index * 0.1) // Slightly fade older notifications
                }}>
                    {error.message}
                </div>
            ))}
        </div>
    );
}

export default ErrorNotification

// Usage example:
// <ErrorNotification />
// or
// <ErrorNotification error={someError} />