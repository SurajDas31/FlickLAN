import React, { useState, useRef, useEffect } from 'react';
import { 
  Send, 
  Paperclip, 
  Hash, 
  User, 
  UploadCloud, 
  Loader2,
  Menu
} from 'lucide-react';
import MessageBubble from './MessageBubble';
import { api } from '../services/api';

export default function ChatArea({
  activeChannel,
  activePeer,
  messages,
  currentUser,
  onSendMessage,
  typingStatus,
  onTyping,
  onOpenMobileNav
}) {
  const [inputText, setInputText] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);

  // Auto-scroll on new message
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = () => {
    if (!inputText.trim()) return;
    onSendMessage(inputText.trim());
    setInputText('');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    } else {
      if (onTyping) onTyping();
    }
  };

  const handleFileUpload = async (file) => {
    if (!file) return;
    setIsUploading(true);
    try {
      const res = await api.uploadFile(file);
      onSendMessage(
        '', // empty text
        res.originalName,
        res.size,
        res.downloadUrl
      );
    } catch (e) {
      alert('Failed to upload file: ' + e.message);
    } finally {
      setIsUploading(false);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      handleFileUpload(file);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) {
      handleFileUpload(file);
    }
  };

  return (
    <main 
      className="flex-1 flex flex-col h-full bg-slate-950 relative w-full overflow-hidden"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* Drag & Drop Overlay */}
      {isDragging && (
        <div className="absolute inset-0 bg-indigo-950/80 backdrop-blur-sm z-50 flex flex-col items-center justify-center border-2 border-dashed border-indigo-400 p-4 text-center">
          <UploadCloud size={48} className="text-indigo-400 animate-bounce mb-2" />
          <p className="text-base sm:text-lg font-semibold text-white">Drop file to send across LAN</p>
          <p className="text-xs text-indigo-300">Transfers directly peer-to-peer</p>
        </div>
      )}

      {/* Top Header */}
      <header className="h-14 sm:h-16 px-3 sm:px-6 border-b border-slate-800 flex items-center justify-between bg-slate-900/60 backdrop-blur z-10 flex-shrink-0">
        <div className="flex items-center gap-2 sm:gap-3 min-w-0">
          {/* Mobile Hamburger Drawer Toggle */}
          <button
            onClick={onOpenMobileNav}
            className="p-2 -ml-1 text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 md:hidden transition-colors flex-shrink-0"
            title="Open Channels and LAN Peers"
          >
            <Menu size={22} />
          </button>

          {activePeer ? (
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="relative flex-shrink-0">
                <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-indigo-600/30 border border-indigo-500/40 text-indigo-300 flex items-center justify-center font-bold text-xs sm:text-sm">
                  {activePeer.name ? activePeer.name[0].toUpperCase() : 'U'}
                </div>
                <span
                  className={`absolute bottom-0 right-0 w-2.5 h-2.5 sm:w-3 sm:h-3 rounded-full border-2 border-slate-900 ${
                    activePeer.online ? 'bg-emerald-500' : 'bg-slate-500'
                  }`}
                />
              </div>
              <div className="min-w-0 truncate">
                <h2 className="font-semibold text-slate-100 text-xs sm:text-sm truncate flex items-center gap-1.5">
                  <span className="truncate">{activePeer.name}</span>
                  <span className="hidden sm:inline-block text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 border border-slate-700">
                    Direct Message
                  </span>
                </h2>
                <p className="text-[10px] sm:text-xs text-slate-400 truncate">
                  {activePeer.online ? 'Online' : 'Offline'}
                </p>
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-2 sm:gap-2.5 min-w-0">
              <div className="p-1.5 sm:p-2 bg-slate-800 rounded-lg text-indigo-400 border border-slate-700 flex-shrink-0">
                <Hash size={16} className="sm:w-[18px] sm:h-[18px]" />
              </div>
              <div className="min-w-0 truncate">
                <h2 className="font-semibold text-slate-100 text-xs sm:text-sm truncate">#{activeChannel}</h2>
                <p className="text-[10px] sm:text-xs text-slate-400 truncate">Public LAN Room</p>
              </div>
            </div>
          )}
        </div>

        {/* Upload status indicator */}
        {isUploading && (
          <div className="flex items-center gap-1.5 sm:gap-2 text-[11px] sm:text-xs text-indigo-400 bg-indigo-500/10 px-2.5 py-1 rounded-full border border-indigo-500/30 flex-shrink-0">
            <Loader2 size={12} className="animate-spin" />
            <span className="hidden sm:inline">Transferring file...</span>
            <span className="sm:hidden">Uploading...</span>
          </div>
        )}
      </header>

      {/* Messages Feed */}
      <div className="flex-1 overflow-y-auto p-3 sm:p-6 space-y-1">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center text-slate-500 space-y-2 px-4">
            <div className="p-3 sm:p-4 bg-slate-900 rounded-full border border-slate-800">
              <Hash size={28} className="text-slate-600 sm:w-8 sm:h-8" />
            </div>
            <p className="font-medium text-slate-400 text-xs sm:text-sm">No messages yet</p>
            <p className="text-[11px] sm:text-xs max-w-sm">
              Say hello or attach a photo or file to share across your local network.
            </p>
          </div>
        ) : (
          messages.map((msg) => (
            <MessageBubble
              key={msg.id}
              message={msg}
              isOwn={msg.senderId === currentUser.id}
            />
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Typing indicator */}
      {typingStatus && (
        <div className="px-3 sm:px-6 py-1 text-[11px] text-slate-400 italic">
          {typingStatus}
        </div>
      )}

      {/* Message Input Bar */}
      <div className="p-2 sm:p-4 bg-slate-900/40 border-t border-slate-800/80 flex-shrink-0">
        <div className="flex items-end gap-1.5 sm:gap-2 bg-slate-900 rounded-2xl p-1.5 sm:p-2 border border-slate-800 focus-within:border-indigo-500/50 transition-all shadow-inner">
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            className="hidden"
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            title="Attach file or photo"
            disabled={isUploading}
            className="p-2 sm:p-2.5 text-slate-400 hover:text-indigo-400 hover:bg-slate-800 rounded-xl transition-colors disabled:opacity-50 flex-shrink-0"
          >
            <Paperclip size={18} />
          </button>

          {/* text-base prevents iOS Safari auto-zoom on input focus */}
          <textarea
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={
              activePeer
                ? `Message @${activePeer.name}...`
                : `Message #${activeChannel}...`
            }
            rows={1}
            className="flex-1 bg-transparent text-base sm:text-sm text-slate-100 placeholder-slate-500 resize-none outline-none py-1.5 sm:py-2 px-1 max-h-28"
          />

          <button
            onClick={handleSend}
            disabled={!inputText.trim() || isUploading}
            className="p-2 sm:p-2.5 bg-indigo-600 text-white rounded-xl hover:bg-indigo-500 transition-colors disabled:opacity-40 disabled:hover:bg-indigo-600 shadow-md shadow-indigo-600/30 flex-shrink-0"
          >
            <Send size={18} />
          </button>
        </div>
      </div>
    </main>
  );
}
