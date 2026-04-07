import React, { useState, useEffect, useMemo } from 'react';
import Markdown from 'react-markdown'
import axios from 'axios';
import guData from '../assets/gu-index.json';
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

const KEYWORD_FILTERS = [
  { key: 'Deductive',      label: 'Deductive',      match: kws => kws.some(k => k.toLowerCase() === 'deductive') },
  { key: 'Dao',            label: 'Dao',             match: kws => kws.some(k => k.toLowerCase() === 'dao') },
  { key: 'Expendable',     label: 'Expendable',      match: kws => kws.some(k => k.toLowerCase().startsWith('expendable')) },
  { key: 'Extinct',        label: 'Extinct',         match: kws => kws.some(k => k.toLowerCase() === 'extinct') },
  { key: 'Fast',           label: 'Fast',            match: kws => kws.some(k => k.toLowerCase() === 'fast') },
  { key: 'Ingredient',     label: 'Ingredient',      match: kws => kws.some(k => k.toLowerCase() === 'ingredient') },
  { key: 'Investigative',  label: 'Investigative',   match: kws => kws.some(k => k.toLowerCase().startsWith('investigative')) },
  { key: 'Low Vitality',   label: 'Low Vitality',    match: kws => kws.some(k => k.toLowerCase() === 'low vitality') },
  { key: 'Piercing',       label: 'Piercing',        match: kws => kws.some(k => k.toLowerCase() === 'piercing') },
  { key: 'Shield',         label: 'Shield',          match: kws => kws.some(k => k.toLowerCase() === 'shield') },
  { key: 'Size',           label: 'Size',            match: kws => kws.some(k => ['small','medium','large','huge'].includes(k.toLowerCase())) },
  { key: 'Steed',          label: 'Steed',           match: kws => kws.some(k => k.toLowerCase() === 'steed') },
  { key: 'Supplementary',  label: 'Supplementary',   match: kws => kws.some(k => k.toLowerCase().startsWith('supplement')) },
  { key: 'Sustained',      label: 'Sustained',       match: kws => kws.some(k => k.toLowerCase() === 'sustained') },
  { key: 'Transformation', label: 'Transformation',  match: kws => kws.some(k => k.toLowerCase() === 'transformation') },
  { key: 'Undodgeable',    label: 'Undodgeable',     match: kws => kws.some(k => k.toLowerCase() === 'undodgeable') },
  { key: 'Unreactable',    label: 'Unreactable',     match: kws => kws.some(k => k.toLowerCase() === 'unreactable') },
  { key: 'Unrefinable',    label: 'Unrefinable',     match: kws => kws.some(k => k.toLowerCase() === 'unrefinable') },
  { key: 'Vital',          label: 'Vital',           match: kws => kws.some(k => k.toLowerCase() === 'vital') },
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

// Extracts the first number found in a string, else returns the original string
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

const KeywordCheckboxes = ({ selected, onToggle }) => (
  <div className="gu-filter-group">
    <span className="gu-filter-label">Keywords</span>
    <div className="keyword-checkbox-grid">
      {KEYWORD_FILTERS.map(({ key, label }) => {
        const checked = selected.has(key);
        return (
          <label
            key={key}
            className={`keyword-checkbox-label${checked ? ' checked' : ''}`}
          >
            <input
              type="checkbox"
              checked={checked}
              onChange={() => onToggle(key)}
            />
            {" " + label}
          </label>
        );
      })}
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
  const [guList,         setGuList]         = useState(guData || []);
  const [search,         setSearch]         = useState('');
  const [sortConfig,     setSortConfig]     = useState({ key: 'name', direction: 'ascending' });
  const [expandedId,     setExpandedId]     = useState(null);
  const [sidebarOpen,    setSidebarOpen]    = useState(false);
  const [screenWidth,    setScreenWidth]    = useState(window.innerWidth);
  const [filterPath,     setFilterPath]     = useState('');
  const [filterRank,     setFilterRank]     = useState('');
  const [filterType,     setFilterType]     = useState('');
  const [filterKeywords, setFilterKeywords] = useState(new Set());

  useEffect(() => {
    const handleResize = () => setScreenWidth(window.innerWidth);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    axios.get('https://gu-index-b9jp.onrender.com/api/gu/search')
      .then(res => {
        console.log('Fetched:', res.data);
        // Only update if we get more entries than the local file; this will only happen when new gu are added and the json isnt updated yet
        if (res.data.length > guList.length) setGuList(Array.isArray(res.data) ? res.data : []);
        console.log("Fetched data: ", res.data, "Local data: ", guList);
      })
      .catch(err => console.error('Fetch error:', err));
  }, []);

  const toggleKeyword = (key) => {
    setFilterKeywords(prev => {
      const next = new Set(prev);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  };

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
    setFilterKeywords(new Set());
  };

  const activeFilterCount =
    [filterPath, filterRank, filterType].filter(Boolean).length + filterKeywords.size;

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

      // ALL selected keywords must be present on the gu
      const matchKeywords = filterKeywords.size === 0 || (
        gu.keywords && [...filterKeywords].every(key => {
          const filter = KEYWORD_FILTERS.find(f => f.key === key);
          return filter?.match(gu.keywords);
        })
      );

      return matchSearch && matchPath && matchRank && matchType && matchKeywords;
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
  }, [guList, search, sortConfig, filterPath, filterRank, filterType, filterKeywords]);

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
          placeholder="Search..."
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

          <KeywordCheckboxes
            selected={filterKeywords}
            onToggle={toggleKeyword}
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
                  {screenWidth >= 718 && (
                    <SortTh label="Type" sortKey="type" sortConfig={sortConfig} onSort={requestSort} className="col-type" />
                  )}
                  {screenWidth >= 768 && (
                    <>
                      <SortTh label="Cost"   sortKey="cost"   sortConfig={sortConfig} onSort={requestSort} className="col-cost" />
                      <SortTh label="Range"  sortKey="range"  sortConfig={sortConfig} onSort={requestSort} className="col-range" />
                      <SortTh label="Health" sortKey="health" sortConfig={sortConfig} onSort={requestSort} className="col-health" />
                    </>
                  )}
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
                      {screenWidth >= 710 && (
                        <td><span className="type-badge">{gu.type}</span></td>
                      )}
                      {screenWidth >= 768 && (
                        <>
                          <td className="cell-cost col-cost">{gu.cost}</td>
                          <td className="cell-range col-range">{gu.range}</td>
                          <td className="cell-health col-health">{gu.health}</td>
                        </>
                      )}
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
                                  <br />
                                  <span className="type-badge">{gu.type}</span>
                                </div>

                                <div className="expand-section-title">Effect</div>
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
                                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{k}</span>
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