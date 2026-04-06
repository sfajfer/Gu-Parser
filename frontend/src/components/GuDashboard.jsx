import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';

const GuDashboard = () => {
  const [guList, setGuList] = useState([]);
  const [search, setSearch] = useState('');
  const [sortConfig, setSortConfig] = useState({ key: 'name', direction: 'ascending' });
  const [expandedId, setExpandedId] = useState(null);

  useEffect(() => {
    // Fetching from the search endpoint
    axios.get('http://localhost:8081/api/gu/search')
      .then(res => {
        // Double check in console that this isn't empty!
        console.log("Fetched Data:", res.data);
        setGuList(Array.isArray(res.data) ? res.data : []);
      })
      .catch(err => console.error("Connection Error:", err));
  }, []);

  const requestSort = (key) => {
    let direction = 'ascending';
    if (sortConfig.key === key && sortConfig.direction === 'ascending') {
      direction = 'descending';
    }
    setSortConfig({ key, direction });
  };

  const processedGu = useMemo(() => {
    if (!guList) return [];
    
    let filtered = guList.filter(gu => {
      const searchStr = search.toLowerCase();
      return (
        gu.name?.toLowerCase().includes(searchStr) ||
        gu.path?.toLowerCase().includes(searchStr) ||
        gu.type?.toLowerCase().includes(searchStr) ||
        gu.keywords?.some(k => k.toLowerCase().includes(searchStr)) ||
        gu.rank?.join(',').includes(searchStr)
      );
    });

    if (sortConfig.key) {
      filtered.sort((a, b) => {
        let valA = a[sortConfig.key] ?? '';
        let valB = b[sortConfig.key] ?? '';
        
        if (Array.isArray(valA)) valA = valA[0] || 0;
        if (Array.isArray(valB)) valB = valB[0] || 0;

        if (valA < valB) return sortConfig.direction === 'ascending' ? -1 : 1;
        if (valA > valB) return sortConfig.direction === 'ascending' ? 1 : -1;
        return 0;
      });
    }
    return filtered;
  }, [guList, search, sortConfig]);

  return (
    <div className="min-h-screen bg-gray-900 text-gray-100 p-4 md:p-8">
      <div className="max-w-6xl mx-auto">
        <header className="mb-8">
          <h1 className="text-4xl font-black text-emerald-500 tracking-tighter mb-2">GU INDEX</h1>
          <p className="text-gray-400 text-sm">Southern Border Repository • Click rows to expand details</p>
        </header>
        
        <input
          type="text"
          placeholder="Search names, paths, or keywords..."
          className="w-full p-4 mb-6 bg-gray-800 border border-gray-700 rounded-xl focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
          onChange={(e) => setSearch(e.target.value)}
        />

        <div className="bg-gray-800 rounded-2xl border border-gray-700 overflow-hidden shadow-2xl">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-900/50 text-gray-400 text-xs uppercase tracking-widest border-b border-gray-700">
                {['Name', 'Path', 'Rank', 'Type', 'Cost', 'Range', 'Health'].map(h => (
                  <th 
                    key={h}
                    className="p-4 cursor-pointer hover:text-emerald-400 transition"
                    onClick={() => requestSort(h.toLowerCase())}
                  >
                    {h} {sortConfig.key === h.toLowerCase() ? (sortConfig.direction === 'ascending' ? '↑' : '↓') : ''}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/50">
              {processedGu.map(gu => (
                <React.Fragment key={gu.id || gu.name}>
                  {/* MAIN THIN ROW */}
                  <tr 
                    onClick={() => setExpandedId(expandedId === gu.id ? null : gu.id)}
                    className="hover:bg-emerald-500/5 cursor-pointer transition-colors group"
                  >
                    <td className="p-4 font-bold text-emerald-400 group-hover:text-emerald-300">{gu.name}</td>
                    <td className="p-4 text-sm text-gray-400">{gu.path}</td>
                    <td className="p-4 text-sm">
                        {gu.rank?.length > 1 ? `${gu.rank[0]}-${gu.rank[gu.rank.length-1]}` : gu.rank?.[0]}
                    </td>
                    <td className="p-4 text-sm">{gu.type}</td>
                    <td className="p-4 text-sm">{gu.cost}</td>
                    <td className="p-4 text-sm">{gu.range}</td>
                    <td className="p-4 text-sm">{gu.health}</td>
                  </tr>

                  {/* EXPANDED CONTENT ROW */}
                  {expandedId === gu.id && (
                    <tr className="bg-gray-900/40 border-l-4 border-emerald-500 animate-in fade-in slide-in-from-top-1 duration-200">
                      <td colSpan="7" className="p-6">
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                          {/* Effect & Lore */}
                          <div className="space-y-4">
                            <div>
                              <h4 className="text-[10px] font-black text-emerald-500 uppercase mb-2">Primary Effect</h4>
                              <p className="text-gray-200 leading-relaxed text-sm whitespace-pre-wrap bg-gray-800/50 p-4 rounded-lg border border-gray-700">
                                {gu.effect || "No effect description provided."}
                              </p>
                            </div>
                            <div className="flex gap-4">
                              <div className="bg-gray-800 p-2 rounded px-3 border border-gray-700">
                                <span className="text-[10px] text-gray-500 block uppercase">Food</span>
                                <span className="text-xs">{gu.food || "Unknown"}</span>
                              </div>
                              <div className="flex-1 bg-gray-800 p-2 rounded px-3 border border-gray-700">
                                <span className="text-[10px] text-gray-500 block uppercase">Keywords</span>
                                <div className="flex flex-wrap gap-1 mt-1">
                                    {gu.keywords?.map(k => (
                                        <span key={k} className="bg-emerald-500/10 text-emerald-400 text-[10px] px-1.5 py-0.5 rounded border border-emerald-500/20">{k}</span>
                                    ))}
                                </div>
                              </div>
                            </div>
                          </div>

                          {/* Steed Stats Display */}
                          {gu.steed && (
                            <div className="bg-gray-900 p-5 rounded-xl border border-emerald-500/20 shadow-inner">
                              <div className="flex justify-between items-center border-b border-gray-800 pb-3 mb-4">
                                <span className="text-xs font-bold text-gray-400 uppercase tracking-tighter">Steed Statblock</span>
                                <span className="text-xs bg-emerald-600 px-2 py-0.5 rounded text-white font-bold">CR {gu.steed.cr}</span>
                              </div>
                              <div className="grid grid-cols-2 gap-6 text-xs">
                                <ul className="space-y-1">
                                    {gu.steed.attributes && Object.entries(gu.steed.attributes).map(([k,v]) => (
                                        <li key={k} className="flex justify-between text-gray-400 border-b border-gray-800/50 pb-1">
                                            <span>{k}</span> <span className="text-emerald-400 font-mono font-bold">{v}</span>
                                        </li>
                                    ))}
                                </ul>
                                <ul className="space-y-1">
                                    {gu.steed.skills && Object.entries(gu.steed.skills).map(([k,v]) => (
                                        <li key={k} className="flex justify-between text-gray-400 border-b border-gray-800/50 pb-1">
                                            <span className="truncate pr-2">{k}</span> <span className="text-emerald-400 font-mono font-bold">{v}</span>
                                        </li>
                                    ))}
                                </ul>
                              </div>
                              {gu.steed.combatActions && (
                                <div className="mt-4 pt-3 border-t border-gray-800">
                                    <span className="text-[10px] text-gray-500 block uppercase mb-1">Combat Actions</span>
                                    <p className="text-[11px] text-gray-400 italic leading-snug">{gu.steed.combatActions}</p>
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default GuDashboard;