import React, { useMemo, useState } from 'react'

interface Peer {
    host: string
    note: string
    status: 'online' | 'offline' | 'unknown'
    latencyMs?: number
}

const DEFAULT_PEERS: Peer[] = [
    { host: 'https://node-dfw1.relaynet.online', note: 'Default peer (DFW1)', status: 'online', latencyMs: 114 },
    { host: 'https://node-dfw2.relaynet.online', note: 'Default peer (DFW2)', status: 'online', latencyMs: 132 },
    { host: 'http://localhost:8080', note: 'Local dev peer', status: 'online', latencyMs: 51 },
]

function StatusPill({ status, latencyMs }: { status: Peer['status']; latencyMs?: number }) {
    if (status === 'online') {
        return <span className="text-xs px-2 py-1 rounded bg-green-900 text-green-100 font-semibold" width="wrap_content" height="wrap_content">
            Online {latencyMs ? `(${latencyMs}ms)` : ''}
        </span>
    }
    if (status === 'offline') {
        return <span className="text-xs px-2 py-1 rounded bg-red-900 text-red-100 font-semibold" width="wrap_content" height="wrap_content">
            Offline
        </span>
    }
    return <span className="text-xs px-2 py-1 rounded bg-gray-800 text-gray-200 font-semibold" width="wrap_content" height="wrap_content">
        Not probed
    </span>
}

function PeerCard({ peer, onRemove }: { peer: Peer; onRemove: (host: string) => void }) {
    return <div className="p-4 mb-3 rounded bg-gray-900 border border-gray-800" width="match_parent" height="wrap_content">
        <div className="mb-2" width="match_parent" height="wrap_content">
            <span className="text-lg font-semibold text-white" width="wrap_content" height="wrap_content">{peer.host}</span>
        </div>
        <div className="mb-2" width="match_parent" height="wrap_content">
            <span className="text-sm text-gray-300" width="wrap_content" height="wrap_content">{peer.note}</span>
        </div>
        <div className="mb-2" width="match_parent" height="wrap_content">
            <StatusPill status={peer.status} latencyMs={peer.latencyMs} />
        </div>
        <div className="flex items-center" width="match_parent" height="wrap_content">
            <button className="mr-2 px-3 py-2 bg-blue-500 text-white rounded" width="wrap_content" height="wrap_content" onClick={() => console.log('Open', peer.host)}>
                Open →
            </button>
            <button className="px-3 py-2 bg-red-600 text-white rounded" width="wrap_content" height="wrap_content" onClick={() => onRemove(peer.host)}>
                ✕
            </button>
        </div>
    </div>
}

export default function PeersView() {
    const [peers, setPeers] = useState<Peer[]>(DEFAULT_PEERS)

    const peersToRender = useMemo(() => peers, [peers])

    const removePeer = (host: string) => {
        setPeers((current) => current.filter((p) => p.host !== host))
    }

    return <div className="p-4 bg-gray-950" width="match_parent" height="match_parent">
        <div className="mb-3" width="match_parent" height="wrap_content">
            <span className="text-xl font-bold text-white" width="wrap_content" height="wrap_content">Relay</span>
        </div>

        <div className="mb-4 p-3 bg-gray-900 border border-gray-800 rounded" width="match_parent" height="wrap_content">
            <span className="text-gray-400" width="wrap_content" height="wrap_content">https://example.com</span>
        </div>

        <div className="mb-4" width="match_parent" height="wrap_content">
            <button className="px-3 py-2 bg-green-600 text-white rounded" width="wrap_content" height="wrap_content" onClick={() => console.log('Add peer placeholder')}>
                Add
            </button>
        </div>

        <div width="match_parent" height="wrap_content">
            {peersToRender.map((peer) => (
                <PeerCard key={peer.host} peer={peer} onRemove={removePeer} />
            ))}
        </div>
    </div>
}
