export default function Home() {
    const appVersion = (globalThis.__appVersion) || '1.0.0';

    const peers = [
        { host: 'https://node-dfw1.relaynet.online', note: 'Default peer (DFW1)' },
        { host: 'https://node-dfw2.relaynet.online', note: 'Default peer (DFW2)' }
    ];

    return <body>
        <div className="bg-primary p-4 mb-4 w-full">
            <h1 className="text-white">Relay Peers</h1>
        </div>

        <div className="p-4 rounded shadow mb-4 w-full">
            <span>Manage your Relay peers. This screen is simplified to match the working settings view.</span>
        </div>

        <div className="p-4 rounded border-themed w-full">
            <div className="mb-2 w-full">
                <h2 className="mb-2">Default peers</h2>
            </div>
            {peers.map(peer => (
                <div key={peer.host} className="p-3 mb-2 rounded border-themed w-full">
                    <span className="font-semibold">{peer.host}</span>
                    <span className="text-muted">{peer.note}</span>
                    <span className="text-xs mt-1 px-2 py-1 rounded bg-surface text-muted">Status: Not probed</span>
                </div>
            ))}
        </div>

        <div className="absolute bottom-0 right-0 p-2 bg-opacity-75 rounded-tl">
            <span className="text-xs">v{appVersion}</span>
        </div>
    </body>;
}
