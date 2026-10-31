import React, { useState, useEffect, useRef } from 'react';
import { 
  Hash, 
  Users, 
  Radio, 
  Database, 
  RefreshCw, 
  User, 
  ArrowUpCircle,
  Smartphone,
  Laptop,
  Bell,
  BellOff,
  Server,
  X,
  MoreVertical,
  Trash2,
  Download,
  MessageSquare
} from 'lucide-react';

export default function Sidebar({
  nodeInfo,
  peers,
  channels,
  activeChannel,
  activePeer,
  onSelectChannel,
  onSelectPeer,
  currentUser,
  onUpdateUserName,
  onOpenBackupModal,
  onCheckUpdate,
  updateAvailable,
  notificationsEnabled,
  onToggleNotifications,
  mobileOpen,
  onCloseMobile,
  onDeleteChat,
  onBackupPeerChat
}) {
  const [isEditingName, setIsEditingName] = useState(false);
  const [nameInput, setNameInput] = useState(currentUser.name);

  // Context Menu State
  const [contextMenu, setContextMenu] = useState(null); // { x, y, peer }
  const contextMenuRef = useRef(null);

  // Close context menu on click outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (contextMenuRef.current && !contextMenuRef.current.contains(e.target)) {
        setContextMenu(null);
      }
    };
    window.addEventListener('click', handleClickOutside);
    window.addEventListener('contextmenu', handleClickOutside);
    return () => {
      window.removeEventListener('click', handleClickOutside);
      window.removeEventListener('contextmenu', handleClickOutside);
    };
  }, []);

  const saveName = () => {
    if (nameInput.trim()) {
      onUpdateUserName(nameInput.trim());
    }
    setIsEditingName(false);
  };

  const handleChannelClick = (chan) => {
    onSelectChannel(chan);
    if (onCloseMobile) onCloseMobile();
  };

  const handlePeerClick = (peer) => {
    onSelectPeer(peer);
    if (onCloseMobile) onCloseMobile();
  };

  const handlePeerContextMenu = (e, peer) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({
      x: Math.min(e.clientX, window.innerWidth - 200),
      y: Math.min(e.clientY, window.innerHeight - 150),
      peer,
    });
  };

  const handleMenuAction = (action) => {
    if (!contextMenu?.peer) return;
    const peer = contextMenu.peer;
    setContextMenu(null);

    if (action === 'chat') {
      handlePeerClick(peer);
    } else if (action === 'backup') {
      if (onBackupPeerChat) onBackupPeerChat(peer);
    } else if (action === 'delete') {
      if (onDeleteChat) onDeleteChat(peer);
    }
  };

  // Filter out ourselves from the peers list
  const otherPeers = peers.filter((p) => p.id !== currentUser.id);

  return (
    <>
      {/* Mobile Drawer Backdrop */}
      {mobileOpen && (
        <div 
          onClick={onCloseMobile}
          className="fixed inset-0 bg-black/70 backdrop-blur-xs z-40 md:hidden transition-opacity"
        />
      )}

      {/* Floating Context Menu */}
      {contextMenu && (
        <div
          ref={contextMenuRef}
          style={{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }}
          className="fixed z-50 w-48 bg-slate-800 border border-slate-700/80 rounded-xl shadow-2xl py-1 text-slate-200 animate-in fade-in zoom-in-95 duration-100"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="px-3 py-1.5 border-b border-slate-700/60 mb-1">
            <p className="text-[11px] font-semibold text-slate-300 truncate">{contextMenu.peer.name}</p>
            <p className="text-[9px] text-slate-400">Peer Options</p>
          </div>

          <button
            onClick={() => handleMenuAction('chat')}
            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs hover:bg-slate-700/70 text-slate-200 transition-colors text-left"
          >
            <MessageSquare size={14} className="text-indigo-400" />
            <span>Open Chat</span>
          </button>

          <button
            onClick={() => handleMenuAction('backup')}
            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs hover:bg-slate-700/70 text-slate-200 transition-colors text-left"
          >
            <Download size={14} className="text-emerald-400" />
            <span>Backup Chat (JSON)</span>
          </button>

          <button
            onClick={() => handleMenuAction('delete')}
            className="w-full flex items-center gap-2 px-3 py-1.5 text-xs hover:bg-rose-500/20 text-rose-400 transition-colors text-left"
          >
            <Trash2 size={14} className="text-rose-400" />
            <span>Delete Chat History</span>
          </button>
        </div>
      )}

      {/* Sidebar Container */}
      <aside 
        className={`
          fixed inset-y-0 left-0 z-40 w-72 sm:w-80 bg-slate-900 border-r border-slate-800 flex flex-col h-full select-none
          transition-transform duration-300 ease-in-out md:static md:translate-x-0
          ${mobileOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full'}
        `}
      >
        {/* Brand Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-indigo-600 rounded-xl text-white shadow-lg shadow-indigo-600/30">
              <Radio size={20} className="animate-pulse" />
            </div>
            <div>
              <h1 className="font-bold text-base text-slate-100 flex items-center gap-1.5">
                FlickLAN
                <span className="text-[10px] font-normal px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">
                  v1.0.0
                </span>
              </h1>
              <p className="text-[11px] text-slate-400">P2P Mesh Network</p>
            </div>
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={onToggleNotifications}
              title={notificationsEnabled ? 'Pop-up notifications enabled' : 'Enable pop-up notifications'}
              className={`p-1.5 rounded-lg transition-colors border ${
                notificationsEnabled
                  ? 'bg-indigo-500/20 text-indigo-400 border-indigo-500/30'
                  : 'bg-slate-800 text-slate-400 hover:text-slate-200 border-slate-700'
              }`}
            >
              {notificationsEnabled ? <Bell size={16} /> : <BellOff size={16} />}
            </button>

            {updateAvailable && (
              <button
                onClick={onCheckUpdate}
                title="Update Available!"
                className="p-1.5 bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500/30 rounded-lg transition-colors border border-emerald-500/30 animate-bounce"
              >
                <ArrowUpCircle size={16} />
              </button>
            )}

            <button
              onClick={onCloseMobile}
              className="p-1.5 text-slate-400 hover:text-slate-200 rounded-lg hover:bg-slate-800 md:hidden ml-1"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Scrollable Navigation List */}
        <div className="flex-1 overflow-y-auto px-3 py-4 space-y-6">
          {/* Public Channels */}
          <div>
            <div className="text-[11px] font-semibold tracking-wider text-slate-400 uppercase px-2 mb-2 flex items-center justify-between">
              <span>Channels</span>
              <span className="text-xs text-slate-500">{channels.length}</span>
            </div>
            <div className="space-y-1">
              {channels.map((chan) => {
                const isActive = activeChannel === chan && !activePeer;
                return (
                  <button
                    key={chan}
                    onClick={() => handleChannelClick(chan)}
                    className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                      isActive
                        ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/20'
                        : 'text-slate-300 hover:bg-slate-800/70 hover:text-slate-100'
                    }`}
                  >
                    <Hash size={16} className={isActive ? 'text-white' : 'text-slate-400'} />
                    <span>{chan}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Discovered LAN Peers */}
          <div>
            <div className="text-[11px] font-semibold tracking-wider text-slate-400 uppercase px-2 mb-2 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Users size={12} />
                Discovered Peers
              </span>
              <span className="text-xs text-slate-500">{otherPeers.length}</span>
            </div>

            {otherPeers.length === 0 ? (
              <div className="px-3 py-5 text-center rounded-xl bg-slate-950/40 border border-slate-800/60">
                <Radio size={18} className="mx-auto text-slate-600 mb-1.5 animate-pulse" />
                <p className="text-xs text-slate-400 font-medium">Scanning local network...</p>
                <p className="text-[10px] text-slate-500 mt-0.5">Other computers on your Wi-Fi will appear automatically</p>
              </div>
            ) : (
              <div className="space-y-1">
                {otherPeers.map((peer) => {
                  const isActive = activePeer?.id === peer.id;
                  const isMobile = peer.clientType === 'MOBILE';

                  return (
                    <div
                      key={peer.id}
                      onContextMenu={(e) => handlePeerContextMenu(e, peer)}
                      className={`group relative w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                        isActive
                          ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/20'
                          : 'text-slate-300 hover:bg-slate-800/70 hover:text-slate-100'
                      }`}
                    >
                      <button
                        onClick={() => handlePeerClick(peer)}
                        className="flex-1 flex items-center gap-2.5 min-w-0 text-left outline-none"
                      >
                        <div className="relative flex-shrink-0">
                          <div className="w-8 h-8 rounded-full bg-slate-800 flex items-center justify-center text-xs font-bold text-indigo-400 border border-slate-700">
                            {isMobile ? (
                              <Smartphone size={15} className="text-emerald-400" />
                            ) : (
                              peer.name ? peer.name[0].toUpperCase() : <Laptop size={15} className="text-slate-300" />
                            )}
                          </div>
                          <span
                            className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-slate-900 ${
                              peer.online ? 'bg-emerald-500' : 'bg-slate-500'
                            }`}
                          />
                        </div>
                        <div className="min-w-0 truncate">
                          <p className="text-xs font-medium truncate flex items-center gap-1.5">
                            {peer.name}
                            {isMobile && (
                              <span className="text-[9px] px-1 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                                Mobile
                              </span>
                            )}
                          </p>
                          <p className="text-[10px] text-slate-400 truncate">
                            {peer.online ? 'Online' : 'Offline'}
                          </p>
                        </div>
                      </button>

                      {/* 3-dots Context Menu Button */}
                      <button
                        onClick={(e) => handlePeerContextMenu(e, peer)}
                        className="p-1 text-slate-400 hover:text-white rounded-lg hover:bg-slate-700/60 opacity-80 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity ml-1 flex-shrink-0"
                        title="Options (Delete chat, Backup)"
                      >
                        <MoreVertical size={15} />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* User Footer & Personal Profile Bar */}
        <div className="p-3 bg-slate-950/80 border-t border-slate-800 flex flex-col gap-2">
          <div className="flex items-center justify-between p-2 rounded-xl bg-slate-900 border border-slate-800">
            <div className="flex items-center gap-2 min-w-0">
              <div className="w-8 h-8 rounded-full bg-indigo-600/30 text-indigo-400 flex items-center justify-center font-bold text-xs flex-shrink-0">
                <User size={16} />
              </div>
              {isEditingName ? (
                <input
                  type="text"
                  value={nameInput}
                  onChange={(e) => setNameInput(e.target.value)}
                  onBlur={saveName}
                  onKeyDown={(e) => e.key === 'Enter' && saveName()}
                  autoFocus
                  className="bg-slate-800 text-xs px-2 py-1 rounded text-white outline-none border border-indigo-500 w-28"
                />
              ) : (
                <div 
                  onClick={() => setIsEditingName(true)}
                  className="cursor-pointer truncate hover:text-indigo-400 transition-colors"
                  title="Click to rename"
                >
                  <p className="text-xs font-semibold text-slate-200 truncate">{currentUser.name}</p>
                  <p className="text-[10px] text-slate-400">Tap to edit name</p>
                </div>
              )}
            </div>

            <div className="flex items-center gap-1">
              <button
                onClick={onOpenBackupModal}
                title="Backup & Restore"
                className="p-1.5 text-slate-400 hover:text-indigo-400 hover:bg-slate-800 rounded-lg transition-colors"
              >
                <Database size={16} />
              </button>
              <button
                onClick={onCheckUpdate}
                title="Check for Updates"
                className="p-1.5 text-slate-400 hover:text-emerald-400 hover:bg-slate-800 rounded-lg transition-colors"
              >
                <RefreshCw size={16} />
              </button>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
