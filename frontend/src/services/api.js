const API_BASE = '/api';

export const api = {
  async getNodeInfo() {
    const res = await fetch(`${API_BASE}/info`);
    if (!res.ok) throw new Error('Failed to fetch node info');
    return res.json();
  },

  async getPeers() {
    const res = await fetch(`${API_BASE}/peers`);
    if (!res.ok) throw new Error('Failed to fetch peers');
    return res.json();
  },

  async getChannelHistory(channel, limit = 100) {
    const res = await fetch(`${API_BASE}/messages/channel/${encodeURIComponent(channel)}?limit=${limit}`);
    if (!res.ok) throw new Error('Failed to fetch channel history');
    return res.json();
  },

  async getDirectHistory(peerId, currentUserId, limit = 100) {
    const res = await fetch(`${API_BASE}/messages/direct/${encodeURIComponent(peerId)}?currentUserId=${encodeURIComponent(currentUserId)}&limit=${limit}`);
    if (!res.ok) throw new Error('Failed to fetch direct messages');
    return res.json();
  },

  async deleteDirectHistory(peerId, currentUserId) {
    const res = await fetch(`${API_BASE}/messages/direct/${encodeURIComponent(peerId)}?currentUserId=${encodeURIComponent(currentUserId)}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error('Failed to delete chat history');
    return res.json();
  },

  getPeerExportUrl(peerId, currentUserId) {
    return `${API_BASE}/backup/export/peer/${encodeURIComponent(peerId)}?currentUserId=${encodeURIComponent(currentUserId)}`;
  },

  async uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/files/upload`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) throw new Error('File upload failed');
    return res.json();
  },

  getExportUrl(format = 'json', includeMedia = false) {
    return `${API_BASE}/backup/export?format=${format}&includeMedia=${includeMedia}`;
  },

  async inspectBackup(file) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/backup/inspect`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.details || 'Failed to inspect backup file');
    }
    return res.json();
  },

  async restoreBackup(file, mode = 'MERGE') {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/backup/restore?mode=${mode}`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.details || 'Failed to restore backup');
    }
    return res.json();
  },

  async checkUpdate() {
    const res = await fetch(`${API_BASE}/update/check`);
    if (!res.ok) throw new Error('Update check failed');
    return res.json();
  },

  async applyUpdate(downloadUrl, sha256) {
    const res = await fetch(`${API_BASE}/update/apply`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ downloadUrl, sha256 }),
    });
    if (!res.ok) throw new Error('Failed to trigger update');
    return res.json();
  },
};

