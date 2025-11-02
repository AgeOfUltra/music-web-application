// ==================== GLOBAL VARIABLES ====================
let stompClient = null;
let currentPage = 0;
let isLoadingSongs = false;
let hasMoreSongs = true;
let currentRoomName = null;
let currentUsername = null;
let currentUserColor = null;
let currentUserDarkerColor = null;
let participantRefreshInterval = null;
let isOrganizer = false;
let ignoreLocalEvents = false;
let jwtToken = null;
let audioLoadTimeout = null;
let currentSongData = null;
const userColors = {};
const colors = ['#1a1a1a', '#2d2d2d', '#3d3d3d', '#505050', '#636363', '#767676'];

// ==================== PAGE INITIALIZATION ====================
window.onload = function () {
    initializePage();
};

function initializePage() {
    currentRoomName = PAGE_DATA.roomName;
    currentUsername = PAGE_DATA.username;
    jwtToken = PAGE_DATA.jwtToken;
    currentUserColor = PAGE_DATA.userColor;
    currentUserDarkerColor = PAGE_DATA.darkerColor;
    isOrganizer = PAGE_DATA.isOrganizer;

    if (!jwtToken || !currentUsername || !currentRoomName) {
        console.error('Missing required data:', {jwtToken, currentUsername, currentRoomName});
        window.location.href = '/app/music/public/login';
        return;
    }

    console.log('✅ Initialized chat for:', currentUsername, 'in room:', currentRoomName);
    console.log('✅ Is organizer:', isOrganizer);

    updatePermissionNotice();
    setupAudioPlayerListeners();
    connectWebSocket(jwtToken);
    loadSongs();
    initializeToastNotifications();

    const songList = document.getElementById('songList');
    if (songList) {
        songList.addEventListener('scroll', () => {
            if (songList.scrollTop + songList.clientHeight >= songList.scrollHeight - 50) {
                loadSongs();
            }
        });
    }
}

// ==================== TOAST NOTIFICATION SYSTEM ====================
const ToastNotification = {
    show: function(message, type = 'info', duration = 3000) {
        const toastContainer = document.getElementById('toastContainer');
        if (!toastContainer) {
            console.error('Toast container not found!');
            return;
        }

        const toastDiv = document.createElement('div');
        toastDiv.className = `toast ${type}`;

        const icons = {
            success: '✓',
            error: '✕',
            info: 'ℹ',
            warning: '⚠'
        };

        toastDiv.innerHTML = `
            <span class="toast-icon">${icons[type] || icons.info}</span>
            <span class="toast-message">${message}</span>
            <button class="toast-close">×</button>
        `;

        toastContainer.appendChild(toastDiv);

        toastDiv.querySelector('.toast-close').addEventListener('click', () => {
            this.removeToast(toastDiv);
        });

        if (duration > 0) {
            setTimeout(() => this.removeToast(toastDiv), duration);
        }
    },

    removeToast: function(toastDiv) {
        toastDiv.classList.add('removing');
        setTimeout(() => toastDiv.remove(), 300);
    },

    success: function(message, duration = 3000) {
        this.show(message, 'success', duration);
    },

    error: function(message, duration = 3000) {
        this.show(message, 'error', duration);
    },

    info: function(message, duration = 3000) {
        this.show(message, 'info', duration);
    },

    warning: function(message, duration = 3000) {
        this.show(message, 'warning', duration);
    }
};

function initializeToastNotifications() {
    const successCreate = document.getElementById('successMessageCreate')?.textContent?.trim();
    const successJoin = document.getElementById('successMessageJoin')?.textContent?.trim();
    if (successCreate) {
        ToastNotification.success(successCreate);
    }
    if (successJoin) {
        ToastNotification.success(successJoin);
    }
}

// ==================== UPDATE CURRENT SONG DISPLAY ====================
function updateCurrentSongDisplay(songName, hero, heroine, language) {
    document.getElementById('currentSongTitle').textContent = songName;
    document.getElementById('currentSongDetails').textContent = `${hero} • ${heroine} • ${language}`;
}

// ==================== AUDIO PLAYER CONTROL ====================
function setupAudioPlayerListeners() {
    const audioPlayer = document.getElementById('audioPlayer');

    if (!audioPlayer) {
        console.error('❌ Audio player not found!');
        return;
    }

    console.log('🎵 Setting up audio player listeners for:', isOrganizer ? 'ORGANIZER' : 'NON-ORGANIZER');

    // Only organizer can send pause events
    audioPlayer.addEventListener('pause', () => {
        if (!ignoreLocalEvents && isOrganizer && stompClient && stompClient.connected && audioPlayer.src) {
            const playbackMessage = {
                action: 'PAUSE',
                timestamp: Math.floor(audioPlayer.currentTime * 1000),
                controller: currentUsername
            };
            console.log('⏸️ [ORGANIZER] Sending PAUSE command:', playbackMessage);
            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
        }
    });

    // Prevent non-organizer from playing audio manually
    if (!isOrganizer) {
        console.log('🔒 [NON-ORGANIZER] Locking audio player...');

        audioPlayer.addEventListener('play', (e) => {
            if (!ignoreLocalEvents) {
                console.log('🔒 [NON-ORGANIZER] Prevented manual play attempt');
                audioPlayer.pause();
            }
        }, true);

        audioPlayer.addEventListener('pause', (e) => {
            if (!ignoreLocalEvents && !audioPlayer.paused) {
                console.log('🔒 [NON-ORGANIZER] Prevented manual pause attempt');
                audioPlayer.play().catch(err => console.log('Resume from prevented pause:', err));
            }
        }, true);
    }
}

// ==================== PLAYBACK HANDLING ====================
function handlePlaybackCommand(playbackMsg) {
    const audioPlayer = document.getElementById('audioPlayer');

    if (!audioPlayer) {
        console.error('❌ Audio player not found in handlePlaybackCommand!');
        return;
    }

    ignoreLocalEvents = true;

    if (audioLoadTimeout) {
        clearTimeout(audioLoadTimeout);
    }

    if (playbackMsg.action === 'ERROR') {
        console.error('❌ Playback error:', playbackMsg.content);
        ToastNotification.error('Error: ' + playbackMsg.content);
        ignoreLocalEvents = false;
        return;
    }

    console.log('📥 [' + currentUsername + '] Received playback command:', playbackMsg.action, 'from:', playbackMsg.controller);

    switch (playbackMsg.action) {
        case 'PLAY':
            handlePlayCommand(audioPlayer, playbackMsg);
            break;
        case 'PAUSE':
            handlePauseCommand(audioPlayer, playbackMsg);
            break;
        case 'RESUME':
            handleResumeCommand(audioPlayer, playbackMsg);
            break;
        default:
            console.warn('⚠️ Unknown playback action:', playbackMsg.action);
            ignoreLocalEvents = false;
    }
}

function handlePlayCommand(audioPlayer, playbackMsg) {
    const newSrc = `/app/music/audio/streamSong/${playbackMsg.songFileName}`;

    currentSongData = {
        songFileName: playbackMsg.songFileName,
        songName: playbackMsg.songName,
        hero: playbackMsg.hero,
        heroine: playbackMsg.heroine,
        language: playbackMsg.language
    };

    console.log('🎵 [' + currentUsername + '] Received PLAY command for song:', playbackMsg.songName);
    console.log('   Audio source URL:', newSrc);
    updateCurrentSongDisplay(playbackMsg.songName, playbackMsg.hero, playbackMsg.heroine, playbackMsg.language);

    // ✅ TOAST NOTIFICATION FOR ALL USERS
    const isOwnAction = playbackMsg.controller === currentUsername;
    if (isOwnAction) {
        ToastNotification.info(`🎵 Now Playing: ${playbackMsg.songName}`);
    } else {
        ToastNotification.info(`🎵 ${playbackMsg.controller} is playing: ${playbackMsg.songName}`);
    }

    const sourceChanged = audioPlayer.src !== newSrc;
    console.log('   Source changed:', sourceChanged);

    if (sourceChanged) {
        console.log('   Setting new audio source...');
        audioPlayer.src = newSrc;
    }

    const startTime = playbackMsg.timestamp ? playbackMsg.timestamp / 1000 : 0;
    console.log('   Start time:', startTime.toFixed(2), 'seconds');

    if (sourceChanged) {
        console.log('   Waiting for metadata to load...');

        let metadataLoaded = false;

        const metadataHandler = () => {
            metadataLoaded = true;
            console.log('   ✅ Metadata loaded');
            audioPlayer.currentTime = startTime;

            console.log('   ▶️ Attempting to play from:', startTime.toFixed(2) + 's');
            const playPromise = audioPlayer.play();

            if (playPromise !== undefined) {
                playPromise
                    .then(() => {
                        console.log('   ✅ Audio playback started successfully');
                        ignoreLocalEvents = false;
                    })
                    .catch(err => {
                        console.error('   ❌ Play error:', err.message);
                        ignoreLocalEvents = false;
                    });
            } else {
                ignoreLocalEvents = false;
            }

            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            if (audioLoadTimeout) clearTimeout(audioLoadTimeout);
        };

        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        audioLoadTimeout = setTimeout(() => {
            if (!metadataLoaded) {
                console.warn('   ⚠️ Metadata loading timeout, attempting to play anyway');
                audioPlayer.removeEventListener('loadedmetadata', metadataHandler);

                // CRITICAL FIX: Keep ignoreLocalEvents = true during recovery
                // This prevents pause listener from sending PAUSE command

                audioPlayer.currentTime = startTime;
                const playPromise = audioPlayer.play();

                if (playPromise !== undefined) {
                    playPromise
                        .then(() => {
                            console.log('   ✅ Audio playback started (after timeout)');
                            ignoreLocalEvents = false;
                        })
                        .catch(err => {
                            console.error('   ❌ Play error after timeout:', err.message);
                            ignoreLocalEvents = false;
                        });
                } else {
                    ignoreLocalEvents = false;
                }
            }
        }, 5000);
    } else {
        console.log('   Same source, seeking and playing');
        audioPlayer.currentTime = startTime;

        const playPromise = audioPlayer.play();
        if (playPromise !== undefined) {
            playPromise
                .then(() => {
                    console.log('   ✅ Audio playback started');
                    ignoreLocalEvents = false;
                })
                .catch(err => {
                    console.error('   ❌ Play error:', err.message);
                    ignoreLocalEvents = false;
                });
        } else {
            ignoreLocalEvents = false;
        }
    }
}

function handlePauseCommand(audioPlayer, playbackMsg) {
    console.log('⏸️ Pausing at timestamp:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
    audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    audioPlayer.pause();

    // ✅ TOAST NOTIFICATION FOR ALL USERS
    const isOwnAction = playbackMsg.controller === currentUsername;
    if (isOwnAction) {
        ToastNotification.warning('⏸️ Music paused');
    } else {
        ToastNotification.warning(`⏸️ ${playbackMsg.controller} paused the music`);
    }

    displaySystemMessage(`${playbackMsg.controller} paused the music`);
    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 100);
}

function handleResumeCommand(audioPlayer, playbackMsg) {
    console.log('▶️ Resuming from timestamp:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
    if (playbackMsg.timestamp !== undefined) {
        audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    }

    const playPromise = audioPlayer.play();
    if (playPromise !== undefined) {
        playPromise.catch(err => {
            console.error('Resume error:', err.message);
            ignoreLocalEvents = false;
        });
    }

    // ✅ TOAST NOTIFICATION FOR ALL USERS
    const isOwnAction = playbackMsg.controller === currentUsername;
    if (isOwnAction) {
        ToastNotification.success('▶️ Music resumed');
    } else {
        ToastNotification.success(`▶️ ${playbackMsg.controller} resumed the music`);
    }

    displaySystemMessage(`${playbackMsg.controller} resumed the music`);
    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 100);
}

function playSong(song) {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can play songs');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        console.error('❌ WebSocket not connected');
        ToastNotification.error('WebSocket not connected. Please refresh.');
        return;
    }

    if (!currentUsername) {
        console.error('❌ Username not available:', currentUsername);
        ToastNotification.error('Username not found. Please refresh page.');
        return;
    }

    const playbackMessage = {
        action: 'PLAY',
        songFileName: song.fileName,
        songName: song.songName,
        hero: song.hero,
        heroine: song.heroine,
        language: song.language,
        sender: currentUsername,
        controller: currentUsername,
        timestamp: 0
    };

    console.log('🎵 [ORGANIZER] Preparing to play song:', song.songName);
    console.log('📤 Playback message to send:', playbackMessage);

    try {
        const messageJson = JSON.stringify(playbackMessage);
        stompClient.send(
            `/app/music/chat/${currentRoomName}/playback`,
            {},
            messageJson
        );

        console.log('✅ Message sent successfully');
    } catch (error) {
        console.error('❌ Error sending playback message:', error);
        ToastNotification.error('Failed to play song');
    }
}

function resumeSong() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can resume songs');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        console.error('❌ WebSocket not connected');
        ToastNotification.error('WebSocket not connected. Please refresh.');
        return;
    }

    const audioPlayer = document.getElementById('audioPlayer');

    const resumeMessage = {
        action: 'RESUME',
        timestamp: Math.floor(audioPlayer.currentTime * 1000),
        controller: currentUsername
    };

    console.log('▶️ [ORGANIZER] Sending RESUME command');

    try {
        stompClient.send(
            `/app/music/chat/${currentRoomName}/playback`,
            {},
            JSON.stringify(resumeMessage)
        );

        console.log('✅ Resume command sent successfully');
    } catch (error) {
        console.error('❌ Error sending resume command:', error);
        ToastNotification.error('Failed to resume music');
    }
}

// ==================== WEBSOCKET CONNECTION ====================
function connectWebSocket(token) {
    const socket = new SockJS('/app/music/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect(
        {'Authorization': `Bearer ${token}`},
        (frame) => {
            console.log('✅ Connected to WebSocket');
            ToastNotification.success('Connected to chat room');

            stompClient.subscribe(`/topic/chat/${currentRoomName}`, (message) => {
                const chatMessage = JSON.parse(message.body);
                displayMessage(chatMessage);
            });

            stompClient.subscribe(`/topic/chat/${currentRoomName}/playback`, (message) => {
                const playbackMsg = JSON.parse(message.body);
                handlePlaybackCommand(playbackMsg);
            });

            stompClient.subscribe(`/topic/chat/${currentRoomName}/participants`, (message) => {
                const participants = JSON.parse(message.body);
                updateParticipantsDisplay(participants);
            });

            stompClient.send(`/app/music/chat/${currentRoomName}/addUser`, {}, JSON.stringify({
                sender: currentUsername,
                type: 'JOIN',
                content: `${currentUsername} joined the room`
            }));

            startParticipantRefreshInterval();
        },
        (error) => {
            console.error('❌ WebSocket error:', error);
            ToastNotification.error('Connection error. Reconnecting...');
            setTimeout(() => {
                if (stompClient && !stompClient.connected) {
                    console.log('🔄 Attempting to reconnect...');
                    connectWebSocket(token);
                }
            }, 3000);
        }
    );
}

function startParticipantRefreshInterval() {
    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
    }

    participantRefreshInterval = setInterval(() => {
        refreshParticipants();
    }, 5000);
}

async function refreshParticipants() {
    try {
        const response = await fetch(`/app/music/room/getRoom?roomName=${encodeURIComponent(currentRoomName)}`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (response.ok) {
            const room = await response.json();
            if (room.participant) {
                updateParticipantsDisplay(room.participant);
            }
        }
    } catch (error) {
        console.error('Error refreshing participants:', error);
    }
}

// ==================== PARTICIPANTS DISPLAY ====================
function updateParticipantsDisplay(participants) {
    const participantsList = document.getElementById('participantsList');
    const participantCount = document.getElementById('participantCount');

    participantsList.innerHTML = '';
    participantCount.textContent = `${participants.length} / ${PAGE_DATA.totalCount} participants`;

    participants.forEach(p => {
        const item = document.createElement('div');
        item.className = 'participant-item';

        let userColor, darkerColor;
        if (p.userName === currentUsername) {
            userColor = currentUserColor;
            darkerColor = currentUserDarkerColor;
        } else {
            userColor = getUserColor(p.userName);
            darkerColor = getDarkerShade(userColor);
        }

        item.innerHTML = `
            <div class="participant-name" style="background: linear-gradient(135deg, ${userColor}, ${darkerColor})">${p.userName}</div>
            ${p.organizer ? '<span class="organizer-badge">Organizer</span>' : ''}
        `;
        participantsList.appendChild(item);

        if (p.userName === currentUsername) {
            isOrganizer = p.organizer;
            updatePermissionNotice();
        }
    });
}

function updatePermissionNotice() {
    const notice = document.getElementById('permissionNotice');
    if (!isOrganizer) {
        notice.style.display = 'block';
    } else {
        notice.style.display = 'none';
    }
}

// ==================== CHAT MESSAGING ====================
function sendMessage() {
    const input = document.getElementById('messageInput');
    const content = input.value.trim();

    if (!content) {
        console.warn('Empty message');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        console.error('❌ WebSocket not connected');
        ToastNotification.error('Not connected to chat. Please refresh.');
        return;
    }

    const chatMessage = {
        sender: currentUsername,
        content: content,
        type: 'CHAT'
    };

    try {
        stompClient.send(`/app/music/chat/${currentRoomName}/send`, {}, JSON.stringify(chatMessage));
        input.value = '';
        console.log('✅ Message sent:', content);
    } catch (error) {
        console.error('❌ Error sending message:', error);
        ToastNotification.error('Error sending message');
    }
}

function displayMessage(message) {
    const chatMessages = document.getElementById('chatMessages');

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        displaySystemMessage(message.content);
    } else {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message';

        const time = new Date().toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'});

        let userColor, darkerColor;
        if (message.sender === currentUsername) {
            userColor = currentUserColor;
            darkerColor = currentUserDarkerColor;
        } else {
            userColor = getUserColor(message.sender);
            darkerColor = getDarkerShade(userColor);
        }

        messageDiv.innerHTML = `
            <div class="message-header">
                <span class="message-sender" style="background: linear-gradient(135deg, ${userColor}, ${darkerColor})">${message.sender}</span>
                <span class="message-time">${time}</span>
            </div>
            <div class="message-content">${escapeHtml(message.content)}</div>
        `;

        chatMessages.appendChild(messageDiv);
    }

    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function displaySystemMessage(text) {
    const chatMessages = document.getElementById('chatMessages');
    const systemMsg = document.createElement('div');
    systemMsg.className = 'system-message';
    systemMsg.textContent = text;
    chatMessages.appendChild(systemMsg);

    setTimeout(() => {
        systemMsg.classList.add('fade-out');
        setTimeout(() => {
            if (systemMsg.parentNode) {
                systemMsg.parentNode.removeChild(systemMsg);
            }
        }, 500);
    }, 5000);

    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// ==================== SONG MANAGEMENT ====================
async function loadSongs() {
    if (isLoadingSongs || !hasMoreSongs) return;

    isLoadingSongs = true;

    try {
        const response = await fetch(`/app/music/audio/fetchAllSongs?page=${currentPage}&size=10`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            displaySongs(data.content);
            hasMoreSongs = !data.last;
            currentPage++;
        } else {
            ToastNotification.error('Error loading songs');
        }
    } catch (error) {
        console.error('Error loading songs:', error);
        ToastNotification.error('Failed to load songs');
    } finally {
        isLoadingSongs = false;
    }
}

function displaySongs(songs) {
    const songList = document.getElementById('songList');
    if (currentPage === 1) {
        songList.innerHTML = '';
    }

    songs.forEach(song => {
        const songItem = document.createElement('div');
        songItem.className = 'song-item';
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero;
        songItem.dataset.singer = song.singer;
        songItem.dataset.heroine = song.heroine;
        songItem.dataset.language = song.language;
        songItem.onclick = () => handleSongClick(songItem);
        songItem.innerHTML = `
            <div class="song-item-title">${song.songName}</div>
            <div class="song-item-info">${song.hero || 'Unknown'} • ${song.singer || 'Unknown'} • ${song.language || 'Unknown'}</div>
        `;
        songList.appendChild(songItem);
    });
}

function handleSongClick(element) {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can play songs');
        return;
    }

    const song = {
        fileName: element.dataset.filename,
        songName: element.dataset.songname,
        hero: element.dataset.hero,
        heroine: element.dataset.heroine,
        language: element.dataset.language
    };

    playSong(song);
}

async function searchSongs() {
    const query = document.getElementById('searchInput').value.trim();
    if (!query) {
        ToastNotification.warning('Please enter a search term');
        return;
    }

    try {
        const response = await fetch(`/app/music/audio/searchSong?query=${encodeURIComponent(query)}`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (response.ok) {
            const songs = await response.json();
            displaySearchResults(songs);
        } else {
            ToastNotification.error('Error searching songs');
        }
    } catch (error) {
        console.error('Error searching songs:', error);
        ToastNotification.error('Search failed');
    }
}

function displaySearchResults(songs) {
    const searchResults = document.getElementById('searchResults');
    searchResults.innerHTML = '';

    if (songs.length === 0) {
        searchResults.innerHTML = '<div class="no-results">No songs found</div>';
        ToastNotification.info('No songs found');
        return;
    }

    ToastNotification.info(`Found ${songs.length} song(s)`);

    songs.forEach(song => {
        const songItem = document.createElement('div');
        songItem.className = 'song-item';
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero;
        songItem.dataset.heroine = song.heroine;
        songItem.dataset.language = song.language;
        songItem.onclick = () => {
            if (isOrganizer) {
                handleSongClick(songItem);
                closeSearchDrawer();
            } else {
                ToastNotification.warning('Only the organizer can play songs');
            }
        };
        songItem.innerHTML = `
            <div class="song-item-title">${song.songName}</div>
            <div class="song-item-info">${song.hero || 'Unknown'} • ${song.heroine || 'Unknown'} • ${song.language || 'Unknown'}</div>
        `;
        searchResults.appendChild(songItem);
    });
}

// ==================== UTILITY FUNCTIONS ====================
function getUserColor(username) {
    if (!userColors[username]) {
        userColors[username] = colors[Object.keys(userColors).length % colors.length];
    }
    return userColors[username];
}

function getDarkerShade(color) {
    const colorMap = {
        '#1a1a1a': '#0a0a0a',
        '#2d2d2d': '#1a1a1a',
        '#3d3d3d': '#2d2d2d',
        '#505050': '#3d3d3d',
        '#636363': '#505050',
        '#767676': '#636363'
    };
    return colorMap[color] || '#000000';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function handleEnter(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

function handleSearchEnter(event) {
    if (event.key === 'Enter') {
        searchSongs();
    }
}

function openSearchDrawer() {
    document.getElementById('searchDrawer').classList.add('open');
}

function closeSearchDrawer() {
    document.getElementById('searchDrawer').classList.remove('open');
    document.getElementById('searchInput').value = '';
    document.getElementById('searchResults').innerHTML = '';
}

// ==================== LOGOUT ====================
async function logout() {
    if (participantRefreshInterval) clearInterval(participantRefreshInterval);

    if (stompClient && stompClient.connected) {
        stompClient.send(`/app/music/chat/${currentRoomName}/removeUser`, {}, JSON.stringify({
            sender: currentUsername,
            type: 'LEAVE',
            content: `${currentUsername} left the room`
        }));
        stompClient.disconnect();
    }

    try {
        await fetch(`/app/music/room/leave?roomName=${encodeURIComponent(currentRoomName)}&username=${encodeURIComponent(currentUsername)}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });
    } catch (error) {
        console.error('Error leaving room:', error);
    }

    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    localStorage.removeItem('currentRoom');
    document.cookie = 'jwtToken=; path=/; max-age=0; SameSite=Lax';
    window.location.href = '/app/music/public/login';
}

// ==================== WINDOW UNLOAD ====================
window.addEventListener('beforeunload', () => {
    if (participantRefreshInterval) clearInterval(participantRefreshInterval);

    if (stompClient && stompClient.connected) {
        stompClient.send(`/app/music/chat/${currentRoomName}/removeUser`, {}, JSON.stringify({
            sender: currentUsername,
            type: 'LEAVE',
            content: `${currentUsername} left the room`
        }));
    }
});