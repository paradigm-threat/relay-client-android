import { useState } from 'react';

export default function StyleTests() {
    const [bodyTall, setBodyTall] = useState(false);
    const bodyHeightClass = bodyTall ? 'h-full' : 'h-auto';

    return <body className={`w-full ${bodyHeightClass} flex flex-col gap-4 items-stretch`} test-id="style-tests-body">
        <div className="bg-primary p-4 w-full" test-id="style-tests-header">
            <h1 className="text-white">Style Tests</h1>
            <p className="text-sm text-muted">Use this screen to verify how the body and peer cards respond to different height controls.</p>
        </div>

        <div className="p-4 rounded border-themed w-full flex flex-col gap-3" test-id="style-tests-controls">
            <div className="flex-row gap-2" test-id="body-height-controls">
                <button
                    className={`flex-1 text-xs rounded border border-themed px-3 py-2 ${bodyTall ? 'bg-surface text-muted' : 'bg-primary text-white'}`}
                    onClick={() => setBodyTall(false)}
                >
                    Body auto height (`h-auto`)
                </button>
                <button
                    className={`flex-1 text-xs rounded border border-themed px-3 py-2 ${bodyTall ? 'bg-primary text-white' : 'bg-surface text-muted'}`}
                    onClick={() => setBodyTall(true)}
                >
                    Body full height (`h-full`)
                </button>
            </div>
            <span className="text-xs text-muted">
                The peer cards below mirror the `.h-fit` utility and should stay tight to their children instead of stretching to the container height.
            </span>
        </div>

        <div className="p-4 rounded border-themed w-full flex flex-col gap-3" test-id="style-tests-cards">
            <div className="p-3 rounded border border-themed w-full h-fit" test-id="style-tests-card">
                <div className="flex-row justify-between items-center">
                    <span className="font-semibold text-sm">Sample peer card</span>
                    <span className="text-xs text-muted">Height: h-fit</span>
                </div>
                <span className="text-xs mt-1 px-2 py-1 rounded bg-surface text-muted">
                    This block uses the same utility classes as the real peer cards to show how `h-fit` keeps the size tight.
                </span>
            </div>
            <div className="flex-row gap-2" test-id="style-tests-row">
                <button className="flex-1 px-3 py-2 bg-primary text-white rounded text-xs">Probe</button>
                <button className="flex-1 px-3 py-2 bg-surface border border-themed rounded text-xs">Connect</button>
            </div>
        </div>
    </body>;
}
