export default function Repo({ repos = [] }) {
    const items = Array.isArray(repos) ? repos : [];
    return (
        <body>
            <div className="bg-primary p-4 w-full">
                <h1 className="text-white">Repos</h1>
            </div>
            <div className="p-4 w-full">
                {items.length === 0 ? (
                    <span>No repos detected</span>
                ) : (
                    items.map((r, i) => (
                        <div className="p-2 w-full" key={i}>
                            <span>{r.name} - {r.url}</span>
                            <button className="bg-primary text-white p-2 m-2" onClick={() => console.log('Open repo:', r.url)}>
                                Open
                            </button>
                        </div>
                    ))
                )}
            </div>
        </body>
    );
}
