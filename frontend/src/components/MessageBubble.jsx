import React from 'react';
import { FileText, Download, User } from 'lucide-react';

export default function MessageBubble({ message, isOwn }) {
  const isFile = message.type === 'FILE' || Boolean(message.fileName);
  const isSystem = message.type === 'SYSTEM';

  const formatTime = (isoString) => {
    if (!isoString) return '';
    try {
      const d = new Date(isoString);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  if (isSystem) {
    return (
      <div className="flex justify-center my-3">
        <span className="px-3 py-1 text-xs font-medium text-slate-400 bg-slate-800/60 rounded-full border border-slate-700/50">
          {message.content}
        </span>
      </div>
    );
  }

  return (
    <div className={`flex flex-col mb-4 ${isOwn ? 'items-end' : 'items-start'}`}>
      <div className="flex items-center gap-2 mb-1 px-1">
        {!isOwn && (
          <div className="w-5 h-5 rounded-full bg-indigo-600 flex items-center justify-center text-[10px] font-bold text-white uppercase">
            {message.senderName ? message.senderName[0] : <User size={12} />}
          </div>
        )}
        <span className="text-xs font-medium text-slate-400">
          {isOwn ? 'You' : message.senderName}
        </span>
        <span className="text-[10px] text-slate-500">
          {formatTime(message.timestamp)}
        </span>
      </div>

      <div
        className={`max-w-[85%] sm:max-w-[75%] rounded-2xl px-3.5 sm:px-4 py-2 sm:py-2.5 text-sm shadow-sm break-words ${
          isOwn
            ? 'bg-indigo-600 text-white rounded-tr-none'
            : 'bg-slate-800 text-slate-100 rounded-tl-none border border-slate-700/60'
        }`}
      >
        {isFile ? (
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-3 p-2 bg-slate-900/50 rounded-xl border border-slate-700/40">
              <div className="p-2 bg-indigo-500/20 text-indigo-400 rounded-lg">
                <FileText size={24} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-xs truncate text-slate-100">{message.fileName}</p>
                <p className="text-[10px] text-slate-400">{formatFileSize(message.fileSize)}</p>
              </div>
              <a
                href={message.fileDownloadUrl}
                download={message.fileName}
                className="p-2 text-indigo-400 hover:text-white hover:bg-indigo-600 rounded-lg transition-colors"
                title="Download File"
              >
                <Download size={18} />
              </a>
            </div>
            {message.content && <p className="text-xs mt-1">{message.content}</p>}
          </div>
        ) : (
          <p className="whitespace-pre-wrap leading-relaxed">{message.content}</p>
        )}
      </div>
    </div>
  );
}

