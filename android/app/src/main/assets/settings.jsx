import { setCurrentTheme, getThemeList, getThemes } from '@clevertree/themed-styler';

export default function Settings() {
    const themes = getThemes() || {};
    const availableThemes = getThemeList() || [];
    const currentTheme = themes.currentTheme || 'default';

    return (
        <body>
            <div className="bg-primary p-4 w-full">
                <h1 className="text-white">Settings</h1>
            </div>

            <div className="p-4 w-full">
                <div className="p-4 rounded shadow w-full">
                    <h2 className="mb-2">Theme</h2>
                    <span>
                        Active: {currentTheme}
                    </span>
                    <div className="p-2 w-full">
                        <select
                            className="p-2 w-full rounded bg-surface border-themed"
                            value={currentTheme}
                            onChange={(e) => setCurrentTheme(e.value)}
                        >
                            {availableThemes.map(themeObj => (
                                <option key={themeObj.key} value={themeObj.key}>
                                    {themeObj.key}
                                </option>
                            ))}
                        </select>
                    </div>
                </div>
            </div>
        </body>
    );
}
