import React, { useState, useEffect, useMemo } from 'react';
import Markdown from 'react-markdown'
import axios from 'axios';
import './GuDashboard.css';

const PATHS = [
  'Blood Path','Dark Path','Earth Path','Enslavement Path','Fire Path',
  'Food Path','Human Path','Ice Path','Information Path','Light Path',
  'Lightning Path','Luck Path','Metal Path','Poison Path','Refinement Path',
  'Rule Path','Soul Path','Sound Path','Space Path','Strength Path',
  'Sword Path','Theft Path','Time Path','Transformation Path','Water Path',
  'Wind Path','Wisdom Path','Wood Path',
];

const RANKS = [1, 2, 3, 4, 5];

const TYPES = [
  'Attack','Manifestation','Guard','Celerity','Divination',
  'Concealment','Tonic','Container','Catalyst','Carver',
];

const rangeToMeters = (rangeStr) => {
  if (!rangeStr) return -1;
  const s = String(rangeStr).toLowerCase().trim();
  const num = parseFloat(s);
  if (isNaN(num)) return -1;
  if (s.includes('kilometer') || s.includes(' km')) return num * 1000;
  if (s.includes('mile'))      return num * 1609.34;
  if (s.includes('foot') || s.includes('feet') || s.includes('ft')) return num * 0.3048;
  if (s.includes('meter'))     return num;
  return num;
};

// NEW HELPER: Extracts the first number found in a string, else returns the original string
const extractNumber = (val) => {
  if (val === null || val === undefined) return '';
  const s = String(val);
  const match = s.match(/-?\d+(\.\d+)?/);
  return match ? parseFloat(match[0]) : s;
};

const FilterDropdown = ({ label, value, onChange, options, placeholder }) => (
  <div className="gu-filter-group">
    <span className="gu-filter-label">{label}</span>
    <div className="gu-select-wrap">
      <select
        className={`gu-select${value ? ' has-value' : ''}`}
        value={value}
        onChange={e => onChange(e.target.value)}
      >
        <option value="">{placeholder}</option>
        {options.map(opt => (
          <option key={opt.value ?? opt} value={opt.value ?? opt}>
            {opt.label ?? opt}
          </option>
        ))}
      </select>
    </div>
  </div>
);

const SortTh = ({ label, sortKey, sortConfig, onSort, className }) => {
  const active = sortConfig.key === sortKey;
  return (
    <th
      className={`${active ? 'sort-active' : ''} ${className ?? ''}`}
      onClick={() => onSort(sortKey)}
    >
      {label}
      <span className="sort-arrow">
        {active ? (sortConfig.direction === 'ascending' ? '↑' : '↓') : ''}
      </span>
    </th>
  );
};

const GuDashboard = () => {
  const [guList,      setGuList]      = useState([]);
  const [search,      setSearch]      = useState('');
  const [sortConfig,  setSortConfig]  = useState({ key: 'name', direction: 'ascending' });
  const [expandedId,  setExpandedId]  = useState(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [filterPath, setFilterPath] = useState('');
  const [filterRank, setFilterRank] = useState('');
  const [filterType, setFilterType] = useState('');

  useEffect(() => {
    axios.get('https://gu-index-b9jp.onrender.com/api/gu/search')
      .then(res => {
        console.log('Fetched:', res.data);
        setGuList(Array.isArray(res.data) ? res.data : []);
      })
      .catch(err => console.error('Fetch error:', err));
  }, []);

  const requestSort = (key) => {
    setSortConfig(prev => ({
      key,
      direction: prev.key === key && prev.direction === 'ascending' ? 'descending' : 'ascending',
    }));
  };

  const clearAll = () => {
    setFilterPath('');
    setFilterRank('');
    setFilterType('');
    setSearch('');
  };

  const activeFilterCount = [filterPath, filterRank, filterType].filter(Boolean).length;

  const processedGu = useMemo(() => {
    if (!guList.length) return [];

    let out = guList.filter(gu => {
      const q = search.toLowerCase();
      const matchSearch = !q || (
        gu.name?.toLowerCase().includes(q) ||
        gu.path?.toLowerCase().includes(q) ||
        gu.type?.toLowerCase().includes(q) ||
        gu.keywords?.some(k => k.toLowerCase().includes(q))
      );
      const matchPath = !filterPath || gu.path === filterPath;
      const matchRank = !filterRank ||
        (gu.rank && gu.rank.some(r => Number(r) === Number(filterRank)));
      const matchType = !filterType || gu.type === filterType;
      return matchSearch && matchPath && matchRank && matchType;
    });

    if (sortConfig.key) {
      out = [...out].sort((a, b) => {
        const dir = sortConfig.direction === 'ascending' ? 1 : -1;
        const key = sortConfig.key;

        // --- RANK SORTING ---
        if (key === 'rank') {
          const aMin = Number(a.rank?.[0] ?? 0);
          const bMin = Number(b.rank?.[0] ?? 0);
          if (aMin !== bMin) return (aMin - bMin) * dir;
          
          // If starting rank is the same, sort by the end of the range
          const aMax = Number(a.rank?.[a.rank.length - 1] ?? 0);
          const bMax = Number(b.rank?.[b.rank.length - 1] ?? 0);
          return (aMax - bMax) * dir;
        }

        // --- RANGE SORTING ---
        if (key === 'range') {
          const rA = rangeToMeters(a.range);
          const rB = rangeToMeters(b.range);
          
          if (rA !== -1 && rB !== -1) return (rA - rB) * dir;
          
          if (rA !== -1) return -1 * dir; 
          if (rB !== -1) return 1 * dir;
          
          const strA = String(a.range || '').toLowerCase();
          const strB = String(b.range || '').toLowerCase();
          if (strA < strB) return -1 * dir;
          if (strA > strB) return 1 * dir;
          return 0;
        }

        // --- COST & HEALTH SORTING ---
        if (key === 'cost' || key === 'health') {
          const valA = extractNumber(a[key]);
          const valB = extractNumber(b[key]);

          const isNumA = typeof valA === 'number';
          const isNumB = typeof valB === 'number';

          if (isNumA && isNumB) return (valA - valB) * dir;
          if (isNumA) return -1 * dir;
          if (isNumB) return 1 * dir;

          const strA = String(valA).toLowerCase();
          const strB = String(valB).toLowerCase();
          if (strA < strB) return -1 * dir;
          if (strA > strB) return 1 * dir;
          return 0;
        }

        // --- DEFAULT SORTING ---
        let va = a[key] ?? '';
        let vb = b[key] ?? '';
        if (Array.isArray(va)) va = va[0] ?? 0;
        if (Array.isArray(vb)) vb = vb[0] ?? 0;
        
        if (typeof va === 'string') va = va.toLowerCase();
        if (typeof vb === 'string') vb = vb.toLowerCase();

        if (va < vb) return -1 * dir;
        if (va > vb) return 1 * dir;
        return 0;
      });
    }
    return out;
  }, [guList, search, sortConfig, filterPath, filterRank, filterType]);

  const rankOptions = RANKS.map(r => ({ value: String(r), label: `Rank ${r}` }));
  const pathOptions = PATHS.map(p => ({ value: p, label: p.replace(' Path', '') }));

  return (
    <div className="gu-shell">
      <header className="gu-topbar">
        <div>
          <div className="gu-title">GU INDEX</div>
          <div className="gu-subtitle">click rows to expand</div>
        </div>

        <input
          type="text"
          className="gu-search"
          placeholder="Search names, paths, keywords…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />

        <button
          className="gu-filter-toggle"
          onClick={() => setSidebarOpen(o => !o)}
        >
          ⚙ Filters
          {activeFilterCount > 0 && <span className="badge">{activeFilterCount}</span>}
        </button>

        <div className="gu-count">
          <strong>{processedGu.length}</strong> / {guList.length}
        </div>
      </header>

      <div className="gu-body">
        <aside className={`gu-sidebar${sidebarOpen ? ' open' : ''}`}>
          <div className="gu-sidebar-header">
            <span className="gu-sidebar-title">Filters</span>
            {activeFilterCount > 0 && (
              <button className="gu-clear-btn" onClick={clearAll}>Clear all</button>
            )}
          </div>

          <FilterDropdown
            label="Path"
            value={filterPath}
            onChange={setFilterPath}
            options={pathOptions}
            placeholder="All paths"
          />

          <FilterDropdown
            label="Rank"
            value={filterRank}
            onChange={setFilterRank}
            options={rankOptions}
            placeholder="All ranks"
          />

          <FilterDropdown
            label="Type"
            value={filterType}
            onChange={setFilterType}
            options={TYPES}
            placeholder="All types"
          />
        </aside>

        <main className="gu-main">
          <div className="gu-table-wrap">
            <table className="gu-table">
              <thead>
                <tr>
                  <SortTh label="Name"   sortKey="name"   sortConfig={sortConfig} onSort={requestSort} />
                  <SortTh label="Path"   sortKey="path"   sortConfig={sortConfig} onSort={requestSort} />
                  <SortTh label="Rank"   sortKey="rank"   sortConfig={sortConfig} onSort={requestSort} />
                  <SortTh label="Type"   sortKey="type"   sortConfig={sortConfig} onSort={requestSort} />
                  <SortTh label="Cost"   sortKey="cost"   sortConfig={sortConfig} onSort={requestSort} className="col-cost" />
                  <SortTh label="Range"  sortKey="range"  sortConfig={sortConfig} onSort={requestSort} className="col-range" />
                  <SortTh label="Health" sortKey="health" sortConfig={sortConfig} onSort={requestSort} className="col-health" />
                </tr>
              </thead>
              <tbody>
                {processedGu.length === 0 ? (
                  <tr>
                    <td colSpan="7">
                      <div className="gu-empty">
                        {guList.length === 0 ? 'Loading…' : 'No results match your filters.'}
                      </div>
                    </td>
                  </tr>
                ) : processedGu.map(gu => (
                  <React.Fragment key={gu.id || gu.name}>
                    <tr
                      className="gu-row"
                      onClick={() => setExpandedId(expandedId === gu.id ? null : gu.id)}
                    >
                      <td className="cell-name">{gu.name}</td>
                      <td className="cell-path">{gu.path}</td>
                      <td className="cell-rank">
                        {gu.rank?.length > 1
                          ? `${gu.rank[0]}–${gu.rank[gu.rank.length - 1]}`
                          : gu.rank?.[0]}
                      </td>
                      <td><span className="type-badge">{gu.type}</span></td>
                      <td className="cell-cost col-cost">{gu.cost}</td>
                      <td className="cell-range col-range">{gu.range}</td>
                      <td className="cell-health col-health">{gu.health}</td>
                    </tr>

                    {expandedId === gu.id && (
                      <tr className="gu-expanded-row">
                        <td colSpan="7">
                          <div className="gu-expanded-inner">
                            <div className={`gu-expanded-grid ${!gu.steed ? 'no-steed' : ''}`}>
                              <div>
                                <div className="mobile-stats-row">
                                  {gu.cost && (
                                    <div className="mobile-stat-chip">
                                      <span className="mobile-stat-label">Cost</span>
                                      <span className="mobile-stat-value">{gu.cost}</span>
                                    </div>
                                  )}
                                  {gu.health && (
                                    <div className="mobile-stat-chip">
                                      <span className="mobile-stat-label">Health</span>
                                      <span className="mobile-stat-value">{gu.health}</span>
                                    </div>
                                  )}
                                  {gu.range && (
                                    <div className="mobile-stat-chip">
                                      <span className="mobile-stat-label">Range</span>
                                      <span className="mobile-stat-value">{gu.range}</span>
                                    </div>
                                  )}
                                </div>

                                <div className="expand-section-title">Primary Effect</div>
                                <div className="effect-box">
                                  <Markdown>{gu.effect}</Markdown>
                                </div>
                                <div className="meta-row">
                                  <div className="meta-chip">
                                    <span className="meta-chip-label">Food</span>
                                    <span className="meta-chip-value">{gu.food || 'Unknown'}</span>
                                  </div>
                                  <div className="meta-chip" style={{ flex: 1 }}>
                                    <span className="meta-chip-label">Keywords</span>
                                    <div className="keyword-list">
                                      {gu.keywords?.map(k => (
                                        <span key={k} className="keyword-tag">{k}</span>
                                      ))}
                                    </div>
                                  </div>
                                </div>
                              </div>

                              {gu.steed && (
                                <div className="steed-block">
                                  <div className="steed-header">
                                    <span className="steed-header-label">Steed Statblock</span>
                                    <span className="steed-cr">CR {gu.steed.cr}</span>
                                  </div>
                                  <div className="steed-stats-grid">
                                    <ul className="steed-stat-list">
                                      {gu.steed.attributes && Object.entries(gu.steed.attributes).map(([k, v]) => (
                                        <li key={k}><span>{k}</span><span>{v}</span></li>
                                      ))}
                                    </ul>
                                    <ul className="steed-stat-list">
                                      {gu.steed.skills && Object.entries(gu.steed.skills).map(([k, v]) => (
                                        <li key={k}>
                                          <span style={{ overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{k}</span>
                                          <span>{v}</span>
                                        </li>
                                      ))}
                                    </ul>
                                  </div>
                                  {gu.steed.combatActions && (
                                    <div className="steed-actions">
                                      <div className="steed-actions-label">Combat Actions</div>
                                      <p className="steed-actions-text">{gu.steed.combatActions}</p>
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </main>
      </div>
    </div>
  );
};

export default GuDashboard;