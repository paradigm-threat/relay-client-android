function Settings() {
    const runtime = (globalThis.__runtime && globalThis.__runtime.mode) || 'react';

    const setRuntime = (mode) => {
        if (mode === runtime) {
            console.log('Runtime already set to', mode);
            return;
        }
        if (typeof globalThis.__nativeSetRuntime === 'function') {
            globalThis.__nativeSetRuntime(mode);
        } else {
            console.warn('Runtime switch is not available');
        }
    };

    return (
        <div className="p-4" width="match_parent" height="wrap_content">
            <div className="bg-blue-500 p-4" width="match_parent" height="wrap_content">
                <span className="text-white lg">Settings</span>
            </div>

            <div className="p-4" width="match_parent" height="wrap_content">
                <span className="text-gray-800" width="wrap_content" height="wrap_content">
                    Choose a runtime. If a screen looks off, try the other option.
                </span>
            </div>

            <div className="p-4" width="match_parent" height="wrap_content">
                <div className="bg-white p-4 rounded shadow" width="match_parent" height="wrap_content">
                    <span className="text-gray-800" width="wrap_content" height="wrap_content">
                        Active runtime: {runtime.toUpperCase()}
                    </span>
                    <div className="p-2" width="match_parent" height="wrap_content">
                        <button className="bg-blue-500 text-white p-2 m-2" width="wrap_content" height="wrap_content" onClick={() => setRuntime('react')}>
                            Switch to React
                        </button>
                        <button className="bg-green-500 text-white p-2 m-2" width="wrap_content" height="wrap_content" onClick={() => setRuntime('act')}>
                            Switch to Act (lightweight)
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

module.exports = Settings;
