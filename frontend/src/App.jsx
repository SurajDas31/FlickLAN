import React, { useState, useEffect, useCallback, useRef } from 'react';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import BackupRestoreModal from './components/BackupRestoreModal';
import UpdateNotification from './components/UpdateNotification';
import { useWebSocket } from './hooks/useWebSocket';
import { api } from './services/api';

const DEFAULT_CHANNELS = ['general', 'random', 'dev-room'];

export default function App() {
  const [nodeInfo, setNodeInfo] = useState(null);

  // Current node identity (Initialized with local storage, synced with node info on boot)
  const [currentUser, setCurrentUser] = useState(() => {
    const savedName = localStorage.getItem('flicklan_username');
    const savedId = localStorage.getItem('flicklan_nodeid') || 'node_' + Math.random().toString(36).substring(2, 9);
    return {
      id: savedId,
      name: savedName || 'My-PC',
    };
  });

  const [channels] = useState(DEFAULT_CHANNELS);
  const [activeChannel, setActiveChannel] = useState('general');
  const [activePeer, setActivePeer] = useState(null);
  const [peers, setPeers] = useState([]);
  const [messages, setMessages] = useState([]);
  const [typingStatus, setTypingStatus] = useState('');
  const [isBackupModalOpen, setIsBackupModalOpen] = useState(false);
  const [updateInfo, setUpdateInfo] = useState(null);
  const [notificationsEnabled, setNotificationsEnabled] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  // Mutable refs to prevent stale callbacks in WebSocket event handlers
  const activePeerRef = useRef(activePeer);
  const activeChannelRef = useRef(activeChannel);
  const currentUserRef = useRef(currentUser);
  const peersRef = useRef(peers);

  useEffect(() => {
    activePeerRef.current = activePeer;
  }, [activePeer]);

  useEffect(() => {
    activeChannelRef.current = activeChannel;
  }, [activeChannel]);

  useEffect(() => {
    currentUserRef.current = currentUser;
  }, [currentUser]);

  useEffect(() => {
    peersRef.current = peers;
  }, [peers]);

  // Request browser notification permissions
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'granted') {
      setNotificationsEnabled(true);
    }
  }, []);

  const playChime = useCallback(() => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, ctx.currentTime);
      osc.frequency.setValueAtTime(880.0, ctx.currentTime + 0.08);
      gain.gain.setValueAtTime(0.2, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    } catch {
      // Audio autoplay catch
    }
  }, []);

  const handleToggleNotifications = async () => {
    if (!('Notification' in window)) {
      alert('This browser does not support desktop notifications.');
      return;
    }
    const perm = await Notification.requestPermission();
    if (perm === 'granted') {
      setNotificationsEnabled(true);
      new Notification('FlickLAN Notifications Enabled!', {
        body: 'You will receive pop-up alerts for incoming P2P messages and files.',
      });
    } else {
      setNotificationsEnabled(false);
    }
  };

  const triggerNotification = useCallback((newMsg) => {
    playChime();

    if ('Notification' in window && Notification.permission === 'granted') {
      const isCurrentChat = activePeerRef.current
        ? newMsg.senderId === activePeerRef.current.id
        : newMsg.channel === activeChannelRef.current;

      if (document.hidden || !isCurrentChat) {
        const title = newMsg.recipientId
          ? `💬 ${newMsg.senderName} (Direct Message)`
          : `📢 ${newMsg.senderName} in #${newMsg.channel || 'general'}`;

        const body = newMsg.content || (newMsg.fileName ? `📁 Shared file: ${newMsg.fileName}` : 'New message');

        const notification = new Notification(title, {
          body: body,
          tag: newMsg.senderId || newMsg.channel,
        });

        notification.onclick = () => {
          window.focus();
          if (newMsg.recipientId) {
            const peer = peersRef.current.find((p) => p.id === newMsg.senderId);
            if (peer) {
              setActivePeer(peer);
            }
          } else if (newMsg.channel) {
            setActivePeer(null);
            setActiveChannel(newMsg.channel);
          }
        };
      }
    }
  }, [playChime]);

  // 1. Fetch Node Info on Startup & Poll Discovered LAN Peers
  useEffect(() => {
    api.getNodeInfo()
      .then((info) => {
        setNodeInfo(info);
        const savedCustomName = localStorage.getItem('flicklan_username');
        setCurrentUser({
          id: info.instanceId,
          name: savedCustomName || info.hostName,
        });
        localStorage.setItem('flicklan_nodeid', info.instanceId);
      })
      .catch((err) => console.error('Error fetching local node info:', err));

    const pollPeers = () => {
      api.getPeers()
        .then((data) => {
          if (Array.isArray(data)) {
            setPeers(data);
          }
        })
        .catch(() => {});
    };

    pollPeers();
    const interval = setInterval(pollPeers, 3000);

    // Auto-update checker
    api.checkUpdate()
      .then((info) => {
        if (info.updateAvailable) setUpdateInfo(info);
      })
      .catch(() => {});

    return () => clearInterval(interval);
  }, []);

  // 2. Load Message History from Local Database
  const loadHistory = useCallback(() => {
    if (activePeer) {
      api.getDirectHistory(activePeer.id, currentUser.id)
        .then(setMessages)
        .catch((err) => console.error('Error loading DM history:', err));
    } else if (activeChannel) {
      api.getChannelHistory(activeChannel)
        .then(setMessages)
        .catch((err) => console.error('Error loading channel history:', err));
    }
  }, [activePeer, activeChannel, currentUser.id]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  // 3. WebSocket Real-Time Message Listener
  const handleMessageReceived = useCallback((newMsg, type) => {
    const currentActivePeer = activePeerRef.current;
    const currentActiveChannel = activeChannelRef.current;
    const currentUserId = currentUserRef.current.id;

    if (type === 'channel') {
      if (newMsg.channel === currentActiveChannel && !currentActivePeer) {
        setMessages((prev) => {
          const idx = prev.findIndex(
            (m) => m.id === newMsg.id || (m.id.startsWith('temp_') && m.senderId === newMsg.senderId && m.content === newMsg.content)
          );
          if (idx !== -1) {
            const updated = [...prev];
            updated[idx] = newMsg;
            return updated;
          }
          return [...prev, newMsg];
        });
      }

      if (newMsg.senderId !== currentUserId) {
        triggerNotification(newMsg);
      }
    } else if (type === 'dm') {
      const isViewingConversation =
        currentActivePeer &&
        (newMsg.senderId === currentActivePeer.id || newMsg.recipientId === currentActivePeer.id);

      if (isViewingConversation) {
        setMessages((prev) => {
          const idx = prev.findIndex(
            (m) => m.id === newMsg.id || (m.id.startsWith('temp_') && m.senderId === newMsg.senderId && m.content === newMsg.content)
          );
          if (idx !== -1) {
            const updated = [...prev];
            updated[idx] = newMsg;
            return updated;
          }
          return [...prev, newMsg];
        });
      }

      if (newMsg.senderId !== currentUserId) {
        triggerNotification(newMsg);
      }
    }
  }, [triggerNotification]);

  const handlePeersUpdated = useCallback((updatedPeers) => {
    if (Array.isArray(updatedPeers)) {
      setPeers(updatedPeers);
    }
  }, []);

  const handleTypingReceived = useCallback((payload) => {
    setTypingStatus(`${payload.senderName} is typing...`);
    const timer = setTimeout(() => setTypingStatus(''), 2500);
    return () => clearTimeout(timer);
  }, []);

  const { sendChannelMessage, sendDirectMessage, sendTyping } = useWebSocket(
    currentUser,
    activeChannel,
    activePeer,
    handleMessageReceived,
    handlePeersUpdated,
    handleTypingReceived
  );

  const handleSendMessage = (text, fileName = null, fileSize = null, fileUrl = null) => {
    const optimisticMessage = {
      id: 'temp_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
      type: fileName ? 'FILE' : 'CHAT',
      senderId: currentUser.id,
      senderName: currentUser.name,
      recipientId: activePeer ? activePeer.id : null,
      channel: activePeer ? null : (activeChannel || 'general'),
      content: text,
      fileName: fileName,
      fileSize: fileSize,
      fileDownloadUrl: fileUrl,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, optimisticMessage]);

    if (activePeer) {
      sendDirectMessage(activePeer.id, text, fileName, fileSize, fileUrl);
    } else {
      sendChannelMessage(text, fileName, fileSize, fileUrl);
    }
  };

  const handleSendTyping = () => {
    const target = activePeer ? activePeer.id : activeChannel;
    sendTyping(target);
  };

  const handleUpdateUserName = (newName) => {
    localStorage.setItem('flicklan_username', newName);
    setCurrentUser((prev) => ({ ...prev, name: newName }));
  };

  const handleSelectChannel = (channel) => {
    setActivePeer(null);
    setActiveChannel(channel);
  };

  const handleSelectPeer = (peer) => {
    setActivePeer(peer);
  };

  const handleDeleteChat = async (peer) => {
    const confirmed = window.confirm(`Are you sure you want to delete all chat history with ${peer.name}?`);
    if (!confirmed) return;

    try {
      await api.deleteDirectHistory(peer.id, currentUser.id);
      if (activePeer?.id === peer.id) {
        setMessages([]);
      }
      alert(`Chat history with ${peer.name} has been deleted.`);
    } catch (err) {
      alert('Failed to delete chat: ' + err.message);
    }
  };

  const handleBackupPeerChat = (peer) => {
    const url = api.getPeerExportUrl(peer.id, currentUser.id);
    window.open(url, '_blank');
  };

  const handleManualCheckUpdate = async () => {
    try {
      const info = await api.checkUpdate();
      setUpdateInfo(info);
      if (!info.updateAvailable) {
        alert(`You are running the latest version of FlickLAN (v${info.currentVersion})!`);
      }
    } catch {
      alert('Could not reach update server. You may be completely offline.');
    }
  };

  return (
    <div className="flex h-[100dvh] w-full overflow-hidden bg-slate-950 font-sans">
      {/* Sidebar: Lists Channels and all other discovered P2P Nodes */}
      <Sidebar
        nodeInfo={nodeInfo}
        peers={peers}
        channels={channels}
        activeChannel={activeChannel}
        activePeer={activePeer}
        onSelectChannel={handleSelectChannel}
        onSelectPeer={handleSelectPeer}
        currentUser={currentUser}
        onUpdateUserName={handleUpdateUserName}
        onOpenBackupModal={() => setIsBackupModalOpen(true)}
        onCheckUpdate={handleManualCheckUpdate}
        updateAvailable={updateInfo?.updateAvailable}
        notificationsEnabled={notificationsEnabled}
        onToggleNotifications={handleToggleNotifications}
        mobileOpen={mobileNavOpen}
        onCloseMobile={() => setMobileNavOpen(false)}
        onDeleteChat={handleDeleteChat}
        onBackupPeerChat={handleBackupPeerChat}
      />

      {/* Main Chat Area */}
      <ChatArea
        activeChannel={activeChannel}
        activePeer={activePeer}
        messages={messages}
        currentUser={currentUser}
        onSendMessage={handleSendMessage}
        typingStatus={typingStatus}
        onTyping={handleSendTyping}
        onOpenMobileNav={() => setMobileNavOpen(true)}
      />

      {/* Backup and Restore Modal */}
      <BackupRestoreModal
        isOpen={isBackupModalOpen}
        onClose={() => setIsBackupModalOpen(false)}
        onRestoreComplete={loadHistory}
      />

      {/* Auto-Update Banner */}
      {updateInfo?.updateAvailable && (
        <UpdateNotification
          updateInfo={updateInfo}
          onClose={() => setUpdateInfo(null)}
        />
      )}
    </div>
  );
}
