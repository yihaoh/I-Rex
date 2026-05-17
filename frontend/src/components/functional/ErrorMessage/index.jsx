import { useState, useEffect } from 'react';

const ErrorMessage = ({ error }) => {
    const [errors, setErrors] = useState([]);

    useEffect(() => {
        if (error) {
            const errorId = Date.now();
            // Add new error to the beginning of the array (so they stack from bottom up)
            setErrors(prev => [{ id: errorId, message: error.message || String(error) }, ...prev]);

            // Remove the error after 5 seconds
            const timer = setTimeout(() => {
                setErrors(prev => prev.filter(err => err.id !== errorId));
            }, 10000);

            return () => clearTimeout(timer);
        }
    }, [error]);

    if (errors.length === 0) return null;

    return (
        <div style={{
            position: 'fixed',
            bottom: '20px',
            right: '20px',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column-reverse', // This makes new items appear at the bottom
            gap: '10px'
        }}>
            {errors.map((err) => (
                <div key={err.id} style={{
                    padding: '15px',
                    backgroundColor: '#ffebee',
                    color: '#d32f2f',
                    borderRadius: '4px',
                    boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
                    borderLeft: '4px solid #d32f2f',
                    maxWidth: '300px',
                    animation: 'fadeIn 0.3s ease-in-out'
                }}>
                    {err.message}
                </div>
            ))}
        </div>
    );
};

// Optional CSS for fade-in animation (add to your global styles)
// @keyframes fadeIn {
//   from { opacity: 0; transform: translateY(10px); }
//   to { opacity: 1; transform: translateY(0); }
// }

export default ErrorMessage;