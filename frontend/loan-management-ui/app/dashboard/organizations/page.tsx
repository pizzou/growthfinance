'use client';
import { useEffect, useState } from 'react';
import { get, post, put, del } from '../../../services/api';
import { toast } from '../../../hooks/useToast';
import { hasRole } from '../../../services/authService';
import { PageSpinner } from '../../../components/ui/Skeleton';

interface Org {
  id: number;
  name: string;
  industry?: string;
}

function inputCls(err?: string) {
  return 'w-full border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 ' +
    (err ? 'border-red-400 focus:ring-red-400 bg-red-50' : 'border-gray-300 focus:ring-blue-500');
}

export default function OrganizationsPage() {
  const isAdmin = hasRole('ADMIN');
  const [orgs,       setOrgs]       = useState<Org[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [editOrg,    setEditOrg]    = useState<Org | null>(null);
  const [delId,      setDelId]      = useState<number | null>(null);

  const [name,     setName]     = useState('');
  const [industry, setIndustry] = useState('');
  const [saving,   setSaving]   = useState(false);
  const [errors,   setErrors]   = useState<Record<string, string>>({});

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const load = () => {
    setLoading(true);
    get('/organizations')
      .then(d => setOrgs(d as Org[]))
      .catch(console.error)
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const validate = () => {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = 'Organization name is required';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      await post('/organizations', { name: name.trim(), industry: industry.trim() || undefined });
      toast('success', name + ' created');
      setName(''); setIndustry(''); setShowCreate(false);
      load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setSaving(false); }
  };

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editOrg || !name.trim()) { setErrors({ name: 'Name is required' }); return; }
    setSaving(true);
    try {
      await put('/organizations/' + editOrg.id,
        { name: name.trim(), industry: industry.trim() || undefined });
      toast('success', 'Organization updated');
      setEditOrg(null); setName(''); setIndustry('');
      load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    if (!delId) return;
    try {
      await del('/organizations/' + delId);
      toast('success', 'Organization deleted');
      setDelId(null);
      load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    }
  };

  const openEdit = (org: Org) => {
    setEditOrg(org);
    setName(org.name);
    setIndustry(org.industry ?? '');
    setErrors({});
  };

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Organizations</h1>
          <p className="text-sm text-gray-500">
            {orgs.length} organization{orgs.length !== 1 ? 's' : ''}
          </p>
        </div>
        {isAdmin && (
          <button
            onClick={() => { setShowCreate(true); setName(''); setIndustry(''); setErrors({}); }}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition"
          >
            + New Organization
          </button>
        )}
      </div>

      {loading ? <PageSpinner /> : (
        <div className="grid gap-4">
          {orgs.length === 0 && (
            <div className="bg-white rounded-xl border border-gray-200 p-10 text-center">
              <p className="text-gray-400 text-sm">No organizations found</p>
            </div>
          )}
          {orgs.map(org => (
            <div key={org.id}
              className="bg-white rounded-xl border border-gray-200 p-5 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-blue-100 text-blue-700 flex items-center justify-center text-lg font-bold flex-shrink-0">
                  {org.name[0].toUpperCase()}
                </div>
                <div>
                  <p className="font-semibold text-gray-800">{org.name}</p>
                  <p className="text-sm text-gray-500">{org.industry ?? 'No industry set'}</p>
                  <p className="text-xs text-gray-400 mt-0.5">ID: #{org.id}</p>
                </div>
              </div>
              {isAdmin && (
                <div className="flex items-center gap-2">
                  <button onClick={() => openEdit(org)}
                    className="text-xs text-blue-600 hover:text-blue-800 font-medium border border-blue-200 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition">
                    Edit
                  </button>
                  <button onClick={() => setDelId(org.id)}
                    className="text-xs text-red-500 hover:text-red-700 font-medium border border-red-200 bg-red-50 hover:bg-red-100 px-3 py-1.5 rounded-lg transition">
                    Delete
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {(showCreate || editOrg) && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-800">
                {editOrg ? 'Edit Organization' : 'New Organization'}
              </h2>
              <button onClick={() => { setShowCreate(false); setEditOrg(null); }}
                className="text-gray-400 hover:text-gray-600 text-xl leading-none">x</button>
            </div>
            <form onSubmit={editOrg ? handleEdit : handleCreate} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Organization Name *
                </label>
                <input value={name} onChange={e => setName(e.target.value)}
                  placeholder="e.g. Kigali Finance Ltd" className={inputCls(errors.name)} />
                {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Industry</label>
                <input value={industry} onChange={e => setIndustry(e.target.value)}
                  placeholder="e.g. Microfinance, Banking, Fintech" className={inputCls()} />
              </div>
              <div className="flex gap-3 pt-2">
                <button type="submit" disabled={saving}
                  className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white py-2.5 rounded-lg text-sm font-medium transition">
                  {saving ? 'Saving...' : (editOrg ? 'Save Changes' : 'Create')}
                </button>
                <button type="button" onClick={() => { setShowCreate(false); setEditOrg(null); }}
                  className="flex-1 border border-gray-300 hover:bg-gray-50 text-gray-700 py-2.5 rounded-lg text-sm font-medium transition">
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {delId !== null && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl p-6 text-center">
            <div className="w-14 h-14 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-7 h-7 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h2 className="text-lg font-semibold text-gray-800 mb-2">Delete Organization?</h2>
            <p className="text-sm text-gray-500 mb-6">
              All users, borrowers and loans in this organization will be affected.
            </p>
            <div className="flex gap-3">
              <button onClick={handleDelete}
                className="flex-1 bg-red-600 hover:bg-red-700 text-white py-2.5 rounded-lg text-sm font-medium transition">
                Delete
              </button>
              <button onClick={() => setDelId(null)}
                className="flex-1 border border-gray-300 hover:bg-gray-50 text-gray-700 py-2.5 rounded-lg text-sm font-medium transition">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}