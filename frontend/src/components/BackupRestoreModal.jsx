import React, { useState } from 'react';
import { 
  X, 
  Download, 
  Upload, 
  Database, 
  CheckCircle2, 
  AlertTriangle, 
  FileArchive, 
  FileJson,
  Loader2
} from 'lucide-react';
import { api } from '../services/api';

export default function BackupRestoreModal({ isOpen, onClose, onRestoreComplete }) {
  const [activeTab, setActiveTab] = useState('export'); // 'export' | 'restore'
  const [exportFormat, setExportFormat] = useState('json');
  const [includeMedia, setIncludeMedia] = useState(false);

  // Restore state
  const [selectedFile, setSelectedFile] = useState(null);
  const [inspectData, setInspectData] = useState(null);
  const [isInspecting, setIsInspecting] = useState(false);
  const [restoreMode, setRestoreMode] = useState('MERGE'); // 'MERGE' | 'OVERWRITE'
  const [isRestoring, setIsRestoring] = useState(false);
  const [restoreStatus, setRestoreStatus] = useState(null);

  if (!isOpen) return null;

  const handleExport = () => {
    const url = api.getExportUrl(exportFormat, includeMedia);
    window.open(url, '_blank');
  };

  const handleFileSelect = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setSelectedFile(file);
    setInspectData(null);
    setRestoreStatus(null);
    setIsInspecting(true);

    try {
      const data = await api.inspectBackup(file);
      setInspectData(data);
    } catch (err) {
      alert('Error reading backup: ' + err.message);
      setSelectedFile(null);
    } finally {
      setIsInspecting(false);
    }
  };

  const handleRestore = async () => {
    if (!selectedFile) return;

    if (restoreMode === 'OVERWRITE') {
      const confirmed = window.confirm(
        'WARNING: Overwrite mode will erase your current local messages and replace them with the backup. Continue?'
      );
      if (!confirmed) return;
    }

    setIsRestoring(true);
    setRestoreStatus(null);

    try {
      const result = await api.restoreBackup(selectedFile, restoreMode);
      setRestoreStatus({
        success: true,
        message: `Successfully restored ${result.importedCount} messages!`,
      });
      if (onRestoreComplete) onRestoreComplete();
    } catch (err) {
      setRestoreStatus({
        success: false,
        message: err.message,
      });
    } finally {
      setIsRestoring(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-xs flex items-center justify-center p-3 sm:p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg max-h-[90vh] shadow-2xl overflow-hidden flex flex-col">
        {/* Header */}
        <div className="p-3.5 sm:p-4 border-b border-slate-800 flex items-center justify-between flex-shrink-0">
          <div className="flex items-center gap-2 text-indigo-400">
            <Database size={18} />
            <h3 className="font-semibold text-slate-100 text-xs sm:text-sm">Backup & Restore Chats</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-200 rounded-lg hover:bg-slate-800 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Tab Buttons */}
        <div className="flex border-b border-slate-800 bg-slate-950/40 flex-shrink-0">
          <button
            onClick={() => setActiveTab('export')}
            className={`flex-1 py-2.5 sm:py-3 text-xs font-semibold flex items-center justify-center gap-1.5 sm:gap-2 border-b-2 transition-all ${
              activeTab === 'export'
                ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Download size={14} />
            Export Backup
          </button>
          <button
            onClick={() => setActiveTab('restore')}
            className={`flex-1 py-2.5 sm:py-3 text-xs font-semibold flex items-center justify-center gap-1.5 sm:gap-2 border-b-2 transition-all ${
              activeTab === 'restore'
                ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Upload size={14} />
            Restore Chats
          </button>
        </div>

        {/* Body (Scrollable on small mobile screens) */}
        <div className="p-4 sm:p-6 space-y-4 overflow-y-auto flex-1">
          {activeTab === 'export' ? (
            <div className="space-y-4">
              <p className="text-xs text-slate-400 leading-relaxed">
                Save a local snapshot of your chat history. You can restore this backup on any computer or phone running FlickLAN.
              </p>

              {/* Format selection */}
              <div className="grid grid-cols-2 gap-2.5 sm:gap-3">
                <button
                  type="button"
                  onClick={() => setExportFormat('json')}
                  className={`p-3 rounded-xl border flex flex-col items-center gap-1.5 sm:gap-2 transition-all ${
                    exportFormat === 'json'
                      ? 'border-indigo-500 bg-indigo-500/10 text-white'
                      : 'border-slate-800 bg-slate-950/40 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  <FileJson size={22} className={exportFormat === 'json' ? 'text-indigo-400' : 'text-slate-500'} />
                  <div className="text-center">
                    <p className="text-xs font-semibold">JSON Format</p>
                    <p className="text-[10px] text-slate-500">Text & metadata</p>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => setExportFormat('zip')}
                  className={`p-3 rounded-xl border flex flex-col items-center gap-1.5 sm:gap-2 transition-all ${
                    exportFormat === 'zip'
                      ? 'border-indigo-500 bg-indigo-500/10 text-white'
                      : 'border-slate-800 bg-slate-950/40 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  <FileArchive size={22} className={exportFormat === 'zip' ? 'text-indigo-400' : 'text-slate-500'} />
                  <div className="text-center">
                    <p className="text-xs font-semibold">ZIP Package</p>
                    <p className="text-[10px] text-slate-500">Includes shared files</p>
                  </div>
                </button>
              </div>

              {exportFormat === 'zip' && (
                <label className="flex items-center gap-2 p-2.5 sm:p-3 rounded-xl bg-slate-950/40 border border-slate-800 text-xs text-slate-300 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={includeMedia}
                    onChange={(e) => setIncludeMedia(e.target.checked)}
                    className="rounded bg-slate-800 border-slate-700 text-indigo-600 focus:ring-0"
                  />
                  <span>Include transferred media files in ZIP</span>
                </label>
              )}

              <button
                onClick={handleExport}
                className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 transition-all"
              >
                <Download size={14} />
                Download Backup File
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              {/* File picker */}
              <div className="border-2 border-dashed border-slate-800 hover:border-slate-700 rounded-xl p-4 text-center cursor-pointer bg-slate-950/30 relative">
                <input
                  type="file"
                  accept=".json,.zip"
                  onChange={handleFileSelect}
                  className="absolute inset-0 opacity-0 cursor-pointer"
                />
                <Upload size={22} className="mx-auto text-slate-500 mb-1.5" />
                <p className="text-xs font-medium text-slate-300 break-all px-2">
                  {selectedFile ? selectedFile.name : 'Choose or tap backup file (.json or .zip)'}
                </p>
                <p className="text-[10px] text-slate-500 mt-0.5">FlickLAN verified backups only</p>
              </div>

              {isInspecting && (
                <div className="flex items-center justify-center gap-2 py-2 text-xs text-indigo-400">
                  <Loader2 size={14} className="animate-spin" />
                  <span>Inspecting backup archive...</span>
                </div>
              )}

              {/* Inspection Preview */}
              {inspectData && (
                <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-400">Total Messages:</span>
                    <span className="font-semibold text-indigo-400">{inspectData.totalMessages}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-400">Exported From:</span>
                    <span className="font-medium text-slate-200">{inspectData.exportedBy || 'Unknown'}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-400">Export Date:</span>
                    <span className="text-slate-300">
                      {inspectData.exportDate ? new Date(inspectData.exportDate).toLocaleDateString() : 'N/A'}
                    </span>
                  </div>

                  {/* Mode Selector */}
                  <div className="pt-2 border-t border-slate-800/80">
                    <p className="text-[11px] font-semibold text-slate-400 mb-1.5">Restore Strategy:</p>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setRestoreMode('MERGE')}
                        className={`p-2 rounded-lg text-left border transition-all ${
                          restoreMode === 'MERGE'
                            ? 'border-indigo-500 bg-indigo-500/10 text-white'
                            : 'border-slate-800 text-slate-400 hover:border-slate-700'
                        }`}
                      >
                        <p className="text-xs font-semibold">Merge (Safe)</p>
                        <p className="text-[10px] text-slate-500">Zero duplicates</p>
                      </button>

                      <button
                        type="button"
                        onClick={() => setRestoreMode('OVERWRITE')}
                        className={`p-2 rounded-lg text-left border transition-all ${
                          restoreMode === 'OVERWRITE'
                            ? 'border-rose-500 bg-rose-500/10 text-rose-200'
                            : 'border-slate-800 text-slate-400 hover:border-slate-700'
                        }`}
                      >
                        <p className="text-xs font-semibold flex items-center gap-1 text-rose-400">
                          <AlertTriangle size={11} /> Overwrite
                        </p>
                        <p className="text-[10px] text-slate-500">Wipe and replace</p>
                      </button>
                    </div>
                  </div>

                  <button
                    onClick={handleRestore}
                    disabled={isRestoring}
                    className="w-full mt-2 py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white rounded-xl text-xs font-semibold flex items-center justify-center gap-2 shadow-lg shadow-emerald-600/20 transition-all"
                  >
                    {isRestoring ? (
                      <>
                        <Loader2 size={14} className="animate-spin" />
                        Restoring...
                      </>
                    ) : (
                      <>
                        <CheckCircle2 size={14} />
                        Confirm and Restore Messages
                      </>
                    )}
                  </button>
                </div>
              )}

              {restoreStatus && (
                <div
                  className={`p-3 rounded-xl text-xs flex items-center gap-2 ${
                    restoreStatus.success
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                      : 'bg-rose-500/10 text-rose-400 border border-rose-500/30'
                  }`}
                >
                  <CheckCircle2 size={16} />
                  <span>{restoreStatus.message}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
