import React, { useState } from 'react';
import { ArrowUpCircle, X, Download, Loader2, Check } from 'lucide-react';
import { api } from '../services/api';

export default function UpdateNotification({ updateInfo, onClose }) {
  const [isUpdating, setIsUpdating] = useState(false);
  const [updateStarted, setUpdateStarted] = useState(false);

  if (!updateInfo || !updateInfo.updateAvailable) return null;

  const handleApplyUpdate = async () => {
    setIsUpdating(true);
    try {
      await api.applyUpdate(updateInfo.downloadUrl, updateInfo.sha256);
      setUpdateStarted(true);
    } catch (err) {
      alert('Failed to trigger update: ' + err.message);
      setIsUpdating(false);
    }
  };

  return (
    <div className="fixed top-3 sm:top-4 right-3 sm:right-4 left-3 sm:left-auto sm:max-w-sm z-50 bg-slate-900 border border-emerald-500/40 rounded-2xl shadow-2xl p-4 text-slate-100 animate-slide-in">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 text-emerald-400">
          <ArrowUpCircle size={20} />
          <h4 className="font-semibold text-xs">New Update Available!</h4>
        </div>
        <button
          onClick={onClose}
          className="text-slate-500 hover:text-slate-300 transition-colors"
        >
          <X size={16} />
        </button>
      </div>

      <p className="text-xs text-slate-300 mb-2">
        Version <span className="font-semibold text-emerald-400">v{updateInfo.latestVersion}</span> is ready to install (current: v{updateInfo.currentVersion}).
      </p>

      {updateInfo.releaseNotes && (
        <div className="text-[11px] text-slate-400 bg-slate-950/60 p-2 rounded-lg max-h-24 overflow-y-auto mb-3 whitespace-pre-wrap border border-slate-800">
          {updateInfo.releaseNotes}
        </div>
      )}

      {updateStarted ? (
        <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-500/10 p-2 rounded-xl border border-emerald-500/30">
          <Loader2 size={14} className="animate-spin" />
          <span>Downloading and applying update. FlickLAN will restart automatically...</span>
        </div>
      ) : (
        <button
          onClick={handleApplyUpdate}
          disabled={isUpdating || !updateInfo.downloadUrl}
          className="w-full py-2 px-3 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-semibold flex items-center justify-center gap-2 shadow-lg shadow-emerald-600/30 transition-all disabled:opacity-50"
        >
          {isUpdating ? (
            <>
              <Loader2 size={14} className="animate-spin" />
              Starting Update...
            </>
          ) : (
            <>
              <Download size={14} />
              Update and Restart Now
            </>
          )}
        </button>
      )}
    </div>
  );
}

