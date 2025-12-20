import React, { useState, useEffect, useRef } from 'react';

// Simplified HookRenderer for Pure Android
export const HookRenderer = ({ host, hookPath = '/hooks/client/get-client.jsx' }) => {
  const [element, setElement] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    
    const loadHook = async () => {
      try {
        // Use native transpile bridge
        const response = await fetch(`${host}${hookPath}`);
        const code = await response.text();
        
        const transpiled = await nativeBridge.transpile(code, hookPath);
        
        // Executing transpiled code which includes its own CommonJS wrapper
        // and sets globalThis.__last_module__
        const fn = new Function('React', transpiled);
        fn(React);
        
        if (mounted) {
          const HookComponent = (globalThis.__last_module__ && globalThis.__last_module__.default);
          if (HookComponent) {
            setElement(<HookComponent />);
          } else {
            setError("Hook did not export a default component");
          }
          setLoading(false);
        }
      } catch (err) {
        if (mounted) {
          setError(err.message);
          setLoading(false);
        }
      }
    };

    loadHook();
    return () => { mounted = false; };
  }, [host, hookPath]);

  if (loading) return <div><span>Loading Hook...</span></div>;
  if (error) return <div className="p-4 bg-red-50"><span className="text-red-800">Error: {error}</span></div>;
  return element;
};

globalThis.HookRenderer = HookRenderer;
