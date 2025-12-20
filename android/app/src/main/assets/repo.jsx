function Repo({ repos = [] }) {
    const items = Array.isArray(repos) ? repos : [];
    return (
        <div className="p-4" width="match_parent" height="wrap_content">
            <div className="bg-blue-500 p-4" width="match_parent" height="wrap_content">
                <span className="text-white lg">Repos</span>
            </div>
            <div className="p-4" width="match_parent" height="wrap_content">
                {items.length === 0 ? (
                    <span className="text-gray-800" width="wrap_content" height="wrap_content">No repos detected</span>
                ) : (
                    items.map((r, i) => (
                        <div className="p-2" width="match_parent" height="wrap_content" key={i}>
                            <span className="text-gray-800" width="wrap_content" height="wrap_content">{r.name} - {r.url}</span>
                            <button className="bg-blue-500 text-white p-2 m-2" width="wrap_content" height="wrap_content" onClick={() => console.log('Open repo:', r.url)}>
                                Open
                            </button>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}

module.exports = Repo;
