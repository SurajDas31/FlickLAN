import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export function useWebSocket(currentUser, activeChannel, activePeer, onMessageReceived, onPeersUpdated, onTypingReceived) {
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef(null);

  // Keep references to latest callbacks to avoid stale closures
  const onMessageReceivedRef = useRef(onMessageReceived);
  const onPeersUpdatedRef = useRef(onPeersUpdated);
  const onTypingReceivedRef = useRef(onTypingReceived);

  useEffect(() => {
    onMessageReceivedRef.current = onMessageReceived;
  }, [onMessageReceived]);

  useEffect(() => {
    onPeersUpdatedRef.current = onPeersUpdated;
  }, [onPeersUpdated]);

  useEffect(() => {
    onTypingReceivedRef.current = onTypingReceived;
  }, [onTypingReceived]);

  useEffect(() => {
    if (!currentUser?.id) return;

    const socketFactory = () => new SockJS('/ws');

    // Detect device type
    const isMobile = /Android|iPhone|iPad|iPod|webOS|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    const clientType = isMobile ? 'MOBILE' : 'BROWSER';

    const client = new Client({
      webSocketFactory: socketFactory,
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setIsConnected(true);

        // 1. Announce presence to LAN mesh / server
        client.publish({
          destination: '/app/chat.register',
          body: JSON.stringify({
            id: currentUser.id,
            name: currentUser.name,
            clientType: clientType,
            ipAddress: window.location.hostname,
          }),
        });

        // 2. Subscribe to Peer Discovery notifications
        client.subscribe('/topic/peers', (message) => {
          try {
            const peers = JSON.parse(message.body);
            onPeersUpdatedRef.current?.(peers);
          } catch (e) {
            console.error('Error parsing peers', e);
          }
        });

        // 3. Subscribe to current user's Direct Messages
        client.subscribe(`/topic/dm/${currentUser.id}`, (message) => {
          try {
            const msg = JSON.parse(message.body);
            onMessageReceivedRef.current?.(msg, 'dm');
          } catch (e) {
            console.error('Error parsing direct message', e);
          }
        });

        // 4. Subscribe to typing indicators
        client.subscribe(`/topic/typing/${currentUser.id}`, (message) => {
          try {
            const payload = JSON.parse(message.body);
            onTypingReceivedRef.current?.(payload);
          } catch (e) {
            console.error('Error parsing typing message', e);
          }
        });
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
        setIsConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;

    // Periodic heartbeat every 8 seconds to keep peer status fresh
    const heartbeatInterval = setInterval(() => {
      if (client.connected) {
        client.publish({
          destination: '/app/chat.register',
          body: JSON.stringify({
            id: currentUser.id,
            name: currentUser.name,
            clientType: clientType,
            ipAddress: window.location.hostname,
          }),
        });
      }
    }, 8000);

    const handleBeforeUnload = () => {
      if (client.connected) {
        client.publish({
          destination: '/app/chat.leave',
          body: JSON.stringify({ id: currentUser.id }),
        });
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      clearInterval(heartbeatInterval);
      window.removeEventListener('beforeunload', handleBeforeUnload);
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, [currentUser?.id, currentUser?.name]);

  // Handle Channel Switch dynamic subscription
  useEffect(() => {
    const client = clientRef.current;
    if (!client || !client.connected || !activeChannel) return;

    const sub = client.subscribe(`/topic/channel/${activeChannel}`, (message) => {
      try {
        const msg = JSON.parse(message.body);
        onMessageReceivedRef.current?.(msg, 'channel');
      } catch (e) {
        console.error('Error parsing channel message', e);
      }
    });

    const typingSub = client.subscribe(`/topic/typing/${activeChannel}`, (message) => {
      try {
        const payload = JSON.parse(message.body);
        onTypingReceivedRef.current?.(payload);
      } catch (e) {
        console.error('Error parsing typing message', e);
      }
    });

    return () => {
      sub.unsubscribe();
      typingSub.unsubscribe();
    };
  }, [activeChannel, isConnected]);

  const sendChannelMessage = useCallback((content, fileName = null, fileSize = null, fileUrl = null) => {
    if (!clientRef.current || !clientRef.current.connected || !currentUser) return;

    const payload = {
      type: fileName ? 'FILE' : 'CHAT',
      senderId: currentUser.id,
      senderName: currentUser.name,
      channel: activeChannel || 'general',
      content: content,
      fileName: fileName,
      fileSize: fileSize,
      fileDownloadUrl: fileUrl,
      timestamp: new Date().toISOString(),
    };

    clientRef.current.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify(payload),
    });
  }, [currentUser, activeChannel]);

  const sendDirectMessage = useCallback((recipientId, content, fileName = null, fileSize = null, fileUrl = null) => {
    if (!clientRef.current || !clientRef.current.connected || !currentUser) return;

    const payload = {
      type: fileName ? 'FILE' : 'CHAT',
      senderId: currentUser.id,
      senderName: currentUser.name,
      recipientId: recipientId,
      content: content,
      fileName: fileName,
      fileSize: fileSize,
      fileDownloadUrl: fileUrl,
      timestamp: new Date().toISOString(),
    };

    clientRef.current.publish({
      destination: '/app/chat.privateMessage',
      body: JSON.stringify(payload),
    });
  }, [currentUser]);

  const sendTyping = useCallback((target) => {
    if (!clientRef.current || !clientRef.current.connected || !currentUser) return;
    clientRef.current.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({
        target,
        senderName: currentUser.name,
      }),
    });
  }, [currentUser]);

  return {
    isConnected,
    sendChannelMessage,
    sendDirectMessage,
    sendTyping,
  };
}
