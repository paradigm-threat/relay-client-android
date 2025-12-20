export default function Home() {
    const versions = globalThis.__versions || {};
    const tVer = versions.transpiler || 'unknown';
    const sVer = versions.styler || 'unknown';

    return (
        <div className="flex-1 bg-gray-100 w-full h-full p-4" width="match_parent" height="match_parent">
            <div className="bg-blue-500 p-4 w-full" width="match_parent" height="wrap_content">
                <span className="text-white text-lg text-center" width="wrap_content" height="wrap_content">
                    Relay Pure Client
                </span>
            </div>

            <div className="p-4 w-full" width="match_parent" height="wrap_content">
                <span className="text-green-500 p-2" width="wrap_content" height="wrap_content">✓ QuickJS Runtime</span>
                <span className="text-green-500 p-2" width="wrap_content" height="wrap_content">✓ Hook Transpiler v{tVer}</span>
                <span className="text-green-500 p-2" width="wrap_content" height="wrap_content">✓ Themed Styler v{sVer}</span>
            </div>

            <div className="p-4 w-full" width="match_parent" height="wrap_content">
                <div className="bg-white p-4 rounded shadow" width="match_parent" height="wrap_content">
                    <span className="text-gray-800" width="wrap_content" height="wrap_content">
                        Welcome to Relay Pure Android.
                    </span>
                </div>
            </div>
        </div>
    );
}
