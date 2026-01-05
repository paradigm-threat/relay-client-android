import { useState, useEffect } from 'react';

export default function Home() {
    const appVersion = (globalThis.__appVersion) || '1.0.0';
    const [newPeer, setNewPeer] = useState('');
    const [peers, setPeers] = useState([
        { host: 'https://node-dfw1.relaynet.online', note: 'Default peer (DFW1)', status: 'Not probed' },
        { host: 'https://node-dfw2.relaynet.online', note: 'Default peer (DFW2)', status: 'Not probed' }
    ]);

    useEffect(() => {
        const saved = localStorage.getItem('custom_peers');
        if (saved) {
            try {
                const custom = JSON.parse(saved);
                setPeers(prev => [...prev, ...custom.map(p => ({ ...p, status: 'Not probed' }))]);
            } catch (e) {
                console.error('Failed to parse custom peers', e);
            }
        }
    }, []);

    const addPeer = () => {
        if (!newPeer) return;
        const peer = { host: newPeer, note: 'Custom peer', status: 'Not probed' };
        const updatedPeers = [...peers, peer];
        setPeers(updatedPeers);

        const custom = updatedPeers.filter(p => p.note === 'Custom peer');
        localStorage.setItem('custom_peers', JSON.stringify(custom));
        setNewPeer('');
    };

    const probePeer = async (host) => {
        setPeers(prev => prev.map(p => p.host === host ? { ...p, status: 'Probing...' } : p));

        let httpStatus = '...';
        let optionsStatus = '...';

        try {
            const httpResp = await fetch(host, { method: 'HEAD' });
            httpStatus = httpResp.ok ? 'OK' : `Err ${httpResp.status}`;
        } catch (e) {
            httpStatus = 'Fail';
        }

        try {
            const optionsResp = await fetch(host, { method: 'OPTIONS' });
            optionsStatus = optionsResp.ok ? 'OK' : `Err ${optionsResp.status}`;
        } catch (e) {
            optionsStatus = 'Fail';
        }

        setPeers(prev => prev.map(p => p.host === host ? {
            ...p,
            status: `HTTP: ${httpStatus} | OPTIONS: ${optionsStatus}`
        } : p));
    };

    const openRepo = (host) => {
        if (globalThis.postMessage) {
            globalThis.postMessage({ type: 'OPEN_REPO', url: host });
        }
    };

    return <body className="w-full h-full p-4 gap-4 border-2 border-red-500" test-id="home-body">
        <div className="bg-primary p-4 w-full" test-id="home-header">
            <h1 className="text-white">Relay Peers (h-full test)</h1>
        </div>

        <div className="p-4 rounded border-themed w-full" test-id="add-peer-section">
            <h2 className="mb-2">Add Custom Peer</h2>
            <div className="flex-row w-full gap-2" test-id="add-peer-form">
                <input
                    className="p-2 border-themed rounded flex-1 bg-surface"
                    placeholder="https://my-node.com"
                    value={newPeer}
                    onChange={(e) => setNewPeer(e.value)}
                    test-id="new-peer-input"
                />
                <button className="p-2 bg-primary text-white rounded" onClick={addPeer} test-id="add-peer-button">Add</button>
            </div>
        </div>

        <div className="p-4 rounded border-themed w-full border-2 border-green-500 flex-1" test-id="peers-list-section">
            <div className="mb-2 w-full flex flex-row justify-between" test-id="peers-list-header">
                <h2 className="mb-2">Peers (flex-1)</h2>
            </div>
            <div className="gap-2 border border-yellow-500" test-id="peers-map">
                {peers.map(peer => (
                    <div key={peer.host} className="p-3 rounded border-themed w-full h-fit bg-surface" test-id={`peer-card-${peer.host}`}>
                        <div className="flex-row justify-between items-center" test-id="peer-header">
                            <div className="flex-1">
                                <span className="font-semibold" test-id="peer-host">{peer.host}</span>
                                <span className="text-muted" test-id="peer-note">{peer.note}</span>
                            </div>
                        </div>
                        <div className="flex-row gap-2 mt-2" test-id="peer-actions">
                            <button className="p-1 px-2 bg-surface border-themed rounded text-xs" onClick={() => probePeer(peer.host)} test-id="probe-button">Probe</button>
                            <button className="p-1 px-2 bg-primary text-white rounded text-xs" onClick={() => openRepo(peer.host)} test-id="connect-button">Connect</button>
                        </div>
                        <span className="text-xs mt-1 px-2 py-1 rounded bg-surface text-muted" test-id="peer-status">
                            Status: {peer.status}
                        </span>
                    </div>
                ))}
            </div>
        </div>

        <div className="h-64 w-full bg-gray-200 flex items-center justify-center">
            <span>Spacer to test scrolling</span>
        </div>

        <div className="p-2 bg-opacity-75 rounded-tl w-full h-fit border border-blue-500" test-id="version-footer">
            <span className="text-xs" test-id="version-text">v{appVersion}</span>
        </div>
    </body>;
}
